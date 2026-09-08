package org.rsmod.content.skills.prayer.items

import dev.openrune.ServerCacheManager
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal data class PrayerScrollUnlock(
    val itemId: Int,
    val itemName: String,
    val prayerName: String,
    val unlockVarbit: String,
)

internal val PRAYER_SCROLL_UNLOCKS =
    listOf(
        PrayerScrollUnlock(
            itemId = 21079,
            itemName = "Arcane prayer scroll",
            prayerName = "Augury",
            unlockVarbit = "varbit.prayer_augury_unlocked",
        ),
        PrayerScrollUnlock(
            itemId = 30626,
            itemName = "Deadeye prayer scroll",
            prayerName = "Deadeye",
            unlockVarbit = "varbit.prayer_deadeye_unlocked",
        ),
        PrayerScrollUnlock(
            itemId = 21034,
            itemName = "Dexterous prayer scroll",
            prayerName = "Rigour",
            unlockVarbit = "varbit.prayer_rigour_unlocked",
        ),
        PrayerScrollUnlock(
            itemId = 30627,
            itemName = "Mystic vigour prayer scroll",
            prayerName = "Mystic Vigour",
            unlockVarbit = "varbit.prayer_mystic_vigour_unlocked",
        ),
        PrayerScrollUnlock(
            itemId = 21047,
            itemName = "Torn prayer scroll",
            prayerName = "Preserve",
            unlockVarbit = "varbit.prayer_preserve_unlocked",
        ),
    )

class PrayerUnlockScrollScript : PluginScript() {
    override fun ScriptContext.startup() {
        PRAYER_SCROLL_UNLOCKS.forEach { spec ->
            val type =
                requireNotNull(ServerCacheManager.getItem(spec.itemId)) {
                    "Missing prayer scroll cache item ${spec.itemId} (${spec.itemName})"
                }
            require(type.name == spec.itemName) {
                "Prayer scroll ${spec.itemId} resolved to ${type.name}, expected ${spec.itemName}"
            }
            require(type.hasInvOp(1)) {
                "${spec.itemName} does not have a first inventory option"
            }
            onOpHeld1(type) { event -> readPrayerScroll(event, spec) }
        }
    }

    private suspend fun ProtectedAccess.readPrayerScroll(
        event: HeldObjEvents.Op1,
        spec: PrayerScrollUnlock,
    ) {
        if (player.vars[spec.unlockVarbit] >= 1) {
            showAlreadyLearned(event, spec)
            return
        }

        objbox(
            event.obj,
            "You can make out some faded words on the ancient parchment. It appears to be an " +
                "archaic invocation of the gods! Would you like to absorb its power?",
        )
        val learn =
            choice2(
                "Learn ${spec.prayerName}",
                true,
                "Cancel",
                false,
                title = "This will consume the scroll.",
            )
        if (!learn) return

        if (player.vars[spec.unlockVarbit] >= 1) {
            showAlreadyLearned(event, spec)
            return
        }
        if (
            invDel(
                    event.inventory,
                    event.type.internalName,
                    count = 1,
                    slot = event.slot,
                )
                .failure
        ) {
            mes("You no longer have that prayer scroll.")
            return
        }

        VarPlayerIntMapSetter.set(player, spec.unlockVarbit, 1)
        objbox(event.obj, "You study the scroll and learn a new prayer: ${spec.prayerName}.")
    }

    private suspend fun ProtectedAccess.showAlreadyLearned(
        event: HeldObjEvents.Op1,
        spec: PrayerScrollUnlock,
    ) {
        objbox(
            event.obj,
            "You can make out some faded words on the ancient parchment. However, there is " +
                "nothing more for you to learn about ${spec.prayerName}.",
        )
    }
}
