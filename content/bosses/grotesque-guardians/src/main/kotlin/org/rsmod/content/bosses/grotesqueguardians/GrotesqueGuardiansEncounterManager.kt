package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
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
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/** Paired guardian presentation only; native sessions, encounters and actors own the fight. */
@Singleton
internal class GrotesqueGuardiansEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val deps: BossDeps,
    private val worldQueues: WorldQueueList,
    private val effects: GuardiansArenaEffects,
) {
    fun onJoin(player: Player, session: InstanceSession) {
        session.state(GUARDIANS_ARENA) {
            val actors = instances.npcsForInstance(session.id)
            val dawn = actors.single { it.type.id == GrotesqueAssets.DAWN_PHASE_ONE }
            val dusk = actors.single { it.type.id == GrotesqueAssets.DUSK_PHASE_ONE }
            prepareActor(player, dawn, GuardianRole.DAWN)
            prepareActor(player, dusk, GuardianRole.DUSK)
            val state =
                GuardiansArenaState(
                    player,
                    session,
                    dawn,
                    dusk,
                    deps.encounterRegistry.of(dawn),
                    deps.encounterRegistry.of(dusk),
                    clock,
                )
            session.onCleanup {
                effects.clear(state)
                player.combatClearQueue()
                player.clearInteraction()
            }
            state
        }
    }

    private fun prepareActor(player: Player, npc: Npc, role: GuardianRole) {
        npc.baseHitpointsLvl = GrotesqueGuardiansRules.HITPOINTS
        npc.hitpoints = GrotesqueGuardiansRules.HITPOINTS
        npc.movementLocked = false
        npc.moveRestrict = MoveRestrict.PassThru
        npc.apRangeOverride = if (role == GuardianRole.DAWN) 10 else 1
        npc.apRequiresLineOfSight = true
        npc.maxOpRangeOverride = 64
        npc.assignSpawnOwner(player, clock.cycle)
    }

    fun launchSpecial(npc: Npc, target: Player, handler: String) {
        if (!canAttack(npc, target)) return
        effects.launch(requireNotNull(instances.guardiansState(target)), handler)
    }

    fun absorbSphere(player: Player, coords: CoordGrid): Boolean =
        effects.absorbSphere(player, coords)

    fun tickAll() {
        instances.sessionsForKey(GROTESQUE_INSTANCE_KEY).toList().forEach { session ->
            val run = session.stateOrNull(GUARDIANS_ARENA) ?: return@forEach
            if (
                !run.player.isSlotAssigned ||
                    run.player.hitpoints <= 0 ||
                    instances.sessionForPlayer(run.player) !== session
            )
                return@forEach
            if (run.phase == GrotesquePhase.LIGHTNING_TRANSITION) {
                effects.tickLightning(run)
                if (clock.cycle >= run.duskEncounter.phaseEnteredTick + 12) finishLightning(run)
            }
            if (
                run.phase == GrotesquePhase.DAWN_DYING &&
                    clock.cycle >= run.duskEncounter.phaseEnteredTick + 5
            )
                beginPhaseFour(run)
            if (run.phase == GrotesquePhase.DAWN_THREE) effects.tickSpheres(run)
            keepAggressive(run)
        }
    }

    fun finishDuskDeath(npc: Npc) {
        val run = instances.guardiansState(npc)?.takeIf { it.dusk === npc } ?: return
        if (run.phase != GrotesquePhase.DYING) return
        run.phase = GrotesquePhase.RESPAWNING
        // Dawn remains visible. Its native encounter owns the pair's delayed replacement.
        run.dawnEncounter.scheduleAt(
            worldQueues,
            clock,
            clock.cycle + GrotesqueGuardiansRules.RESPAWN_TICKS,
        ) {
            if (
                instances.guardiansState(run.player) !== run ||
                    run.phase != GrotesquePhase.RESPAWNING
            )
                return@scheduleAt
            if (!instances.resetNpcs(run.session, clock.cycle)) return@scheduleAt
            run.session.resetState()
            onJoin(run.player, run.session)
            run.player.mes("The rooftop falls quiet. Ring the bell when you are ready.")
        }
    }

    fun isEncounterNpc(npc: Npc): Boolean =
        instances.guardiansState(npc)?.let { it.dawn === npc || it.dusk === npc } == true

    fun isParticipant(player: Player, npc: Npc): Boolean =
        instances.guardiansState(player)?.let {
            (it.dawn === npc || it.dusk === npc) && npc.isSpawnOwnedBy(player)
        } == true

    fun validateAssets() {
        (GrotesqueAssets.DAWN_FORMS + GrotesqueAssets.DUSK_FORMS).forEach { id ->
            requireNotNull(ServerCacheManager.getNpc(id)) {
                "Missing revision-240 Guardian NPC $id"
            }
        }
        (intArrayOf(GrotesqueAssets.ROOF_ENTRANCE, GrotesqueAssets.CLOISTER_BELL) +
                GrotesqueAssets.ENERGY_SPHERES)
            .forEach { id ->
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 Guardian location $id"
                }
            }
        GrotesqueAssets.ANIMATIONS.forEach { id ->
            requireNotNull(ServerCacheManager.getAnim(id)) { "Missing Guardian animation $id" }
        }
    }

    fun ringBell(player: Player): Boolean {
        val run = instances.guardiansState(player) ?: return false
        if (run.phase != GrotesquePhase.DORMANT) {
            player.mes("Concentrate on your fight!")
            return false
        }
        player.anim(GrotesqueAssets.PLAYER_RING_ANIMATION)
        run.phase = GrotesquePhase.DAWN_ONE
        run.dawn.anim(GrotesqueAssets.DAWN_DESCEND_ANIMATION)
        run.dusk.anim(GrotesqueAssets.DUSK_TRANSFORM_ANIMATION)
        run.dawn.actionDelay = clock.cycle + 2
        run.dusk.actionDelay = clock.cycle + 2
        run.dawn.apPlayer2(player, interactions)
        run.dusk.apPlayer2(player, interactions)
        player.mes("The Grotesque Guardians awaken.")
        return true
    }

    fun canAttack(npc: Npc, target: Player): Boolean {
        val run = instances.guardiansState(target) ?: return false
        if (!npc.isSpawnOwnedBy(target)) return false
        return when (npc) {
            run.dawn -> run.phase in setOf(GrotesquePhase.DAWN_ONE, GrotesquePhase.DAWN_THREE)
            run.dusk ->
                run.phase in
                    setOf(
                        GrotesquePhase.DAWN_ONE,
                        GrotesquePhase.DUSK_TWO,
                        GrotesquePhase.DAWN_THREE,
                        GrotesquePhase.DUSK_FOUR,
                    )
            else -> false
        }
    }

    fun acceptedHit(npc: Npc, damage: Int) {
        if (damage <= 0) return
        val run = instances.guardiansState(npc) ?: return
        when (run.phase) {
            GrotesquePhase.DAWN_ONE ->
                if (npc === run.dawn && npc.hitpoints <= GrotesqueGuardiansRules.PHASE_THRESHOLD)
                    beginPhaseTwo(run)
            GrotesquePhase.DUSK_TWO ->
                if (npc === run.dusk && npc.hitpoints <= GrotesqueGuardiansRules.PHASE_THRESHOLD)
                    beginLightning(run)
            GrotesquePhase.DAWN_THREE ->
                if (npc === run.dawn && npc.hitpoints <= GrotesqueGuardiansRules.FINISH_THRESHOLD)
                    run.player.mes("Dawn is weakened. Finish her with a rock hammer.")
            else -> Unit
        }
    }

    private fun beginPhaseTwo(run: GuardiansArenaState) {
        if (run.phase != GrotesquePhase.DAWN_ONE) return
        run.phase = GrotesquePhase.DAWN_ASCENDING
        run.dawn.clearInteraction()
        run.dawn.anim(GrotesqueAssets.DAWN_ASCEND_ANIMATION)
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_DEFENSIVE), Int.MAX_VALUE)
        run.dawn.movementLocked = true
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_PHASE_TWO), Int.MAX_VALUE)
        run.dusk.movementLocked = false
        run.phase = GrotesquePhase.DUSK_TWO
        run.nextDebrisCycle = clock.cycle + 8
        run.dusk.apPlayer2(run.player, interactions)
    }

    private fun beginLightning(run: GuardiansArenaState) {
        if (run.phase != GrotesquePhase.DUSK_TWO) return
        run.phase = GrotesquePhase.LIGHTNING_TRANSITION
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_PHASE_THREE), Int.MAX_VALUE)
        run.dawn.anim(GrotesqueAssets.DAWN_DESCEND_ANIMATION)
        run.dawn.movementLocked = true
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_LIGHTNING), Int.MAX_VALUE)
        run.dusk.anim(GrotesqueAssets.DUSK_AOE_ANIMATION)
        run.dusk.movementLocked = true
        val warning = clock.cycle
        val tiles = linkedSetOf(run.player.coords)
        while (tiles.size < 18) tiles += effects.randomArenaTile(run)
        run.lightning += tiles.map { Lightning(it, warning + 2, warning + 10) }
        run.lightning.forEach { effects.showLightning(run, it.tile) }
    }

    private fun finishLightning(run: GuardiansArenaState) {
        run.lightning.clear()
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_PHASE_THREE), Int.MAX_VALUE)
        run.dawn.movementLocked = false
        run.dawn.actionDelay = clock.cycle + 1
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_DEFENSIVE), Int.MAX_VALUE)
        run.dusk.movementLocked = false
        run.dusk.actionDelay = clock.cycle + 1
        run.phase = GrotesquePhase.DAWN_THREE
        effects.spawnSpheres(run)
        run.dawn.apPlayer2(run.player, interactions)
        run.dusk.apPlayer2(run.player, interactions)
    }

    fun useHammer(player: Player, npc: Npc, item: String): Boolean {
        val run = instances.guardiansState(player) ?: return false
        if (npc !== run.dawn || run.phase != GrotesquePhase.DAWN_THREE) return false
        if (item !in GrotesqueGuardiansRules.ROCK_HAMMERS) {
            player.mes("You need a rock hammer to finish Dawn.")
            return false
        }
        if (run.dawn.hitpoints > GrotesqueGuardiansRules.FINISH_THRESHOLD) {
            player.mes("Dawn is not weak enough to finish.")
            return false
        }
        run.phase = GrotesquePhase.DAWN_DYING
        effects.clearSpheres(run)
        run.dawn.clearInteraction()
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_DEATH), Int.MAX_VALUE)
        run.dawn.anim(GrotesqueAssets.DAWN_DEATH_ANIMATION)
        return true
    }

    private fun beginPhaseFour(run: GuardiansArenaState) {
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_PHASE_FOUR), Int.MAX_VALUE)
        run.dusk.movementLocked = false
        run.dusk.actionDelay = clock.cycle + 2
        run.phase = GrotesquePhase.DUSK_FOUR
        run.duskEncounter.forceNextAbility("prison")
        run.dusk.apPlayer2(run.player, interactions)
    }

    private fun keepAggressive(run: GuardiansArenaState) {
        if (run.phase in setOf(GrotesquePhase.DAWN_ONE, GrotesquePhase.DAWN_THREE)) {
            if (run.dawn.mode == null && run.dawn.hitpoints > 0)
                run.dawn.apPlayer2(run.player, interactions)
        }
        if (
            run.phase in
                setOf(
                    GrotesquePhase.DAWN_ONE,
                    GrotesquePhase.DUSK_TWO,
                    GrotesquePhase.DAWN_THREE,
                    GrotesquePhase.DUSK_FOUR,
                )
        ) {
            if (run.dusk.mode == null && run.dusk.hitpoints > 0)
                run.dusk.apPlayer2(run.player, interactions)
        }
    }

    fun beginDuskDeath(npc: Npc): CoordGrid? {
        val run = instances.guardiansState(npc)?.takeIf { it.dusk === npc } ?: return null
        if (run.phase != GrotesquePhase.DUSK_FOUR) return null
        run.phase = GrotesquePhase.DYING
        effects.clearSpheres(run)
        run.dawn.clearInteraction()
        run.dusk.clearInteraction()
        run.player.combatClearQueue()
        return run.player.coords
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run =
            instances.guardiansState(player)
                ?: return "You do not have an active Guardians encounter."
        val role =
            if (npc === run.dawn) GuardianRole.DAWN
            else if (npc === run.dusk) GuardianRole.DUSK
            else return "That is not part of your encounter."
        return if (GrotesqueGuardiansRules.canDamage(role, run.phase)) null
        else if (run.phase == GrotesquePhase.DORMANT) "Ring the Cloister Bell first."
        else "That Guardian cannot be harmed during this phase."
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Guardian NPC $id" }
}
