package org.rsmod.content.minigames.corruptedgauntlet

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.plugin.module.PluginModule

public class CorruptedGauntletModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(CorruptedGauntletAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(CorruptedGauntletDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(CorruptedGauntletDeathCleanupHook::class.java)
    }
}
