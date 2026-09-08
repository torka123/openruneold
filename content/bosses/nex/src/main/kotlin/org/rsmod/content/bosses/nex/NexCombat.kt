package org.rsmod.content.bosses.nex

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

internal class NexCombat
@Inject
constructor(
    private val encounters: NexEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        NPC_IDS.forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Nex NPC $id" }
            onAiApPlayer2(type) { encounters.attack(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                val kind = encounters.handleDeath(npc)
                death.deathNoDrops(this)
                encounters.finishDeath(npc, kind)
            }
        }
    }

    private companion object {
        val NPC_IDS = (11278..11286).toList() + 11294
    }
}
