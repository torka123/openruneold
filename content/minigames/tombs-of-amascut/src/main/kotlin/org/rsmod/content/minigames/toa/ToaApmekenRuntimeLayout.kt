package org.rsmod.content.minigames.toa

internal data class ApmekenBounds(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
    init {
        require(minX in 0..63 && maxX in minX..63)
        require(minZ in 0..63 && maxZ in minZ..63)
    }

    fun contains(tile: ApmekenTile): Boolean = tile.x in minX..maxX && tile.z in minZ..maxZ
}

internal data class ApmekenTileSpan(val z: Int, val x: IntRange) {
    init {
        require(z in 0..63)
        require(!x.isEmpty() && x.first in 0..63 && x.last in 0..63)
    }

    fun tiles(): Set<ApmekenTile> = x.mapTo(linkedSetOf()) { ApmekenTile(it, z) }
}

internal data class ApmekenSourceLoc(
    val locId: Int,
    val tile: ApmekenTile,
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

    fun footprint(): Set<ApmekenTile> {
        val rotated = angle == 1 || angle == 3
        val footprintWidth = if (rotated) length else width
        val footprintLength = if (rotated) width else length
        return buildSet {
            for (xOffset in 0 until footprintWidth) {
                for (zOffset in 0 until footprintLength) {
                    add(ApmekenTile(tile.x + xOffset, tile.z + zOffset))
                }
            }
        }
    }
}

internal enum class ApmekenSupplyKind {
    HAMMERS,
    POTIONS,
}

internal data class ApmekenSupplyFixture(val kind: ApmekenSupplyKind, val source: ApmekenSourceLoc)

internal data class ApmekenLocVariant(
    val locId: Int,
    val cacheName: String?,
    val action: String?,
    val animationId: Int,
    val width: Int,
    val length: Int,
    val clipType: Int,
)

internal data class ApmekenSpotAnimAsset(val id: Int, val sequenceId: Int, val modelId: Int)

internal enum class ApmekenDynamicBindingKey {
    ENTRY_ARRIVAL_TILE,
    WAVE_SPAWN_ANCHORS,
    CORRUPTION_STACK_TILE,
    CURSED_POOL_RUNTIME_TILES,
}

internal data class ApmekenUnresolvedRuntimeBinding(
    val key: ApmekenDynamicBindingKey,
    val assetIds: Set<Int>,
    val evidence: String,
)

/** Revision-240 Path of Apmeken cache assets. */
internal object ApmekenRevision240Assets {
    val npcIds = (11709..11718).toSet()

    const val IDLE_SEQUENCE = 9741
    const val WALK_SEQUENCE = 9739
    const val WALK_BACK_SEQUENCE = 9740
    const val MELEE_SEQUENCE = 9742
    const val RANGED_SEQUENCE = 9745
    const val MAGIC_SEQUENCE = 9746
    const val SHAMAN_SUMMON_SEQUENCE = 9747
    const val SPAWN_SEQUENCE = 9752
    const val SPAWN_ALTERNATE_SEQUENCE = 9753
    const val DESPAWN_SEQUENCE = 9754
    const val DESPAWN_ALTERNATE_SEQUENCE = 9755
    const val VOLATILE_EXPLODE_SEQUENCE = 9756
    const val VOLATILE_EXPLODE_SPOT_SEQUENCE = 9757

    const val REPAIR_PLAYER_SEQUENCE = 3676
    const val POUR_PLAYER_SEQUENCE = 2295

    val sightGranted = ApmekenSpotAnimAsset(2132, 9571, 45327)
    val sightLost = ApmekenSpotAnimAsset(2133, 9570, 45327)
    val roofWarning = ApmekenSpotAnimAsset(2134, 9567, 45327)
    val roofWarningAlternate = ApmekenSpotAnimAsset(2135, 9567, 45330)
    val corruption = ApmekenSpotAnimAsset(2137, 9571, 45333)
    val ventWarning = ApmekenSpotAnimAsset(2138, 5422, 20727)
    val roofCollapse = ApmekenSpotAnimAsset(2139, 4449, 45286)
    val thrallSummon = ApmekenSpotAnimAsset(2017, 9202, 43210)
    val rangedProjectile = ApmekenSpotAnimAsset(2242, -1, 46350)
    val magicProjectile = ApmekenSpotAnimAsset(2247, 661, 3084)
    val volatileBody = ApmekenSpotAnimAsset(2249, VOLATILE_EXPLODE_SPOT_SEQUENCE, 46355)
    val fire = ApmekenSpotAnimAsset(131, 664, 3088)

    val qteSpotAnims =
        listOf(
            sightGranted,
            sightLost,
            roofWarning,
            roofWarningAlternate,
            corruption,
            ventWarning,
            roofCollapse,
        )
    val baboonSpotAnims =
        listOf(thrallSummon, rangedProjectile, magicProjectile, volatileBody, fire)
}

/**
 * Revision-240-only Path of Apmeken runtime crosswalk.
 *
 * Static evidence comes from level 0 of map archive 15186 in `.data/cache/LIVE`, decoded with
 * OpenRune's MapTileDecoder and MapLocListDecoder. The archive has no map-NPC file; the separate
 * [ApmekenPacketWaveLayout] therefore owns the six spawn anchors observed in RsProx capture 14. No
 * donor coordinate is used as runtime evidence.
 */
internal object ApmekenRevision240RuntimeLayout {
    const val SOURCE_REGION = 15186
    const val SOURCE_REGION_X = 59
    const val SOURCE_REGION_Z = 82
    const val SOURCE_LEVEL = 0
    const val SOURCE_BASE_X = SOURCE_REGION_X * 64
    const val SOURCE_BASE_Z = SOURCE_REGION_Z * 64

    val encounterBounds = ApmekenBounds(16, 48, 24, 40)

    /** Colored, non-BLOCK_MAP_SQUARE level-zero terrain in archive 15186. */
    val terrainFloorSpans =
        listOf(
            ApmekenTileSpan(24, 22..42),
            ApmekenTileSpan(25, 22..42),
            ApmekenTileSpan(26, 22..42),
            ApmekenTileSpan(27, 17..20),
            ApmekenTileSpan(27, 22..42),
            ApmekenTileSpan(27, 44..47),
            ApmekenTileSpan(28, 17..20),
            ApmekenTileSpan(28, 22..42),
            ApmekenTileSpan(28, 44..47),
            ApmekenTileSpan(29, 17..20),
            ApmekenTileSpan(29, 22..42),
            ApmekenTileSpan(29, 44..47),
            ApmekenTileSpan(30, 17..20),
            ApmekenTileSpan(30, 22..42),
            ApmekenTileSpan(30, 44..47),
            ApmekenTileSpan(31, 16..19),
            ApmekenTileSpan(31, 21..30),
            ApmekenTileSpan(31, 34..48),
            ApmekenTileSpan(32, 16..19),
            ApmekenTileSpan(32, 21..30),
            ApmekenTileSpan(32, 34..48),
            ApmekenTileSpan(33, 16..19),
            ApmekenTileSpan(33, 21..30),
            ApmekenTileSpan(33, 34..48),
            ApmekenTileSpan(34, 17..20),
            ApmekenTileSpan(34, 22..42),
            ApmekenTileSpan(34, 44..47),
            ApmekenTileSpan(35, 17..20),
            ApmekenTileSpan(35, 22..42),
            ApmekenTileSpan(35, 44..47),
            ApmekenTileSpan(36, 17..20),
            ApmekenTileSpan(36, 22..42),
            ApmekenTileSpan(36, 44..47),
            ApmekenTileSpan(37, 17..20),
            ApmekenTileSpan(37, 22..42),
            ApmekenTileSpan(37, 44..47),
            ApmekenTileSpan(38, 22..42),
            ApmekenTileSpan(39, 22..42),
            ApmekenTileSpan(40, 22..42),
        )

    val terrainFloorTiles: Set<ApmekenTile> = expand(terrainFloorSpans)

    val exit = ApmekenSourceLoc(45128, ApmekenTile(15, 30), 10, 3, 5, 1)
    val entry = ApmekenSourceLoc(45500, ApmekenTile(49, 31), 10, 0, 1, 3)
    val centreStatue = ApmekenSourceLoc(45496, ApmekenTile(31, 31), 10, 1, 3, 3)

    val roofSupports =
        listOf(
            ApmekenSourceLoc(45494, ApmekenTile(27, 21), 10, 3, 3, 3),
            ApmekenSourceLoc(45494, ApmekenTile(35, 21), 10, 3, 3, 3),
            ApmekenSourceLoc(45494, ApmekenTile(27, 41), 10, 3, 3, 3),
            ApmekenSourceLoc(45494, ApmekenTile(35, 41), 10, 3, 3, 3),
        )

    /** Walkable centre tiles immediately in front of each cache-placed 3x3 roof support. */
    val roofInteractionTiles =
        listOf(ApmekenTile(28, 24), ApmekenTile(36, 24), ApmekenTile(28, 40), ApmekenTile(36, 40))

    /** Unnamed loc 45499 is placed exactly at all four vent QTE coordinates. */
    val vents =
        listOf(
            ApmekenSourceLoc(45499, ApmekenTile(24, 28), 22, 0, 1, 1),
            ApmekenSourceLoc(45499, ApmekenTile(40, 28), 22, 0, 1, 1),
            ApmekenSourceLoc(45499, ApmekenTile(24, 36), 22, 0, 1, 1),
            ApmekenSourceLoc(45499, ApmekenTile(40, 36), 22, 0, 1, 1),
        )

    val supplies =
        listOf(
            ApmekenSupplyFixture(
                ApmekenSupplyKind.POTIONS,
                ApmekenSourceLoc(45498, ApmekenTile(22, 25), 10, 2, 1, 1),
            ),
            ApmekenSupplyFixture(
                ApmekenSupplyKind.HAMMERS,
                ApmekenSourceLoc(45497, ApmekenTile(42, 25), 10, 2, 1, 1),
            ),
            ApmekenSupplyFixture(
                ApmekenSupplyKind.HAMMERS,
                ApmekenSourceLoc(45497, ApmekenTile(17, 36), 10, 0, 1, 1),
            ),
            ApmekenSupplyFixture(
                ApmekenSupplyKind.POTIONS,
                ApmekenSourceLoc(45498, ApmekenTile(19, 37), 10, 1, 1, 1),
            ),
            ApmekenSupplyFixture(
                ApmekenSupplyKind.HAMMERS,
                ApmekenSourceLoc(45497, ApmekenTile(22, 39), 10, 0, 1, 1),
            ),
            ApmekenSupplyFixture(
                ApmekenSupplyKind.POTIONS,
                ApmekenSourceLoc(45498, ApmekenTile(42, 39), 10, 0, 1, 1),
            ),
        )

    val venomPoolLoc = ApmekenLocVariant(45493, "Venom pool", null, -1, 1, 1, 0)
    val roofSupportLoc = ApmekenLocVariant(45494, "Roof support", "Repair", 9503, 3, 3, -1)
    val roofSupportAlternateLoc = ApmekenLocVariant(45495, "Roof support", "Repair", 9502, 3, 3, -1)
    val ventLoc = ApmekenLocVariant(45499, null, null, -1, 1, 1, -1)
    val hammerSupplyLoc = ApmekenLocVariant(45497, "Hammers", "Take", -1, 1, 1, -1)
    val potionSupplyLoc = ApmekenLocVariant(45498, "Potions", "Take", -1, 1, 1, -1)

    /**
     * Runtime hazards must not replace a QTE target or supply interaction. Static collision already
     * excludes the centre statue and room-edge entry/exit footprints.
     */
    val mechanicProtectedTiles: Set<ApmekenTile> = buildSet {
        addAll(roofInteractionTiles)
        addAll(vents.map { it.tile })
        addAll(supplies.map { it.source.tile })
    }
    val venomPlacementTiles: Set<ApmekenTile> = terrainFloorTiles - mechanicProtectedTiles

    val verifiedStaticMechanicLocIds = setOf(45128, 45494, 45496, 45497, 45498, 45499, 45500)

    val unresolvedDynamicBindings =
        listOf(
            ApmekenUnresolvedRuntimeBinding(
                ApmekenDynamicBindingKey.ENTRY_ARRIVAL_TILE,
                setOf(entry.locId),
                "Map 15186 proves entry loc 45500 at 49,31, not the server-selected player arrival tile.",
            ),
            ApmekenUnresolvedRuntimeBinding(
                ApmekenDynamicBindingKey.CORRUPTION_STACK_TILE,
                setOf(ApmekenRevision240Assets.corruption.id),
                "Corruption spotanim 2137 targets players; the cache contains no fixed cleanse-stack tile.",
            ),
            ApmekenUnresolvedRuntimeBinding(
                ApmekenDynamicBindingKey.CURSED_POOL_RUNTIME_TILES,
                setOf(venomPoolLoc.locId, ApmekenBaboon.CURSED.npcId),
                "Venom pool 45493 is server-spawned and absent from map 15186; choose only from venomPlacementTiles at runtime.",
            ),
        )

    init {
        check(ToaRoom.APMEKEN.sourceRegion == SOURCE_REGION)
        check(ToaRoom.APMEKEN.sourceMaxLevel == SOURCE_LEVEL)
        check(terrainFloorTiles.all(encounterBounds::contains))
        check(mechanicProtectedTiles.all(terrainFloorTiles::contains))
    }

    fun isWalkableFloor(tile: ApmekenTile): Boolean = tile in terrainFloorTiles

    fun isVenomPlacementTile(tile: ApmekenTile): Boolean = tile in venomPlacementTiles

    fun sourceAbsolute(tile: ApmekenTile): ApmekenTile =
        ApmekenTile(SOURCE_BASE_X + tile.x, SOURCE_BASE_Z + tile.z)

    private fun expand(spans: List<ApmekenTileSpan>): Set<ApmekenTile> =
        spans.flatMapTo(linkedSetOf()) { it.tiles() }
}
