package org.rsmod.content.minigames.cox

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import dev.openrune.types.ItemServerType
import dev.openrune.types.SequenceServerType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CoxWorld @Inject constructor(private val raids: CoxRaidManager) : PluginScript() {
    override fun ScriptContext.startup() {
        raids.validateAssets()

        onOpLoc1(requireNotNull(ServerCacheManager.getObject(CoxLocs.ENTRANCE))) {
            arriveDelay()
            enterDonorRaid()
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(CoxLocs.RECRUITING_BOARD))) {
            arriveDelay()
            openPartyBoard()
        }
        val donorPassages =
            CoxLocs.progression +
                setOf(CoxLocs.MYSTIC_BARRIER, CoxLocs.VANGUARD_CRYSTAL, CoxLocs.MUTTADILE_BARRIER)
        donorPassages.forEach { id ->
            onOpLoc1(requireNotNull(ServerCacheManager.getObject(id))) {
                raids.usePassage(player, it.loc)
            }
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(CoxLocs.OLM_BARRIER))) {
            passOlmBarrier(it.loc)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(CoxLocs.TIGHTROPE))) {
            crossTightrope(it.loc)
        }
        onOpLocU(
            requireNotNull(ServerCacheManager.getObject(CoxLocs.SHIMMERING_BARRIER)).internalName,
            requireNotNull(ServerCacheManager.getItem(CoxItems.KEYSTONE_CRYSTAL)).internalName,
        ) {
            arriveDelay()
            raids.usePassage(player, it.loc)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(CoxLocs.KEYSTONE_CRYSTAL))) {
            arriveDelay()
            raids.takeKeystone(player, it.loc)
        }
        ((CoxLocs.puzzle - CoxLocs.TIGHTROPE) + CoxLocs.resources).forEach { id ->
            onOpLoc1(requireNotNull(ServerCacheManager.getObject(id))) {
                arriveDelay()
                raids.puzzleAction(player, id)
            }
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(CoxLocs.STEPS))) {
            arriveDelay()
            if (choice2("Leave the Chambers", 1, "Stay", 2) == 1) raids.leaveParty(player)
        }
        onOpLoc1(requireNotNull(ServerCacheManager.getObject(CoxLocs.ANCIENT_CHEST))) {
            arriveDelay()
            val reward = raids.claim(player, it.loc.coords) ?: return@onOpLoc1
            anim("seq.human_openchest")
            showRewardInterface(reward)
        }
        onOpNpc1(requireNotNull(ServerCacheManager.getNpc(CoxNpcs.MUTTADILE_MEAT_TREE))) {
            chopMeatTree(it.npc)
        }

        onCommand("cox") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport beside the Chambers of Xeric entrance"
            invalidArgs = "Use as ::cox"
            cheat { if (args.isEmpty()) raids.teleportLobby(player) }
        }
        onCommand("coxpc") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter a Chambers forced-purple chest test without point requirements"
            invalidArgs = "Use ::coxpc"
            cheat { if (args.isEmpty()) raids.forceRewards(player, forceUnique = true) }
        }
        onCommand("coxparty") {
            desc = "Manage a Chambers party"
            invalidArgs =
                "Use ::coxparty create|invite <name>|join|leave|kick <name>|start normal|challenge|olm|rejoin|status"
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "create" -> raids.createParty(player)
                    "invite" -> raids.invite(player, args.drop(1).joinToString(" "))
                    "join" -> raids.joinInvited(player)
                    "leave" -> raids.leaveParty(player)
                    "kick" -> raids.kick(player, args.drop(1).joinToString(" "))
                    "start" -> {
                        val option = args.getOrNull(1).orEmpty()
                        val difficulty =
                            if (option.startsWith("c", true)) RaidDifficulty.CHALLENGE
                            else RaidDifficulty.NORMAL
                        raids.start(player, difficulty, olmOnly = option.startsWith("o", true))
                    }
                    "rejoin" -> raids.openLobby(player)
                    "status" -> player.mes(raids.status(player))
                }
            }
        }
        onCommand("coxdev") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Chambers developer controls"
            invalidArgs =
                "Use ::coxdev list|start normal|challenge|olm|room <name>|olmphase <phase>|points <amount>|complete|reset|status"
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "list" -> player.mes("CoX rooms: ${CoxRoom.entries.joinToString { it.key }}")
                    "start" -> {
                        val option = args.getOrNull(1).orEmpty()
                        val difficulty =
                            if (option.startsWith("c", true)) RaidDifficulty.CHALLENGE
                            else RaidDifficulty.NORMAL
                        raids.start(
                            player,
                            difficulty,
                            args.getOrNull(2)?.toLongOrNull() ?: 1L,
                            olmOnly = option.startsWith("o", true),
                        )
                    }
                    "room" ->
                        CoxRoom.find(args.getOrNull(1).orEmpty())?.let {
                            raids.forceRoom(player, it)
                        }
                    "olm",
                    "olmphase" -> raids.forceOlmPhase(player, args.getOrNull(1)?.toIntOrNull() ?: 1)
                    "points" -> raids.setPoints(player, args.getOrNull(1)?.toIntOrNull() ?: 0)
                    "complete" -> raids.forceComplete(player)
                    "reset" -> raids.reset(player)
                    "status" -> player.mes(raids.status(player))
                }
            }
        }
        onCommand("olmbot") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Spawn combat-only targets for Great Olm multi-hit testing"
            invalidArgs = "Use ::olmbot [1-5] or ::olmbot clear"
            cheat {
                if (args.firstOrNull()?.equals("clear", ignoreCase = true) == true) {
                    raids.clearOlmTestTargets(player)
                } else {
                    val count = args.firstOrNull()?.toIntOrNull() ?: 1
                    if (count !in 1..5) {
                        player.mes("Use ::olmbot [1-5] or ::olmbot clear")
                    } else {
                        raids.spawnOlmTestTargets(player, count)
                    }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.crossTightrope(loc: org.rsmod.game.loc.BoundLocInfo) {
        val destination = raids.prepareTightropeCrossing(player, loc) ?: return
        val start = player.coords
        val direction =
            when {
                destination.x > start.x -> FACE_EAST
                destination.x < start.x -> FACE_WEST
                destination.z > start.z -> FACE_NORTH
                else -> FACE_SOUTH
            }
        val previous = player.bas
        player.bas = TIGHTROPE_BALANCE_BAS
        try {
            anim(TIGHTROPE_READY_SEQUENCE)
            delay(1)
            exactMove(start, destination, 0, 300, direction, TeleportType.Exempt)
            delay(10)
        } finally {
            player.bas = previous
        }
        raids.puzzleAction(player, CoxLocs.TIGHTROPE)
    }

    private suspend fun ProtectedAccess.passOlmBarrier(loc: org.rsmod.game.loc.BoundLocInfo) {
        val destination = raids.prepareOlmBarrierCrossing(player, loc) ?: return
        exactMove(
            player.coords,
            destination,
            delay1 = 0,
            delay2 = 60,
            dir = FACE_NORTH,
            teleportType = TeleportType.Exempt,
        )
        delay(2)
        raids.activateOlmFor(player)
    }

    private suspend fun ProtectedAccess.enterDonorRaid() {
        raids.raidFor(player)?.let {
            raids.rejoin(player, it)
            return
        }
        val party = raids.partyFor(player) ?: raids.createParty(player)
        if (party.leader != player.uid) {
            mes("Your party leader must choose and start the raid.")
            return
        }
        when (
            choice3(
                "Create a regular raid",
                1,
                "Create an Olm-only raid",
                2,
                "Cancel",
                3,
                title = "Chambers of Xeric",
            )
        ) {
            1 -> raids.start(player, RaidDifficulty.NORMAL, olmOnly = false)
            2 -> raids.start(player, RaidDifficulty.NORMAL, olmOnly = true)
        }
    }

    private suspend fun ProtectedAccess.chopMeatTree(tree: Npc) {
        val axe = findMuttadileAxe(player)
        if (axe == null) {
            mes("You need an axe that you can use to chop the meat tree.")
            return
        }
        val animation = getInvObj(axe).axeWoodcuttingAnim.id
        while (raids.isActiveMeatTree(player, tree) && distanceTo(tree) <= 1) {
            player.faceNpc(tree)
            anim(animation)
            delay(MuttadileMeatTreeRules.CHOP_INTERVAL_TICKS)
            val damage = raids.chopMeatTree(player, tree) ?: break
            if (damage > 0) {
                PlayerSkillXP.internalAddXP(
                    player,
                    "stat.woodcutting",
                    MuttadileMeatTreeRules.WOODCUTTING_XP,
                    rate = 1.0,
                )
            }
        }
        resetAnim()
    }

    private fun findMuttadileAxe(player: Player): InvObj? {
        val candidates =
            buildList {
                player.righthand?.let(::add)
                addAll(player.inv.filterNotNull())
            }
        return candidates
            .filter { axe ->
                val type = getInvObj(axe)
                type.isContentType("content.woodcutting_axe") &&
                    player.woodcuttingLvl >= type.axeWoodcuttingReq
            }
            .maxByOrNull { getInvObj(it).axeWoodcuttingReq }
    }

    private fun ProtectedAccess.showRewardInterface(roll: CoxRewardRoll) {
        val display = Inventory.create(REWARD_DISPLAY_INVENTORY)
        check(display.size >= roll.rewards.size) {
            "CoX reward display inventory is too small (size=${display.size}, rewards=${roll.rewards.size})."
        }
        roll.rewards.forEachIndexed { slot, reward ->
            display[slot] =
                InvObj(RSCM.getReverseMapping(RSCMType.OBJ, reward.objId), reward.count)
        }
        // Interface 539 owns the dedicated `raids_rewards` inventory and initializes itself from
        // that container when it opens. The donor opens the interface first, transmits inventory
        // 581, then unlocks component 5; using the deathkeep inventory here leaves the stock
        // raids-reward client script with no items to build.
        ifOpenMainModal(RSCM.getReverseMapping(RSCMType.INTERFACE, REWARD_INTERFACE))
        invTransmit(display)
        ifSetEvents(
            RSCM.getReverseMapping(RSCMType.COMPONENT, REWARD_ITEMS_COMPONENT),
            0..9,
            IfEvent.Op1,
        )
    }

    private suspend fun ProtectedAccess.openPartyBoard() {
        val party = raids.partyFor(player)
        if (party == null) {
            when (choice3("Create a party", 1, "Join my invitation", 2, "Cancel", 3)) {
                1 -> raids.createParty(player)
                2 -> raids.joinInvited(player)
            }
            return
        }
        if (party.leader == player.uid) {
            when (choice3("Invite a player", 1, "Leave the party", 2, "Close", 3)) {
                1 -> {
                    val name = stringDialog("Enter the player's name:").trim()
                    if (name.isNotEmpty()) raids.invite(player, name)
                }
                2 -> raids.leaveParty(player)
            }
        } else if (choice2("Leave the party", 1, "Close", 2) == 1) {
            raids.leaveParty(player)
        }
    }

    private companion object {
        val ItemServerType.axeWoodcuttingReq: Int by objParam(params.levelrequire)
        val ItemServerType.axeWoodcuttingAnim: SequenceServerType by objParam(params.skill_anim)

        private const val REWARD_INTERFACE = 539
        private const val REWARD_ITEMS_COMPONENT = (REWARD_INTERFACE shl 16) or 5
        private const val REWARD_DISPLAY_INVENTORY = "inv.raids_rewards"

        private const val FACE_SOUTH = 0
        private const val FACE_WEST = 512
        private const val FACE_NORTH = 1024
        private const val FACE_EAST = 1536
        private const val TIGHTROPE_READY_SEQUENCE = 763
        private const val TIGHTROPE_WALK_SEQUENCE = 762
        private val TIGHTROPE_BALANCE_BAS =
            BasType(
                readyAnim = TIGHTROPE_READY_SEQUENCE,
                turnOnSpot = TIGHTROPE_READY_SEQUENCE,
                walkForward = TIGHTROPE_WALK_SEQUENCE,
                walkBack = TIGHTROPE_WALK_SEQUENCE,
                walkLeft = TIGHTROPE_WALK_SEQUENCE,
                walkRight = TIGHTROPE_WALK_SEQUENCE,
                running = TIGHTROPE_WALK_SEQUENCE,
            )
    }
}
