package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.ScriptContext

internal class AlchemicalHydraBoss
@Inject
constructor(
    deps: BossDeps,
    private val encounters: HydraEncounterManager,
    private val death: NpcDeath,
) : BossPluginScript(deps) {

    override val spec: BossSpec = alchemicalHydraSpec(encounters)

    override fun ScriptContext.startup() {
        encounters.registerExtensions()
        BossCombat.register(
            this,
            alchemicalHydraSpec(encounters),
            deps,
            onModifyHit = { encounters.modifyIncoming(npc, hit) },
            onHit = { encounters.onHit(npc, hit.damage) },
        )

        ACTIVE_FORMS.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getNpc(id))
            onNpcQueue(type, "queue.death") {
                val dropCoords = encounters.beginDeath(npc)
                if (dropCoords == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathWithDrops(this, dropCoords, respawnDelay = HYDRA_RESPAWN_TICKS)
                    encounters.afterDeath(npc)
                }
            }
        }

        TRANSITION_FORMS.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getNpc(id))
            onNpcQueue(type, "queue.death") { death.deathNoDrops(this) }
        }

        onEvent<GameLifecycle.LateCycle> { encounters.onLateCycle() }
        onEvent<NpcStateEvents.Respawn> { encounters.onRespawn(npc) }
        onEvent<NpcStateEvents.Delete> { encounters.onDelete(npc) }
    }

    private companion object {
        private const val HYDRA_RESPAWN_TICKS = 43
        private val ACTIVE_FORMS = listOf(HydraAssets.GREEN, HydraAssets.BLUE, HydraAssets.RED, HydraAssets.FINAL)
        private val TRANSITION_FORMS = listOf(HydraAssets.GREEN_TO_BLUE, HydraAssets.BLUE_TO_RED, HydraAssets.RED_TO_FINAL)
    }
}
