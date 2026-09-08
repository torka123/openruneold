package org.rsmod.content.bosses.cerberus

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathDropLocationHook
import org.rsmod.plugin.module.PluginModule

public class CerberusModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(CerberusAttackHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(CerberusDeathCleanup::class.java)
        addSetBinding<PlayerDeathDropLocationHook>(CerberusDeathDropLocation::class.java)
    }
}
