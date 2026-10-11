package org.rsmod.content.bosses.royaltitans

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.MapClock
import org.rsmod.map.CoordGrid

/** Native route observations; phase identity/readiness belong to the actor's BossEncounter. */
internal class RoyalTitanMovement(
    private val clock: MapClock,
    private val encounter: BossEncounter,
) {
    var phase: RoyalTitanMovementPhase
        get() = RoyalTitanMovementPhase.entries.first { it.key == encounter.currentPhaseName }
        set(value) {
            // Align a post-input/NPC/LateCycle observation with the native pre-input phase origin.
            encounter.transitionTo(value.key, clock.cycle - 1)
        }

    var destination: CoordGrid? = null
    var arrivalCycle: Int? = null
    var afterArrival = RoyalTitanMovementPhase.FIRST_EDGE_ACTION
    var summonPending = false
    var rangedVulnerable = false

    fun beginRoute(destination: CoordGrid, afterArrival: RoyalTitanMovementPhase) {
        this.destination = destination
        this.afterArrival = afterArrival
        arrivalCycle = null
        phase = RoyalTitanMovementPhase.WALKING
    }

    fun arrive(actual: CoordGrid, cycle: Int) {
        // Native callbacks can also follow a cancelled/aborted route. That is not arrival.
        if (phase == RoyalTitanMovementPhase.WALKING && actual == destination) {
            arrivalCycle = cycle
            // Route completion can run in native post-tick before the next pre-input pass.
            // Own the arrival deadline here, not one frame later in the presentation adapter.
            encounter.transitionTo(RoyalTitanMovementPhase.ARRIVAL_FACE_WAIT.key, cycle - 1)
        }
    }
}

internal enum class RoyalTitanMovementPhase {
    SPAWN_WAIT,
    SPAWN,
    RETREAT_WAIT,
    RETREAT,
    WALKING,
    ARRIVAL_FACE_WAIT,
    FACE_INWARD,
    ARRIVAL_ACTION_WAIT,
    ARRIVAL_READY,
    FIRST_EDGE_ACTION,
    PATROL_WAIT,
    PATROL,
    SECOND_EDGE_ACTION,
    RETURN_WAIT,
    RETURN,
    CLOSE_ACTION,
    CLOSE_SECOND_WAIT,
    CLOSE_SECOND,
    CLOSE_THIRD_WAIT,
    CLOSE_THIRD,
    TURN_WAIT,
    TURN_OUTWARD;

    val key: String
        get() = "platform-${name.lowercase()}"
}

/**
 * Unresolved selection policies are deliberately named. Captures prove the movement envelope, not
 * uniform patrol weighting, melee alignment tie breaking, or a fixed close-action budget. These
 * policies must not be described as OSRS 1:1; timings and arrival semantics are separate.
 */
internal object RoyalTitansUnverifiedMovementSelection {
    fun patrolRow(current: Int, nextInt: (Int) -> Int): Int {
        val candidates = (25..35).filter { it != current }
        return candidates[nextInt(candidates.size)]
    }

    fun returnRow(playerRow: Int, nextInt: (Int) -> Int): Int =
        (playerRow - 1 - nextInt(2)).coerceIn(25, 35)

    // Captured candidates, not a claim that the capture exhausts every OSRS spawn candidate.
    fun packStart(element: RoyalElement, arena: Boolean, nextInt: (Int) -> Int): Pair<Int, Int> =
        if (arena) {
            (29 + nextInt(5)) to if (element == RoyalElement.FIRE) 32 else 28
        } else {
            val observed =
                if (element == RoyalElement.FIRE) listOf(29 to 35, 29 to 33)
                else listOf(32 to 27, 29 to 25)
            observed[nextInt(observed.size)]
        }
}
