package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerTeleportHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Releases runtime boat authority before any teleport that leaves the current leased deck. */
internal class SailingTeleportHook
@Inject
constructor(private val boats: BoatManager, private val access: BoatAccessController) :
    PlayerTeleportHook {
    override fun beforeTeleport(player: Player, destination: CoordGrid, type: TeleportType) {
        access.beforeTeleport(player, destination, boats)
    }
}
