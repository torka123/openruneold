package org.rsmod.content.bosses.muspah

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuspahRulesTest {
    @Test fun rangedCanStart() = assertEquals(MuspahForm.RANGED, MuspahRules.startingForm(0))

    @Test fun meleeCanStart() = assertEquals(MuspahForm.MELEE, MuspahRules.startingForm(1))

    @Test
    fun rangedStartUsesCloudsFirst() =
        assertEquals(MuspahSpecial.LIGHTNING_CLOUDS, MuspahRules.firstSpecial(MuspahForm.RANGED))

    @Test
    fun meleeStartUsesHomingFirst() =
        assertEquals(MuspahSpecial.HOMING_SPIKES, MuspahRules.firstSpecial(MuspahForm.MELEE))

    @Test
    fun rangedStartUsesHomingSecond() =
        assertEquals(MuspahSpecial.HOMING_SPIKES, MuspahRules.secondSpecial(MuspahForm.RANGED))

    @Test fun rangedThresholdIs100() = assertEquals(100, MuspahRules.swapThreshold(MuspahForm.RANGED))

    @Test fun meleeThresholdIs80() = assertEquals(80, MuspahRules.swapThreshold(MuspahForm.MELEE))

    @Test
    fun firstSwapOnlyNeedsRangedThreshold() =
        assertTrue(MuspahRules.canNormalSwap(MuspahForm.RANGED, false, 1, 100))

    @Test
    fun firstSwapOnlyNeedsMeleeThreshold() =
        assertTrue(MuspahRules.canNormalSwap(MuspahForm.MELEE, false, 1, 80))

    @Test
    fun laterRangedSwapNeedsFourPositiveAttacks() =
        assertFalse(MuspahRules.canNormalSwap(MuspahForm.RANGED, true, 3, 200))

    @Test
    fun laterRangedSwapNeedsDamageThreshold() =
        assertFalse(MuspahRules.canNormalSwap(MuspahForm.RANGED, true, 4, 99))

    @Test
    fun laterRangedSwapRequiresBothConditions() =
        assertTrue(MuspahRules.canNormalSwap(MuspahForm.RANGED, true, 4, 100))

    @Test
    fun laterMeleeSwapRequiresBothConditions() =
        assertTrue(MuspahRules.canNormalSwap(MuspahForm.MELEE, true, 4, 80))

    @Test
    fun zeroDoesNotCountAsPositiveAttack() =
        assertFalse(MuspahRules.shouldCountPositiveAttack(0, 100, 99))

    @Test
    fun twoSplatsFromOneAttackTickCountOnce() =
        assertFalse(MuspahRules.shouldCountPositiveAttack(20, 100, 100))

    @Test
    fun positiveDamageOnNewTickCounts() =
        assertTrue(MuspahRules.shouldCountPositiveAttack(1, 100, 99))

    @Test fun firstSpecialTriggersAt637() = assertTrue(MuspahRules.crossedFirstSpecial(637, false))

    @Test
    fun firstSpecialDoesNotRepeat() = assertFalse(MuspahRules.crossedFirstSpecial(600, true))

    @Test
    fun secondSpecialTriggersAt425() = assertTrue(MuspahRules.crossedSecondSpecial(425, false))

    @Test
    fun shieldDoesNotTriggerAt127() = assertFalse(MuspahRules.crossedShieldThreshold(127))

    @Test fun shieldTriggersAt126() = assertTrue(MuspahRules.crossedShieldThreshold(126))

    @Test fun homingLastsExactly34Ticks() = assertEquals(134, MuspahRules.homingEndCycle(100))

    @Test fun homingAliveOnTick33() = assertFalse(MuspahRules.homingExpired(133, 134))

    @Test fun homingEndsOnTick34() = assertTrue(MuspahRules.homingExpired(134, 134))

    @Test fun passiveShieldDrainIsTwo() = assertEquals(73, MuspahRules.drainShield(75, 2))

    @Test fun shieldNeverGoesNegative() = assertEquals(0, MuspahRules.drainShield(10, 33))

    @Test fun smiteUsesQuarterDamageFloor() = assertEquals(5, MuspahRules.smiteDrain(23))

    @Test fun zeroDamageCannotSmite() = assertEquals(0, MuspahRules.smiteDrain(0))

    @Test fun sapphireDrainIs33() = assertEquals(33, MuspahRules.sapphireDrain())

    @Test fun soulSplitRoundsDown() = assertEquals(11, MuspahRules.soulSplitHeal(23))

    @Test fun spikeHealRoundsDownAt75Percent() = assertEquals(8, MuspahRules.spikeHeal(11))

    @Test
    fun movingMuspahWithPrayerEnablesStepBack() =
        assertTrue(MuspahRules.meleeStepBackBlocks(true, true))

    @Test
    fun stationaryMuspahDoesNotEnableStepBack() =
        assertFalse(MuspahRules.meleeStepBackBlocks(false, true))

    @Test
    fun movementWithoutPrayerDoesNotEnableStepBack() =
        assertFalse(MuspahRules.meleeStepBackBlocks(true, false))

    @Test fun stationaryDamageCapsAt34() = assertEquals(34, MuspahRules.meleeMaximum(99))

    @Test fun firstStationaryDamageStartsAt8() = assertEquals(8, MuspahRules.meleeMaximum(0))

    @Test
    fun homingStepNeverMovesDiagonally() {
        val next = MuspahRules.nextOrthogonalStep(10, 10, 15, 15)
        assertTrue(next.first == 10 || next.second == 10)
    }

    @Test
    fun homingStepMovesOneTile() {
        val next = MuspahRules.nextOrthogonalStep(10, 10, 15, 12)
        assertEquals(Pair(11, 10), next)
    }

    @Test fun zeroTicksFormats() = assertEquals("0.0 seconds", MuspahRules.formatTicks(0))

    @Test fun oneHundredTicksFormatsMinute() = assertEquals("1:00.0", MuspahRules.formatTicks(100))
}
