package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.input.ResumePNameDialogInput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.ScriptContext

internal class RoyalTitansWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: RoyalTitansEncounterManager,
    private val scoreboard: RoyalTitansScoreboard,
    private val players: PlayerList,
    private val protectedAccess: ProtectedAccessLauncher,
) :
    InstanceScript(
        registry,
        ROYAL_TITANS_INSTANCE_KEY,
        royalTitansSpec(
            requireNotNull(ServerCacheManager.getNpc(RoyalTitansAssets.BRANDA_ACTIVE)),
            requireNotNull(ServerCacheManager.getNpc(RoyalTitansAssets.ELDRIC_ACTIVE)),
        ),
    ) {
    private var ProtectedAccess.warningSuppressed by boolVarBit("varbit.royal_titans_enter_warning")

    override fun ScriptContext.configure() {
        encounters.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onInstancePlayerLeave { encounters.onLeave(player, instanceId) }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(ROYAL_TITANS_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (!protectedAccess.launch(player) { leaveArena() }) RaidLeaveResult.Busy
                    else if (manager.sessionForPlayer(player)?.id != instanceId)
                        RaidLeaveResult.Left
                    else RaidLeaveResult.Failed
            }
        }
        val entrance = requireLoc(RoyalTitansAssets.ENTRANCE)
        onOpLoc1(entrance) {
            arriveDelay()
            entryMenu()
        }
        onOpLoc2(entrance) {
            arriveDelay()
            joinMenu()
        }
        onOpLoc3(entrance) {
            arriveDelay()
            startFight()
        }

        val exit = requireLoc(RoyalTitansAssets.EXIT)
        onOpLoc1(exit) {
            arriveDelay()
            leaveArena()
        }
        onOpLoc2(exit) {
            arriveDelay()
            leaveArena()
        }

        onOpLoc1(requireLoc(RoyalTitansAssets.SCOREBOARD)) {
            arriveDelay()
            scoreboard.show(this)
        }

        for (id in listOf(RoyalTitansAssets.BRANDA_CORPSE, RoyalTitansAssets.ELDRIC_CORPSE)) {
            val corpse = requireNpc(id)
            onOpNpc1(corpse) {
                arriveDelay()
                encounters.claim(player, it.npc, ClaimChoice.LOOT)
            }
            onOpNpc3(corpse.internalName) {
                arriveDelay()
                if (choice2("Destroy regular loot for doubled pet chance?", 1, "Cancel", 2) == 1) {
                    encounters.claim(player, it.npc, ClaimChoice.SACRIFICE)
                }
            }
            onOpNpc4(corpse.internalName) {
                arriveDelay()
                encounters.claim(player, it.npc, ClaimChoice.PAGES)
            }
        }

        onCommand("rtenter") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter a Royal Titans encounter"
            invalidArgs = "Use as ::rtenter"
            cheat { if (args.isEmpty()) protectedAccess.launch(player) { enterArena() } }
        }
        onCommand("rtjoin") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Join a Royal Titans encounter by player name"
            invalidArgs = "Use as ::rtjoin player name"
            cheat {
                if (args.isNotEmpty())
                    protectedAccess.launch(player) { joinArena(args.joinToString(" ")) }
            }
        }
        onCommand("rtstatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect the active Royal Titans encounter"
            invalidArgs = "Use as ::rtstatus"
            cheat { player.mes(encounters.status(player)) }
        }
    }

    private suspend fun ProtectedAccess.entryMenu() {
        when (
            choice3(
                "Start a fight (Your friends will be able to join you).",
                1,
                "Join a fight.",
                2,
                "Do nothing.",
                3,
            )
        ) {
            1 -> startFight()
            2 -> joinMenu()
        }
    }

    private suspend fun ProtectedAccess.startFight() {
        if (!confirmUnsafeDeath()) return
        ifClose()
        enterArena()
    }

    private suspend fun ProtectedAccess.joinMenu() {
        val result =
            nameDialog("Whose fight would you like to join? You must be on their friends' list.")
        val target = (result as? ResumePNameDialogInput.Result.SameWorld)?.uid
        if (!confirmUnsafeDeath()) return
        ifClose()
        // Resolve after the warning as well: a disconnected or reused slot is not the same player.
        joinArena(target?.resolve(players)?.displayName.orEmpty())
    }

    internal suspend fun ProtectedAccess.enterArena() {
        if (!canEnter()) return
        completeInstanceEntry(
            manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
        )
    }

    internal suspend fun ProtectedAccess.joinArena(name: String) {
        if (!canEnter()) return
        val session = manager.sessionForKeyAndMemberName(key, name.trim())
        if (session == null) {
            mes("That player does not have an available Royal Titans instance.")
            return
        }
        if (!encounters.canJoin(session)) {
            mes("That fight has progressed too far for another player to join.")
            return
        }
        completeInstanceEntry(manager.join(player, session, worldClock.cycle, forceAccess = true))
    }

    internal suspend fun ProtectedAccess.leaveArena() {
        if (manager.sessionForPlayer(player)?.key != key) {
            mes("You are not inside a Royal Titans encounter.")
            return
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key != key) mes("You leave the Royal Titans' arena.")
    }

    private fun ProtectedAccess.canEnter(): Boolean {
        if (player.hitpoints <= 0) {
            mes("Finish what you are doing before entering the Royal Titans' arena.")
            return false
        }
        if (manager.sessionForPlayer(player) != null) {
            mes("You already have an active instance.")
            return false
        }
        return true
    }

    private suspend fun ProtectedAccess.confirmUnsafeDeath(): Boolean {
        if (warningSuppressed) return true
        mesbox(
            "You are about to begin an encounter with the Royal Titans. Dying during this encounter " +
                "will not be considered a safe death. To leave, you'll need to use <col=ff0000>'Quick-escape'</col> " +
                "via the tunnel or teleport out. Are you sure you wish to proceed?"
        )
        return when (
            choice3(
                "Yes.",
                1,
                "Yes, and don't ask again.",
                2,
                "No.",
                3,
                title = "Are you sure you wish to proceed?",
            )
        ) {
            1 -> true
            2 -> {
                warningSuppressed = true
                true
            }
            else -> false
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Royal Titans NPC $id" }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing Royal Titans location $id" }
}
