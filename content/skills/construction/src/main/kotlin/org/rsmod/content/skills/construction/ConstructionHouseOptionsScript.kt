package org.rsmod.content.skills.construction

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Server authority for the stock revision-240 House Options and House Viewer interfaces. */
internal class ConstructionHouseOptionsScript
@Inject
constructor(
    private val houses: ConstructionHouseService,
    private val servants: ConstructionServantService,
    private val protectedAccess: ProtectedAccessLauncher,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            houses.syncHouseOptions(player)
            protectedAccess.launch(player) { servants.reconcile(this) }
        }
        onIfOpen("interface.poh_options") { houses.syncHouseOptions(player) }

        onIfModalButton("component.poh_options:viewer") { houses.openViewer(this) }
        onIfModalButton("component.poh_options:build_mode_on") { houses.setBuildMode(this, true) }
        onIfModalButton("component.poh_options:build_mode_off") { houses.setBuildMode(this, false) }
        onIfModalButton("component.poh_options:tele_on") { houses.setTeleportInside(player, true) }
        onIfModalButton("component.poh_options:tele_off") {
            houses.setTeleportInside(player, false)
        }
        onIfModalButton("component.poh_options:default_build_mode_on") {
            houses.setDefaultBuildMode(player, true)
        }
        onIfModalButton("component.poh_options:default_build_mode_off") {
            houses.setDefaultBuildMode(player, false)
        }
        listOf("doors_closed", "icon_doors_closed").forEach { child ->
            onIfModalButton("component.poh_options:$child") {
                houses.setDoors(this, HouseDoorsMode.CLOSED)
            }
        }
        listOf("doors_open", "icon_doors_open").forEach { child ->
            onIfModalButton("component.poh_options:$child") {
                houses.setDoors(this, HouseDoorsMode.OPEN)
            }
        }
        listOf("doors_none", "icon_doors_none").forEach { child ->
            onIfModalButton("component.poh_options:$child") {
                houses.setDoors(this, HouseDoorsMode.NONE)
            }
        }
        onIfModalButton("component.poh_options:expel_guests") {
            mes("Guest expulsion is not available yet.")
        }
        onIfModalButton("component.poh_options:call_servant") { servants.call(this) }
        onIfModalButton("component.poh_options:leave_house") { houses.leave(this) }
        onIfModalButton("component.poh_options:com_24") { player.ifClose(eventBus) }

        repeat(38) { index ->
            val child = (index + 1).toString().padStart(2, '0')
            onIfModalButton("component.poh_viewer:$child") { houses.selectViewerRoom(this, index) }
        }
        onIfModalButton("component.poh_viewer:portal") { houses.goToViewerPortal(this) }
        onIfModalButton("component.poh_viewer:rotate") { houses.beginViewerRotation(this) }
        onIfModalButton("component.poh_viewer:clockwise") { houses.rotateViewerRoom(this, true) }
        onIfModalButton("component.poh_viewer:anticlockwise") {
            houses.rotateViewerRoom(this, false)
        }
        onIfModalButton("component.poh_viewer:move") { houses.beginViewerMove(this) }
        onIfModalButton("component.poh_viewer:delete") { houses.deleteViewerRoom(this) }
        onIfModalButton("component.poh_viewer:map") {
            houses.handleViewerMap(this, it.comsub, it.op)
        }
        onIfModalButton("component.poh_viewer:cancel") { houses.cancelViewer(this) }
        onIfModalButton("component.poh_viewer:done") { houses.finishViewer(this) }
        onIfClose("interface.poh_viewer") { houses.closeViewer(player) }
    }
}
