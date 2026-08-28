package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onOpWorn1
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class FoundryWorld @Inject constructor(
    private val service: FoundryService,
    private val ui: FoundryUi,
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        ui.validateAssets()
        registerKovac()
        registerMouldJig()
        registerCrucible()
        registerTemperatureStations()
        registerWorkstations()
        registerStorage()
        registerPreformEquipment()
        onCommand("foundry") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to Giants' Foundry"
            invalidArgs = "Use as ::foundry"
            cheat {
                if (args.isEmpty()) {
                    protectedAccess.launch(player, "Finish your current action before teleporting to Giants' Foundry.") {
                        telejump(FOUNDRY_WORKSHOP, TeleportType.Exempt)
                    }
                }
            }
        }
    }

    private fun ScriptContext.registerKovac() {
        val kovac = RSCM.getReverseMapping(RSCMType.NPC, KOVAC_ACTIVITY_NPC)
        onOpNpc1(kovac) {
            arriveDelay()
            mes("Kovac says: Bring me a finished sword, or choose Commission to begin a new order.")
        }
        onOpNpc3(kovac) {
            arriveDelay()
            handInSword()
        }
        onOpNpc4(kovac) {
            arriveDelay()
            if (!hasEmptyFoundryHands()) return@onOpNpc4
            when (val result = service.assignCommission(player)) {
                is FoundryServiceResult.Assigned -> {
                    sync(result.state)
                    mes("Kovac assigns a ${result.commission.first.name.lowercase()} ${result.commission.second.name.lowercase()} sword commission. Set up the mould jig.")
                }
                else -> show(result)
            }
        }
        onOpNpc5(kovac) {
            arriveDelay()
            when (val state = service.state(player)) {
                is FoundryServiceResult.Changed -> {
                    ui.openRewardShop(player, state.state)
                    mes("You have ${state.state.reputation} Foundry Reputation.")
                }
                else -> show(state)
            }
        }
    }

    private fun ScriptContext.registerMouldJig() {
        onFoundryLoc1(MOULD_JIG_EMPTY) { setupMoulds() }
        onFoundryLoc1(MOULD_JIG_SETUP) { checkMouldJig() }
        MOULD_JIG_PICKUP.forEach { id -> onFoundryLoc1(id) { pickUpPreform() } }
    }

    private fun ScriptContext.registerCrucible() {
        CRUCIBLE_FILL.forEach { id ->
            onFoundryLoc1(id) { fillCrucibleFromInventory() }
            onFoundryLoc2(id) { checkCrucible() }
            onFoundryLoc3(id) { emptyCrucible() }
            FoundryMetal.entries.forEach { metal ->
                onOpLocU(objectType(id), metal.item) {
                    arriveDelay()
                    fillCrucible(metal)
                }
            }
        }
        onFoundryLoc1(CRUCIBLE_FULL) { pourCrucible() }
    }

    private fun ScriptContext.registerTemperatureStations() {
        onFoundryLoc1At(LAVA_POOL) { loc ->
            changeHeat(FoundryRules.SLOW_HEAT_CHANGE, "The preform heats up.", ANIM_TEMPERATURE, loc, LOC_ANIM_LAVA)
        }
        onFoundryLoc2At(LAVA_POOL) { loc ->
            changeHeat(FoundryRules.FAST_HEAT_CHANGE, "You dunk the preform into the lava.", ANIM_TEMPERATURE, loc, LOC_ANIM_LAVA)
        }
        onFoundryLoc1At(WATERFALL) { loc ->
            changeHeat(-FoundryRules.SLOW_HEAT_CHANGE, "The preform cools down.", ANIM_TEMPERATURE, loc, LOC_ANIM_WATERFALL)
        }
        onFoundryLoc2At(WATERFALL) { loc ->
            changeHeat(-FoundryRules.FAST_HEAT_CHANGE, "You quench the preform.", ANIM_TEMPERATURE, loc, LOC_ANIM_WATERFALL)
        }
        onOpLocU(objectType(WATERFALL), "obj.bucket_empty") {
            arriveDelay()
            if (invReplace(inv, "obj.bucket_empty", 1, "obj.bucket_water").success) {
                anim(ANIM_TEMPERATURE)
                worldRepo.locAnim(it.loc, LOC_ANIM_WATERFALL)
                mes("You fill the bucket with water from the waterfall.")
            }
        }
    }

    private fun ScriptContext.registerWorkstations() {
        onFoundryLoc1At(TRIP_HAMMER) { loc -> useTool(FoundryTool.TRIP_HAMMER, loc) }
        onFoundryLoc1At(GRINDSTONE) { loc -> useTool(FoundryTool.GRINDSTONE, loc) }
        onFoundryLoc1At(POLISHING_WHEEL) { loc -> useTool(FoundryTool.POLISHING_WHEEL, loc) }
    }

    private fun ScriptContext.registerStorage() {
        listOf(PREFORM_STORAGE_BASE, PREFORM_STORAGE_STATIC, PREFORM_STORAGE_EMPTY, PREFORM_STORAGE_FULL).forEach { id ->
            onFoundryLoc1(id) { useStorage() }
        }
    }

    private fun ScriptContext.registerPreformEquipment() {
        onOpWorn1(PREFORM_ITEM) {
            val state = (service.state(player) as? FoundryServiceResult.Changed)?.state
            if (state?.preform != null) {
                mes("The Foundry preform must remain equipped while you work it.")
            } else {
                wornUnequip(it.slot)
            }
        }
    }

    private fun ScriptContext.objectType(id: Int) = requireNotNull(ServerCacheManager.getObject(id)) { "Missing native Foundry object $id" }

    private fun ScriptContext.onFoundryLoc1(id: Int, action: suspend ProtectedAccess.() -> Unit) {
        onOpLoc1(objectType(id)) { arriveDelay(); action() }
    }

    private fun ScriptContext.onFoundryLoc1At(id: Int, action: suspend ProtectedAccess.(BoundLocInfo) -> Unit) {
        onOpLoc1(objectType(id)) { arriveDelay(); action(it.loc) }
    }

    private fun ScriptContext.onFoundryLoc2(id: Int, action: suspend ProtectedAccess.() -> Unit) {
        onOpLoc2(objectType(id)) { arriveDelay(); action() }
    }

    private fun ScriptContext.onFoundryLoc2At(id: Int, action: suspend ProtectedAccess.(BoundLocInfo) -> Unit) {
        onOpLoc2(objectType(id)) { arriveDelay(); action(it.loc) }
    }

    private fun ScriptContext.onFoundryLoc3(id: Int, action: suspend ProtectedAccess.() -> Unit) {
        onOpLoc3(objectType(id)) { arriveDelay(); action() }
    }

    private fun ProtectedAccess.setupMoulds() {
        if (!ensureFoundryHands()) return
        when (val result = service.setupBestAvailableMoulds(player, player.smithingLvl)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_SMITHING)
                ui.openMouldJig(player, result.state)
                mes("The mould jig is set up. You can now fill the crucible with up to 28 bars.")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.checkMouldJig() {
        when (val result = service.state(player)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_SMITHING)
                ui.openMouldJig(player, result.state)
                val selected = result.state.selectedMoulds.values.mapNotNull(FoundryMoulds.byKey::get).joinToString { it.displayName }
                mes("Moulds: $selected.")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.fillCrucibleFromInventory() {
        if (!ensureFoundryHands()) return
        val result = service.state(player)
        val state = (result as? FoundryServiceResult.Changed)?.state ?: return show(result)
        var remaining = FoundryRules.CRUCIBLE_CAPACITY - state.crucible.total
        if (remaining <= 0) return mes("The crucible is full. Pour the metal into the mould jig.")
        var added = 0
        FoundryMetal.entries.forEach { metal ->
            val count = minOf(inv.count(metal.item), remaining)
            if (count > 0 && invDel(inv, metal.item, count).success) {
                val update = service.addMetal(player, metal, count)
                if (update is FoundryServiceResult.Changed) {
                    remaining -= count
                    added += count
                    sync(update.state)
                }
            }
        }
        if (added == 0) {
            mes("You need smithable bars to fill the crucible.")
        } else {
            anim(ANIM_SMITHING)
            mes("You add $added bar-equivalents to the crucible.")
        }
    }

    private fun ProtectedAccess.fillCrucible(metal: FoundryMetal) {
        if (!ensureFoundryHands()) return
        val result = service.state(player)
        val state = (result as? FoundryServiceResult.Changed)?.state ?: return show(result)
        val count = minOf(inv.count(metal.item), FoundryRules.CRUCIBLE_CAPACITY - state.crucible.total)
        if (count <= 0) return mes("There is no room for that metal in the crucible.")
        if (invDel(inv, metal.item, count).failure) return
        when (val update = service.addMetal(player, metal, count)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_SMITHING)
                sync(update.state)
            }
            else -> show(update)
        }
    }

    private fun ProtectedAccess.checkCrucible() {
        when (val result = service.state(player)) {
            is FoundryServiceResult.Changed -> {
                val contents = FoundryMetal.entries.mapNotNull { metal -> result.state.crucible.amount(metal).takeIf { it > 0 }?.let { "$it ${metal.name.lowercase()}" } }
                mes(if (contents.isEmpty()) "The crucible is empty." else "The crucible contains ${contents.joinToString()} bars (${result.state.crucible.total}/28).")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.emptyCrucible() {
        if (!ensureFoundryHands()) return
        val current = service.state(player)
        val state = (current as? FoundryServiceResult.Changed)?.state ?: return show(current)
        if (state.crucible.total == 0) return mes("The crucible is already empty.")
        when (val result = service.emptyCrucible(player)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_SMITHING)
                sync(result.state)
                mes("You empty the crucible. Its contents are destroyed.")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.pourCrucible() {
        if (!ensureFoundryHands()) return
        when (val result = service.pour(player, player.smithingLvl)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_SMITHING)
                sync(result.state)
                mes("You pour the alloy into the mould jig. Use Pick-up once it has cooled.")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.pickUpPreform() {
        if (!ensureFoundryHands()) return
        if (!hasPreformProtection()) {
            return mes("You need a bucket of water, ice gloves, or Smiths' gloves (i) to safely pick up the preform.")
        }
        when (val result = service.pickUpPreform(player)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_PICKUP)
                equipWorkingPreform()
                ui.openHud(player, result.state)
                mes("You equip the Foundry preform. Follow the heat meter and use the highlighted workstation.")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.changeHeat(
        amount: Int,
        message: String,
        animation: String,
        station: BoundLocInfo,
        stationAnimation: Int,
    ) {
        if (!ensureFoundryHands()) return
        when (val result = service.changeHeat(player, amount)) {
            is FoundryServiceResult.Changed -> {
                anim(animation)
                worldRepo.locAnim(station, stationAnimation)
                sync(result.state)
                mes(message)
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.useTool(tool: FoundryTool, station: BoundLocInfo) {
        if (!ensureFoundryHands()) return
        val before = (service.state(player) as? FoundryServiceResult.Changed)?.state?.preform
        when (val result = service.useTool(player, tool)) {
            is FoundryServiceResult.Changed -> {
                anim(tool.animation)
                worldRepo.locAnim(station, tool.stationAnimation)
                sync(result.state)
                val after = result.state.preform
                when {
                    after?.lifecycle == PreformLifecycle.Ruined -> mes("You badly damage the sword. It is now ruined; hand it in to Kovac.")
                    after?.lifecycle == PreformLifecycle.Complete -> mes("The sword is complete. Take it to Kovac for inspection.")
                    before != null && after != null && after.quality < before.quality -> mes("That was the wrong workstation or temperature. The sword loses quality.")
                    else -> mes("You work the preform with the ${tool.displayName}.")
                }
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.storePreform() {
        if (!ensureFoundryHands()) return
        when (val result = service.storePreform(player)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_PICKUP)
                clearWorkingPreform()
                ui.sync(player, result.state)
                ui.closeHud(player)
                mes("You store the preform. Its heat and progress are preserved.")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.useStorage() {
        val preform = (service.state(player) as? FoundryServiceResult.Changed)?.state?.preform
        when (preform?.lifecycle) {
            PreformLifecycle.Processing -> storePreform()
            PreformLifecycle.Stored -> takePreform()
            else -> mes("There is no active preform to store here.")
        }
    }

    private fun ProtectedAccess.takePreform() {
        if (!ensureFoundryHands()) return
        if (!hasPreformProtection()) {
            return mes("You need a bucket of water, ice gloves, or Smiths' gloves (i) to take the preform.")
        }
        when (val result = service.takePreform(player)) {
            is FoundryServiceResult.Changed -> {
                anim(ANIM_PICKUP)
                equipWorkingPreform()
                ui.openHud(player, result.state)
                mes("You equip the stored Foundry preform.")
            }
            else -> show(result)
        }
    }

    /** A water bucket is held; ice gloves and Smiths' gloves (i) must be worn. */
    private fun ProtectedAccess.hasPreformProtection(): Boolean =
        inv.contains("obj.bucket_water") || player.worn.any { it != null && it.id in PREFORM_GLOVE_IDS }

    private fun ProtectedAccess.hasEmptyFoundryHands(): Boolean {
        if (player.righthand == null && player.lefthand == null) return true
        mes("Unequip your weapon and shield before starting at Giants' Foundry.")
        return false
    }

    private fun ProtectedAccess.ensureFoundryHands(): Boolean {
        val state = (service.state(player) as? FoundryServiceResult.Changed)?.state ?: return true
        if (state.commission == null && state.preform == null) return true
        val preform = state.preform
        if (preform?.lifecycle in HELD_PREFORM_LIFECYCLES) {
            if (player.righthand?.id == PREFORM_ITEM_ID && player.lefthand == null) return true
            if (player.righthand == null && player.lefthand == null) {
                equipWorkingPreform()
                return true
            }
        } else if (player.righthand == null && player.lefthand == null) {
            return true
        }
        mes("Unequip your weapon and shield before working at Giants' Foundry.")
        return false
    }

    private fun ProtectedAccess.equipWorkingPreform() {
        player.righthand = InvObj(PREFORM_ITEM)
    }

    private fun ProtectedAccess.clearWorkingPreform() {
        if (player.righthand?.id == PREFORM_ITEM_ID) player.righthand = null
    }
    private fun ProtectedAccess.handInSword() {
        if (!ensureFoundryHands()) return
        when (val result = service.settle(player)) {
            is FoundryServiceResult.Settled -> {
                clearWorkingPreform()
                statAdvance("stat.smithing", result.reward.smithingXp.toDouble())
                val bank = player.invMap.getOrPut("inv.bank")
                invAdd(bank, "obj.coins", result.reward.coins, strict = false)
                ui.sync(player, result.state)
                ui.closeHud(player)
                mes("Kovac accepts the sword: ${result.reward.quality} quality, ${result.reward.smithingXp} Smithing XP, ${result.reward.coins} coins sent to your bank, and ${result.reward.reputation} Foundry Reputation.")
            }
            else -> show(result)
        }
    }

    private fun ProtectedAccess.show(result: FoundryServiceResult) {
        when (result) {
            is FoundryServiceResult.Changed -> sync(result.state)
            is FoundryServiceResult.Assigned -> sync(result.state)
            is FoundryServiceResult.Settled -> sync(result.state)
            is FoundryServiceResult.Rejected -> mes(result.reason)
            FoundryServiceResult.CorruptState -> mes("Your Giants' Foundry state needs staff recovery before it can be changed.")
        }
    }

    private fun ProtectedAccess.sync(state: FoundryState) = ui.sync(player, state)

    private val FoundryMetal.item: String
        get() = when (this) {
            FoundryMetal.BRONZE -> "obj.bronze_bar"
            FoundryMetal.IRON -> "obj.iron_bar"
            FoundryMetal.STEEL -> "obj.steel_bar"
            FoundryMetal.MITHRIL -> "obj.mithril_bar"
            FoundryMetal.ADAMANT -> "obj.adamantite_bar"
            FoundryMetal.RUNE -> "obj.runite_bar"
        }

    private val FoundryTool.animation: String
        get() = when (this) {
            FoundryTool.TRIP_HAMMER -> ANIM_SMITHING
            FoundryTool.GRINDSTONE -> ANIM_GRINDING
            FoundryTool.POLISHING_WHEEL -> ANIM_POLISHING
        }

    private val FoundryTool.stationAnimation: Int
        get() = when (this) {
            FoundryTool.TRIP_HAMMER -> LOC_ANIM_TRIP_HAMMER
            FoundryTool.GRINDSTONE,
            FoundryTool.POLISHING_WHEEL -> LOC_ANIM_WHEEL
        }

    private companion object {
        const val KOVAC_ACTIVITY_NPC = 11_472
        val FOUNDRY_WORKSHOP = CoordGrid(3369, 11486, 0)

        const val TRIP_HAMMER = 44_619
        const val GRINDSTONE = 44_620
        const val POLISHING_WHEEL = 44_621
        const val CRUCIBLE_EMPTY = 44_622
        const val CRUCIBLE_PARTIAL = 44_623
        const val CRUCIBLE_FULL = 44_624
        val CRUCIBLE_FILL = listOf(CRUCIBLE_EMPTY, CRUCIBLE_PARTIAL)
        const val MOULD_JIG_EMPTY = 44_625
        const val MOULD_JIG_SETUP = 44_626
        val MOULD_JIG_PICKUP = listOf(44_627, 44_628, 44_629)
        const val LAVA_POOL = 44_631
        const val WATERFALL = 44_632
        const val PREFORM_STORAGE_BASE = 44_604
        const val PREFORM_STORAGE_STATIC = 44_616
        const val PREFORM_STORAGE_EMPTY = 44_633
        const val PREFORM_STORAGE_FULL = 44_634
        val PREFORM_GLOVE_IDS = setOf(1_580, 27_031)
        const val PREFORM_ITEM = "obj.giants_foundry_preform"
        const val PREFORM_ITEM_ID = 27_010
        val HELD_PREFORM_LIFECYCLES = setOf(PreformLifecycle.Processing, PreformLifecycle.Complete, PreformLifecycle.Ruined)

        const val ANIM_SMITHING = "seq.human_smithing"
        const val ANIM_GRINDING = "seq.human_herbing_grind"
        const val ANIM_POLISHING = "seq.human_spinningwheel_90"
        const val ANIM_TEMPERATURE = "seq.human_fillbucket_sandpit"
        const val ANIM_PICKUP = "seq.human_pickupfloor"
        const val LOC_ANIM_TRIP_HAMMER = 9_449
        const val LOC_ANIM_WHEEL = 9_445
        const val LOC_ANIM_LAVA = 3_743
        const val LOC_ANIM_WATERFALL = 7_024
    }
}