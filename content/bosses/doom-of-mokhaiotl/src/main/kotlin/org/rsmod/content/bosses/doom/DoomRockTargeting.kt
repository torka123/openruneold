package org.rsmod.content.bosses.doom

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.map.CoordGrid

internal data class DoomRockShadowPlan(val target: CoordGrid, val tiles: List<CoordGrid>)

internal object DoomRockTargeting {
    /** Captures split the large boulder halfway from its snapshotted target back toward Doom. */
    fun splitTile(playerSnapshot: CoordGrid, bossCentre: CoordGrid): CoordGrid {
        require(playerSnapshot.level == bossCentre.level)
        val distance =
            max(abs(playerSnapshot.x - bossCentre.x), abs(playerSnapshot.z - bossCentre.z))
        val steps = distance / 2
        return playerSnapshot.translate(
            bossCentre.x.compareTo(playerSnapshot.x) * steps,
            bossCentre.z.compareTo(playerSnapshot.z) * steps,
        )
    }

    fun targetAtDetonation(
        projectileImpactTile: CoordGrid,
        playerTileAtDetonation: CoordGrid,
    ): CoordGrid {
        require(projectileImpactTile.level == playerTileAtDetonation.level)
        // The large projectile still splits at its fixed impact coordinate, but captures show
        // the one debris fragment that becomes permanent cover targets the tile occupied by the
        // player on the split tick.
        return playerTileAtDetonation
    }

    /**
     * Selects one complete debris set without reusing a tile from an earlier delve-eight throw.
     * Candidate order is deliberately deterministic; the encounter may randomize before calling.
     */
    fun plan(
        playerAtDetonation: CoordGrid,
        candidates: Iterable<CoordGrid>,
        reserved: Set<CoordGrid> = emptySet(),
        debrisCount: Int = DoomRules.ROCK_DEBRIS_COUNT,
    ): DoomRockShadowPlan {
        return requireNotNull(tryPlan(playerAtDetonation, candidates, reserved, debrisCount)) {
            "Not enough unique Doom shadow tiles for a $debrisCount-tile plan."
        }
    }

    /**
     * Returns a complete debris plan, or null when the supplied tiles cannot safely satisfy it.
     * Invalid counts remain programmer errors; runtime map congestion is an expected failure mode.
     */
    fun tryPlan(
        playerAtDetonation: CoordGrid,
        candidates: Iterable<CoordGrid>,
        reserved: Set<CoordGrid> = emptySet(),
        debrisCount: Int = DoomRules.ROCK_DEBRIS_COUNT,
    ): DoomRockShadowPlan? {
        require(debrisCount > 0)
        val available =
            sequenceOf(playerAtDetonation)
                .plus(candidates.asSequence())
                .filter { it.level == playerAtDetonation.level && it !in reserved }
                .distinct()
                .take(debrisCount)
                .toList()
        if (available.size != debrisCount) return null
        return DoomRockShadowPlan(target = available.first(), tiles = available)
    }

    /**
     * Follow-up bolts launch from ordinary debris, never from the debris tile that becomes cover.
     */
    fun followupSource(
        debrisTiles: Iterable<CoordGrid>,
        rubbleTile: CoordGrid,
        index: Int,
        fallback: CoordGrid,
    ): CoordGrid {
        require(index >= 0)
        return debrisTiles.asSequence().filter { it != rubbleTile }.elementAtOrNull(index)
            ?: fallback
    }
}
