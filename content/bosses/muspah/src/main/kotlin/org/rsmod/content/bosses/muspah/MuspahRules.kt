package org.rsmod.content.bosses.muspah

import kotlin.math.max

internal object MuspahRules {
    fun startingForm(roll: Int): MuspahForm =
        if ((roll and 1) == 0) MuspahForm.RANGED else MuspahForm.MELEE

    fun firstSpecial(initialForm: MuspahForm): MuspahSpecial =
        if (initialForm == MuspahForm.RANGED) {
            MuspahSpecial.LIGHTNING_CLOUDS
        } else {
            MuspahSpecial.HOMING_SPIKES
        }

    fun secondSpecial(initialForm: MuspahForm): MuspahSpecial =
        if (firstSpecial(initialForm) == MuspahSpecial.LIGHTNING_CLOUDS) {
            MuspahSpecial.HOMING_SPIKES
        } else {
            MuspahSpecial.LIGHTNING_CLOUDS
        }

    fun swapThreshold(form: MuspahForm): Int =
        when (form) {
            MuspahForm.RANGED -> MUSPAH_RANGED_THRESHOLD
            MuspahForm.MELEE -> MUSPAH_MELEE_THRESHOLD
            else -> Int.MAX_VALUE
        }

    fun canNormalSwap(
        form: MuspahForm,
        firstSwapComplete: Boolean,
        positiveAttacks: Int,
        eligibleDamage: Int,
    ): Boolean {
        if (eligibleDamage < swapThreshold(form)) return false
        return !firstSwapComplete || positiveAttacks >= MUSPAH_LATER_SWAP_POSITIVE_HITS
    }

    fun shouldCountPositiveAttack(damage: Int, currentCycle: Int, previousCycle: Int): Boolean =
        damage > 0 && currentCycle != previousCycle

    fun crossedFirstSpecial(afterHitpoints: Int, alreadyFired: Boolean): Boolean =
        !alreadyFired && afterHitpoints <= MUSPAH_FIRST_SPECIAL_HITPOINTS

    fun crossedSecondSpecial(afterHitpoints: Int, alreadyFired: Boolean): Boolean =
        !alreadyFired && afterHitpoints <= MUSPAH_SECOND_SPECIAL_HITPOINTS

    fun crossedShieldThreshold(afterHitpoints: Int): Boolean =
        afterHitpoints < MUSPAH_SHIELD_TRANSITION_HITPOINTS

    fun homingEndCycle(startCycle: Int): Int = startCycle + MUSPAH_HOMING_DURATION

    fun homingExpired(currentCycle: Int, endCycle: Int): Boolean = currentCycle >= endCycle

    fun smiteDrain(damage: Int): Int = if (damage > 0) max(1, damage / 4) else 0

    fun sapphireDrain(): Int = 33

    fun passiveShieldDrain(): Int = 2

    fun drainShield(current: Int, amount: Int): Int =
        (current - amount.coerceAtLeast(0)).coerceAtLeast(0)

    fun soulSplitHeal(damage: Int): Int = damage.coerceAtLeast(0) / 2

    fun spikeHeal(damage: Int): Int = (damage.coerceAtLeast(0) * 3) / 4

    fun meleeMaximum(stationaryAttacks: Int): Int =
        (8 + stationaryAttacks.coerceAtLeast(0) * 6).coerceAtMost(34)

    fun meleeStepBackBlocks(movedPreviousTick: Boolean, protectFromMelee: Boolean): Boolean =
        movedPreviousTick && protectFromMelee

    fun formatTicks(ticks: Int): String {
        val tenths = ticks.coerceAtLeast(0) * 6
        val minutes = tenths / 600
        val seconds = (tenths / 10) % 60
        val decimal = tenths % 10
        return if (minutes > 0) "%d:%02d.%d".format(minutes, seconds, decimal)
        else "%d.%d seconds".format(seconds, decimal)
    }

    fun nextOrthogonalStep(fromX: Int, fromZ: Int, targetX: Int, targetZ: Int): Pair<Int, Int> {
        val dx = targetX - fromX
        val dz = targetZ - fromZ
        return if (kotlin.math.abs(dx) >= kotlin.math.abs(dz) && dx != 0) {
            Pair(fromX + dx.coerceIn(-1, 1), fromZ)
        } else if (dz != 0) {
            Pair(fromX, fromZ + dz.coerceIn(-1, 1))
        } else {
            Pair(fromX, fromZ)
        }
    }
}
