package org.rsmod.content.minigames.tob

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.script.advanced.onOpPlayer5
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal fun tobPassageRequiresArrival(locId: Int): Boolean = locId == TobLocs.XARPUS_DOOR

internal class TobWorld
@Inject
constructor(
    private val raids: TobRaidManager,
    private val supplies: TobSupplies,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        raids.validateAssets()
        onOpPlayer5 { inviteLobbyPlayer(it.target) }
        val entrance = requireNotNull(ServerCacheManager.getObject(TobLocs.ENTRANCE))
        onOpLoc1(entrance) {
            arriveDelay()
            raids.enterTheatre(player)
        }
        onOpLoc2(entrance) {
            arriveDelay()
            raids.openLobby(player)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(TobLocs.NOTICE_BOARD))) {
            arriveDelay()
            raids.openLobby(player)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(TobLocs.MAIDEN_BARRIER))) {
            arriveDelay()
            raids.passChallengeBarrier(player, it.loc.coords)
        }
        TobLocs.passages.forEach { id ->
            onOpLoc1(requireNotNull(ServerCacheManager.getObject(id))) {
                // The formidable passage and Verzik reward doorway have collision-blocked
                // footprints. Waiting for an arrival event prevents their validated transition
                // callbacks from ever running. The Xarpus door remains normally reachable and
                // therefore keeps standard walking behaviour.
                if (tobPassageRequiresArrival(id)) arriveDelay()
                raids.enterPassage(player, id)
            }
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(TobLocs.DAWNBRINGER_CHEST))) {
            arriveDelay()
            raids.claimDawnbringer(player)
        }
        TobMode.entries.map(TobNpcs::verzikInitial).distinct().forEach { id ->
            onOpNpc1(requireNotNull(ServerCacheManager.getNpc(id))) { talkToVerzik(it.npc) }
        }
        val resign = requireNotNull(ServerCacheManager.getObject(TobLocs.RESIGN))
        onOpLoc1(resign) {
            arriveDelay()
            raids.exit(player)
        }
        onOpLoc2(resign) {
            player.mes(
                "Pass the barriers to face each challenge, or use Resign to leave the Theatre."
            )
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(TobLocs.SUPPLY_CHEST))) {
            arriveDelay()
            supplies.open(player, raids.raidFor(player) ?: return@onOpLoc1)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(TobLocs.TELEPORT_CRYSTAL))) {
            arriveDelay()
            raids.exit(player)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(TobLocs.STRATEGY_TABLE))) {
            arriveDelay()
            player.mes(raids.status(player))
        }
        TobLocs.rewardChests.forEach { id ->
            ServerCacheManager.getObject(id)?.let { type ->
                onOpLoc1(type) {
                    // These centrepiece locs have blocked footprints, so search the clicked tile.
                    // Routing to their centre can stall or disconnect the client.
                    val reward = raids.claim(player, it.loc.coords) ?: return@onOpLoc1
                    anim("seq.human_openchest")
                    showRewardInterface(reward)
                }
            }
        }
        onProtectedEvent<IfModalButton>(TobSupplies.SUPPLY_ITEMS_COMPONENT) {
            supplies.buySlot(player, raids.raidFor(player) ?: return@onProtectedEvent, it.comsub)
        }

        onProtectedEvent<IfModalButton>(TobPartyBoard.PARTY_LIST_CONTROLS) {
            when (it.comsub) {
                0 -> raids.openPartyList(player)
                1 -> raids.openOwnPartyDetails(player)
            }
        }
        onProtectedEvent<IfModalButton>(TobPartyBoard.PARTY_LIST_PARTIES) {
            raids.openPartyDetails(player, it.comsub)
        }
        onProtectedEvent<IfModalButton>(TobPartyBoard.PARTY_DETAILS_CONTROLS) {
            when (it.comsub) {
                3 -> {
                    val size = countDialog("Enter your preferred Theatre party size (1-5):")
                    raids.setViewedPartySize(player, size)
                }
                4 -> {
                    val level = countDialog("Enter your preferred combat level (3-126):")
                    raids.setViewedPartyCombat(player, level)
                }
                else -> raids.partyDetailsAction(player, it.comsub)
            }
        }

        onCommand("tob") {
            desc = "Teleport to the Theatre of Blood"
            invalidArgs = "Use ::tob"
            cheat { if (args.isEmpty()) raids.teleportLobby(player) }
        }
        onCommand("tobc") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter a fresh Theatre treasure-room chest test"
            invalidArgs = "Use ::tobc"
            cheat { if (args.isEmpty()) raids.forceRewards(player) }
        }
        onCommand("tobpc") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter a Theatre treasure-room forced-purple chest test"
            invalidArgs = "Use ::tobpc"
            cheat { if (args.isEmpty()) raids.forceRewards(player, forceUnique = true) }
        }
        onCommand("tobsupply") {
            desc = "Buy from a Theatre supply chest"
            invalidArgs = "Use ::tobsupply stamina|prayer|brew|restore|potato|shark|turtle|manta"
            cheat {
                supplies.buy(
                    player,
                    raids.raidFor(player) ?: return@cheat,
                    args.firstOrNull().orEmpty(),
                )
            }
        }
        onCommand("tobparty") {
            desc = "Manage a Theatre party"
            invalidArgs =
                "Use ::tobparty create|invite <name>|join|leave|kick <name>|start entry|normal|hard|rejoin|status"
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "create" -> raids.createParty(player)
                    "invite" -> raids.invite(player, args.drop(1).joinToString(" "))
                    "join" -> raids.joinInvited(player)
                    "leave" -> raids.leaveParty(player)
                    "kick" -> raids.kick(player, args.drop(1).joinToString(" "))
                    "start" -> raids.start(player, mode(args.getOrNull(1)))
                    "rejoin" -> raids.openLobby(player)
                    "status" -> player.mes(raids.status(player))
                }
            }
        }
        onCommand("tobdev") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Theatre developer controls"
            invalidArgs =
                "Use ::tobdev list|start <mode>|maiden|bloat|nylocas <wave>|nylocasboss|sotetseg <phase>|xarpus <phase>|verzik <phase>|complete|wipe|reset|status"
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "list" -> player.mes("ToB rooms: ${TobRoom.entries.joinToString { it.key }}")
                    "start" ->
                        raids.start(player, mode(args.getOrNull(1)), bypassRequirements = true)
                    "maiden" -> raids.forceRoom(player, TobRoom.MAIDEN)
                    "bloat" -> raids.forceRoom(player, TobRoom.BLOAT)
                    "nylocas" -> {
                        raids.forceRoom(player, TobRoom.NYLOCAS)
                        raids.forceWave(player, args.getOrNull(1)?.toIntOrNull() ?: 1)
                    }
                    "nylocasboss" -> raids.forceNylocasBoss(player)
                    "sotetseg" ->
                        raids.forceSotetsegPhase(player, args.getOrNull(1)?.toIntOrNull() ?: 1)
                    "xarpus" ->
                        raids.forceXarpusPhase(player, args.getOrNull(1)?.toIntOrNull() ?: 1)
                    "room" ->
                        TobRoom.find(args.getOrNull(1).orEmpty())?.let {
                            raids.forceRoom(player, it)
                        }
                    "wave" -> raids.forceWave(player, args.getOrNull(1)?.toIntOrNull() ?: 1)
                    "verzik" ->
                        raids.forceVerzikPhase(player, args.getOrNull(1)?.toIntOrNull() ?: 1)
                    "score" -> raids.setScore(player, args.getOrNull(1)?.toIntOrNull() ?: 0)
                    "complete" -> raids.forceCompleteRoom(player)
                    "reward" -> raids.forceRewards(player)
                    "wipe" -> raids.forceWipe(player)
                    "reset" -> raids.reset(player)
                    "status" -> player.mes(raids.status(player))
                }
            }
        }
    }

    private fun ProtectedAccess.inviteLobbyPlayer(target: Player) {
        val inviter = player
        val inviterName = inviter.displayName
        val partyId = raids.offerLobbyInvite(inviter, target) ?: return
        val launched =
            protectedAccess.launch(
                target,
                busyText = "Finish what you are doing before responding to the Theatre invite.",
            ) {
                val accepted =
                    choice2(
                        "Accept",
                        true,
                        "Decline",
                        false,
                        title = "$inviterName has invited you to join their Theatre party.",
                    )
                raids.respondToLobbyInvite(target, partyId, accepted)
            }
        if (!launched) {
            raids.cancelLobbyInvite(target, partyId)
            inviter.mes("${target.displayName} is too busy to receive a Theatre invitation.")
        }
    }

    private fun ProtectedAccess.showRewardInterface(roll: TobRewardRoll) {
        val rewardInventory = Inventory.create(REWARD_DISPLAY_INVENTORY)
        check(rewardInventory.size >= roll.rewards.size) {
            "ToB reward display inventory has too few slots. " +
                "(size=${rewardInventory.size}, rewards=${roll.rewards.size})"
        }
        roll.rewards.forEachIndexed { slot, reward ->
            val internal = RSCM.getReverseMapping(RSCMType.OBJ, reward.objId)
            rewardInventory[slot] = InvObj(internal, reward.count)
        }
        invTransmit(rewardInventory)
        // The donor Theatre uses stock cache interface 23 and binds its reward inventory to
        // component 12 with client script 149. `interfaceInvInit` is that exact script helper.
        ifOpenMainModal(RSCM.getReverseMapping(RSCMType.INTERFACE, REWARD_INTERFACE))
        interfaceInvInit(
            inv = rewardInventory,
            target = RSCM.getReverseMapping(RSCMType.COMPONENT, REWARD_ITEMS_COMPONENT),
            objRowCount = 2,
            objColCount = REWARD_COLUMNS,
        )
    }

    private suspend fun ProtectedAccess.talkToVerzik(npc: Npc) {
        if (!raids.canStartVerzik(player, npc)) return
        startDialogue(npc) {
            chatNpc(
                neutral,
                "Now that was quite the show! I haven't been that entertained in a long time.",
            )
            chatNpc(
                neutral,
                "Of course, you know I can't let you leave here alive. Time for your final performance...",
            )
            if (choice2("Yes, let's begin.", 1, "No.", 2) == 1) {
                raids.startVerzik(player, npc)
            }
        }
    }

    private fun mode(value: String?): TobMode =
        when (value?.lowercase()) {
            "entry",
            "story" -> TobMode.ENTRY
            "hard",
            "hm",
            "hmt" -> TobMode.HARD
            else -> TobMode.NORMAL
        }

    private companion object {
        private const val REWARD_INTERFACE = 23
        private const val REWARD_ITEMS_COMPONENT = (REWARD_INTERFACE shl 16) or 12
        private const val REWARD_DISPLAY_INVENTORY = "inv.deathkeep_items"
        private const val REWARD_COLUMNS = 4
    }
}
