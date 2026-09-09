package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
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
    private val deps: BossDeps,
    private val parameters: ZulrahAttackParameters,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.registerAttackParameters("zulrah.standard") { npc, target, data ->
            parameters.standard(npc, target, data as ZulrahAttackStyle)
        }
        deps.extensionRegistry.registerAttackParameters("zulrah.snakeling") { npc, target, data ->
            parameters.snakeling(npc, target, data as Boolean)
        }
        for (name in listOf("tail", "clouds", "egg")) {
            deps.extensionRegistry.register("zulrah.$name") { _, npc, target, data ->
                encounters.launchSpecial(npc, target, requireNotNull(data))
            }
        }
        deps.extensionRegistry.register("zulrah.surface") { _, npc, target, data ->
            encounters.present(npc, target, data as ZulrahSurface)
        }
        for ((name, spec) in
            listOf(
                "zulrah" to zulrahCombatSpec(),
                "zulrah-snakeling-melee" to snakelingSpec(false),
                "zulrah-snakeling-magic" to snakelingSpec(true),
            )) {
            BossCombat.register(
                this,
                spec,
                deps,
                name,
                canTarget = encounters::canAttack,
                onAbilityCommitted = encounters::onAbility,
                onModifyHit = {
                    val player =
                        if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                        else null
                    // Retained source exclusion is an unresolved mechanics issue, not a new rule.
                    if (player == null) hit.damage = 0
                    else encounters.modifyNpcHit(player, npc, hit)
                },
            )
        }
        ZulrahAssets.forms.forEach { internal ->
            val type = requireNpc(internal)
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
