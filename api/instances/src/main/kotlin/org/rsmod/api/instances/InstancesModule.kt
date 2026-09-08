package org.rsmod.api.instances

import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntValidateHook
import org.rsmod.api.instances.hook.InstanceBossDeathHook
import org.rsmod.api.instances.hook.InstanceDeathCleanupHook
import org.rsmod.api.instances.hook.InstanceLifecycleScript
import org.rsmod.api.instances.hook.InstanceNpcDamageContributor
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class InstancesModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcPlayerHuntValidateHook>(InstanceNpcPlayerHuntValidateHook::class.java)
        addSetBinding<NpcDamageContributor>(InstanceNpcDamageContributor::class.java)
        addSetBinding<PlayerDeathCleanupHook>(InstanceDeathCleanupHook::class.java)
        addSetBinding<NpcDeathKillHook>(InstanceBossDeathHook::class.java)
        addSetBinding<PlayerPostTickHook>(InstanceLifecycleScript::class.java)
    }
}
