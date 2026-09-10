package org.rsmod.content.bosses.cerberus

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
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

internal class CerberusLifecycle
@Inject
constructor(private val encounters: CerberusEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<NpcStateEvents.Respawn> { encounters.onNativeRespawn(npc) }
    }
}
