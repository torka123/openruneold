package org.rsmod.content.bosses.muspah

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class MuspahCombat
@Inject
constructor(
    private val encounters: MuspahEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (internal in MuspahAssets.npcForms) {
            val type = requireNpc(internal)
            onAiApPlayer2(type) { encounters.attack(this, it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players) else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                val run = encounters.beginBossDeath(npc)
                if (run == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathWithDrops(this, npc.coords)
                    encounters.finishBossDeath(npc)
                }
            }
        }
    }

    private fun requireNpc(internal: String) =
        requireNotNull(
            dev.openrune.ServerCacheManager.getNpc(
                dev.openrune.rscm.RSCM.run { internal.asRSCM(dev.openrune.rscm.RSCMType.NPC) }
            )
        ) {
            "Missing Phantom Muspah npc definition: $internal"
        }
}
