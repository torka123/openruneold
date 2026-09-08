package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class Dt2RulesTest {
    @Test
    fun normalWhispererThresholdsMatchCurrentFight() =
        assertEquals(listOf(720, 495, 270), Dt2Rules.whispererThresholds(Dt2Difficulty.NORMAL))

    @Test
    fun awakenedWhispererHasSevenSpecialThresholds() =
        assertEquals(7, Dt2Rules.whispererThresholds(Dt2Difficulty.AWAKENED).size)

    @Test
    fun whispererFirstRotationIsStable() =
        assertEquals(WhispererSpecial.CORRUPTED_SEEDS, Dt2Rules.whispererSpecial(0, 0))

    @Test
    fun whispererEnrageIsTwoTicks() =
        assertEquals(2, Dt2Rules.whispererAttackRate(3, enraged = true))

    @Test
    fun normalVardorvisAxesScaleAtCapturedThresholds() {
        assertEquals(1, Dt2Rules.vardorvisAxeCount(691, false))
        assertEquals(2, Dt2Rules.vardorvisAxeCount(690, false))
        assertEquals(3, Dt2Rules.vardorvisAxeCount(230, false))
    }

    @Test
    fun awakenedVardorvisAddsAnAxe() {
        assertEquals(2, Dt2Rules.vardorvisAxeCount(1_400, true))
        assertEquals(4, Dt2Rules.vardorvisAxeCount(461, true))
    }

    @Test
    fun leviathanTierUsesCapturedPerTierVolleyLengths() {
        assertEquals(0, Dt2Rules.leviathanVolleyTier(5))
        assertEquals(1, Dt2Rules.leviathanVolleyTier(6))
        assertEquals(1, Dt2Rules.leviathanVolleyTier(13))
        assertEquals(2, Dt2Rules.leviathanVolleyTier(14))
        assertEquals(6, Dt2Rules.leviathanVolleyTier(54))
        assertTrue(Dt2Rules.leviathanVolleyComplete(6))
        assertTrue(Dt2Rules.leviathanVolleyComplete(14))
        assertTrue(Dt2Rules.leviathanVolleyComplete(22))
        assertTrue(!Dt2Rules.leviathanVolleyComplete(12))
        assertEquals(0, Dt2Rules.leviathanInitialAttackCounter(awakened = false))
        assertEquals(6, Dt2Rules.leviathanInitialAttackCounter(awakened = true))
    }

    @Test
    fun leviathanStyleSetsMatchTheSevenLiveVolleyTiers() {
        assertEquals(2, Dt2Rules.leviathanStyles(0).size)
        assertEquals(2, Dt2Rules.leviathanStyles(1).size)
        assertEquals(3, Dt2Rules.leviathanStyles(2).size)
        assertEquals(2, Dt2Rules.leviathanStyles(3).size)
        assertEquals(3, Dt2Rules.leviathanStyles(4).size)
        assertEquals(3, Dt2Rules.leviathanStyles(5).size)
        assertEquals(3, Dt2Rules.leviathanStyles(6).size)
    }

    @Test
    fun leviathanSpecialsAlternateDeterministically() =
        assertEquals(LeviathanSpecial.LIGHTNING_BARRAGE, Dt2Rules.leviathanSpecial(0)).also {
            assertEquals(LeviathanSpecial.SMOKE_BLAST, Dt2Rules.leviathanSpecial(1))
            assertEquals(LeviathanSpecial.LIGHTNING_BARRAGE, Dt2Rules.leviathanSpecial(2))
        }

    @Test
    fun leviathanOnlyStartsFromTheExactSouthWesternHandholds() {
        val handholds = CoordGrid(2070, 6368, 0)
        assertTrue(Dt2Rules.leviathanValidStart(47593, handholds, handholds))
        assertFalse(Dt2Rules.leviathanValidStart(47594, handholds, handholds))
        assertFalse(Dt2Rules.leviathanValidStart(47593, handholds.translate(1, 0), handholds))
        assertFalse(Dt2Rules.leviathanValidStart(47593, handholds, null))
    }

    @Test
    fun leviathanWalkInStartsOnlyAfterTheCapturedArenaThreshold() {
        val trigger = CoordGrid(2075, 6375, 0)
        assertFalse(Dt2Rules.leviathanCrossedCombatTrigger(CoordGrid(2071, 6368, 0), trigger))
        assertFalse(Dt2Rules.leviathanCrossedCombatTrigger(CoordGrid(2075, 6374, 0), trigger))
        assertTrue(Dt2Rules.leviathanCrossedCombatTrigger(trigger, trigger))
        assertTrue(Dt2Rules.leviathanCrossedCombatTrigger(CoordGrid(2076, 6378, 0), trigger))
        assertFalse(Dt2Rules.leviathanCrossedCombatTrigger(CoordGrid(2084, 6375, 0), trigger))
        assertFalse(Dt2Rules.leviathanCrossedCombatTrigger(CoordGrid(2075, 6375, 1), trigger))
    }

    @Test
    fun leviathanAttackContractsUseRevision240VisualsAndLiveMaximumHits() {
        val ranged = Dt2Rules.leviathanAttackContract(Dt2AttackStyle.RANGED, false, 0)
        assertEquals(10278, ranged.animation)
        assertEquals(2484, ranged.castGraphic)
        assertEquals(2487, ranged.projectileGraphic)
        assertEquals(2490, ranged.impactGraphic)
        assertEquals(24, ranged.maximumHit)

        val magic = Dt2Rules.leviathanAttackContract(Dt2AttackStyle.MAGIC, false, 0)
        assertEquals(2486, magic.castGraphic)
        assertEquals(2489, magic.projectileGraphic)
        assertEquals(2492, magic.impactGraphic)
        assertEquals(32, magic.maximumHit)

        val meleeOrb = Dt2Rules.leviathanAttackContract(Dt2AttackStyle.MELEE, false, 0)
        assertEquals(2485, meleeOrb.castGraphic)
        assertEquals(2488, meleeOrb.projectileGraphic)
        assertEquals(2491, meleeOrb.impactGraphic)
        assertEquals(50, meleeOrb.maximumHit)
    }

    @Test
    fun leviathanAdjacentBiteAlternatesAndNeverCreatesAProjectile() {
        val first = Dt2Rules.leviathanAttackContract(Dt2AttackStyle.MELEE, true, 0)
        val second = Dt2Rules.leviathanAttackContract(Dt2AttackStyle.MELEE, true, 1)
        assertEquals(10283, first.animation)
        assertEquals(10284, second.animation)
        assertEquals(null, first.castGraphic)
        assertEquals(null, first.projectileGraphic)
        assertEquals(null, first.impactGraphic)
    }

    @Test
    fun leviathanSpecialContractsPreserveCapturedSequenceTimings() {
        assertEquals(
            LeviathanSpecialContract(10282, null, null, 4),
            Dt2Rules.leviathanSpecialContract(LeviathanSpecial.VOLLEY_ROAR),
        )
        assertEquals(
            LeviathanSpecialContract(10285, 10286, 10287, 18),
            Dt2Rules.leviathanSpecialContract(LeviathanSpecial.LIGHTNING_BARRAGE),
        )
        assertEquals(
            LeviathanSpecialContract(10288, 10289, 10290, 16),
            Dt2Rules.leviathanSpecialContract(LeviathanSpecial.SMOKE_BLAST),
        )
    }

    @Test
    fun awakenedLeviathanEnrageIsOneTick() =
        assertEquals(1, Dt2Rules.leviathanAttackRate(6, enraged = true, awakened = true))

    @Test
    fun normalDukeNeedsTwoPoisons() =
        assertEquals(2, Dt2Rules.dukePoisonRequired(Dt2Difficulty.NORMAL))

    @Test
    fun awakenedDukeNeedsThreePoisons() =
        assertEquals(3, Dt2Rules.dukePoisonRequired(Dt2Difficulty.AWAKENED))

    @Test
    fun awakenedDukeUsesFourFlaresBelowSeventyFivePercent() =
        assertEquals(4, DukeRules.gasCount(1_200, true))

    @Test
    fun protectionNeverProducesNegativeDamage() = assertEquals(0, Dt2Rules.protectedDamage(50, 120))

    @Test
    fun seededRngIsReplayable() {
        val a = Dt2SeededRng(1234)
        val b = Dt2SeededRng(1234)
        repeat(20) { assertEquals(a.nextInt(100), b.nextInt(100)) }
    }

    @Test
    fun seededRngRemainsInBounds() {
        val rng = Dt2SeededRng(99)
        repeat(100) { assertTrue(rng.nextInt(7) in 0..6) }
    }
}
