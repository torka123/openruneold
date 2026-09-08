package org.rsmod.content.drops

import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.game.entity.Player

private val LOOTING_BAGS = setOf("obj.looting_bag", "obj.looting_bag_open")

public fun Player.shouldDropLootingBag(): Boolean {
    if (!coords.isInWildernessBasic()) {
        return false
    }
    return !ownsLootingBag()
}

/** Searches every loaded ownership container and both the closed and open bag forms. */
public fun Player.ownsLootingBag(): Boolean =
    invMap.values.any { inventory -> LOOTING_BAGS.any { bag -> bag in inventory } }
