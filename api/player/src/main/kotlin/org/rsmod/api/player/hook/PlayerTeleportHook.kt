package org.rsmod.api.player.hook

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Observes a successful player teleport immediately before its movement is committed. */
public fun interface PlayerTeleportHook {
    public fun beforeTeleport(player: Player, destination: CoordGrid, type: TeleportType)
}
