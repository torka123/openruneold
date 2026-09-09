package org.rsmod.api.player.hit

import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.queue.QueueArgumentValidity

public data class DeferredPlayerHit(val builder: HitBuilder, val modifier: PlayerHitModifier) :
    QueueArgumentValidity {
    override fun isQueueArgumentValid(): Boolean = builder.context.isValid()
}
