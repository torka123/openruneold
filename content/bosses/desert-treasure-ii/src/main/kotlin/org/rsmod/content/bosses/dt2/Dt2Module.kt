package org.rsmod.content.bosses.dt2

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathStorageHook
import org.rsmod.api.game.process.player.PlayerNpcApLineOfSightHook
import org.rsmod.plugin.module.PluginModule

public class Dt2Module : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(Dt2AttackHook::class.java)
        addSetBinding<PlayerDeathHook>(Dt2PlayerDeathHook::class.java)
        addSetBinding<PlayerDeathStorageHook>(LeviathanDeathStorage::class.java)
        addSetBinding<PlayerDeathCleanupHook>(Dt2DeathCleanup::class.java)
        addSetBinding<PlayerNpcApLineOfSightHook>(Dt2PlayerNpcApLineOfSightHook::class.java)
    }
}
