package org.rsmod.content.bosses.alchemicalhydra

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.plugin.module.PluginModule

public class AlchemicalHydraModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(AlchemicalHydraAttackHook::class.java)
    }
}
