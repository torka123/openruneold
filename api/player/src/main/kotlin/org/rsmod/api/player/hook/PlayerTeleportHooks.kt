package org.rsmod.api.player.hook

import jakarta.inject.Inject
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Dispatches post-validation, pre-movement callbacks for player teleports. */
public class PlayerTeleportHooks
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards PlayerTeleportHook>) {
    public fun beforeTeleport(player: Player, destination: CoordGrid, type: TeleportType) {
        hooks.forEach { it.beforeTeleport(player, destination, type) }
    }

    public companion object {
        /** Optional hook dispatcher used by lightweight protected-access contexts. */
        public val EMPTY: PlayerTeleportHooks = PlayerTeleportHooks(emptySet())
    }
}
