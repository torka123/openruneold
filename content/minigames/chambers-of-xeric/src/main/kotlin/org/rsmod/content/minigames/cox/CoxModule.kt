package org.rsmod.content.minigames.cox

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.plugin.module.PluginModule

public class CoxModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(CoxNpcAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(CoxNpcKillHook::class.java)
        addSetBinding<PlayerDeathHook>(CoxPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(CoxDeathCleanupHook::class.java)
    }
}
