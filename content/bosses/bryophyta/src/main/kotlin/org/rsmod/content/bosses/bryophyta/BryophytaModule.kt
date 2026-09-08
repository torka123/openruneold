package org.rsmod.content.bosses.bryophyta

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.plugin.module.PluginModule

public class BryophytaModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(BryophytaDeathHook::class.java)
    }
}

internal class BryophytaDeathHook
@Inject
constructor(
    private val instances: InstanceManager,
    private val state: BryophytaRunState,
    private val npcRepo: NpcRepository,
    private val encounters: EncounterRegistry,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        when (npc.type.internalName) {
            BRYOPHYTA_NPC -> {
                encounters.existing(npc)?.drainOwnedNpcs()?.forEach { growthling ->
                    growthling.respawns = false
                    if (growthling.isSlotAssigned) {
                        npcRepo.del(growthling, Int.MAX_VALUE)
                    }
                }
                val instanceId = instances.instanceForNpc(npc)
                if (instanceId != null) {
                    npc.lifecycleRespawnCycle = Int.MAX_VALUE
                    state.markDefeated(instanceId)
                }
            }
        }
    }
}
