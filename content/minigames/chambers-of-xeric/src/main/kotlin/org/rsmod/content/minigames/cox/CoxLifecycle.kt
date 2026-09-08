package org.rsmod.content.minigames.cox

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
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class CoxNpcAttackHook @Inject constructor(private val raids: CoxRaidManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        raids.validateAttack(player, npc)
}

@Singleton
internal class CoxNpcKillHook @Inject constructor(private val raids: CoxRaidManager) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        raids.handleNpcKill(context.hero, context.npc)
    }
}

@Singleton
internal class CoxPlayerDeathHook @Inject constructor(private val raids: CoxRaidManager) :
    PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (raids.raidFor(context.player) == null) return null
        raids.markDeath(context.player)
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = context.player,
            dropDuration = PlayerDeathDrops.DROP_DURATION_STANDARD,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
        )
    }
}

@Singleton
internal class CoxDeathCleanupHook @Inject constructor(private val raids: CoxRaidManager) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        raids.respawnAfterDeath(player)
    }
}

@Singleton
internal class CoxLifecycle @Inject constructor(private val raids: CoxRaidManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { raids.tick() }
        onEvent<RaidLeaveRequestEvent>(instanceEventId(COX_INSTANCE_KEY)) {
            result = raids.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == COX_INSTANCE_KEY) raids.externalTeleport(player, instanceId)
        }
        onPlayerLogout { raids.logout(player) }
    }
}
