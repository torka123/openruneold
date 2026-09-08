package org.rsmod.content.bosses.kraken

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
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
internal class KrakenAttackHook
@Inject
constructor(private val encounters: KrakenEncounterManager) : NpcAttackValidateHook {
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

internal class KrakenLifecycle @Inject constructor(private val encounters: KrakenEncounterManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(KRAKEN_INSTANCE_KEY)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KRAKEN_INSTANCE_KEY)) {
            encounters.membershipEnded(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == KRAKEN_INSTANCE_KEY) encounters.externalTeleport(player, instanceId)
        }
        onPlayerLogout { encounters.logout(player) }
    }
}
