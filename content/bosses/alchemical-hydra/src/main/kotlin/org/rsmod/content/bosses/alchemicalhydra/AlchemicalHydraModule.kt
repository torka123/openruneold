package org.rsmod.content.bosses.alchemicalhydra

import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

public class AlchemicalHydraModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(AlchemicalHydraAttackHook::class.java)
    }
}

internal class AlchemicalHydraAttackHook
@Inject
constructor(private val encounters: HydraEncounterManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isParticipant(player, npc)) {
            return NpcAttackValidateResult.Deny("That creature belongs to another adventurer's encounter.")
        }
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}
