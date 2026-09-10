package org.rsmod.content.bosses.muspah

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
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
    private val deps: BossDeps,
    private val parameters: MuspahAttackParameters,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.registerAttackParameters("muspah.attack") { npc, target, data ->
            parameters.resolve(npc, target, data as MuspahAttack)
        }
        BossCombat.register(
            this,
            muspahCombatSpec(),
            deps,
            "phantom-muspah",
            canTarget = encounters::canAttack,
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                // Existing source exclusion is a mechanical evidence gap, not a new DSL rule.
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            },
        )
        for (internal in MuspahAssets.npcForms) {
            val type = requireNpc(internal)
            onNpcQueue(type, "queue.death") {
                val run = encounters.beginBossDeath(npc)
                if (run == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathWithDrops(this, npc.coords, respawnDelay = MUSPAH_RESPAWN_TICKS)
                    encounters.finishBossDeath(run)
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
