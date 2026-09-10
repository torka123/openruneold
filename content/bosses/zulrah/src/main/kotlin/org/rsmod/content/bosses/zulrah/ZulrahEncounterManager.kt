package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Surface positioning and completion data; native instances and BossEncounter own lifetimes. */
@Singleton
internal class ZulrahEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val encounters: EncounterRegistry,
    private val effects: ZulrahArenaEffects,
) {
    fun validateAssets() {
        (ZulrahAssets.forms + ZulrahAssets.snakelings + ZulrahAssets.priestesses).forEach(
            ::requireNpc
        )
        ZulrahAssets.sequences.forEach { internal ->
            requireNotNull(ServerCacheManager.getAnim(internal.asRSCM(RSCMType.SEQ))) {
                "Missing Zulrah sequence: $internal"
            }
        }
        ZulrahAssets.spotanims.forEach { internal -> internal.asRSCM(RSCMType.SPOTANIM) }
        ZulrahAssets.projectileControllers.forEach { internal ->
            requireNotNull(ServerCacheManager.getProjectile(internal.asRSCM(RSCMType.PROJANIM))) {
                "Missing Zulrah projectile controller: $internal"
            }
        }
        ZulrahAssets.sounds.forEach { internal -> internal.asRSCM(RSCMType.SYNTH) }
        (ZulrahAssets.boats + ZulrahAssets.EXIT + ZulrahAssets.CLOUD_LOC).forEach { internal ->
            requireNotNull(ServerCacheManager.getObject(internal.asRSCM(RSCMType.LOC))) {
                "Missing Zulrah loc: $internal"
            }
        }
    }

    fun onJoin(player: Player, session: InstanceSession) {
        session.state(ZULRAH_ARENA_STATE) {
            val options = session.stateOrNull(ZULRAH_ENTRY_OPTIONS) ?: ZulrahEntryOptions()
            val rotation = options.rotation ?: random.of(ZulrahRotations.all.size) + 1
            val phase = options.phase.coerceIn(1, ZulrahRotations.byNumber(rotation).phases.size)
            val boss = instances.npcsForInstance(session.id).single { it.type.id == 2042 }
            val rng = options.seed?.let(::DefaultGameRandom) ?: random
            val run =
                ZulrahArenaState(
                    player,
                    session,
                    boss,
                    encounters.of(boss, rng),
                    ZulrahSurface(rotation, phase),
                    options.rotation != null,
                    rng,
                    clock.cycle,
                )
            prepareRound(run)
            player.lootDropDuration = ZULRAH_DROP_DURATION
            session.onCleanup {
                effects.clear(run)
                encounters.remove(run.boss)
                player.lootDropDuration = null
                player.combatClearQueue()
                player.clearInteraction()
            }
            if (run.debug)
                player.mes(
                    "Debug encounter: rotation $rotation, phase $phase, seed ${options.seed ?: 0}. Rewards are disabled."
                )
            run
        }
    }

    private fun prepareRound(run: ZulrahArenaState) {
        val boss = run.boss
        boss.baseHitpointsLvl = ZULRAH_MAX_HITPOINTS
        boss.hitpoints = ZULRAH_MAX_HITPOINTS
        boss.baseDefenceLvl = 300
        boss.defenceLvl = 300
        boss.respawns = !run.debug
        boss.suppressRewards = run.debug
        boss.movementLocked = true
        boss.apRangeOverride = 32
        boss.apRequiresLineOfSight = true
        boss.assignSpawnOwner(run.player, clock.cycle)
        boss.showAllOps()
        boss.transmog(requireNpc(internalFor(run.surface.data.form)), Int.MAX_VALUE)
        boss.teleport(
            collision,
            coord(run, ShrineTile(run.surface.data.position.x, run.surface.data.position.z)),
        )
        face(run)
        boss.anim(if (run.surface.phase == 1) ZulrahAssets.SPAWN else ZulrahAssets.EMERGE)
        run.encounter.transitionTo(
            "${run.surface.key}-${if (run.surface.phase == 1) "spawning" else "emerging"}",
            clock.cycle,
        )
        run.encounter.onCleanup { effects.clear(run) }
        boss.apPlayer2(run.player, interactions)
        run.startCycle = clock.cycle
        run.killTicks = 0
        run.player.combatClearQueue()
        run.player.clearInteraction()
        run.player.mes("Zulrah emerges from the swamp.")
    }

    fun tickAll() {
        for (session in instances.sessionsForKey(ZULRAH_INSTANCE_KEY)) {
            val run = session.stateOrNull(ZULRAH_ARENA_STATE) ?: continue
            if (run.pendingRespawn) {
                run.pendingRespawn = false
                run.surface = ZulrahSurface(random.of(ZulrahRotations.all.size) + 1, 1)
                run.selectionRandom = random
                run.encounter = encounters.of(run.boss)
                prepareRound(run)
            }
            if (run.player.hitpoints > 0 && run.player.isSlotAssigned) effects.tick(run)
        }
    }

    fun onNativeRespawn(npc: Npc) {
        val run = instances.zulrahState(npc) ?: return
        if (run.boss === npc && run.state == ZulrahState.RESPAWNING) run.pendingRespawn = true
    }

    fun present(npc: Npc, target: Player, surface: ZulrahSurface) {
        val run = instances.zulrahState(target)?.takeIf { it.boss === npc } ?: return
        run.surface = surface
        npc.transmog(requireNpc(internalFor(surface.data.form)), Int.MAX_VALUE)
        npc.teleport(
            collision,
            coord(run, ShrineTile(surface.data.position.x, surface.data.position.z)),
        )
        face(run)
        npc.anim(ZulrahAssets.EMERGE)
    }

    fun launchSpecial(npc: Npc, target: Player, action: Any) {
        val run = instances.zulrahState(target)?.takeIf { it.boss === npc } ?: return
        effects.launch(run, action)
    }

    fun canAttack(npc: Npc, target: Player): Boolean =
        instances.zulrahState(target)?.let { run ->
            run.owns(npc) &&
                npc.isSpawnOwnedBy(target) &&
                inArena(run, target.coords) &&
                run.state in setOf(ZulrahState.ACTIVE, ZulrahState.DIVING, ZulrahState.EMERGING)
        } == true

    fun validHit(npc: Npc, target: Player): Boolean =
        instances.zulrahState(target)?.let { run ->
            run.owns(npc) &&
                instances.zulrahState(npc) === run &&
                inArena(run, target.coords) &&
                run.state != ZulrahState.DISPOSED
        } == true

    fun isEncounterNpc(npc: Npc): Boolean = instances.zulrahState(npc)?.owns(npc) == true

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        instances.zulrahState(player)?.let { it.owns(npc) && npc.isSpawnOwnedBy(player) } == true

    fun isActive(player: Player): Boolean =
        instances.zulrahState(player)?.let { it.state != ZulrahState.DISPOSED } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run =
            instances.zulrahState(player) ?: return "You do not have an active Zulrah encounter."
        if (!run.owns(npc)) return "That creature belongs to another encounter."
        return if (npc === run.boss && run.state != ZulrahState.ACTIVE)
            "Zulrah cannot be attacked while beneath the swamp."
        else null
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = instances.zulrahState(player)
        if (run == null || !run.owns(npc) || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        if (npc === run.boss) {
            if (run.state !in setOf(ZulrahState.ACTIVE, ZulrahState.DIVING, ZulrahState.EMERGING)) {
                hit.damage = 0
                return
            }
            hit.damage =
                ZulrahRules.cappedDamage(hit.damage) { range ->
                    range.first + run.selectionRandom.of(range.count())
                }
        }
    }

    fun beginBossDeath(npc: Npc): ZulrahArenaState? {
        val run = instances.zulrahState(npc)?.takeIf { it.boss === npc } ?: return null
        if (run.state !in setOf(ZulrahState.ACTIVE, ZulrahState.EMERGING, ZulrahState.DIVING))
            return null
        run.encounter.transitionTo("dying", clock.cycle)
        // Retire arena tasks without ending the already-fired standard projectile hit scope.
        run.encounter.runCleanupActions()
        run.killTicks = (clock.cycle - run.startCycle).coerceAtLeast(0)
        return run
    }

    fun finishBossDeath(run: ZulrahArenaState) {
        if (instances.zulrahState(run.player) !== run) return
        if (run.state != ZulrahState.DYING) return
        if (!run.debug) {
            val oldPb = run.player.attr[ZULRAH_PERSONAL_BEST_TICKS]
            val personalBest = oldPb == null || run.killTicks < oldPb
            if (personalBest) run.player.attr[ZULRAH_PERSONAL_BEST_TICKS] = run.killTicks
            val suffix = if (personalBest) " <col=ff0000>New personal best!</col>" else ""
            run.player.mes(
                "<col=ff9900>Zulrah kill ${run.player.vars["varp.total_snakeboss_kills"]}: ${ZulrahRules.formatTicks(run.killTicks)}.$suffix</col>"
            )
            run.encounter.transitionTo("respawning", clock.cycle)
            run.player.combatClearQueue()
            run.player.mes("Zulrah will respawn in 10 seconds.")
        } else {
            run.player.mes("Debug Zulrah defeated. No rewards, kill count, or record were awarded.")
            run.encounter.transitionTo("completed", clock.cycle)
        }
    }

    fun finishSnakelingDeath(npc: Npc) {
        val run = instances.zulrahState(npc) ?: return
        run.snakelings.remove(npc)
        run.encounter.disown(npc)
    }

    fun dropTile(npc: Npc): CoordGrid =
        instances.zulrahState(npc)?.let { ZulrahRules.rewardTile(it.player.coords, npc.coords) }
            ?: npc.coords

    fun entryScene(player: Player): ZulrahEntryScene? {
        val run = instances.zulrahState(player) ?: return null
        return ZulrahEntryScene(
            coord(run, ShrineTile(2268, 3058)),
            coord(run, ShrineTile(2266, 3073)),
            1_800,
            0,
            ZULRAH_ENTRY_CUTSCENE_TICKS,
        )
    }

    fun debugStatus(player: Player): String {
        val run = instances.zulrahState(player) ?: return "Zulrah: no active encounter."
        return "Zulrah rotation=${run.surface.rotation} phase=${run.surface.phase} " +
            "form=${run.surface.data.form} state=${run.state} action=${run.encounter.lastAbilityName} " +
            "tick=${clock.cycle - run.startCycle} hp=${run.boss.hitpoints} clouds=${run.clouds.size} snakelings=${run.snakelings.size}"
    }

    private fun coord(run: ZulrahArenaState, tile: ShrineTile) =
        instances.zulrahCoord(run.session, tile)

    private fun face(run: ZulrahArenaState) {
        val position = run.surface.data.position
        run.boss.faceSquare(coord(run, ShrineTile(position.faceX, position.faceZ)))
    }

    private fun inArena(run: ZulrahArenaState, tile: CoordGrid): Boolean {
        val sw = coord(run, ShrineTile(2256, 3056))
        val ne = coord(run, ShrineTile(2279, 3079))
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    private fun internalFor(form: ZulrahForm) =
        when (form) {
            ZulrahForm.SERPENTINE -> ZulrahAssets.GREEN
            ZulrahForm.MAGMA -> ZulrahAssets.RED
            ZulrahForm.TANZANITE -> ZulrahAssets.BLUE
        }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC)))
}
