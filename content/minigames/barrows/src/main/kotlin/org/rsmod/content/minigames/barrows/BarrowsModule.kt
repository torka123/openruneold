package org.rsmod.content.minigames.barrows

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class BarrowsModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(BarrowsKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(BarrowsAttackHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(BarrowsDeathCleanupHook::class.java)
        addSetBinding<PlayerPostTickHook>(BarrowsLifecycleEvents::class.java)
    }
}
