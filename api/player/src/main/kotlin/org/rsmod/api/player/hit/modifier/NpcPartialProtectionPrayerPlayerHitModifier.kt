package org.rsmod.api.player.hit.modifier

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

/**
 * Applies a boss-specific protection-prayer percentage while preserving the rest of the standard
 * NPC-hit handling (admin protection, equipment immunities, and absorption).
 */
public data class NpcPartialProtectionPrayerPlayerHitModifier(public val reductionPercent: Int) :
    PlayerHitModifier {
    init {
        require(reductionPercent in 0..100) {
            "Protection-prayer reduction must be between 0 and 100."
        }
    }

    override fun HitBuilder.modify(target: Player) {
        applyStandardPlayerHitModifier(target, applyProtectionPrayer = false)
        val protected =
            when (type) {
                HitType.Typeless -> false
                HitType.Melee -> target.vars["varbit.prayer_protectfrommelee"] == 1
                HitType.Ranged -> target.vars["varbit.prayer_protectfrommissiles"] == 1
                HitType.Magic -> target.vars["varbit.prayer_protectfrommagic"] == 1
            }
        if (protected) {
            damage = (damage * (100 - reductionPercent)) / 100
        }
    }
}
