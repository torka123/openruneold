package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GangplankEvents
@Inject
constructor(
    private val boats: BoatManager,
    private val access: BoatAccessController,
    private val docks: SailingDockRegistry,
    private val clientState: SailingClientState,
) : PluginScript() {
    override fun ScriptContext.startup() {
        if (!SailingReleaseGate.playerGameplayEnabled) {
            return
        }
        for (locKey in EMBARK_LOC_KEYS) {
            onOpLoc1(locKey) { embark(it.loc.coords) }
        }
        for (locKey in DISEMBARK_LOC_KEYS) {
            onOpLoc1(locKey) { disembark() }
        }
    }

    private suspend fun ProtectedAccess.embark(coords: CoordGrid) {
        val dock = docks.byInteractionCoord(coords)
        if (dock == null) {
            mes("Nothing interesting happens.")
            return
        }
        if (dock.operational !is SailingDockOperationalGeometry.Verified) {
            mes("This dock is not available yet.")
            return
        }
        val boat = boats.mooredAt(dock.definition.dockId)
        if (boat == null) {
            mes("There isn't a boat here for you to board.")
            return
        }
        when (access.boardOwner(player, boat)) {
            is BoatBoardResult.Success,
            is BoatBoardResult.AlreadyAboard -> {
                try {
                    telejump(boat.boardDest, TeleportType.Exempt)
                } catch (t: Throwable) {
                    access.leave(player)
                    throw t
                }
                clientState.board(player, boat)
                delay(1)
                clientState.settleBoarding(player)
                mes("You board your boat.")
            }
            is BoatBoardResult.NotOwner -> mes("That is not your boat.")
            is BoatBoardResult.Full -> mes("There is no room on that boat.")
            is BoatBoardResult.AboardOtherBoat -> mes("You must leave your current boat first.")
            BoatBoardResult.InvalidPlayer,
            BoatBoardResult.MissingBoat -> mes("You cannot board that boat right now.")
        }
    }

    private suspend fun ProtectedAccess.disembark() {
        val boat = boats.boatOf(player)
        val dockId = boat?.dockId
        if (dockId == null) {
            mes("You can only disembark while your boat is moored.")
            return
        }
        val dock = docks.byDockId(dockId)
        val geometry = dock?.operational as? SailingDockOperationalGeometry.Verified
        if (dock == null || geometry == null) {
            mes("This dock is not available yet.")
            return
        }
        boats.releaseHelm(player)
        access.leave(player)
        clientState.clear(player, restoreCombat = true)
        telejump(geometry.landReturn, TeleportType.Exempt)
        mes("You disembark at ${dock.definition.displayName}.")
    }

    private companion object {
        val EMBARK_LOC_KEYS = listOf("loc.sailing_gangplank_embark", "loc.sailing_mooring_embark")
        val DISEMBARK_LOC_KEYS =
            listOf("loc.sailing_gangplank_disembark", "loc.sailing_mooring_disembark")
    }
}
