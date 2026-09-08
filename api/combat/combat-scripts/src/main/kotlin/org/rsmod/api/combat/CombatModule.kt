package org.rsmod.api.combat

import org.rsmod.api.combat.scripts.NpcMeleeApRangeHook
import org.rsmod.plugin.module.PluginModule

internal class CombatModule : PluginModule() {
    override fun bind() {
        bindInstance<NvPCombat>()
        bindInstance<PvNCombat>()
        bindInstance<PvPCombat>()
        newSetBinding<NpcMeleeApRangeHook>()
    }
}
