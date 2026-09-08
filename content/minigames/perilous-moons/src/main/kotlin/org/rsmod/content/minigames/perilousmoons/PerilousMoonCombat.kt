package org.rsmod.content.minigames.perilousmoons

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Hit, helper, subdue, and no-drop authority for every personal Perilous Moon npc. */
internal class PerilousMoonCombat
@Inject
constructor(
    private val encounters: PerilousMoonEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        PerilousMoon.entries.forEach { registerBoss(it) }
        MOON_HELPER_NPCS.forEach { registerHelper(it) }
    }

    private fun ScriptContext.registerBoss(moon: PerilousMoon) {
        val type = requireNpc(moon.bossNpc, moon.bossNpcId)
        onModifyNpcHit(type) {
            val attacker =
                if (hit.isFromPlayer) {
                    hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                } else null
            if (attacker == null) {
                hit.damage = 0
            } else {
                encounters.modifyNpcHit(attacker, npc, hit)
            }
        }
        onNpcHit(type) {
            val attacker = if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
            if (attacker != null) encounters.onNpcHit(attacker, npc, hit.damage)
        }
        onNpcQueue(type, "queue.death") {
            val run = encounters.beginSubdue(npc)
            death.deathNoDrops(this)
            if (run != null) encounters.finishSubdue(npc)
        }
    }

    private fun ScriptContext.registerHelper(internalName: String) {
        val type = requireNpc(internalName)
        onModifyNpcHit(type) {
            val attacker =
                if (hit.isFromPlayer) {
                    hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                } else null
            if (attacker == null) {
                hit.damage = 0
            } else {
                encounters.modifyNpcHit(attacker, npc, hit)
            }
        }
        onNpcHit(type) {
            val attacker = if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
            if (attacker != null) encounters.onNpcHit(attacker, npc, hit.damage)
        }
        onNpcQueue(type, "queue.death") { death.deathNoDrops(this) }
    }

    private fun requireNpc(internalName: String, expectedId: Int? = null) =
        requireNotNull(ServerCacheManager.getNpc(internalName.asRSCM(RSCMType.NPC))) {
                "Missing Perilous Moons npc definition: $internalName"
            }
            .also { type ->
                check(expectedId == null || type.id == expectedId) {
                    "Perilous Moons npc mapping drifted: $internalName expected=$expectedId actual=${type.id}"
                }
            }
}
