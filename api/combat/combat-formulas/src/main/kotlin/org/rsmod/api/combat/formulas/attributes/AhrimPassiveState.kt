package org.rsmod.api.combat.formulas.attributes

import java.util.WeakHashMap
import org.rsmod.game.entity.Player

/**
 * Per-cast state for Ahrim's Blighted Aura.
 *
 * A cast rolls once before accuracy/max-hit calculation. Both formula collection passes read the
 * same value, and the spell queue consumes it on either hit or splash. Weak keys make abandoned
 * player objects self-cleaning if a cast is interrupted before either queue path runs.
 */
public object AhrimPassiveState {
    private val active: WeakHashMap<Player, Boolean> = WeakHashMap()

    public fun begin(player: Player, triggered: Boolean) {
        active[player] = triggered
    }

    public fun triggered(player: Player): Boolean = active[player] == true

    public fun consume(player: Player): Boolean = active.remove(player) == true

    public fun clear(player: Player) {
        active.remove(player)
    }

    public const val PROC_DENOMINATOR: Int = 4
    public const val STRENGTH_DRAIN: Int = 5
}
