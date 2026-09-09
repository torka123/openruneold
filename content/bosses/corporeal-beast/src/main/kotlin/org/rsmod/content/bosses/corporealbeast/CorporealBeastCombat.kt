package org.rsmod.content.bosses.corporealbeast

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CorporealBeastCombat
@Inject
constructor(
    private val deps: BossDeps,
    private val effects: CorporealBeastRoomEffects,
    private val parameters: CorporealBeastAttackParameters,
    private val death: NpcDeath,
) : PluginScript() {
    override fun ScriptContext.startup() {
        CorporealBeastValidation.validateAssets()
        val boss = requireNpc(CorporealBeastAssets.BOSS_NPC_ID)
        val core = requireNpc(CorporealBeastAssets.DARK_CORE_NPC_ID)

        deps.extensionRegistry.registerAttackParameters("corp.normal") { npc, target, data ->
            parameters
                .resolve(npc, target, data as Boolean)
                .copy(hitValidity = corpHitValidity(deps, deps.encounterRegistry.of(npc), target))
        }
        deps.extensionRegistry.register("corp.area") { _, npc, target, _ ->
            effects.state(npc)
            effects.area.launch(npc, target, deps.encounterRegistry.of(npc))
        }
        BossCombat.register(
            this,
            corporealBeastSpec(),
            deps,
            "corporeal-beast",
            canTarget = { npc, target ->
                npc.hitpoints > 0 &&
                    target.isSlotAssigned &&
                    target.hitpoints > 0 &&
                    CorporealBeastRules.roomMatches(npc.spawnCoords, target.coords)
            },
            onImpactHit = { effects.onBossImpact(npc, hit.damage) },
            onAbilityCommitted = { npc, _, _ -> effects.afterAttack(npc) },
        )
        onNpcQueue(boss, "queue.death") {
            deps.encounterRegistry.remove(npc)
            death.deathWithDrops(this)
        }

        onAiApPlayer2(core) {
            npc.clearInteraction()
            actionDelay = deps.mapClock.cycle + 2
        }
        onAiOpPlayer2(core) {
            npc.clearInteraction()
            actionDelay = deps.mapClock.cycle + 2
        }
        onNpcQueue(core, "queue.death") {
            effects.coreDied(npc)
            death.deathNoDrops(this)
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 NPC definition: $id" }
}
