package org.rsmod.api.player.hit.modifier

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.runClientScript
import org.rsmod.game.entity.Player

/** Ward's effect state belongs below combat-commons so every player hit path can use it. */
public object WardOfArceuusState {
    private val expiry: AttributeKey<Int> = AttributeKey(resetOnDeath = true, temp = true)

    public fun activate(player: Player, expiresAt: Int) {
        player.attr[expiry] = expiresAt
        // Ward's native buff reader uses this start clock and the player's base Magic level.
        player.runClientScript(5926, 3126, player.currentMapClock)
    }

    public fun isActive(player: Player, cycle: Int): Boolean {
        val expiresAt = player.attr[expiry] ?: return false
        if (expiresAt <= cycle) {
            clear(player)
            return false
        }
        return true
    }

    public fun clear(player: Player) {
        player.attr.remove(expiry)
        player.runClientScript(5926, 3126, 0)
    }
}
