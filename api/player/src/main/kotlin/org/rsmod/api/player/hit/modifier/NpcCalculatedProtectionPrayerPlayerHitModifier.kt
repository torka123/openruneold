package org.rsmod.api.player.hit.modifier

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder

/**
 * Applies an NPC protection-prayer reduction from a prayer state captured on the attack's
 * calculation tick. This is used by telegraphed attacks whose later impact must not re-sample the
 * target's prayer.
 */
public data class NpcCalculatedProtectionPrayerPlayerHitModifier(
    public val protectedAtCalculation: Boolean,
    public val reductionPercent: Int,
) : PlayerHitModifier {
    init {
        require(reductionPercent in 0..100) {
            "Protection-prayer reduction must be between 0 and 100."
        }
    }

    override fun HitBuilder.modify(target: Player) {
        applyStandardPlayerHitModifier(target, applyProtectionPrayer = false)
        if (protectedAtCalculation) {
            damage = (damage * (100 - reductionPercent)) / 100
        }
    }
}
