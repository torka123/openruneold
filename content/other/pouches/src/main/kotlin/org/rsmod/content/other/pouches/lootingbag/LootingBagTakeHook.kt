package org.rsmod.content.other.pouches.lootingbag

import dev.openrune.types.ItemServerType
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.PlayerObjTakeRedirectHook
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.objtx.TransactionResultList

/**
 * With an open looting bag, tradeable loot picked up in the Wilderness goes straight into the bag.
 * Anything the bag refuses, or no longer fits, takes the normal inventory route.
 */
@OptIn(UncheckedType::class)
class LootingBagTakeHook @Inject constructor() : PlayerObjTakeRedirectHook {
    override fun prepare(
        player: Player,
        obj: Obj,
        objType: ItemServerType,
    ): TransactionResultList<InvObj>? {
        if (!player.hasOpenLootingBag()) {
            return null
        }
        if (player.vars["varbit.inside_wilderness"] != 1) {
            return null
        }
        if (LootingBags.rejection(InvObj(obj.type, obj.count), objType) != null) {
            return null
        }
        return player.invAdd(
            player.lootingBag,
            obj.type,
            obj.count,
            vars = obj.vars,
            autoCommit = false,
        )
    }

    override fun onTake(player: Player) {
        player.saveLootingBag()
    }
}
