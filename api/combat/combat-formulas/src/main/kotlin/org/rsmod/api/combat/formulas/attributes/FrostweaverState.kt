package org.rsmod.api.combat.formulas.attributes

import java.util.WeakHashMap
import org.rsmod.game.entity.Player

/** One-shot state for the Blue Moon Frostweaver instant melee follow-up. */
public object FrostweaverState {
    private val ready = WeakHashMap<Player, Boolean>()

    public fun trigger(player: Player) {
        synchronized(ready) { ready[player] = true }
    }

    public fun isReady(player: Player): Boolean = synchronized(ready) { ready[player] == true }

    public fun consume(player: Player): Boolean = synchronized(ready) { ready.remove(player) == true }

    public fun clear(player: Player) {
        synchronized(ready) { ready.remove(player) }
    }

    public const val STANDARD_OR_ICE_DENOMINATOR: Int = 5
    public const val GRASP_DENOMINATOR: Int = 2
}
