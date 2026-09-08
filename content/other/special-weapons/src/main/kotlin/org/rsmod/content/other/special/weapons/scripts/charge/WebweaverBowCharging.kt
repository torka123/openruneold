package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.math.min
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

/**
 * Webweaver activation and ammo are deliberately handled outside ObjChargeManager because the
 * revision-240 item types do not expose charged_variant or uncharged_variant parameters.
 */
class WebweaverBowCharging : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(WEBWEAVER_UNCHARGED, REVENANT_ETHER) { charge(it.firstSlot) }
        onOpHeldU(WEBWEAVER_CHARGED, REVENANT_ETHER) { charge(it.firstSlot) }
        onOpHeld3(WEBWEAVER_CHARGED) { checkCharges(it.inventory[it.slot]) }
        onOpHeld5(WEBWEAVER_CHARGED) { uncharge(it.inventory, it.slot) }
        onOpWorn2(WEBWEAVER_CHARGED) { checkCharges(player.righthand) }
    }

    private fun ProtectedAccess.charge(bowSlot: Int) {
        val bow = inv[bowSlot] ?: return
        val activated = bow.isType(WEBWEAVER_CHARGED)
        if (!activated && !bow.isType(WEBWEAVER_UNCHARGED)) {
            return
        }

        val available = invTotal(inv, REVENANT_ETHER)
        if (!activated && available < WebweaverBowChargePolicy.ACTIVATION_COST) {
            mes(
                "You need at least ${WebweaverBowChargePolicy.ACTIVATION_COST} revenant ether " +
                    "to activate your Webweaver bow."
            )
            return
        }

        val current = charges(bow)
        if (current >= WebweaverBowChargePolicy.MAX_AMMO_CHARGES) {
            mes("Your Webweaver bow is already fully charged.")
            return
        }

        val plan =
            WebweaverBowChargePolicy.planLoad(
                activated = activated,
                currentAmmo = current,
                availableEther = available,
            ) ?: return
        if (invDel(inv, REVENANT_ETHER, plan.totalEtherRemoved).failure) {
            return
        }

        val packed = bow.vars.withBits(chargeVarBits(), plan.totalAmmo)
        inv[bowSlot] = InvObj(WEBWEAVER_CHARGED, vars = packed)
        if (!activated && plan.ammoAdded == 0) {
            mes("You activate your Webweaver bow. Add more revenant ether before firing it.")
        } else {
            mes(
                "You add ${plan.ammoAdded} revenant ether to your Webweaver bow. " +
                    "It now has ${plan.totalAmmo} usable charges."
            )
        }
    }

    private fun ProtectedAccess.checkCharges(bow: InvObj?) {
        val current = charges(bow)
        mes("Your Webweaver bow has $current usable charges remaining.")
    }

    private suspend fun ProtectedAccess.uncharge(inventory: Inventory, bowSlot: Int) {
        val bow = inventory[bowSlot] ?: return
        if (!bow.isType(WEBWEAVER_CHARGED)) {
            return
        }
        val stored = charges(bow)
        val refund = WebweaverBowChargePolicy.refundForUncharge(stored)
        if (REVENANT_ETHER !in inventory && inventory.freeSpace() < 1) {
            mes("You do not have enough inventory space to remove the revenant ether.")
            return
        }
        val confirmed =
            choice2(
                "Yes, uncharge it.",
                true,
                "No.",
                false,
                title = "Remove all $refund revenant ether from the bow?",
            )
        if (!confirmed) {
            return
        }

        val packed = bow.vars.withBits(chargeVarBits(), 0)
        inventory[bowSlot] = InvObj(WEBWEAVER_UNCHARGED, vars = packed)
        check(invAdd(inv, REVENANT_ETHER, refund).success)
        mes("You uncharge your Webweaver bow and recover $refund revenant ether.")
    }

    private fun charges(bow: InvObj?): Int = bow?.vars?.getBits(chargeVarBits()) ?: 0

    private fun chargeVarBits() =
        requireNotNull(
            ServerCacheManager.getVarObj(ETHER_VAROBJ.asRSCM(RSCMType.VAROBJ))
        ).bits

    private companion object {
        const val WEBWEAVER_UNCHARGED: String = "obj.wild_cave_webweaver_uncharged"
        const val WEBWEAVER_CHARGED: String = "obj.wild_cave_webweaver_charged"
        const val REVENANT_ETHER: String = "obj.wild_cave_shard"
        const val ETHER_VAROBJ: String = "varobj.charges_16383"
    }
}

internal object WebweaverBowChargePolicy {
    const val ACTIVATION_COST: Int = 1_000
    const val MAX_AMMO_CHARGES: Int = 16_000

    data class LoadPlan(
        val activationEther: Int,
        val ammoAdded: Int,
        val totalAmmo: Int,
    ) {
        val totalEtherRemoved: Int
            get() = activationEther + ammoAdded
    }

    fun planLoad(
        activated: Boolean,
        currentAmmo: Int,
        availableEther: Int,
    ): LoadPlan? {
        require(currentAmmo in 0..MAX_AMMO_CHARGES)
        require(availableEther >= 0)

        val activationEther = if (activated) 0 else ACTIVATION_COST
        if (availableEther < activationEther) {
            return null
        }

        val ammoAdded =
            min(
                MAX_AMMO_CHARGES - currentAmmo,
                availableEther - activationEther,
            )
        if (activated && ammoAdded == 0) {
            return null
        }
        return LoadPlan(
            activationEther = activationEther,
            ammoAdded = ammoAdded,
            totalAmmo = currentAmmo + ammoAdded,
        )
    }

    fun refundForUncharge(currentAmmo: Int): Int {
        require(currentAmmo in 0..MAX_AMMO_CHARGES)
        return ACTIVATION_COST + currentAmmo
    }
}
