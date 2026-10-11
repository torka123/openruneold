package org.rsmod.content.bosses.royaltitans

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathDropLocationHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.plugin.module.PluginModule

public class RoyalTitansModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(RoyalTitansAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(RoyalTitansPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(RoyalTitansDeathCleanup::class.java)
        addSetBinding<PlayerDeathDropLocationHook>(RoyalTitansDeathDropLocation::class.java)
    }
}
