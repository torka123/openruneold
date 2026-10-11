package org.rsmod.content.bosses.yama

import org.rsmod.api.combat.scripts.NpcMeleeApRangeHook
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathStorageHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class YamaModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(YamaAttackHook::class.java)
        addSetBinding<NpcMeleeApRangeHook>(YamaJudgeMeleeApRangeHook::class.java)
        addSetBinding<PlayerDeathHook>(YamaPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathStorageHook>(YamaDeathStorage::class.java)
        addSetBinding<PlayerPostTickHook>(YamaLifecycle::class.java)
    }
}
