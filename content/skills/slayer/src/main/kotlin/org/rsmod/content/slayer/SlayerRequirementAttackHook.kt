package org.rsmod.content.slayer

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.stat.statBase
import org.rsmod.content.slayer.core.CanonicalSlayerRegistry
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Enforces canonical Slayer requirements at the central player-to-NPC attack boundary. */
class SlayerRequirementAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val required = CanonicalSlayerRegistry.requiredLevel(npc.visType.id)
            ?: return NpcAttackValidateResult.Pass
        val level = player.statBase("stat.slayer")
        if (level >= required) return NpcAttackValidateResult.Pass
        return NpcAttackValidateResult.Deny(
            "You need a Slayer level of $required to attack this creature.",
        )
    }
}
