package org.rsmod.content.areas.city.prifddinas.locs

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
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val PRIF_CRYSTAL_KEY = "obj.prif_crystal_key"

private const val CHEST_CLOSED = "loc.prif_crystal_chest_closed"

private const val VARP_KC_ELVEN_CRYSTAL_CHEST = "varp.kc_elven_crystal_chest"
private const val VARBIT_CHEST = "varbit.prif_crystal_chest_open"

class ElvenCrystalChestScript @Inject constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
    private val collectionLog: CollectionLogService,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CHEST_CLOSED) { openChest(it.loc) }
        onOpLoc2(CHEST_CLOSED) { checkCount() }
    }

    private suspend fun ProtectedAccess.openChest(loc: BoundLocInfo) {
        arriveDelay()

        if (invTotal(inv, PRIF_CRYSTAL_KEY) == 0) {
            mes("The chest is locked. You need a key to open it.")
            return
        }

        invDel(inv, PRIF_CRYSTAL_KEY)
        anim("seq.human_openchest")
        VarPlayerIntMapSetter.toggle(player, VARBIT_CHEST)
        mes("You unlock the chest with your key.")

        val chestCount = player.vars[VARP_KC_ELVEN_CRYSTAL_CHEST] + 1
        VarPlayerIntMapSetter.set(
            player,
            VARP_KC_ELVEN_CRYSTAL_CHEST,
            chestCount,
        )

        val table = dropRegistry.forLoc(CHEST_CLOSED) ?: return
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
        val count = player.vars[VARP_KC_ELVEN_CRYSTAL_CHEST]
        mes("You have opened the Elven Crystal Chest $count ${if (count == 1) "time" else "times"}.")
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
                    "elven-crystal-chest",
                ),
            eventId = "${player.uid}:$chestCount:$awardIndex",
        )
    }
}
