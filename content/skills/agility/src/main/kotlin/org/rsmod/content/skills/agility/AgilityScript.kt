package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.types.BasType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.slf4j.LoggerFactory

public class AgilityScript
@Inject
constructor(private val random: GameRandom, private val objects: ObjRepository) : PluginScript() {
    private val progress = WeakHashMap<Player, CourseProgress>()
    private val lastMarkCycleByPlayer = mutableMapOf<String, Int>()

    override fun ScriptContext.startup() {
        var registered = 0
        val missing = mutableListOf<Int>()
        for (id in AgilityDefinitions.obstacleIds) {
            val type = ServerCacheManager.getObject(id)
            if (type == null) {
                missing += id
                continue
            }
            val actions =
                AgilityDefinitions.courses
                    .flatMap { it.obstacles }
                    .filter { it.objectId == id }
                    .map { it.action }
            val opIndex =
                (0 until 5).firstOrNull { index ->
                    actions.any { expected ->
                        type.actions.getOpOrNull(index).equals(expected, ignoreCase = true)
                    }
                }
            if (opIndex == null) {
                log.warn(
                    "Agility obstacle {} ({}) has none of the expected actions {}",
                    id,
                    type.name,
                    actions,
                )
                continue
            }
            when (opIndex) {
                0 -> onProtectedEvent<LocEvents.Op1>(id) { useObstacle(it.loc) }
                1 -> onProtectedEvent<LocEvents.Op2>(id) { useObstacle(it.loc) }
                2 -> onProtectedEvent<LocEvents.Op3>(id) { useObstacle(it.loc) }
                3 -> onProtectedEvent<LocEvents.Op4>(id) { useObstacle(it.loc) }
                4 -> onProtectedEvent<LocEvents.Op5>(id) { useObstacle(it.loc) }
            }
            val usesCustomApproach =
                AgilityDefinitions.courses.any { course ->
                    course.obstacles.any { obstacle ->
                        obstacle.objectId == id &&
                            AgilityRoutes.approachFor(course.key, obstacle.stage) != null
                    }
                }
            if (usesCustomApproach) {
                when (opIndex) {
                    0 -> onProtectedEvent<LocEvents.Ap1>(id) { useApproachObstacle(it.loc) }
                    1 -> onProtectedEvent<LocEvents.Ap2>(id) { useApproachObstacle(it.loc) }
                    2 -> onProtectedEvent<LocEvents.Ap3>(id) { useApproachObstacle(it.loc) }
                    3 -> onProtectedEvent<LocEvents.Ap4>(id) { useApproachObstacle(it.loc) }
                    4 -> onProtectedEvent<LocEvents.Ap5>(id) { useApproachObstacle(it.loc) }
                }
            }
            registered++
        }
        onPlayerLogout { progress.remove(player) }
        log.info(
            "Registered Agility: {} courses, {} obstacle object IDs{}",
            AgilityDefinitions.courses.size,
            registered,
            if (missing.isEmpty()) "" else "; missing=${missing.joinToString()}",
        )
    }

    private suspend fun ProtectedAccess.useApproachObstacle(loc: BoundLocInfo) {
        if (!isWithinApRange(loc, AGILITY_APPROACH_RANGE)) return
        useObstacle(loc, skipArriveDelay = true)
    }

    private suspend fun ProtectedAccess.useObstacle(
        loc: BoundLocInfo,
        skipArriveDelay: Boolean = false,
    ) {
        if (!skipArriveDelay) arriveDelay()
        val resolved = AgilityDefinitions.resolve(loc.id, loc.coords)
        if (resolved == null) {
            log.warn("No Agility definition for object {} at {}", loc.id, loc.coords)
            return
        }
        val (course, obstacle) = resolved
        if (stat(AGILITY_STAT) < course.requiredLevel) {
            mes("You need an Agility level of ${course.requiredLevel} to use this obstacle.")
            return
        }
        val approach = AgilityRoutes.approachFor(course.key, obstacle.stage)
        if (approach != null && player.coords != approach) {
            playerWalkWithMinDelay(approach)
            if (player.coords != approach) {
                mes("Move directly in front of the obstacle before attempting it.")
                return
            }
        }
        faceLoc(loc)
        val route = AgilityRoutes.routeFor(course.key, obstacle.stage)
        val start = player.coords
        val destination =
            route.lastOrNull()?.destination
                ?: obstacle.movement.destination
                ?: relativeDestination(start, obstacle.movement)
        if (route.isNotEmpty()) {
            performRoute(route)
        } else {
            performMovement(obstacle.movement, start, destination)
        }
        if (player.coords != destination) {
            log.warn(
                "Agility route {} stage {} ended at {} instead of {}",
                course.key,
                obstacle.stage,
                player.coords,
                destination,
            )
            return
        }

        val ordered = isOrdered(player, course, obstacle)
        if (obstacle.finalObstacle) {
            finishLap(course, obstacle, ordered)
            progress.remove(player)
        } else {
            if (obstacle.baseXp > 0.0) statAdvance(AGILITY_STAT, obstacle.baseXp)
            if (obstacle.stage == 1 || ordered) {
                progress[player] = CourseProgress(course.key, obstacle.stage)
            }
        }
        if (course.rooftop) restoreRooftopEnergy()
    }

    private suspend fun ProtectedAccess.performRoute(route: List<AgilityRouteStep>) {
        for (step in route) {
            val start = player.coords
            val movementRenderOwnsAnimation =
                step.renderStyle != null && step.animation == step.renderStyle.readyAnimation
            if (step.animation != null && !movementRenderOwnsAnimation) anim(step.animation)
            if (step.teleport || start.level != step.destination.level) {
                delay(step.durationTicks)
                telejump(step.destination, TeleportType.Exempt)
            } else if (start == step.destination) {
                delay(step.durationTicks)
            } else {
                val previousBas = player.bas
                step.renderStyle?.let { player.bas = COURSE_RENDER_BAS.getValue(it) }
                try {
                    exactMove(
                        start,
                        step.destination,
                        0,
                        step.durationTicks * CLIENT_CYCLES_PER_TICK,
                        step.facing?.toDirectionConstant() ?: facing(start, step.destination),
                        TeleportType.Exempt,
                    )
                    delay(step.durationTicks)
                } finally {
                    if (step.renderStyle != null) player.bas = previousBas
                }
            }
            step.landingAnimation?.let(::anim)
        }
    }

    private suspend fun ProtectedAccess.performMovement(
        movement: AgilityMovement,
        start: CoordGrid,
        destination: CoordGrid,
    ) {
        when (movement.style) {
            AgilityMovementStyle.Balance -> {
                val previousBas = player.bas
                player.bas = COURSE_RENDER_BAS.getValue(AgilityRenderStyle.Balance)
                try {
                    exactMove(
                        start,
                        destination,
                        0,
                        movement.durationTicks * CLIENT_CYCLES_PER_TICK,
                        facing(start, destination),
                        TeleportType.Exempt,
                    )
                    delay(movement.durationTicks)
                } finally {
                    player.bas = previousBas
                }
            }
            AgilityMovementStyle.Jump -> {
                anim(movement.animation)
                if (start.level == destination.level) {
                    exactMove(
                        start,
                        destination,
                        0,
                        movement.durationTicks * CLIENT_CYCLES_PER_TICK,
                        facing(start, destination),
                        TeleportType.Exempt,
                    )
                    delay(movement.durationTicks)
                } else {
                    delay(movement.durationTicks)
                    telejump(destination, TeleportType.Exempt)
                }
                movement.landingAnimation?.let(::anim)
            }
            AgilityMovementStyle.Climb -> {
                anim(movement.animation)
                delay(movement.durationTicks)
                telejump(destination, TeleportType.Exempt)
            }
            AgilityMovementStyle.Pipe -> {
                val travelAnimation = movement.travelAnimation
                if (travelAnimation == null) {
                    anim(movement.animation)
                    delay(movement.durationTicks)
                    telejump(destination, TeleportType.Exempt)
                } else {
                    val travelTicks = (movement.durationTicks - 2).coerceAtLeast(1)
                    anim(movement.animation)
                    delay(1)
                    if (travelAnimation != movement.animation) anim(travelAnimation)
                    exactMove(
                        start,
                        destination,
                        0,
                        travelTicks * CLIENT_CYCLES_PER_TICK,
                        facing(start, destination),
                        TeleportType.Exempt,
                    )
                    delay(travelTicks)
                    movement.landingAnimation?.let {
                        anim(it)
                        delay(1)
                    }
                }
            }
        }
    }

    private fun ProtectedAccess.finishLap(
        course: AgilityCourse,
        obstacle: AgilityObstacle,
        ordered: Boolean,
    ) {
        if (!ordered) {
            if (obstacle.awardBaseWhenUnordered && obstacle.baseXp > 0.0) {
                statAdvance(AGILITY_STAT, obstacle.baseXp)
            } else {
                mes("Complete every obstacle in order to receive the course completion experience.")
            }
            return
        }
        val agilityXp = obstacle.baseXp + obstacle.lapBonusXp
        if (agilityXp > 0.0) statAdvance(AGILITY_STAT, agilityXp)
        if (obstacle.strengthBonusXp > 0.0) statAdvance(STRENGTH_STAT, obstacle.strengthBonusXp)
        spam("You complete a lap of the ${course.displayName}.")
        rollMarkOfGrace(course)
    }

    private fun ProtectedAccess.rollMarkOfGrace(course: AgilityCourse) {
        if (course.markNumerator <= 0 || course.markSpawns.isEmpty()) return
        val identity = player.displayName.lowercase()
        val currentCycle = player.currentMapClock
        val lastCycle = lastMarkCycleByPlayer[identity]
        if (lastCycle != null && currentCycle - lastCycle < MARK_COOLDOWN_TICKS) return

        var success = random.of(course.markDenominator) < course.markNumerator
        val overLevelled = stat(AGILITY_STAT) >= course.requiredLevel + 20
        if (success && overLevelled && course.key != "canifis") success = random.of(5) == 0
        if (!success) return

        val spawn = course.markSpawns[random.of(course.markSpawns.size)]
        objects.add(
            MARK_OF_GRACE,
            spawn,
            duration = MARK_DESPAWN_TICKS,
            receiver = player,
            reveal = Int.MAX_VALUE,
        )
        lastMarkCycleByPlayer[identity] = currentCycle
        mes("A mark of grace has appeared somewhere on the course.")
    }

    private fun ProtectedAccess.restoreRooftopEnergy() {
        val restore = 10 + random.of(11)
        val updated = (player.runEnergy + restore).coerceAtMost(constants.run_max_energy)
        if (updated == player.runEnergy) return
        player.runEnergy = updated
        UpdateRun.energy(player, updated)
    }

    private fun isOrdered(
        player: Player,
        course: AgilityCourse,
        obstacle: AgilityObstacle,
    ): Boolean {
        if (obstacle.stage == 1) return true
        val state = progress[player] ?: return false
        return state.courseKey == course.key && state.stage == obstacle.stage - 1
    }

    private fun relativeDestination(start: CoordGrid, movement: AgilityMovement): CoordGrid =
        CoordGrid(
            start.x + movement.deltaX,
            start.z + movement.deltaZ,
            start.level + movement.deltaLevel,
        )

    /** Exact-move direction is the arrival facing; use real compass directions. */
    private fun facing(start: CoordGrid, end: CoordGrid): Int {
        val dx = end.x - start.x
        val dz = end.z - start.z
        return when {
            dx < 0 && dz < 0 -> constants.em_face_southwest
            dx < 0 && dz > 0 -> constants.em_face_northwest
            dx > 0 && dz < 0 -> constants.em_face_southeast
            dx > 0 && dz > 0 -> constants.em_face_northeast
            dx < 0 -> constants.em_face_west
            dx > 0 -> constants.em_face_east
            dz < 0 -> constants.em_face_south
            else -> constants.em_face_north
        }
    }

    private fun AgilityRouteFacing.toDirectionConstant(): Int =
        when (this) {
            AgilityRouteFacing.North -> constants.em_face_north
            AgilityRouteFacing.NorthEast -> constants.em_face_northeast
            AgilityRouteFacing.East -> constants.em_face_east
            AgilityRouteFacing.SouthEast -> constants.em_face_southeast
            AgilityRouteFacing.South -> constants.em_face_south
            AgilityRouteFacing.SouthWest -> constants.em_face_southwest
            AgilityRouteFacing.West -> constants.em_face_west
            AgilityRouteFacing.NorthWest -> constants.em_face_northwest
        }

    private data class CourseProgress(val courseKey: String, val stage: Int)

    private companion object {
        private val log = LoggerFactory.getLogger(AgilityScript::class.java)
        private const val AGILITY_STAT = "stat.agility"
        private const val STRENGTH_STAT = "stat.strength"
        private const val CLIENT_CYCLES_PER_TICK = 30
        private const val AGILITY_APPROACH_RANGE = 5
        private const val MARK_COOLDOWN_TICKS = 300
        private const val MARK_DESPAWN_TICKS = 1_000
        private val MARK_OF_GRACE = ItemServerType(11849)
        private val COURSE_RENDER_BAS =
            AgilityRenderStyle.values().associateWith { style ->
                BasType(
                    readyAnim = style.readyAnimation,
                    turnOnSpot = style.readyAnimation,
                    walkForward = style.movementAnimation,
                    walkBack = style.movementAnimation,
                    walkLeft = style.movementAnimation,
                    walkRight = style.movementAnimation,
                    running = style.movementAnimation,
                )
            }
    }
}
