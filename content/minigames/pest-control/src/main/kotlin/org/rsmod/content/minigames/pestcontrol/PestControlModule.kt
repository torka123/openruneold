package org.rsmod.content.minigames.pestcontrol

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class PestControlModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(PestControlAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(PestControlNpcKillHook::class.java)
        addSetBinding<PlayerDeathHook>(PestControlPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(PestControlDeathCleanupHook::class.java)
        addSetBinding<PlayerPostTickHook>(PestControlLifecycle::class.java)
    }
}
