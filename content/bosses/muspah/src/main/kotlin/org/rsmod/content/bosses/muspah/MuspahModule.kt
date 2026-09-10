package org.rsmod.content.bosses.muspah

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class MuspahModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(MuspahAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(MuspahPlayerDeathHook::class.java)
        addSetBinding<PlayerPostTickHook>(MuspahLifecycle::class.java)
    }
}
