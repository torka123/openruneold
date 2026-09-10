package org.rsmod.content.bosses.royaltitans

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.sql.SQLException
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.content.skills.magic.spell.attacks.RoyalTitansWallSpells
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitScope
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class RoyalTitansEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val objects: ObjRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val interactions: AiPlayerInteractions,
    private val players: PlayerList,
    private val drops: DropTableRegistry,
    private val bossHpBar: BossHpBarScript,
    private val npcHitModifier: StandardNpcHitModifier,
    private val statistics: RoyalTitansStatistics,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<InstanceId, Run>()
    private val participantRuns = mutableMapOf<Long, Run>()
    private val npcRuns = mutableMapOf<Npc, Run>()
    private var generation = 1L
    private val pendingDeaths = mutableMapOf<UUID, Pair<Int, UUID>>()
    // Retry ownership must outlive an empty/destroyed instance. This is not a durable outbox.
    private val pendingCompletions = mutableMapOf<UUID, PendingCompletion>()
    private val routes = RouteFactory(collision)
    private val bounds = BoundValidator(collision)

    fun validateAssets() {
        RoyalTitansAssets.npcIds.forEach(::requireNpc)
        RoyalTitansAssets.locIds.forEach(::requireLoc)
        RoyalTitansAssets.sequenceIds.forEach(::anim)
        RoyalTitansAssets.spotanimIds.forEach(::spot)
        requireNotNull(
            ServerCacheManager.getHealthBar(
                RSCM.run { RoyalTitansAssets.AREA_TIMER_HEADBAR.asRSCM(RSCMType.HEADBAR) }
            )
        ) {
            "Missing Royal Titans Area timer headbar"
        }
    }

    fun enter(player: Player): Boolean {
        if (!canTravel(player)) return false
        val brandaType = requireNpc(RoyalTitansAssets.BRANDA_ACTIVE)
        val eldricType = requireNpc(RoyalTitansAssets.ELDRIC_ACTIVE)
        val result =
            instances.create(
                owner = player,
                key = ROYAL_TITANS_INSTANCE_KEY,
                spec = royalTitansSpec(brandaType, eldricType),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Royal Titans arena is unavailable."
            )
            return false
        }

        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        val participant = participant(player, result.session)
        val run =
            Run(
                generation = generation++,
                session = result.session,
                ownerHash = player.accountHash,
                participants = mutableMapOf(player.accountHash to participant),
            )
        runs[result.session.id] = run
        participantRuns[player.accountHash] = run
        spawnTitans(run)
        player.mes("Branda the Fire Queen and Eldric the Ice King turn toward the arena.")
        log.info { "[RoyalTitans] generation=${run.generation} owner=${player.displayName}" }
        return true
    }

    fun join(player: Player, leaderName: String): Boolean {
        if (!canTravel(player)) return false
        val run =
            runs.values.firstOrNull { candidate ->
                online(candidate).any {
                    it.displayName.equals(leaderName.trim(), ignoreCase = true) ||
                        it.username.equals(leaderName.trim(), ignoreCase = true)
                }
            }
        if (run == null) {
            player.mes("That player does not have an available Royal Titans instance.")
            return false
        }
        val brandaHp = run.branda?.hitpoints ?: 0
        val eldricHp = run.eldric?.hitpoints ?: if (run.stage == RoyalTitansStage.INTRO) 600 else 0
        if (!RoyalTitansRules.canJoin(brandaHp, eldricHp, run.stage)) {
            player.mes("That fight has progressed too far for another player to join.")
            return false
        }
        val result = instances.join(player, run.session, clock.cycle, forceAccess = true)
        if (result !is InstanceManager.Result.Joined) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason ?: "You cannot join that fight."
            )
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, run.session, clock.cycle)
        run.participants[player.accountHash] = participant(player, run.session)
        participantRuns[player.accountHash] = run
        online(run).forEach { it.mes("${player.displayName} joins the Royal Titans encounter.") }
        return true
    }

    fun escape(player: Player): Boolean = removeParticipant(player, "left", teleport = true)

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        val run = participantRuns[player.accountHash] ?: return
        if (run.session.id != instanceId) return
        removeParticipant(player, "teleported out", teleport = false)
    }

    fun instanceEnded(instanceId: InstanceId) {
        val run = runs[instanceId] ?: return
        abort(run, "instance ended externally", terminate = false)
    }

    fun removeParticipant(
        player: Player,
        reason: String,
        teleport: Boolean,
        logout: Boolean = false,
    ): Boolean {
        val run = participantRuns.remove(player.accountHash) ?: return false
        run.participants[player.accountHash]?.hitScope?.close()
        player.combatHitScope = null
        if (reason == "player death") {
            pendingDeaths[UUID.randomUUID()] = player.accountId to run.completionId
            flushDeaths()
        }
        run.lastBossTarget.remove(player.accountHash)?.let {
            bossHpBar.onClose(player, it, instant = true)
        }
        run.participants.remove(player.accountHash)
        if (!logout) {
            val exit = instances.leave(player, run.session, clock.cycle)
            if (teleport && player.isSlotAssigned)
                PathingEntityCommon.telejump(player, collision, exit)
            prepare(player)
            if (reason == "left") player.mes("You leave the Royal Titans' arena.")
        }
        if (run.participants.isEmpty()) abort(run, reason)
        return true
    }

    fun tickAll() {
        flushDeaths()
        flushCompletions()
        runs.values.toList().forEach(::tick)
    }

    private fun flushDeaths() {
        for ((id, death) in pendingDeaths.toMap()) {
            try {
                statistics.death(id, death.first, death.second)
                pendingDeaths.remove(id)
            } catch (failure: SQLException) {
                // Native unsafe-death cleanup must still run if persistence is unavailable.
                log.error(failure) { "Royal Titans death persistence pending: $id" }
                return
            }
        }
    }

    private fun tick(run: Run) {
        if (online(run).isEmpty()) return
        expireWalls(run)
        processPendingSummons(run)
        processElementals(run)
        processAreaAttack(run)
        run.pendingRemovals
            .filterValues { clock.cycle >= it }
            .keys
            .toList()
            .forEach {
                run.pendingRemovals.remove(it)
                removeNpc(run, it)
            }

        if (
            run.stage in
                setOf(RoyalTitansStage.INTRO, RoyalTitansStage.RANGED, RoyalTitansStage.MELEE) &&
                RoyalTitansRules.shouldEnterArena(
                    run.branda?.hitpoints ?: ROYAL_TITANS_MAX_HITPOINTS,
                    run.eldric?.hitpoints ?: ROYAL_TITANS_MAX_HITPOINTS,
                )
        ) {
            beginTransition(run)
        }

        when (run.stage) {
            RoyalTitansStage.INTRO -> tickIntro(run)
            RoyalTitansStage.RANGED -> tickRanged(run)
            RoyalTitansStage.MELEE -> tickMelee(run)
            RoyalTitansStage.TRANSITION -> tickTransition(run)
            RoyalTitansStage.ARENA,
            RoyalTitansStage.FINAL_BRANDA,
            RoyalTitansStage.FINAL_ELDRIC -> tickArena(run)
            RoyalTitansStage.COMPLETE -> tickComplete(run)
        }
        run.stageTick++
        if (run.rangedGrace > 0) run.rangedGrace--
        updateRangedVulnerability(run)
        updateTargetHud(run)
    }

    private fun updateTargetHud(run: Run) {
        online(run).forEach { player ->
            val targeted =
                (player.interaction as? InteractionNpc)?.target?.takeIf {
                    (it === run.branda || it === run.eldric) && it.hitpoints > 0
                }
            val previous = run.lastBossTarget[player.accountHash]
            if (previous !== targeted) {
                previous?.let { bossHpBar.onClose(player, it, instant = true) }
                if (targeted == null) run.lastBossTarget.remove(player.accountHash)
                else {
                    run.lastBossTarget[player.accountHash] = targeted
                    bossHpBar.onOpen(player, targeted)
                }
            }
            targeted?.let { bossHpBar.onUpdate(player, it) }
        }
    }

    private fun updateRangedVulnerability(run: Run) {
        // The inherited accuracy adapter remains separately documented; state is per Titan.
        listOfNotNull(run.branda, run.eldric).forEach { npc ->
            val moving =
                run.movement[npc]?.rangedVulnerable == true &&
                    run.stage in
                        setOf(
                            RoyalTitansStage.INTRO,
                            RoyalTitansStage.RANGED,
                            RoyalTitansStage.MELEE,
                        )
            npc.incomingRangedAccuracyMultiplier =
                if (moving || run.areaAttack != null || run.rangedGrace > 0) 6.0 else 1.0
        }
    }

    private fun tickIntro(run: Run) {
        if (run.eldric == null && clock.cycle >= run.eldricSpawnCycle) {
            run.eldric = spawnTitan(run, RoyalTitansAssets.ELDRIC_ACTIVE, ROYAL_TITANS_ELDRIC_SPAWN)
        }
        tickPhaseOneMovement(run)
        if (run.stageTick >= RoyalTitansRules.INTRO_TICKS) setStage(run, RoyalTitansStage.RANGED)
    }

    private fun tickRanged(run: Run) {
        if (
            !run.elementalsUnlocked &&
                ((run.branda?.hitpoints ?: 600) <= 500 || (run.eldric?.hitpoints ?: 600) <= 500)
        ) {
            run.elementalsUnlocked = true
            run.movement.values.forEach { it.summonPending = true }
        }
        tickPhaseOneMovement(run)
    }

    private fun tickMelee(run: Run) {
        tickRanged(run)
    }

    private fun tickPhaseOneMovement(run: Run) {
        listOfNotNull(run.branda, run.eldric).forEach { titan ->
            val movement = run.movement[titan] ?: return@forEach
            if (titan.hitpoints <= 0) return@forEach
            val element = if (titan === run.branda) RoyalElement.FIRE else RoyalElement.ICE
            val outerX = if (element == RoyalElement.FIRE) 24 else 36
            val innerX = if (element == RoyalElement.FIRE) 26 else 34
            if (movement.phase == RoyalTitanMovementPhase.WALKING) {
                val arrived = movement.arrivalCycle
                if (arrived == null) {
                    if (
                        !titan.movementLocked &&
                            !titan.isBound &&
                            titan.coords == titan.previousCoords
                    ) {
                        movement.destination?.let {
                            walkTitan(run, titan, movement, it, movement.afterArrival)
                        }
                    }
                    return@forEach
                }
                if (clock.cycle <= arrived) return@forEach
                titan.faceSquare(
                    titan.coords.translate(if (element == RoyalElement.FIRE) 6 else -6, 0)
                )
                movement.phase = RoyalTitanMovementPhase.FACE_INWARD
                movement.readyCycle = arrived + 2
            }
            if (clock.cycle < movement.readyCycle) return@forEach
            if (movement.phase == RoyalTitanMovementPhase.FACE_INWARD)
                movement.phase = movement.afterArrival
            when (movement.phase) {
                RoyalTitanMovementPhase.SPAWN,
                RoyalTitanMovementPhase.TURN_OUTWARD -> {
                    titan.clearInteraction()
                    titan.mode = NpcMode.None
                    titan.faceSquare(
                        titan.coords.translate(if (element == RoyalElement.FIRE) -6 else 6, 0)
                    )
                    movement.rangedVulnerable = true
                    movement.phase = RoyalTitanMovementPhase.RETREAT
                    movement.readyCycle = clock.cycle + 1
                }
                RoyalTitanMovementPhase.RETREAT ->
                    walkTitan(
                        run,
                        titan,
                        movement,
                        local(run, outerX, localZ(run, titan.coords)),
                        RoyalTitanMovementPhase.FIRST_EDGE_ACTION,
                    )
                RoyalTitanMovementPhase.FIRST_EDGE_ACTION -> {
                    if (!consumeReadySummon(run, titan, movement, element))
                        castWall(run, titan, element)
                    movement.phase = RoyalTitanMovementPhase.PATROL
                    movement.readyCycle = clock.cycle + 8
                }
                RoyalTitanMovementPhase.PATROL -> {
                    val row =
                        RoyalTitansUnverifiedMovementSelection.patrolRow(
                            localZ(run, titan.coords)
                        ) {
                            random.of(it)
                        }
                    walkTitan(
                        run,
                        titan,
                        movement,
                        local(run, outerX, row),
                        RoyalTitanMovementPhase.SECOND_EDGE_ACTION,
                    )
                }
                RoyalTitanMovementPhase.SECOND_EDGE_ACTION -> {
                    if (!consumeReadySummon(run, titan, movement, element))
                        castWall(run, titan, element)
                    movement.phase = RoyalTitanMovementPhase.RETURN
                    movement.readyCycle = clock.cycle + 8
                }
                RoyalTitanMovementPhase.RETURN -> {
                    val target = targetFor(run, titan) ?: return@forEach
                    val row =
                        RoyalTitansUnverifiedMovementSelection.returnRow(
                            localZ(run, target.coords)
                        ) {
                            random.of(it)
                        }
                    // Snapshot at the turn: 2283 B1347/E1366 keep their route after the player
                    // moves.
                    movement.rangedVulnerable = false
                    movement.closeActions = 0
                    walkTitan(
                        run,
                        titan,
                        movement,
                        local(run, innerX, row),
                        RoyalTitanMovementPhase.CLOSE_ACTION,
                    )
                }
                RoyalTitanMovementPhase.CLOSE_ACTION -> {
                    if (!consumeReadySummon(run, titan, movement, element))
                        stomp(run, titan, element, false)
                    movement.closeActions++
                    movement.phase =
                        if (
                            movement.closeActions >=
                                RoyalTitansUnverifiedMovementSelection.CLOSE_ACTIONS
                        )
                            RoyalTitanMovementPhase.TURN_OUTWARD
                        else RoyalTitanMovementPhase.CLOSE_ACTION
                    movement.readyCycle = clock.cycle + 7
                }
                else -> Unit
            }
        }
    }

    private fun consumeReadySummon(
        run: Run,
        titan: Npc,
        movement: RoyalTitanMovement,
        element: RoyalElement,
    ): Boolean {
        if (!movement.summonPending) return false
        movement.summonPending = false
        scheduleSummon(run, titan, element)
        return true
    }

    private fun walkTitan(
        run: Run,
        titan: Npc,
        movement: RoyalTitanMovement,
        destination: CoordGrid,
        afterArrival: RoyalTitanMovementPhase,
    ) {
        titan.clearInteraction()
        titan.mode = NpcMode.None
        titan.movementLocked = false
        if (titan.coords == destination) {
            movement.beginRoute(destination, afterArrival)
            movement.arrive(titan.coords, clock.cycle)
            return
        }
        val strategy = titan.collisionStrategy ?: return
        val route = routes.create(titan.avatar, destination, strategy)
        // A failed/partial route never advances a Titan's action clock or teleports it.
        if (
            !route.success ||
                route.lastOrNull()?.let { CoordGrid(it.x, it.z, destination.level) } != destination
        )
            return
        movement.beginRoute(destination, afterArrival)
        val generation = run.generation
        titan.walk(route.map { CoordGrid(it.x, it.z, destination.level) }) {
            if (
                run.generation == generation &&
                    npcRuns[titan] === run &&
                    run.stage in
                        setOf(
                            RoyalTitansStage.INTRO,
                            RoyalTitansStage.RANGED,
                            RoyalTitansStage.MELEE,
                        )
            ) {
                movement.arrive(titan.coords, clock.cycle)
            }
        }
    }

    private fun beginTransition(run: Run) {
        clearWalls(run)
        clearElementals(run)
        clearArenaFloors(run)
        setStage(run, RoyalTitansStage.TRANSITION)
        run.transitionStartCycle = clock.cycle
        run.branda?.apply {
            clearInteraction()
            movementLocked = true
            anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.BRANDA_TELEPORT))
            spotanim(spot(RoyalTitansAssets.TELEPORT_GFX), slot = 2)
            say("Time to finish this!")
        }
        landingTelegraph(run, RoyalElement.FIRE)
    }

    private fun tickTransition(run: Run) {
        when (clock.cycle - run.transitionStartCycle) {
            RoyalTitansRules.BRANDA_TRANSITION_TICKS -> {
                run.branda?.let {
                    PathingEntityCommon.telejump(it, collision, local(run, 30, 35))
                    it.moveRestrict = it.type.moveRestrict
                    it.anim(anim(RoyalTitansAssets.BRANDA_ARENA_SPAWN))
                    it.spotanim(spot(RoyalTitansAssets.BRANDA_SPAWN_GFX), slot = 2)
                    it.setBodyModels(listOf(RoyalTitansAssets.BRANDA_ARENA_BODY_MODEL))
                    it.faceSquare(local(run, 31, 31))
                    it.movementLocked = true
                }
                spawnBrandaArenaFloors(run)
                resolveTitanLanding(run, RoyalElement.FIRE)
            }
            RoyalTitansRules.ELDRIC_TRANSITION_CAST_TICK -> {
                run.eldric?.apply {
                    clearInteraction()
                    movementLocked = true
                    anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.ELDRIC_TELEPORT))
                    say("This is no game anymore!")
                }
                landingTelegraph(run, RoyalElement.ICE)
            }
            RoyalTitansRules.ELDRIC_TRANSITION_TICKS -> {
                run.eldric?.let {
                    PathingEntityCommon.telejump(it, collision, local(run, 30, 25))
                    it.moveRestrict = it.type.moveRestrict
                    it.anim(anim(RoyalTitansAssets.ELDRIC_ARENA_SPAWN))
                    it.setBodyModels(listOf(RoyalTitansAssets.ELDRIC_ARENA_BODY_MODEL))
                    it.faceSquare(local(run, 30, 30))
                    it.movementLocked = true
                }
                spawnEldricArenaFloors(run)
                resolveTitanLanding(run, RoyalElement.ICE)
                setStage(run, RoyalTitansStage.ARENA)
            }
        }
    }

    private fun landingTelegraph(run: Run, element: RoyalElement) {
        val row = if (element == RoyalElement.FIRE) 35 else 25
        val gfx =
            if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_ACTIVE_GFX
            else RoyalTitansAssets.ICE_WALL_ACTIVE_GFX
        // 2283 T1555/T1559: the visual is5x3, the eventual occupied footprint is3x3.
        for (z in row..row + 2) for (x in 29..33) {
            world.spotanimMap(SpotanimType(gfx), local(run, x, z))
        }
    }

    private fun resolveTitanLanding(run: Run, element: RoyalElement) {
        online(run).forEach { player ->
            if (inTitanLanding(run, player.coords, element)) {
                hit(
                    run,
                    player,
                    random.of(16) + 15,
                    HitType.Typeless,
                    bypassPrayer = true,
                    mechanic = "royal_titans.landing",
                )
                PathingEntityCommon.telejump(player, collision, local(run, 31, 30))
            }
        }
    }

    private fun tickArena(run: Run) {
        // Stage tick zero is the first tick AFTER landing (setStage resets to -1).
        val opening = RoyalTitansRules.ARENA_OPENING_TICKS - 1
        if (run.stageTick < opening) return
        val elapsed = run.stageTick - opening
        val cycle =
            if (elapsed < RoyalTitansRules.FIRST_ARENA_CYCLE_TICKS) elapsed
            else
                (elapsed - RoyalTitansRules.FIRST_ARENA_CYCLE_TICKS) %
                    RoyalTitansRules.ARENA_CYCLE_TICKS
        when (cycle) {
            0 -> startAreaAttack(run)
            14 -> {
                run.branda
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { scheduleSummon(run, it, RoyalElement.FIRE) }
                run.eldric
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { scheduleSummon(run, it, RoyalElement.ICE) }
            }
            21,
            28 -> {
                // A 29-tick rotation has two ordinary actions (+14,+21), not a third at +28
                // immediately followed by charge. The legacy cycle-selection policy is unresolved.
                if (cycle == 28 && elapsed >= RoyalTitansRules.FIRST_ARENA_CYCLE_TICKS) return
                run.branda
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { stomp(run, it, RoyalElement.FIRE, true) }
                run.eldric
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { stomp(run, it, RoyalElement.ICE, true) }
            }
        }
    }

    private fun tickComplete(run: Run) {
        commitCompletion(run)
        val claimCycle = run.firstClaimCycle ?: return
        when (clock.cycle - claimCycle) {
            21 ->
                run.corpses.forEach { (corpse, element) ->
                    corpse.hideAllOps()
                    corpse.anim(
                        anim(
                            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_DEATH
                            else RoyalTitansAssets.ELDRIC_DEATH
                        )
                    )
                    if (element == RoyalElement.FIRE)
                        corpse.spotanim(spot(RoyalTitansAssets.BRANDA_DEATH_GFX), slot = 2)
                    replaceArenaFloors(run, element)
                }
            26 -> {
                run.corpses.keys.toList().forEach { removeNpc(run, it) }
                clearArenaFloors(run)
            }
            29 -> reset(run)
        }
    }

    private fun castWall(run: Run, titan: Npc, element: RoyalElement) {
        val target = online(run).minByOrNull { distance(it.coords, titan.coords) } ?: return
        titan.faceSquare(target.coords)
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_WALL_CAST
                else RoyalTitansAssets.ELDRIC_WALL_CAST
            )
        )
        titan.spotanim(spot(RoyalTitansAssets.WALL_CAST_GFX))
        val row = localZ(run, target.coords).coerceIn(27, 34)
        (0..4).forEach { index ->
            val tile = local(run, 29 + index, row)
            world.spotanimMap(
                SpotanimType(
                    if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_START_GFX
                    else RoyalTitansAssets.ICE_WALL_START_GFX
                ),
                tile,
            )
            world.soundArea(
                tile,
                if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_FORM_SOUND
                else RoyalTitansAssets.ICE_WALL_FORM_SOUND,
                delay = 5,
                radius = 1,
            )
            world.soundArea(
                tile,
                if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_ACTIVE_SOUND
                else RoyalTitansAssets.ICE_WALL_ACTIVE_SOUND,
                delay = if (element == RoyalElement.FIRE) 120 else 122,
                radius = 1,
            )
        }
        run.pendingWalls +=
            PendingWall(clock.cycle + RoyalTitansRules.WALL_TELEGRAPH_TICKS, element, row)
    }

    private fun formWall(run: Run, pending: PendingWall) {
        (0..4).forEach { index ->
            val tile = local(run, 29 + index, pending.row)
            val occupant = online(run).firstOrNull { it.coords == tile }
            if (occupant != null) {
                hit(
                    run,
                    occupant,
                    random.of(RoyalTitansRules.WALL_MAX_HIT + 1),
                    HitType.Typeless,
                    bypassPrayer = true,
                    mechanic = "royal_titans.wall_formation",
                )
                return@forEach
            }
            run.elementals.entries
                .filter { it.key.coords == tile && it.value.element != pending.element }
                .map { it.key }
                .forEach { it.queueDeath() }
            val loc =
                locs.add(
                    tile,
                    requireLoc(
                        if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL
                        else RoyalTitansAssets.ICE_WALL
                    ),
                    Int.MAX_VALUE,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
            val hitbox =
                spawnNpc(
                    run,
                    if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_HITBOX
                    else RoyalTitansAssets.ICE_WALL_HITBOX,
                    tile,
                    hitpoints = 1,
                    movementLocked = true,
                )
            run.walls +=
                WallSegment(
                    pending.element,
                    pending.row,
                    index,
                    loc,
                    hitbox,
                    clock.cycle + RoyalTitansRules.WALL_LIFETIME_TICKS,
                )
            world.spotanimMap(
                SpotanimType(
                    if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_ACTIVE_GFX
                    else RoyalTitansAssets.ICE_WALL_ACTIVE_GFX
                ),
                tile,
            )
        }
    }

    private fun expireWalls(run: Run) {
        val pending = run.pendingWalls.iterator()
        while (pending.hasNext()) {
            val wall = pending.next()
            if (clock.cycle < wall.formCycle) continue
            pending.remove()
            formWall(run, wall)
        }
        run.walls.filter { clock.cycle >= it.expireCycle }.toList().forEach { clearWall(run, it) }
    }

    private fun clearWallTriplet(run: Run, wall: WallSegment) {
        run.walls
            .filter {
                it.element == wall.element &&
                    it.row == wall.row &&
                    it.index in RoyalTitansRules.removedWallIndices(wall.index)
            }
            .sortedBy { if (it.index == wall.index) 0 else if (it.index < wall.index) 1 else 2 }
            .toList()
            .forEach { clearWall(run, it, playerRemoved = true) }
    }

    private fun clearWall(run: Run, wall: WallSegment, playerRemoved: Boolean = false) {
        if (!run.walls.remove(wall)) return
        locs.del(wall.loc, Int.MAX_VALUE)
        removeNpc(run, wall.hitbox)
        world.spotanimMap(
            SpotanimType(
                if (wall.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_END_GFX
                else RoyalTitansAssets.ICE_WALL_END_GFX
            ),
            wall.loc.coords,
        )
        if (playerRemoved) return
        world.soundArea(
            wall.loc.coords,
            if (wall.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_BREAK_SOUND
            else RoyalTitansAssets.ICE_WALL_BREAK_SOUND,
            delay = if (wall.element == RoyalElement.FIRE) 0 else 4,
            radius = 1,
        )
    }

    private fun scheduleSummon(run: Run, titan: Npc, element: RoyalElement) {
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_SUMMON
                else RoyalTitansAssets.ELDRIC_SUMMON
            )
        )
        if (element == RoyalElement.FIRE)
            titan.spotanim(spot(RoyalTitansAssets.ELEMENTAL_SUMMON_GFX), slot = 2)
        run.pendingSummons +=
            PendingSummon(clock.cycle + RoyalTitansRules.SUMMON_DELAY_TICKS, element, titan)
    }

    private fun processPendingSummons(run: Run) {
        val iterator = run.pendingSummons.iterator()
        while (iterator.hasNext()) {
            val summon = iterator.next()
            if (clock.cycle < summon.spawnCycle) continue
            iterator.remove()
            if (summon.titan !== run.branda && summon.titan !== run.eldric) continue
            if (summon.titan.hitpoints <= 0) continue
            val arena =
                run.stage in
                    setOf(
                        RoyalTitansStage.ARENA,
                        RoyalTitansStage.FINAL_BRANDA,
                        RoyalTitansStage.FINAL_ELDRIC,
                    )
            val (packX, baseZ) =
                RoyalTitansUnverifiedMovementSelection.packStart(summon.element, arena) {
                    random.of(it)
                }
            val packTarget = targetFor(run, summon.titan)
            for (offset in 0..2) {
                val npc =
                    spawnNpc(
                        run,
                        if (summon.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL
                        else RoyalTitansAssets.ICE_ELEMENTAL,
                        local(run, packX, baseZ + offset),
                        hitpoints = RoyalTitansRules.ELEMENTAL_HITPOINTS,
                    )
                npc.anim(
                    anim(
                        if (summon.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN
                        else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN
                    )
                )
                npc.spotanim(
                    spot(
                        if (summon.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN_GFX
                        else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN_GFX
                    ),
                    slot = 2,
                )
                run.elementals[npc] =
                    ElementalState(
                        summon.element,
                        clock.cycle + RoyalTitansRules.ELEMENTAL_LIFETIME_TICKS,
                        packTarget?.uid,
                    )
                packTarget?.let { npc.apPlayer2(it, interactions) }
            }
            online(run).forEach {
                it.mes(
                    if (summon.element == RoyalElement.FIRE) "Branda summons fire elementals!"
                    else "Eldric summons ice elementals!"
                )
            }
            world.soundArea(
                local(run, packX, baseZ),
                if (summon.element == RoyalElement.FIRE)
                    RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN_SOUND
                else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN_SOUND,
            )
        }
    }

    private fun processElementals(run: Run) {
        run.elementals.toMap().forEach { (npc, state) ->
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) return@forEach
            if (clock.cycle >= state.explodeCycle) {
                state.exploding = true
                npc.anim(
                    anim(
                        if (state.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_EXPLODE
                        else RoyalTitansAssets.ICE_ELEMENTAL_EXPLODE
                    )
                )
                world.spotanimMap(
                    SpotanimType(
                        if (state.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_EXPLOSION_GFX
                        else RoyalTitansAssets.ICE_ELEMENTAL_EXPLOSION_GFX
                    ),
                    npc.coords,
                )
                online(run)
                    .filter { distance(it.coords, npc.coords) <= 1 }
                    .forEach {
                        hit(
                            run,
                            it,
                            8 + random.of(3),
                            HitType.Typeless,
                            bypassPrayer = true,
                            source = npc,
                            mechanic = "royal_titans.elemental_explosion",
                        )
                    }
                npc.queueDeath()
            } else {
                val retained =
                    state.target?.resolve(players)?.takeIf {
                        isPresent(run, it) && it.hitpoints > 0
                    }
                val target = retained ?: targetFor(run, npc)
                state.target = target?.uid
                if (target == null) {
                    npc.clearInteraction()
                    npc.resetMovement()
                    npc.mode = NpcMode.None
                } else if (
                    bounds.touches(npc.avatar, target.avatar) ||
                        bounds.collides(npc.avatar, target.avatar)
                ) {
                    if (
                        (npc.interaction as? org.rsmod.game.interact.InteractionPlayer)?.target !=
                            target
                    ) {
                        npc.apPlayer2(target, interactions)
                    }
                    state.routeTarget = null
                } else if (
                    state.routeTarget != target.coords ||
                        npc.routeDestination.isEmpty() ||
                        npc.mode != NpcMode.None ||
                        (!npc.movementLocked && !npc.isBound && npc.coords == npc.previousCoords)
                ) {
                    // Keep native attack/reach processing when adjacent. For travel feed the
                    // shared pathfinder's full route to native movement, including wall detours.
                    npc.clearInteraction()
                    npc.mode = NpcMode.None
                    npc.facePlayer(target)
                    val route = routes.create(npc.avatar, target.avatar)
                    state.routeTarget = target.coords
                    npc.walk(route.map { CoordGrid(it.x, it.z, it.level) })
                }
            }
        }
    }

    private fun startAreaAttack(run: Run) {
        if (run.areaAttack != null) return
        val survivor =
            when (run.stage) {
                RoyalTitansStage.FINAL_BRANDA -> RoyalElement.FIRE
                RoyalTitansStage.FINAL_ELDRIC -> RoyalElement.ICE
                else -> null
            }
        val (safeX, safeZ) = RoyalTitansRules.safeTile(survivor) { random.of(it) }
        val safeTile = local(run, safeX, safeZ)
        val safeLoc =
            locs.add(
                safeTile,
                requireLoc(RoyalTitansAssets.SAFE_TILE),
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.GroundDecor,
            )
        val timer = spawnNpc(run, RoyalTitansAssets.AREA_TIMER, safeTile, 1, movementLocked = true)
        val timerHeadbar =
            requireNotNull(
                ServerCacheManager.getHealthBar(
                    RSCM.run { RoyalTitansAssets.AREA_TIMER_HEADBAR.asRSCM(RSCMType.HEADBAR) }
                )
            )
        timer.showHeadbar(
            Headbar.fromNoSource(
                self = timerHeadbar.id,
                public = timerHeadbar.id,
                startFill = 0,
                endFill = timerHeadbar.segments.coerceIn(1, 255),
                startTime = 0,
                endTime = 210,
            )
        )
        run.branda
            ?.takeIf { it.hitpoints > 0 }
            ?.apply {
                anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.BRANDA_AREA_CHARGE))
                spotanim(spot(RoyalTitansAssets.AREA_CHARGE_GFX))
            }
        run.eldric
            ?.takeIf { it.hitpoints > 0 }
            ?.apply {
                anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.ELDRIC_AREA_CHARGE))
            }
        val executeCycle = clock.cycle + RoyalTitansRules.AREA_CHARGE_TICKS
        listOfNotNull(run.branda, run.eldric).forEach {
            it.clearInteraction()
            it.actionDelay = executeCycle + 6
        }
        run.areaAttack = AreaAttack(safeX, safeZ, executeCycle, safeLoc, timer, survivor)
    }

    private fun processAreaAttack(run: Run) {
        val attack = run.areaAttack ?: return
        val elapsed = clock.cycle - attack.impactCycle
        if (elapsed < 0) return
        if (elapsed == 0) {
            run.branda
                ?.takeIf { it.hitpoints > 0 }
                ?.apply {
                    anim(
                        this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.BRANDA_AREA_EXECUTE)
                    )
                    spotanim(spot(RoyalTitansAssets.AREA_EXECUTE_GFX))
                }
            run.eldric
                ?.takeIf { it.hitpoints > 0 }
                ?.anim(anim(RoyalTitansAssets.ELDRIC_AREA_EXECUTE))
            // 2283 T1576/L24471-24473: one synth per pulse per viewer, not area sounds.
            online(run).forEach { player ->
                for (delay in listOf(28, 88, 148)) {
                    player.soundSynth(RoyalTitansAssets.AREA_PULSE_SOUND, loops = 1, delay = delay)
                }
            }
            return
        }
        if (elapsed == 7) {
            endAreaAttack(run, attack)
            return
        }
        if (elapsed !in setOf(1, 3, 5)) return
        if (elapsed == 1) removeAreaTimer(run, attack)
        for (z in 28..34) {
            for (x in 29..33) {
                if (RoyalTitansRules.isSafeTile(x, z, attack.safeX, attack.safeZ)) continue
                val element = areaElement(run, attack, x, z) ?: continue
                world.spotanimMap(
                    SpotanimType(
                        if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_AREA_PROJECTILE
                        else RoyalTitansAssets.ICE_AREA_PROJECTILE
                    ),
                    local(run, x, z),
                )
                // 2283 T1613: fire coverage kills every stacked ice elemental on the tile.
                run.elementals.entries
                    .filter { it.key.coords == local(run, x, z) && it.value.element != element }
                    .map { it.key }
                    .forEach { it.queueDeath() }
            }
        }
        online(run)
            .filter {
                inArena(run, it.coords) &&
                    areaElement(run, attack, localX(run, it.coords), localZ(run, it.coords)) != null
            }
            .forEach {
                hit(
                    run,
                    it,
                    random.of(RoyalTitansRules.AREA_MAX_HIT + 1),
                    HitType.Typeless,
                    bypassPrayer = true,
                    mechanic = "royal_titans.elemental_blast",
                )
            }
    }

    private fun removeAreaTimer(run: Run, attack: AreaAttack) {
        if (!attack.timer.isSlotAssigned) return
        removeNpc(run, attack.timer)
    }

    private fun areaElement(run: Run, attack: AreaAttack, x: Int, z: Int): RoyalElement? {
        if (RoyalTitansRules.isSafeTile(x, z, attack.safeX, attack.safeZ)) return null
        val element =
            attack.survivor
                ?: if (z > attack.safeZ || (z == attack.safeZ && x > attack.safeX))
                    RoyalElement.FIRE
                else RoyalElement.ICE
        return element.takeIf {
            (if (it == RoyalElement.FIRE) run.branda else run.eldric)?.hitpoints?.let { hp ->
                hp > 0
            } == true
        }
    }

    private fun endAreaAttack(run: Run, attack: AreaAttack) {
        if (run.areaAttack !== attack) return
        locs.del(attack.safeLoc, Int.MAX_VALUE)
        removeAreaTimer(run, attack)
        run.areaAttack = null
        run.rangedGrace = RoyalTitansRules.AREA_GRACE_TICKS
    }

    private fun stomp(
        run: Run,
        titan: Npc,
        element: RoyalElement,
        arenaWide: Boolean =
            run.stage in
                setOf(
                    RoyalTitansStage.ARENA,
                    RoyalTitansStage.FINAL_BRANDA,
                    RoyalTitansStage.FINAL_ELDRIC,
                ),
    ) {
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP
                else RoyalTitansAssets.ELDRIC_STOMP
            )
        )
        world.soundArea(
            titan.coords,
            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP_SOUND
            else RoyalTitansAssets.ELDRIC_STOMP_SOUND,
        )
        val tiles = stompTiles(run, titan, element, arenaWide)
        tiles.forEach { tile ->
            val column =
                if (arenaWide) {
                    if (element == RoyalElement.FIRE) tiles.maxOf { it.z } - tile.z
                    else tile.z - tiles.minOf { it.z }
                } else {
                    if (element == RoyalElement.FIRE) tile.x - tiles.minOf { it.x }
                    else tiles.maxOf { it.x } - tile.x
                }
            world.spotanimMap(
                SpotanimType(RoyalTitansAssets.STOMP_TILE_GFX),
                tile,
                delay = column * 2,
            )
        }
        world.soundArea(
            titan.coords,
            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP_IMPACT_SOUND
            else RoyalTitansAssets.ELDRIC_STOMP_IMPACT_SOUND,
            delay = 1,
        )
        val final = run.branda == null || run.eldric == null
        online(run)
            .filter { it.coords in tiles }
            .forEach { player ->
                val protected = player.vars["varbit.prayer_protectfrommelee"] == 1
                val roll = random.of(RoyalTitansRules.STOMP_MAX_HIT + 1)
                val damage =
                    if (!final && protected) 0
                    else RoyalTitansRules.finalStompDamage(roll, protected)
                hit(
                    run,
                    player,
                    damage,
                    HitType.Melee,
                    bypassPrayer = true,
                    source = titan,
                    mechanic = "royal_titans.stomp",
                )
            }
    }

    private fun stompTiles(
        run: Run,
        titan: Npc,
        element: RoyalElement,
        arenaWide: Boolean,
    ): List<CoordGrid> =
        if (arenaWide) {
            (29..33).flatMap { x -> (28..34).map { z -> local(run, x, z) } }
        } else {
            val zRange = titan.coords.z..(titan.coords.z + 2)
            val xRange =
                if (element == RoyalElement.FIRE) (titan.coords.x + 3)..(titan.coords.x + 7)
                else (titan.coords.x - 5)..(titan.coords.x - 1)
            xRange.flatMap { x -> zRange.map { z -> CoordGrid(x, z, titan.coords.level) } }
        }

    fun attackTitan(access: org.rsmod.api.npc.access.StandardNpcAccess, target: Player) {
        val run = npcRuns[access.npc] ?: return
        if (target.accountHash !in run.participants) return
        if (
            run.stage in
                setOf(
                    RoyalTitansStage.ARENA,
                    RoyalTitansStage.FINAL_BRANDA,
                    RoyalTitansStage.FINAL_ELDRIC,
                )
        ) {
            access.actionDelay = clock.cycle + 7
            return
        }
        access.actionDelay = clock.cycle + 7
    }

    fun canAttackElemental(npc: Npc, target: Player): Boolean =
        npcRuns[npc]?.let { run ->
            npc in run.elementals && target.accountHash in run.participants
        } == true

    fun modifyNpcHit(player: Player?, npc: Npc, hit: HitBuilder) {
        val run = npcRuns[npc]
        if (run == null || (player != null && player.accountHash !in run.participants)) {
            hit.damage = 0
            return
        }
        // Native damage remains legal independently of its owner's contribution eligibility.
        // The shared hit context preserves the actual origin; typeless is not a provenance tag.
        if (player == null) {
            if (
                (npc !== run.branda && npc !== run.eldric) ||
                    run.stage in
                        setOf(
                            RoyalTitansStage.INTRO,
                            RoyalTitansStage.TRANSITION,
                            RoyalTitansStage.COMPLETE,
                        )
            ) {
                hit.damage = 0
            }
            return
        }
        if (run.walls.any { it.hitbox === npc }) {
            hit.damage = 0
            return
        }
        run.elementals[npc]?.let { elemental ->
            val spell = hit.secondaryType()
            if (
                hit.type == HitType.Magic &&
                    spell != null &&
                    RoyalTitansRules.opposingSpell(elemental.element, spell.internalName)
            ) {
                // Damage comes from the native spell calculation. Special accuracy must be
                // resolved by the spell content before presentation, never rerolled here.
                if (hit.damage > 0)
                    splashElementals(run, player, npc, elemental.element, hit.damage)
            }
            return
        }
        val titanElement =
            when (npc) {
                run.branda -> RoyalElement.FIRE
                run.eldric -> RoyalElement.ICE
                else -> null
            }
        if (
            titanElement == null ||
                run.stage in
                    setOf(
                        RoyalTitansStage.INTRO,
                        RoyalTitansStage.TRANSITION,
                        RoyalTitansStage.COMPLETE,
                    )
        ) {
            hit.damage = 0
            return
        }
        // HUD values are resolved HP, never a prediction made when this hit is queued.
    }

    fun canCastOnWall(player: Player, npc: Npc): Boolean {
        val run = npcRuns[npc] ?: return false
        return npc.isSlotAssigned && isPresent(run, player) && run.walls.any { it.hitbox === npc }
    }

    /** Called by the wall's native hit queue at projectile impact, not the launch-time modifier. */
    fun wallHit(npc: Npc, hit: Hit) {
        val run = npcRuns[npc] ?: return
        if (!hit.isFromPlayer || hit.type != HitType.Magic) return
        val player = hit.resolvePlayerSource(players) ?: return
        if (!canCastOnWall(player, npc)) return
        val spell = hit.secondaryType() ?: return
        if (!RoyalTitansWallSpells.accepts(npc.visType.id, spell.internalName)) return
        val wall = run.walls.firstOrNull { it.hitbox === npc } ?: return
        clearWallTriplet(run, wall)
    }

    /** The native impact event is emitted after HP capping/subtraction, before death execution. */
    fun npcHit(npc: Npc, hit: Hit) {
        val run = npcRuns[npc] ?: return
        if (!hit.isFromPlayer || hit.damage <= 0) return
        val player = hit.resolvePlayerSource(players) ?: return
        if (!isPresent(run, player)) return
        if (npc in run.elementals) {
            if (npc.hitpoints == 0) onElementalKilled(run, player, npc)
        }
    }

    private fun onElementalKilled(run: Run, player: Player, npc: Npc) {
        if (!run.creditedElementalKills.add(npc) || !isPresent(run, player)) return
        player.actionDelay =
            clock.cycle +
                RoyalTitansRules.elementalKillAttackDelay(player.actionDelay - clock.cycle)
    }

    private fun splashElementals(
        run: Run,
        player: Player,
        primary: Npc,
        element: RoyalElement,
        damage: Int,
    ) {
        if (damage <= 0) return
        run.elementals.entries
            .filter {
                it.key !== primary &&
                    it.value.element == element &&
                    distance(it.key.coords, primary.coords) <= 1
            }
            .forEach { (npc, _) ->
                val splash = damage.coerceAtMost(npc.hitpoints)
                npc.queueHit(
                    source = player,
                    delay = RoyalTitansRules.ELEMENTAL_SPLASH_HIT_DELAY_TICKS,
                    type = HitType.Magic,
                    damage = splash,
                    modifier = npcHitModifier,
                )
            }
    }

    fun beginTitanDeath(npc: Npc): Run? {
        val run = npcRuns[npc] ?: return null
        if (npc !== run.branda && npc !== run.eldric) return null
        if (run.dyingTitans.add(npc)) {
            npc.clearInteraction()
            npc.movementLocked = true
        }
        return run
    }

    fun finishTitanDeath(npc: Npc) {
        val run = npcRuns[npc] ?: return
        if (!run.dyingTitans.remove(npc)) return
        online(run).forEach { player ->
            if (run.lastBossTarget[player.accountHash] === npc)
                bossHpBar.onClose(player, npc, instant = true)
        }
        val element = if (npc === run.branda) RoyalElement.FIRE else RoyalElement.ICE
        if (element == RoyalElement.FIRE) run.branda = null else run.eldric = null
        val corpse = npc
        corpse.queueList.clear()
        corpse.noneMode()
        val corpseType =
            requireNpc(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_CORPSE
                else RoyalTitansAssets.ELDRIC_CORPSE
            )
        corpse.transmog(corpseType, Int.MAX_VALUE)
        corpse.resetBodyModels()
        // Inactive cache types have 500 HP for native noncombat interaction validity. They are
        // no longer active Titans; their own queue handlers reject combat without a new headbar.
        corpse.copyStats(corpseType)
        corpse.hideAllOps()
        corpse.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_INACTIVE
                else RoyalTitansAssets.ELDRIC_INACTIVE
            )
        )
        run.corpses[corpse] = element
        if (element == RoyalElement.FIRE) {
            run.brandaCorpse = corpse
        } else {
            run.eldricCorpse = corpse
        }
        when {
            // Defeating one Titan does not restart the surviving Titan's rotation clock.
            run.branda != null && run.eldric == null -> run.stage = RoyalTitansStage.FINAL_BRANDA
            run.eldric != null && run.branda == null -> run.stage = RoyalTitansStage.FINAL_ELDRIC
            run.branda == null && run.eldric == null -> complete(run, npc)
        }
    }

    fun finishElementalDeath(npc: Npc) {
        val run = npcRuns[npc] ?: return
        val state = run.elementals.remove(npc) ?: return
        npc.clearInteraction()
        npc.resetMovement()
        npc.movementLocked = true
        npc.hideAllOps()
        if (state.exploding) {
            run.pendingRemovals[npc] =
                clock.cycle + if (state.element == RoyalElement.FIRE) 2 else 1
            return
        }
        npc.anim(
            anim(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH
            )
        )
        npc.spotanim(
            spot(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH_GFX
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH_GFX
            )
        )
        val sequence =
            ServerCacheManager.getAnim(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH
            )
        run.pendingRemovals[npc] = clock.cycle + (sequence?.tickDuration ?: 1).coerceAtLeast(1)
    }

    private fun complete(run: Run, lastTitan: Npc) {
        if (run.completion != null) return
        run.hitScope.invalidate()
        run.participants.values.forEach { it.hitScope.invalidate() }
        clearWalls(run)
        clearElementals(run)
        setStage(run, RoyalTitansStage.COMPLETE)
        val duration =
            RoyalTitansTime.elapsed(requireNotNull(run.spawnCycles[lastTitan]), clock.cycle)
        run.completion =
            RoyalTitansCompletion(
                run.completionId,
                duration,
                online(run)
                    .filter { contribution(it) > 0 }
                    .map {
                        RoyalTitansFinisher(
                            it.accountId,
                            it.displayName,
                            personalBestEligible = true,
                        )
                    }
                    .distinctBy { it.accountId },
            )
        online(run).forEach {
            it.combatClearQueue()
            it.clearInteraction()
            run.lastBossTarget.remove(it.accountHash)?.let { target ->
                bossHpBar.onClose(it, target, instant = true)
            }
        }
        commitCompletion(run)
    }

    private fun commitCompletion(run: Run) {
        val completion = run.completion ?: return
        if (run.completionCommitted) return
        pendingCompletions.putIfAbsent(completion.encounterId, PendingCompletion(completion))
        flushCompletions()
    }

    private fun flushCompletions() {
        for ((id, pending) in pendingCompletions.toMap()) {
            if (clock.cycle < pending.retryCycle) continue
            val receipts =
                try {
                    statistics.complete(pending.completion)
                } catch (failure: SQLException) {
                    pending.retryCycle = clock.cycle + 10
                    log.error(failure) { "Royal Titans completion persistence pending: $id" }
                    continue
                }
            pendingCompletions.remove(id)
            val run = runs.values.firstOrNull { it.completion?.encounterId == id }
            if (run != null) {
                run.completionCommitted = true
                run.corpses.keys.forEach { it.showAllOps() }
            }
            // Refresh an online finisher's projection even after exit/reconnect. Never reopen an
            // abandoned run or advertise its expired loot to someone outside that exact run.
            for (player in players) {
                val receipt = receipts[player.accountId] ?: continue
                VarPlayerIntMapSetter.set(
                    player,
                    "varp.total_royal_titan_kills",
                    receipt.snapshot.personalKills,
                )
                if (run == null || participantRuns[player.accountHash] !== run) continue
                player.mes(
                    "Your Royal Titans kill count is: <col=ff0000>${receipt.snapshot.personalKills}</col>."
                )
                player.mes(
                    RoyalTitansTime.durationMessage(pending.completion.elapsedTicks, receipt)
                )
                player.mes(
                    "You are eligible to take loot from either Branda the Fire Queen or Eldric the Ice King."
                )
            }
        }
    }

    fun claim(player: Player, corpse: Npc, choice: ClaimChoice) {
        val run = npcRuns[corpse] ?: return player.mes("That Titan is no longer available.")
        val element = run.corpses[corpse] ?: return player.mes("That Titan cannot be claimed.")
        if (run.stage != RoyalTitansStage.COMPLETE || !run.completionCommitted)
            return player.mes("Defeat both Titans before claiming a reward.")
        if (contribution(player) <= 0) return player.mes("You did not contribute to this victory.")
        if (!run.claimed.add(player.accountHash))
            return player.mes("You have already claimed this victory's reward.")
        when (choice) {
            ClaimChoice.LOOT -> rollLoot(player, corpse)
            ClaimChoice.PAGES ->
                objects.add("obj.desiccated_page", player.coords, 200, player, 10 + random.of(10))
            ClaimChoice.SACRIFICE -> {
                if (random.of(1500) == 0)
                    objects.add("obj.rtbrandapet", player.coords, 200, player, 1)
                player.mes("You sacrifice your loot for an improved pet chance.")
            }
        }
        if (choice != ClaimChoice.SACRIFICE)
            player.mes(
                "You take your reward from ${if (element == RoyalElement.FIRE) "Branda" else "Eldric"}."
            )
        if (run.firstClaimCycle == null) {
            run.firstClaimCycle = clock.cycle
            online(run).forEach {
                it.mes(
                    "<col=ef1020>The Royal Titans will reinvigorate themselves in 18 seconds.</col>"
                )
            }
        }
    }

    private fun rollLoot(player: Player, corpse: Npc) {
        val activeType =
            requireNpc(
                if (runElement(corpse) == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_ACTIVE
                else RoyalTitansAssets.ELDRIC_ACTIVE
            )
        val proxy = Npc(activeType, corpse.coords)
        when (val result = drops.forNpc(proxy)?.roll(player, ArgMap())?.flatten()) {
            is RollResult.Single -> giveDrop(player, result.result)
            is RollResult.ListOf -> result.results.forEach { giveDrop(player, it) }
            else -> player.mes("No reward table was available.")
        }
    }

    private fun runElement(corpse: Npc): RoyalElement =
        npcRuns[corpse]?.corpses?.get(corpse) ?: RoyalElement.FIRE

    private fun giveDrop(player: Player, drop: DropRollItem) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        objects.add(obj, player.coords, 200, player, drop.rollCount(random))
    }

    fun finishAuxiliaryDeath(npc: Npc) {
        val run = npcRuns[npc] ?: return
        if (npc in run.elementals) finishElementalDeath(npc)
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = npcRuns[npc] ?: return "That creature is not part of this encounter."
        if (player.accountHash !in run.participants)
            return "That creature belongs to another encounter."
        if (npc in run.corpses) return "Choose a reward option instead."
        if (npc === run.branda || npc === run.eldric) {
            return if (
                run.stage in
                    setOf(
                        RoyalTitansStage.RANGED,
                        RoyalTitansStage.MELEE,
                        RoyalTitansStage.ARENA,
                        RoyalTitansStage.FINAL_BRANDA,
                        RoyalTitansStage.FINAL_ELDRIC,
                    )
            )
                null
            else "The Titan cannot be attacked during this transition."
        }
        if (npc in run.elementals || run.walls.any { it.hitbox === npc }) return null
        return "You cannot attack that."
    }

    fun isEncounterNpc(npc: Npc): Boolean = npcRuns.containsKey(npc)

    fun isParticipant(player: Player, npc: Npc): Boolean =
        npcRuns[npc]?.participants?.containsKey(player.accountHash) == true

    fun isActive(player: Player): Boolean = participantRuns.containsKey(player.accountHash)

    /** Native processed Titan damage only; helpers are deliberately excluded. */
    fun contribution(player: Player): Int {
        val run = participantRuns[player.accountHash] ?: return 0
        return (listOfNotNull(run.branda, run.eldric) + run.corpses.keys).distinct().sumOf {
            it.damageContributions.damageBy(player)
        }
    }

    fun status(player: Player): String {
        val run = participantRuns[player.accountHash] ?: return "Royal Titans: no active encounter."
        return "Royal Titans ${run.stage}: Branda ${run.branda?.hitpoints ?: 0}/600, Eldric ${run.eldric?.hitpoints ?: 0}/600, walls=${run.walls.size}, elementals=${run.elementals.size}, contribution=${contribution(player)}."
    }

    private fun spawnTitans(run: Run) {
        run.completionId = UUID.randomUUID()
        run.completion = null
        run.completionCommitted = false
        run.firstClaimCycle = null
        run.spawnCycles.clear()
        run.branda = spawnTitan(run, RoyalTitansAssets.BRANDA_ACTIVE, ROYAL_TITANS_BRANDA_SPAWN)
        run.eldric = null
        run.eldricSpawnCycle = clock.cycle + 2
    }

    private fun spawnTitan(run: Run, id: Int, local: RegionLocal): Npc {
        val tile = requireNotNull(instances.localCoord(run.session, local))
        val npc = spawnNpc(run, id, tile, ROYAL_TITANS_MAX_HITPOINTS)
        // The fire/ice platforms carry BLOCK_WALK in region 11669. Titans walk on those
        // blocked tiles; players and elementals retain normal terrain restrictions. Use the
        // same native restriction for route selection and step execution, preserving loc walls.
        npc.moveRestrict = MoveRestrict.Blocked
        run.spawnCycles[npc] = clock.cycle
        run.movement[npc] = RoyalTitanMovement(clock.cycle)
        npc.anim(
            anim(
                if (id == RoyalTitansAssets.BRANDA_ACTIVE) RoyalTitansAssets.BRANDA_ARENA_SPAWN
                else RoyalTitansAssets.ELDRIC_ARENA_SPAWN
            )
        )
        npc.faceSquare(local(run, if (id == RoyalTitansAssets.BRANDA_ACTIVE) 32 else 28, 30))
        npc.mode = NpcMode.None
        return npc
    }

    private fun spawnNpc(
        run: Run,
        id: Int,
        tile: CoordGrid,
        hitpoints: Int,
        movementLocked: Boolean = false,
    ): Npc {
        val npc = Npc(requireNpc(id), tile)
        npc.combatHitScope = run.hitScope
        npc.mode = NpcMode.None
        npc.baseHitpointsLvl = hitpoints
        npc.hitpoints = hitpoints
        npc.movementLocked = movementLocked
        npcs.add(npc, ROYAL_TITANS_NPC_LIFETIME)
        npc.respawns = false
        instances.attachNpc(run.session.id, npc)
        npcRuns[npc] = run
        return npc
    }

    private fun spawnBrandaArenaFloors(run: Run) {
        run.arenaFloors += spawnLoc(run, 29, 35, RoyalTitansAssets.BRANDA_FLOOR_WEST)
        spawnTitanFootprintBlockers(run, 35, 30..31)
        run.arenaFloors += spawnLoc(run, 33, 35, RoyalTitansAssets.BRANDA_FLOOR_EAST)
        spawnTitanFootprintBlockers(run, 35, 32..32)
    }

    private fun spawnEldricArenaFloors(run: Run) {
        run.arenaFloors += spawnLoc(run, 29, 25, RoyalTitansAssets.ELDRIC_FLOOR_WEST)
        spawnTitanFootprintBlockers(run, 25, 30..31)
        run.arenaFloors += spawnLoc(run, 33, 25, RoyalTitansAssets.ELDRIC_FLOOR_EAST)
        spawnTitanFootprintBlockers(run, 25, 32..32)
    }

    private fun spawnTitanFootprintBlockers(run: Run, row: Int, columns: IntRange) {
        for (x in columns) for (z in row..row + 2) {
            run.arenaBlockers += spawnLoc(run, x, z, 26209)
        }
    }

    private fun replaceArenaFloors(run: Run, element: RoyalElement) {
        val relevant =
            run.arenaFloors.filter {
                if (element == RoyalElement.FIRE) localZ(run, it.coords) == 35
                else localZ(run, it.coords) == 25
            }
        relevant.forEach {
            locs.del(it, Int.MAX_VALUE)
            run.arenaFloors.remove(it)
        }
        if (element == RoyalElement.FIRE) {
            run.arenaFloors += spawnLoc(run, 29, 35, RoyalTitansAssets.BRANDA_DEATH_FLOOR_WEST)
            run.arenaFloors += spawnLoc(run, 33, 35, RoyalTitansAssets.BRANDA_DEATH_FLOOR_EAST)
        } else {
            run.arenaFloors += spawnLoc(run, 29, 25, RoyalTitansAssets.ELDRIC_DEATH_FLOOR_WEST)
            run.arenaFloors += spawnLoc(run, 33, 25, RoyalTitansAssets.ELDRIC_DEATH_FLOOR_EAST)
        }
    }

    private fun spawnLoc(run: Run, x: Int, z: Int, id: Int): LocInfo =
        locs.add(
            local(run, x, z),
            requireLoc(id),
            Int.MAX_VALUE,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )

    private fun clearArenaFloors(run: Run) {
        run.arenaFloors.toList().forEach { locs.del(it, Int.MAX_VALUE) }
        run.arenaFloors.clear()
        run.arenaBlockers.toList().forEach { locs.del(it, Int.MAX_VALUE) }
        run.arenaBlockers.clear()
    }

    private fun setStage(run: Run, stage: RoyalTitansStage) {
        run.stage = stage
        run.stageTick = -1
    }

    private fun reset(run: Run) {
        run.hitScope.invalidate()
        run.participants.values.forEach { it.hitScope.invalidate() }
        run.generation = generation++
        clearWalls(run)
        clearElementals(run)
        run.areaAttack?.let { endAreaAttack(run, it) }
        run.corpses.keys.toList().forEach { removeNpc(run, it) }
        run.corpses.clear()
        run.brandaCorpse = null
        run.eldricCorpse = null
        clearArenaFloors(run)
        run.claimed.clear()
        run.elementalsUnlocked = false
        run.areaSequence = 0
        run.packSequence = 0
        run.sameTickElementalKills = 0
        run.elementalKillCycle = -1
        run.rangedGrace = 0
        run.creditedElementalKills.clear()
        run.pendingRemovals.keys.toList().forEach { removeNpc(run, it) }
        run.pendingRemovals.clear()
        run.lastBossTarget.clear()
        run.movement.clear()
        run.dyingTitans.clear()
        spawnTitans(run)
        setStage(run, RoyalTitansStage.INTRO)
        online(run).forEach { it.mes("The Royal Titans reinvigorate themselves.") }
    }

    private fun clearWalls(run: Run) {
        run.pendingWalls.clear()
        run.walls.toList().forEach { clearWall(run, it) }
    }

    private fun clearElementals(run: Run) {
        run.pendingSummons.clear()
        run.elementals.keys.toList().forEach { removeNpc(run, it) }
        run.elementals.clear()
    }

    private fun removeNpc(run: Run, npc: Npc) {
        npc.resetBodyModels()
        npcRuns.remove(npc)
        run.elementals.remove(npc)
        run.corpses.remove(npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    private fun abort(run: Run, reason: String, terminate: Boolean = true) {
        if (runs.remove(run.session.id) !== run) return
        run.hitScope.close()
        run.participants.values.forEach { it.hitScope.close() }
        run.areaAttack?.let { endAreaAttack(run, it) }
        clearWalls(run)
        clearElementals(run)
        clearArenaFloors(run)
        run.pendingRemovals.keys.toList().forEach { removeNpc(run, it) }
        run.pendingRemovals.clear()
        (listOfNotNull(run.branda, run.eldric, run.brandaCorpse, run.eldricCorpse) +
                run.corpses.keys)
            .distinct()
            .forEach { removeNpc(run, it) }
        online(run).forEach { player ->
            run.lastBossTarget[player.accountHash]?.let { target ->
                bossHpBar.onClose(player, target, instant = true)
            }
        }
        online(run).forEach { player ->
            player.combatHitScope = null
            prepare(player)
        }
        run.participants.keys.forEach(participantRuns::remove)
        if (terminate) instances.terminate(run.session)
        log.info { "[RoyalTitans] generation=${run.generation} abort=$reason" }
    }

    private fun hit(
        run: Run,
        player: Player,
        damage: Int,
        type: HitType,
        bypassPrayer: Boolean,
        mechanic: String,
        source: Npc? = null,
    ) {
        val attacker = source ?: run.branda ?: run.eldric ?: return
        player.queueHit(
            source = attacker,
            delay = 1,
            type = type,
            damage = damage.coerceAtLeast(0),
            context =
                HitContext.of(HitAttribution(HitOrigin.Direct, mechanic = mechanic), run.hitScope),
            modifier =
                if (bypassPrayer) NpcBypassProtectionPrayerPlayerHitModifier
                else StandardPlayerHitModifier,
        )
    }

    private fun isPresent(run: Run, player: Player): Boolean =
        participantRuns[player.accountHash] === run &&
            player.isSlotAssigned &&
            !player.pendingLogout &&
            !player.loggingOut &&
            player.hitpoints > 0 &&
            instances.sessionForPlayer(player)?.id == run.session.id

    private fun canTravel(player: Player): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the Royal Titans' arena.")
            return false
        }
        if (
            participantRuns.containsKey(player.accountHash) ||
                instances.sessionForPlayer(player) != null
        ) {
            player.mes("You already have an active instance.")
            return false
        }
        return true
    }

    private fun targetFor(run: Run, npc: Npc): Player? =
        online(run)
            .filter { isPresent(run, it) && it.hitpoints > 0 }
            .minByOrNull { npc.distanceTo(it) }

    private fun online(run: Run): List<Player> =
        run.participants.values
            .mapNotNull { participant ->
                participant.uid.resolve(players)
                    ?: players.firstOrNull {
                        it.accountHash == participant.accountHash && it.isSlotAssigned
                    }
            }
            .filter { it.isSlotAssigned && !it.pendingLogout && !it.loggingOut }

    private fun inArena(run: Run, tile: CoordGrid): Boolean {
        val sw = local(run, 29, 28)
        val ne = local(run, 33, 34)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    private fun inTitanLanding(run: Run, tile: CoordGrid, element: RoyalElement): Boolean {
        val x = localX(run, tile)
        val z = localZ(run, tile)
        return x in 30..32 &&
            when (element) {
                RoyalElement.FIRE -> z in 35..37
                RoyalElement.ICE -> z in 25..27
            }
    }

    private fun local(run: Run, x: Int, z: Int): CoordGrid =
        requireNotNull(instances.localCoord(run.session, RegionLocal(0, 45, 149, x, z)))

    private fun localX(run: Run, tile: CoordGrid): Int = tile.x - local(run, 0, 0).x

    private fun localZ(run: Run, tile: CoordGrid): Int = tile.z - local(run, 0, 0).z

    private fun distance(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) {
            "Missing revision-240 Royal Titans NPC $id"
        }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing revision-240 Royal Titans location $id"
        }

    private fun anim(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) {
            "Missing Royal Titans sequence $id"
        }

    private fun spot(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
            "Missing Royal Titans spotanim $id"
        }

    private fun participant(player: Player, session: InstanceSession): Participant {
        val base = requireNotNull(instances.localCoord(session, RegionLocal(0, 45, 149, 0, 0)))
        val scope = HitScope(Bounds(base, 64, 64), player.clientDisconnected)
        player.combatHitScope = scope
        return Participant(player.accountHash, player.uid, scope)
    }

    internal data class Participant(
        val accountHash: Long,
        var uid: PlayerUid,
        val hitScope: HitScope,
    )

    private data class PendingCompletion(
        val completion: RoyalTitansCompletion,
        var retryCycle: Int = Int.MIN_VALUE,
    )

    internal data class Run(
        var generation: Long,
        val session: InstanceSession,
        val ownerHash: Long,
        val participants: MutableMap<Long, Participant>,
        val hitScope: HitScope = HitScope(),
        var completionId: UUID = UUID.randomUUID(),
        var completion: RoyalTitansCompletion? = null,
        var completionCommitted: Boolean = false,
        var firstClaimCycle: Int? = null,
        val spawnCycles: MutableMap<Npc, Int> = mutableMapOf(),
        val movement: MutableMap<Npc, RoyalTitanMovement> = mutableMapOf(),
        var branda: Npc? = null,
        var eldric: Npc? = null,
        var brandaCorpse: Npc? = null,
        var eldricCorpse: Npc? = null,
        var stage: RoyalTitansStage = RoyalTitansStage.INTRO,
        var stageTick: Int = 0,
        var transitionStartCycle: Int = Int.MIN_VALUE,
        var elementalsUnlocked: Boolean = false,
        var areaSequence: Int = 0,
        var packSequence: Int = 0,
        var eldricSpawnCycle: Int = Int.MAX_VALUE,
        var sameTickElementalKills: Int = 0,
        var elementalKillCycle: Int = -1,
        var areaAttack: AreaAttack? = null,
        var rangedGrace: Int = 0,
        val claimed: MutableSet<Long> = mutableSetOf(),
        val lastBossTarget: MutableMap<Long, Npc> = mutableMapOf(),
        val walls: MutableList<WallSegment> = mutableListOf(),
        val pendingWalls: MutableList<PendingWall> = mutableListOf(),
        val elementals: MutableMap<Npc, ElementalState> = mutableMapOf(),
        val pendingSummons: MutableList<PendingSummon> = mutableListOf(),
        val corpses: MutableMap<Npc, RoyalElement> = mutableMapOf(),
        val dyingTitans: MutableSet<Npc> = mutableSetOf(),
        val arenaFloors: MutableList<LocInfo> = mutableListOf(),
        val arenaBlockers: MutableList<LocInfo> = mutableListOf(),
        val pendingRemovals: MutableMap<Npc, Int> = mutableMapOf(),
        val creditedElementalKills: MutableSet<Npc> = mutableSetOf(),
    )

    internal data class PendingWall(val formCycle: Int, val element: RoyalElement, val row: Int)

    internal data class WallSegment(
        val element: RoyalElement,
        val row: Int,
        val index: Int,
        val loc: LocInfo,
        val hitbox: Npc,
        val expireCycle: Int,
    )

    internal data class PendingSummon(
        val spawnCycle: Int,
        val element: RoyalElement,
        val titan: Npc,
    )

    internal data class ElementalState(
        val element: RoyalElement,
        val explodeCycle: Int,
        var target: PlayerUid? = null,
        var routeTarget: CoordGrid? = null,
        var exploding: Boolean = false,
    )

    internal data class AreaAttack(
        val safeX: Int,
        val safeZ: Int,
        val impactCycle: Int,
        val safeLoc: LocInfo,
        val timer: Npc,
        val survivor: RoyalElement?,
    )
}

internal enum class ClaimChoice {
    LOOT,
    SACRIFICE,
    PAGES,
}
