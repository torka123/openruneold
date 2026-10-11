package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.floor
import org.rsmod.api.random.GameRandom
import org.rsmod.content.drops.tables.monsters.YAMA_DOSSIER_DENOMINATOR
import org.rsmod.content.drops.tables.monsters.YAMA_ELITE_CLUE_DENOMINATOR
import org.rsmod.content.drops.tables.monsters.YAMA_LOCKBOX_DENOMINATOR
import org.rsmod.content.drops.tables.monsters.YAMA_PET_DENOMINATOR
import org.rsmod.content.drops.tables.monsters.YAMA_SHARDS_DENOMINATOR
import org.rsmod.content.drops.tables.monsters.YAMA_UNIQUE_DENOMINATOR

internal const val YAMA_SHARD_BASE_QUANTITY = 12
internal const val YAMA_FAMILIAR_PET_DENOMINATOR = 100
internal const val YAMA_JUNK_CABBAGE = 1965
internal const val YAMA_JUNK_BLACK_BEAD = 1474
internal const val YAMA_JUNK_SPINACH_ROLL = 1969
internal const val YAMA_JUNK_DEMON_DUNG = 30747

internal enum class YamaPrimaryReward {
    JUNK,
    UNIQUE,
    DOSSIER,
    LOCKBOX,
    SHARDS,
    STANDARD,
    NONE,
}

internal enum class YamaTertiaryReward {
    PET,
    ELITE_CLUE,
}

internal data class YamaRewardInput(
    val damage: Int,
    val maximumHitpoints: Int,
    val contract: YamaContract,
    val eliteClueDenominator: Int = YAMA_ELITE_CLUE_DENOMINATOR,
)

internal data class YamaRewardRoll(
    val primary: YamaPrimaryReward,
    val primaryQuantity: Int,
    val tertiaries: Set<YamaTertiaryReward>,
    val grantContractReward: Boolean,
    val contribution: YamaRewardContribution,
) {
    fun scaledStandardQuantity(baseQuantity: Int): Int =
        YamaRewardRules.scaledQuantity(baseQuantity, contribution)
}

internal data class YamaRewardContribution(val damage: Int, val maximumHitpoints: Int) {
    val cappedDamage: Int = damage.coerceIn(0, maximumHitpoints.coerceAtLeast(0))

    val ratio: Double =
        if (maximumHitpoints <= 0) 0.0 else cappedDamage.toDouble() / maximumHitpoints.toDouble()
}

internal object YamaRewardRules {
    fun contribution(damage: Int, maximumHitpoints: Int): YamaRewardContribution =
        YamaRewardContribution(damage, maximumHitpoints)

    fun eligible(contribution: YamaRewardContribution): Boolean =
        contribution.damage >= YamaRules.MIN_REWARD_DAMAGE &&
            contribution.ratio * 100.0 >= YamaRules.MIN_REWARD_CONTRIBUTION_PERCENT

    fun petEligible(contribution: YamaRewardContribution): Boolean =
        eligible(contribution) && contribution.ratio * 100.0 > 15.0

    fun allowsStandardTable(contract: YamaContract): Boolean =
        contract == YamaContract.NONE || contract == YamaContract.FAMILIAR

    fun junkItemId(roll: Int): Int =
        when (Math.floorMod(roll, 11)) {
            in 0..2 -> YAMA_JUNK_CABBAGE
            in 3..5 -> YAMA_JUNK_BLACK_BEAD
            in 6..8 -> YAMA_JUNK_SPINACH_ROLL
            else -> YAMA_JUNK_DEMON_DUNG
        }

    // Familiar's contract benefit is the separate 1/100 tertiary pet roll below.
    fun grantsContractReward(contract: YamaContract): Boolean =
        contract !in setOf(YamaContract.NONE, YamaContract.FAMILIAR, YamaContract.CUSTOM)

    fun scaledChance(baseDenominator: Int, contribution: YamaRewardContribution): Double {
        require(baseDenominator > 0) { "Base denominator must be positive." }
        return contribution.ratio / baseDenominator.toDouble()
    }

    fun scaledQuantity(baseQuantity: Int, contribution: YamaRewardContribution): Int {
        if (baseQuantity <= 0 || contribution.ratio <= 0.0) return 0
        return floor(baseQuantity * contribution.ratio).toInt().coerceAtLeast(1)
    }
}

/**
 * Performs only Yama's contribution-sensitive reward decisions. Item selection remains in the
 * existing Yama weighted tables so this service does not duplicate the drop definitions.
 */
@Singleton
internal class YamaRewardService @Inject constructor(private val random: GameRandom) {
    fun roll(input: YamaRewardInput): YamaRewardRoll {
        val contribution = YamaRewardRules.contribution(input.damage, input.maximumHitpoints)
        if (!YamaRewardRules.eligible(contribution)) {
            return YamaRewardRoll(
                primary = YamaPrimaryReward.JUNK,
                primaryQuantity = 1,
                tertiaries = emptySet(),
                grantContractReward = false,
                contribution = contribution,
            )
        }

        val primary = rollPrimary(input.contract, contribution)
        val tertiaries = rollTertiaries(input, contribution)
        return YamaRewardRoll(
            primary = primary,
            primaryQuantity =
                if (primary == YamaPrimaryReward.SHARDS) {
                    YamaRewardRules.scaledQuantity(YAMA_SHARD_BASE_QUANTITY, contribution)
                } else {
                    1
                },
            tertiaries = tertiaries,
            grantContractReward = YamaRewardRules.grantsContractReward(input.contract),
            contribution = contribution,
        )
    }

    private fun rollPrimary(
        contract: YamaContract,
        contribution: YamaRewardContribution,
    ): YamaPrimaryReward =
        when {
            passes(YAMA_UNIQUE_DENOMINATOR, contribution) -> YamaPrimaryReward.UNIQUE
            passes(YAMA_DOSSIER_DENOMINATOR, contribution) -> YamaPrimaryReward.DOSSIER
            passes(YAMA_LOCKBOX_DENOMINATOR, contribution) -> YamaPrimaryReward.LOCKBOX
            passes(YAMA_SHARDS_DENOMINATOR, contribution) -> YamaPrimaryReward.SHARDS
            YamaRewardRules.allowsStandardTable(contract) -> YamaPrimaryReward.STANDARD
            else -> YamaPrimaryReward.NONE
        }

    private fun rollTertiaries(
        input: YamaRewardInput,
        contribution: YamaRewardContribution,
    ): Set<YamaTertiaryReward> {
        val rewards = linkedSetOf<YamaTertiaryReward>()
        val standardPet =
            YamaRewardRules.petEligible(contribution) && passes(YAMA_PET_DENOMINATOR, contribution)
        val familiarPet =
            input.contract == YamaContract.FAMILIAR &&
                YamaRewardRules.petEligible(contribution) &&
                passes(YAMA_FAMILIAR_PET_DENOMINATOR, contribution)
        if (standardPet || familiarPet) rewards += YamaTertiaryReward.PET

        if (passes(input.eliteClueDenominator.coerceAtLeast(1), contribution)) {
            rewards += YamaTertiaryReward.ELITE_CLUE
        }
        return rewards
    }

    private fun passes(baseDenominator: Int, contribution: YamaRewardContribution): Boolean =
        random.randomDouble() < YamaRewardRules.scaledChance(baseDenominator, contribution)
}
