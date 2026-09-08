package org.rsmod.content.skills.sailing

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/** Synchronizes only revision-240 Sailing state proven by cache and packet evidence. */
@Singleton
class SailingClientState @Inject constructor(private val eventBus: EventBus) {
    fun board(player: Player, boat: Boat) {
        player.sailingSidePanelVisible = 1
        player.sailingSidePanelTab = FACILITIES_TAB
        player.sailingSidePanelBoatType = boat.type.cacheRow.asRSCM(RSCMType.DBROW)
        player.sailingBoatMoveMode = SailingMoveModes.HELM_IDLE
        player.sailingHelmStatus = HELM_AVAILABLE
        player.sailingPlayerAtHelm = 0
        player.sailingSailButtonToggled = 0
        player.sailingAmmoNeedsUpdate = 1
        player.sailingBoatStatsNeedsUpdate = 1
        player.ifOpenOverlay(SIDE_PANEL, SIDE_ZERO, eventBus)
        enableInteractions(player)
    }

    fun settleBoarding(player: Player) {
        player.sailingAmmoNeedsUpdate = 0
        player.sailingBoatStatsNeedsUpdate = 0
    }

    fun helmLocked(player: Player) {
        player.sailingBoatMoveMode = SailingMoveModes.HELM_IDLE
        player.sailingHelmStatus = HELM_AVAILABLE
        player.sailingPlayerAtHelm = 0
        ClientScripts.toplevelSidebuttonSwitch(player, COMBAT_TAB)
    }

    fun helmEntered(player: Player) {
        player.sailingPlayerAtHelm = 1
        player.sailingHelmStatus = HELM_NAVIGATING
    }

    fun fullSail(player: Player) {
        player.sailingBoatMoveMode = SailingMoveModes.FULL
        player.sailingSailButtonToggled = 1
    }

    fun syncMotion(player: Player, boat: Boat) {
        player.sailingBoatMoveMode = boat.moveMode
        player.sailingSailButtonToggled =
            if (boat.moveMode == SailingMoveModes.HALF || boat.moveMode == SailingMoveModes.FULL) {
                1
            } else {
                0
            }
    }

    fun helmReleased(player: Player, boat: Boat) {
        player.sailingPlayerAtHelm = 0
        player.sailingHelmStatus = HELM_AVAILABLE
        syncMotion(player, boat)
    }

    fun enableInteractions(player: Player) {
        player.ifSetEvents(SWITCH_BUTTON, -1..-1, IfEvent.Op1)
        player.ifSetEvents(SWITCH_BUTTON, 0..12, IfEvent.Op1)
    }

    fun showCombatOptions(player: Player) {
        player.ifOpenOverlay(COMBAT_OPTIONS, SIDE_ZERO, eventBus)
        ClientScripts.toplevelSidebuttonSwitch(player, COMBAT_TAB)
    }

    fun clear(player: Player, restoreCombat: Boolean) {
        clearTransientSailingState(player)
        if (restoreCombat) {
            showCombatOptions(player)
        }
    }

    private companion object {
        const val FACILITIES_TAB = 0
        const val HELM_AVAILABLE = 1
        const val HELM_NAVIGATING = 2
        const val COMBAT_TAB = 0
        const val SIDE_PANEL = "interface.sailing_sidepanel"
        const val COMBAT_OPTIONS = "interface.combat_interface"
        const val SIDE_ZERO = "component.toplevel_osrs_stretch:side0"
        const val SWITCH_BUTTON = "component.sailing_sidepanel:switch_button"
    }
}
