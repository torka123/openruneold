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
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val MOON_KEY = "obj.varlamore_key"
private const val MOON_HALF_1 = "obj.varlamore_key_half_1"
private const val MOON_HALF_2 = "obj.varlamore_key_half_2"

private const val VARP_KC = "varp.kc_moon_chest"

private const val CHEST_CLOSED = "loc.varlamore_moon_chestclosed"
private const val CHEST_OPEN = "loc.varlamore_moon_chestopen"
private const val CHEST_OPEN_TICKS = 2

class MoonChestScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
    private val collectionLog: CollectionLogService,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CHEST_CLOSED) { openChest(it.loc) }
        onOpHeldU(MOON_HALF_1, MOON_HALF_2) { combineKeyHalves() }
    }

    private suspend fun ProtectedAccess.openChest(loc: BoundLocInfo) {
        arriveDelay()

        if (invTotal(inv, MOON_KEY) == 0) {
            mes("The chest is securely locked.")
            return
        }

        invDel(inv, MOON_KEY)
        anim("seq.human_openchest")
        locRepo.change(loc, CHEST_OPEN, CHEST_OPEN_TICKS)
        mes("You find some treasure in the chest!")

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
                    "lunar-chest",
                ),
            eventId = "${player.uid}:$chestCount:$awardIndex",
        )
    }

    private fun ProtectedAccess.combineKeyHalves() {
        invDel(inv, MOON_HALF_1, 1, MOON_HALF_2, 1)
        invAddOrDrop(objRepo, MOON_KEY)
        mes("You join the two halves of the key together.")
    }

}
