package org.rsmod.content.bosses.corporealbeast

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CorporealBeastCombat
@Inject
constructor(private val encounters: CorporealBeastEncounterManager, private val death: NpcDeath) :
    PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
        val boss = requireNpc(CorporealBeastAssets.BOSS_NPC_ID)
        val core = requireNpc(CorporealBeastAssets.DARK_CORE_NPC_ID)

        onAiApPlayer2(boss) { encounters.attack(this, it.target) }
        onAiOpPlayer2(boss) { encounters.attack(this, it.target) }
        onNpcHit(boss) { encounters.onBossImpact(npc, hit.damage) }
        onNpcQueue(boss, "queue.death") {
            encounters.cleanup(npc)
            death.deathWithDrops(this)
        }

        onAiApPlayer2(core) { encounters.idleCore(this) }
        onAiOpPlayer2(core) { encounters.idleCore(this) }
        onNpcQueue(core, "queue.death") {
            encounters.coreDied(npc)
            death.deathNoDrops(this)
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 NPC definition: $id" }
}
