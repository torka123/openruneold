package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class YamaCombat
@Inject
constructor(
    private val encounters: YamaEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
    private val npcs: NpcList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val boss = requireNpc(YamaAssets.BOSS)
        onAiApPlayer2(boss) { encounters.attack(this, it.target) }
        onModifyNpcHit(boss) {
            val player = resolveSourcePlayer(hit)
            if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
        }
        onNpcQueue(boss, "queue.death") {
            val run = encounters.beginBossDeath(npc)
            death.deathNoDrops(this)
            if (run != null) encounters.finishBossDeath(npc)
        }

        for (id in listOf(YamaAssets.VOID_FLARE, YamaAssets.JUDGE)) {
            val type = requireNpc(id)
            onModifyNpcHit(type) {
                val player = resolveSourcePlayer(hit)
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }

            onNpcQueue(type, "queue.death") {
                death.deathNoDrops(this)
                encounters.finishAuxiliaryDeath(npc)
            }
        }
    }

    private fun resolveSourcePlayer(hit: HitBuilder): Player? =
        when {
            hit.isFromPlayer -> hit.sourceUid?.let(::PlayerUid)?.resolve(players)
            hit.isFromNpc ->
                hit.sourceUid?.let(::NpcUid)?.resolve(npcs)?.spawnOwner?.resolve(players)
            else -> null
        }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Yama NPC $id" }
}
