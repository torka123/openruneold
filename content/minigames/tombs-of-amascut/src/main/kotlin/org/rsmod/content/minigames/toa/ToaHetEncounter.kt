package org.rsmod.content.minigames.toa

import kotlin.math.min

internal data class HetTile(val x: Int, val z: Int) {
    fun step(direction: HetDirection): HetTile = HetTile(x + direction.dx, z + direction.dz)
}

internal data class HetBounds(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
    init {
        require(minX <= maxX)
        require(minZ <= maxZ)
    }

    fun contains(tile: HetTile): Boolean = tile.x in minX..maxX && tile.z in minZ..maxZ
}

internal enum class HetDirection(val dx: Int, val dz: Int) {
    NORTH(0, 1),
    EAST(1, 0),
    SOUTH(0, -1),
    WEST(-1, 0),
}

/**
 * The direction names describe the two exposed faces of a diagonal Het mirror. A beam that arrives
 * at the back of a mirror is blocked rather than reflected.
 */
internal enum class HetMirrorDirection {
    NORTH_EAST,
    SOUTH_EAST,
    SOUTH_WEST,
    NORTH_WEST;

    fun clockwise(): HetMirrorDirection = entries[(ordinal + 1) % entries.size]

    fun counterClockwise(): HetMirrorDirection =
        entries[(ordinal + entries.size - 1) % entries.size]

    fun reflect(incoming: HetDirection): HetDirection? =
        when (this) {
            NORTH_EAST ->
                when (incoming) {
                    HetDirection.WEST -> HetDirection.NORTH
                    HetDirection.SOUTH -> HetDirection.EAST
                    else -> null
                }

            SOUTH_EAST ->
                when (incoming) {
                    HetDirection.WEST -> HetDirection.SOUTH
                    HetDirection.NORTH -> HetDirection.EAST
                    else -> null
                }

            SOUTH_WEST ->
                when (incoming) {
                    HetDirection.EAST -> HetDirection.SOUTH
                    HetDirection.NORTH -> HetDirection.WEST
                    else -> null
                }

            NORTH_WEST ->
                when (incoming) {
                    HetDirection.EAST -> HetDirection.NORTH
                    HetDirection.SOUTH -> HetDirection.WEST
                    else -> null
                }
        }
}

internal data class HetMirror(
    val id: Int,
    val tile: HetTile,
    val direction: HetMirrorDirection,
    val movable: Boolean,
    val dirty: Boolean = false,
)

internal data class HetMirrorPlacement(
    val mirrorId: Int,
    val tile: HetTile,
    val direction: HetMirrorDirection,
)

internal data class HetBarrier(val tile: HetTile, val workRequired: Int) {
    init {
        require(workRequired > 0)
    }
}

internal data class HetLayout(
    val bounds: HetBounds,
    val source: HetTile,
    val sourceDirection: HetDirection,
    val shield: HetTile,
    val solidTiles: Set<HetTile> = emptySet(),
    val barriers: List<HetBarrier> = emptyList(),
    val fixedMirrors: List<HetMirror> = emptyList(),
    val movableMirrorIds: Set<Int> = emptySet(),
    val solution: List<HetMirrorPlacement> = emptyList(),
) {
    init {
        require(bounds.contains(source))
        require(bounds.contains(shield))
        require(source != shield)
        require(solidTiles.all(bounds::contains))
        require(barriers.all { bounds.contains(it.tile) })
        require(barriers.map { it.tile }.distinct().size == barriers.size)
        require(fixedMirrors.all { bounds.contains(it.tile) && !it.movable })
        require(fixedMirrors.map { it.tile }.distinct().size == fixedMirrors.size)
        require(fixedMirrors.map { it.id }.distinct().size == fixedMirrors.size)
        require(fixedMirrors.none { it.id in movableMirrorIds })
        require(solution.all { it.mirrorId in movableMirrorIds && bounds.contains(it.tile) })
        require(solution.map { it.mirrorId }.distinct().size == solution.size)
        require(solution.map { it.tile }.distinct().size == solution.size)
        require(solution.none { it.tile == source || it.tile == shield || it.tile in solidTiles })
        require(solution.none { placement -> fixedMirrors.any { it.tile == placement.tile } })
    }

    fun isSolvable(): Boolean {
        val mirrors =
            (fixedMirrors +
                    solution.map {
                        HetMirror(
                            id = it.mirrorId,
                            tile = it.tile,
                            direction = it.direction,
                            movable = true,
                        )
                    })
                .associateBy { it.tile }
        if (mirrors.size != fixedMirrors.size + solution.size) {
            return false
        }
        return HetBeamTracer.trace(this, mirrors, activeBarriers = emptySet()).end ==
            HetBeamEnd.HIT_SHIELD
    }
}

internal enum class HetBeamEnd {
    HIT_SHIELD,
    BLOCKED,
    LEFT_ROOM,
    LOOP,
    STEP_LIMIT,
}

internal data class HetBeamTrace(val tiles: List<HetTile>, val end: HetBeamEnd)

internal object HetBeamTracer {
    fun trace(
        layout: HetLayout,
        mirrors: Map<HetTile, HetMirror>,
        activeBarriers: Set<HetTile>,
        stepLimit: Int = 512,
    ): HetBeamTrace {
        require(stepLimit > 0)
        var tile = layout.source
        var direction = layout.sourceDirection
        val traversed = mutableListOf<HetTile>()
        val visited = mutableSetOf<Pair<HetTile, HetDirection>>()

        repeat(stepLimit) {
            if (!visited.add(tile to direction)) {
                return HetBeamTrace(traversed, HetBeamEnd.LOOP)
            }

            val next = tile.step(direction)
            if (!layout.bounds.contains(next)) {
                return HetBeamTrace(traversed, HetBeamEnd.LEFT_ROOM)
            }
            tile = next
            traversed += tile

            if (tile == layout.shield) {
                return HetBeamTrace(traversed, HetBeamEnd.HIT_SHIELD)
            }
            if (tile in layout.solidTiles || tile in activeBarriers) {
                return HetBeamTrace(traversed, HetBeamEnd.BLOCKED)
            }

            val mirror = mirrors[tile] ?: return@repeat
            if (mirror.dirty) {
                return HetBeamTrace(traversed, HetBeamEnd.BLOCKED)
            }
            direction =
                mirror.direction.reflect(direction)
                    ?: return HetBeamTrace(traversed, HetBeamEnd.BLOCKED)
        }

        return HetBeamTrace(traversed, HetBeamEnd.STEP_LIMIT)
    }
}

internal fun interface HetGroupSealScaling {
    fun maxHitpoints(partySize: Int): Int
}

internal object HetSealHitpoints {
    const val SOLO = 130

    fun maximum(partySize: Int, groupScaling: HetGroupSealScaling): Int {
        require(partySize in 1..8)
        if (partySize == 1) {
            return SOLO
        }
        return groupScaling.maxHitpoints(partySize).also {
            require(it >= SOLO) { "Group Het seal health cannot be lower than solo health." }
        }
    }
}

internal enum class HetSealWorkClass {
    STANDARD,
    RUNE,
    DRAGON,
}

internal enum class HetPickaxeTier(
    val barrierPower: Int,
    private val sealWorkClass: HetSealWorkClass,
) {
    BRONZE(1, HetSealWorkClass.STANDARD),
    IRON(2, HetSealWorkClass.STANDARD),
    STEEL(3, HetSealWorkClass.STANDARD),
    BLACK(4, HetSealWorkClass.STANDARD),
    MITHRIL(5, HetSealWorkClass.STANDARD),
    ADAMANT(6, HetSealWorkClass.STANDARD),
    RUNE(7, HetSealWorkClass.RUNE),
    GILDED(7, HetSealWorkClass.RUNE),
    DRAGON(8, HetSealWorkClass.DRAGON),
    INFERNAL(8, HetSealWorkClass.DRAGON),
    CRYSTAL(8, HetSealWorkClass.DRAGON),
    THIRD_AGE(8, HetSealWorkClass.DRAGON);

    internal fun workClass(): HetSealWorkClass = sealWorkClass
}

internal data class HetWorkRange(val minimum: Int, val maximum: Int) {
    init {
        require(minimum >= 0)
        require(maximum >= minimum)
    }

    fun roll(ticket: Int): Int = minimum + Math.floorMod(ticket, maximum - minimum + 1)
}

internal object HetSealWork {
    fun range(tier: HetPickaxeTier, miningLevel: Int): HetWorkRange {
        require(miningLevel >= 1)
        return when {
            miningLevel <= 84 ->
                when (tier.workClass()) {
                    HetSealWorkClass.STANDARD -> HetWorkRange(5, 7)
                    HetSealWorkClass.RUNE -> HetWorkRange(8, 10)
                    HetSealWorkClass.DRAGON -> HetWorkRange(10, 12)
                }

            miningLevel <= 98 ->
                when (tier.workClass()) {
                    HetSealWorkClass.STANDARD -> HetWorkRange(12, 15)
                    HetSealWorkClass.RUNE -> HetWorkRange(15, 17)
                    HetSealWorkClass.DRAGON -> HetWorkRange(17, 19)
                }

            else ->
                when (tier.workClass()) {
                    HetSealWorkClass.STANDARD -> HetWorkRange(14, 16)
                    HetSealWorkClass.RUNE -> HetWorkRange(17, 19)
                    HetSealWorkClass.DRAGON -> HetWorkRange(19, 21)
                }
        }
    }
}

private val missingGroupSealScaling = HetGroupSealScaling {
    error("A verified Het group seal scaling rule must be supplied for parties larger than one.")
}

internal class HetEncounter(
    val layout: HetLayout,
    partySize: Int = 1,
    groupSealScaling: HetGroupSealScaling = missingGroupSealScaling,
) {
    companion object {
        const val VULNERABILITY_TICKS = 24
    }

    private val mirrors = layout.fixedMirrors.associateByTo(mutableMapOf()) { it.tile }
    private val availableMirrorIds = layout.movableMirrorIds.toMutableSet()
    private val barrierWork =
        layout.barriers.associate { it.tile to it.workRequired }.toMutableMap()
    private var vulnerableFrom = Int.MAX_VALUE
    private var vulnerableUntil = Int.MIN_VALUE

    val sealMaxHitpoints = HetSealHitpoints.maximum(partySize, groupSealScaling)
    var sealHitpoints: Int = sealMaxHitpoints
        private set

    val complete: Boolean
        get() = sealHitpoints == 0

    fun availableMirrors(): Set<Int> = availableMirrorIds.toSet()

    fun mirrorAt(tile: HetTile): HetMirror? = mirrors[tile]

    fun remainingBarrierWork(tile: HetTile): Int = barrierWork[tile] ?: 0

    fun allBarriersBroken(): Boolean = barrierWork.values.all { it == 0 }

    fun placeMirror(mirrorId: Int, tile: HetTile, direction: HetMirrorDirection): Boolean {
        if (!canPlaceMirror(mirrorId, tile)) {
            return false
        }
        availableMirrorIds.remove(mirrorId)
        mirrors[tile] = HetMirror(mirrorId, tile, direction, movable = true)
        return true
    }

    fun canPlaceMirror(mirrorId: Int, tile: HetTile): Boolean =
        mirrorId in availableMirrorIds && canOccupy(tile)

    fun pickUpMirror(tile: HetTile): Boolean {
        val mirror = mirrors[tile] ?: return false
        if (!mirror.movable) {
            return false
        }
        mirrors.remove(tile)
        availableMirrorIds += mirror.id
        return true
    }

    fun pushMirror(tile: HetTile, direction: HetDirection): Boolean {
        if (!canPushMirror(tile, direction)) {
            return false
        }
        val mirror = requireNotNull(mirrors[tile])
        val destination = tile.step(direction)
        mirrors.remove(tile)
        mirrors[destination] = mirror.copy(tile = destination)
        return true
    }

    fun canPushMirror(tile: HetTile, direction: HetDirection): Boolean {
        val mirror = mirrors[tile] ?: return false
        return mirror.movable && canOccupy(tile.step(direction))
    }

    fun rotateMirror(tile: HetTile, clockwise: Boolean = true): Boolean {
        val mirror = mirrors[tile] ?: return false
        val direction =
            if (clockwise) mirror.direction.clockwise() else mirror.direction.counterClockwise()
        mirrors[tile] = mirror.copy(direction = direction)
        return true
    }

    fun dirtyMirror(tile: HetTile): Boolean {
        val mirror = mirrors[tile] ?: return false
        if (mirror.dirty) {
            return false
        }
        mirrors[tile] = mirror.copy(dirty = true)
        return true
    }

    fun cleanMirror(tile: HetTile): Boolean {
        val mirror = mirrors[tile] ?: return false
        if (!mirror.dirty) {
            return false
        }
        mirrors[tile] = mirror.copy(dirty = false)
        return true
    }

    fun workBarrier(tile: HetTile, tier: HetPickaxeTier): Int {
        val remaining = barrierWork[tile] ?: return 0
        if (remaining == 0) {
            return 0
        }
        val applied = min(remaining, tier.barrierPower)
        barrierWork[tile] = remaining - applied
        return applied
    }

    fun traceBeam(): HetBeamTrace =
        HetBeamTracer.trace(
            layout = layout,
            mirrors = mirrors,
            activeBarriers = barrierWork.filterValues { it > 0 }.keys,
        )

    fun fireBeam(cycle: Int): HetBeamTrace {
        val trace = traceBeam()
        if (trace.end == HetBeamEnd.HIT_SHIELD && !complete) {
            exposeSeal(cycle)
        }
        return trace
    }

    fun isSealVulnerable(cycle: Int): Boolean =
        !complete && cycle >= vulnerableFrom && cycle < vulnerableUntil

    fun workSeal(cycle: Int, tier: HetPickaxeTier, miningLevel: Int, rngTicket: Int): Int {
        if (!isSealVulnerable(cycle)) {
            return 0
        }
        val applied = min(sealHitpoints, HetSealWork.range(tier, miningLevel).roll(rngTicket))
        sealHitpoints -= applied
        return applied
    }

    private fun exposeSeal(cycle: Int) {
        require(cycle <= Int.MAX_VALUE - VULNERABILITY_TICKS)
        vulnerableFrom = cycle
        vulnerableUntil = cycle + VULNERABILITY_TICKS
    }

    private fun canOccupy(tile: HetTile): Boolean =
        layout.bounds.contains(tile) &&
            tile != layout.source &&
            tile != layout.shield &&
            tile !in layout.solidTiles &&
            barrierWork.getOrDefault(tile, 0) == 0 &&
            tile !in mirrors
}

/** Seeded, deterministic layouts. Each recorded solution uses one to three movable mirrors. */
internal object HetLayoutGenerator {
    private val templates =
        listOf(
            HetLayout(
                bounds = HetBounds(0, 8, 0, 8),
                source = HetTile(0, 1),
                sourceDirection = HetDirection.EAST,
                shield = HetTile(7, 6),
                barriers = listOf(HetBarrier(HetTile(5, 6), 8)),
                movableMirrorIds = setOf(1, 2),
                solution =
                    listOf(
                        HetMirrorPlacement(1, HetTile(3, 1), HetMirrorDirection.NORTH_WEST),
                        HetMirrorPlacement(2, HetTile(3, 6), HetMirrorDirection.SOUTH_EAST),
                    ),
            ),
            HetLayout(
                bounds = HetBounds(0, 8, 0, 8),
                source = HetTile(1, 7),
                sourceDirection = HetDirection.SOUTH,
                shield = HetTile(7, 1),
                barriers = listOf(HetBarrier(HetTile(4, 4), 8)),
                movableMirrorIds = setOf(1, 2),
                solution =
                    listOf(
                        HetMirrorPlacement(1, HetTile(1, 4), HetMirrorDirection.NORTH_EAST),
                        HetMirrorPlacement(2, HetTile(7, 4), HetMirrorDirection.SOUTH_WEST),
                    ),
            ),
            HetLayout(
                bounds = HetBounds(0, 8, 0, 8),
                source = HetTile(0, 2),
                sourceDirection = HetDirection.EAST,
                shield = HetTile(7, 2),
                barriers = listOf(HetBarrier(HetTile(7, 4), 8)),
                movableMirrorIds = setOf(1, 2, 3),
                solution =
                    listOf(
                        HetMirrorPlacement(1, HetTile(2, 2), HetMirrorDirection.NORTH_WEST),
                        HetMirrorPlacement(2, HetTile(2, 7), HetMirrorDirection.SOUTH_EAST),
                        HetMirrorPlacement(3, HetTile(7, 7), HetMirrorDirection.SOUTH_WEST),
                    ),
            ),
        )

    fun generate(seed: Long): HetLayout {
        val size = templates.size.toLong()
        val index = (((seed % size) + size) % size).toInt()
        return templates[index]
    }
}
