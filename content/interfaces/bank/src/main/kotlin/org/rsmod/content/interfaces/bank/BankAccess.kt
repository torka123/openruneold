package org.rsmod.content.interfaces.bank

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifClose
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

internal val UNSAFE_DUNGEON_BANK_SESSION: AttributeKey<Boolean> = AttributeKey(temp = true)

private val unsafeDungeonBankerTile = CoordGrid(3123, 9954, 0)

fun ProtectedAccess.tryOpenBank(source: Npc? = null): Boolean {
    if (IronmanRestrictions.blockUimBank(player)) {
        return false
    }
    val unsafeDungeonBank = source?.coords == unsafeDungeonBankerTile
    if (unsafeDungeonBank) {
        if (!isWithinDistance(source, distance = 2)) {
            mes("Move closer to the banker before opening your bank.")
            return false
        }
        if (player.isInCombat()) {
            mes("You cannot use this bank while you are in combat.")
            return false
        }
        player.attr[UNSAFE_DUNGEON_BANK_SESSION] = true
    } else {
        player.attr.remove(UNSAFE_DUNGEON_BANK_SESSION)
    }
    ifOpenMainSidePair(main = "interface.bankmain", side = "interface.bankside")
    return true
}

public fun ProtectedAccess.validateUnsafeDungeonBankSession(eventBus: EventBus): Boolean {
    if (player.attr[UNSAFE_DUNGEON_BANK_SESSION] != true) {
        return true
    }
    val withinRange = distanceTo(unsafeDungeonBankerTile) <= 2
    if (withinRange && !player.isInCombat()) {
        return true
    }
    player.attr.remove(UNSAFE_DUNGEON_BANK_SESSION)
    player.ifClose(eventBus)
    if (!withinRange) {
        mes("You have moved too far away from the banker.")
    } else {
        mes("Your bank closes because you are under attack.")
    }
    return false
}
