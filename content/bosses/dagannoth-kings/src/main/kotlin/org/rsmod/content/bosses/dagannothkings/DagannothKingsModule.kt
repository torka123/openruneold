package org.rsmod.content.bosses.dagannothkings

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

public class DagannothKingsModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(DagannothKingsSlayerChamberHook::class.java)
    }
}

internal class DagannothKingsSlayerChamberHook @Inject constructor() : NpcAttackValidateHook {
    private val kingIds by lazy {
        DagannothKingsAssets.kings.mapTo(mutableSetOf()) { it.asRSCM(RSCMType.NPC) }
    }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (
            npc.id !in kingIds || !DagannothKingsRules.isSlayerChamber(npc.coords.x, npc.coords.z)
        ) {
            return NpcAttackValidateResult.Pass
        }
        if (SlayerTaskManager.countsAsCurrentTaskKill(player, npc)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny(
            "You can only attack the Dagannoth Kings in this chamber while on an appropriate Slayer task."
        )
    }
}
