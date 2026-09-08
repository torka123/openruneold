package org.rsmod.content.bosses.cerberus

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
internal class CerberusAttackHook
@Inject
constructor(private val encounters: CerberusEncounterManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isParticipant(player, npc)) {
            return NpcAttackValidateResult.Deny(
                "That Cerberus belongs to another adventurer's encounter."
            )
        }
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class CerberusDeathCleanup
@Inject
constructor(private val encounters: CerberusEncounterManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) = encounters.playerDied(player)
}

internal class CerberusLifecycle
@Inject
constructor(private val encounters: CerberusEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(CERBERUS_INSTANCE_KEY)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(CERBERUS_INSTANCE_KEY)) {
            encounters.membershipEnded(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == CERBERUS_INSTANCE_KEY) encounters.externalTeleport(player, instanceId)
        }
        onPlayerLogout { encounters.logout(player) }
    }
}
