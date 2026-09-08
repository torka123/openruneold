package org.rsmod.content.minigames.toa

internal data class ScabarasTile(val x: Int, val z: Int) {
    init {
        require(x in 0..63 && z in 0..63) { "Scabaras source tile must be inside one map region." }
    }
}

internal data class ScabarasWorldTile(val x: Int, val z: Int) {
    init {
        require(x >= 0 && z >= 0)
    }
}

internal data class ScabarasFloorArea(val x: IntRange, val z: IntRange) {
    init {
        require(!x.isEmpty() && !z.isEmpty())
        require(x.first in 0..63 && x.last in 0..63)
        require(z.first in 0..63 && z.last in 0..63)
    }

    val northWest: ScabarasTile
        get() = ScabarasTile(x.first, z.last)

    fun tiles(): Set<ScabarasTile> = buildSet {
        for (tileX in x) {
            for (tileZ in z) {
                add(ScabarasTile(tileX, tileZ))
            }
        }
    }
}

internal data class ScabarasTileGrid(
    val northWest: ScabarasTile,
    val width: Int,
    val height: Int,
    val xStride: Int = 1,
    val zStride: Int = 1,
) {
    init {
        require(width > 0 && height > 0)
        require(xStride > 0 && zStride > 0)
        require(northWest.x + (width - 1) * xStride <= 63)
        require(northWest.z - (height - 1) * zStride >= 0)
    }

    fun tiles(): List<ScabarasTile> =
        buildList(width * height) {
            for (row in 0 until height) {
                for (column in 0 until width) {
                    add(ScabarasTile(northWest.x + column * xStride, northWest.z - row * zStride))
                }
            }
        }
}

internal data class ScabarasSourceLoc(
    val locId: Int,
    val tile: ScabarasTile,
    val shape: Int,
    val angle: Int,
) {
    init {
        require(locId >= 0)
        require(shape in 0..22)
        require(angle in 0..3)
    }
}

internal enum class ScabarasLane {
    NORTH,
    SOUTH,
}

/** Explicit model indexes avoid coupling the runtime map to enum declaration order. */
internal enum class ScabarasSideRoomSlot(
    val modelIndex: Int,
    val lane: ScabarasLane,
    val firstInLane: Boolean,
) {
    NORTH_FIRST(0, ScabarasLane.NORTH, true),
    NORTH_SECOND(1, ScabarasLane.NORTH, false),
    SOUTH_FIRST(2, ScabarasLane.SOUTH, true),
    SOUTH_SECOND(3, ScabarasLane.SOUTH, false),
}

internal data class ScabarasSideRoomLayout(
    val slot: ScabarasSideRoomSlot,
    val floor: ScabarasFloorArea,
) {
    /** Sequence uses the complete spaced 3x3 pressure-plate grid. */
    val sequenceTiles: List<ScabarasTile> =
        ScabarasTileGrid(floor.northWest, width = 3, height = 3, xStride = 2, zStride = 2).tiles()

    /** Lights uses the same spaced grid without its centre tile. */
    val lightTiles: List<ScabarasTile> = sequenceTiles.filterIndexed { index, _ -> index != 4 }

    /** The addition puzzle is the dense 5x5 grid beginning at the same north-west anchor. */
    val additionTiles: List<ScabarasTile> =
        ScabarasTileGrid(floor.northWest, width = 5, height = 5).tiles()

    fun verifiedInteractionTiles(puzzle: ScabarasPuzzle): List<ScabarasTile>? =
        when (puzzle) {
            ScabarasPuzzle.SEQUENCE -> sequenceTiles
            ScabarasPuzzle.LIGHTS -> lightTiles
            ScabarasPuzzle.ADDITION -> additionTiles
            ScabarasPuzzle.OBELISKS -> null
            ScabarasPuzzle.MATCHING -> null
        }
}

internal data class ScabarasBoundSideRoom(
    val slot: ScabarasSideRoomSlot,
    val puzzle: ScabarasPuzzle,
    /** Null means revision 240 proves the asset family but not its server-selected placement. */
    val verifiedInteractionTiles: List<ScabarasTile>?,
)

internal data class ScabarasSequenceCache(
    val buttonLoc: Int,
    val tileUpLoc: Int,
    val sequenceDisplayDownLoc: Int,
    val playerDownLoc: Int,
)

internal data class ScabarasLightsCache(val tileOffLoc: Int, val enabledEffectLoc: Int)

internal data class ScabarasAdditionTileCache(
    val value: Int,
    val groundLoc: Int,
    val activeEffectLoc: Int,
)

internal data class ScabarasMatchingTileCache(
    val modelSymbol: Int,
    val cacheOrdinal: Int,
    val hiddenButtonLoc: Int,
    val revealedSymbolLoc: Int,
    val matchedEffectLoc: Int,
)

internal data class ScabarasObeliskCache(val attackableNpc: Int, val hitNpc: Int)

internal enum class ScabarasCacheAssetKind {
    LOC,
    NPC,
}

internal enum class ScabarasDynamicBindingKey {
    SEQUENCE_BUTTON_TILE,
    ADDITION_TABLET_TILE,
    OBELISK_TILES,
    MATCHING_TILE_COORDINATES,
    MATCHING_FINAL_BUTTON_TILE,
    RANGED_SCARAB_SPAWN_TILES,
    RANGED_SCARAB_SWARM_EFFECT_TILE,
    UNCLASSIFIED_FX02_ROLE,
}

internal data class ScabarasUnresolvedRuntimeBinding(
    val key: ScabarasDynamicBindingKey,
    val assetKind: ScabarasCacheAssetKind,
    val assetIds: Set<Int>,
    val evidence: String,
)

/**
 * Revision-240-only Path of Scabaras runtime crosswalk.
 *
 * Static evidence comes from level 0 of map archive 14162 in `.data/cache/LIVE`. The four
 * mechanic-grid anchors are also the four anchors used by the current RuneLite Scabaras helpers;
 * every dense 5x5 grid tile resolves to the room's revision-240 floor locs. Puzzle locs themselves
 * are inserted by the OSRS server and are deliberately represented as unresolved when the archive
 * contains no placement.
 */
internal object ScabarasRevision240RuntimeLayout {
    const val SOURCE_REGION = 14162
    const val SOURCE_REGION_X = 55
    const val SOURCE_REGION_Z = 82
    const val SOURCE_LEVEL = 0
    const val SOURCE_BASE_X = SOURCE_REGION_X * 64
    const val SOURCE_BASE_Z = SOURCE_REGION_Z * 64

    val sideRoomSlots =
        listOf(
            ScabarasSideRoomSlot.NORTH_FIRST,
            ScabarasSideRoomSlot.NORTH_SECOND,
            ScabarasSideRoomSlot.SOUTH_FIRST,
            ScabarasSideRoomSlot.SOUTH_SECOND,
        )

    val sideRooms =
        listOf(
            ScabarasSideRoomLayout(
                ScabarasSideRoomSlot.NORTH_FIRST,
                ScabarasFloorArea(19..25, 35..41),
            ),
            ScabarasSideRoomLayout(
                ScabarasSideRoomSlot.NORTH_SECOND,
                ScabarasFloorArea(36..42, 35..41),
            ),
            ScabarasSideRoomLayout(
                ScabarasSideRoomSlot.SOUTH_FIRST,
                ScabarasFloorArea(19..25, 23..29),
            ),
            ScabarasSideRoomLayout(
                ScabarasSideRoomSlot.SOUTH_SECOND,
                ScabarasFloorArea(36..42, 23..29),
            ),
        )

    val branchPassages =
        mapOf(
            ScabarasLane.NORTH to ScabarasSourceLoc(45343, ScabarasTile(28, 35), 0, 1),
            ScabarasLane.SOUTH to ScabarasSourceLoc(45343, ScabarasTile(28, 29), 0, 3),
        )

    /**
     * Walkable emergence tiles for the two cache-authored Crawl-through passages. The first tile in
     * each list is the OSRS-facing landing; the remaining tiles are collision-safe fallbacks when
     * the primary landing is unavailable in a live instance.
     */
    val branchPassageLandings =
        mapOf(
            ScabarasLane.NORTH to
                listOf(ScabarasTile(28, 36), ScabarasTile(27, 36), ScabarasTile(29, 36)),
            ScabarasLane.SOUTH to
                listOf(ScabarasTile(28, 28), ScabarasTile(27, 28), ScabarasTile(29, 28)),
        )

    /** Static floor footprints surrounding the two server-populated 3x3 matching boards. */
    val matchingBoardFloorAreas =
        mapOf(
            ScabarasMatchingBoard.NORTH to ScabarasFloorArea(48..54, 35..41),
            ScabarasMatchingBoard.SOUTH to ScabarasFloorArea(48..54, 23..29),
        )

    val sequenceCache =
        ScabarasSequenceCache(
            buttonLoc = 45338,
            tileUpLoc = 45340,
            sequenceDisplayDownLoc = 45341,
            playerDownLoc = 45342,
        )

    val lightsCache = ScabarasLightsCache(tileOffLoc = 45344, enabledEffectLoc = 45384)

    const val ADDITION_TABLET_LOC = 45339

    val additionTiles =
        listOf(
            ScabarasAdditionTileCache(1, 45345, 45388),
            ScabarasAdditionTileCache(2, 45346, 45389),
            ScabarasAdditionTileCache(3, 45347, 45390),
            ScabarasAdditionTileCache(4, 45348, 45391),
            ScabarasAdditionTileCache(5, 45349, 45392),
            ScabarasAdditionTileCache(6, 45350, 45393),
            ScabarasAdditionTileCache(7, 45351, 45386),
            ScabarasAdditionTileCache(8, 45352, 45394),
            ScabarasAdditionTileCache(9, 45353, 45395),
        )

    const val MATCHING_FINAL_BUTTON_LOC = 45355
    const val MATCHING_DIVIDER_LOC = 45374

    val matchingTiles =
        listOf(
            ScabarasMatchingTileCache(0, 1, 45356, 45365, 45388),
            ScabarasMatchingTileCache(1, 2, 45357, 45366, 45389),
            ScabarasMatchingTileCache(2, 3, 45358, 45367, 45386),
            ScabarasMatchingTileCache(3, 4, 45359, 45368, 45391),
            ScabarasMatchingTileCache(4, 5, 45360, 45369, 45392),
            ScabarasMatchingTileCache(5, 6, 45361, 45370, 45387),
            ScabarasMatchingTileCache(6, 7, 45362, 45371, 45393),
            ScabarasMatchingTileCache(7, 8, 45363, 45372, 45394),
            ScabarasMatchingTileCache(8, 9, 45364, 45373, 45395),
        )

    val obelisks = ScabarasObeliskCache(attackableNpc = 11698, hitNpc = 11699)
    const val RANGED_SCARAB_NPC = 11697
    const val RANGED_SCARAB_SWARM_LOC = 45354

    val verifiedStaticMechanicLocIds = setOf(45343)

    val unresolvedDynamicBindings =
        listOf(
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.SEQUENCE_BUTTON_TILE,
                ScabarasCacheAssetKind.LOC,
                setOf(sequenceCache.buttonLoc),
                "Ancient button 45338 is not placed in source map 14162; its room-relative tile is server-selected.",
            ),
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.ADDITION_TABLET_TILE,
                ScabarasCacheAssetKind.LOC,
                setOf(ADDITION_TABLET_LOC),
                "Ancient tablet 45339 is not placed in source map 14162.",
            ),
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.OBELISK_TILES,
                ScabarasCacheAssetKind.NPC,
                setOf(obelisks.attackableNpc, obelisks.hitNpc),
                "NPC definitions 11698 and 11699 are verified, but no production spawn coordinates are present in the map inputs.",
            ),
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.MATCHING_TILE_COORDINATES,
                ScabarasCacheAssetKind.LOC,
                matchingTiles.flatMapTo(linkedSetOf()) {
                    listOf(it.hiddenButtonLoc, it.revealedSymbolLoc, it.matchedEffectLoc)
                },
                "Both 7x7 board floor areas are static; their 3x3 symbol locs are server-populated and absent from map 14162.",
            ),
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.MATCHING_FINAL_BUTTON_TILE,
                ScabarasCacheAssetKind.LOC,
                setOf(MATCHING_FINAL_BUTTON_LOC, MATCHING_DIVIDER_LOC),
                "The matching button and divider definitions resolve but have no source-map placement.",
            ),
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.RANGED_SCARAB_SPAWN_TILES,
                ScabarasCacheAssetKind.NPC,
                setOf(RANGED_SCARAB_NPC),
                "Scarab 11697 is the verified ranged challenge NPC; its party-size-dependent spawns are not present in production map NPC inputs.",
            ),
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.RANGED_SCARAB_SWARM_EFFECT_TILE,
                ScabarasCacheAssetKind.LOC,
                setOf(RANGED_SCARAB_SWARM_LOC),
                "Scabaras swarm effect 45354 is not placed in source map 14162; no production spawn tile was found.",
            ),
            ScabarasUnresolvedRuntimeBinding(
                ScabarasDynamicBindingKey.UNCLASSIFIED_FX02_ROLE,
                ScabarasCacheAssetKind.LOC,
                setOf(45385),
                "Scabaras FX02 resolves in revision 240, but neither the map nor current RuneLite helpers assign it to a puzzle state.",
            ),
        )

    init {
        check(ToaRoom.SCABARAS.sourceRegion == SOURCE_REGION)
        check(ToaRoom.SCABARAS.sourceMaxLevel == SOURCE_LEVEL)
        check(sideRooms.map { it.slot } == sideRoomSlots)
        check(additionTiles.map { it.value } == (1..9).toList())
        check(matchingTiles.map { it.modelSymbol } == (0..8).toList())
    }

    fun sourceAbsolute(tile: ScabarasTile): ScabarasWorldTile =
        ScabarasWorldTile(SOURCE_BASE_X + tile.x, SOURCE_BASE_Z + tile.z)

    /**
     * Applies the model's documented north-first, north-second, south-first, south-second order.
     */
    fun bindSideRooms(solutions: ScabarasSolutions): List<ScabarasBoundSideRoom> =
        sideRooms.map { room ->
            val puzzle = solutions.sideRooms[room.slot.modelIndex]
            ScabarasBoundSideRoom(
                slot = room.slot,
                puzzle = puzzle,
                verifiedInteractionTiles = room.verifiedInteractionTiles(puzzle),
            )
        }
}
