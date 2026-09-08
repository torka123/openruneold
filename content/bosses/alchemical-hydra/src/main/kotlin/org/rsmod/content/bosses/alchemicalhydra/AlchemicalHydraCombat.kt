package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class AlchemicalHydraCombat
@Inject
constructor(
    private val encounters: AlchemicalHydraEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        AlchemicalHydraAssets.activeForms.forEach { id ->
            val type = requireNpc(id)
            onAiApPlayer2(type) { encounters.attack(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                val dropTile = encounters.beginBossDeath(npc)
                if (dropTile == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathWithDrops(this, dropTile)
                    encounters.finishBossDeath(npc)
                }
            }
        }
        AlchemicalHydraAssets.transitionForms.forEach { id ->
            val type = requireNpc(id)
            onModifyNpcHit(type) { hit.damage = 0 }
            onNpcQueue(type, "queue.death") { death.deathNoDrops(this) }
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Hydra NPC $id" }
}
