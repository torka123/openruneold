package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import kotlin.math.min
import org.rsmod.api.combat.commons.magic.TomeOfFireState
import org.rsmod.api.combat.commons.magic.TomeOfFireState.AddPagesResult
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn1
import org.rsmod.api.script.onOpWorn2
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Inventory and equipment options for revision-240 Tome of fire variants. */
public class TomeOfFireCharging : PluginScript() {
    override fun ScriptContext.startup() {
        val charged = ItemServerType(TomeOfFireState.CHARGED_ID)
        val empty = ItemServerType(TomeOfFireState.EMPTY_ID)

        for (pageType in TomeOfFireState.PageType.entries) {
            val page = ItemServerType(pageType.itemId)
            onOpHeldU(page, charged) { addPages(it.first, it.secondSlot, pageType) }
            onOpHeldU(page, empty) { addPages(it.first, it.secondSlot, pageType) }
        }

        // Inventory: Wield, Check, Pages, Drop / Wield, Pages, Drop.
        onOpHeld2(charged) { checkCharges(it.inventory[it.slot]) }
        onOpHeld3(charged) { removePages(it.inventory, it.slot) }
        onOpHeld2(empty) { emptyPagesMessage() }

        // Equipped: Check, Pages / Pages.
        onOpWorn1("obj.tome_of_fire") { checkCharges(player.worn[it.slot]) }
        onOpWorn2("obj.tome_of_fire") { removePages(player.worn, it.slot) }
        onOpWorn1("obj.tome_of_fire_uncharged") { emptyPagesMessage() }
    }

    private fun ProtectedAccess.addPages(
        page: ItemServerType,
        tomeSlot: Int,
        pageType: TomeOfFireState.PageType,
    ) {
        val available = inv.count(page.internalName)
        val capacity = TomeOfFireState.availablePageCapacity(inv[tomeSlot])
        val pages = min(available, capacity)
        if (pages <= 0) {
            mes("Your tome cannot hold any more pages.")
            return
        }

        val currentType = TomeOfFireState.pageType(inv[tomeSlot])
        if (TomeOfFireState.charges(inv[tomeSlot]) > 0 && currentType != pageType) {
            mes(
                "You must remove the ${currentType?.displayName ?: "existing pages"} before " +
                    "adding ${pageType.displayName}."
            )
            return
        }

        if (invDel(inv, page.internalName, pages).failure) {
            return
        }
        when (val result = TomeOfFireState.addPages(inv, tomeSlot, pageType, pages)) {
            is AddPagesResult.Success ->
                mes(
                    "You add ${result.pagesAdded} ${pageType.displayName} to the tome. " +
                        "It now has ${result.chargesTotal} charges."
                )
            is AddPagesResult.MixedPageType -> {
                check(invAdd(inv, InvObj(page, pages)).success)
                mes("You must remove the ${result.current.displayName} first.")
            }
            AddPagesResult.Full,
            AddPagesResult.InvalidTome -> {
                check(invAdd(inv, InvObj(page, pages)).success)
                mes("You are unable to add those pages to the tome.")
            }
        }
    }

    private fun ProtectedAccess.checkCharges(tome: InvObj?) {
        val charges = TomeOfFireState.charges(tome)
        val pages = TomeOfFireState.pageType(tome)?.displayName ?: "pages"
        mes("Your tome contains $charges charges from $pages.")
    }

    private suspend fun ProtectedAccess.removePages(inventory: Inventory, tomeSlot: Int) {
        val tome = inventory[tomeSlot]
        val charges = TomeOfFireState.charges(tome)
        val pageType = TomeOfFireState.pageType(tome)
        if (charges <= 0 || pageType == null) {
            emptyPagesMessage()
            return
        }
        val pagesReturned = charges / TomeOfFireState.CHARGES_PER_PAGE
        if (pagesReturned > 0 && inv.none { it?.id == pageType.itemId } && inv.freeSpace() == 0) {
            mes("You do not have enough inventory space to remove the pages.")
            return
        }
        val confirmed =
            choice2(
                "Remove all pages.",
                true,
                "Keep them in the tome.",
                false,
                title = "Remove ${pageType.displayName}?",
            )
        if (!confirmed) {
            return
        }

        val removed = TomeOfFireState.removeAllPages(inventory, tomeSlot) ?: return
        if (removed.pagesReturned > 0) {
            val page = requireNotNull(ServerCacheManager.getItem(removed.pageType.itemId))
            check(invAdd(inv, InvObj(page, removed.pagesReturned)).success)
        }
        if (removed.partialPageChargesLost > 0) {
            mes("The partially used page crumbles as you remove the remaining pages.")
        }
        mes("You remove ${removed.pagesReturned} ${removed.pageType.displayName} from the tome.")
    }

    private fun ProtectedAccess.emptyPagesMessage() {
        mes("The tome is empty. Use burnt or searing pages on it to charge it.")
    }
}
