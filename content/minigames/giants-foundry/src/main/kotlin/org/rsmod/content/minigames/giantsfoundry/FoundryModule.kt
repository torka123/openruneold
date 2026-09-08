package org.rsmod.content.minigames.giantsfoundry

import org.rsmod.api.player.hook.PlayerTeleportHook
import org.rsmod.plugin.module.PluginModule

internal class FoundryModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerTeleportHook>(FoundryTeleportHook::class.java)
    }
}
