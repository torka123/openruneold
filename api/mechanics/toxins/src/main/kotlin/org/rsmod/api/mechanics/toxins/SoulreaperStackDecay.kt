package org.rsmod.api.mechanics.toxins

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/**
 * Holds the transient deadline for the next Soulreaper Axe stack decay.
 *
 * The normal axe attack resets this deadline. Behead clears it because it consumes every stack.
 * This uses the game cycle directly instead of an RSCM queue, since rev 240 does not define a
 * Soulreaper-specific queue key.
 */
public object SoulreaperStackDecay {
    private const val INTERVAL_CYCLES: Int = 20

    private val dueAt =
        AttributeKey<Int>(
            resetOnDeath = true,
            temp = true,
        )

    public fun reset(
        player: Player,
        clock: Int = player.currentMapClock,
    ) {
        player.attr[dueAt] = clock + INTERVAL_CYCLES
    }

    public fun isDue(
        player: Player,
        clock: Int,
    ): Boolean {
        val due = player.attr[dueAt] ?: return false
        return clock >= due
    }

    public fun clear(player: Player) {
        player.attr.remove(dueAt)
    }
}
