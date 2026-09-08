package org.rsmod.content.minigames.perilousmoons

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class PerilousMoonModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(PerilousMoonAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(PerilousMoonPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(PerilousMoonDeathCleanupHook::class.java)
        addSetBinding<PlayerPostTickHook>(PerilousMoonLifecycle::class.java)
    }
}
