package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.random.GameRandom

class YamaRewardRulesTest {
    @Test
    fun `contribution is capped and quantities scale linearly`() {
        val none = YamaRewardRules.contribution(-20, 2500)
        val half = YamaRewardRules.contribution(1250, 2500)
        val capped = YamaRewardRules.contribution(9000, 2500)

        assertEquals(0, none.cappedDamage)
        assertEquals(0.0, none.ratio)
        assertEquals(0.5, half.ratio)
        assertEquals(1.0, capped.ratio)
        assertEquals(6, YamaRewardRules.scaledQuantity(12, half))
        assertEquals(12, YamaRewardRules.scaledQuantity(12, capped))
        assertEquals(0, YamaRewardRules.scaledQuantity(12, none))
    }

    @Test
    fun `reward threshold requires both 375 damage and fifteen percent`() {
        assertFalse(YamaRewardRules.eligible(YamaRewardRules.contribution(374, 2500)))
        assertTrue(YamaRewardRules.eligible(YamaRewardRules.contribution(375, 2500)))
        assertFalse(YamaRewardRules.eligible(YamaRewardRules.contribution(375, 3000)))
        assertTrue(YamaRewardRules.eligible(YamaRewardRules.contribution(450, 3000)))
    }

    @Test
    fun `only normal and familiar fights retain the standard table`() {
        assertTrue(YamaRewardRules.allowsStandardTable(YamaContract.NONE))
        assertFalse(YamaRewardRules.allowsStandardTable(YamaContract.GLYPHIC))
        assertFalse(YamaRewardRules.allowsStandardTable(YamaContract.FORFEIT))
        assertFalse(YamaRewardRules.allowsStandardTable(YamaContract.OATHPLATE))
        assertFalse(YamaRewardRules.allowsStandardTable(YamaContract.CATALYST))
        assertTrue(YamaRewardRules.allowsStandardTable(YamaContract.FAMILIAR))
        assertFalse(YamaRewardRules.allowsStandardTable(YamaContract.CUSTOM))
    }

    @Test
    fun `familiar pet benefit is not dispatched again as a guaranteed contract reward`() {
        assertFalse(YamaRewardRules.grantsContractReward(YamaContract.NONE))
        assertFalse(YamaRewardRules.grantsContractReward(YamaContract.FAMILIAR))
        assertFalse(YamaRewardRules.grantsContractReward(YamaContract.CUSTOM))
        assertTrue(YamaRewardRules.grantsContractReward(YamaContract.GLYPHIC))
        assertTrue(YamaRewardRules.grantsContractReward(YamaContract.CATALYST))
    }

    @Test
    fun `junk roll preserves the three three three two weighting`() {
        assertEquals(List(3) { YAMA_JUNK_CABBAGE }, (0..2).map(YamaRewardRules::junkItemId))
        assertEquals(List(3) { YAMA_JUNK_BLACK_BEAD }, (3..5).map(YamaRewardRules::junkItemId))
        assertEquals(List(3) { YAMA_JUNK_SPINACH_ROLL }, (6..8).map(YamaRewardRules::junkItemId))
        assertEquals(List(2) { YAMA_JUNK_DEMON_DUNG }, (9..10).map(YamaRewardRules::junkItemId))
    }

    @Test
    fun `low contribution deterministically routes to junk without tertiary or contract rolls`() {
        val random = SequenceRandom()
        val result =
            YamaRewardService(random).roll(YamaRewardInput(374, 2500, YamaContract.CATALYST))

        assertEquals(YamaPrimaryReward.JUNK, result.primary)
        assertEquals(1, result.primaryQuantity)
        assertTrue(result.tertiaries.isEmpty())
        assertFalse(result.grantContractReward)
        assertEquals(0, random.doubleCalls)
    }

    @Test
    fun `primary table is evaluated in unique dossier lockbox shard order`() {
        val dossier =
            YamaRewardService(SequenceRandom(1.0, 0.0, 1.0, 1.0))
                .roll(YamaRewardInput(2500, 2500, YamaContract.NONE))
        assertEquals(YamaPrimaryReward.DOSSIER, dossier.primary)

        val lockbox =
            YamaRewardService(SequenceRandom(1.0, 1.0, 0.0, 1.0, 1.0))
                .roll(YamaRewardInput(2500, 2500, YamaContract.NONE))
        assertEquals(YamaPrimaryReward.LOCKBOX, lockbox.primary)

        val shards =
            YamaRewardService(SequenceRandom(1.0, 1.0, 1.0, 0.0, 1.0, 1.0))
                .roll(YamaRewardInput(1250, 2500, YamaContract.NONE))
        assertEquals(YamaPrimaryReward.SHARDS, shards.primary)
        assertEquals(6, shards.primaryQuantity)
    }

    @Test
    fun `failed primary rolls fall through to standard or suppressed none`() {
        val standard =
            YamaRewardService(SequenceRandom.repeat(1.0, 6))
                .roll(YamaRewardInput(2500, 2500, YamaContract.NONE))
        val suppressed =
            YamaRewardService(SequenceRandom.repeat(1.0, 6))
                .roll(YamaRewardInput(2500, 2500, YamaContract.CATALYST))

        assertEquals(YamaPrimaryReward.STANDARD, standard.primary)
        assertEquals(YamaPrimaryReward.NONE, suppressed.primary)
        assertTrue(suppressed.grantContractReward)
    }

    @Test
    fun `tertiaries roll separately even when the primary is unique`() {
        val result =
            YamaRewardService(SequenceRandom(0.0, 0.0, 0.0))
                .roll(YamaRewardInput(2500, 2500, YamaContract.NONE, eliteClueDenominator = 30))

        assertEquals(YamaPrimaryReward.UNIQUE, result.primary)
        assertEquals(
            setOf(YamaTertiaryReward.PET, YamaTertiaryReward.ELITE_CLUE),
            result.tertiaries,
        )
    }

    @Test
    fun `familiar contract adds its one in one hundred pet benefit`() {
        val result =
            YamaRewardService(SequenceRandom(0.0, 1.0, 0.0, 1.0))
                .roll(YamaRewardInput(2500, 2500, YamaContract.FAMILIAR))

        assertEquals(YamaPrimaryReward.UNIQUE, result.primary)
        assertEquals(setOf(YamaTertiaryReward.PET), result.tertiaries)
        assertFalse(result.grantContractReward)
    }

    @Test
    fun `elite clue denominator supplied by achievement hook is respected`() {
        val base =
            YamaRewardService(SequenceRandom(0.0, 1.0, 0.99))
                .roll(YamaRewardInput(2500, 2500, YamaContract.NONE, eliteClueDenominator = 30))
        val boosted =
            YamaRewardService(SequenceRandom(0.0, 1.0, 0.99))
                .roll(YamaRewardInput(2500, 2500, YamaContract.NONE, eliteClueDenominator = 1))

        assertFalse(YamaTertiaryReward.ELITE_CLUE in base.tertiaries)
        assertTrue(YamaTertiaryReward.ELITE_CLUE in boosted.tertiaries)
    }

    @Test
    fun `two players consume independent reward rolls`() {
        val random = SequenceRandom(0.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        val service = YamaRewardService(random)

        val first = service.roll(YamaRewardInput(1250, 2500, YamaContract.NONE))
        val second = service.roll(YamaRewardInput(1250, 2500, YamaContract.NONE))

        assertEquals(YamaPrimaryReward.UNIQUE, first.primary)
        assertEquals(YamaPrimaryReward.STANDARD, second.primary)
        assertEquals(9, random.doubleCalls)
    }

    @Test
    fun `standard quantities use the same capped contribution ratio`() {
        val result =
            YamaRewardService(SequenceRandom.repeat(1.0, 6))
                .roll(YamaRewardInput(1250, 2500, YamaContract.NONE))

        assertEquals(YamaPrimaryReward.STANDARD, result.primary)
        assertEquals(20, result.scaledStandardQuantity(40))
    }
}

private class SequenceRandom(private vararg val doubles: Double) : GameRandom {
    private var index = 0
    var doubleCalls: Int = 0
        private set

    override fun of(maxExclusive: Int): Int = 0

    override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive

    override fun randomDouble(): Double {
        check(index < doubles.size) { "No scripted random double remains at call $index." }
        doubleCalls++
        return doubles[index++]
    }

    companion object {
        fun repeat(value: Double, count: Int): SequenceRandom =
            SequenceRandom(*DoubleArray(count) { value })
    }
}
