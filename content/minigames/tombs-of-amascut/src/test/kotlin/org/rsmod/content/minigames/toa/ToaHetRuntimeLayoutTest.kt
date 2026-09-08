package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToaHetRuntimeLayoutTest {
    @Test
    fun sourceRegionBindingComesFromOpenRuneHetRoom() {
        assertEquals(ToaRoom.HET.sourceRegion, HetRevision240RuntimeLayout.SOURCE_REGION)
        assertEquals(14674, HetRevision240RuntimeLayout.SOURCE_REGION)
        assertEquals(57, HetRevision240RuntimeLayout.SOURCE_REGION_X)
        assertEquals(82, HetRevision240RuntimeLayout.SOURCE_REGION_Z)
        assertEquals(
            HetTile(3659, 5278),
            HetRevision240RuntimeLayout.sourceAbsolute(HetTile(11, 30)),
        )
    }

    @Test
    fun bridgeTileMapCoversEveryValidTileInTheCopiedHetRegion() {
        val runtime = HetRevision240RuntimeLayout

        assertEquals(64 * 64, runtime.allRoomTiles.size)
        assertTrue(HetTile(runtime.bounds.minX, runtime.bounds.minZ) in runtime.allRoomTiles)
        assertTrue(HetTile(runtime.bounds.maxX, runtime.bounds.maxZ) in runtime.allRoomTiles)
        assertTrue(runtime.allRoomTiles.all(runtime.bounds::contains))
    }

    @Test
    fun cacheTerrainAndPlacementSafeGridsAreExactAndSeparate() {
        val runtime = HetRevision240RuntimeLayout
        assertEquals(456, runtime.terrainFloorTiles.size)
        assertEquals(406, runtime.placementSafeTiles.size)
        assertEquals(3690, runtime.modelSolidTiles.size)
        assertTrue(runtime.placementSafeTiles.all { it in runtime.terrainFloorTiles })
        assertTrue(runtime.placementSafeTiles.none { it in runtime.modelSolidTiles })

        assertTrue(HetTile(14, 27) in runtime.terrainFloorTiles)
        assertFalse(runtime.isPlacementSafe(HetTile(14, 27)))
        assertFalse(runtime.isPlacementSafe(HetTile(21, 32)))
        assertTrue(runtime.isPlacementSafe(HetTile(22, 32)))
        assertTrue(
            HetTile(ToaRoom.HET.entryLocalX, ToaRoom.HET.entryLocalZ) in runtime.terrainFloorTiles
        )
    }

    @Test
    fun staticSourceAnchorsMatchRevision240MapArchive() {
        val runtime = HetRevision240RuntimeLayout
        assertEquals(HetSourceLoc(45131, HetTile(11, 30), 10, 2, 1, 5), runtime.entry)
        assertEquals(HetSourceLoc(45128, HetTile(51, 30), 10, 1, 5, 1), runtime.exit)
        assertEquals(
            setOf(HetTile(21, 31), HetTile(21, 32), HetTile(21, 33)),
            runtime.passBarriers.filter { it.angle == 3 }.mapTo(linkedSetOf()) { it.tile },
        )
        assertEquals(
            setOf(HetTile(43, 31), HetTile(43, 32), HetTile(43, 33)),
            runtime.passBarriers.filter { it.angle == 1 }.mapTo(linkedSetOf()) { it.tile },
        )
        assertEquals(
            setOf(
                HetTile(31, 19),
                HetTile(14, 27),
                HetTile(14, 35),
                HetTile(47, 36),
                HetTile(31, 43),
            ),
            runtime.pickaxeStatueAnchors.mapTo(linkedSetOf()) { it.tile },
        )
        assertTrue(
            runtime.pickaxeStatueAnchors.all {
                it.locId == 45468 && it.width == 3 && it.length == 3
            }
        )
    }

    @Test
    fun mirrorBarrierCasterShieldSealAndPickaxeLocFamiliesAreNotConflated() {
        val runtime = HetRevision240RuntimeLayout
        assertEquals(HetMirrorCacheLocs(45455, 45456, 45457), runtime.mirrorLocs)
        assertEquals(
            setOf(45462, 45464),
            runtime.barrierLocs.filter { it.playerBreakable }.mapTo(linkedSetOf()) { it.intact },
        )
        assertEquals(45466, runtime.CLEARED_BARRIER_LOC)
        assertEquals(45486, runtime.CASTER_STATUE_LOC)
        assertEquals(45485, runtime.SHIELDED_STATUE_LOC)
        assertEquals(setOf(45489, 45491), runtime.verifiedBrokenSealLocs)
        assertTrue(runtime.verifiedBrokenSealLocs.intersect(setOf(45462, 45464)).isEmpty())

        assertEquals(45468, runtime.pickaxeStatue.baseLoc)
        assertEquals(45469, runtime.pickaxeStatue.emptyActionLoc)
        assertEquals(14440, runtime.pickaxeStatue.controllingVarbit)
        assertEquals(24, runtime.pickaxeStatue.transforms.size)
        assertTrue(55808 in runtime.pickaxeStatue.transforms)
        assertTrue(55809 in runtime.pickaxeStatue.transforms)
    }

    @Test
    fun serverDynamicCoordinatesRemainExplicitlyUnresolved() {
        val runtime = HetRevision240RuntimeLayout
        assertEquals(
            HetDynamicBindingKey.entries.toSet(),
            runtime.unresolvedDynamicBindings.mapTo(linkedSetOf()) { it.key },
        )
        assertTrue(
            runtime.unresolvedDynamicBindings.all {
                it.cacheLocIds.isNotEmpty() && it.evidence.isNotBlank()
            }
        )
        val dynamicLocIds =
            runtime.unresolvedDynamicBindings
                .filterNot { it.key == HetDynamicBindingKey.ENTRY_ARRIVAL_TILE }
                .flatMapTo(linkedSetOf()) { it.cacheLocIds }
        assertTrue(dynamicLocIds.intersect(runtime.verifiedStaticMechanicLocIds).isEmpty())
    }

    @Test
    fun provenGridCanBindDynamicCoordinatesIntoPureHetModel() {
        val runtime = HetRevision240RuntimeLayout
        val layout =
            runtime.bindModel(
                HetDynamicModelBinding(
                    beamSource = HetTile(22, 32),
                    beamDirection = HetDirection.EAST,
                    shield = HetTile(42, 32),
                    barriers = listOf(HetBarrier(HetTile(25, 32), 8)),
                    fixedMirrors =
                        listOf(
                            HetMirror(
                                id = 10,
                                tile = HetTile(24, 32),
                                direction = HetMirrorDirection.NORTH_EAST,
                                movable = false,
                            )
                        ),
                    movableMirrorIds = setOf(1, 2, 3),
                )
            )

        assertEquals(runtime.bounds, layout.bounds)
        assertFalse(layout.source in layout.solidTiles)
        assertFalse(layout.shield in layout.solidTiles)
        assertTrue(HetTile(21, 32) in layout.solidTiles)

        assertFailsWith<IllegalArgumentException> {
            runtime.bindModel(
                HetDynamicModelBinding(
                    beamSource = HetTile(22, 32),
                    beamDirection = HetDirection.EAST,
                    shield = HetTile(42, 32),
                    fixedMirrors =
                        listOf(
                            HetMirror(
                                id = 10,
                                tile = HetTile(21, 32),
                                direction = HetMirrorDirection.NORTH_EAST,
                                movable = false,
                            )
                        ),
                )
            )
        }
    }
}
