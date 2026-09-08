package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class ToaZebakEncounterTest {
    @Test
    fun `completed Crondis challenge still permits Zebak to target raid players`() {
        assertTrue(
            CrondisZebakParticipationRules.permitsRoom(ToaRoom.ZEBAK, ToaChallengeStage.COMPLETE)
        )
        assertTrue(CrondisZebakParticipationRules.permitsRoom(ToaRoom.ZEBAK, null))
        assertTrue(
            CrondisZebakParticipationRules.permitsRoom(ToaRoom.CRONDIS, ToaChallengeStage.ACTIVE)
        )
        assertFalse(
            CrondisZebakParticipationRules.permitsRoom(ToaRoom.CRONDIS, ToaChallengeStage.COMPLETE)
        )
        assertFalse(
            CrondisZebakParticipationRules.permitsRoom(ToaRoom.LOBBY, ToaChallengeStage.ACTIVE)
        )
        assertTrue(
            CrondisZebakParticipationRules.permitsPosition(
                ToaRoom.ZEBAK,
                insideChallengeBounds = false,
                insideZebakArena = true,
            )
        )
        assertFalse(
            CrondisZebakParticipationRules.permitsPosition(
                ToaRoom.ZEBAK,
                insideChallengeBounds = true,
                insideZebakArena = false,
            )
        )
        assertTrue(
            CrondisZebakParticipationRules.permitsPosition(
                ToaRoom.CRONDIS,
                insideChallengeBounds = true,
                insideZebakArena = false,
            )
        )
        assertTrue(
            CrondisZebakParticipationRules.permitsTracking(
                ToaRoom.ZEBAK,
                ToaChallengeStage.COMPLETE,
                participantRoom = ToaRoom.ZEBAK,
                connected = true,
                spectating = false,
            )
        )
        assertFalse(
            CrondisZebakParticipationRules.permitsTracking(
                ToaRoom.ZEBAK,
                ToaChallengeStage.COMPLETE,
                participantRoom = ToaRoom.ZEBAK,
                connected = false,
                spectating = false,
            )
        )
        assertTrue(
            CrondisZebakParticipationRules.permitsStateRetention(
                ToaRoom.ZEBAK,
                ToaChallengeStage.COMPLETE,
                participantRoom = ToaRoom.ZEBAK,
                spectating = false,
            )
        )
        assertTrue(
            CrondisZebakParticipationRules.permitsZebakCombatTarget(
                insideZebakArena = false,
                swimming = true,
            )
        )
        assertFalse(
            CrondisZebakParticipationRules.permitsZebakCombatTarget(
                insideZebakArena = false,
                swimming = false,
            )
        )
    }

    @Test
    fun `revision 240 Zebak assets remain centralized and paired`() {
        assertEquals(11730, ZebakAssets.BODY)
        assertEquals(11731, ZebakAssets.TAIL)
        assertEquals(11732, ZebakAssets.ENRAGED_BODY)
        assertEquals(11735, ZebakAssets.JUG)
        assertEquals(11737, ZebakAssets.ROCK)
        assertEquals(11738, ZebakAssets.WAVE)
        assertEquals(14, ZebakAssets.npcIds.size)
        assertEquals(15, ZebakAssets.validationNpcIds.size)
        assertTrue(ZebakAssets.SPLIT_HELPER in ZebakAssets.validationNpcIds)
        assertEquals((45570..45576).toList(), ZebakAssets.ACID_LOCS)
        assertTrue(ZebakAssets.BODY_BLOCKER_LOC in ZebakAssets.locIds)
        assertTrue(ZebakAssets.LIVE_ROCK_BLOCKER_LOC in ZebakAssets.locIds)
        assertTrue(ZebakAssets.COLLAPSED_ROCK_LOC in ZebakAssets.locIds)
        assertEquals(45509, ZebakAssets.ROCK_STEPS)
        assertTrue(ZebakAssets.ROCK_STEPS in ZebakAssets.locIds)
        assertEquals(163, ZebakAssets.CLIMB_SPLASH)
        assertTrue(ZebakAssets.CLIMB_SPLASH in ZebakAssets.spotanimIds)
        assertEquals(
            mapOf(
                9618 to 9619,
                9620 to 9621,
                9622 to 9623,
                9624 to 9625,
                9626 to 9627,
                9628 to 9629,
                9630 to 9631,
                9632 to 9633,
                9634 to 9635,
                9636 to 9637,
            ),
            ZebakAnimationPairs.all(),
        )
    }

    @Test
    fun `both revision 240 Rock steps route swimmers onto safe interior tiles`() {
        val south = requireNotNull(ZebakRockSteps.routeFor(29, 21))
        val north = requireNotNull(ZebakRockSteps.routeFor(29, 43))
        assertEquals(30, south.landingLocalX)
        assertEquals(23, south.landingLocalZ)
        assertEquals(30, north.landingLocalX)
        assertEquals(41, north.landingLocalZ)
        assertTrue(ZebakRoomLayout.contains(south.landingLocalX, south.landingLocalZ))
        assertTrue(ZebakRoomLayout.contains(north.landingLocalX, north.landingLocalZ))
        assertEquals(null, ZebakRockSteps.routeFor(29, 32))
    }

    @Test
    fun `blood magic healing is derived from damage that actually landed`() {
        assertEquals(0, ZebakInvocationRules.healAmount(0, arterialHealing = false))
        assertEquals(5, ZebakInvocationRules.healAmount(8, arterialHealing = false))
        assertEquals(8, ZebakInvocationRules.healAmount(8, arterialHealing = true))
    }

    @Test
    fun `one large hit queues every crossed threshold once and preserves alternation`() {
        val state = ZebakEncounter(UUID(1, 1), 42)
        state.state = ZebakState.NORMAL
        state.firstSpecial = ZebakSpecial.WAVES
        assertEquals(listOf(85, 70, 55, 40), state.queueCrossedThresholds(540, 0, 540))
        val order = mutableListOf<ZebakSpecial>()
        while (state.pendingThresholds.isNotEmpty()) {
            order += state.nextSpecial()
            state.pendingThresholds.removeFirst()
        }
        assertEquals(
            listOf(ZebakSpecial.WAVES, ZebakSpecial.ROAR, ZebakSpecial.WAVES, ZebakSpecial.ROAR),
            order,
        )
        assertTrue(state.queueCrossedThresholds(540, 0, 540).isEmpty())
    }

    @Test
    fun `enrage discards queued threshold specials and reset invalidates old generation`() {
        val state = ZebakEncounter(UUID(1, 2), 99)
        state.state = ZebakState.NORMAL
        state.queueCrossedThresholds(540, 200, 540)
        assertTrue(state.pendingThresholds.isNotEmpty())
        state.discardThresholdsForEnrage()
        assertTrue(state.pendingThresholds.isEmpty())
        val generation = state.generation
        state.acidTiles += CoordGrid(10, 10, 0)
        state.waterTiles += CoordGrid(11, 10, 0)
        state.reset()
        assertEquals(generation + 1, state.generation)
        assertEquals(ZebakState.RESET, state.state)
        assertTrue(state.acidTiles.isEmpty())
        assertTrue(state.waterTiles.isEmpty())
    }

    @Test
    fun `path levels speed attacks and narrow the contiguous wave gap`() {
        assertEquals(70, ZebakTuning.BASE_DEFENCE)
        assertEquals(50, ZebakTuning.MINIMUM_DRAINED_DEFENCE)
        assertEquals(7, ZebakTuning.attackInterval(0, enraged = false))
        assertEquals(5, ZebakTuning.attackInterval(4, enraged = false))
        assertEquals(4, ZebakTuning.attackInterval(0, enraged = true))
        assertEquals(2, ZebakTuning.attackInterval(99, enraged = true))
        assertEquals(35, ZebakTuning.preparationTicks(0, enraged = false))
        assertEquals(25, ZebakTuning.preparationTicks(4, enraged = false))
        assertEquals(3, ZebakTuning.waveGap(0))
        assertEquals(2, ZebakTuning.waveGap(2))
        assertEquals(1, ZebakTuning.waveGap(4))
        assertEquals(6, ZebakTuning.jugCount(0))
        assertEquals(7, ZebakTuning.jugCount(1))
        assertEquals(8, ZebakTuning.jugCount(2))
        assertEquals(8, ZebakTuning.jugCount(99))
        assertEquals(2, ZebakTuning.rockCount(1))
        assertEquals(3, ZebakTuning.rockCount(2))
    }

    @Test
    fun `jug water and acid replace one another with Upset Stomach footprints`() {
        val center = CoordGrid(50, 50, 0)
        val acid = linkedSetOf<CoordGrid>()
        val water = linkedSetOf<CoordGrid>()
        assertEquals(25, ZebakGeometry.applyJugWater(center, false, acid, water).size)
        assertTrue(center in water)
        assertEquals(9, ZebakGeometry.applyAcid(center, false, acid, water).size)
        assertTrue(center in acid)
        assertFalse(center in water)
        acid.clear()
        water.clear()
        assertEquals(9, ZebakGeometry.applyJugWater(center, true, acid, water).size)
        assertEquals(25, ZebakGeometry.applyAcid(center, true, acid, water).size)
    }

    @Test
    fun `roar cover is behind the rock and no more than three tiles deep`() {
        val zebak = CoordGrid(0, 0, 0)
        val rock = CoordGrid(5, 0, 0)
        assertTrue(ZebakGeometry.isCovered(zebak, rock, CoordGrid(6, 0, 0)))
        assertTrue(ZebakGeometry.isCovered(zebak, rock, CoordGrid(8, 0, 0)))
        assertFalse(ZebakGeometry.isCovered(zebak, rock, CoordGrid(8, 1, 0)))
        assertFalse(ZebakGeometry.isCovered(zebak, rock, CoordGrid(9, 0, 0)))
        assertFalse(ZebakGeometry.isCovered(zebak, rock, CoordGrid(4, 0, 0)))
        assertFalse(ZebakGeometry.isCovered(zebak, rock, CoordGrid(7, 2, 0)))
        assertFalse(ZebakGeometry.isCovered(zebak, rock, CoordGrid(7, 0, 1)))
    }

    @Test
    fun `waves preserve their north and south movement directions`() {
        assertEquals(-1, ZebakWaveDirection.SOUTH.stepZ)
        assertEquals(1, ZebakWaveDirection.NORTH.stepZ)
    }

    @Test
    fun `native room anchors keep entry hazards and waves on the dry arena`() {
        assertEquals(37, ToaRoom.ZEBAK.fightLocalX)
        assertEquals(32, ToaRoom.ZEBAK.fightLocalZ)
        assertEquals(37, ZebakRoomLayout.ENTRY_LOCAL_X)
        assertEquals(32, ZebakRoomLayout.ENTRY_LOCAL_Z)
        assertEquals(14 to 28, ZebakRoomLayout.BODY_LOCAL_X to ZebakRoomLayout.BODY_LOCAL_Z)
        assertEquals(5 to 27, ZebakRoomLayout.TAIL_LOCAL_X to ZebakRoomLayout.TAIL_LOCAL_Z)
        assertEquals(
            30 to 32,
            ZebakRoomLayout.ARENA_CENTER_LOCAL_X to ZebakRoomLayout.ARENA_CENTER_LOCAL_Z,
        )
        assertTrue(ZebakRoomLayout.contains(37, 32))
        assertFalse(ZebakRoomLayout.contains(42, 32))
        assertEquals(19, ZebakRoomLayout.WAVE_MIN_LOCAL_X)
        assertEquals(39, ZebakRoomLayout.WAVE_MAX_LOCAL_X)
        assertEquals(21, ZebakRoomLayout.waveStartLocalZ(ZebakWaveDirection.NORTH))
        assertEquals(43, ZebakRoomLayout.waveStartLocalZ(ZebakWaveDirection.SOUTH))
        assertTrue(ZebakRoomLayout.waveHasPassed(ZebakWaveDirection.NORTH, 46))
        assertTrue(ZebakRoomLayout.waveHasPassed(ZebakWaveDirection.SOUTH, 18))
        assertFalse(ZebakRoomLayout.waveHasPassed(ZebakWaveDirection.NORTH, 45))
        assertFalse(ZebakRoomLayout.waveHasPassed(ZebakWaveDirection.SOUTH, 19))
    }

    @Test
    fun `Zebak entry emits its first ordinary attack ten ticks after activation`() {
        assertEquals(3, ZebakTuning.EMERGE_TICKS)
        assertEquals(110, ZebakTimeline.firstAutoAt(100))
        assertTrue(ZebakState.EMERGING !in setOf(ZebakState.NORMAL, ZebakState.ENRAGED))
    }

    @Test
    fun `Zebak living actors transform four ticks after death starts`() {
        assertEquals(4, ZebakTuning.DEATH_TRANSFORM_TICKS)
        assertEquals(204, ZebakTimeline.deathTransformAt(200))
    }

    @Test
    fun waveAndHazardTuningKeepsOrderedReleasesAndBoundedRamping() {
        assertEquals(listOf(100, 107, 114), ZebakTimeline.waveReleaseTicks(100))
        assertEquals(8, ZebakTuning.JUG_ROLL_STEPS)
        assertEquals(4, ZebakTuning.WAVE_CALL_DELAY)
        assertEquals(101, ZebakTimeline.hazardLaunchAt(100))
        assertEquals(106, ZebakTimeline.hazardLandAt(100))
        assertEquals(5, ZebakTimeline.hazardLandAt(100) - ZebakTimeline.hazardLaunchAt(100))
        assertEquals(62, ZebakTuning.HAZARD_PROJECTILE_START_HEIGHT)
        assertEquals(0, ZebakTuning.HAZARD_PROJECTILE_END_HEIGHT)
        assertEquals(30, ZebakTuning.HAZARD_PROJECTILE_START_CYCLES)
        assertEquals(150, ZebakTuning.HAZARD_PROJECTILE_END_CYCLES)
        assertEquals(35, ZebakTuning.HAZARD_PROJECTILE_ANGLE)
        assertEquals(100, ZebakTuning.HAZARD_PROJECTILE_PROGRESS)
        assertEquals(2184, ZebakAssets.ROAR_DUST)
        assertEquals(13, ZebakTuning.WAVE_FIRST_ROW_DELAY)
        assertEquals(14, ZebakTuning.WAVE_FIRST_AUTO_DELAY)
        assertEquals(33, ZebakTuning.ROAR_START_DELAY)
        assertEquals(4, ZebakTuning.ROAR_FIRST_PULSE_DELAY)
        assertEquals(4, ZebakTuning.ACID_TICK_INTERVAL)
        assertEquals(4, ZebakTuning.ACID_BASE_DAMAGE)
        assertEquals(20, ZebakTuning.ACID_DAMAGE_CAP)
        assertEquals(2, ZebakTuning.BLOOD_CLOUD_DECAY)
        assertEquals(4, ZebakTuning.INITIAL_PROJECTILE_TICKS)
        assertEquals(90, ToaProjectileTiming.clientCyclesForServerTicks(4))
        assertEquals(4, ZebakTuning.SPLIT_PROJECTILE_TICKS)
        assertEquals(90, ToaProjectileTiming.clientCyclesForServerTicks(4))
        val normalAcid = ZebakAcidLayout.centerLocalTiles(upsetStomach = false)
        val upsetAcid = ZebakAcidLayout.centerLocalTiles(upsetStomach = true)
        assertEquals(12, normalAcid.distinct().size)
        assertEquals(3, normalAcid.map { it.first }.distinct().size)
        assertEquals(4, normalAcid.map { it.second }.distinct().size)
        assertTrue(normalAcid.all { (x, z) -> ZebakRoomLayout.acidFootprintFits(x, z, 1) })
        assertTrue(upsetAcid.all { (x, z) -> ZebakRoomLayout.acidFootprintFits(x, z, 2) })
        assertFalse(ZebakRoomLayout.acidFootprintFits(21, 25, 1))
    }

    @Test
    fun `ranged and magic projectiles split and land on deterministic server ticks`() {
        assertEquals(204, ZebakTimeline.splitAt(200))
        assertEquals(208, ZebakTimeline.projectileImpactAt(200))
        assertEquals(207, ZebakTimeline.splitVisualRemoveAt(204))
        assertEquals(3, ZebakTuning.SPLIT_VISUAL_TICKS)
        assertEquals(11744, ZebakAssets.SPLIT_HELPER)
        assertEquals(7, ZebakRoomLayout.PROJECTILE_SPLIT_HELPER_SIZE)
        assertEquals(
            ZebakRoomLayout.PROJECTILE_SPLIT_LOCAL_X,
            ZebakRoomLayout.PROJECTILE_SPLIT_HELPER_LOCAL_X +
                ZebakRoomLayout.PROJECTILE_SPLIT_HELPER_SIZE / 2,
        )
        assertEquals(
            ZebakRoomLayout.PROJECTILE_SPLIT_LOCAL_Z,
            ZebakRoomLayout.PROJECTILE_SPLIT_HELPER_LOCAL_Z +
                ZebakRoomLayout.PROJECTILE_SPLIT_HELPER_SIZE / 2,
        )
        assertEquals(90, ToaProjectileTiming.clientCyclesForServerTicks(4))
        assertEquals(38, ZebakTuning.MELEE_MAX_HIT)
        assertEquals(16, ZebakTuning.MAGIC_MAX_HIT)
        assertEquals(16, ZebakTuning.RANGE_MAX_HIT)
        assertEquals(832, ZebakAssets.JUG_PUSH_ANIMATION)
        assertTrue(ZebakAssets.POISON_PROJECTILE in ZebakAssets.spotanimIds)
        assertTrue(ZebakAssets.BOULDER_PROJECTILE in ZebakAssets.spotanimIds)
        assertTrue(ZebakAssets.JUG_PROJECTILE in ZebakAssets.spotanimIds)
        assertEquals(2187, ZebakAssets.RANGE_SPLIT)
        assertEquals(131, ZebakAssets.MAGIC_IMPACT)
        assertEquals(1103, ZebakAssets.RANGE_IMPACT)
    }

    @Test
    fun `jug motion is one tile per tick for eight cardinal or diagonal steps`() {
        val start = CoordGrid(100, 100, 0)
        val east = ZebakJugMotion.unobstructedPath(start, 1, 0)
        val northWest = ZebakJugMotion.unobstructedPath(start, -1, 1)
        assertEquals(8, east.size)
        assertEquals(CoordGrid(108, 100, 0), east.last())
        assertEquals(CoordGrid(99, 101, 0), northWest.first())
        assertEquals(CoordGrid(92, 108, 0), northWest.last())
        assertEquals(501, ZebakTimeline.jugBreakAt(500))
        assertEquals(502, ZebakTimeline.jugAcidClearAt(500))
    }

    @Test
    fun `Great Roar emits three pulses two ticks apart and consumes one third rock health`() {
        assertEquals(listOf(704, 706, 708), ZebakTimeline.roarPulseTicks(700))
        assertEquals(150, ZebakTuning.ROCK_HITPOINTS)
        assertEquals(50, ZebakTuning.ROCK_ROAR_DAMAGE)
        assertEquals(
            listOf(100, 50, 0),
            (1..ZebakTuning.ROAR_PULSES).map {
                ZebakTuning.ROCK_HITPOINTS - it * ZebakTuning.ROCK_ROAR_DAMAGE
            },
        )
    }

    @Test
    fun `wave collision catches occupied and crossing tiles but not safe gaps`() {
        val previousWave = CoordGrid(30, 30, 0)
        val nextWave = CoordGrid(30, 31, 0)
        assertTrue(
            ZebakWaveCollision.crossed(previousWave, nextWave, CoordGrid(30, 32, 0), nextWave)
        )
        assertTrue(ZebakWaveCollision.crossed(previousWave, nextWave, nextWave, previousWave))
        assertFalse(
            ZebakWaveCollision.crossed(
                previousWave,
                nextWave,
                CoordGrid(31, 31, 0),
                CoordGrid(31, 30, 0),
            )
        )
        assertEquals(
            CoordGrid(30, 34, 0),
            ZebakWaveCollision.pushedTile(previousWave, ZebakWaveDirection.NORTH),
        )
    }
}
