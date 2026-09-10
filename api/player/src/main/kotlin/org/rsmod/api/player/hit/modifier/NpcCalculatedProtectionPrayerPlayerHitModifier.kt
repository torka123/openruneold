package org.rsmod.api.player.hit.modifier

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

/**
 * Applies an NPC protection-prayer reduction from a prayer state captured on the attack's
 * calculation tick. By default later prayer changes do not affect it. Attacks with a separate
 * late-prayer reduction may opt in, and must apply this modifier through the native impact queue if
 * they need the late state sampled at impact rather than when the hit is enqueued.
 */
public data class NpcCalculatedProtectionPrayerPlayerHitModifier(
    public val protectedAtCalculation: Boolean,
    public val reductionPercent: Int,
    public val lateReductionPercent: Int = 0,
) : PlayerHitModifier {
    init {
        require(reductionPercent in 0..100) {
            "Protection-prayer reduction must be between 0 and 100."
        }
        require(lateReductionPercent in 0..100) {
            "Late protection-prayer reduction must be between 0 and 100."
        }
    }

    override fun HitBuilder.modify(target: Player) {
        applyStandardPlayerHitModifier(target, applyProtectionPrayer = false)
        val protectedNow =
            when (type) {
                HitType.Magic -> target.vars["varbit.prayer_protectfrommagic"] == 1
                HitType.Ranged -> target.vars["varbit.prayer_protectfrommissiles"] == 1
                HitType.Melee -> target.vars["varbit.prayer_protectfrommelee"] == 1
                HitType.Typeless -> false
            }
        val reduction =
            when {
                protectedAtCalculation -> reductionPercent
                protectedNow -> lateReductionPercent
                else -> 0
            }
        damage = (damage * (100 - reduction)) / 100
    }
}
