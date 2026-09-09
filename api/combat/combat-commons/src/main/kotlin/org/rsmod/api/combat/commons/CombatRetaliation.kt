package org.rsmod.api.combat.commons

import org.rsmod.game.hit.HitContext
import org.rsmod.game.queue.QueueArgumentValidity

/** Native retaliation keeps the same launch lifecycle as the damage that provoked it. */
public data class CombatRetaliation<T>(public val source: T, public val context: HitContext) :
    QueueArgumentValidity {
    override fun isQueueArgumentValid(): Boolean = context.isValid()
}
