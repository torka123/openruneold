package org.rsmod.content.bosses.grotesqueguardians

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

internal class GrotesqueGuardiansCombat
@Inject
constructor(
    private val encounters: GrotesqueGuardiansEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        GrotesqueAssets.DAWN_FORMS.forEach { id ->
            val type = requireNpc(id)
            onAiApPlayer2(type) { encounters.attack(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") { death.deathNoDrops(this) }
        }
        GrotesqueAssets.DUSK_FORMS.forEach { id ->
            val type = requireNpc(id)
            onAiApPlayer2(type) { encounters.attack(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                val dropTile = encounters.beginDuskDeath(npc)
                if (dropTile == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathWithDrops(this, dropTile)
                    encounters.finishDuskDeath(npc)
                }
            }
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Guardian NPC $id" }
}
