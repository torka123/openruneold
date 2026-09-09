package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
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
    private val deps: BossDeps,
    private val parameters: AlchemicalHydraAttackParameters,
    private val damageRules: AlchemicalHydraDamageRules,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.registerAttackParameters("hydra.normal") { npc, target, data ->
            parameters.snapshot(npc, target, data as HydraAttackStyle)
        }
        HydraSpecial.entries.forEach { special ->
            deps.extensionRegistry.register("hydra.${special.name.lowercase()}") { _, npc, target, _
                ->
                encounters.launchSpecial(npc, target, special)
            }
        }
        BossCombat.register(
            this,
            alchemicalHydraBossSpec(),
            deps,
            "alchemical-hydra",
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else damageRules.modify(player, npc, hit)
            },
            onResolveHit = { damage = damageRules.resolve(npc, damage) },
            onImpactHit = { damageRules.impact(npc, hit.damage) },
            canTarget = encounters::canAttack,
        )
        AlchemicalHydraAssets.activeForms.forEach { id ->
            val type = requireNpc(id)
            onNpcQueue(type, "queue.death") {
                val dropTile = encounters.beginBossDeath(npc)
                if (dropTile == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathWithDrops(this, dropTile, respawnDelay = HYDRA_RESPAWN_TICKS)
                    encounters.finishBossDeath(npc)
                }
            }
        }
        AlchemicalHydraAssets.transitionForms.forEach { id ->
            val type = requireNpc(id)
            onNpcQueue(type, "queue.death") { death.deathNoDrops(this) }
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Hydra NPC $id" }
}
