package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.construction.data.DonorConstructionData
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid

class ConstructionRoomHotspotTest {
    @Test
    fun everyRevision240HouseDoorHotspotExposesBuild() {
        val ids =
            ServerCacheManager.getObjects()
                .values
                .filter { it.name.equals("Door hotspot", ignoreCase = true) }
                .mapTo(sortedSetOf()) { it.id }
        val expected =
            ((15305..15322).toSet() +
                    setOf(
                        27068,
                        27069,
                        37436,
                        37437,
                        37617,
                        37618,
                        40768,
                        40769,
                        56143,
                        56144,
                        56145,
                        56146,
                    ))
                .toSortedSet()
        assertEquals(expected, ids)
        ids.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            assertTrue(
                (0 until 5).any { type.actions.getOpOrNull(it).equals("Build", ignoreCase = true) },
                "Door hotspot " + id + " has no Build option: " + type.actions,
            )
        }
    }

    @Test
    fun everyHouseStyleUsesDistinctClosedAndOpenedDoorObjects() {
        HouseStyle.entries.forEach { style ->
            val pairs =
                listOf(
                    style.doorLocId1 to style.openedDoorLocId1,
                    style.doorLocId2 to style.openedDoorLocId2,
                )
            pairs.forEach { (closedId, openedId) ->
                assertFalse(closedId == openedId, style.name)
                val closed = requireNotNull(ServerCacheManager.getObject(closedId))
                val opened = requireNotNull(ServerCacheManager.getObject(openedId))
                assertTrue(
                    (0 until 5).any { closed.actions.getOpOrNull(it).equals("Open", true) },
                    "Closed ${style.name} door $closedId does not expose Open",
                )
                assertTrue(
                    (0 until 5).any { opened.actions.getOpOrNull(it).equals("Close", true) },
                    "Opened ${style.name} door $openedId does not expose Close",
                )
            }
        }
    }

    @Test
    fun everyDoubleDoorFacingFoldsBothLeavesAwayFromTheDoorwayCentre() {
        data class Doorway(
            val angle: LocAngle,
            val left: CoordGrid,
            val right: CoordGrid,
            val expectedLeft: ConstructionDoorPlacement,
            val expectedRight: ConstructionDoorPlacement,
        )

        val doorways =
            listOf(
                Doorway(
                    LocAngle.West,
                    CoordGrid(10, 10),
                    CoordGrid(10, 11),
                    ConstructionDoorPlacement(CoordGrid(9, 10), LocAngle.South),
                    ConstructionDoorPlacement(CoordGrid(9, 11), LocAngle.North),
                ),
                Doorway(
                    LocAngle.North,
                    CoordGrid(10, 10),
                    CoordGrid(11, 10),
                    ConstructionDoorPlacement(CoordGrid(10, 11), LocAngle.West),
                    ConstructionDoorPlacement(CoordGrid(11, 11), LocAngle.East),
                ),
                Doorway(
                    LocAngle.East,
                    CoordGrid(10, 11),
                    CoordGrid(10, 10),
                    ConstructionDoorPlacement(CoordGrid(11, 11), LocAngle.North),
                    ConstructionDoorPlacement(CoordGrid(11, 10), LocAngle.South),
                ),
                Doorway(
                    LocAngle.South,
                    CoordGrid(11, 10),
                    CoordGrid(10, 10),
                    ConstructionDoorPlacement(CoordGrid(11, 9), LocAngle.East),
                    ConstructionDoorPlacement(CoordGrid(10, 9), LocAngle.West),
                ),
            )

        doorways.forEach { doorway ->
            assertEquals(
                doorway.right,
                constructionDoorRightLeafCoords(doorway.left, doorway.angle),
            )
            val left = openedConstructionDoorPlacement(doorway.left, doorway.angle, true)
            val right = openedConstructionDoorPlacement(doorway.right, doorway.angle, false)
            assertEquals(doorway.expectedLeft, left)
            assertEquals(doorway.expectedRight, right)
            assertNotEquals(
                left.coords,
                right.coords,
                doorway.angle.toString() + " leaves overlapped",
            )
            assertNotEquals(
                left.angle,
                right.angle,
                doorway.angle.toString() + " leaves faced together",
            )
        }
    }

    @Test
    fun chapelWindowHotspotUsesEveryRevision240PositionalVariant() {
        val window = DonorConstructionData.hotspots.single { it.name == "WINDOW" }
        assertEquals(
            listOf(13733, 13728, 13732, 13729, 13730, 13731, 27070),
            window.locIds.toList(),
        )
        window.locIds.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            assertTrue(type.name.equals("Window space", ignoreCase = true))
            assertTrue(
                (0 until 5).any { type.actions.getOpOrNull(it).equals("Build", ignoreCase = true) },
                "Window hotspot " + id + " has no Build option: " + type.actions,
            )
        }
        window.buildables.forEach { name ->
            val buildable = DonorConstructionData.buildables.single { it.name == name }
            assertEquals(window.locIds.size, buildable.builtLocIds.size, name)
        }
    }

    @Test
    fun defaultPlayerHouseDoesNotRelyOnAllRoomsTestFixture() {
        val blueprint = HouseBlueprint.default()
        assertEquals(setOf("GARDEN", "PARLOUR"), blueprint.rooms.mapTo(mutableSetOf()) { it.room })
        assertEquals(2, blueprint.rooms.size)
        assertFalse(blueprint.rooms.any { it.room == "CHAPEL" })
    }

    @Test
    fun rawBuildHotspotsNeverOverlapBuiltFurnitureObjects() {
        val hotspotIds =
            DonorConstructionData.hotspots.flatMapTo(mutableSetOf()) { it.locIds.asIterable() }
        val builtIds =
            DonorConstructionData.buildables
                .flatMapTo(mutableSetOf()) { it.builtLocIds.asIterable() }
                .filterTo(mutableSetOf()) { it > 0 }
        assertTrue(hotspotIds.intersect(builtIds).isEmpty())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadRevision240Cache() {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            System.setProperty("user.dir", root.toString())
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) {
                    GameValProvider.load(root.toString() + java.io.File.separator)
                    ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
                }
            }
        }
    }
}
