package org.rsmod.content.bosses.dt2

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops.Companion.DROP_DURATION_STANDARD
import org.rsmod.api.death.PlayerDeathDrops.Companion.standardKeepCount
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.player.PlayerNpcApLineOfSightHook
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class Dt2AttackHook @Inject constructor(private val encounters: Dt2EncounterManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isOwnedBy(player, npc)) {
            return NpcAttackValidateResult.Deny(
                "That creature belongs to another adventurer's encounter."
            )
        }
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class Dt2PlayerNpcApLineOfSightHook
@Inject
constructor(private val encounters: Dt2EncounterManager) : PlayerNpcApLineOfSightHook {
    override fun bypassLineOfSight(player: Player, npc: Npc): Boolean =
        encounters.bypassLeviathanPlayerLineOfSight(player, npc)
}

@Singleton
internal class Dt2PlayerDeathHook @Inject constructor(private val encounters: Dt2EncounterManager) :
    PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!encounters.isActive(context.player)) return null
        if (
            encounters.isLeviathanActive(context.player) &&
                !encounters.rewardEligible(context.player)
        ) {
            return PlayerDeathHandling(
                keepCount = Int.MAX_VALUE,
                dropReceiver = context.player,
                dropDuration = DROP_DURATION_STANDARD,
                revealDelay = 0,
                supplyPile = false,
                untradeableHandling = UntradeableHandling.KEEP,
            )
        }
        return PlayerDeathHandling(
            keepCount = standardKeepCount(context.hasProtectItem),
            dropReceiver = context.player,
            dropDuration = DROP_DURATION_STANDARD,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.DROP,
        )
    }
}

@Singleton
internal class Dt2DeathCleanup @Inject constructor(private val encounters: Dt2EncounterManager) :
    PlayerDeathCleanupHook {
    override val priority: Int = 1_000

    override fun cleanup(player: Player) {
        encounters.playerDied(player)
    }
}

internal class Dt2Lifecycle @Inject constructor(private val encounters: Dt2EncounterManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        // Duke resolves attacks before player movement. A one-cycle hit queue then runs in the
        // same game cycle through the standard player hit/death processor.
        onEvent<GameLifecycle.StartCycle> { encounters.tickAll(dukeOnly = true) }
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(Dt2Boss.DUKE.key)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(Dt2Boss.DUKE.key)) {
            encounters.membershipEnded(player, instanceId)
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(Dt2Boss.VARDORVIS.key)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(Dt2Boss.VARDORVIS.key)) {
            encounters.membershipEnded(player, instanceId)
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(Dt2Boss.LEVIATHAN.key)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(Dt2Boss.LEVIATHAN.key)) {
            encounters.membershipEnded(player, instanceId)
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(Dt2Boss.WHISPERER.key)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(Dt2Boss.WHISPERER.key)) {
            encounters.membershipEnded(player, instanceId)
        }
    }
}
