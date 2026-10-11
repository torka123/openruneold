package org.rsmod.content.bosses.kraken

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.plugin.module.PluginModule

public class KrakenModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(KrakenAttackHook::class.java)
    }
}
