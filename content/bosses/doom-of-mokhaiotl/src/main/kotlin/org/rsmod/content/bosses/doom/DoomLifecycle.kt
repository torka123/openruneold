package org.rsmod.content.bosses.doom

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
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class DoomAttackHook @Inject constructor(private val encounters: DoomEncounterManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isOwnedBy(player, npc))
            return NpcAttackValidateResult.Deny(
                "That creature belongs to another adventurer's delve."
            )
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class DoomPlayerDeathHook
@Inject
constructor(private val encounters: DoomEncounterManager) : PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!encounters.isActive(context.player)) return null
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
internal class DoomDeathCleanup @Inject constructor(private val encounters: DoomEncounterManager) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        if (!encounters.isActive(player)) return
        val hasCompletedRewards = encounters.pendingRewards(player).isNotEmpty()
        encounters.recordDeath(player)
        encounters.abort(player, "player death", teleport = true, clearRewards = true)
        if (hasCompletedRewards) {
            player.mes("Every reward accumulated during this delve has been forfeited.")
        } else {
            player.mes("You return to the Mokhaiotl lobby.")
        }
    }
}

internal class DoomLifecycle @Inject constructor(private val encounters: DoomEncounterManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        // Doom emits delayed projectiles, map animations, sounds, and locations while ticking.
        // LateCycle runs before player zone buffers are sent and cleared.
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(DOOM_INSTANCE_KEY)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(DOOM_INSTANCE_KEY)) {
            encounters.membershipEnded(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == DOOM_INSTANCE_KEY) encounters.externalTeleport(player, instanceId)
        }
        onPlayerLogout {
            encounters.abort(
                player,
                "logout or disconnect",
                teleport = false,
                logout = true,
                clearRewards = false,
            )
        }
    }
}
