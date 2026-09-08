package org.rsmod.content.other.pkkits

import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.plugin.module.PluginModule

public class KitModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathCleanupHook>(KitDeathOpenHook::class.java)
    }
}
