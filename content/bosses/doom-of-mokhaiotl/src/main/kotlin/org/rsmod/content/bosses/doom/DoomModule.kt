package org.rsmod.content.bosses.doom

import org.rsmod.api.combat.manager.PlayerNpcAccuracyOverrideHook
import org.rsmod.api.combat.manager.PlayerNpcMeleeAttackDelayOverrideHook
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.plugin.module.PluginModule

public class DoomModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerNpcAccuracyOverrideHook>(DoomPlayerAccuracyHook::class.java)
        addSetBinding<PlayerNpcMeleeAttackDelayOverrideHook>(DoomMeleeAttackDelayHook::class.java)
        addSetBinding<NpcAttackValidateHook>(DoomAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(DoomPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(DoomDeathCleanup::class.java)
    }
}
