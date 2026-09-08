package org.rsmod.content.minigames.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.instances.events.RaidLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class TobNpcAttackHook @Inject constructor(private val raids: TobRaidManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        raids.validateAttack(player, npc)
}

@Singleton
internal class TobNpcKillHook @Inject constructor(private val raids: TobRaidManager) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) =
        raids.handleNpcKill(context.hero, context.npc)
}

@Singleton
internal class TobPlayerDeathHook @Inject constructor(private val raids: TobRaidManager) :
    PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (raids.raidFor(context.player) == null) return null
        raids.markDeath(context.player)
        return PlayerDeathHandling(
            Int.MAX_VALUE,
            context.player,
            PlayerDeathDrops.DROP_DURATION_STANDARD,
            0,
            false,
            UntradeableHandling.KEEP,
        )
    }
}

@Singleton
internal class TobDeathCleanupHook @Inject constructor(private val raids: TobRaidManager) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) = raids.respawnAfterDeath(player)
}

@Singleton
internal class TobLifecycle @Inject constructor(private val raids: TobRaidManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { raids.tick() }
        onEvent<RaidLeaveRequestEvent>(instanceEventId(TOB_INSTANCE_KEY)) {
            result = raids.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == TOB_INSTANCE_KEY) raids.externalTeleport(player, instanceId)
        }
        onPlayerCoordsChanged {
            if (isTobLobby(lastKnownCoords) != isTobLobby(player.coords)) {
                raids.syncLobbyInviteOption(player)
            }
        }
        onPlayerLogin {
            raids.cleanupOrphanedRaidItems(player)
            raids.syncLobbyInviteOption(player)
        }
        onPlayerLogout { raids.logout(player) }
    }
}
