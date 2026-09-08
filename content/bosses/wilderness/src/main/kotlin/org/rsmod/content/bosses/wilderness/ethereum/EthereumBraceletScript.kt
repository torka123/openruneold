package org.rsmod.content.bosses.wilderness.ethereum

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class EthereumBraceletScript
@Inject
constructor(private val bracelet: EthereumBraceletService, private val charges: ObjChargeManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(CHARGED_BRACELET, REVENANT_ETHER) { charge(it.firstSlot) }
        onOpHeldU(UNCHARGED_BRACELET, REVENANT_ETHER) { charge(it.firstSlot) }

        onOpHeld3(CHARGED_BRACELET) { checkCharges(inv, it.slot) }
        onOpHeld4(CHARGED_BRACELET) { toggleAbsorption() }
        onOpHeld5(CHARGED_BRACELET) { uncharge(inv, it.slot) }
        onOpHeld3(UNCHARGED_BRACELET) { toggleAbsorption() }
        onOpHeld4(UNCHARGED_BRACELET) { dismantle(inv, it.slot) }

        onOpWorn2(CHARGED_BRACELET) { checkCharges(player.worn, Wearpos.Hands.slot) }
        onOpWorn3(CHARGED_BRACELET) { toggleAbsorption() }
        onOpWorn4(CHARGED_BRACELET) { uncharge(player.worn, Wearpos.Hands.slot) }
        onOpWorn2(UNCHARGED_BRACELET) { toggleAbsorption() }
        onOpWorn3(UNCHARGED_BRACELET) { dismantle(player.worn, Wearpos.Hands.slot) }
    }

    private fun ProtectedAccess.charge(braceletSlot: Int) {
        val held = inv[braceletSlot] ?: return
        if (held.id != CHARGED_BRACELET_ID && held.id != UNCHARGED_BRACELET_ID) return
        val current = bracelet.charges(held)
        if (current >= MAX_ETHER_CHARGES) {
            mes("Your bracelet of ethereum is already fully charged.")
            return
        }
        val etherAvailable = inv.sumOf { if (it?.id == REVENANT_ETHER_ID) it.count else 0 }
        if (etherAvailable == 0) {
            mes("You don't have any revenant ether to add to the bracelet.")
            return
        }
        val add = min(MAX_ETHER_CHARGES - current, etherAvailable)
        if (invDel(inv, REVENANT_ETHER, add).failure) return
        val result =
            charges.addCharges(inv, braceletSlot, add, ETHER_CHARGES_VAROBJ, MAX_ETHER_CHARGES)
        check(result is ObjChargeManager.Charge.Success)
        mes("You add $add revenant ether to your bracelet. It now has ${current + add} charges.")
    }

    private fun ProtectedAccess.checkCharges(inventory: Inventory, slot: Int) {
        val held = inventory[slot] ?: return
        if (held.id != CHARGED_BRACELET_ID) return
        mes("Your bracelet of ethereum has ${bracelet.charges(held)} charges remaining.")
    }

    private fun ProtectedAccess.toggleAbsorption() {
        player.ethereumAbsorptionEnabled = !player.ethereumAbsorptionEnabled
        if (player.ethereumAbsorptionEnabled) {
            mes("Your bracelet will now absorb revenant ether drops.")
        } else {
            mes("Your bracelet will no longer absorb revenant ether drops.")
        }
    }

    private suspend fun ProtectedAccess.uncharge(inventory: Inventory, slot: Int) {
        val held = inventory[slot] ?: return
        if (held.id != CHARGED_BRACELET_ID) return
        val stored = bracelet.charges(held)
        if (stored <= 0) {
            charges.removeAllCharges(inventory, slot, ETHER_CHARGES_VAROBJ)
            return
        }
        if (!invCanFitEther()) {
            mes("You don't have enough inventory space to remove the revenant ether.")
            return
        }
        val confirmed =
            choice2(
                "Yes, uncharge it.",
                true,
                "No.",
                false,
                title = "Remove all $stored charges from the bracelet?",
            )
        if (!confirmed) return
        val removed = charges.removeAllCharges(inventory, slot, ETHER_CHARGES_VAROBJ)
        check(removed == stored)
        check(invAdd(inv, REVENANT_ETHER, removed).success)
        mes("You uncharge the bracelet and recover $removed revenant ether.")
    }

    private suspend fun ProtectedAccess.dismantle(inventory: Inventory, slot: Int) {
        val held = inventory[slot] ?: return
        if (held.id != UNCHARGED_BRACELET_ID) return
        if (inventory === player.worn && !invCanFitEther()) {
            mes("You don't have enough inventory space to dismantle the bracelet.")
            return
        }
        val confirmed =
            choice2(
                "Yes, dismantle it.",
                true,
                "No.",
                false,
                title = "Dismantle the bracelet for 250 revenant ether?",
            )
        if (!confirmed) return
        if (invDel(inventory, UNCHARGED_BRACELET, 1, slot = slot).failure) return
        check(invAdd(inv, REVENANT_ETHER, 250).success)
        mes("You dismantle the bracelet and receive 250 revenant ether.")
    }

    private fun ProtectedAccess.invCanFitEther(): Boolean =
        inv.freeSpace() > 0 || inv.any { it?.id == REVENANT_ETHER_ID }
}
