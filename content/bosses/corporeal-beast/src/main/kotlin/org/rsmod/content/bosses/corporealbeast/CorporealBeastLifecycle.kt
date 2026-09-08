package org.rsmod.content.bosses.corporealbeast

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CorporealBeastLifecycle
@Inject
constructor(private val encounters: CorporealBeastEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<NpcStateEvents.Delete> {
            if (
                npc.id == CorporealBeastAssets.BOSS_NPC_ID ||
                    npc.id == CorporealBeastAssets.DARK_CORE_NPC_ID
            ) {
                encounters.npcDeleted(npc)
            }
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.id == CorporealBeastAssets.BOSS_NPC_ID) encounters.npcRespawned(npc)
        }
    }
}
