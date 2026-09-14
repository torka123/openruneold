package org.rsmod.content.bosses.doom

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
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

internal class DoomCombat
@Inject
constructor(
    private val encounters: DoomEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
    private val npcs: NpcList,
    private val deps: BossDeps,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.registerAttackParameters(DOOM_ATTACK_PARAMETERS) { npc, target, data
            ->
            encounters.attackParameters(npc, target, data as DoomPrimaryAttack)
        }
        deps.extensionRegistry.register(DOOM_ROCK_ATTACK) { access, _, target, _ ->
            encounters.rockAttack(access, target)
        }
        BossCombat.register(
            this,
            doomBossSpec(),
            deps,
            ownerSlug = "doom-of-mokhaiotl",
            canTarget = encounters::prepareAttack,
            onModifyHit = {
                val player = resolveSourcePlayer(hit)
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            },
            onAbilityCommitted = { npc, target, _ -> encounters.afterAttack(npc, target) },
        )
        for (id in listOf(DoomAssets.BOSS, DoomAssets.SHIELDED, DoomAssets.BURROWED)) {
            val type = requireNpc(id)
            onNpcQueue(type, "queue.death") {
                val run = encounters.beginNpcDeath(npc)
                if (run != null) {
                    death.deathNoDrops(this)
                    encounters.finishNpcDeath(npc)
                }
            }
        }
        for (id in
            DoomAssets.npcIds - setOf(DoomAssets.BOSS, DoomAssets.SHIELDED, DoomAssets.BURROWED)) {
            val type = requireNpc(id)
            // These NPCs are encounter support entities, not independent combatants. Registering
            // the AI event consumes the default BasicCombat fallback so larvae/earth nodes never
            // acquire their own target or wander into combat.
            onAiApPlayer2(type) {}
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
        requireNotNull(dev.openrune.ServerCacheManager.getNpc(id)) {
            "Missing Doom NPC definition $id"
        }
}
