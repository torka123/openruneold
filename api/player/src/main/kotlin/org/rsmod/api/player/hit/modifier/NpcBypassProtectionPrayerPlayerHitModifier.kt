package org.rsmod.api.player.hit.modifier

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder

/**
 * Applies normal NPC-hit handling without the target's protection-prayer reduction.
 *
 * This is for explicitly rolled mechanics such as Verac's Defiler strike. Admin protection,
 * equipment immunities, and NPC absorption still apply.
 */
public object NpcBypassProtectionPrayerPlayerHitModifier : PlayerHitModifier {
    /** For encounters that apply Ward selectively to their regular attacks. */
    public val withoutWard: PlayerHitModifier = PlayerHitModifier { target ->
        applyStandardPlayerHitModifier(target, applyProtectionPrayer = false, applyWard = false)
    }

    override fun HitBuilder.modify(target: Player): Unit =
        applyStandardPlayerHitModifier(target, applyProtectionPrayer = false)
}
