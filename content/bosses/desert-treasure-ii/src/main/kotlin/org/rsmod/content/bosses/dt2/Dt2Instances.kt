package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.*
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.ScriptContext

internal val DT2_ENTRY_STATE = InstanceStateKey<Dt2Entry>("desert-treasure-ii-entry")

/** Immutable admission input retained by the pending native session until its keyed join. */
internal data class Dt2Entry(
    val player: Player,
    val boss: Dt2Boss,
    val difficulty: Dt2Difficulty,
    val seed: Long,
) {
    val playerUid: PlayerUid = player.uid
}

/** Shared entry mechanics; each boss has one native registered instance/lifecycle owner. */
internal abstract class Dt2InstanceScript(
    registry: BossInstanceRegistry,
    private val boss: Dt2Boss,
    private val encounters: Dt2EncounterManager,
    private val protectedAccess: ProtectedAccessLauncher,
    private val storage: LeviathanDeathStorage? = null,
) : InstanceScript(registry, boss.key, dt2InstanceSpec(boss)) {
    override fun ScriptContext.configure() {
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onInstancePlayerLeave { encounters.membershipEnded(player, instanceId) }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(key)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (!protectedAccess.launch(player) { leaveEncounter() }) RaidLeaveResult.Busy
                    else if (manager.sessionForPlayer(player)?.id != instanceId)
                        RaidLeaveResult.Left
                    else RaidLeaveResult.Failed
            }
        }
        val aliases =
            when (boss) {
                Dt2Boss.LEVIATHAN -> listOf("dt2lev")
                Dt2Boss.VARDORVIS -> listOf("dt2vi")
                Dt2Boss.WHISPERER -> listOf("dt2wi")
                Dt2Boss.DUKE -> emptyList()
            }
        for (name in listOf(boss.command, boss.alias) + aliases) {
            onCommand(name) {
                requiredRights = Rights.ADMINISTRATOR
                desc = "Start a private ${boss.displayName} encounter"
                invalidArgs = "Use as ::$name [awakened] [seed]"
                cheat {
                    val awakened = args.firstOrNull()?.equals("awakened", ignoreCase = true) == true
                    val difficulty = if (awakened) Dt2Difficulty.AWAKENED else Dt2Difficulty.NORMAL
                    val seed = args.getOrNull(if (awakened) 1 else 0)?.toLongOrNull() ?: 0xD72L
                    protectedAccess.launch(
                        player,
                        "Finish what you are doing before starting this encounter.",
                    ) {
                        enterEncounter(difficulty, seed)
                    }
                }
            }
        }
        if (boss != Dt2Boss.DUKE) {
            onCommand("leave${boss.command}") {
                desc = "Leave your current private ${boss.displayName} encounter"
                invalidArgs = "Use as ::leave${boss.command}"
                cheat {
                    if (args.isNotEmpty()) return@cheat
                    val session = manager.sessionForPlayer(player)
                    if (session == null || !encounters.isActive(player)) {
                        player.mes("You are not inside a private ${boss.displayName} encounter.")
                    } else {
                        // These legacy command aliases all leave the player's current DT2 room.
                        eventBus.publish(InstanceLeaveRequestEvent(player, session.key, session.id))
                    }
                }
            }
        }
        for (id in exitLocs()) {
            ServerCacheManager.getObject(id)?.let { type ->
                onOpLoc1(type) {
                    arriveDelay()
                    leaveEncounter(id)
                }
                if (boss == Dt2Boss.DUKE)
                    onOpLoc2(type) {
                        arriveDelay()
                        leaveEncounter(id)
                    }
            }
        }
    }

    internal suspend fun ProtectedAccess.enterEncounter(
        difficulty: Dt2Difficulty,
        seed: Long,
    ): Boolean {
        if (player.hitpoints <= 0) {
            mes("Finish what you are doing before starting this encounter.")
            return false
        }
        if (manager.sessionForPlayer(player) != null) {
            mes("Leave your current instance before starting another encounter.")
            return false
        }
        if (boss == Dt2Boss.VARDORVIS && difficulty == Dt2Difficulty.AWAKENED) {
            mes("Awakened Vardorvis is unavailable: revision-240 NPC 12224 is the quest form.")
            return false
        }
        if (boss == Dt2Boss.WHISPERER) {
            val fragment = requireNotNull(ServerCacheManager.getItem(Dt2Assets.BLACKSTONE_FRAGMENT))
            if (player.inv.count(fragment.internalName) == 0 && player.inv.isFull()) {
                mes("Make one free inventory space before entering The Whisperer.")
                return false
            }
        }
        val result =
            manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
        if (result is InstanceManager.Result.Created) {
            result.session.state(DT2_ENTRY_STATE) { Dt2Entry(player, boss, difficulty, seed) }
            player.combatClearQueue()
            player.clearInteraction()
            player.resetAnim()
        }
        completeInstanceEntry(result)
        return manager.sessionForPlayer(player)?.key == key && encounters.isActive(player)
    }

    internal suspend fun ProtectedAccess.leaveEncounter(expectedLocId: Int? = null): Boolean {
        val session = manager.sessionForPlayer(player)
        if (session?.key != key || !encounters.isActive(player)) {
            if (
                boss == Dt2Boss.LEVIATHAN &&
                    expectedLocId in Dt2Assets.LEVIATHAN_EXIT_ROWBOATS &&
                    storage?.hasItems(player) == true
            )
                return storage.reclaim(player)
            mes(
                if (expectedLocId == null)
                    "You are not inside a private ${boss.displayName} encounter."
                else "There is no encounter to leave here."
            )
            return false
        }
        if (expectedLocId != null) {
            if (expectedLocId !in exitLocs()) return false
            encounters.exitDenial(player, expectedLocId)?.let {
                mes(it)
                return true
            }
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player) === session) return false
        mes(
            if (expectedLocId != null) "You leave the encounter."
            else
                when (boss) {
                    Dt2Boss.VARDORVIS -> "You leave Vardorvis' private ritual site."
                    Dt2Boss.LEVIATHAN -> "You leave The Leviathan's private arena."
                    Dt2Boss.WHISPERER -> "You leave The Whisperer's private arena."
                    Dt2Boss.DUKE -> "You leave the private encounter."
                }
        )
        return true
    }

    private fun exitLocs(): List<Int> =
        when (boss) {
            Dt2Boss.DUKE -> listOf(47565, 47566)
            Dt2Boss.WHISPERER -> listOf(47584, 47585, 47586)
            Dt2Boss.VARDORVIS -> listOf(Dt2Assets.VARDORVIS_EXIT, Dt2Assets.VARDORVIS_EXIT_DISABLED)
            Dt2Boss.LEVIATHAN -> Dt2Assets.LEVIATHAN_EXIT_ROWBOATS
        }
}

private fun dt2InstanceSpec(boss: Dt2Boss): InstanceSpec {
    val npcId = if (boss == Dt2Boss.WHISPERER) Dt2Assets.WHISPERER_SPAWN_ID else boss.normalNpcId
    return boss.spec(requireNotNull(ServerCacheManager.getNpc(npcId)))
}

internal class DukeInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
    encounters: Dt2EncounterManager,
    protectedAccess: ProtectedAccessLauncher,
) : Dt2InstanceScript(registry, Dt2Boss.DUKE, encounters, protectedAccess)

internal class WhispererInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
    encounters: Dt2EncounterManager,
    protectedAccess: ProtectedAccessLauncher,
) : Dt2InstanceScript(registry, Dt2Boss.WHISPERER, encounters, protectedAccess)

internal class VardorvisInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
    encounters: Dt2EncounterManager,
    protectedAccess: ProtectedAccessLauncher,
) : Dt2InstanceScript(registry, Dt2Boss.VARDORVIS, encounters, protectedAccess)

internal class LeviathanInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
    encounters: Dt2EncounterManager,
    protectedAccess: ProtectedAccessLauncher,
    storage: LeviathanDeathStorage,
) : Dt2InstanceScript(registry, Dt2Boss.LEVIATHAN, encounters, protectedAccess, storage)
