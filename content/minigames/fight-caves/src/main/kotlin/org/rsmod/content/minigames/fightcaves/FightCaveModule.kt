package org.rsmod.content.minigames.fightcaves

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class FightCaveModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(FightCaveAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(FightCavePlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(FightCaveDeathCleanupHook::class.java)
        addSetBinding<PlayerPostTickHook>(FightCaveLifecycle::class.java)
    }
}
