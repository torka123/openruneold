package org.rsmod.content.bosses.royaltitans

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

internal class RoyalTitansCombat
@Inject
constructor(
    private val encounters: RoyalTitansEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (id in listOf(RoyalTitansAssets.BRANDA_ACTIVE, RoyalTitansAssets.ELDRIC_ACTIVE)) {
            val type = requireNpc(id)
            onAiApPlayer2(type) { encounters.attackTitan(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                val run = encounters.beginTitanDeath(npc)
                death.deathNoDrops(this)
                if (run != null) encounters.finishTitanDeath(npc)
            }
        }

        for (id in listOf(RoyalTitansAssets.FIRE_ELEMENTAL, RoyalTitansAssets.ICE_ELEMENTAL)) {
            val type = requireNpc(id)
            onAiApPlayer2(type) { encounters.attackElemental(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                death.deathNoDrops(this)
                encounters.finishAuxiliaryDeath(npc)
            }
        }

        for (id in listOf(RoyalTitansAssets.FIRE_WALL_HITBOX, RoyalTitansAssets.ICE_WALL_HITBOX)) {
            val type = requireNpc(id)
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") { death.deathNoDrops(this) }
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) {
            "Missing revision-240 Royal Titans NPC $id"
        }
}
