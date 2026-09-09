package org.rsmod.content.other.pouches.lootingbag

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import dev.openrune.types.ItemServerType
import dev.openrune.types.util.UncheckedType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerPersistenceHints
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

object LootingBags {
    const val CLOSED = "obj.looting_bag"
    const val OPEN = "obj.looting_bag_open"
    const val INV = "inv.looting_bag"
    const val CAPACITY = 28

    val ALL = listOf(CLOSED, OPEN)

    val ids: Set<Int> by lazy { ALL.map { it.asRSCM(RSCMType.OBJ) }.toSet() }
    val openId: Int by lazy { OPEN.asRSCM(RSCMType.OBJ) }
    private val coinsId: Int by lazy { "obj.coins".asRSCM(RSCMType.OBJ) }

    /** Why [obj] cannot go into the bag, or `null` when it can. */
    fun rejection(obj: InvObj, type: ItemServerType): String? =
        when {
            obj.id in ids -> "You may be surprised to learn that bagception is not permitted."
            !type.tradeable || obj.id == coinsId -> "Only tradeable items can be put in the bag."
            else -> null
        }
}

/**
 * Legacy upstream storage key. This server's revision-240 bag inventory is permanent and is saved
 * by CharacterInventoryPipeline, like the inventory and bank. Never restore a stale attribute over
 * that authority. The mirror is only used with a temporary-inventory definition.
 */
val LOOTING_BAG_ATTR: AttributeKey<MutableList<Int>> = AttributeKey(persistenceKey = "looting_bag")

val Player.lootingBag: Inventory
    get() = invMap.getOrPut(LootingBags.INV)

fun Player.hasLootingBag(): Boolean = inv.any { it != null && it.id in LootingBags.ids }

fun Player.hasOpenLootingBag(): Boolean = inv.any { it != null && it.id == LootingBags.openId }

fun Player.saveLootingBag() {
    if (lootingBag.type.scope == InvScope.Perm) {
        attr.remove(LOOTING_BAG_ATTR)
        PlayerPersistenceHints.notify(this)
        return
    }
    val encoded = LootingBagCodec.encode(lootingBag.objs)
    if (encoded.isEmpty()) {
        attr.remove(LOOTING_BAG_ATTR)
    } else {
        attr[LOOTING_BAG_ATTR] = encoded
    }
}

fun Player.restoreLootingBag() {
    if (lootingBag.type.scope == InvScope.Perm) {
        attr.remove(LOOTING_BAG_ATTR)
        return
    }
    val saved = attr[LOOTING_BAG_ATTR] ?: return
    val bag = lootingBag
    val decoded = LootingBagCodec.decode(saved, bag.size)
    for (slot in bag.indices) {
        bag[slot] = decoded[slot]
    }
}

object LootingBagCodec {
    fun encode(objs: Array<InvObj?>): MutableList<Int> {
        val encoded = ArrayList<Int>()
        // Retain the upstream pair format for ordinary items; versioned triples preserve the
        // native per-item vars used for charges/provenance in this tree.
        val withVars = objs.any { it != null && it.vars != 0 }
        if (withVars) encoded.add(-1)
        for (obj in objs) {
            if (obj == null) {
                continue
            }
            encoded.add(obj.id)
            encoded.add(obj.count)
            if (withVars) encoded.add(obj.vars)
        }
        return encoded
    }

    /** Fills the slots in order, skipping malformed pairs; anything past [size] is dropped. */
    @OptIn(UncheckedType::class)
    fun decode(encoded: List<Int>, size: Int): Array<InvObj?> {
        val objs = arrayOfNulls<InvObj>(size)
        var slot = 0
        val withVars = encoded.firstOrNull() == -1
        val width = if (withVars) 3 else 2
        var index = if (withVars) 1 else 0
        while (index + width <= encoded.size && slot < size) {
            val id = encoded[index]
            val count = encoded[index + 1]
            val vars = if (withVars) encoded[index + 2] else 0
            index += width
            if (id <= 0 || count <= 0) {
                continue
            }
            objs[slot++] = InvObj(id, count, vars)
        }
        return objs
    }
}
