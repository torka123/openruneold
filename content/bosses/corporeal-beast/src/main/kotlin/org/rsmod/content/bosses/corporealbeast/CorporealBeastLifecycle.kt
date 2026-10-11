package org.rsmod.content.bosses.corporealbeast

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CorporealBeastLifecycle
@Inject
constructor(private val effects: CorporealBeastRoomEffects, private val npcs: NpcList) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> {
            npcs.filter { it.id == CorporealBeastAssets.BOSS_NPC_ID }.forEach(effects::tick)
        }
        onEvent<NpcStateEvents.Delete> {
            if (npc.id == CorporealBeastAssets.DARK_CORE_NPC_ID) effects.coreDied(npc)
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.id == CorporealBeastAssets.BOSS_NPC_ID) npc.copyStats(npc.type)
        }
    }
}
