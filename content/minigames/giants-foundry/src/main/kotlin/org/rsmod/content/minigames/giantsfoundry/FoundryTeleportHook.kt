package org.rsmod.content.minigames.giantsfoundry

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerTeleportHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Stores an in-progress preform whenever its owner leaves the Foundry workshop. */
internal class FoundryTeleportHook @Inject constructor(
    private val service: FoundryService,
    private val ui: FoundryUi,
) : PlayerTeleportHook {
    override fun beforeTeleport(player: Player, destination: CoordGrid, type: TeleportType) {
        if (!FoundryWorkshop.contains(player.coords) || FoundryWorkshop.contains(destination)) return

        when (val result = service.storePreform(player)) {
            is FoundryServiceResult.Changed -> {
                player.righthand = null
                ui.sync(player, result.state)
                ui.closeHud(player)
                player.mes("Your Foundry preform is safely stored. You can resume it when you return.")
            }
            else -> Unit
        }
    }
}

internal object FoundryWorkshop {
    private const val MIN_X = 3328
    private const val MAX_X = 3391
    private const val MIN_Z = 11456
    private const val MAX_Z = 11519

    fun contains(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in MIN_X..MAX_X && coords.z in MIN_Z..MAX_Z
}