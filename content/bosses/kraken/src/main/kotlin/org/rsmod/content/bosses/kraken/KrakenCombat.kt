package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class KrakenCombat
@Inject
constructor(private val encounters: KrakenEncounterManager, private val death: NpcDeath) :
    PluginScript() {
    override fun ScriptContext.startup() {
        val boss = requireNpc(KrakenAssets.BOSS)
        onAiApPlayer2(boss) { encounters.attack(this, it.target) }
        onNpcQueue(boss, "queue.death") {
            val dropTile = encounters.beginBossDeath(npc)
            if (dropTile == null) {
                death.deathNoDrops(this)
            } else {
                death.deathWithDrops(this, dropTile)
                encounters.finishBossDeath(npc)
            }
        }

        val tentacle = requireNpc(KrakenAssets.TENTACLE)
        onAiApPlayer2(tentacle) { encounters.attack(this, it.target) }
        onNpcQueue(tentacle, "queue.death") {
            death.deathNoDrops(this)
            encounters.finishTentacleDeath(npc)
        }
    }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Kraken NPC definition: $internal"
        }
}
