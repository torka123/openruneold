package org.rsmod.content.bosses.alchemicalhydra

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class AlchemicalHydraAttackHook
@Inject
constructor(private val encounters: AlchemicalHydraEncounterManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isParticipant(player, npc)) {
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
internal class AlchemicalHydraDeathCleanup
@Inject
constructor(private val encounters: AlchemicalHydraEncounterManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        encounters.abort(player, "player death", teleport = false)
    }
}

internal class AlchemicalHydraLifecycle
@Inject
constructor(private val encounters: AlchemicalHydraEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(HYDRA_INSTANCE_KEY)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(HYDRA_INSTANCE_KEY)) {
            encounters.membershipEnded(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == HYDRA_INSTANCE_KEY) encounters.externalTeleport(player, instanceId)
        }
        onPlayerLogout { encounters.logout(player) }
    }
}
