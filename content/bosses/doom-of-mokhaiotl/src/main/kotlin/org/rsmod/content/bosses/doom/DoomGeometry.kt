package org.rsmod.content.bosses.doom

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.map.CoordGrid

/**
 * The single geometry authority for Doom and every mechanic which touches its footprint.
 *
 * NPC coordinates are the south-west tile. Doom's normal, shielded and burrowed definitions all
 * occupy five by five tiles; callers may pass another size only for auxiliary NPCs.
 */
internal object DoomGeometry {
    const val BOSS_SIZE = 5

    fun occupied(southWest: CoordGrid, size: Int = BOSS_SIZE): Set<CoordGrid> {
        require(size > 0)
        return buildSet(size * size) {
            repeat(size) { dx -> repeat(size) { dz -> add(southWest.translate(dx, dz)) } }
        }
    }

    fun centre(southWest: CoordGrid, size: Int = BOSS_SIZE): CoordGrid {
        require(size > 0)
        return southWest.translate(size / 2, size / 2)
    }

    fun contains(southWest: CoordGrid, tile: CoordGrid, size: Int = BOSS_SIZE): Boolean =
        tile.level == southWest.level &&
            tile.x in southWest.x until (southWest.x + size) &&
            tile.z in southWest.z until (southWest.z + size)

    fun distanceToFootprint(southWest: CoordGrid, tile: CoordGrid, size: Int = BOSS_SIZE): Int {
        if (tile.level != southWest.level) return Int.MAX_VALUE
        val east = southWest.x + size - 1
        val north = southWest.z + size - 1
        val dx =
            when {
                tile.x < southWest.x -> southWest.x - tile.x
                tile.x > east -> tile.x - east
                else -> 0
            }
        val dz =
            when {
                tile.z < southWest.z -> southWest.z - tile.z
                tile.z > north -> tile.z - north
                else -> 0
            }
        return max(dx, dz)
    }

    /** One-tile contact ring around the complete footprint, including diagonal contact. */
    fun contactTiles(southWest: CoordGrid, size: Int = BOSS_SIZE): Set<CoordGrid> {
        require(size > 0)
        val minX = southWest.x - 1
        val maxX = southWest.x + size
        val minZ = southWest.z - 1
        val maxZ = southWest.z + size
        return buildSet(size * 4 + 4) {
            for (x in minX..maxX) {
                add(CoordGrid(x, minZ, southWest.level))
                add(CoordGrid(x, maxZ, southWest.level))
            }
            for (z in (minZ + 1) until maxZ) {
                add(CoordGrid(minX, z, southWest.level))
                add(CoordGrid(maxX, z, southWest.level))
            }
        }
    }

    fun closestContact(
        southWest: CoordGrid,
        from: CoordGrid,
        size: Int = BOSS_SIZE,
        isOpen: (CoordGrid) -> Boolean,
    ): CoordGrid? =
        contactTiles(southWest, size)
            .asSequence()
            .filter(isOpen)
            .minWithOrNull(
                compareBy<CoordGrid>(
                    { max(abs(it.x - from.x), abs(it.z - from.z)) },
                    { abs(it.x - from.x) + abs(it.z - from.z) },
                    { it.x },
                    { it.z },
                )
            )

    fun intersects(
        area: Collection<CoordGrid>,
        southWest: CoordGrid,
        size: Int = BOSS_SIZE,
    ): Boolean = area.any { contains(southWest, it, size) }

    fun sweptFootprint(anchors: Iterable<CoordGrid>, size: Int = BOSS_SIZE): Set<CoordGrid> =
        buildSet {
            anchors.forEach { addAll(occupied(it, size)) }
        }
}

/** Capture-backed floor bounds and packet delays for Doom's arena-wide shockwave. */
internal object DoomShockwaveGeometry {
    private const val MIN_X_FROM_BOSS = -10
    private const val MAX_X_FROM_BOSS = 14
    private const val MIN_Z_FROM_BOSS = -10
    private const val MAX_Z_FROM_BOSS = 14
    private const val VISUAL_RADIUS_SQUARED = 16 * 16

    /** A centred capture emits 613 tiles: the 25x25 arena clipped by a strict radius-16 circle. */
    fun candidateTiles(bossSouthWest: CoordGrid): List<CoordGrid> {
        val centre = DoomGeometry.centre(bossSouthWest)
        return buildList {
            for (dx in MIN_X_FROM_BOSS..MAX_X_FROM_BOSS) {
                for (dz in MIN_Z_FROM_BOSS..MAX_Z_FROM_BOSS) {
                    val tile = bossSouthWest.translate(dx, dz)
                    val centreX = tile.x - centre.x
                    val centreZ = tile.z - centre.z
                    if (centreX * centreX + centreZ * centreZ < VISUAL_RADIUS_SQUARED) add(tile)
                }
            }
        }
    }

    /** Captured client delay is the centre-relative Chebyshev ring minus two cycles. */
    fun clientDelay(bossSouthWest: CoordGrid, tile: CoordGrid): Int =
        max(
            abs(tile.x - DoomGeometry.centre(bossSouthWest).x),
            abs(tile.z - DoomGeometry.centre(bossSouthWest).z),
        ) - 2
}
