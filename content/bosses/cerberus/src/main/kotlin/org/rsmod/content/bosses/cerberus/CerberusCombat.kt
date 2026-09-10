package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CerberusCombat
@Inject
constructor(
    private val encounters: CerberusEncounterManager,
    private val death: NpcDeath,
    private val deps: BossDeps,
    private val parameters: CerberusAttackParameters,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("cerberus.attack-presentation") { _, npc, _, data ->
            encounters.attackPresentation(npc, data)
        }
        deps.extensionRegistry.register("cerberus.souls") { _, npc, _, _ -> encounters.souls(npc) }
        deps.extensionRegistry.register("cerberus.lava") { _, npc, _, _ -> encounters.lava(npc) }
        deps.extensionRegistry.registerAttackParameters("cerberus.head-attack") { npc, target, data
            ->
            parameters.resolve(npc, target, data as CerberusHeadAttack)
        }
        BossCombat.register(
            this,
            cerberusCombatSpec(),
            deps,
            "cerberus",
            canTarget = encounters::canAttack,
            onImpactHit = { encounters.defend(npc) },
        )
        // AI/death dispatch uses the immutable spawn ID; incoming hits use visible form.
        for (id in
            listOf(
                CerberusAssets.SITTING_NPC_ID,
                CerberusAssets.ACTIVE_NPC_ID,
                CerberusAssets.RESETTING_NPC_ID,
            )) {
            val boss = requireNotNull(ServerCacheManager.getNpc(id))
            onNpcQueue(boss, "queue.death") {
                if (encounters.beginBossDeath(npc)) {
                    death.deathWithDrops(
                        this,
                        deathAnimationId = CerberusAssets.DEATH_ANIMATION,
                        respawnDelay = CerberusRules.RESPAWN_TICKS,
                    )
                    encounters.finishBossDeath(npc)
                } else {
                    death.deathNoDrops(this, CerberusAssets.DEATH_ANIMATION)
                }
            }
        }
    }
}
