package org.rsmod.content.minigames.toa

internal data class HetTileSpan(val z: Int, val x: IntRange) {
    init {
        require(z in 0..63)
        require(x.first in 0..63 && x.last in 0..63 && !x.isEmpty())
    }

    fun tiles(): Set<HetTile> = x.mapTo(linkedSetOf()) { HetTile(it, z) }
}

internal data class HetSourceLoc(
    val locId: Int,
    val tile: HetTile,
    val shape: Int,
    val angle: Int,
    val width: Int,
    val length: Int,
) {
    init {
        require(locId >= 0)
        require(shape in 0..22)
        require(angle in 0..3)
        require(width > 0 && length > 0)
    }

    fun footprint(): Set<HetTile> {
        val rotated = angle == 1 || angle == 3
        val footprintWidth = if (rotated) length else width
        val footprintLength = if (rotated) width else length
        return buildSet {
            for (xOffset in 0 until footprintWidth) {
                for (zOffset in 0 until footprintLength) {
                    add(HetTile(tile.x + xOffset, tile.z + zOffset))
                }
            }
        }
    }
}

internal data class HetMirrorCacheLocs(
    val movableClean: Int,
    val fixedClean: Int,
    val fixedDirty: Int,
)

internal data class HetBarrierCachePair(
    val intact: Int,
    val transition: Int,
    val playerBreakable: Boolean,
)

internal data class HetPickaxeStatueCache(
    val baseLoc: Int,
    val emptyActionLoc: Int,
    val controllingVarbit: Int,
    val transforms: List<Int>,
)

internal enum class HetDynamicBindingKey {
    ENTRY_ARRIVAL_TILE,
    BEAM_SOURCE_TILE,
    SHIELD_TILE,
    SEAL_TILE,
    MOVABLE_MIRROR_TILES,
    FIXED_MIRROR_TILES,
    DIRTY_MIRROR_TILES,
    BREAKABLE_BARRIER_TILES,
}

internal data class HetUnresolvedRuntimeBinding(
    val key: HetDynamicBindingKey,
    val cacheLocIds: Set<Int>,
    val evidence: String,
)

/**
 * Dynamic coordinates are deliberately supplied by the caller. Revision 240 proves the loc types
 * and static room geometry, but the OSRS server inserts the actual puzzle layout after entry.
 */
internal data class HetDynamicModelBinding(
    val beamSource: HetTile,
    val beamDirection: HetDirection,
    val shield: HetTile,
    val barriers: List<HetBarrier> = emptyList(),
    val fixedMirrors: List<HetMirror> = emptyList(),
    val movableMirrorIds: Set<Int> = emptySet(),
    val solution: List<HetMirrorPlacement> = emptyList(),
)

/**
 * Revision-240-only Path of Het crosswalk.
 *
 * Evidence source: map archive 14674 in `.data/cache/LIVE`, level 0, decoded with OpenRune's
 * MapTileDecoder and MapLocListDecoder. No donor coordinates or donor IDs are represented here.
 */
internal object HetRevision240RuntimeLayout {
    const val SOURCE_REGION = 14674
    const val SOURCE_REGION_X = 57
    const val SOURCE_REGION_Z = 82
    const val SOURCE_LEVEL = 0
    const val SOURCE_BASE_X = SOURCE_REGION_X * 64
    const val SOURCE_BASE_Z = SOURCE_REGION_Z * 64

    val bounds = HetBounds(0, 63, 0, 63)
    /**
     * Every local tile copied into the 64x64 Het instance. Beam graphics may terminate on any of
     * these tiles, including solid perimeter tiles outside the walkable floor.
     */
    val allRoomTiles: Set<HetTile> = buildSet {
        for (x in bounds.minX..bounds.maxX) {
            for (z in bounds.minZ..bounds.maxZ) add(HetTile(x, z))
        }
    }

    /** Colored, non-BLOCK_MAP_SQUARE terrain decoded from archive 14674. */
    val terrainFloorSpans =
        listOf(
            HetTileSpan(42, 30..34),
            HetTileSpan(41, 27..37),
            HetTileSpan(40, 25..39),
            HetTileSpan(39, 24..40),
            HetTileSpan(38, 24..40),
            HetTileSpan(38, 48..48),
            HetTileSpan(37, 14..14),
            HetTileSpan(37, 16..17),
            HetTileSpan(37, 23..41),
            HetTileSpan(37, 47..49),
            HetTileSpan(36, 13..17),
            HetTileSpan(36, 23..41),
            HetTileSpan(36, 47..49),
            HetTileSpan(35, 13..17),
            HetTileSpan(35, 23..41),
            HetTileSpan(35, 47..49),
            HetTileSpan(34, 13..17),
            HetTileSpan(34, 22..42),
            HetTileSpan(34, 47..49),
            HetTileSpan(33, 12..30),
            HetTileSpan(33, 34..50),
            HetTileSpan(32, 12..30),
            HetTileSpan(32, 34..50),
            HetTileSpan(31, 12..30),
            HetTileSpan(31, 34..50),
            HetTileSpan(30, 13..17),
            HetTileSpan(30, 22..42),
            HetTileSpan(30, 47..49),
            HetTileSpan(29, 13..17),
            HetTileSpan(29, 23..41),
            HetTileSpan(29, 47..49),
            HetTileSpan(28, 13..17),
            HetTileSpan(28, 23..41),
            HetTileSpan(28, 47..49),
            HetTileSpan(27, 14..14),
            HetTileSpan(27, 16..17),
            HetTileSpan(27, 23..41),
            HetTileSpan(27, 47..49),
            HetTileSpan(26, 24..40),
            HetTileSpan(26, 48..48),
            HetTileSpan(25, 24..40),
            HetTileSpan(24, 25..39),
            HetTileSpan(23, 27..37),
            HetTileSpan(22, 30..34),
        )

    /**
     * Terrain floor after subtracting every decoded solid centrepiece and blocking ground-decor
     * footprint. Wall-edge collision remains a runtime route-finder concern.
     */
    val placementSafeSpans =
        listOf(
            HetTileSpan(42, 30..33),
            HetTileSpan(41, 27..37),
            HetTileSpan(40, 25..39),
            HetTileSpan(39, 24..40),
            HetTileSpan(38, 24..40),
            HetTileSpan(37, 23..41),
            HetTileSpan(36, 23..41),
            HetTileSpan(35, 23..41),
            HetTileSpan(35, 48..49),
            HetTileSpan(34, 14..16),
            HetTileSpan(34, 22..42),
            HetTileSpan(34, 48..48),
            HetTileSpan(33, 12..20),
            HetTileSpan(33, 22..30),
            HetTileSpan(33, 34..42),
            HetTileSpan(33, 44..50),
            HetTileSpan(32, 12..20),
            HetTileSpan(32, 22..30),
            HetTileSpan(32, 34..42),
            HetTileSpan(32, 44..50),
            HetTileSpan(31, 12..20),
            HetTileSpan(31, 22..30),
            HetTileSpan(31, 34..42),
            HetTileSpan(31, 44..50),
            HetTileSpan(30, 14..17),
            HetTileSpan(30, 22..42),
            HetTileSpan(30, 47..49),
            HetTileSpan(29, 23..41),
            HetTileSpan(29, 47..49),
            HetTileSpan(28, 23..41),
            HetTileSpan(28, 47..48),
            HetTileSpan(27, 23..41),
            HetTileSpan(27, 48..48),
            HetTileSpan(26, 24..40),
            HetTileSpan(25, 24..40),
            HetTileSpan(24, 25..39),
            HetTileSpan(23, 27..37),
            HetTileSpan(22, 30..34),
        )

    val terrainFloorTiles: Set<HetTile> = expand(terrainFloorSpans)
    val placementSafeTiles: Set<HetTile> = expand(placementSafeSpans)
    val modelSolidTiles: Set<HetTile> = buildSet {
        for (x in 0..63) {
            for (z in 0..63) {
                val tile = HetTile(x, z)
                if (tile !in placementSafeTiles) add(tile)
            }
        }
    }

    val entry = HetSourceLoc(45131, HetTile(11, 30), shape = 10, angle = 2, width = 1, length = 5)
    val exit = HetSourceLoc(45128, HetTile(51, 30), shape = 10, angle = 1, width = 5, length = 1)
    val passBarriers =
        listOf(
            HetSourceLoc(45135, HetTile(21, 31), 10, 3, 1, 1),
            HetSourceLoc(45135, HetTile(21, 32), 10, 3, 1, 1),
            HetSourceLoc(45135, HetTile(21, 33), 10, 3, 1, 1),
            HetSourceLoc(45135, HetTile(43, 31), 10, 1, 1, 1),
            HetSourceLoc(45135, HetTile(43, 32), 10, 1, 1, 1),
            HetSourceLoc(45135, HetTile(43, 33), 10, 1, 1, 1),
        )
    val pickaxeStatueAnchors =
        listOf(
            HetSourceLoc(45468, HetTile(31, 19), 10, 2, 3, 3),
            HetSourceLoc(45468, HetTile(14, 27), 10, 2, 3, 3),
            HetSourceLoc(45468, HetTile(14, 35), 10, 0, 3, 3),
            HetSourceLoc(45468, HetTile(47, 36), 10, 0, 3, 3),
            HetSourceLoc(45468, HetTile(31, 43), 10, 0, 3, 3),
        )

    val mirrorLocs = HetMirrorCacheLocs(45455, 45456, 45457)
    val barrierLocs =
        listOf(
            HetBarrierCachePair(45458, 45459, playerBreakable = false),
            HetBarrierCachePair(45460, 45461, playerBreakable = false),
            HetBarrierCachePair(45462, 45463, playerBreakable = true),
            HetBarrierCachePair(45464, 45465, playerBreakable = true),
        )
    const val CLEARED_BARRIER_LOC = 45466

    const val CASTER_STATUE_LOC = 45486
    const val SHIELDED_STATUE_LOC = 45485
    val verifiedBrokenSealLocs = setOf(45489, 45491)
    val unresolvedAdjacentSealCandidateLocs = setOf(45490)

    val pickaxeStatue =
        HetPickaxeStatueCache(
            baseLoc = 45468,
            emptyActionLoc = 45469,
            controllingVarbit = 14440,
            transforms =
                listOf(
                    45470,
                    45471,
                    45472,
                    45473,
                    45474,
                    45475,
                    45476,
                    45477,
                    45478,
                    45480,
                    45479,
                    45482,
                    45482,
                    45481,
                    45483,
                    45483,
                    55808,
                    45484,
                    45484,
                    45484,
                    55809,
                    55809,
                    55809,
                    45470,
                ),
        )

    val verifiedStaticMechanicLocIds = setOf(entry.locId, exit.locId, 45135, 45468)

    val unresolvedDynamicBindings =
        listOf(
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.ENTRY_ARRIVAL_TILE,
                setOf(entry.locId),
                "The cache proves the entry loc anchor, not the server-selected arrival tile.",
            ),
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.BEAM_SOURCE_TILE,
                setOf(CASTER_STATUE_LOC),
                "Caster statue 45486 has no placement in source map 14674.",
            ),
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.SHIELD_TILE,
                setOf(SHIELDED_STATUE_LOC),
                "Shielded statue 45485 has no placement in source map 14674.",
            ),
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.SEAL_TILE,
                verifiedBrokenSealLocs,
                "The named broken-seal locs have no placement in source map 14674.",
            ),
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.MOVABLE_MIRROR_TILES,
                setOf(mirrorLocs.movableClean),
                "Movable mirror 45455 is server-spawned; no source-map coordinate exists.",
            ),
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.FIXED_MIRROR_TILES,
                setOf(mirrorLocs.fixedClean),
                "Fixed mirror 45456 is server-spawned; no source-map coordinate exists.",
            ),
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.DIRTY_MIRROR_TILES,
                setOf(mirrorLocs.fixedDirty),
                "Dirty mirror 45457 is server-spawned; no source-map coordinate exists.",
            ),
            HetUnresolvedRuntimeBinding(
                HetDynamicBindingKey.BREAKABLE_BARRIER_TILES,
                barrierLocs.flatMapTo(linkedSetOf()) { listOf(it.intact, it.transition) } +
                    CLEARED_BARRIER_LOC,
                "Barrier visuals 45458-45466 are not placed in source map 14674.",
            ),
        )

    init {
        check(ToaRoom.HET.sourceRegion == SOURCE_REGION)
        check(ToaRoom.HET.sourceMaxLevel == SOURCE_LEVEL)
    }

    fun isPlacementSafe(tile: HetTile): Boolean = tile in placementSafeTiles

    fun sourceAbsolute(tile: HetTile): HetTile {
        require(bounds.contains(tile))
        return HetTile(SOURCE_BASE_X + tile.x, SOURCE_BASE_Z + tile.z)
    }

    fun bindModel(binding: HetDynamicModelBinding): HetLayout {
        require(bounds.contains(binding.beamSource))
        require(bounds.contains(binding.shield))
        require(binding.fixedMirrors.all { !it.movable && isPlacementSafe(it.tile) })
        require(binding.barriers.all { isPlacementSafe(it.tile) })
        require(binding.solution.all { isPlacementSafe(it.tile) })
        return HetLayout(
            bounds = bounds,
            source = binding.beamSource,
            sourceDirection = binding.beamDirection,
            shield = binding.shield,
            solidTiles = modelSolidTiles - binding.beamSource - binding.shield,
            barriers = binding.barriers,
            fixedMirrors = binding.fixedMirrors,
            movableMirrorIds = binding.movableMirrorIds,
            solution = binding.solution,
        )
    }

    private fun expand(spans: List<HetTileSpan>): Set<HetTile> =
        spans.flatMapTo(linkedSetOf()) { it.tiles() }
}
