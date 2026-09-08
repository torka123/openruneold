package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class DoomRulesTest {
    @Test
    fun `delve combat scaling`() {
        assertEquals(525, DoomRules.hitpoints(1))
        assertEquals(675, DoomRules.hitpoints(8))
        assertEquals(625, DoomRules.hitpoints(14))
        assertEquals(6, DoomRules.attackRate(2))
        assertEquals(5, DoomRules.attackRate(6))
        assertEquals(4, DoomRules.attackRate(7))
        assertEquals(4, DoomRules.tongueAttackRate(1))
        assertEquals(4, DoomRules.tongueAttackRate(2))
        assertEquals(3, DoomRules.tongueAttackRate(3))
        assertEquals(3, DoomRules.tongueAttackRate(6))
        assertEquals(2, DoomRules.tongueAttackRate(7))
        assertEquals(2, DoomRules.tongueAttackRate(8))
        assertEquals(2, DoomRules.tongueAttackRate(9))
        assertEquals(65, DoomRules.standardMaxHit(8))
        assertEquals(99, DoomRules.beamMaxHit(6))
        assertEquals(55, DoomRules.burrowMaxHit(6))
        assertEquals(56, DoomRules.burrowMaxHit(7))
        assertEquals(58, DoomRules.burrowMaxHit(8))
        assertEquals(60, DoomRules.burrowMaxHit(9))
    }

    @Test
    fun `special scaling`() {
        assertEquals(1, DoomRules.rockProjectiles(1))
        assertEquals(2, DoomRules.rockProjectiles(3))
        assertEquals(3, DoomRules.rockProjectiles(8))
        assertEquals(2, DoomRules.rockSets(8))
        assertEquals(5, DoomRules.shockwaveSlams(8))
        assertEquals(2, DoomRules.carZooms(5))
        assertEquals(3, DoomRules.carZooms(6))
        assertEquals(2, DoomRules.outsideLarvaCount(7))
        assertEquals(3, DoomRules.outsideLarvaCount(8))
        assertEquals(4..4, DoomRules.acidTilesPerHit(8))
        assertEquals(6, DoomRules.meleePunishDelay(8))
        assertEquals(3, DoomRules.larvaReachDamage(8, demonicCharge = 0, giant = true))
        assertEquals(30, DoomRules.larvaHeal(demonicCharge = 0, giant = true))
        assertEquals(5, DoomRules.SHIELD_FIRST_LARVA_DELAY_TICKS)
        assertEquals(7..9, DoomRules.shieldLarvaIntervalRange(3))
        assertEquals(7..9, DoomRules.shieldLarvaIntervalRange(4))
        assertEquals(5..7, DoomRules.shieldLarvaIntervalRange(5))
        assertEquals(5..7, DoomRules.shieldLarvaIntervalRange(6))
        assertEquals(4..6, DoomRules.shieldLarvaIntervalRange(7))
        assertEquals(4..6, DoomRules.shieldLarvaIntervalRange(8))
        assertEquals(3, DoomRules.preShockwaveAttacks(6))
        assertEquals(4, DoomRules.preShockwaveAttacks(7))
        assertEquals(32, DoomRules.HOLY_WATER_HITPOINTS)
        assertEquals(24, DoomRules.HOLY_WATER_PRAYER)
        assertEquals(250, DoomRules.HOLY_WATER_SPECIAL_ENERGY)
    }

    @Test
    fun `timeline matches live mechanic delays`() {
        assertEquals(107, DoomCombatTimeline.rockExplosionCycle(100))
        assertEquals(109, DoomCombatTimeline.rockShadowImpactCycle(107))
        assertEquals(115, DoomCombatTimeline.shockwaveStartCycle(100))
        assertEquals(106, DoomCombatTimeline.postCarShockwavePreparationCycle(100))
        assertEquals(122, DoomCombatTimeline.shockwaveChargeCycle(120))
        assertEquals(
            listOf(204, 206, 208, 210, 212),
            DoomCombatTimeline.shockwaveSlamCycles(200, 8),
        )
        assertEquals(
            listOf(206, 208, 210, 212, 214),
            DoomCombatTimeline.shockwavePulseCycles(200, 8),
        )
        assertEquals(104, DoomCombatTimeline.beamImpactCycle(100, 4))
        assertEquals(101, DoomCombatTimeline.beamImpactCycle(100, 0))
        assertEquals(113, DoomCombatTimeline.beamFireCycle(100, DoomState.MELEE_CHARGE))
        assertEquals(117, DoomCombatTimeline.beamFireCycle(100, DoomState.SHIELDED))
        assertEquals(120, DoomCombatTimeline.beamFireCycle(100, DoomState.BURROWING))
        assertEquals(102, DoomCombatTimeline.shieldActiveCycle(100))
        assertEquals(105, DoomCombatTimeline.firstShieldLarvaSpawnCycle(100))
        assertEquals(101, DoomCombatTimeline.larvaMovementCycle(100))
        assertEquals(102, DoomCombatTimeline.larvaAttackableCycle(100))
        assertEquals(102, DoomCombatTimeline.larvaActiveCycle(100))
        assertEquals(8, DoomRules.ROCK_DEBRIS_COUNT)
        assertEquals(listOf(3), DoomRules.rockFollowupTravelTicks(1))
        assertEquals(listOf(3, 4), DoomRules.rockFollowupTravelTicks(3))
        assertEquals(listOf(3, 4, 5), DoomRules.rockFollowupTravelTicks(6))
        assertEquals(listOf(1, 2, 3), DoomRules.rockFollowupTravelTicks(7))
        assertEquals(listOf(1, 2, 3), DoomRules.rockFollowupTravelTicks(8))

        val blueBallImpact = DoomCombatTimeline.beamImpactCycle(100, 4)
        assertEquals(103, DoomCombatTimeline.queueCycleForImpact(blueBallImpact))
        assertEquals(
            100,
            DoomCombatTimeline.queueCycleForImpact(DoomCombatTimeline.beamImpactCycle(100, 0)),
        )
    }

    @Test
    fun `encounter activation and floor persistence`() {
        assertFalse(DoomActivationRules.shouldWake(false, false, false))
        assertTrue(DoomActivationRules.shouldWake(true, false, false))
        assertTrue(DoomActivationRules.shouldWake(false, true, false))
        assertTrue(DoomActivationRules.shouldWake(false, false, true))
        assertTrue(DoomPersistenceRules.shouldCarryAcidInto(5))
        assertFalse(DoomPersistenceRules.shouldCarryAcidInto(6))
        assertTrue(DoomPersistenceRules.shouldCarryAcidInto(7))
        assertFalse(DoomPersistenceRules.shouldClearAcidAtCarStart(5))
        assertTrue(DoomPersistenceRules.shouldClearAcidAtCarStart(6))
        assertFalse(DoomPersistenceRules.shouldClearAcidAtCarStart(7))
    }

    @Test
    fun `stationary boss can retaliate from every arena and entry tile`() {
        val bossSouthWest = CoordGrid(29, 35, 0)
        val requiredTiles = buildList {
            add(CoordGrid(31, 23, 0))
            add(CoordGrid(31, 30, 0))
            for (x in 22..40) {
                for (z in 28..46) add(CoordGrid(x, z, 0))
            }
        }

        requiredTiles.forEach { tile ->
            assertTrue(
                bossSouthWest.chebyshevDistance(tile) <= DoomRules.BOSS_ARENA_MAX_OP_RANGE,
                "Doom must retain its player interaction at $tile.",
            )
        }
        assertTrue(DoomRules.BOSS_ARENA_ATTACK_RANGE >= 12)
        assertFalse(
            DoomRules.BOSS_ATTACK_REQUIRES_LINE_OF_SIGHT,
            "Temporary rubble and arena scenery must not create a ranged safe spot.",
        )
    }

    @Test
    fun `reward scaling`() {
        assertEquals(50, DoomRules.rewardQuantityPercent(1))
        assertEquals(65, DoomRules.rewardQuantityPercent(2))
        assertEquals(100, DoomRules.rewardQuantityPercent(3))
        assertEquals(105, DoomRules.rewardQuantityPercent(4))
        assertEquals(117, DoomRules.rewardQuantityPercent(8))
        assertEquals(120, DoomRules.rewardQuantityPercent(9))
        assertEquals(2, DoomRules.scaleRewardQuantity(1, 3))
        assertEquals(3, DoomRules.scaleRewardQuantity(4, 3))
        assertEquals(100, DoomRules.guaranteedTears(8))
    }

    @Test
    fun `moving shield protects exactly its visible three by three footprint`() {
        val shieldSouthWest = CoordGrid(104, 100, 0)
        for (x in 104..106) {
            for (z in 100..102) {
                assertTrue(
                    DoomShieldGeometry.protects(
                        shieldSouthWest,
                        shieldSize = 3,
                        CoordGrid(x, z, 0),
                    )
                )
            }
        }
        assertFalse(
            DoomShieldGeometry.protects(shieldSouthWest, 3, CoordGrid(103, 101, 0))
        )
        assertFalse(
            DoomShieldGeometry.protects(shieldSouthWest, 3, CoordGrid(107, 101, 0))
        )
        assertFalse(
            DoomShieldGeometry.protects(shieldSouthWest, 3, CoordGrid(105, 106, 0))
        )
        assertFalse(
            DoomShieldGeometry.protects(shieldSouthWest, 3, CoordGrid(105, 101, 1))
        )
    }
}
