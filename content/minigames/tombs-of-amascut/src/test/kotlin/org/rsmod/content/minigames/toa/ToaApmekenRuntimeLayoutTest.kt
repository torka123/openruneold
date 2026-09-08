package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToaApmekenRuntimeLayoutTest {
    @Test
    fun sourceRegionAndFloorUseRevision240Map15186() {
        val runtime = ApmekenRevision240RuntimeLayout
        assertEquals(ToaRoom.APMEKEN.sourceRegion, runtime.SOURCE_REGION)
        assertEquals(15186, runtime.SOURCE_REGION)
        assertEquals(59, runtime.SOURCE_REGION_X)
        assertEquals(82, runtime.SOURCE_REGION_Z)
        assertEquals(ApmekenBounds(16, 48, 24, 40), runtime.encounterBounds)
        assertEquals(445, runtime.terrainFloorTiles.size)
        assertTrue(runtime.terrainFloorTiles.all(runtime.encounterBounds::contains))
        assertEquals(ApmekenTile(3800, 5272), runtime.sourceAbsolute(ApmekenTile(24, 24)))
    }

    @Test
    fun entryExitStatueAndRoofSupportsMatchStaticMapLocs() {
        val runtime = ApmekenRevision240RuntimeLayout
        assertEquals(ApmekenSourceLoc(45128, ApmekenTile(15, 30), 10, 3, 5, 1), runtime.exit)
        assertEquals(ApmekenSourceLoc(45500, ApmekenTile(49, 31), 10, 0, 1, 3), runtime.entry)
        assertEquals(
            ApmekenSourceLoc(45496, ApmekenTile(31, 31), 10, 1, 3, 3),
            runtime.centreStatue,
        )
        assertEquals(
            listOf(
                ApmekenTile(27, 21),
                ApmekenTile(35, 21),
                ApmekenTile(27, 41),
                ApmekenTile(35, 41),
            ),
            runtime.roofSupports.map { it.tile },
        )
        assertTrue(runtime.roofSupports.all { it.locId == 45494 && it.footprint().size == 9 })
        assertEquals(
            listOf(
                ApmekenTile(28, 24),
                ApmekenTile(36, 24),
                ApmekenTile(28, 40),
                ApmekenTile(36, 40),
            ),
            runtime.roofInteractionTiles,
        )
        assertTrue(runtime.roofInteractionTiles.all(runtime::isWalkableFloor))
    }

    @Test
    fun ventsAndSupplyFixturesUseExactStaticCoordinates() {
        val runtime = ApmekenRevision240RuntimeLayout
        assertEquals(
            listOf(
                ApmekenTile(24, 28),
                ApmekenTile(40, 28),
                ApmekenTile(24, 36),
                ApmekenTile(40, 36),
            ),
            runtime.vents.map { it.tile },
        )
        assertTrue(runtime.vents.all { it.locId == 45499 && it.shape == 22 })
        assertEquals(
            listOf(
                ApmekenSupplyKind.POTIONS to ApmekenTile(22, 25),
                ApmekenSupplyKind.HAMMERS to ApmekenTile(42, 25),
                ApmekenSupplyKind.HAMMERS to ApmekenTile(17, 36),
                ApmekenSupplyKind.POTIONS to ApmekenTile(19, 37),
                ApmekenSupplyKind.HAMMERS to ApmekenTile(22, 39),
                ApmekenSupplyKind.POTIONS to ApmekenTile(42, 39),
            ),
            runtime.supplies.map { it.kind to it.source.tile },
        )
        assertTrue(runtime.vents.all { runtime.isWalkableFloor(it.tile) })
        assertTrue(runtime.supplies.all { runtime.isWalkableFloor(it.source.tile) })
    }

    @Test
    fun locVariantsPreserveCacheActionsAnimationsAndCollision() {
        val runtime = ApmekenRevision240RuntimeLayout
        assertEquals(
            ApmekenLocVariant(45493, "Venom pool", null, -1, 1, 1, 0),
            runtime.venomPoolLoc,
        )
        assertEquals(
            ApmekenLocVariant(45494, "Roof support", "Repair", 9503, 3, 3, -1),
            runtime.roofSupportLoc,
        )
        assertEquals(
            ApmekenLocVariant(45495, "Roof support", "Repair", 9502, 3, 3, -1),
            runtime.roofSupportAlternateLoc,
        )
        assertEquals(ApmekenLocVariant(45499, null, null, -1, 1, 1, -1), runtime.ventLoc)
        assertEquals("Take", runtime.hammerSupplyLoc.action)
        assertEquals("Take", runtime.potionSupplyLoc.action)
        assertEquals(
            setOf(45128, 45494, 45496, 45497, 45498, 45499, 45500),
            runtime.verifiedStaticMechanicLocIds,
        )
    }

    @Test
    fun protectedMechanicTilesAreExcludedFromCursedPoolPlacement() {
        val runtime = ApmekenRevision240RuntimeLayout
        assertEquals(14, runtime.mechanicProtectedTiles.size)
        assertEquals(431, runtime.venomPlacementTiles.size)
        assertTrue(runtime.mechanicProtectedTiles.all(runtime::isWalkableFloor))
        assertTrue(runtime.mechanicProtectedTiles.none(runtime::isVenomPlacementTile))
        assertTrue(runtime.venomPlacementTiles.all(runtime::isVenomPlacementTile))
        assertFalse(runtime.isVenomPlacementTile(ApmekenTile(24, 28)))
        assertTrue(runtime.isVenomPlacementTile(ApmekenTile(32, 24)))
    }

    @Test
    fun npcSequencesAndSpotAnimationsResolveToRevision240Assets() {
        val assets = ApmekenRevision240Assets
        assertEquals(ApmekenBaboon.entries.mapTo(linkedSetOf()) { it.npcId }, assets.npcIds)
        assertEquals(
            listOf(9741, 9739, 9740),
            listOf(assets.IDLE_SEQUENCE, assets.WALK_SEQUENCE, assets.WALK_BACK_SEQUENCE),
        )
        assertEquals(
            listOf(9742, 9745, 9746, 9747),
            listOf(
                assets.MELEE_SEQUENCE,
                assets.RANGED_SEQUENCE,
                assets.MAGIC_SEQUENCE,
                assets.SHAMAN_SUMMON_SEQUENCE,
            ),
        )
        assertEquals(
            listOf(9752, 9753, 9754, 9755, 9756, 9757),
            listOf(
                assets.SPAWN_SEQUENCE,
                assets.SPAWN_ALTERNATE_SEQUENCE,
                assets.DESPAWN_SEQUENCE,
                assets.DESPAWN_ALTERNATE_SEQUENCE,
                assets.VOLATILE_EXPLODE_SEQUENCE,
                assets.VOLATILE_EXPLODE_SPOT_SEQUENCE,
            ),
        )
        assertEquals(
            listOf(2132, 2133, 2134, 2135, 2137, 2138, 2139),
            assets.qteSpotAnims.map { it.id },
        )
        assertEquals(listOf(2017, 2242, 2247, 2249, 131), assets.baboonSpotAnims.map { it.id })
        assertEquals(ApmekenSpotAnimAsset(2249, 9757, 46355), assets.volatileBody)
        assertEquals(ApmekenSpotAnimAsset(2138, 5422, 20727), assets.ventWarning)
        assertEquals(3676, assets.REPAIR_PLAYER_SEQUENCE)
        assertEquals(2295, assets.POUR_PLAYER_SEQUENCE)
    }

    @Test
    fun packetAnchorsAreResolvedWhileOtherServerSelectedCoordinatesRemainExplicit() {
        val runtime = ApmekenRevision240RuntimeLayout
        assertEquals(
            ApmekenDynamicBindingKey.entries.toSet() - ApmekenDynamicBindingKey.WAVE_SPAWN_ANCHORS,
            runtime.unresolvedDynamicBindings.mapTo(linkedSetOf()) { it.key },
        )
        assertTrue(
            runtime.unresolvedDynamicBindings.all {
                it.assetIds.isNotEmpty() && it.evidence.isNotBlank()
            }
        )
        assertEquals(6, ApmekenPacketWaveLayout.anchors.size)
        assertTrue(ApmekenPacketWaveLayout.anchors.all(runtime::isWalkableFloor))
    }
}
