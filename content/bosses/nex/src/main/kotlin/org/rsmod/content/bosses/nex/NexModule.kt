package org.rsmod.content.bosses.nex

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class NexModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(NexAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(NexPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(NexDeathCleanup::class.java)
        addSetBinding<PlayerPostTickHook>(NexLifecycle::class.java)
    }
}
