package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onNpcQueueWithArgs
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class RoyalTitansCombat
@Inject
constructor(
    private val encounters: RoyalTitansEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
    private val deps: BossDeps,
    private val phases: RoyalTitansPhaseProgram,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("royal-titans.arena-action") { _, npc, _, data ->
            phases.ready(npc, data as RoyalTitansAction)
        }
        for (id in listOf(RoyalTitansAssets.BRANDA_ACTIVE, RoyalTitansAssets.ELDRIC_ACTIVE)) {
            val type = requireNpc(id)
            // Scripted Titans keep their arrival-driven route when hit. Native hit/contribution
            // and queue-token processing still run; only the default chase action is consumed.
            onNpcQueue(type, "queue.com_retaliate_player") {}
            val element =
                if (id == RoyalTitansAssets.BRANDA_ACTIVE) RoyalElement.FIRE else RoyalElement.ICE
            BossCombat.register(
                this,
                royalTitansTitanSpec(element),
                deps,
                ownerSlug = "royal-titans-${element.name.lowercase()}-titan",
                canTarget = encounters::canAttackTitan,
                onImpactHit = { encounters.npcHit(npc, hit) },
                onModifyHit = {
                    val player =
                        if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                        else null
                    encounters.modifyNpcHit(player, npc, hit)
                },
            )
            onNpcQueue(type, "queue.death") {
                if (encounters.beginTitanDeath(npc) != null) encounters.finishTitanDeath(npc)
            }
        }

        for (id in listOf(RoyalTitansAssets.BRANDA_CORPSE, RoyalTitansAssets.ELDRIC_CORPSE)) {
            val type = requireNpc(id)
            onModifyNpcHit(type) { hit.damage = 0 }
            // Inactive corpses cannot receive combat hits or HP headbars. Consume the native queue
            // without calling the living-NPC hit processor. Corpse cache stats are for interaction.
            onNpcQueue(type, "queue.hit") {}
            // A projectile already in flight can enqueue another hit after transformation. Never
            // let a residual death queue fall through to generic drops/deletion for the corpse.
            onNpcQueue(type, "queue.death") { npc.queueList.clear() }
        }

        for (id in listOf(RoyalTitansAssets.FIRE_ELEMENTAL, RoyalTitansAssets.ICE_ELEMENTAL)) {
            val type = requireNpc(id)
            val element =
                if (id == RoyalTitansAssets.FIRE_ELEMENTAL) RoyalElement.FIRE else RoyalElement.ICE
            BossCombat.register(
                this,
                royalTitansElementalSpec(element),
                deps,
                ownerSlug = "royal-titans-${element.name.lowercase()}-elemental",
                canTarget = encounters::canAttackElemental,
                onImpactHit = { encounters.npcHit(npc, hit) },
                onModifyHit = {
                    // Preserve the existing elemental-only incoming-damage/spell policy.
                    val player =
                        if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                        else null
                    if (player == null) hit.damage = 0
                    else encounters.modifyNpcHit(player, npc, hit)
                },
            )
            onNpcQueue(type, "queue.death") { encounters.finishElementalDeath(npc) }
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
            onNpcQueueWithArgs<Hit>(type, "queue.hit") { encounters.wallHit(npc, it.args) }
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) {
            "Missing revision-240 Royal Titans NPC $id"
        }
}
