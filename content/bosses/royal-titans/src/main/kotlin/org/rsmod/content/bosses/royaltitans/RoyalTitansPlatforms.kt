package org.rsmod.content.bosses.royaltitans

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.route.RouteFactory
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native route/arrival adapter for the platform phases declared in RoyalTitansTitanSpec. */
internal class RoyalTitansPlatforms
@Inject
constructor(
    private val clock: MapClock,
    collision: CollisionFlagMap,
    private val random: GameRandom,
    private val phases: RoyalTitansPhaseProgram,
    private val arena: RoyalTitansArena,
    private val walls: RoyalTitansWalls,
    private val elementals: RoyalTitansElementals,
    private val tileAttacks: RoyalTitansTileAttacks,
) {
    private val routes = RouteFactory(collision)

    fun tick(run: Run) {
        val target = arena.online(run).firstOrNull { it.hitpoints > 0 } ?: return
        listOfNotNull(run.branda, run.eldric).forEach { titan ->
            if (titan.hitpoints <= 0) return@forEach
            phases.retarget(titan, target)
            val movement = phases.movement(titan)
            val element = if (titan === run.branda) RoyalElement.FIRE else RoyalElement.ICE
            val outerX = if (element == RoyalElement.FIRE) 24 else 36
            val innerX = if (element == RoyalElement.FIRE) 26 else 34
            if (movement.phase == RoyalTitanMovementPhase.WALKING) {
                if (movement.arrivalCycle == null) {
                    if (
                        !titan.movementLocked &&
                            !titan.isBound &&
                            titan.coords == titan.previousCoords
                    ) {
                        movement.destination?.let {
                            walkTitan(run, titan, movement, it, movement.afterArrival)
                        }
                    }
                }
                return@forEach
            }
            if (movement.phase == RoyalTitanMovementPhase.ARRIVAL_READY)
                movement.phase = movement.afterArrival
            when (movement.phase) {
                RoyalTitanMovementPhase.FACE_INWARD -> {
                    titan.faceSquare(
                        titan.coords.translate(if (element == RoyalElement.FIRE) 6 else -6, 0)
                    )
                    movement.phase = RoyalTitanMovementPhase.ARRIVAL_ACTION_WAIT
                }
                RoyalTitanMovementPhase.SPAWN,
                RoyalTitanMovementPhase.TURN_OUTWARD -> {
                    titan.clearInteraction()
                    titan.mode = NpcMode.None
                    titan.faceSquare(
                        titan.coords.translate(if (element == RoyalElement.FIRE) -6 else 6, 0)
                    )
                    movement.rangedVulnerable = true
                    movement.phase = RoyalTitanMovementPhase.RETREAT_WAIT
                }
                RoyalTitanMovementPhase.RETREAT ->
                    walkTitan(
                        run,
                        titan,
                        movement,
                        arena.local(run, outerX, arena.localZ(run, titan.coords)),
                        RoyalTitanMovementPhase.FIRST_EDGE_ACTION,
                    )
                RoyalTitanMovementPhase.FIRST_EDGE_ACTION -> {
                    if (!consumeReadySummon(run, titan, movement, element))
                        walls.castWall(run, titan, element)
                    movement.phase = RoyalTitanMovementPhase.PATROL_WAIT
                }
                RoyalTitanMovementPhase.PATROL -> {
                    val row =
                        RoyalTitansUnverifiedMovementSelection.patrolRow(
                            arena.localZ(run, titan.coords)
                        ) {
                            random.of(it)
                        }
                    walkTitan(
                        run,
                        titan,
                        movement,
                        arena.local(run, outerX, row),
                        RoyalTitanMovementPhase.SECOND_EDGE_ACTION,
                    )
                }
                RoyalTitanMovementPhase.SECOND_EDGE_ACTION -> {
                    if (!consumeReadySummon(run, titan, movement, element))
                        walls.castWall(run, titan, element)
                    movement.phase = RoyalTitanMovementPhase.RETURN_WAIT
                }
                RoyalTitanMovementPhase.RETURN -> {
                    val meleeTarget = arena.targetFor(run, titan) ?: return@forEach
                    val row =
                        RoyalTitansUnverifiedMovementSelection.returnRow(
                            arena.localZ(run, meleeTarget.coords)
                        ) {
                            random.of(it)
                        }
                    // Snapshot at the turn: 2283 B1347/E1366 keep their route after the player
                    // moves.
                    movement.rangedVulnerable = false
                    walkTitan(
                        run,
                        titan,
                        movement,
                        arena.local(run, innerX, row),
                        RoyalTitanMovementPhase.CLOSE_ACTION,
                    )
                }
                RoyalTitanMovementPhase.CLOSE_ACTION,
                RoyalTitanMovementPhase.CLOSE_SECOND,
                RoyalTitanMovementPhase.CLOSE_THIRD -> {
                    if (!consumeReadySummon(run, titan, movement, element))
                        tileAttacks.stomp(run, titan, element, false)
                    movement.phase =
                        when (movement.phase) {
                            RoyalTitanMovementPhase.CLOSE_ACTION ->
                                RoyalTitanMovementPhase.CLOSE_SECOND_WAIT
                            RoyalTitanMovementPhase.CLOSE_SECOND ->
                                RoyalTitanMovementPhase.CLOSE_THIRD_WAIT
                            else -> RoyalTitanMovementPhase.TURN_WAIT
                        }
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
        elementals.scheduleSummon(run, titan, element)
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
                    arena.isPresent(run, titan) &&
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
}
