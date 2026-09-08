package org.rsmod.content.minigames.corruptedgauntlet

internal object CorruptedGauntletLayoutGenerator {
    private val bossPoint = CorruptedGauntletGridPoint(3, 3)
    private val startCandidates =
        listOf(
            CorruptedGauntletGridPoint(3, 2),
            CorruptedGauntletGridPoint(4, 3),
            CorruptedGauntletGridPoint(3, 4),
            CorruptedGauntletGridPoint(2, 3),
        )

    /** Twelve middle positions on the outer edges are eligible for the six demi-boss rooms. */
    private val demiBossCandidates = buildList {
        for (axis in 2..4) {
            add(CorruptedGauntletGridPoint(axis, 0))
            add(CorruptedGauntletGridPoint(6, axis))
            add(CorruptedGauntletGridPoint(axis, 6))
            add(CorruptedGauntletGridPoint(0, axis))
        }
    }

    fun generate(seed: Long): CorruptedGauntletLayout {
        val random = StableRandom(seed)
        val start = startCandidates[random.nextInt(startCandidates.size)]
        val demiLocations = demiBossCandidates.shuffled(random).take(6)
        val demiAssignments =
            listOf(
                    CorruptedGauntletDemiBoss.BEAR,
                    CorruptedGauntletDemiBoss.BEAR,
                    CorruptedGauntletDemiBoss.DRAGON,
                    CorruptedGauntletDemiBoss.DRAGON,
                    CorruptedGauntletDemiBoss.DARK_BEAST,
                    CorruptedGauntletDemiBoss.DARK_BEAST,
                )
                .shuffled(random)
        val demiByPoint = demiLocations.zip(demiAssignments).toMap()
        val fishGuarantee =
            startCandidates.filterNot { it == start }.minBy { stableRoomScore(seed, it) }

        val rooms =
            buildList(CorruptedGauntletConfig.GRID_SIZE * CorruptedGauntletConfig.GRID_SIZE) {
                for (z in 0 until CorruptedGauntletConfig.GRID_SIZE) {
                    for (x in 0 until CorruptedGauntletConfig.GRID_SIZE) {
                        val point = CorruptedGauntletGridPoint(x, z)
                        val demiBoss = demiByPoint[point]
                        val kind =
                            when {
                                point == start -> CorruptedGauntletRoomKind.START
                                point == bossPoint -> CorruptedGauntletRoomKind.BOSS
                                demiBoss != null -> CorruptedGauntletRoomKind.DEMI_BOSS
                                random.nextInt(100) < 56 -> CorruptedGauntletRoomKind.RESOURCE
                                else -> CorruptedGauntletRoomKind.COMBAT
                            }
                        val shape = shape(point)
                        add(
                            CorruptedGauntletRoom(
                                grid = point,
                                kind = kind,
                                shape = shape,
                                sourceChunkX = sourceChunkX(kind, shape),
                                sourceChunkZ = sourceChunkZ(kind, random),
                                rotation = rotationFor(point, kind, random),
                                demiBoss = demiBoss,
                                guaranteedFishNodes = if (point == fishGuarantee) 3 else 0,
                            )
                        )
                    }
                }
            }
        return CorruptedGauntletLayout(seed, start, rooms)
    }

    private fun shape(point: CorruptedGauntletGridPoint): CorruptedGauntletRoomShape {
        val xEdge = point.x == 0 || point.x == CorruptedGauntletConfig.GRID_SIZE - 1
        val zEdge = point.z == 0 || point.z == CorruptedGauntletConfig.GRID_SIZE - 1
        return when {
            xEdge && zEdge -> CorruptedGauntletRoomShape.CORNER
            xEdge || zEdge -> CorruptedGauntletRoomShape.EDGE
            else -> CorruptedGauntletRoomShape.MIDDLE
        }
    }

    private fun sourceChunkX(
        kind: CorruptedGauntletRoomKind,
        shape: CorruptedGauntletRoomShape,
    ): Int =
        when (kind) {
            CorruptedGauntletRoomKind.START,
            CorruptedGauntletRoomKind.BOSS -> CorruptedGauntletConfig.SourceChunks.START_X
            else ->
                when (shape) {
                    CorruptedGauntletRoomShape.MIDDLE ->
                        CorruptedGauntletConfig.SourceChunks.MIDDLE_X
                    CorruptedGauntletRoomShape.EDGE -> CorruptedGauntletConfig.SourceChunks.EDGE_X
                    CorruptedGauntletRoomShape.CORNER ->
                        CorruptedGauntletConfig.SourceChunks.CORNER_X
                }
        }

    private fun sourceChunkZ(kind: CorruptedGauntletRoomKind, random: StableRandom): Int =
        when (kind) {
            CorruptedGauntletRoomKind.START -> CorruptedGauntletConfig.SourceChunks.START_Z
            CorruptedGauntletRoomKind.BOSS -> CorruptedGauntletConfig.SourceChunks.BOSS_Z
            else -> {
                val variants = CorruptedGauntletConfig.SourceChunks.roomVariantsZ
                variants[random.nextInt(variants.size)]
            }
        }

    private fun rotationFor(
        point: CorruptedGauntletGridPoint,
        kind: CorruptedGauntletRoomKind,
        random: StableRandom,
    ): Int {
        if (kind == CorruptedGauntletRoomKind.BOSS) return 0
        if (kind == CorruptedGauntletRoomKind.START) {
            return when {
                point.z < bossPoint.z -> 0
                point.x > bossPoint.x -> 1
                point.z > bossPoint.z -> 2
                else -> 3
            }
        }
        return when {
            point.x == 6 && point.z == 6 -> 0
            point.x == 0 && point.z == 0 -> 2
            point.x == 0 && point.z == 6 -> 3
            point.x == 6 && point.z == 0 -> 1
            point.x == 0 -> 3
            point.x == 6 -> 1
            point.z == 0 -> 2
            point.z == 6 -> 0
            else -> random.nextInt(4)
        }
    }

    private fun stableRoomScore(seed: Long, point: CorruptedGauntletGridPoint): Long =
        (seed xor (point.x.toLong() shl 32) xor point.z.toLong()) * -7_046_029_254_386_353_131L
}

/** SplitMix64 keeps layouts reproducible across JVM/Kotlin library upgrades. */
private class StableRandom(seed: Long) {
    private var state = seed

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val positive = nextLong().ushr(1)
        return (positive % bound.toLong()).toInt()
    }

    private fun nextLong(): Long {
        state += -7_046_029_254_386_353_131L
        var value = state
        value = (value xor (value ushr 30)) * -4_658_895_280_553_007_687L
        value = (value xor (value ushr 27)) * -7_723_592_293_110_705_685L
        return value xor (value ushr 31)
    }
}

private fun <T> List<T>.shuffled(random: StableRandom): List<T> {
    val result = toMutableList()
    for (index in result.lastIndex downTo 1) {
        val other = random.nextInt(index + 1)
        val value = result[index]
        result[index] = result[other]
        result[other] = value
    }
    return result
}
