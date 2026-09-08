package org.rsmod.content.minigames.corruptedgauntlet

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

internal class CorruptedGauntletCombat
@Inject
constructor(
    private val runs: CorruptedGauntletRunManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        CorruptedGauntletConfig.Npc.all.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getNpc(id))
            onAiApPlayer2(type) { runs.attack(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player != null && runs.isEncounterNpc(npc)) runs.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                if (!runs.beforeNpcDeath(npc)) {
                    death.deathWithDrops(this)
                    return@onNpcQueue
                }
                val boss =
                    id in
                        CorruptedGauntletConfig.Npc.HUNLLEF_MELEE_PROTECTED..CorruptedGauntletConfig
                                .Npc
                                .HUNLLEF_MAGIC_PROTECTED
                death.deathNoDrops(
                    this,
                    if (boss) CorruptedGauntletConfig.Sequence.HUNLLEF_DEATH else null,
                )
                runs.afterNpcDeath(npc)
            }
        }
    }
}
