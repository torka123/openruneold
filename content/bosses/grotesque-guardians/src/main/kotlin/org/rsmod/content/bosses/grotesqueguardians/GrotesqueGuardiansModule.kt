package org.rsmod.content.bosses.grotesqueguardians

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.plugin.module.PluginModule

public class GrotesqueGuardiansModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(GrotesqueAttackHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(GrotesqueDeathCleanup::class.java)
    }
}
