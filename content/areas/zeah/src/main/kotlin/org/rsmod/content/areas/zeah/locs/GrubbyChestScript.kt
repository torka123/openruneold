package org.rsmod.content.areas.zeah.locs

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val GRUBBY_KEY = "obj.hosdun_grubby_key"

private const val CHEST_LOC = "loc.hosdun_grubby_chest"
private const val VARBIT_CHEST = "varbit.hosdun_chest_status"
private const val VARP_KC = "varp.kc_grubby_chest"

class GrubbyChestScript @Inject constructor(
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
    private val collectionLog: CollectionLogService,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CHEST_LOC) { openChest() }
        onOpLoc2(CHEST_LOC) { checkCount() }
    }

    private suspend fun ProtectedAccess.openChest() {
        arriveDelay()

        if (invTotal(inv, GRUBBY_KEY) == 0) {
            mes("You don't have a key which can open this chest.")
            return
        }

        invDel(inv, GRUBBY_KEY)
        anim("seq.human_openchest")
        VarPlayerIntMapSetter.toggle(player, VARBIT_CHEST)
        mes("You find treasure and supplies within the chest.")

        val chestCount = player.vars[VARP_KC] + 1
        VarPlayerIntMapSetter.set(player, VARP_KC, chestCount)

        val table = dropRegistry.forLoc(CHEST_LOC) ?: return
        when (val result = table.roll(player, ArgMap()).flatten()) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> giveDrop(result.result, chestCount, 0)
            is RollResult.ListOf ->
                result.results.forEachIndexed { index, drop ->
                    giveDrop(drop, chestCount, index)
                }
        }

        delay(1)
        VarPlayerIntMapSetter.toggle(player, VARBIT_CHEST)
    }

    private fun ProtectedAccess.checkCount() {
        val count = player.vars[VARP_KC]
        mes("You have opened the Grubby Chest $count ${if (count == 1) "time" else "times"}.")
    }

    private fun ProtectedAccess.giveDrop(
        drop: DropRollItem,
        chestCount: Int,
        awardIndex: Int,
    ) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        val amount = drop.rollCount(random)
        invAddOrDrop(objRepo, obj, amount)
        collectionLog.record(
            player = player,
            itemId = obj.asRSCM(RSCMType.OBJ),
            amount = amount,
            source =
                CollectionLogAwardSource(
                    CollectionLogAwardKind.SpecialContainer,
                    "grubby-chest",
                ),
            eventId = "${player.uid}:$chestCount:$awardIndex",
        )
    }
}
