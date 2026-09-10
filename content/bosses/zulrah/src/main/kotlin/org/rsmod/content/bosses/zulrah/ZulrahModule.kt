package org.rsmod.content.bosses.zulrah

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathStorageHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class ZulrahModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(ZulrahAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(ZulrahPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathStorageHook>(ZulrahDeathStorage::class.java)
        addSetBinding<PlayerPostTickHook>(ZulrahLifecycle::class.java)
    }
}
