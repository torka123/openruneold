package org.rsmod.content.minigames.corruptedgauntlet

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenMain
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.content.other.consumables.ConsumableDelayState
import org.rsmod.content.other.consumables.ConsumableType
import org.rsmod.content.other.consumables.potion.PotionEffectService
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CorruptedGauntletWorld
@Inject
constructor(
    private val runs: CorruptedGauntletRunManager,
    private val potionEffects: PotionEffectService,
    private val rewards: CorruptedGauntletRewardService,
) : PluginScript() {
    override fun ScriptContext.startup() {
        runs.validateAssets()

        val entrance = loc(CorruptedGauntletConfig.Loc.ENABLED_ENTRANCE)
        onOpLoc1(entrance) {
            mes("The standard Gauntlet is unavailable here. Choose Enter-corrupted to begin.")
        }
        onOpLoc2(entrance) { runs.enter(player) }
        onOpNpc1("npc.gauntlet_instructor") { talkToGauntletInstructor(it.npc) }

        val barrier = loc(CorruptedGauntletConfig.Loc.BOSS_BARRIER)
        onOpLoc1(barrier) {
            arriveDelay()
            runs.startBoss(player)
        }
        onOpLoc2(barrier) {
            arriveDelay()
            runs.startBoss(player)
        }

        val bossEscape = loc(CorruptedGauntletConfig.Loc.BOSS_ESCAPE_BARRIER)
        onOpLoc1(bossEscape) { runs.leave(player) }
        onOpLoc2(bossEscape) { runs.leave(player) }

        CorruptedGauntletConfig.Loc.unlitNodes.forEach { nodeId ->
            val node = loc(nodeId)
            onOpLoc1(node) {
                arriveDelay()
                runs.lightNode(player, it.loc)
            }
            onOpLoc2(node) {
                arriveDelay()
                runs.lightNode(player, it.loc)
            }
        }

        val exit = loc(CorruptedGauntletConfig.Loc.EXIT_PLATFORM)
        onOpLoc1(exit) { runs.leave(player) }
        onOpLoc2(exit) { runs.leave(player) }

        val scoreboard = loc(CorruptedGauntletConfig.Loc.SCOREBOARD)
        onOpLoc1(scoreboard) { CorruptedGauntletScoreboard.show(this, rewards.scoreboard(player)) }
        onOpLoc2(scoreboard) { CorruptedGauntletScoreboard.show(this, rewards.scoreboard(player)) }
        onIfModalButton(CorruptedGauntletScoreboard.closeComponentName()) {
            player.ifClose(eventBus)
        }

        CorruptedGauntletResource.entries.forEach { resource ->
            onOpLoc1(loc(resource.nodeId)) { event -> gather(event.loc, resource) }
        }

        val range = loc(CorruptedGauntletConfig.Loc.RANGE)
        onOpLoc1(range) { cook() }
        val pump = loc(CorruptedGauntletConfig.Loc.PUMP)
        onOpLoc1(pump) { fillVials() }
        onOpLocU(pump, item(CorruptedGauntletConfig.Obj.VIAL)) { fillVials() }
        onOpLocU(
            loc(CorruptedGauntletConfig.Loc.FISHING_SPOT),
            item(CorruptedGauntletConfig.Obj.VIAL),
        ) {
            fillVials()
        }
        onOpLocU(
            loc(CorruptedGauntletConfig.Loc.DEPLETED_FISHING_SPOT),
            item(CorruptedGauntletConfig.Obj.VIAL),
        ) {
            fillVials()
        }
        val bowl = loc(CorruptedGauntletConfig.Loc.SINGING_BOWL)
        onOpLoc1(bowl) { openCrafting() }
        onOpLoc1(loc(CorruptedGauntletConfig.Loc.TOOL_STORAGE)) { recoverTools() }
        onOpLoc1(loc(CorruptedGauntletConfig.Loc.RECIPES)) { openRecipes() }
        onOpLoc1(loc(CorruptedGauntletConfig.Loc.EGNIOL_BOOK)) { readEgniolBook() }

        val closedChest = loc(CorruptedGauntletConfig.Loc.REWARD_CHEST_CLOSED)
        onOpLoc1(closedChest) {
            arriveDelay()
            if (runs.claim(player)) anim("seq.human_openchest")
        }

        val pestle = item(CorruptedGauntletConfig.Obj.PESTLE_AND_MORTAR)
        val shards = item(CorruptedGauntletConfig.Obj.SHARDS)
        onOpHeldU(pestle, shards) {
            if (runs.grindShards(player)) mes("You grind ten corrupted shards into crystal dust.")
            else mes("You need ten corrupted shards.")
        }
        onOpHeldU(
            item(CorruptedGauntletConfig.Obj.VIAL_OF_WATER),
            item(CorruptedGauntletConfig.Obj.GRYM_LEAF),
        ) {
            if (runs.mixPotion(player, secondStep = false))
                mes("You add the grym leaf to the vial.")
        }
        onOpHeldU(
            item(CorruptedGauntletConfig.Obj.UNFINISHED_POTION),
            item(CorruptedGauntletConfig.Obj.DUST),
        ) {
            if (runs.mixPotion(player, secondStep = true)) mes("You finish an Egniol potion.")
        }
        onOpHeld1(item(CorruptedGauntletConfig.Obj.TELEPORT_CRYSTAL)) {
            if (!runs.teleportToStart(player)) mes("The teleport crystal does not respond here.")
        }
        onOpHeld1(item(CorruptedGauntletConfig.Obj.ESCAPE_CRYSTAL)) {
            if (!runs.leave(player)) mes("The escape crystal does not respond here.")
        }
        onOpHeld1(item(CorruptedGauntletConfig.Obj.PADDLEFISH)) {
            eatPaddlefish(it.slot, it.type.id)
        }
        CorruptedGauntletConsumableRules.egniolDoseIds.forEach { potionId ->
            onOpHeld1(item(potionId)) { drinkEgniol(it.slot, it.type.id) }
        }

        onCommand("cg") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Go to the Gauntlet chest or create a seeded test run"
            invalidArgs = "Use ::cg [seed]"
            cheat {
                val seed = args.firstOrNull()?.toLongOrNull()
                if (args.isEmpty()) runs.teleportToRewardChest(player)
                else runs.enter(player, seed ?: System.nanoTime(), bypass = true)
            }
        }
        onCommand("corruptedgauntlet") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create a private Corrupted Gauntlet"
            invalidArgs = "Use ::corruptedgauntlet [seed]"
            cheat {
                val seed = args.firstOrNull()?.toLongOrNull()
                runs.enter(player, seed ?: System.nanoTime(), bypass = true)
            }
        }
        onCommand("cgboss") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Move the current Corrupted Gauntlet to Hunllef"
            invalidArgs = "Use ::cgboss"
            cheat {
                if (!runs.startBoss(player, forced = true))
                    player.mes("No active Corrupted Gauntlet.")
            }
        }
        onCommand("cgstatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect the current Corrupted Gauntlet"
            invalidArgs = "Use ::cgstatus"
            cheat { player.mes(runs.status(player)) }
        }
        onCommand("leavecg") {
            desc = "Leave the current Corrupted Gauntlet"
            invalidArgs = "Use ::leavecg"
            cheat {
                if (!runs.leave(player)) player.mes("You are not inside the Corrupted Gauntlet.")
            }
        }
    }

    private suspend fun ProtectedAccess.gather(
        clicked: org.rsmod.game.loc.BoundLocInfo,
        resource: CorruptedGauntletResource,
    ) {
        arriveDelay()
        val animation =
            when (resource) {
                CorruptedGauntletResource.ORE -> 8_348
                CorruptedGauntletResource.BARK -> 8_325
                CorruptedGauntletResource.FISH -> 8_337
                CorruptedGauntletResource.GRYM,
                CorruptedGauntletResource.LINUM -> 8_323
            }
        player.anim(animation)
        delay(2)
        if (runs.harvest(player, clicked) == null)
            player.mes("There is nothing left to gather there.")
        resetAnim()
    }

    private suspend fun ProtectedAccess.talkToGauntletInstructor(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "Would you like to take on the Corrupted Gauntlet?")
            if (choice2("Start a New Game", 1, "Not now.", 2) == 1) runs.enter(player)
        }
    }

    private suspend fun ProtectedAccess.cook() {
        arriveDelay()
        val cooked = runs.cookAll(player)
        if (cooked == 0) {
            mes("You do not have any raw paddlefish to cook.")
            return
        }
        anim("seq.human_cookrange")
        delay(cooked.coerceAtMost(20))
        resetAnim()
        mes("You cook $cooked paddlefish.")
    }

    private suspend fun ProtectedAccess.fillVials() {
        arriveDelay()
        val filled = runs.fillVials(player)
        mes(
            if (filled == 0) "You do not have any empty vials."
            else "You fill $filled vial${if (filled == 1) "" else "s"} with water."
        )
    }

    private suspend fun ProtectedAccess.recoverTools() {
        arriveDelay()
        val recovery = runs.recoverMissingTools(player)
        when {
            recovery.recovered == 0 && recovery.unavailable == 0 ->
                mes("You already have all of the Corrupted Gauntlet tools.")
            recovery.unavailable > 0 ->
                mes(
                    "You recover ${recovery.recovered} tool(s), but need ${recovery.unavailable} more free inventory space."
                )
            else -> mes("You recover ${recovery.recovered} Corrupted Gauntlet tool(s).")
        }
    }

    private suspend fun ProtectedAccess.openRecipes() {
        if (!runs.isPreparing(player)) {
            mes("You can only consult the recipes during preparation.")
            return
        }
        ifOpenMain(
            RSCM.getReverseMapping(RSCMType.INTERFACE, CorruptedGauntletConfig.Interface.RECIPES)
        )
        player.runClientScript(CorruptedGauntletConfig.ClientScript.RECIPES)
    }

    private suspend fun ProtectedAccess.readEgniolBook() {
        if (!runs.isPreparing(player)) {
            mes("You can only consult this book during preparation.")
            return
        }
        mesbox(
            "Egniol potions restore 40% run energy, restore Prayer points, and provide a stamina effect."
        )
        mesbox(
            "Fill a vial with water, add a grym leaf, then add ten corrupted dust made by grinding ten corrupted shards."
        )
    }

    private fun ProtectedAccess.eatPaddlefish(slot: Int, itemId: Int) {
        if (!runs.isActive(player)) return
        if (!ConsumableDelayState.canConsume(this, ConsumableType.FOOD)) return
        if (!runs.consumePaddlefish(player, slot, itemId)) return
        val restored =
            minOf(
                CorruptedGauntletConsumableRules.PADDLEFISH_HEAL,
                (player.baseHitpointsLvl - player.hitpoints).coerceAtLeast(0),
            )
        anim("seq.human_eat")
        soundSynth(2_393)
        if (restored > 0) statAdd("stat.hitpoints", restored, 0)
        ConsumableDelayState.recordConsumption(
            access = this,
            type = ConsumableType.FOOD,
            consumeDelay = 3,
            combatDelay = 3,
        )
        player.resetFaceEntity()
        mes("You eat the paddlefish.")
    }

    private fun ProtectedAccess.drinkEgniol(slot: Int, itemId: Int) {
        if (!runs.isActive(player)) return
        if (!ConsumableDelayState.canConsume(this, ConsumableType.POTION)) return
        val remaining = runs.consumeEgniol(player, slot, itemId) ?: return
        anim("seq.human_drink")
        soundSynth(2_402)
        potionEffects.apply(this, staminaEffect)
        val extraRunEnergy =
            (CorruptedGauntletConsumableRules.EGNIOL_RUN_ENERGY_PERCENT -
                CorruptedGauntletConsumableRules.STANDARD_STAMINA_RUN_ENERGY_PERCENT) * 10
        player.runEnergy = (player.runEnergy + extraRunEnergy).coerceAtMost(1_000)
        UpdateRun.energy(player, player.runEnergy)
        val basePrayer = player.statBase("stat.prayer")
        val currentPrayer = player.stat("stat.prayer")
        val restoredPrayer =
            minOf(
                CorruptedGauntletConsumableRules.egniolPrayerRestore(basePrayer),
                (basePrayer - currentPrayer).coerceAtLeast(0),
            )
        if (restoredPrayer > 0) statAdd("stat.prayer", restoredPrayer, 0)
        ConsumableDelayState.recordConsumption(
            access = this,
            type = ConsumableType.POTION,
            consumeDelay = 3,
            combatDelay = 0,
        )
        player.resetFaceEntity()
        mes("You drink some of your Egniol potion.")
        mes(
            when (remaining) {
                0 -> "You have finished your potion."
                1 -> "You have 1 dose of potion left."
                else -> "You have $remaining doses of potion left."
            }
        )
    }

    private suspend fun ProtectedAccess.openCrafting() {
        if (!runs.isPreparing(player)) {
            mes("The singing bowl can only be used during preparation.")
            return
        }
        when (
            choice4(
                "Armour",
                1,
                "Weapons",
                2,
                "Supplies",
                3,
                "Cancel",
                4,
                title = "Corrupted singing bowl",
            )
        ) {
            1 -> craftArmour()
            2 -> craftWeapon()
            3 -> craftSupplies()
        }
    }

    private suspend fun ProtectedAccess.craftArmour() {
        val slot = choice3("Helm", 1, "Body", 2, "Legs", 3, title = "Choose armour")
        val tier = choice3("Basic", 1, "Attuned", 2, "Perfected", 3, title = "Choose tier")
        val ids =
            when (slot) {
                1 -> intArrayOf(23_840, 23_841, 23_842)
                2 -> intArrayOf(23_843, 23_844, 23_845)
                else -> intArrayOf(23_846, 23_847, 23_848)
            }
        runs.craft(player, ids[tier - 1])
    }

    private suspend fun ProtectedAccess.craftWeapon() {
        val weapon = choice3("Halberd", 1, "Staff", 2, "Bow", 3, title = "Choose weapon")
        val tier = choice3("Basic", 1, "Attuned", 2, "Perfected", 3, title = "Choose tier")
        val ids =
            when (weapon) {
                1 -> intArrayOf(23_849, 23_850, 23_851)
                2 -> intArrayOf(23_852, 23_853, 23_854)
                else -> intArrayOf(23_855, 23_856, 23_857)
            }
        runs.craft(player, ids[tier - 1])
    }

    private suspend fun ProtectedAccess.craftSupplies() {
        when (
            choice4(
                "Vial",
                1,
                "Teleport crystal",
                2,
                "Crystal paddlefish",
                3,
                "Escape crystal",
                4,
                title = "Choose supply",
            )
        ) {
            1 -> runs.craft(player, 23_879)
            2 -> runs.craft(player, 23_858)
            3 -> runs.craft(player, 25_958)
            4 -> runs.craft(player, 25_959)
        }
    }

    private fun loc(id: Int) = requireNotNull(ServerCacheManager.getObject(id))

    private fun item(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)

    private val staminaEffect: PotionEffectRow
        get() = PotionEffectRow.getRow("dbrow.effect_stamina".asRSCM(RSCMType.DBROW))
}
