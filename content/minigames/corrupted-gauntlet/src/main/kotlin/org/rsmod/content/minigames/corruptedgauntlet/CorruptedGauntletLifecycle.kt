package org.rsmod.content.minigames.corruptedgauntlet

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class CorruptedGauntletAttackHook
@Inject
constructor(private val runs: CorruptedGauntletRunManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!runs.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!runs.isParticipant(player, npc))
            return NpcAttackValidateResult.Deny("That creature belongs to another Gauntlet.")
        val denial = runs.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class CorruptedGauntletDeathHook
@Inject
constructor(private val runs: CorruptedGauntletRunManager) : PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!runs.isActive(context.player)) return null
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
internal class CorruptedGauntletDeathCleanupHook
@Inject
constructor(private val runs: CorruptedGauntletRunManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) = runs.playerDied(player)
}

internal class CorruptedGauntletLifecycle
@Inject
constructor(private val runs: CorruptedGauntletRunManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { runs.tickAll() }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(CORRUPTED_GAUNTLET_INSTANCE_KEY)) {
            result = runs.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(CORRUPTED_GAUNTLET_INSTANCE_KEY)) {
            runs.membershipEnded(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == CORRUPTED_GAUNTLET_INSTANCE_KEY) runs.externalTeleport(player, instanceId)
        }
        onPlayerLogin { runs.onLogin(player) }
        onPlayerLogout { runs.logout(player) }
    }
}
