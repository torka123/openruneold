package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaScabarasRuntimeLayoutTest {
    @Test
    fun sourceRegionBindingUsesOpenRuneRevision240Room() {
        val runtime = ScabarasRevision240RuntimeLayout
        assertEquals(ToaRoom.SCABARAS.sourceRegion, runtime.SOURCE_REGION)
        assertEquals(14162, runtime.SOURCE_REGION)
        assertEquals(55, runtime.SOURCE_REGION_X)
        assertEquals(82, runtime.SOURCE_REGION_Z)
        assertEquals(ScabarasWorldTile(3539, 5289), runtime.sourceAbsolute(ScabarasTile(19, 41)))
    }

    @Test
    fun sideRoomSlotsAndMechanicGridsUseExplicitSourceCoordinates() {
        val runtime = ScabarasRevision240RuntimeLayout
        assertEquals(
            listOf(
                ScabarasSideRoomSlot.NORTH_FIRST,
                ScabarasSideRoomSlot.NORTH_SECOND,
                ScabarasSideRoomSlot.SOUTH_FIRST,
                ScabarasSideRoomSlot.SOUTH_SECOND,
            ),
            runtime.sideRoomSlots,
        )
        assertEquals(listOf(0, 1, 2, 3), runtime.sideRoomSlots.map { it.modelIndex })
        assertEquals(
            listOf(
                ScabarasTile(19, 41),
                ScabarasTile(36, 41),
                ScabarasTile(19, 29),
                ScabarasTile(36, 29),
            ),
            runtime.sideRooms.map { it.floor.northWest },
        )

        runtime.sideRooms.forEach { room ->
            assertEquals(49, room.floor.tiles().size)
            assertEquals(9, room.sequenceTiles.size)
            assertEquals(8, room.lightTiles.size)
            assertEquals(25, room.additionTiles.size)
            assertTrue(room.sequenceTiles.all { it in room.floor.tiles() })
            assertTrue(room.lightTiles.all { it in room.sequenceTiles })
            assertTrue(room.additionTiles.all { it in room.floor.tiles() })
            assertFalse(
                ScabarasTile(room.floor.northWest.x + 2, room.floor.northWest.z - 2) in
                    room.lightTiles
            )
        }

        val allFloors = runtime.sideRooms.map { it.floor.tiles() }
        assertEquals(allFloors.sumOf { it.size }, allFloors.flatten().toSet().size)
    }

    @Test
    fun branchPassagesAndMatchingFloorAreasMatchTheSourceMap() {
        val runtime = ScabarasRevision240RuntimeLayout
        assertEquals(
            ScabarasSourceLoc(45343, ScabarasTile(28, 35), shape = 0, angle = 1),
            runtime.branchPassages.getValue(ScabarasLane.NORTH),
        )
        assertEquals(
            ScabarasSourceLoc(45343, ScabarasTile(28, 29), shape = 0, angle = 3),
            runtime.branchPassages.getValue(ScabarasLane.SOUTH),
        )
        assertEquals(
            listOf(ScabarasTile(28, 36), ScabarasTile(27, 36), ScabarasTile(29, 36)),
            runtime.branchPassageLandings.getValue(ScabarasLane.NORTH),
        )
        assertEquals(
            listOf(ScabarasTile(28, 28), ScabarasTile(27, 28), ScabarasTile(29, 28)),
            runtime.branchPassageLandings.getValue(ScabarasLane.SOUTH),
        )
        assertTrue(
            runtime.branchPassageLandings.values.flatten().all { tile ->
                tile.x in 27..29 && tile.z in setOf(28, 36)
            }
        )
        assertEquals(
            ScabarasFloorArea(48..54, 35..41),
            runtime.matchingBoardFloorAreas.getValue(ScabarasMatchingBoard.NORTH),
        )
        assertEquals(
            ScabarasFloorArea(48..54, 23..29),
            runtime.matchingBoardFloorAreas.getValue(ScabarasMatchingBoard.SOUTH),
        )
        assertTrue(
            runtime.matchingBoardFloorAreas
                .getValue(ScabarasMatchingBoard.NORTH)
                .tiles()
                .intersect(
                    runtime.matchingBoardFloorAreas.getValue(ScabarasMatchingBoard.SOUTH).tiles()
                )
                .isEmpty()
        )
    }

    @Test
    fun puzzleCacheFamiliesUseExplicitRevision240Mappings() {
        val runtime = ScabarasRevision240RuntimeLayout
        assertEquals(ScabarasSequenceCache(45338, 45340, 45341, 45342), runtime.sequenceCache)
        assertEquals(ScabarasLightsCache(45344, 45384), runtime.lightsCache)
        assertEquals(45339, runtime.ADDITION_TABLET_LOC)
        assertEquals(45355, runtime.MATCHING_FINAL_BUTTON_LOC)
        assertEquals(45374, runtime.MATCHING_DIVIDER_LOC)
        assertEquals(ScabarasObeliskCache(11698, 11699), runtime.obelisks)
        assertEquals(11697, runtime.RANGED_SCARAB_NPC)
        assertEquals(45354, runtime.RANGED_SCARAB_SWARM_LOC)

        assertEquals((1..9).toList(), runtime.additionTiles.map { it.value })
        assertEquals((45345..45353).toList(), runtime.additionTiles.map { it.groundLoc })
        assertEquals(
            listOf(45388, 45389, 45390, 45391, 45392, 45393, 45386, 45394, 45395),
            runtime.additionTiles.map { it.activeEffectLoc },
        )

        assertEquals((0..8).toList(), runtime.matchingTiles.map { it.modelSymbol })
        assertEquals((1..9).toList(), runtime.matchingTiles.map { it.cacheOrdinal })
        assertEquals((45356..45364).toList(), runtime.matchingTiles.map { it.hiddenButtonLoc })
        assertEquals((45365..45373).toList(), runtime.matchingTiles.map { it.revealedSymbolLoc })
        assertEquals(
            listOf(45388, 45389, 45386, 45391, 45392, 45387, 45393, 45394, 45395),
            runtime.matchingTiles.map { it.matchedEffectLoc },
        )
    }

    @Test
    fun serverOnlyPlacementsRemainExplicitlyUnresolved() {
        val runtime = ScabarasRevision240RuntimeLayout
        assertEquals(
            ScabarasDynamicBindingKey.entries.toSet(),
            runtime.unresolvedDynamicBindings.mapTo(linkedSetOf()) { it.key },
        )
        assertTrue(
            runtime.unresolvedDynamicBindings.all {
                it.assetIds.isNotEmpty() && it.evidence.isNotBlank()
            }
        )
        val unresolvedLocIds =
            runtime.unresolvedDynamicBindings
                .filter { it.assetKind == ScabarasCacheAssetKind.LOC }
                .flatMapTo(linkedSetOf()) { it.assetIds }
        assertTrue(unresolvedLocIds.intersect(runtime.verifiedStaticMechanicLocIds).isEmpty())
        assertEquals(
            setOf(runtime.RANGED_SCARAB_NPC),
            runtime.unresolvedDynamicBindings
                .single { it.key == ScabarasDynamicBindingKey.RANGED_SCARAB_SPAWN_TILES }
                .assetIds,
        )
    }

    @Test
    fun modelSideRoomOrderBindsToExplicitMapSlots() {
        val solutions = ScabarasEncounter(seed = 0x5CA8A5L, partySize = 1).solutions
        val bound = ScabarasRevision240RuntimeLayout.bindSideRooms(solutions)

        assertEquals(4, bound.size)
        assertEquals(solutions.sideRooms, bound.sortedBy { it.slot.modelIndex }.map { it.puzzle })
        assertEquals(
            setOf(
                ScabarasPuzzle.SEQUENCE,
                ScabarasPuzzle.LIGHTS,
                ScabarasPuzzle.OBELISKS,
                ScabarasPuzzle.ADDITION,
            ),
            bound.mapTo(linkedSetOf()) { it.puzzle },
        )
        assertEquals(
            9,
            bound.single { it.puzzle == ScabarasPuzzle.SEQUENCE }.verifiedInteractionTiles?.size,
        )
        assertEquals(
            8,
            bound.single { it.puzzle == ScabarasPuzzle.LIGHTS }.verifiedInteractionTiles?.size,
        )
        assertEquals(
            25,
            bound.single { it.puzzle == ScabarasPuzzle.ADDITION }.verifiedInteractionTiles?.size,
        )
        assertNull(bound.single { it.puzzle == ScabarasPuzzle.OBELISKS }.verifiedInteractionTiles)
    }
}
