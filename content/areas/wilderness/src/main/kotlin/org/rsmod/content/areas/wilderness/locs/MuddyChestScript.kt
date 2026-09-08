package org.rsmod.content.areas.wilderness.locs

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
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val MUDDY_KEY = "obj.muddy_key"

private const val CHEST_CLOSED = "loc.muddy_chestclosed"
private const val CHEST_OPEN = "loc.muddy_chestopen"
private const val CHEST_OPEN_TICKS = 2
private const val VARP_KC = "varp.kc_muddy_chest"

class MuddyChestScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
    private val collectionLog: CollectionLogService,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CHEST_CLOSED) { openChest(it.loc) }
    }

    private suspend fun ProtectedAccess.openChest(loc: BoundLocInfo) {
        arriveDelay()

        if (invTotal(inv, MUDDY_KEY) == 0) {
            mes("The chest is locked. You need a muddy key to open it.")
            return
        }

        invDel(inv, MUDDY_KEY)
        anim("seq.human_openchest")
        locRepo.change(loc, CHEST_OPEN, CHEST_OPEN_TICKS)
        mes("You unlock the chest with your key.")

        val chestCount = player.vars[VARP_KC] + 1
        VarPlayerIntMapSetter.set(player, VARP_KC, chestCount)

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
                    "muddy-chest",
                ),
            eventId = "${player.uid}:$chestCount:$awardIndex",
        )
    }
}
