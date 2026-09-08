package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipState
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Releases transient helm ownership before disconnect persistence or after leaving a boat. */
class SailingLifecycle
@Inject
constructor(
    private val boats: BoatManager,
    private val access: BoatAccessController,
    private val clientState: SailingClientState,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            SailingOwnershipState.reference(player)
            access.leave(player)
            clientState.clear(player, restoreCombat = false)
        }
        onPlayerCoordsChanged { access.reconcile(player, boats) }
        onPlayerLogout {
            boats.releaseHelm(player, updateClient = false)
            access.leave(player)
            clientState.clear(player, restoreCombat = false)
        }
    }
}

internal fun clearTransientSailingState(player: Player) {
    player.aboardPlayerBoat = 0
    player.helmLockedIn = 0
    player.sailingSidePanelVisible = 0
    player.sailingSidePanelTab = 0
    player.sailingSailButtonToggled = 0
    player.sailingBoatMoveMode = SailingMoveModes.STOPPED
    player.sailingHelmStatus = 0
    player.sailingPlayerAtHelm = 0
    player.sailingAmmoNeedsUpdate = 0
    player.sailingBoatStatsNeedsUpdate = 0
    player.sailingSidePanelBoatType = 0
}
