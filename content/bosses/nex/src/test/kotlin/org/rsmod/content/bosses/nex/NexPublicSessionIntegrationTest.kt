package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.instances.InstanceAttributes
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.currentInstanceId
import org.rsmod.api.instances.events.InstancePlayerJoinUnboundEvent
import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Public-session adoption mapped in docs/bosses/nex/NEX_OSRS_AUDIT.md: real InstanceManager,
 * protected barrier dispatch, exact player admission and native actor/loc ownership. Existing M26
 * capture3284 t549 -> t587 -> t790 supports consecutive rounds; its 50-cycle reset and five-cycle
 * opening remain characterization. M21/M14 loc lifetimes retain their separate evidence/tests.
 * Guard phase setup and player-death cleanup hooks are exercised directly; this does not claim
 * complete player death/drop simulation, new reward behavior or normal OSRS timing certification.
 */
@OptIn(InternalApi::class)
class NexPublicSessionIntegrationTest {
    @Test
    fun `startup creates one canonical public session owning five actors without admission`() {
        val f = NexMinionTestWorld(enterRoom = false)
        val session = session(f)
        assertTrue(session.isServerOwned)
        assertIs<InstancePlacement.Fixed>(session.placement)
        assertTrue(session.regionIds.isEmpty())
        assertEquals(0, session.spec.fee)
        assertNull(session.spec.timeLimitTicks)
        assertFalse(session.spec.recordsEligible)
        assertEquals(NEX_BANK_RETURN, f.player.coords)
        assertTrue(f.instances.playersIn(session).isEmpty())
        assertNull(f.instances.sessionForPlayer(f.player))
        assertNull(f.player.currentInstanceId())
        assertNull(f.player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
        assertNull(f.player.lootDropDuration)
        assertRoster(f, session)
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
        f.tick(20)
        assertSame(session, session(f))
        assertRoster(f, session)
        assertNoRegionWork(f)
    }

    @Test
    fun `registered barrier admits only after actual protected arrival`() {
        for (delayed in listOf(false, true)) {
            val f = NexMinionTestWorld(enterRoom = false)
            val session = session(f)
            var hookCalls = 0
            f.beforeTeleport = { target, destination, _ ->
                hookCalls++
                assertSame(f.player, target)
                assertEquals(NEX_BANK_RETURN, target.coords)
                assertEquals(NEX_ARENA_ENTRY, destination)
                assertNull(f.instances.sessionForPlayer(target))
                assertTrue(f.instances.playersIn(session).isEmpty())
            }
            if (delayed) f.player.lastMovement = f.player.processedMapClock - 1
            assertTrue(f.passBarrier())
            if (delayed) {
                assertEquals(0, hookCalls)
                assertNull(f.instances.sessionForPlayer(f.player))
                f.tick()
            }
            assertEquals(1, hookCalls)
            assertEquals(NEX_ARENA_ENTRY, f.player.coords)
            assertSame(session, f.instances.sessionForPlayer(f.player))
            assertEquals(listOf(f.player), f.instances.playersIn(session))
            assertEquals(NEX_DROP_DURATION, f.player.lootDropDuration)
            assertNull(f.player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
            assertEquals(11279, f.npc(11278).visType.id)
            assertEquals(9182, f.npc(11278).pendingSequence.id)
            framework().clearInlineMocks()
        }

        val f = NexMinionTestWorld(enterRoom = false)
        val session = session(f)
        f.collision.deallocateIfPresent(NEX_ARENA_ENTRY.x, NEX_ARENA_ENTRY.z, NEX_ARENA_ENTRY.level)
        assertTrue(f.passBarrier())
        assertEquals(NEX_BANK_RETURN, f.player.coords)
        assertNull(f.instances.sessionForPlayer(f.player))
        assertTrue(f.instances.playersIn(session).isEmpty())
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
    }

    @Test
    fun `bank native logout disconnect and either death cleanup order release one admission`() {
        for (departure in
            listOf("bank", "logout", "disconnect", "nex-death-first", "instance-death-first")) {
            val f = NexMinionTestWorld(duo = true)
            val session = session(f)
            val partner = assertNotNull(f.partner)
            val boss = f.npc(11278)
            val origin = f.player.coords
            when (departure) {
                "bank" -> assertTrue(f.travelBank())
                "logout" -> f.events.publish(SessionStateEvent.Logout(f.player))
                "disconnect" -> {
                    f.player.clientDisconnected.set(true)
                    f.tick()
                }
                else -> {
                    f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
                    assertSame(session, f.instances.sessionForPlayer(f.player))
                    assertNotNull(NexPlayerDeathHook(f.manager).handleDeath(deathContext(f.player)))
                    val cleanup = NexDeathCleanup(f.manager)
                    if (departure == "nex-death-first") {
                        cleanup.cleanup(f.player)
                        f.instances.handleDeath(f.player, f.clock.cycle)
                    } else {
                        f.instances.handleDeath(f.player, f.clock.cycle)
                        cleanup.cleanup(f.player)
                    }
                }
            }
            assertNull(f.instances.sessionForPlayer(f.player), departure)
            assertFalse(f.manager.isParticipant(f.player), departure)
            assertNull(f.player.currentInstanceId(), departure)
            assertNull(f.player.lootDropDuration, departure)
            assertNull(f.player.attr[InstanceAttributes.LOGIN_EXIT_COORD], departure)
            assertEquals(if (departure == "bank") NEX_BANK_TELEPORT else origin, f.player.coords)
            assertEquals(listOf(partner), f.instances.playersIn(session), departure)
            assertSame(session, f.instances.sessionForPlayer(partner))
            assertEquals(NEX_DROP_DURATION, partner.lootDropDuration)
            assertSame(boss, f.npc(11278))
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `raw slot replacement never inherits native admission or player effect ownership`() {
        for (sameUid in listOf(false, true)) {
            val f = NexMinionTestWorld(duo = true)
            val session = session(f)
            val partner = assertNotNull(f.partner)
            val replacement = replacement(f.player, f, sameUid).apply { lootDropDuration = 731 }
            f.players[f.player.slotId] = replacement
            assertNull(f.instances.sessionForPlayer(replacement))
            assertFalse(replacement in f.instances.playersIn(session))
            f.tick()
            assertEquals(listOf(partner), f.instances.playersIn(session))
            assertFalse(f.manager.isParticipant(replacement))
            assertNull(replacement.currentInstanceId())
            assertEquals(731, replacement.lootDropDuration)
            assertNull(
                f.player.lootDropDuration,
                "Clean the captured original player, not its replacement",
            )
            assertSame(session, session(f))
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `altar exit rejects a raw replacement with the same packed player uid`() {
        val f = NexMinionTestWorld(duo = true)
        val session = session(f)
        val partner = assertNotNull(f.partner)
        val replacement = replacement(f.player, f, sameUid = true).apply { lootDropDuration = 731 }
        assertEquals(f.player.uid, replacement.uid)
        f.players[f.player.slotId] = replacement
        val origin = replacement.coords
        f.beforeTeleport = { _, _, _ -> fail("A replacement login must not pass altar validation") }

        assertFalse(f.exit(replacement))

        assertEquals(origin, replacement.coords)
        assertNull(f.instances.sessionForPlayer(replacement))
        assertEquals(731, replacement.lootDropDuration)
        assertEquals(NEX_DROP_DURATION, f.player.lootDropDuration)
        assertSame(session, f.instances.sessionForPlayer(partner))
        assertEquals(NEX_DROP_DURATION, partner.lootDropDuration)
    }

    @Test
    fun `native join callback moving the entrant cannot leave an orphan admission`() {
        val f = NexMinionTestWorld(enterRoom = false)
        val session = session(f)
        val shifted = NEX_ARENA_ENTRY.translateX(1)
        var joined = false
        f.events.subscribeUnbound(InstancePlayerJoinUnboundEvent::class.java) {
            if (player === f.player) {
                joined = true
                assertSame(session, f.instances.sessionForPlayer(player))
                assertEquals(NEX_ARENA_ENTRY, player.coords)
                player.coords = shifted
            }
        }

        assertFalse(f.enter())

        assertTrue(joined)
        assertEquals(shifted, f.player.coords, "Admission rollback must not undo callback movement")
        assertNull(f.instances.sessionForPlayer(f.player))
        assertTrue(f.instances.playersIn(session).isEmpty())
        assertNull(f.player.currentInstanceId())
        assertNull(f.player.lootDropDuration)
        assertFalse(f.manager.isParticipant(f.player))
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
    }

    @Test
    fun `two native completed rounds keep one session and preclaim each configured actor`() {
        val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val session = session(f)
        val partner = assertNotNull(f.partner)
        f.player.coords = NEX_SPAWN.translateX(-10)
        partner.coords = NEX_SPAWN.translateX(10)
        val positions = listOf(f.player.coords, partner.coords)
        var creations = 0
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            if (npc.type.id in MAIN_ACTORS) {
                creations++
                assertEquals(session.id, f.instances.instanceForNpc(npc))
                assertTrue(f.instances.npcsForInstance(session.id).any { it === npc })
                assertEquals(expectedHitpoints(npc), npc.hitpoints)
            }
        }
        repeat(2) {
            val oldBoss = f.npc(11278)
            val oldEncounter = f.encounters.of(oldBoss)
            val oldActors = f.instances.npcsForInstance(session.id)
            completeRound(f)
            assertSame(session, session(f))
            assertEquals(setOf(f.player, partner), f.instances.playersIn(session).toSet())
            assertEquals(positions, listOf(f.player.coords, partner.coords))
            assertTrue(oldActors.none { it.isSlotAssigned })
            assertTrue(oldEncounter.isEnded)
            val freshBoss = f.npc(11278)
            val freshUid = freshBoss.uid
            assertNotSame(oldBoss, freshBoss)
            assertEquals(11279, freshBoss.visType.id)
            assertEquals(9182, freshBoss.pendingSequence.id)
            assertRoster(f, session)
            assertEquals("SPAWNING", f.encounters.of(f.npc(11278)).currentPhaseName)
            f.tick(5)
            assertSame(freshBoss, f.npc(11278))
            assertEquals(freshUid, freshBoss.uid)
            assertEquals(11278, freshBoss.visType.id)
            assertEquals("SMOKE", f.encounters.of(freshBoss).currentPhaseName)
        }
        assertEquals(10, creations)
        assertNoRegionWork(f)
    }

    @Test
    fun `blood reavers expose prepared stats and owning public session during native creation`() {
        val f = NexMinionTestWorld(duo = true)
        val session = session(f)
        val observed = mutableListOf<Npc>()
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            if (npc.type.id == 11294) {
                observed += npc
                assertEquals(NEX_REAVER_HITPOINTS, npc.hitpoints)
                assertEquals(session.id, f.instances.instanceForNpc(npc))
                assertTrue(f.instances.npcsForInstance(session.id).any { it === npc })
            }
        }
        val reaver = f.summonReaver()
        assertTrue(observed.any { it === reaver })
        assertTrue(observed.isNotEmpty())
        observed.forEach {
            assertEquals(session.id, f.instances.instanceForNpc(it))
            assertFalse(it.respawns, "Keep Nex's existing post-add reaver policy")
            assertEquals(-1, it.lifecycleDelCycle, "Do not introduce a finite Summon duration")
        }
        assertTrue(f.npcs.all { f.instances.instanceForNpc(it) == session.id })
        assertEquals(f.npcs.toSet(), f.instances.npcsForInstance(session.id).toSet())
    }

    @Test
    fun `source retirement and terminal session cleanup retire native locs and exact player effects`() {
        for (phase in listOf(NexState.SHADOW, NexState.ICE)) for (terminal in listOf(false, true)) {
            val f = NexMinionTestWorld(duo = true, nativeLocs = true)
            val session = session(f)
            val partner = assertNotNull(f.partner)
            f.reachPhase(phase)
            f.player.coords = CoordGrid(2915, 5200)
            partner.coords = CoordGrid(2933, 5200)
            f.player.queueList.clear()
            partner.queueList.clear()
            f.registeredSpecial()
            if (phase == NexState.ICE) f.registeredSpecial()
            val locs = encounterLocs(f)
            assertTrue(locs.isNotEmpty(), "Production special must create native locs")
            val oldBoss = f.npc(11278)
            val oldEncounter = f.encounters.of(oldBoss)
            val oldActors = f.instances.npcsForInstance(session.id)
            if (terminal) assertTrue(f.instances.terminate(session))
            else f.repository.del(oldBoss, Int.MAX_VALUE)
            f.tick(2)
            assertTrue(oldEncounter.isEnded)
            assertTrue(oldActors.none { it.isSlotAssigned })
            assertTrue(f.instances.playersIn(session).isEmpty())
            for (player in listOf(f.player, partner)) {
                assertNull(f.instances.sessionForPlayer(player))
                assertNull(player.currentInstanceId())
                assertNull(player.lootDropDuration)
                assertTrue(f.hits(player).isEmpty())
            }
            assertLocsGone(f, locs)
            if (terminal) {
                assertNull(f.instances.sessionForId(session.id))
                assertEquals(0, f.npcs.count())
            } else {
                assertSame(session, session(f))
                assertRoster(f, session)
                assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
            }
            f.tick(NEX_RESPAWN_TICKS + 5)
            assertLocsGone(f, locs)
            assertEquals(if (terminal) 0 else 5, f.npcs.count())
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `termination during native replacement creation cannot publish or restart a new round`() {
        val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val session = session(f)
        val oldBoss = f.npc(11278)
        val oldEncounter = f.encounters.of(oldBoss)
        var intercepted = false
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            if (!intercepted && npc.type.id == 11278 && npc !== oldBoss) {
                intercepted = true
                assertEquals(session.id, f.instances.instanceForNpc(npc))
                assertTrue(f.instances.terminate(session))
            }
        }
        completeRound(f)
        assertTrue(intercepted)
        assertTrue(oldEncounter.isEnded)
        assertNull(f.instances.sessionForId(session.id))
        assertTrue(f.instances.npcsForInstance(session.id).isEmpty())
        assertEquals(0, f.npcs.count())
        assertNull(f.player.lootDropDuration)
        assertNull(assertNotNull(f.partner).lootDropDuration)
        f.tick(NEX_RESPAWN_TICKS + 5)
        assertEquals(0, f.npcs.count())
        assertNull(f.instances.firstSession())
    }

    @Test
    fun `later guard creation deleting an earlier actor cannot publish a partial round`() {
        for (victimId in listOf(11278, 11283)) {
            val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
            val session = session(f)
            val oldBoss = f.npc(11278)
            val oldEncounter = f.encounters.of(oldBoss)
            val attemptedActors = mutableListOf<Npc>()
            var intercepted = false
            f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
                if (npc.type.id in MAIN_ACTORS) attemptedActors += npc
                if (!intercepted && npc.type.id == 11284) {
                    intercepted = true
                    val earlier =
                        f.instances.npcsForInstance(session.id).first {
                            it.type.id == victimId && it !== oldBoss
                        }
                    assertNotSame(npc, earlier)
                    assertSame(earlier, earlier.uid.resolve(f.npcs))
                    assertNotEquals(ZoneKey.NULL, earlier.lastProcessedZone)
                    // This actor's native add is complete. Do not delete the current Create actor.
                    f.repository.del(earlier, Int.MAX_VALUE)
                }
            }

            completeRound(f)

            assertTrue(intercepted)
            assertTrue(oldEncounter.isEnded)
            assertTrue(attemptedActors.isNotEmpty())
            attemptedActors.forEach {
                assertFalse(it.isSlotAssigned)
                assertNull(f.instances.instanceForNpc(it))
            }
            assertTrue(f.instances.npcsForInstance(session.id).isEmpty())
            assertNull(f.instances.sessionForId(session.id))
            assertEquals(0, f.npcs.count())
            assertNull(f.player.lootDropDuration)
            assertNull(assertNotNull(f.partner).lootDropDuration)
            f.tick(NEX_RESPAWN_TICKS + 5)
            assertEquals(0, f.npcs.count())
            assertNull(f.instances.firstSession())
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `round cleanup reentering world maintenance cannot delete the new roster`() {
        val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val session = session(f)
        f.player.coords = NEX_SPAWN.translateX(-10)
        assertNotNull(f.partner).coords = NEX_SPAWN.translateX(10)
        val oldBoss = f.npc(11278)
        var cleanups = 0
        f.encounters.of(oldBoss).onCleanup {
            cleanups++
            f.manager.tickWorld()
        }

        completeRound(f)

        assertEquals(1, cleanups)
        assertSame(session, session(f))
        assertNotSame(oldBoss, f.npc(11278))
        assertRoster(f, session)
        assertTrue(f.manager.isParticipant(f.player))
        assertTrue(f.manager.isParticipant(assertNotNull(f.partner)))
        assertEquals("SPAWNING", f.encounters.of(f.npc(11278)).currentPhaseName)
        f.tick(5)
        assertRoster(f, session)
        assertEquals(1, cleanups)
    }

    private fun session(f: NexMinionTestWorld): InstanceSession =
        assertNotNull(f.instances.firstSession(), "Nex must use a registered native public session")

    private fun assertRoster(f: NexMinionTestWorld, session: InstanceSession) {
        val owned = f.instances.npcsForInstance(session.id)
        assertEquals(MAIN_ACTORS, owned.map { it.type.id }.toSet())
        assertEquals(5, owned.size)
        assertEquals(owned.toSet(), f.npcs.toSet())
        assertTrue(session.regionIds.isEmpty())
        owned.forEach {
            assertEquals(session.id, f.instances.instanceForNpc(it))
            assertEquals(expectedHitpoints(it), it.hitpoints)
        }
    }

    private fun expectedHitpoints(npc: Npc) =
        if (npc.type.id == 11278) NEX_MAX_HITPOINTS else NEX_BODYGUARD_HITPOINTS

    private fun assertNoRegionWork(f: NexMinionTestWorld) {
        assertTrue(
            mockingDetails(f.instanceRegions).invocations.none {
                it.method.name in setOf("add", "protect", "unprotect")
            }
        )
    }

    /** Uses the established M26 native final-death setup and unchanged reset deadline. */
    private fun completeRound(f: NexMinionTestWorld) {
        f.reachPhase(NexState.ZAROS)
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        boss.hitpoints = 1
        f.damage(boss, 1)
        var waited = 0
        while (encounter.currentPhaseName != "RESETTING" && waited++ < 40) f.tick()
        assertEquals("RESETTING", encounter.currentPhaseName)
        assertTrue(boss.hidden)
        f.tick(NEX_RESPAWN_TICKS - 1)
        assertFalse(boss.isSlotAssigned)
    }

    private fun encounterLocs(f: NexMinionTestWorld): List<LocInfo> =
        listOf(f.player.coords, assertNotNull(f.partner).coords)
            .flatMap { center ->
                (-1..1).flatMap { x ->
                    (-1..1).flatMap { z ->
                        f.locs
                            .findAll(center.translate(x, z))
                            .filter { it.id in 42942..42944 }
                            .toList()
                    }
                }
            }
            .distinct()

    private fun assertLocsGone(f: NexMinionTestWorld, locs: List<LocInfo>) {
        locs.forEach {
            assertNull(
                f.locs.findExact(it.coords, assertNotNull(ServerCacheManager.getObject(it.id)))
            )
            assertEquals(
                0,
                f.collision[it.coords.x, it.coords.z, it.coords.level] and CollisionFlag.LOC,
            )
        }
    }

    private fun replacement(original: Player, f: NexMinionTestWorld, sameUid: Boolean) =
        Player().apply {
            slotId = original.slotId
            uuid = if (sameUid) original.uuid else 999L
            observerUUID = 999L
            assignUid()
            coords = original.coords
            currentMapClock = f.clock.cycle
            processedMapClock = f.clock.cycle
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }

    private fun deathContext(player: Player) =
        PlayerDeathContext(
            player,
            player.coords,
            false,
            0,
            false,
            true,
            false,
            false,
            false,
            0,
            null,
        )

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private val MAIN_ACTORS = setOf(11278, 11283, 11284, 11285, 11286)

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
