package org.rsmod.content.minigames.toa

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
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.RaidLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class ToaNpcAttackHook @Inject constructor(private val raids: ToaRaidManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        raids.validateAttack(player, npc)
}

@Singleton
internal class ToaNpcKillHook @Inject constructor(private val raids: ToaRaidManager) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) =
        raids.handleNpcKill(context.hero, context.npc)
}

@Singleton
internal class ToaPlayerDeathHook @Inject constructor(private val raids: ToaRaidManager) :
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
internal class ToaDeathCleanupHook @Inject constructor(private val raids: ToaRaidManager) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) = raids.respawnAfterDeath(player)
}

@Singleton
internal class ToaLifecycle @Inject constructor(private val raids: ToaRaidManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { raids.login(player) }
        onEvent<RaidLeaveRequestEvent>(instanceEventId(TOA_INSTANCE_KEY)) {
            result = raids.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<GameLifecycle.LateCycle> { raids.tick() }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == TOA_INSTANCE_KEY) raids.externalTeleport(player, instanceId)
        }
        onPlayerLogout { raids.logout(player) }
    }
}
