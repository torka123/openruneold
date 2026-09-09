package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Hydra-only observation/door and form presentation. InstanceManager owns membership/NPC lifetime;
 * BossEncounter owns ordinary combat, phase identity and rotation. This coordinator keeps neither a
 * player index nor an attack/respawn clock.
 */
@Singleton
internal class AlchemicalHydraEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val random: GameRandom,
    private val deps: BossDeps,
    private val hazards: AlchemicalHydraHazards,
    private val transforms: AlchemicalHydraTransforms,
) {

    private fun runFor(player: Player): HydraArenaState? =
        instances.sessionForPlayer(player)?.stateOrNull(HYDRA_ARENA_STATE)

    private fun runFor(npc: Npc): HydraArenaState? =
        instances.instanceForNpc(npc)?.let(instances::sessionForId)?.stateOrNull(HYDRA_ARENA_STATE)

    fun onJoin(player: Player, session: InstanceSession) {
        val boss =
            instances.npcsForInstance(session.id).single {
                it.type.id == AlchemicalHydraAssets.GREEN
            }
        val run =
            session.state(HYDRA_ARENA_STATE) {
                HydraArenaState(
                    player,
                    session,
                    boss,
                    if (random.of(2) == 0) HydraAttackStyle.MAGIC else HydraAttackStyle.RANGED,
                    deps.encounterRegistry.of(boss),
                )
            }
        run.encounter.transitionTo(hydraPhaseName(HydraPhase.GREEN, run.initialStyle), clock.cycle)
        prepareObservation(run)
        session.onCleanup { release(run) }
    }

    fun onLeave(player: Player, session: InstanceSession) {
        val run = session.stateOrNull(HYDRA_ARENA_STATE) ?: return
        if (run.player === player) release(run)
    }

    private fun release(run: HydraArenaState) {
        run.state = HydraState.INVALIDATED
        hazards.clear(run)
        run.boss.clearInteraction()
        deps.encounterRegistry.remove(run.boss)
    }

    fun onNativeRespawn(npc: Npc) {
        val run = runFor(npc) ?: return
        if (run.state == HydraState.RESPAWNING) run.pendingRespawn = true
    }

    fun validateAssets() {
        AlchemicalHydraAssets.allForms.forEach { id ->
            requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Hydra NPC $id" }
        }
        require(
            ServerCacheManager.getNpc(AlchemicalHydraAssets.GREEN)?.hitpoints == HYDRA_MAX_HITPOINTS
        ) {
            "Revision-240 Hydra must have $HYDRA_MAX_HITPOINTS hitpoints."
        }
        AlchemicalHydraAssets.animations.forEach { id ->
            requireNotNull(ServerCacheManager.getAnim(id)) {
                "Missing revision-240 Hydra animation $id"
            }
        }
        (AlchemicalHydraAssets.doors + HydraVent.entries.map(HydraVent::locId)).forEach { id ->
            requireNotNull(ServerCacheManager.getObject(id)) {
                "Missing revision-240 Hydra location $id"
            }
        }
    }

    fun crossDoor(access: ProtectedAccess): Boolean {
        val player = access.player
        val run = runFor(player) ?: return false
        return when (run.state) {
            HydraState.OBSERVATION -> {
                val entry = instances.hydraCoord(run.session, HYDRA_CHAMBER_ENTRY)
                access.telejump(entry)
                if (player.coords == entry) activate(run)
                true
            }
            HydraState.RESPAWNING -> {
                player.mes("The chamber is open while the Alchemical Hydra reforms.")
                true
            }
            HydraState.ACTIVE,
            HydraState.TRANSITION -> {
                player.mes("The alchemical doors are locked during the fight.")
                true
            }
            HydraState.DYING,
            HydraState.INVALIDATED -> false
        }
    }

    fun canAttack(npc: Npc, target: Player): Boolean =
        runFor(target)?.let {
            it.boss === npc && it.state == HydraState.ACTIVE && npc.isSpawnOwnedBy(target)
        } == true

    fun launchSpecial(npc: Npc, target: Player, special: HydraSpecial) {
        val run = runFor(target) ?: return
        if (run.boss !== npc || run.state != HydraState.ACTIVE) return
        hazards.launch(run, special)
    }

    fun tickAll() {
        instances.sessionsForKey(HYDRA_INSTANCE_KEY).forEach { session ->
            val run = session.stateOrNull(HYDRA_ARENA_STATE) ?: return@forEach
            if (run.state == HydraState.INVALIDATED) return@forEach
            // Native respawn listeners finish before this LateCycle presentation reset.
            if (run.pendingRespawn) {
                finishRespawn(run)
                return@forEach
            }
            hazards.process(run)
            when (run.state) {
                HydraState.OBSERVATION -> {
                    if (instances.inHydraChamber(run)) activate(run)
                }
                HydraState.ACTIVE -> {
                    hazards.processVent(run)
                    keepAggressive(run)
                }
                HydraState.TRANSITION -> {
                    if (transforms.completeIfReady(run)) keepAggressive(run)
                }
                HydraState.RESPAWNING -> Unit
                HydraState.DYING,
                HydraState.INVALIDATED -> Unit
            }
        }
    }

    private fun activate(run: HydraArenaState) {
        if (run.state != HydraState.OBSERVATION) return
        run.state = HydraState.ACTIVE
        run.nextVentCycle = clock.cycle + AlchemicalHydraRules.VENT_INITIAL_DELAY
        run.boss.movementLocked = false
        run.boss.showAllOps()
        run.boss.actionDelay = clock.cycle + 1
        keepAggressive(run)
        run.player.mes("The alchemical doors lock behind you. The Hydra attacks!")
    }

    fun beginBossDeath(npc: Npc): CoordGrid? {
        val run = runFor(npc) ?: return null
        if (run.phase != HydraPhase.FINAL || run.state != HydraState.ACTIVE) return null
        run.state = HydraState.DYING
        deps.encounterRegistry.remove(npc)
        hazards.clear(run)
        run.player.combatClearQueue()
        npc.anim(AlchemicalHydraAssets.DEATH_ANIM)
        return run.player.coords
    }

    fun finishBossDeath(npc: Npc) {
        val run = runFor(npc) ?: return
        if (run.state != HydraState.DYING) return
        // NpcDeath has already published the native kill hook; never increment a second time.
        val kills = run.player.vars["varp.total_hydraboss_kills"]
        run.state = HydraState.RESPAWNING
        run.player.mes("Alchemical Hydra kill $kills. Its drops land at your feet.")
        run.player.mes("The Alchemical Hydra will reform in about 26 seconds.")
    }

    private fun finishRespawn(run: HydraArenaState) {
        run.pendingRespawn = false
        hazards.clear(run)
        run.encounter = deps.encounterRegistry.of(run.boss)
        run.state = HydraState.OBSERVATION
        run.initialStyle =
            if (random.of(2) == 0) HydraAttackStyle.MAGIC else HydraAttackStyle.RANGED
        run.encounter.transitionTo(hydraPhaseName(HydraPhase.GREEN, run.initialStyle), clock.cycle)
        run.boss.movementLocked = true
        run.lastBasicStyle = run.initialStyle
        run.weakened = false
        run.wrongVentStacks = 0
        run.player.combatClearQueue()
        prepareObservation(run)
        run.player.mes("The Alchemical Hydra has reformed. Cross the doors to fight again.")
    }

    fun isEncounterNpc(npc: Npc): Boolean = runFor(npc)?.boss === npc

    fun isParticipant(player: Player, npc: Npc): Boolean =
        runFor(player)?.let { it.boss === npc && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runFor(player) ?: return "You do not have an active Hydra encounter."
        if (run.boss !== npc) return "That Hydra belongs to another encounter."
        return if (run.state == HydraState.ACTIVE) null
        else
            "The Alchemical Hydra cannot be attacked from the observation room or during a transition."
    }

    fun status(player: Player): String {
        val run = runFor(player) ?: return "Hydra: no active encounter."
        return "Hydra phase=${run.phase} state=${run.state} hp=${run.boss.hitpoints}/$HYDRA_MAX_HITPOINTS " +
            "style=${run.style} recentAbilities=${run.encounter.recentAttackTraces().map { it.ability }} " +
            "weakened=${run.weakened} wrongVent=${run.wrongVentStacks}"
    }

    private fun prepareObservation(run: HydraArenaState) {
        val npc = run.boss
        npc.mode = null
        npc.movementLocked = true
        npc.apRangeOverride = 5
        npc.maxOpRangeOverride = 64
        npc.apRequiresLineOfSight = true
        npc.baseHitpointsLvl = HYDRA_MAX_HITPOINTS
        npc.hitpoints = HYDRA_MAX_HITPOINTS
        npc.hideAllOps()
        npc.assignSpawnOwner(run.player, clock.cycle)
        run.encounter.onCleanup { hazards.clear(run) }
    }

    private fun keepAggressive(run: HydraArenaState) {
        if (
            run.state == HydraState.ACTIVE &&
                run.boss.isSlotAssigned &&
                run.boss.hitpoints > 0 &&
                run.boss.mode == null
        ) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }
}
