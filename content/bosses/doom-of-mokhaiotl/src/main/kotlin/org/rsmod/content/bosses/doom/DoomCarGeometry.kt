package org.rsmod.content.bosses.doom

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.map.CoordGrid

/** Pure geometry and exact-movement planning for Doom's five-by-five burrowed form. */
internal object DoomCarGeometry {
    const val BOSS_SIZE: Int = DoomGeometry.BOSS_SIZE
    const val BOSS_HALF_SIZE: Int = DoomGeometry.BOSS_SIZE / 2
    const val EXACT_MOVE_CLIENT_CYCLES: Int = 30
    const val SLAM_RADIUS: Int = 15
    const val SLAM_VISUAL_RADIUS: Int = 16
    const val SLAM_VISUAL_DELAY_OFFSET: Int = 2
    const val SLAM_VISUAL_MAX_CLIENT_DELAY: Int = 13

    fun centre(southWest: CoordGrid): CoordGrid = DoomGeometry.centre(southWest)

    fun footprint(southWest: CoordGrid): Set<CoordGrid> = DoomGeometry.occupied(southWest)

    /** Chebyshev distance from [tile] to the nearest occupied tile of Doom's 5x5 footprint. */
    fun distanceToFootprint(southWest: CoordGrid, tile: CoordGrid): Int =
        DoomGeometry.distanceToFootprint(southWest, tile)

    /**
     * The yellow eye marks Doom's centre. NPC movement, however, is encoded from its south-west
     * tile, so the exact-move destination must be offset by half the five-tile NPC size.
     */
    fun southWestTarget(eyeTile: CoordGrid): CoordGrid =
        eyeTile.translate(-BOSS_HALF_SIZE, -BOSS_HALF_SIZE)

    fun exactMoveSpeed(delve: Int): Int {
        require(delve >= 5) { "Doom's car phase starts at delve 5: $delve" }
        return DoomRules.carTilesPerTick(delve)
    }

    fun plan(
        startSouthWest: CoordGrid,
        eyeTile: CoordGrid,
        delve: Int,
    ): DoomCarPlan {
        require(startSouthWest.level == eyeTile.level) {
            "Doom car movement cannot cross levels: $startSouthWest -> $eyeTile"
        }
        val target = southWestTarget(eyeTile)
        val speed = exactMoveSpeed(delve)
        val path = rasterLine(startSouthWest, target)
        val segments =
            if (path.size <= 1) {
                emptyList()
            } else {
                buildList {
                    var pathIndex = 0
                    while (pathIndex < path.lastIndex) {
                        val nextIndex = (pathIndex + speed).coerceAtMost(path.lastIndex)
                        add(
                            DoomCarExactSegment(
                                startSouthWest = path[pathIndex],
                                endSouthWest = path[nextIndex],
                                clientCycles = EXACT_MOVE_CLIENT_CYCLES,
                            )
                        )
                        pathIndex = nextIndex
                    }
                }
            }
        return DoomCarPlan(
            eyeTile = eyeTile,
            targetSouthWest = target,
            tilesPerTick = speed,
            segments = segments,
        )
    }

    /** Every tile occupied by Doom while its south-west tile follows [segment]. */
    fun sweptFootprint(segment: DoomCarExactSegment): Set<CoordGrid> = buildSet {
        rasterLine(segment.startSouthWest, segment.endSouthWest).forEach { addAll(footprint(it)) }
    }

    fun tramples(segment: DoomCarExactSegment, playerTile: CoordGrid): Boolean =
        playerTile in sweptFootprint(segment)

    fun intersectedRocks(
        segment: DoomCarExactSegment,
        rocks: Collection<CoordGrid>,
    ): Set<CoordGrid> {
        val sweep = sweptFootprint(segment)
        return rocks.filterTo(linkedSetOf()) { it in sweep }
    }

    /** Returns true when [blocker] lies on the client-style sight line before [target]. */
    fun providesDirectionalCover(
        origin: CoordGrid,
        blocker: CoordGrid,
        target: CoordGrid,
    ): Boolean {
        if (origin.level != blocker.level || origin.level != target.level) return false
        if (blocker == origin || blocker == target) return false
        return blocker in rayTiles(origin, target).dropLast(1)
    }

    /** Builds the capture-backed radius-16 visual mask, including rock shadows. */
    fun slamMask(
        origin: CoordGrid,
        rocks: Collection<CoordGrid>,
        arenaTiles: Collection<CoordGrid>,
        visualRadius: Int = SLAM_VISUAL_RADIUS,
    ): DoomCarSlamMask {
        require(visualRadius >= 0) { "Slam visual radius must not be negative: $visualRadius" }
        val candidateTiles =
            arenaTiles
                .asSequence()
                .filter { it.level == origin.level }
                .filter { squaredDistance(origin, it) < visualRadius * visualRadius }
                .toCollection(linkedSetOf())
        val sameLevelRocks = rocks.filterTo(hashSetOf()) { it.level == origin.level }
        if (origin in sameLevelRocks) {
            return DoomCarSlamMask(candidateTiles, emptySet(), candidateTiles)
        }
        val protectedTiles =
            candidateTiles.filterTo(linkedSetOf()) { tile ->
                rayTiles(origin, tile).any(sameLevelRocks::contains)
            }
        return DoomCarSlamMask(
            candidateTiles = candidateTiles,
            exposedTiles = candidateTiles - protectedTiles,
            protectedTiles = protectedTiles,
        )
    }

    /** Client-cycle delay used by captured 3405-3407 radial slam graphics. */
    fun slamPropagationClientDelay(
        origin: CoordGrid,
        tile: CoordGrid,
        maximum: Int = SLAM_VISUAL_MAX_CLIENT_DELAY,
    ): Int {
        require(origin.level == tile.level) { "Slam propagation cannot cross levels." }
        require(maximum >= 0) { "Maximum client delay must not be negative: $maximum" }
        return (chebyshev(origin, tile) - SLAM_VISUAL_DELAY_OFFSET).coerceAtMost(maximum)
    }

    /**
     * Resolves one car slam. A rock on Doom's centre tile absorbs the entire slam and is the only
     * rock destroyed. Otherwise, rocks inside the slam radius are destroyed unless another rock is
     * directionally between that rock and Doom.
     */
    fun resolveSlam(
        bossSouthWest: CoordGrid,
        playerTile: CoordGrid,
        rocks: Collection<CoordGrid>,
        radius: Int = SLAM_RADIUS,
        arenaTiles: Collection<CoordGrid> = emptyList(),
    ): DoomCarSlamResolution {
        require(radius >= 0) { "Slam radius must not be negative: $radius" }
        val origin = centre(bossSouthWest)
        val sameLevelRocks = rocks.filter { it.level == origin.level }
        val mask = slamMask(origin, sameLevelRocks, arenaTiles)
        val centreRock = sameLevelRocks.firstOrNull { it == origin }
        if (centreRock != null) {
            return DoomCarSlamResolution(
                origin = origin,
                centreRock = centreRock,
                playerProtected = true,
                playerWithinWave = playerTile in mask.candidateTiles,
                destroyedRocks = setOf(centreRock),
                exposedTiles = mask.exposedTiles,
                protectedTiles = mask.protectedTiles,
            )
        }

        val relevantRocks = sameLevelRocks.filter { chebyshev(origin, it) <= radius }
        val playerProtected =
            playerTile.level == origin.level &&
                relevantRocks.any { providesDirectionalCover(origin, it, playerTile) }
        val destroyed =
            relevantRocks.filterTo(linkedSetOf()) { candidate ->
                relevantRocks.none { blocker ->
                    blocker != candidate && providesDirectionalCover(origin, blocker, candidate)
                }
            }
        return DoomCarSlamResolution(
            origin = origin,
            centreRock = null,
            playerProtected = playerProtected,
            playerWithinWave = playerTile in mask.candidateTiles,
            destroyedRocks = destroyed,
            exposedTiles = mask.exposedTiles,
            protectedTiles = mask.protectedTiles,
        )
    }

    /** Fixed-point tile traversal mirrored from OpenRune's line-of-sight route finder. */
    private fun rayTiles(start: CoordGrid, end: CoordGrid): List<CoordGrid> {
        require(start.level == end.level) { "Ray cannot cross levels: $start -> $end" }
        if (start == end) return emptyList()
        val deltaX = end.x - start.x
        val deltaZ = end.z - start.z
        val travelEast = deltaX >= 0
        val travelNorth = deltaZ >= 0
        return buildList {
            if (abs(deltaX) > abs(deltaZ)) {
                val offsetX = if (travelEast) 1 else -1
                val offsetZ = if (travelNorth) 0 else -1
                var scaledZ = scaleUp(start.z) + HALF_TILE + offsetZ
                val tangent = scaleUp(deltaZ) / abs(deltaX)
                var x = start.x
                while (x != end.x) {
                    x += offsetX
                    val z = scaleDown(scaledZ)
                    add(CoordGrid(x, z, start.level))
                    scaledZ += tangent
                    val nextZ = scaleDown(scaledZ)
                    if (nextZ != z) add(CoordGrid(x, nextZ, start.level))
                }
            } else {
                val offsetX = if (travelEast) 0 else -1
                val offsetZ = if (travelNorth) 1 else -1
                var scaledX = scaleUp(start.x) + HALF_TILE + offsetX
                val tangent = scaleUp(deltaX) / abs(deltaZ)
                var z = start.z
                while (z != end.z) {
                    z += offsetZ
                    val x = scaleDown(scaledX)
                    add(CoordGrid(x, z, start.level))
                    scaledX += tangent
                    val nextX = scaleDown(scaledX)
                    if (nextX != x) add(CoordGrid(nextX, z, start.level))
                }
            }
        }
    }

    private fun rasterLine(start: CoordGrid, end: CoordGrid): List<CoordGrid> {
        require(start.level == end.level) { "Line cannot cross levels: $start -> $end" }
        val deltaX = end.x - start.x
        val deltaZ = end.z - start.z
        val steps = max(abs(deltaX), abs(deltaZ))
        if (steps == 0) return listOf(start)
        return List(steps + 1) { step ->
            CoordGrid(
                start.x + roundedRatio(deltaX, step, steps),
                start.z + roundedRatio(deltaZ, step, steps),
                start.level,
            )
        }
    }

    private fun roundedRatio(delta: Int, step: Int, steps: Int): Int {
        val numerator = delta * step
        return if (numerator >= 0) {
            (numerator + steps / 2) / steps
        } else {
            -((-numerator + steps / 2) / steps)
        }
    }

    private fun squaredDistance(a: CoordGrid, b: CoordGrid): Int {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private fun chebyshev(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    private fun scaleUp(tiles: Int): Int = tiles shl FIXED_POINT_SHIFT

    private fun scaleDown(tiles: Int): Int = tiles ushr FIXED_POINT_SHIFT

    private const val FIXED_POINT_SHIFT = 16
    private const val HALF_TILE = 1 shl (FIXED_POINT_SHIFT - 1)
}

internal data class DoomCarPlan(
    val eyeTile: CoordGrid,
    val targetSouthWest: CoordGrid,
    val tilesPerTick: Int,
    val segments: List<DoomCarExactSegment>,
)

internal data class DoomCarExactSegment(
    val startSouthWest: CoordGrid,
    val endSouthWest: CoordGrid,
    val clientCycles: Int,
) {
    val distance: Int =
        max(abs(endSouthWest.x - startSouthWest.x), abs(endSouthWest.z - startSouthWest.z))
}

/** Resolves trample collision from the player's tile when an exact-move segment arrives. */
internal object DoomCarTrampleRules {
    fun shouldHitAtArrival(
        segment: DoomCarExactSegment,
        impactCycle: Int,
        currentCycle: Int,
        playerTile: CoordGrid,
        alreadyTrampled: Boolean,
    ): Boolean =
        !alreadyTrampled &&
            currentCycle >= impactCycle &&
            DoomCarGeometry.tramples(segment, playerTile)
}

internal data class DoomCarSlamResolution(
    val origin: CoordGrid,
    val centreRock: CoordGrid?,
    val playerProtected: Boolean,
    val playerWithinWave: Boolean,
    val destroyedRocks: Set<CoordGrid>,
    val exposedTiles: Set<CoordGrid> = emptySet(),
    val protectedTiles: Set<CoordGrid> = emptySet(),
) {
    /** Damage uses the authoritative surviving-rubble result, not the presentation mask. */
    val damagesPlayer: Boolean
        get() = centreRock == null && playerWithinWave && !playerProtected
}

internal data class DoomCarSlamMask(
    val candidateTiles: Set<CoordGrid>,
    val exposedTiles: Set<CoordGrid>,
    val protectedTiles: Set<CoordGrid>,
)
