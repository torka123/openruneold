package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/** Behavioral oracle: verified pre-migration runtime at 9b5815be6, not additional OSRS claims. */
@OptIn(InternalApi::class)
class ZebakNativePhaseTest {
    @Test
    fun `native roar graph preserves preparation pulse snapshots first jug break and recovery`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = prepare(f, player, ZebakSpecial.ROAR)
            val state = raid.zebak
            val phases = assertNotNull(state.phases)
            val body = assertNotNull(state.body)
            val prepared = f.clock.cycle
            assertEquals(0, f.bossEncounters.of(body).phaseAbilityCount)
            val rocks = mutableListOf<ZebakRockRuntime>()
            for (offset in 1..45) {
                f.dispatchLateCycle(prepared + offset)
                val expectedPhase =
                    when {
                        offset < 33 -> ZebakPhase.PREP_ROAR
                        offset < 37 -> ZebakPhase.ROAR_START
                        offset < 39 -> ZebakPhase.ROAR_PULSE_1
                        offset < 41 -> ZebakPhase.ROAR_PULSE_2
                        offset < 45 -> ZebakPhase.ROAR_PULSE_3
                        else -> ZebakPhase.NORMAL
                    }
                assertEquals(expectedPhase, phases.phase, "offset=$offset")
                assertTrue(body.movementLocked)
                if (offset < 6) {
                    assertTrue(state.rocks.isEmpty())
                    assertTrue(state.jugs.isEmpty())
                } else if (offset < 45) {
                    if (offset == 6) rocks += state.rocks.values
                    assertEquals(2, state.rocks.size)
                    val pulse = expectedPhase.roarPulse
                    assertEquals(List(2) { 150 - pulse * 50 }, rocks.map { it.hitpoints })
                    assertEquals(offset < 37, state.jugs.isNotEmpty(), "first pulse breaks jugs")
                    if (offset >= 41) {
                        assertEquals(List(2) { prepared + 45 }, rocks.map { it.destroysAt })
                        assertTrue(rocks.all { it.npc.isSlotAssigned })
                    }
                }
            }
            assertTrue(state.rocks.isEmpty())
            assertTrue(rocks.all { !it.npc.isSlotAssigned })
            assertEquals(prepared + 48, body.actionDelay)
            assertTrue(f.bossEncounters.of(body).attackTracesAfter(0).isEmpty())
            assertFalse(phases.isClosed)
        }
    }

    @Test
    fun `wave phase graph preserves every row movement gap and shared RNG draw across paths`() {
        for (path in listOf(0, 2, 4, 6)) for (seed in listOf(5L, 37L)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = prepare(f, player, ZebakSpecial.WAVES, path, seed)
                val phases = assertNotNull(raid.zebak.phases)
                val prepared = f.clock.cycle
                val oracle = LegacyRandom(seed xor 0x5A4542414BL)
                oracle.nextInt(2) // Initial special selection precedes all fight rolls.
                oracle.nextInt(3) // Preparation jug count; no ordinary launch is due yet.
                val waves = mutableListOf<Pair<Int, Int>>()
                val gap = (3 - path / 2).coerceAtLeast(1)
                for (offset in 1..52) {
                    // Retained LateCycle order: existing rows move and roll before a new row.
                    val moved =
                        waves.mapNotNull { (x, z) ->
                            if (z + 1 > 45) null
                            else {
                                oracle.nextInt(4)
                                x to z + 1
                            }
                        }
                    waves.clear()
                    waves += moved
                    if (offset in listOf(13, 20, 27)) {
                        val gapStart = 19 + oracle.nextInt(21 - gap + 1)
                        waves +=
                            (19..39)
                                .filter { it !in gapStart until gapStart + gap }
                                .map { it to 21 }
                    }
                    f.dispatchLateCycle(prepared + offset)
                    val label = "path=$path seed=$seed offset=$offset"
                    val expectedPhase =
                        when {
                            offset < 4 -> ZebakPhase.PREP_WAVES
                            offset < 13 -> ZebakPhase.WAVE_START
                            offset < 20 -> ZebakPhase.WAVE_ROW_1
                            offset < 27 -> ZebakPhase.WAVE_ROW_2
                            offset < 52 -> ZebakPhase.WAVE_ROW_3
                            else -> ZebakPhase.NORMAL
                        }
                    assertEquals(expectedPhase, phases.phase, label)
                    assertEquals(
                        waves.map { (x, z) -> tile(f, raid, x, z) },
                        raid.zebak.waves.values.map { it.tile },
                        label,
                    )
                    assertEquals(expectedPhase.waveStage, raid.zebak.waveStage, label)
                    if (offset == 6) {
                        assertTrue(raid.zebak.jugs.isNotEmpty(), "Landing survives call at +4")
                        assertTrue(raid.zebak.rocks.isEmpty())
                    }
                }
                assertEquals(oracle.nextInt(1_000_003), raid.zebak.nextInt(1_000_003))
                assertEquals(prepared + 55, assertNotNull(raid.zebak.body).actionDelay)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `paused raid and default world queues cannot advance overdue hazards or multiple phase edges`() {
        for (special in ZebakSpecial.entries) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = prepare(f, player, special)
                val phases = assertNotNull(raid.zebak.phases)
                val prepared = f.clock.cycle
                val participant = assertNotNull(raid.participant(player.uid))
                participant.connected = false
                f.advance(100)
                f.queueProcess.process()
                f.dispatchLateCycle()
                assertEquals(
                    if (special == ZebakSpecial.ROAR) ZebakPhase.PREP_ROAR
                    else ZebakPhase.PREP_WAVES,
                    phases.phase,
                )
                assertTrue(projectiles(f).isEmpty())
                assertTrue(raid.zebak.jugs.isEmpty())
                participant.connected = true
                f.dispatchLateCycle()
                val resumed = prepared + 100
                assertTrue(projectiles(f).isNotEmpty(), "Both overdue hazard entries run on resume")
                assertTrue(raid.zebak.jugs.isNotEmpty())
                assertEquals(
                    if (special == ZebakSpecial.ROAR) ZebakPhase.ROAR_START
                    else ZebakPhase.WAVE_START,
                    phases.phase,
                )
                val nextDelay = if (special == ZebakSpecial.ROAR) 4 else 9
                assertEquals(resumed + nextDelay, phases.deadline)
                f.dispatchLateCycle(resumed + nextDelay - 1)
                assertEquals(0, phases.phase.roarPulse + phases.phase.waveStage)
                f.dispatchLateCycle(resumed + 200)
                assertEquals(1, phases.phase.roarPulse + phases.phase.waveStage)
                val interval = if (special == ZebakSpecial.ROAR) 2 else 7
                assertEquals(resumed + 200 + interval, phases.deadline)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `overdue hazard launch and landing precede existing split fanout at the same LateCycle`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = prepare(f, player, ZebakSpecial.ROAR)
            val prepared = f.clock.cycle
            val body = assertNotNull(raid.zebak.body)
            assertNotNull(raid.participant(player.uid)).spectating = false
            // The retained API is due now while the special's phase/hazard queues stay pending.
            body.actionDelay = prepared
            f.bossEncounters.of(body).lastAbilityTick = prepared - 7
            f.dispatchLateCycle()
            assertEquals(1, raid.zebak.splitProjectiles.size)
            val launch = projectiles(f).single()
            f.advance(6)
            f.queueProcess.process()
            assertEquals(listOf(launch), projectiles(f))
            f.dispatchLateCycle()
            val packets = projectiles(f)
            val hazardPackets = packets.drop(1).dropLast(1)
            assertTrue(hazardPackets.isNotEmpty())
            assertTrue(hazardPackets.all { it.targetIndex == 0 })
            assertEquals(-(player.slotId + 1), packets.last().targetIndex)
            assertTrue(raid.zebak.rocks.isNotEmpty())
            assertTrue(raid.zebak.jugs.isNotEmpty())
            assertEquals(prepared + 8, raid.encounter.pendingHits.single().landsAt)
        }
    }

    @Test
    fun `enrage cancels preparation hazards but preserves called-wave hazards and split owners`() {
        for (callStarted in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = prepare(f, player, ZebakSpecial.WAVES)
                val state = raid.zebak
                val phases = assertNotNull(state.phases)
                val splits = assertNotNull(state.attackQueues)
                val prepared = f.clock.cycle
                if (callStarted) f.dispatchLateCycle(prepared + 4)
                assertNotNull(state.body).hitpoints = assertNotNull(state.body).baseHitpointsLvl / 4
                f.dispatchLateCycle(prepared + 5)
                assertEquals(ZebakPhase.ENRAGED, phases.phase)
                assertFalse(splits.isClosed)
                f.dispatchLateCycle(prepared + 6)
                assertEquals(callStarted, state.jugs.isNotEmpty(), "callStarted=$callStarted")
                f.dispatchLateCycle(prepared + 40)
                assertEquals(ZebakPhase.ENRAGED, phases.phase)
                assertTrue(state.waves.isEmpty(), "Cancelled phase edge cannot create a row")
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `death deletion reset and session termination retire pending phase and hazard owners`() {
        for (retirement in listOf("death", "delete", "reset", "terminate")) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = prepare(f, player, ZebakSpecial.ROAR)
                val state = raid.zebak
                val phases = assertNotNull(state.phases)
                val body = assertNotNull(state.body)
                assertEquals(3, f.worldQueues.size)
                when (retirement) {
                    "death" -> f.manager.handleNpcKill(player, body)
                    "delete" -> f.npcs.del(body, Int.MAX_VALUE)
                    "reset" -> f.enterZebak(player)
                    "terminate" -> f.instances.terminate(raid.instance)
                }
                assertTrue(phases.isClosed, retirement)
                assertEquals(if (retirement == "reset") 1 else 0, f.worldQueues.size, retirement)
                f.advance(100)
                f.queueProcess.process()
                phases.processHazards()
                assertNull(phases.advance())
                assertTrue(projectiles(f).isEmpty(), retirement)
                if (retirement == "death") {
                    assertEquals(ZebakState.DYING, state.state)
                    // Completion remains captured by the raid session after native NPC deletion.
                    f.npcs.del(body, Int.MAX_VALUE)
                    assertSame(player, f.crondisZebak.tickZebak(raid))
                    assertEquals(ZebakState.COMPLETE, state.state)
                }
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `last native logout pauses emergence and rejoin retains the original boss and phase owner`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            f.enterZebak(player)
            val body = assertNotNull(raid.zebak.body)
            val phases = assertNotNull(raid.zebak.phases)
            val encounter = f.bossEncounters.of(body)
            f.events.publish(SessionStateEvent.Logout(player))
            f.advance(20)
            f.queueProcess.process()
            f.dispatchLateCycle()
            assertEquals(ZebakPhase.EMERGING, phases.phase)
            assertTrue(raid.instance.occupants.isEmpty())
            f.players.remove(player.slotId)
            val replacement =
                f.player(9).apply {
                    uuid = player.uuid
                    accountHash = player.accountHash
                    accountId = player.accountId
                    characterId = player.characterId
                    assignUid()
                }
            assertNotEquals(player.uid, replacement.uid)
            assertTrue(f.manager.openLobby(replacement))
            assertSame(raid, f.manager.raidFor(replacement))
            assertTrue(assertNotNull(raid.participant(replacement.uid)).connected)
            assertTrue(body.isSlotAssigned, "Empty raid rejoin must preserve its retained boss")
            assertSame(body, raid.zebak.body)
            assertSame(encounter, f.bossEncounters.existing(body))
            assertFalse(phases.isClosed, "Rejoin must not retire the retained native phase owner")
            f.dispatchLateCycle()
            assertEquals(ZebakPhase.NORMAL, phases.phase)
            assertTrue(encounter.attackTracesAfter(0).isEmpty())
        }
    }

    private fun prepare(
        f: ToaNativeCombatFixture,
        player: Player,
        special: ZebakSpecial,
        path: Int = 0,
        seed: Long = 42,
    ): ToaRaidSession {
        val raid = f.start(player, seed = seed)
        raid.pathLevels[ToaPath.CRONDIS] = path
        f.enterZebak(player)
        PathingEntityCommon.telejump(player, f.collision, tile(f, raid, 37, 32))
        f.dispatchLateCycle(raid.zebak.emergesAt)
        val body = assertNotNull(raid.zebak.body)
        raid.zebak.firstSpecial = special
        f.hitNpc(player, body, body.baseHitpointsLvl * 16 / 100)
        f.dispatchLateCycle()
        assertEquals(
            if (special == ZebakSpecial.ROAR) ZebakState.PREP_ROAR else ZebakState.PREP_WAVES,
            raid.zebak.state,
        )
        // Connected spectators preserve native raid ticking but remove unrelated player damage
        // rolls.
        assertNotNull(raid.participant(player.uid)).spectating = true
        return raid
    }

    private fun tile(f: ToaNativeCombatFixture, raid: ToaRaidSession, x: Int, z: Int): CoordGrid =
        assertNotNull(
            f.instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(ToaRoom.ZEBAK, x, z))
        )

    private fun projectiles(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world).invocations.flatMap { it.arguments.filterIsInstance<ProjAnim>() }

    private class LegacyRandom(private var state: Long) {
        fun nextInt(bound: Int): Int {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            return ((state and Long.MAX_VALUE) % bound).toInt()
        }
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
