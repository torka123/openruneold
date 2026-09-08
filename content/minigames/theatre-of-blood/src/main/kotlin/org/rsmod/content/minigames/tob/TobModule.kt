package org.rsmod.content.minigames.tob

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.plugin.module.PluginModule

public class TobModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(TobNpcAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(TobNpcKillHook::class.java)
        addSetBinding<PlayerDeathHook>(TobPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(TobDeathCleanupHook::class.java)
        addSetBinding<SpecialAttackMap>(DawnbringerSpecialAttack::class.java)
    }
}
