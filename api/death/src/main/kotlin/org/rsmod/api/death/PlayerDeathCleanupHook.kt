package org.rsmod.api.death

import org.rsmod.game.entity.Player

public interface PlayerDeathCleanupHook {
    public val priority: Int get() = 0

    public fun cleanup(player: Player)
}
