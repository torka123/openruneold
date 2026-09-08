package org.rsmod.api.player.hit.modifier

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder

/**
 * Applies standard hit modification while skipping protection-prayer reduction for player-source
 * hits.
 *
 * Admin god mode remains active. Hits from an npc or without a player source retain the exact
 * [StandardPlayerHitModifier] behavior, including npc absorption.
 */
public object BypassProtectionPrayerPlayerHitModifier : PlayerHitModifier {
    override fun HitBuilder.modify(target: Player): Unit {
        applyStandardPlayerHitModifier(target = target, applyProtectionPrayer = !isFromPlayer)
    }
}
