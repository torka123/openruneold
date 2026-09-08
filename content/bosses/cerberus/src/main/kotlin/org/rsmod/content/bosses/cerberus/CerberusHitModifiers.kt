package org.rsmod.content.bosses.cerberus

import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder

/** Fixed encounter damage has already resolved its mechanic-specific protection at launch. */
internal object CerberusFixedHitModifier : PlayerHitModifier {
    override fun HitBuilder.modify(target: Player) {
        if (target.adminGodMode) damage = 0
    }
}
