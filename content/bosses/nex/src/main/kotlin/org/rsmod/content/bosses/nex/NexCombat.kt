package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class NexCombat
@Inject
constructor(
    private val encounters: NexEncounterManager,
    private val death: NpcDeath,
    private val deps: BossDeps,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.registerAttackParameters(NEX_ATTACK_PARAMETERS) { npc, target, data
            ->
            encounters.normalParameters(npc, target, data as NexNormalAttack)
        }
        deps.extensionRegistry.register(NEX_SPECIAL) { _, npc, target, _ ->
            encounters.executeSpecial(npc, target)
        }
        deps.extensionRegistry.register(NEX_MAGIC_SPLASH) { _, npc, target, _ ->
            encounters.magicSplash(npc, target)
        }
        deps.extensionRegistry.register(NEX_OVERHEAD) { _, npc, _, data ->
            encounters.setZarosOverhead(npc, data as NexOverhead)
        }
        BossCombat.register(
            this,
            nexBossSpec(),
            deps,
            ownerSlug = "nex",
            canTarget = encounters::canAttackNex,
            onModifyHit = { encounters.prepareNpcHit(npc, hit) },
            onResolveHit = encounters::resolveNpcHit,
            onImpactHit = encounters::impactNpcHit,
        )
        // M11 captured travel visuals; the existing native attack still owns launch and hit delay.
        val minionProjectiles =
            mapOf(
                11283 to minionProjectile(390, "projanim.magic_spell"),
                11286 to minionProjectile(368, "projanim.magic_spell_low"),
            )
        deps.extensionRegistry.registerAttackParameters(NEX_MINION_TARGET_PARAMETERS) {
            npc,
            target,
            _ ->
            NpcAttackOverride(
                projectile = minionProjectiles[npc.id],
                hitValidity = encounters.minionHitValidity(npc, target),
            )
        }
        for (reaver in listOf(false, true)) {
            BossCombat.register(
                this,
                nexMinionSpec(reaver),
                deps,
                ownerSlug = if (reaver) "nex-reaver" else "nex-bodyguards",
                canTarget = encounters::canAttackMinion,
                onModifyHit = { encounters.prepareNpcHit(npc, hit) },
                onResolveHit = encounters::resolveNpcHit,
                onImpactHit = encounters::impactNpcHit,
            )
        }
        NPC_IDS.forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Nex NPC $id" }
            onNpcQueue(type, "queue.death") {
                val kind = encounters.handleDeath(npc)
                if (kind == NexEncounterManager.DeathKind.BODYGUARD) {
                    death.deathWithDrops(this, respawnDelay = Int.MAX_VALUE)
                } else {
                    // Main Nex stays registered for Wrath attribution, but must not auto-respawn
                    // independently of room reset. Reavers retain their existing no-drop route.
                    death.deathNoDrops(this, respawnDelay = Int.MAX_VALUE)
                }
                encounters.finishDeath(npc, kind)
            }
        }
    }

    private fun minionProjectile(spotanim: Int, controller: String): NpcProjectile =
        NpcProjectile(
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, spotanim)),
            requireNotNull(ServerCacheManager.getProjectile(controller.asRSCM(RSCMType.PROJANIM))),
        )

    private companion object {
        val NPC_IDS = (11278..11286).toList() + 11294
    }
}
