package org.rsmod.content.bosses.yama

import kotlin.math.abs
import org.rsmod.map.CoordGrid

/** Capture-backed geometry and timing rules shared by the live Yama encounter and tests. */
internal object YamaHazardRules {
    const val ARENA_MIN_X = 17
    const val ARENA_MAX_X = 44
    const val ARENA_MIN_Z = 20
    const val ARENA_MAX_Z = 45
    const val FIRE_FIRST_STEP = 1
    const val FIRE_STEPS_PER_TICK = 2
    const val SHADOW_WAVE_STEPS_PER_TICK = 2
    const val SHADOW_WAVE_FIRST_CLIENT_DELAY = 120
    const val SHADOW_WAVE_STEP_CLIENT_DELAY = 15
    const val SHADOW_CRASH_CARDINAL_RADIUS = 3
    const val SHADOW_CRASH_DIAGONAL_RADIUS = 2

    enum class HorizontalDirection(val deltaX: Int) {
        EAST(1),
        WEST(-1),
    }

    enum class WaveOrigin(val localX: Int, val localZ: Int, val graphic: Int) {
        SOUTH_WEST(18, 22, YamaAssets.GFX_SHADOW_WALL_MIDDLE),
        SOUTH_EAST(41, 22, YamaAssets.GFX_SHADOW_WALL_END),
    }

    enum class CrashOrientation {
        VERTICAL,
        DIAGONAL_DESCENDING,
        HORIZONTAL,
        DIAGONAL_ASCENDING,
    }

    enum class MovingFrontCollision {
        NONE,
        SKIPPED,
        HIT,
    }

    data class ShadowVisual(val tile: CoordGrid, val clientDelay: Int)

    fun fireFrontLocalX(startLocalX: Int, direction: HorizontalDirection, activeTick: Int): Int {
        require(activeTick >= 0)
        val distance = FIRE_FIRST_STEP + activeTick * FIRE_STEPS_PER_TICK
        return startLocalX + direction.deltaX * distance
    }

    fun fireFrontTiles(
        startLocalX: Int,
        localZ: Int,
        direction: HorizontalDirection,
        activeTick: Int,
    ): IntRange {
        val leading = fireFrontLocalX(startLocalX, direction, activeTick)
        val trailing = leading - direction.deltaX
        return minOf(leading, trailing)..maxOf(leading, trailing)
    }

    fun occupiedRectangleDistance(southWest: CoordGrid, size: Int, tile: CoordGrid): Int {
        if (southWest.level != tile.level) return Int.MAX_VALUE
        val edge = size.coerceAtLeast(1) - 1
        val minX = southWest.x
        val maxX = southWest.x + edge
        val minZ = southWest.z
        val maxZ = southWest.z + edge
        val dx = if (tile.x < minX) minX - tile.x else if (tile.x > maxX) tile.x - maxX else 0
        val dz = if (tile.z < minZ) minZ - tile.z else if (tile.z > maxZ) tile.z - maxZ else 0
        return maxOf(dx, dz)
    }

    fun insideSquare(centre: CoordGrid, tile: CoordGrid, radius: Int): Boolean {
        require(radius >= 0) { "Square radius cannot be negative." }
        return centre.level == tile.level &&
            abs(centre.x - tile.x) <= radius &&
            abs(centre.z - tile.z) <= radius
    }

    fun meteorParticipantsAtCast(
        centre: CoordGrid,
        participantStartTiles: Map<Long, CoordGrid>,
    ): Map<Long, CoordGrid> =
        participantStartTiles.filterValues { insideSquare(centre, it, radius = 2) }

    fun shadowWaveDistance(origin: WaveOrigin, localX: Int, localZ: Int): Int =
        abs(localX - origin.localX) + (localZ - origin.localZ)

    fun shadowWaveArrivalTick(origin: WaveOrigin, localX: Int, localZ: Int): Int {
        val distance = shadowWaveDistance(origin, localX, localZ)
        return if (distance < 0) Int.MAX_VALUE else distance / SHADOW_WAVE_STEPS_PER_TICK
    }

    fun shadowWaveClientDelay(origin: WaveOrigin, localX: Int, localZ: Int): Int {
        val distance = shadowWaveDistance(origin, localX, localZ)
        return SHADOW_WAVE_FIRST_CLIENT_DELAY +
            distance.coerceAtLeast(0) * SHADOW_WAVE_STEP_CLIENT_DELAY
    }

    /**
     * Resolves a moving front against the exact tiles traversed during this server tick. Merely
     * changing tiles is not immunity: a skip requires a two-step move whose intermediate tile
     * passes through the front while both the starting and landing tiles remain clear.
     */
    fun movingFrontCollision(
        traversedTiles: List<CoordGrid>,
        movementSteps: Int,
        frontTiles: Set<CoordGrid>,
    ): MovingFrontCollision {
        if (traversedTiles.isEmpty() || frontTiles.isEmpty()) return MovingFrontCollision.NONE
        val contacts = traversedTiles.withIndex().filter { it.value in frontTiles }
        if (contacts.isEmpty()) return MovingFrontCollision.NONE
        val lastIndex = traversedTiles.lastIndex
        val crossedOnlyBetweenEndpoints =
            movementSteps >= 2 &&
                traversedTiles.size >= 3 &&
                contacts.all { it.index in 1 until lastIndex }
        return if (crossedOnlyBetweenEndpoints) {
            MovingFrontCollision.SKIPPED
        } else {
            MovingFrontCollision.HIT
        }
    }

    fun crashSequence(firstVertical: Boolean, sets: Int): List<CrashOrientation> {
        if (sets <= 0) return emptyList()
        val base =
            if (firstVertical) {
                listOf(
                    CrashOrientation.VERTICAL,
                    CrashOrientation.DIAGONAL_DESCENDING,
                    CrashOrientation.HORIZONTAL,
                )
            } else {
                listOf(
                    CrashOrientation.HORIZONTAL,
                    CrashOrientation.DIAGONAL_ASCENDING,
                    CrashOrientation.VERTICAL,
                )
            }
        return List(sets) { base[it % base.size] }
    }

    fun crashImpactMarkers(centre: CoordGrid, orientation: CrashOrientation): Set<CoordGrid> {
        val (dx, dz, radius) =
            when (orientation) {
                CrashOrientation.VERTICAL -> Triple(0, 1, SHADOW_CRASH_CARDINAL_RADIUS)
                CrashOrientation.HORIZONTAL -> Triple(1, 0, SHADOW_CRASH_CARDINAL_RADIUS)
                CrashOrientation.DIAGONAL_DESCENDING -> Triple(1, -1, SHADOW_CRASH_DIAGONAL_RADIUS)
                CrashOrientation.DIAGONAL_ASCENDING -> Triple(1, 1, SHADOW_CRASH_DIAGONAL_RADIUS)
            }
        return setOf(
            centre,
            centre.translate(dx * radius, dz * radius),
            centre.translate(-dx * radius, -dz * radius),
        )
    }

    fun crashDamageTiles(centre: CoordGrid, orientation: CrashOrientation): Set<CoordGrid> =
        buildSet {
            val (dx, dz, radius) =
                when (orientation) {
                    CrashOrientation.VERTICAL -> Triple(0, 1, SHADOW_CRASH_CARDINAL_RADIUS)
                    CrashOrientation.HORIZONTAL -> Triple(1, 0, SHADOW_CRASH_CARDINAL_RADIUS)
                    CrashOrientation.DIAGONAL_DESCENDING ->
                        Triple(1, -1, SHADOW_CRASH_DIAGONAL_RADIUS)
                    CrashOrientation.DIAGONAL_ASCENDING ->
                        Triple(1, 1, SHADOW_CRASH_DIAGONAL_RADIUS)
                }
            for (offset in -radius..radius) add(centre.translate(dx * offset, dz * offset))
        }

    fun isOneTilePerpendicularDodge(
        centre: CoordGrid,
        player: CoordGrid,
        orientation: CrashOrientation,
    ): Boolean {
        val dx = player.x - centre.x
        val dz = player.z - centre.z
        return when (orientation) {
            CrashOrientation.VERTICAL -> abs(dx) == 1 && dz == 0
            CrashOrientation.HORIZONTAL -> dx == 0 && abs(dz) == 1
            CrashOrientation.DIAGONAL_DESCENDING -> abs(dx) == 1 && dx == dz
            CrashOrientation.DIAGONAL_ASCENDING -> abs(dx) == 1 && dx == -dz
        }
    }

    /**
     * Builds the capture-shaped bow-tie: cardinal drops use 7/5/3-wide wings with 8/15/23/30
     * client-cycle delays; diagonal drops use five converging tiers at 6-cycle steps.
     */
    fun crashShadowVisuals(centre: CoordGrid, orientation: CrashOrientation): List<ShadowVisual> {
        val visuals = linkedMapOf<CoordGrid, Int>()
        visuals[centre] = 30
        when (orientation) {
            CrashOrientation.VERTICAL,
            CrashOrientation.HORIZONTAL -> {
                val vertical = orientation == CrashOrientation.VERTICAL
                for (sign in listOf(-1, 1)) {
                    for (distance in 1..3) {
                        val delay =
                            when (distance) {
                                1 -> 23
                                2 -> 15
                                else -> 8
                            }
                        for (width in -distance..distance) {
                            val tile =
                                if (vertical) centre.translate(width, sign * distance)
                                else centre.translate(sign * distance, width)
                            visuals[tile] = minOf(visuals[tile] ?: Int.MAX_VALUE, delay)
                        }
                    }
                }
            }
            CrashOrientation.DIAGONAL_DESCENDING,
            CrashOrientation.DIAGONAL_ASCENDING -> {
                val axisZ = if (orientation == CrashOrientation.DIAGONAL_DESCENDING) -1 else 1
                for (sign in listOf(-1, 1)) {
                    for (distance in 1..5) {
                        val delay = (30 - distance * 6).coerceAtLeast(0)
                        for (width in 0 until distance) {
                            val axisX = sign * distance
                            val axisDeltaZ = sign * axisZ * distance
                            val perpendicularX = if (sign > 0) -width else width
                            val perpendicularZ = if (sign > 0) -axisZ * width else axisZ * width
                            val tile =
                                centre.translate(
                                    axisX + perpendicularX,
                                    axisDeltaZ + perpendicularZ,
                                )
                            visuals[tile] = minOf(visuals[tile] ?: Int.MAX_VALUE, delay)
                        }
                    }
                }
            }
        }
        return visuals.map { (tile, delay) -> ShadowVisual(tile, delay) }
    }
}
