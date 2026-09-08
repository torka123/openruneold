package org.rsmod.content.minigames.toa

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.plugin.module.PluginModule

public class ToaModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(ToaNpcAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(ToaNpcKillHook::class.java)
        addSetBinding<PlayerDeathHook>(ToaPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(ToaDeathCleanupHook::class.java)
    }
}
