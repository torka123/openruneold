package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.types.ItemServerType
import kotlin.math.min
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ranged.BlowpipeAmmo
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Handles the cache-packed contents of toxic blowpipes. The per-item values live in [InvObj.vars],
 * so banking, equipping, saving, and reloading use the normal inventory persistence path.
 */
class ToxicBlowpipeCharging : PluginScript() {
    override fun ScriptContext.startup() {
        for (blowpipe in BlowpipeAmmo.toxicLoadedBlowpipeAliases()) {
            onOpHeld2(blowpipe) {
                if (!BlowpipeAmmo.hasScales(it.inventory[it.slot], 1)) {
                    mes("You need to charge the blowpipe with Zulrah's scales first.")
                    return@onOpHeld2
                }
                val result = invEquip(it.slot, it.inventory)
                if (result is HeldEquipResult.Fail) {
                    result.messages.forEach(::mes)
                }
            }
            onOpHeld3(blowpipe) { checkContents(it.inventory[it.slot]) }
            onOpHeld4(blowpipe) { unloadDarts(it.inventory, it.slot) }
            onOpHeld5(blowpipe) { uncharge(it.inventory, it.slot) }
        }
        for (blowpipe in BlowpipeAmmo.toxicBlowpipeAliases()) {
            onOpHeldU(ZULRAH_SCALE, blowpipe) { loadScales(inv, it.secondSlot) }
            for (dart in BlowpipeAmmo.toxicDartAliases()) {
                onOpHeldU(dart, blowpipe) { loadDarts(inv, it.secondSlot, it.first) }
            }
        }
    }

    private fun ProtectedAccess.checkContents(blowpipe: InvObj?) {
        if (!BlowpipeAmmo.isToxic(blowpipe)) {
            return
        }
        val scales = BlowpipeAmmo.scales(blowpipe)
        val dart = BlowpipeAmmo.storedDart(blowpipe)
        if (dart == null) {
            mes("Your toxic blowpipe contains $scales Zulrah's scales and no darts.")
            return
        }
        val darts = BlowpipeAmmo.darts(blowpipe)
        mes("Your toxic blowpipe contains $scales Zulrah's scales and $darts ${dart.type.name}.")
    }

    private suspend fun ProtectedAccess.loadScales(inventory: Inventory, blowpipeSlot: Int) {
        val original = inventory[blowpipeSlot] ?: return
        val blowpipe = inventory[blowpipeSlot]
        val capacity = MAX_STORED_AMOUNT - BlowpipeAmmo.scales(blowpipe)
        if (capacity <= 0) {
            mes("Your toxic blowpipe is already full of Zulrah's scales.")
            return
        }
        val available = invTotal(inventory, ZULRAH_SCALE)
        if (available <= 0) {
            return
        }
        val maximum = min(available, capacity)
        val scaleUsingItems = inventory.objs.count(BlowpipeAmmo::isToxic)
        val requested =
            if (scaleUsingItems == 1) {
                maximum
            } else {
                min(countDialog("How many scales do you want to add? (Up to $maximum)"), maximum)
            }
        if (requested <= 0) {
            return
        }

        // OSRS puts the whole stack in immediately when this is the only scale-using item in the
        // backpack, but asks for an amount when another toxic blowpipe also needs disambiguation.
        if (invDel(inventory, ZULRAH_SCALE, requested).failure) {
            return
        }
        val stored = BlowpipeAmmo.storeScales(inventory, blowpipeSlot, requested)
        if (stored != requested) {
            // Never lose supplies or crash the player if the exact pipe changed unexpectedly.
            inventory[blowpipeSlot] = original
            check(invAdd(inventory, ZULRAH_SCALE, requested).success)
            mes("You are unable to add the scales to that blowpipe.")
            return
        }

        val total = BlowpipeAmmo.scales(inventory[blowpipeSlot])
        mes(
            "You add $stored Zulrah's scales to your toxic blowpipe. It now contains $total scales."
        )
    }

    private suspend fun ProtectedAccess.loadDarts(
        inventory: Inventory,
        blowpipeSlot: Int,
        dart: ItemServerType,
    ) {
        val blowpipe = inventory[blowpipeSlot]
        val loaded = BlowpipeAmmo.storedDart(blowpipe)
        if (loaded != null && loaded.type.id != dart.id) {
            if (loaded.type.internalName !in inventory && inventory.freeSpace() < 1) {
                mes("You do not have enough inventory space to swap the darts in your blowpipe.")
                return
            }
            val unloaded = BlowpipeAmmo.unloadDarts(inventory, blowpipeSlot) ?: return
            val recovered = unloaded.dart ?: return
            if (invAdd(inventory, recovered.type.internalName, unloaded.count).failure) {
                // The capacity check above makes this unreachable during normal play. Restore the
                // original pipe rather than risking lost ammunition if inventory state changes.
                inventory[blowpipeSlot] = blowpipe
                mes("You are unable to swap the darts in your blowpipe.")
                return
            }
        }
        val capacity = MAX_STORED_AMOUNT - BlowpipeAmmo.darts(inventory[blowpipeSlot])
        if (capacity <= 0) {
            mes("Your toxic blowpipe is already full of darts.")
            return
        }
        val available = invTotal(inventory, dart.internalName)
        if (available <= 0) {
            return
        }
        val maximum = min(available, capacity)
        val requested =
            min(countDialog("How many darts do you want to add? (Up to $maximum)"), maximum)
        if (requested <= 0) {
            return
        }
        val original = inventory[blowpipeSlot] ?: return
        if (invDel(inventory, dart.internalName, requested).failure) {
            return
        }
        val stored = BlowpipeAmmo.storeDarts(inventory, blowpipeSlot, dart, requested)
        if (stored != requested) {
            inventory[blowpipeSlot] = original
            check(invAdd(inventory, dart.internalName, requested).success)
            mes("You are unable to add those darts to your blowpipe.")
            return
        }

        val total = BlowpipeAmmo.darts(inventory[blowpipeSlot])
        mes("You add $stored ${dart.name} to your toxic blowpipe. It now contains $total darts.")
    }

    private fun ProtectedAccess.unloadDarts(inventory: Inventory, blowpipeSlot: Int) {
        val blowpipe = inventory[blowpipeSlot] ?: return
        val dart = BlowpipeAmmo.storedDart(blowpipe)
        val dartCount = BlowpipeAmmo.darts(blowpipe)
        if (dart == null || dartCount <= 0) {
            mes("Your toxic blowpipe has no darts to unload.")
            return
        }
        if (dart.type.internalName !in inventory && inventory.freeSpace() < 1) {
            mes("You do not have enough inventory space to unload your toxic blowpipe.")
            return
        }

        val contents = BlowpipeAmmo.unloadDarts(inventory, blowpipeSlot) ?: return
        val recoveredDart = contents.dart ?: return
        check(invAdd(inventory, recoveredDart.type.internalName, contents.count).success)
        mes("You unload the darts from your toxic blowpipe.")
    }

    private suspend fun ProtectedAccess.uncharge(inventory: Inventory, blowpipeSlot: Int) {
        val blowpipe = inventory[blowpipeSlot] ?: return
        val dart = BlowpipeAmmo.storedDart(blowpipe)
        val dartCount = BlowpipeAmmo.darts(blowpipe)
        val scaleCount = BlowpipeAmmo.scales(blowpipe)
        if (dartCount == 0 && scaleCount == 0) {
            mes("Your toxic blowpipe is already empty.")
            return
        }

        var slotsNeeded = 0
        if (dart != null && dart.type.internalName !in inventory) {
            slotsNeeded++
        }
        if (scaleCount > 0 && ZULRAH_SCALE !in inventory) {
            slotsNeeded++
        }
        if (inventory.freeSpace() < slotsNeeded) {
            mes("You do not have enough inventory space to unload your toxic blowpipe.")
            return
        }

        val confirmed =
            choice2("Proceed.", true, "Cancel.", false, title = "Uncharge your toxic blowpipe?")
        if (!confirmed) {
            return
        }

        val contents = BlowpipeAmmo.uncharge(inventory, blowpipeSlot) ?: return
        val recoveredDart = contents.dart
        if (recoveredDart != null && contents.dartCount > 0) {
            check(invAdd(inventory, recoveredDart.type.internalName, contents.dartCount).success)
        }
        if (contents.scaleCount > 0) {
            check(invAdd(inventory, ZULRAH_SCALE, contents.scaleCount).success)
        }
        mes("You uncharge your toxic blowpipe.")
    }

    private companion object {
        const val ZULRAH_SCALE: String = "obj.snakeboss_scale"
        const val MAX_STORED_AMOUNT: Int = 16_383
    }
}
