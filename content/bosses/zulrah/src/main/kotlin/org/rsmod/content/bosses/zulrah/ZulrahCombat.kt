package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ZulrahCombat
@Inject
constructor(
    private val encounters: ZulrahEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        ZulrahAssets.forms.forEach { internal ->
            val type = requireNpc(internal)
            onAiApPlayer2(type) { encounters.attack(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                val run = encounters.beginBossDeath(npc)
                if (run == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathNoDrops(this)
                    if (!run.debug) death.spawnDrops(this, encounters.dropTile(npc))
                    encounters.finishBossDeath(npc)
                }
            }
        }
        ZulrahAssets.snakelings.forEach { internal ->
            val type = requireNpc(internal)
            onAiApPlayer2(type) { encounters.attackSnakeling(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                death.deathNoDrops(this)
                encounters.finishSnakelingDeath(npc)
            }
        }
    }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Zulrah npc definition: $internal"
        }
}
