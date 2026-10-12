package org.rsmod.content.areas.wilderness

import dev.openrune.util.Wearpos
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpWorn2
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WildernessSkullItemsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpWorn2("obj.wild_cave_amulet") { checkForinthrySurge() }

        onOpHeld2("obj.wild_cave_amulet") {
            val confirm =
                choice2(
                    "Yes, wear it.",
                    true,
                    "No, thanks.",
                    false,
                    title = "Wearing this amulet will cause you to become skulled. Continue?",
                )
            if (confirm) {
                invEquip(it.slot)
            }
        }

        onWearposChange {
            when (wearpos) {
                Wearpos.Front -> handleAmuletChange()
                Wearpos.Back -> handleCapeChange()
                else -> Unit
            }
        }
    }

    private fun ProtectedAccess.checkForinthrySurge() {
        val remaining = player.forinthrySurgeRemainingSeconds()
        if (remaining <= 0) {
            mes("You do not currently have Forinthry Surge.")
            return
        }
        val minutes = remaining / 60
        val seconds = remaining % 60
        mes("Your Forinthry Surge has $minutes minutes and $seconds seconds remaining.")
    }

    private fun HeldEquipEvents.WearposChange.handleAmuletChange() {
        if (!objType.isType("obj.wild_cave_amulet")) {
            return
        }

        val stillWorn = player.worn[Wearpos.Front.slot]?.isType("obj.wild_cave_amulet") == true
        if (stillWorn) {
            player.applyEquipmentSkull()
        } else {
            player.unlockEquipmentSkull()
            if (player.isSkulled()) {
                player.applyPostEquipSkull()
            }
        }
    }

    private fun HeldEquipEvents.WearposChange.handleCapeChange() {
        if (!objType.isType("obj.cape_of_skulls")) {
            return
        }

        val stillWorn = player.worn[Wearpos.Back.slot]?.isType("obj.cape_of_skulls") == true
        if (stillWorn && !player.isSkullEquipLocked()) {
            player.applySkull(SkullSource.CAPE)
        }
    }
}
