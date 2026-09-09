package org.rsmod.content.interfaces.collectionlog

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.collectionlog.CollectionLogRanks
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpWorn1
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class CollectionLogScript @Inject constructor(private val ui: CollectionLogUi) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton(CollectionLogUi.CLOSE_COMPONENT) {
            player.ifCloseOverlay(CollectionLogUi.INTERFACE, eventBus)
        }
        onIfModalButton(CollectionLogUi.CLOSE_COMPONENT) { player.ifClose(eventBus) }
        ui.validateAssets()
        ui.installRecordHook()
        onPlayerLogin { ui.onLogin(player) }
        onIfOpen(CollectionLogUi.INTERFACE) { ui.onOpen(player) }
        onIfOpen(CollectionLogUi.OVERVIEW_INTERFACE) { ui.prepareOverview(player) }
        onIfClose(CollectionLogUi.INTERFACE) { ui.onClose(player) }

        CollectionLogUi.CATEGORY_COMPONENTS.forEachIndexed { index, component ->
            onIfOverlayButton(component) { ui.selectCategory(player, index) }
            onIfModalButton(component) { ui.selectCategory(player, index) }
        }
        CollectionLogUi.SECTION_COMPONENTS.forEachIndexed { categoryIndex, component ->
            onIfOverlayButton(component) { event ->
                ui.selectCurrentSection(player, event.comsub, categoryIndex)
            }
            onIfModalButton(component) { event ->
                ui.selectCurrentSection(player, event.comsub, categoryIndex)
            }
        }
        onIfOverlayButton(CollectionLogUi.SEARCH_RESULTS_COMPONENT) { event ->
            ui.selectSearchResult(player, event.comsub)
        }
        onIfModalButton(CollectionLogUi.SEARCH_RESULTS_COMPONENT) { event ->
            ui.selectSearchResult(player, event.comsub)
        }

        CollectionLogUi.SEARCH_INPUT_COMPONENTS.forEach { component ->
            onIfOverlayButton(component) { ui.refreshSearch(player) }
            onIfModalButton(component) { ui.refreshSearch(player) }
        }
        onIfOverlayButton(CollectionLogUi.COMBAT_ACHIEVEMENTS_COMPONENT) {
            ifOpenMainModal("interface.ca_overview")
        }
        onIfModalButton(CollectionLogUi.COMBAT_ACHIEVEMENTS_COMPONENT) {
            ifOpenMainModal("interface.ca_overview")
        }
        CollectionLogUi.COLLECTION_BOOKS.forEach { book ->
            onOpHeld3(book) { ifOpenMainModal(CollectionLogUi.INTERFACE) }
            onOpHeld4(book) {
                if (ui.prepareOverview(player)) ifOpenMainModal(CollectionLogUi.OVERVIEW_INTERFACE)
            }
            onOpWorn1(book) { ifOpenMainModal(CollectionLogUi.INTERFACE) }
            onOpWorn2(book) {
                if (ui.prepareOverview(player)) ifOpenMainModal(CollectionLogUi.OVERVIEW_INTERFACE)
            }
        }

        CollectionLogRanks.all.forEach { rank ->
            onOpHeld3(rank.staffInternalName) { ui.createRankBook(player, rank) }
            onOpWorn1(rank.staffInternalName) { ui.createRankBook(player, rank) }
        }

        val collector = RSCM.getReverseMapping(RSCMType.NPC, CollectionLogUi.COLLECTOR_NPC_ID)
        onOpNpc1(collector) {
            player.mes("The Collector keeps the Collection Log and awards its rank staves.")
        }
        onOpNpc3(collector) { ui.giveCollectionBook(player) }
        onOpNpc4(collector) { ui.claimRankReward(player) }
    }
}
