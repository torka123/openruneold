package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfModalPauseButton
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val REWARD_DISPLAY_INVENTORY = "inv.deathkeep_items"
private const val SCABARAS_PASSAGE_ANIMATION = 749
private const val SCABARAS_PASSAGE_DURATION_TICKS = 3

internal class ToaWorld
@Inject
constructor(
    private val raids: ToaRaidManager,
    private val supplies: ToaSupplies,
    private val heldInteractions: HeldInteractions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        raids.validateAssets()
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(ToaLocs.GROUPING_OBELISK))) {
            arriveDelay()
            raids.openLobby(player)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(ToaLocs.INVOCATION_BOARD))) {
            arriveDelay()
            raids.openInvocationBoard(player)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(ToaLocs.START))) {
            arriveDelay()
            val choice =
                menu("Are you sure you want to start the raid?", "Yes, I'm ready.", "Nevermind.")
            if (choice == 0) raids.start(player)
        }
        ToaLocs.pathEntrances.forEach { (id, path) ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.enterPath(player, path)
            }
            onOpLoc2(type) {
                arriveDelay()
                raids.enterPath(player, path)
            }
        }
        val challengeBarrier =
            requireNotNull(ServerCacheManager.getObject(ToaLocs.CHALLENGE_BARRIER))
        onOpLoc1(challengeBarrier) {
            arriveDelay()
            handleChallengeBarrier(it.loc, quickPass = false)
        }
        onOpLoc2(challengeBarrier) {
            arriveDelay()
            handleChallengeBarrier(it.loc, quickPass = true)
        }
        listOf(CrondisAssets.WATERFALL_WET, CrondisAssets.WATERFALL_DRY).forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.fillCrondisContainer(player, it.loc)
            }
            onOpLoc2(type) {
                arriveDelay()
                raids.fillCrondisContainer(player, it.loc)
            }
        }
        val crondisContinue =
            requireNotNull(ServerCacheManager.getObject(CrondisAssets.CONTINUE_LOC))
        onOpLoc1(crondisContinue) {
            arriveDelay()
            raids.interactChallenge(player, CrondisAssets.CONTINUE_LOC)
        }
        onOpLoc2(crondisContinue) {
            arriveDelay()
            raids.interactChallenge(player, CrondisAssets.CONTINUE_LOC)
        }
        CrondisAssets.PALM.rangeTo(CrondisAssets.PALM_COMPLETE).forEach { id ->
            val type = requireNotNull(ServerCacheManager.getNpc(id))
            onOpNpc1(type) {
                arriveDelay()
                raids.waterCrondisPalm(player, it.npc)
            }
        }
        val waterContainerType =
            requireNotNull(ServerCacheManager.getItem(CrondisAssets.WATER_CONTAINER))
        onOpObj3(waterContainerType) {
            val raid = raids.raidFor(player) ?: return@onOpObj3
            if (
                raid.currentRoom != ToaRoom.CRONDIS ||
                    raid.challenge?.stage != ToaChallengeStage.ACTIVE
            ) {
                return@onOpObj3
            }
            player.resetAnim()
            if (player.coords != it.obj.coords) {
                delay(1)
                anim("seq.human_pickuptable")
            }
            soundSynth("synth.pick2")
            raids.takeCrondisContainer(player, it.obj)
        }
        val waterContainer = RSCM.getReverseMapping(RSCMType.OBJ, CrondisAssets.WATER_CONTAINER)
        onOpHeld1(waterContainer) { raids.checkCrondisContainer(player) }
        onOpHeld2(waterContainer) { raids.emptyCrondisContainer(player) }
        onOpHeld5(waterContainer) {
            val raid = raids.raidFor(player)
            if (raid != null && raids.destroyCrondisContainer(player)) return@onOpHeld5
            if (raid != null && supplies.discardTemporarySlot(this, raid, it.slot)) return@onOpHeld5
            player.mes("That temporary raid item cannot be dropped.")
        }

        val suppliesContainer =
            requireNotNull(ServerCacheManager.getItem(ToaSupplies.SUPPLIES_CONTAINER))
        onOpHeld1(suppliesContainer) { openSuppliesContainer() }
        onOpHeld2(suppliesContainer) {
            val raid = raids.raidFor(player) ?: return@onOpHeld2
            supplies.withdrawFromContainer(this, raid, amount = 1)
            refreshSuppliesContainer()
        }
        onOpHeld3(suppliesContainer) {
            val raid = raids.raidFor(player) ?: return@onOpHeld3
            supplies.withdrawFromContainer(this, raid, amount = Int.MAX_VALUE)
            refreshSuppliesContainer()
        }
        onOpHeld4(suppliesContainer) {
            val raid = raids.raidFor(player) ?: return@onOpHeld4
            supplies.resupply(this, raid)
        }
        ToaSupplies.TOA_CONSUMABLE_IDS.forEach { id ->
            val supply = requireNotNull(ServerCacheManager.getItem(id))
            onOpHeldU(supply, suppliesContainer) {
                val raid = raids.raidFor(player) ?: return@onOpHeldU
                if (supplies.storeSupplySlot(this, raid, it.firstSlot)) {
                    player.mes("You store the raid supply in the Supplies container.")
                }
            }
        }
        ToaSupplies.LOCAL_DISCARD_GUARD_IDS.filterNot { it == CrondisAssets.WATER_CONTAINER }
            .forEach { id ->
                val temporary = requireNotNull(ServerCacheManager.getItem(id))
                onOpHeld5(temporary) {
                    val raid = raids.raidFor(player)
                    if (raid != null && supplies.discardTemporarySlot(this, raid, it.slot)) {
                        return@onOpHeld5
                    }
                    val obj = player.inv[it.slot] ?: return@onOpHeld5
                    if (
                        id == ApmekenLiveTuning.HAMMER_ITEM &&
                            !supplies.isTemporaryOwnershipVars(obj.vars)
                    ) {
                        heldInteractions.drop(this, player.inv, it.slot)
                    } else {
                        player.mes("That temporary raid item cannot be dropped.")
                    }
                }
            }
        setOf(
                ApmekenRevision240RuntimeLayout.hammerSupplyLoc.locId,
                ApmekenRevision240RuntimeLayout.potionSupplyLoc.locId,
                ApmekenRevision240RuntimeLayout.roofSupportLoc.locId,
                ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId,
            )
            .forEach { id ->
                val type = requireNotNull(ServerCacheManager.getObject(id))
                onOpLoc1(type) {
                    arriveDelay()
                    raids.interactApmekenObject(player, it.loc)
                }
                onOpLoc2(type) {
                    arriveDelay()
                    raids.interactApmekenObject(player, it.loc)
                }
            }
        val apmekenVent =
            requireNotNull(
                ServerCacheManager.getObject(ApmekenRevision240RuntimeLayout.ventLoc.locId)
            )
        val neutralisingPotion =
            requireNotNull(ServerCacheManager.getItem(ApmekenLiveTuning.NEUTRALISING_POTION_ITEM))
        onOpHeld1(neutralisingPotion) { raids.pourApmekenPotion(player) }
        onOpLocU(apmekenVent, neutralisingPotion) {
            arriveDelay()
            raids.interactApmekenObject(player, it.loc)
        }
        onOpPlayerU(neutralisingPotion) { raids.useApmekenPotion(player, it.target) }
        ScabarasWorldBindings.primaryActions.forEach { (id, action) ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.interactScabarasObject(player, action, it.loc)
            }
        }
        ScabarasWorldBindings.fifthActions.forEach { (id, action) ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc5(type) {
                arriveDelay()
                raids.interactScabarasObject(player, action, it.loc)
            }
        }
        val scabarasPassage =
            requireNotNull(
                ServerCacheManager.getObject(
                    ScabarasRevision240RuntimeLayout.branchPassages.values.first().locId
                )
            )
        onOpLoc1(scabarasPassage) {
            arriveDelay()
            val destination = raids.scabarasPassageDestination(player, it.loc)
            if (destination == null) return@onOpLoc1
            anim(SCABARAS_PASSAGE_ANIMATION)
            delay(SCABARAS_PASSAGE_DURATION_TICKS)
            telejump(destination, TeleportType.Exempt)
        }
        // Revision-240 cache slots: Pick-up, clockwise, anticlockwise, then Push.
        ToaLocs.hetMovableMirrors.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.interactHetObject(player, HetLiveObjectAction.PICK_UP, it.loc)
            }
            onOpLoc2(type) {
                arriveDelay()
                raids.interactHetObject(player, HetLiveObjectAction.ROTATE_CLOCKWISE, it.loc)
            }
            onOpLoc3(type) {
                arriveDelay()
                raids.interactHetObject(player, HetLiveObjectAction.ROTATE_ANTICLOCKWISE, it.loc)
            }
            onOpLoc4(type) {
                arriveDelay()
                raids.interactHetObject(player, HetLiveObjectAction.PUSH, it.loc)
            }
        }
        ToaLocs.hetDirtyMirrors.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.interactHetObject(player, HetLiveObjectAction.CLEAN, it.loc)
            }
        }
        ToaLocs.hetBreakableBarriers.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.interactHetObject(player, HetLiveObjectAction.BREAK, it.loc)
            }
        }
        ToaLocs.hetSealNpcs.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getNpc(id))
            onOpNpc1(type) {
                arriveDelay()
                raids.mineHetSeal(player, it.npc)
            }
        }
        val hetMirror = RSCM.getReverseMapping(RSCMType.OBJ, HetLiveAssets.MIRROR_ITEM)
        onOpHeld1(hetMirror) { raids.placeHetMirror(player) }
        ToaLocs.hetPickaxeStatues.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.takeHetPickaxe(player)
            }
            onOpLoc2(type) {
                arriveDelay()
                raids.depositHetPickaxe(player)
            }
        }
        listOf(ZebakAssets.JUG, ZebakAssets.ROLLING_JUG).forEach { id ->
            val jug = requireNotNull(ServerCacheManager.getNpc(id))
            onOpNpc1(jug) {
                arriveDelay()
                raids.pushZebakJug(player, it.npc)
            }
            onOpNpc3(RSCM.getReverseMapping(RSCMType.NPC, id)) {
                arriveDelay()
                raids.pullZebakJug(player, it.npc)
            }
            onOpNpc4(RSCM.getReverseMapping(RSCMType.NPC, id)) {
                arriveDelay()
                raids.hitZebakJug(player, it.npc)
            }
        }
        val zebakRockSteps = requireNotNull(ServerCacheManager.getObject(ZebakAssets.ROCK_STEPS))
        onOpLoc1(zebakRockSteps) {
            arriveDelay()
            raids.climbZebakSteps(player, it.loc)
        }

        ToaLocs.disabledPathEntrances.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.disabledKephriRoute(player)
            }
            onOpLoc2(type) {
                arriveDelay()
                raids.disabledKephriRoute(player)
            }
        }
        ToaLocs.bossCrystals.forEach { (id, path) ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                val choice =
                    menu(
                        "Are you sure you want to teleport into the boss arena?",
                        "Yes, I'm ready.",
                        "Nevermind.",
                    )
                if (choice == 0) enterBossWithCutscene(path)
            }
            onOpLoc2(type) {
                arriveDelay()
                enterBossWithCutscene(path)
            }
        }
        ToaLocs.disabledBossCrystals.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.disabledKephriRoute(player)
            }
            onOpLoc2(type) {
                arriveDelay()
                raids.disabledKephriRoute(player)
            }
        }
        val wardensPath = requireNotNull(ServerCacheManager.getObject(ToaLocs.WARDENS_PATH))
        onOpLoc1(wardensPath) {
            arriveDelay()
            raids.enterWardensRoom(player)
        }
        onOpLoc2(wardensPath) {
            arriveDelay()
            raids.enterWardensRoom(player)
        }
        val wardensCrystal = requireNotNull(ServerCacheManager.getObject(ToaLocs.WARDENS_CRYSTAL))
        onOpLoc1(wardensCrystal) {
            arriveDelay()
            enterWardensFight()
        }
        onOpLoc2(wardensCrystal) {
            arriveDelay()
            enterWardensFight()
        }
        ToaLocs.returnToLobby.forEach { id ->
            ServerCacheManager.getObject(id)?.let { type ->
                onOpLoc1(type) {
                    arriveDelay()
                    raids.useExit(player)
                }
            }
        }
        ToaLocs.leaveRaid.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                raids.leave(player)
            }
        }
        ServerCacheManager.getObject(ToaLocs.REWARD_ENTRY)?.let { type ->
            onOpLoc1(type) {
                arriveDelay()
                enterRewardsWithTransition()
            }
        }
        ToaLocs.rewardNiches.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            onOpLoc1(type) {
                arriveDelay()
                showRewardInterface()
            }
        }
        ToaLocs.rewardSarcophagi.forEach { id ->
            ServerCacheManager.getObject(id)?.let { type ->
                onOpLoc1(type) {
                    arriveDelay()
                    showRewardInterface()
                }
            }
        }
        onProtectedEvent<IfModalButton>(ToaRewardService.REWARD_INVENTORY_COMPONENT) {
            claimRewardToInventory()
        }
        onProtectedEvent<IfModalPauseButton>(ToaRewardService.REWARD_INVENTORY_COMPONENT) {
            claimRewardToInventory()
        }
        onProtectedEvent<IfModalButton>(ToaRewardService.REWARD_BANK_COMPONENT) {
            claimRewardToBank()
        }
        onProtectedEvent<IfModalPauseButton>(ToaRewardService.REWARD_BANK_COMPONENT) {
            claimRewardToBank()
        }
        onProtectedEvent<IfModalButton>(ToaRewardService.REWARD_DISCARD_COMPONENT) {
            discardReward()
        }
        onProtectedEvent<IfModalPauseButton>(ToaRewardService.REWARD_DISCARD_COMPONENT) {
            discardReward()
        }
        val bankCamel = requireNotNull(ServerCacheManager.getObject(ToaLocs.BANK_CAMEL))
        onOpLoc1(bankCamel) {
            arriveDelay()
            ifOpenMainSidePair(main = "interface.bankmain", side = "interface.bankside")
        }
        onOpLoc3(bankCamel) {
            arriveDelay()
            ifOpenMainModal("interface.ge_collect")
        }
        val completionSpirit = requireNotNull(ServerCacheManager.getNpc(ToaNpcs.COMPLETION_SPIRIT))
        onOpNpc1(completionSpirit) {
            arriveDelay()
            val choice =
                menu("Would you like to return to the lobby?", "Yes, I'm ready.", "Nevermind.")
            if (choice == 0) raids.returnFromCompletionSpirit(player, it.npc)
        }
        onOpNpc3(RSCM.getReverseMapping(RSCMType.NPC, ToaNpcs.COMPLETION_SPIRIT)) {
            arriveDelay()
            raids.returnFromCompletionSpirit(player, it.npc)
        }
        val helpfulSpirit = requireNotNull(ServerCacheManager.getNpc(ToaNpcs.HELPFUL_SPIRIT))
        onOpNpc1(helpfulSpirit) {
            arriveDelay()
            openHelpfulSpiritSupplies()
        }
        ToaSupplies.DISPLAY_COMPONENT_BY_BUNDLE.forEach { (bundle, component) ->
            onProtectedEvent<IfModalButton>(component) { claimHelpfulSpiritBundle(bundle) }
        }
        ToaSupplies.SELECT_COMPONENT_BY_BUNDLE.forEach { (bundle, component) ->
            onProtectedEvent<IfModalButton>(component) { claimHelpfulSpiritBundle(bundle) }
        }
        onProtectedEvent<IfModalButton>(ToaSupplies.CONTAINER_GRID_COMPONENT) {
            val raid = raids.raidFor(player) ?: return@onProtectedEvent
            if (supplies.withdrawSlot(this, raid, it.comsub, it.op)) refreshSuppliesContainer()
        }
        onProtectedEvent<IfModalButton>(ToaSupplies.CONTAINER_CLOSE_COMPONENT) { ifClose() }

        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_LIST_REFRESH_BUTTON) {
            raids.openPartyList(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_LIST_PARTY_BUTTON) {
            raids.openOwnMembers(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_LIST_FILTER_BUTTON) {
            raids.refreshPartyListFilter(player)
        }
        ToaPartyBoard.PARTY_LIST_ROW_COMPONENTS.forEach { component ->
            onProtectedEvent<IfModalButton>(component) {
                val slot = requireNotNull(ToaPartyBoard.partyListSlotFromComponent(component))
                raids.openPartyView(player, slot)
            }
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_BACK) {
            raids.openPartyList(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_MEMBERS_TAB) {
            raids.openViewedMembers(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_APPLICANTS_TAB) {
            raids.openViewedApplicants(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_INVOCATIONS_TAB) {
            raids.openViewedInvocations(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_SUMMARY_TAB) {
            raids.openPartySummary(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_REFRESH_BUTTON) {
            raids.openViewedMembers(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_ACTION_BUTTON) {
            raids.partyMainAction(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_UNBLOCK_BUTTON) {
            raids.clearBlockedApplicants(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_MEMBER_LIST) {
            raids.memberRowAction(player, it.comsub)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_APPLICANT_LIST) {
            if (it.op == IfButtonOp.Op2) raids.rejectApplicant(player, it.comsub)
            else raids.acceptApplicant(player, it.comsub)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_CLEAR_INVOCATIONS) {
            if (raids.clearInvocations(player)) raids.openViewedInvocations(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_REWARDS) {
            raids.openPartyRewards(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_PRESETS) {
            raids.openInvocationPresets(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_COMPLETIONS) {
            raids.openPartyCompletions(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_LOAD_PRESET) {
            raids.loadInvocationPreset(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_SAVE_PRESET) {
            raids.saveInvocationPreset(player)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_PRESET_SLOTS) {
            raids.selectInvocationPreset(player, it.comsub, clear = it.op == IfButtonOp.Op2)
        }
        onProtectedEvent<IfModalButton>(ToaPartyBoard.PARTY_DETAILS_INVOCATION_CONTENT) {
            raids.toggleInvocationFromInterface(player, it.comsub)
        }
        onCommand("toa") {
            desc = "Teleport to the Tombs of Amascut waiting room"
            invalidArgs = "Use ::toa"
            cheat { if (args.isEmpty()) raids.teleportLobby(player) }
        }
        onCommand("toawarden") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Start a solo Tombs raid directly at the Wardens encounter"
            invalidArgs = "Use ::toawarden"
            cheat { if (args.isEmpty()) raids.devStartWardens(player) }
        }
        onCommand("crondistrace") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enable, disable, or inspect the current raid's Path of Crondis trace"
            invalidArgs = "Use ::crondistrace on|off|status"
            cheat { raids.setCrondisTrace(player, args.firstOrNull() ?: "status") }
        }
        onCommand("zebaktrace") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enable, disable, or inspect the current raid's Zebak trace"
            invalidArgs = "Use ::zebaktrace on|off|status"
            cheat { raids.setZebakTrace(player, args.firstOrNull() ?: "status") }
        }
        onCommand("babatrace") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enable, disable, or inspect the current raid's Ba-Ba tick trace"
            invalidArgs = "Use ::babatrace on|off|status|dump"
            cheat { raids.setBabaTrace(player, args.firstOrNull() ?: "status") }
        }
        onCommand("akkhatrace") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enable, disable, or inspect the current raid's Akkha tick trace"
            invalidArgs = "Use ::akkhatrace on|off|status"
            cheat { raids.setAkkhaTrace(player, args.firstOrNull() ?: "status") }
        }
        onCommand("toadev") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Start, inspect, route, or reset a Tombs of Amascut test raid"
            invalidArgs =
                "Use ::toadev start <party-size> <seed>|solo|status|reset|paths|complete <path>|room <name>|wardens|reward"
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "start" -> {
                        val expectedPartySize = args.getOrNull(1)?.toIntOrNull()
                        val seed = args.getOrNull(2)?.toLongOrNull()
                        if (expectedPartySize == null || seed == null) {
                            player.mes("Use ::toadev start <party-size 1-8> <seed>.")
                        } else {
                            raids.devStart(player, expectedPartySize, seed)
                        }
                    }
                    "solo" -> raids.devStartSolo(player)
                    "reset" -> raids.reset(player)
                    "status" -> player.mes(raids.status(player))
                    "paths" -> raids.devCompleteAllPaths(player)
                    "complete" -> {
                        val path =
                            when (args.getOrNull(1)?.lowercase()) {
                                "kephri",
                                "scabaras" -> ToaPath.SCABARAS
                                "baba",
                                "ba-ba",
                                "apmeken" -> ToaPath.APMEKEN
                                "zebak",
                                "crondis" -> ToaPath.CRONDIS
                                "akkha",
                                "het" -> ToaPath.HET
                                else -> null
                            }
                        if (path == null)
                            player.mes("Use ::toadev complete kephri|baba|zebak|akkha")
                        else raids.devCompletePath(player, path)
                    }
                    "room" -> args.getOrNull(1)?.let { raids.devEnterRoom(player, it) }
                    "wardens" -> raids.devEnterRoom(player, "wardens")
                    "reward" -> raids.devEnterRoom(player, "reward")
                    else ->
                        player.mes(
                            "Use ::toadev start <party-size> <seed>|solo|status|reset|paths|complete <path>|room <name>|wardens|reward"
                        )
                }
            }
        }
    }

    private suspend fun ProtectedAccess.handleChallengeBarrier(
        barrier: BoundLocInfo,
        quickPass: Boolean,
    ) {
        var decision = raids.useChallengeBarrier(player, barrier, confirmed = quickPass)
        if (decision == ToaBarrierDecision.CONFIRM_START) {
            val choice =
                menu(
                    "Begin ${raids.raidFor(player)?.currentRoom?.displayName ?: "the challenge"}?",
                    "Yes, pass the barrier.",
                    "Not yet.",
                )
            if (choice != 0) return
            decision = raids.useChallengeBarrier(player, barrier, confirmed = true)
        }
        if (
            decision != ToaBarrierDecision.START_CUTSCENE &&
                decision != ToaBarrierDecision.JOIN_CUTSCENE
        )
            return

        val cutscene = raids.challengeEntryCutscene(player)
        if (cutscene == null) {
            raids.cancelChallengeBarrier(player)
            return
        }
        var completed = false
        try {
            playEntryCutscene(cutscene)
            completed = raids.finishChallengeBarrier(player, barrier)
        } finally {
            if (!completed) raids.cancelChallengeBarrier(player)
        }
    }

    private suspend fun ProtectedAccess.enterBossWithCutscene(path: ToaPath) {
        if (path == ToaPath.HET) {
            raids.enterBoss(player, path)
            return
        }
        val cutscene = raids.bossEntryCutscene(player, path) ?: return
        playEntryCutscene(cutscene)
        raids.enterBoss(player, path)
    }

    private suspend fun ProtectedAccess.enterRewardsWithTransition() {
        if (!raids.canEnterRewards(player)) return
        fadeOverlay(
            WardenRewardTransition.FADE_START_COLOUR,
            WardenRewardTransition.FADE_START_TRANSPARENCY,
            WardenRewardTransition.FADE_END_COLOUR,
            WardenRewardTransition.FADE_END_TRANSPARENCY,
            WardenRewardTransition.FADE_CLIENT_DURATION,
        )
        try {
            delay(WardenRewardTransition.FADE_DELAY_TICKS)
            // The phase-two cutscene used a fixed camera and hidden minimap. Reset both sides of
            // the instance jump so a canceled cutscene can never leave the reward room looking at
            // unloaded map space.
            camReset()
            camModeReset()
            minimapReset()
            raids.enterRewards(player, preserveActiveCoroutine = true)
        } finally {
            camReset()
            camModeReset()
            minimapReset()
            closeFadeOverlay(cycles = 1)
        }
    }

    private fun ProtectedAccess.enterWardensFight() {
        if (raids.startWardensFight(player)) {
            player.spotanim(WardensEntryLifecycle.PLAYER_SPOTANIM)
            player.soundSynth(WardensEntryLifecycle.TELEPORT_SOUND)
        } else {
            player.mes("The teleport crystal does not respond.")
        }
    }

    private suspend fun ProtectedAccess.playEntryCutscene(scene: ToaResolvedEntryCutscene) {
        camModeFixed()
        minimapHideMap()
        try {
            camMoveTo(scene.camera, height = scene.cameraHeight, rate = 100, rate2 = 100)
            camLookAt(scene.focus, height = scene.focusHeight, rate = 100, rate2 = 100)
            delay(scene.durationTicks)
        } finally {
            camReset()
            camModeReset()
            minimapReset()
        }
    }

    private fun ProtectedAccess.openHelpfulSpiritSupplies() {
        val raid = raids.raidFor(player) ?: return
        if (!supplies.open(player, raid)) return
        val selection = supplies.selection(player, raid) ?: return
        selection.displays.forEach { (bundle, inventory) ->
            transmitFresh(inventory)
            val display = ToaSupplies.DISPLAY_COMPONENT_BY_BUNDLE.getValue(bundle)
            ifSetEvents(RSCM.getReverseMapping(RSCMType.COMPONENT, display), 2..8, IfEvent.Op1)
            val select = ToaSupplies.SELECT_COMPONENT_BY_BUNDLE.getValue(bundle)
            ifSetEvents(RSCM.getReverseMapping(RSCMType.COMPONENT, select), 0..9, IfEvent.Op1)
        }
        ifOpenMainModal(RSCM.getReverseMapping(RSCMType.INTERFACE, ToaSupplies.SELECTION_INTERFACE))
    }

    private fun ProtectedAccess.claimHelpfulSpiritBundle(bundle: ToaSupplyBundle) {
        val raid = raids.raidFor(player) ?: return
        if (supplies.claimBundle(this, raid, bundle)) openSuppliesContainer()
    }

    private fun ProtectedAccess.openSuppliesContainer() {
        val raid = raids.raidFor(player)
        if (raid == null) {
            player.mes("These supplies no longer belong to an active Tombs raid.")
            return
        }
        val inventory = supplies.containerInventory(player, raid)
        if (inventory == null) {
            player.mes("Your Supplies container is empty.")
            return
        }
        transmitFresh(inventory)
        ifOpenMainModal(RSCM.getReverseMapping(RSCMType.INTERFACE, ToaSupplies.CONTAINER_INTERFACE))
        ifSetEvents(
            RSCM.getReverseMapping(RSCMType.COMPONENT, ToaSupplies.CONTAINER_GRID_COMPONENT),
            0..27,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op9,
            IfEvent.Depth1,
            IfEvent.DragTarget,
        )
        ifSetEvents(
            RSCM.getReverseMapping(RSCMType.COMPONENT, ToaSupplies.CONTAINER_CLOSE_COMPONENT),
            -1..-1,
            IfEvent.Op1,
        )
    }

    private fun ProtectedAccess.refreshSuppliesContainer() {
        val raid = raids.raidFor(player) ?: return
        if (supplies.containerInventory(player, raid) == null) ifClose()
        else openSuppliesContainer()
    }

    private fun ProtectedAccess.transmitFresh(inventory: Inventory) {
        player.invMap.values.firstOrNull { it.type.id == inventory.type.id }?.let(::invStopTransmit)
        invTransmit(inventory)
    }

    private fun ProtectedAccess.showRewardInterface() {
        val roll = raids.rewardRoll(player)
        if (roll == null) {
            player.mes("You have no Tombs of Amascut reward waiting.")
            return
        }
        val display = Inventory.create(REWARD_DISPLAY_INVENTORY)
        check(display.size >= roll.rewards.size) {
            "ToA reward display inventory is too small (size=${display.size}, rewards=${roll.rewards.size})."
        }
        roll.rewards.forEachIndexed { slot, reward ->
            display[slot] = InvObj(RSCM.getReverseMapping(RSCMType.OBJ, reward.objId), reward.count)
        }
        invTransmit(display)
        ifOpenMainModal(
            RSCM.getReverseMapping(RSCMType.INTERFACE, ToaRewardService.REWARD_INTERFACE)
        )
        interfaceInvInit(
            inv = display,
            target =
                RSCM.getReverseMapping(RSCMType.COMPONENT, ToaRewardService.REWARD_ITEMS_COMPONENT),
            objRowCount = 2,
            objColCount = 4,
        )
        listOf(
                ToaRewardService.REWARD_BANK_COMPONENT,
                ToaRewardService.REWARD_INVENTORY_COMPONENT,
                ToaRewardService.REWARD_DISCARD_COMPONENT,
            )
            .forEach { packed ->
                ifSetEvents(
                    RSCM.getReverseMapping(RSCMType.COMPONENT, packed),
                    -1..-1,
                    IfEvent.PauseButton,
                    IfEvent.Op1,
                )
            }
    }

    private fun ProtectedAccess.claimRewardToInventory() {
        if (raids.claimRewardToInventory(player, player.coords)) ifClose()
    }

    private fun ProtectedAccess.claimRewardToBank() {
        if (raids.claimRewardToBank(player, bank)) ifClose()
    }

    private fun ProtectedAccess.discardReward() {
        if (raids.discardReward(player)) ifClose()
    }
}
