package org.rsmod.content.bosses.royaltitans

import org.rsmod.map.CoordGrid

/** Content readiness, not an alternative movement engine. Every step is processed natively. */
internal class RoyalTitanMovement(spawnCycle: Int) {
    var phase = RoyalTitanMovementPhase.SPAWN
    var readyCycle = spawnCycle + 3
    var destination: CoordGrid? = null
    var arrivalCycle: Int? = null
    var afterArrival = RoyalTitanMovementPhase.FIRST_EDGE_ACTION
    var closeActions = 0
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
        if (phase == RoyalTitanMovementPhase.WALKING && actual == destination) arrivalCycle = cycle
    }
}

internal enum class RoyalTitanMovementPhase {
    SPAWN,
    RETREAT,
    WALKING,
    FACE_INWARD,
    FIRST_EDGE_ACTION,
    PATROL,
    SECOND_EDGE_ACTION,
    RETURN,
    CLOSE_ACTION,
    TURN_OUTWARD,
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

    // Retain the previous implementation's budget until the choosing rule is established.
    const val CLOSE_ACTIONS = 3

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
