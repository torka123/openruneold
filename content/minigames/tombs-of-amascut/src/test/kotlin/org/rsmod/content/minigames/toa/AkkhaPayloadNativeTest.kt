package org.rsmod.content.minigames.toa

import kotlin.test.*
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * Actual manager admission, payload creation and LateCycle dispatch. Timing expectations preserve
 * the checkpoint source and its existing AkkhaTuning evidence labels, including unresolved tuning.
 */
@OptIn(InternalApi::class)
class AkkhaPayloadNativeTest {
    @Test
    fun `normal and Feeling Special memory reveal and resolve on the retained native timeline`() {
        for (feeling in listOf(false, true)) {
            try {
                ToaNativeCombatFixture().use { f ->
                    val player = f.player()
                    val messages = recordMessages(player)
                    val raid = enter(f, player, feeling)
                    val started = startMemory(f, raid, player)
                    val state = raid.akkha
                    val steps = state.memorySteps.toList()
                    val completion = assertNotNull(state.combatQueues).completionAt
                    val window = if (feeling) 2 else 4
                    assertEquals(listOf(4, 6, 8, 10), steps.map { it.revealAt - started })
                    assertEquals(
                        (0..3).map { 12 + it * window },
                        steps.map { it.resolvesAt - started },
                    )
                    assertEquals(steps.map { it.resolvesAt }, steps.map { it.blastStartsAt })
                    assertEquals(steps.last().resolvesAt + 2, completion)
                    clearInvocations(f.world, f.locs)
                    messages.clear()

                    f.dispatchLateCycle(started + 3)
                    assertTrue(steps.none { it.revealed })
                    for ((index, step) in steps.withIndex()) {
                        f.advance(step.revealAt - f.clock.cycle)
                        f.queueProcess.process()
                        assertFalse(step.revealed, "Default world processing cannot reveal memory")
                        f.dispatchLateCycle()
                        assertEquals(index + 1, steps.count { it.revealed })
                        assertEquals(index + 1, locAdds(f))
                        assertEquals(1, messages.count { it == SynthSound(5688, 1, 0) })
                    }
                    assertEquals(0, mapGraphics(f))
                    assertTrue(f.hits(player).isEmpty())

                    for ((index, step) in steps.withIndex()) {
                        player.coords = quadrantTile(f, raid, step.safeQuadrant)
                        f.dispatchLateCycle(step.resolvesAt - 1)
                        assertEquals(index * 225, mapGraphics(f))
                        f.dispatchLateCycle(step.resolvesAt)
                        assertEquals(0, step.renderedWaveTick)
                        assertEquals((index + 1) * 225, mapGraphics(f))
                        assertEquals(steps.size - index - 1, state.memorySteps.size)
                        assertTrue(
                            f.hits(player).isEmpty(),
                            "Current safe quadrant prevents the hit",
                        )
                    }
                    f.dispatchLateCycle(completion)
                    assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
                    assertTrue(state.memorySteps.isEmpty())
                    assertEquals(-1, assertNotNull(state.combatQueues).completionAt)
                    val graphics = mapGraphics(f)
                    f.dispatchLateCycle(completion + 1)
                    assertEquals(graphics, mapGraphics(f))
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `overdue memory reveals precede resolutions and tied completion cancels remaining payloads`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val messages = recordMessages(player)
            val raid = enter(f, player)
            val started = startMemory(f, raid, player)
            val state = raid.akkha
            val steps = state.memorySteps.toList()
            state.trace.enabled = true
            state.trace.clear()
            messages.clear()
            clearInvocations(f.world)
            // The first two successive safe quadrants differ. Stay safe for the first only.
            player.coords = quadrantTile(f, raid, steps.first().safeQuadrant)
            f.dispatchLateCycle(started + 16)
            assertEquals(
                List(4) { "memory_reveal" } + List(2) { "memory_resolve" },
                state.trace
                    .snapshot()
                    .map { it.event }
                    .filter { it in setOf("memory_reveal", "memory_resolve") },
            )
            assertEquals(1, messages.count { it == SynthSound(5688, 1, 0) })
            assertEquals(2, state.memorySteps.size)
            assertEquals(
                451,
                mapGraphics(f),
                "Two 225-tile renders then the unsafe player's element",
            )
            val (remainingCycles, hit) = f.hits(player).single()
            assertEquals(1, remainingCycles)
            assertEquals(HitType.Typeless, hit.type)
            assertTrue(hit.damage > 0)

            // Completion is processed before the payload owners. Skipping straight to it cancels
            // the other overdue steps instead of delivering them after the boss reappears.
            val completion = assertNotNull(state.combatQueues).completionAt
            val resolutions = state.trace.snapshot().count { it.event == "memory_resolve" }
            f.dispatchLateCycle(completion)
            assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
            assertTrue(state.memorySteps.isEmpty())
            assertEquals(resolutions, state.trace.snapshot().count { it.event == "memory_resolve" })
            assertEquals(451, mapGraphics(f))
            assertEquals(1, messages.count { it == SynthSound(5688, 1, 0) })
        }
    }

    @Test
    fun `detonation follows current tiles while logout and departure retire old origins`() {
        for (feeling in listOf(false, true)) {
            try {
                ToaNativeCombatFixture().use { f ->
                    val leader = f.player()
                    val marked = f.player(2)
                    val retiring = f.player(3)
                    val party = f.manager.createParty(leader)
                    if (feeling) assertTrue(party.invocations.toggle(ToaInvocation.FEELING_SPECIAL))
                    f.joinParty(leader, marked)
                    f.joinParty(leader, retiring)
                    val raid = f.start(leader, partySize = 3)
                    f.enterAkkha(leader)
                    leader.coords = tile(f, raid, 38, 38)
                    marked.coords = tile(f, raid, 40, 40)
                    retiring.coords = tile(f, raid, 24, 24)
                    performSpecial(f, raid, f.boss(raid), leader, AkkhaSpecial.DETONATE)
                    val cast = assertNotNull(raid.akkha.detonation)
                    assertEquals(setOf(leader.uid, marked.uid, retiring.uid), cast.origins.keys)
                    assertEquals(f.clock.cycle + 5, cast.resolvesAt)
                    assertEquals(feeling, cast.diagonal)

                    f.events.publish(SessionStateEvent.Logout(marked))
                    f.players.remove(marked.slotId)
                    val rebound = replacement(f, marked)
                    assertTrue(f.manager.openLobby(rebound))
                    assertTrue(f.manager.leave(retiring))
                    val pending = assertNotNull(raid.akkha.detonation)
                    assertEquals(setOf(leader.uid), pending.origins.keys)
                    assertEquals(cast.resolvesAt, pending.resolvesAt)
                    // Logout retires its marked origin. The leader's current ray hits the rebound
                    // player, while the origin itself remains safe. Neither cast tile could hit
                    // these locations. Freeze avoids introducing periodic status hits.
                    leader.coords = tile(f, raid, 28, 28)
                    rebound.coords = leader.coords.translate(1, if (feeling) 1 else 0)
                    clearInvocations(f.world)
                    f.dispatchLateCycle(cast.resolvesAt - 1)
                    assertTrue(f.hits(leader).isEmpty())
                    assertTrue(f.hits(rebound).isEmpty())
                    f.advance(1)
                    f.queueProcess.process()
                    assertNotNull(raid.akkha.detonation)
                    f.dispatchLateCycle()
                    assertNull(raid.akkha.detonation)
                    assertTrue(f.hits(marked).isEmpty())
                    assertTrue(f.hits(retiring).isEmpty())
                    assertTrue(f.hits(leader).isEmpty(), "A marked player's own origin is safe")
                    val hits =
                        listOf(rebound).map { player ->
                            val (remainingCycles, hit) = f.hits(player).single()
                            assertEquals(1, remainingCycles)
                            assertEquals(HitType.Typeless, hit.type)
                            assertTrue(hit.damage > 0)
                            Triple(player, hit, player.hitpoints)
                        }
                    f.advance(1)
                    hits.forEach { (player, hit, hp) ->
                        f.processHit(player, hit)
                        assertEquals(hp - hit.damage, player.hitpoints)
                    }
                    f.dispatchLateCycle()
                    assertNull(raid.akkha.detonation)
                    assertTrue(f.hits(leader).isEmpty())
                    assertEquals(1, f.hits(rebound).size)
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `final orb native cadence follows movement retries a full cap and retires across generations`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = enter(f, player)
            startMemory(f, raid, player)
            val oldPayload = assertNotNull(raid.akkha.payloadQueues)
            assertEquals(4, oldPayload.memorySteps.size)
            enterFinalStand(f, raid, f.boss(raid))
            assertTrue(oldPayload.isClosed)
            assertTrue(oldPayload.memorySteps.isEmpty())
            val state = raid.akkha
            val payload = assertNotNull(state.payloadQueues)
            assertNotSame(oldPayload, payload)
            val activated = f.clock.cycle
            player.coords = tile(f, raid, AkkhaArenaLayout.CENTER_X, AkkhaArenaLayout.CENTER_Z)
            assertEquals(activated + 3, state.nextFinalOrbAt)
            f.dispatchLateCycle(activated + 2)
            assertTrue(state.finalOrbs.isEmpty())
            f.advance(1)
            f.queueProcess.process()
            assertTrue(state.finalOrbs.isEmpty())
            f.dispatchLateCycle()
            val first = state.finalOrbs.values.single()
            val firstActor = raid.spawnedNpcs.single { it.hashCode() == first.actorKey }
            assertEquals(f.clock.cycle, first.spawnedAt)
            assertEquals(
                firstActor.coords,
                first.tile,
                "The new orb has not moved on its spawn tick",
            )
            val next = AkkhaFinalOrbController.next(firstActor.coords, first.direction)
            f.dispatchLateCycle(f.clock.cycle + 1)
            assertEquals(next, first.tile)
            f.dispatchLateCycle(state.nextFinalOrbAt)
            assertEquals(2, state.finalOrbs.size)
            val second = state.finalOrbs.values.last()
            val secondActor = raid.spawnedNpcs.single { it.hashCode() == second.actorKey }
            assertEquals(secondActor.coords, second.tile)

            // The actual manager supplies all actors; the test adds no synthetic orb records.
            while (state.finalOrbs.size < AkkhaTuning.FINAL_ORB_ACTIVE_CAP.value) {
                invoke(f, "spawnFinalOrb", raid)
            }
            val due = state.nextFinalOrbAt
            f.dispatchLateCycle(due)
            assertEquals(AkkhaTuning.FINAL_ORB_ACTIVE_CAP.value, state.finalOrbs.size)
            assertEquals(due, state.nextFinalOrbAt, "A capped opportunity remains pending")
            f.npcs.del(firstActor, Int.MAX_VALUE)
            f.dispatchLateCycle(due + 1)
            assertFalse(first.actorKey in state.finalOrbs)
            assertEquals(AkkhaTuning.FINAL_ORB_ACTIVE_CAP.value, state.finalOrbs.size)
            assertEquals(
                due + 4,
                state.nextFinalOrbAt,
                "Rearm is actual successful spawn plus three",
            )
            assertEquals(due + 1, state.finalOrbs.values.last().spawnedAt)

            invoke(f, "resetEncounter", raid)
            assertTrue(payload.isClosed)
            assertTrue(state.finalOrbs.isEmpty())
            assertEquals(AkkhaState.WAITING, state.state)
            f.dispatchLateCycle(f.clock.cycle + 30)
            assertTrue(raid.spawnedNpcs.none { it.id == AkkhaAssets.ENRAGE_ORB })
            f.enterAkkha(player)
            enterFinalStand(f, raid, f.boss(raid))
            val freshPayload = assertNotNull(state.payloadQueues)
            assertNotSame(payload, freshPayload)
            f.instances.terminate(raid.instance)
            assertTrue(freshPayload.isClosed)
            assertEquals(-1, freshPayload.finalOrbAt)
            f.dispatchLateCycle(f.clock.cycle + 30)
            assertTrue(state.finalOrbs.isEmpty())
        }
    }

    private fun enter(
        f: ToaNativeCombatFixture,
        player: Player,
        feeling: Boolean = false,
    ): ToaRaidSession {
        if (feeling) {
            val party = f.manager.createParty(player)
            assertTrue(party.invocations.toggle(ToaInvocation.FEELING_SPECIAL))
        }
        f.start(player)
        return f.enterAkkha(player)
    }

    private fun startMemory(f: ToaNativeCombatFixture, raid: ToaRaidSession, player: Player): Int {
        val source = f.boss(raid)
        f.bossEncounters.of(source).akkhaSpecialHistory().previous = AkkhaSpecial.TRAILING_ORBS
        player.coords = source.coords.translate(-1, 0)
        repeat(8) {
            if (source.actionDelay > f.clock.cycle) f.advance(source.actionDelay - f.clock.cycle)
            f.attack(source, player)
        }
        assertEquals(setOf(AkkhaSpecial.MEMORY_BLAST), raid.akkha.activeSpecials.toSet())
        player.clearQueue("queue.hit")
        return f.clock.cycle
    }

    private fun performSpecial(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        source: Npc,
        target: Player,
        special: AkkhaSpecial,
    ) {
        // Special selection/admission is covered by ToaSpecialChoicesTest and native combat tests.
        // Execute the real named payload, including its native queue registration.
        val method =
            ToaRaidManager::class
                .java
                .getDeclaredMethod(
                    "performAkkhaSpecial",
                    ToaRaidSession::class.java,
                    Npc::class.java,
                    Player::class.java,
                    AkkhaSpecial::class.java,
                )
        method.isAccessible = true
        method.invoke(f.manager, raid, source, target, special)
    }

    private fun enterFinalStand(f: ToaNativeCombatFixture, raid: ToaRaidSession, source: Npc) {
        val started = f.clock.cycle
        val method =
            ToaRaidManager::class
                .java
                .getDeclaredMethod(
                    "beginAkkhaFinalStand",
                    ToaRaidSession::class.java,
                    Npc::class.java,
                )
        method.isAccessible = true
        method.invoke(f.manager, raid, source)
        for (offset in listOf(2, 4, 5)) f.dispatchLateCycle(started + offset)
        assertEquals(AkkhaState.FINAL_STAND, raid.akkha.state)
    }

    private fun invoke(f: ToaNativeCombatFixture, name: String, raid: ToaRaidSession) {
        val method = ToaRaidManager::class.java.getDeclaredMethod(name, ToaRaidSession::class.java)
        method.isAccessible = true
        method.invoke(f.manager, raid)
    }

    private fun replacement(f: ToaNativeCombatFixture, departed: Player): Player =
        f.player(9).apply {
            uuid = departed.uuid
            accountHash = departed.accountHash
            accountId = departed.accountId
            characterId = departed.characterId
            assignUid()
        }

    private fun quadrantTile(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        quadrant: AkkhaQuadrant,
    ): CoordGrid =
        AkkhaFloorBlastController.quadrantTiles(
                tile(f, raid, AkkhaArenaLayout.CENTER_X, AkkhaArenaLayout.CENTER_Z),
                setOf(quadrant),
            )
            .first()

    private fun tile(f: ToaNativeCombatFixture, raid: ToaRaidSession, x: Int, z: Int): CoordGrid =
        assertNotNull(
            f.instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(ToaRoom.AKKHA, x, z))
        )

    private fun locAdds(f: ToaNativeCombatFixture) =
        mockingDetails(f.locs).invocations.count { it.method.name.startsWith("add") }

    private fun mapGraphics(f: ToaNativeCombatFixture) =
        mockingDetails(f.world).invocations.count { it.method.name.startsWith("spotanimMap") }

    @Suppress("UNCHECKED_CAST")
    private fun recordMessages(player: Player): MutableList<Any> {
        val messages = mutableListOf<Any>()
        player.client =
            mock(Client::class.java) { call ->
                if (call.method.name == "write") messages += call.arguments[0]
                RETURNS_DEFAULTS.answer(call)
            }
                as Client<Any, Any>
        return messages
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
