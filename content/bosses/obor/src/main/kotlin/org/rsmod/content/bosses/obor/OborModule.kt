package org.rsmod.content.bosses.obor

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.plugin.module.PluginModule

public class OborModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(OborDeathHook::class.java)
    }
}

internal class OborDeathHook
@Inject
constructor(
    private val instances: InstanceManager,
    private val state: OborRunState,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        if (npc.type.internalName != OBOR_NPC) {
            return
        }
        val instanceId = instances.instanceForNpc(npc) ?: return

        // Obor remains down until the chest is tried. NpcRepository watches this mutable cycle.
        npc.lifecycleRespawnCycle = Int.MAX_VALUE
        state.markDefeated(instanceId)
    }
}
