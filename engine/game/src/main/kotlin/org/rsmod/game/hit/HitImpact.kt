package org.rsmod.game.hit

import org.rsmod.game.entity.PathingEntity

/**
 * The final result of a queued or instant [Hit] after its hit processor has applied it.
 *
 * [damage] is the amount of hitpoints actually removed from [target]. It can differ from
 * [Hit.damage] when the target had fewer hitpoints remaining than the pending hit.
 */
public data class HitImpact(
    public val target: PathingEntity,
    public val hit: Hit,
    public val damage: Int,
)

/**
 * Optional callback invoked once a [Hit] has been processed.
 *
 * This is stored with the hit so callers can react to the final damage of an individual queued hit
 * without inferring intent from the source weapon or target type.
 */
public fun interface HitImpactHandler {
    public fun onImpact(impact: HitImpact)
}
