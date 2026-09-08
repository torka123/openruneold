package org.rsmod.content.skills.construction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@Test
fun `revision 240 viewer furniture choices use client script 1376 bit layout`() {
    val choices = listOf(0xA5, 0x53, 0xE1, 0x65, 0x1D, 0x12, 0x1B, 0x0F, 0x11)
    val packed = HouseViewerLayout.packFurnitureChoices(choices)
    val bits = { value: Int, start: Int, width: Int -> (value ushr start) and ((1 shl width) - 1) }
    val decoded =
        listOf(
            bits(packed[0], 15, 3) or
                (bits(packed[1], 0, 2) shl 3) or
                (bits(packed[2], 0, 3) shl 5),
            bits(packed[0], 18, 3) or
                (bits(packed[1], 2, 2) shl 3) or
                (bits(packed[2], 3, 3) shl 5),
            bits(packed[0], 21, 3) or
                (bits(packed[1], 4, 2) shl 3) or
                (bits(packed[2], 6, 3) shl 5),
            bits(packed[0], 24, 2) or
                (bits(packed[1], 6, 3) shl 2) or
                (bits(packed[2], 9, 2) shl 5),
            bits(packed[0], 26, 2) or (bits(packed[1], 9, 3) shl 2),
            bits(packed[0], 28, 2) or (bits(packed[1], 12, 3) shl 2),
            bits(packed[0], 30, 2) or (bits(packed[1], 15, 3) shl 2),
            bits(packed[1], 18, 5),
            bits(packed[1], 23, 5),
        )
    assertEquals(choices, decoded)
}

class HouseViewerLayoutTest {
    @Test
    fun `revision 240 viewer cells decode all three floors and reject border cells`() {
        assertEquals(HouseViewerCell(0, 0, 0), HouseViewerLayout.decodeCell(0))
        assertEquals(HouseViewerCell(7, 7, 0), HouseViewerLayout.decodeCell(70))
        assertNull(HouseViewerLayout.decodeCell(8))
        assertNull(HouseViewerLayout.decodeCell(80))
        assertEquals(HouseViewerCell(0, 0, 1), HouseViewerLayout.decodeCell(81))
        assertEquals(HouseViewerCell(0, 0, 2), HouseViewerLayout.decodeCell(162))
        assertNull(HouseViewerLayout.decodeCell(243))
        // Clicked map slots are zero-based; destination-highlight varbit values reserve zero.
        assertEquals(1, HouseViewerLayout.gridPos(0, 0, 0))
        assertEquals(71, HouseViewerLayout.gridPos(7, 7, 0))
        assertEquals(82, HouseViewerLayout.gridPos(0, 0, 1))
    }

    @Test
    fun `room local coordinates round trip through every rotation`() {
        for (rotation in 0..3) {
            for (x in 0..7) {
                for (z in 0..7) {
                    val rotated = HouseViewerLayout.rotateLocal(x, z, rotation)
                    assertEquals(
                        x to z,
                        HouseViewerLayout.unrotateLocal(rotated.first, rotated.second, rotation),
                    )
                }
            }
        }
        assertEquals(5 to 5, HouseViewerLayout.remapLocal(2, 5, 0, 1))
        assertEquals(5 to 5, HouseViewerLayout.rotateLocal(2, 5, 1))
        assertEquals(2 to 2, HouseViewerLayout.rotateLocal(2, 5, 3))
    }

    @Test
    fun `every viewer room receives a stable deterministic slot`() {
        val rooms =
            (0 until 38)
                .map { index ->
                    HouseRoom(
                        room = "ROOM_$index",
                        zoneX = index % 8,
                        zoneZ = (index / 8) % 8,
                        level = index / 32,
                        stableId = "room-$index",
                    )
                }
                .shuffled(java.util.Random(240L))

        val ordered = HouseViewerLayout.orderedRooms(rooms)

        assertEquals(38, ordered.size)
        assertEquals(
            rooms.map(HouseRoom::stableId).toSet(),
            ordered.map(HouseRoom::stableId).toSet(),
        )
        assertEquals(ordered, HouseViewerLayout.orderedRooms(ordered.reversed()))

        ordered.forEach { selected ->
            val rotated = HouseViewerLayout.rotateRooms(ordered, setOf(selected.stableId), 1)
            rotated.forEach { room ->
                val original = ordered.first { it.stableId == room.stableId }
                val expected =
                    if (room.stableId == selected.stableId) {
                        Math.floorMod(original.rotation + 1, 4)
                    } else {
                        original.rotation
                    }
                assertEquals(expected, room.rotation, room.stableId)
            }
        }
    }

    @Test
    fun `linked vertical rooms move as a stable identity stack`() {
        val rooms =
            listOf(
                HouseRoom("DUNGEON_STAIRS", 4, 4, 0, stableId = "basement"),
                HouseRoom("SKILL_HALL_DOWN", 4, 4, 1, stableId = "ground"),
                HouseRoom("SKILL_HALL_UP", 4, 4, 2, stableId = "upstairs"),
                HouseRoom("PARLOUR", 4, 5, 1, stableId = "parlour"),
            )
        val linked = HouseViewerLayout.linkedStack(rooms, rooms[1]) { it.room != "PARLOUR" }
        assertEquals(setOf("basement", "ground", "upstairs"), linked)
        assertEquals(
            setOf("parlour"),
            HouseViewerLayout.linkedStack(rooms, rooms[3]) { it.room != "PARLOUR" },
        )
    }

    @Test
    fun `moving rooms changes persisted coordinates without detaching furniture`() {
        val rooms =
            listOf(
                HouseRoom("GARDEN", 3, 3, 1, stableId = "garden"),
                HouseRoom("PARLOUR", 3, 4, 1, stableId = "parlour"),
                HouseRoom("BEDROOM", 3, 5, 1, stableId = "bedroom"),
            )
        val furniture =
            mapOf(
                "garden:CENTERPIECE" to "EXIT_PORTAL",
                "parlour:CURTAINS" to "OPULENT_CURTAINS",
                "bedroom:BED" to "GILDED_FOUR_POSTER",
            )

        val moved = HouseViewerLayout.moveRooms(rooms, setOf("bedroom"), 1, -1, 0)

        assertEquals(4, moved.single { it.stableId == "bedroom" }.zoneX)
        assertEquals(4, moved.single { it.stableId == "bedroom" }.zoneZ)
        assertEquals("GILDED_FOUR_POSTER", furniture["bedroom:BED"])
        assertEquals(rooms[0], moved[0])
        assertEquals(rooms[1], moved[1])
    }

    @Test
    fun `deleting a room removes only that stable room and its furniture`() {
        val rooms =
            listOf(
                HouseRoom("GARDEN", 3, 3, 1, stableId = "garden"),
                HouseRoom("PARLOUR", 3, 4, 1, stableId = "parlour"),
                HouseRoom("BEDROOM", 3, 5, 1, stableId = "bedroom"),
            )
        val furniture =
            mapOf(
                "garden:CENTERPIECE" to "EXIT_PORTAL",
                "parlour:CURTAINS" to "OPULENT_CURTAINS",
                "bedroom:BED" to "GILDED_FOUR_POSTER",
            )

        val deletion = HouseViewerLayout.deleteRooms(rooms, furniture, setOf("bedroom"))

        assertEquals(listOf("garden", "parlour"), deletion.rooms.map(HouseRoom::stableId))
        assertEquals(
            mapOf("garden:CENTERPIECE" to "EXIT_PORTAL", "parlour:CURTAINS" to "OPULENT_CURTAINS"),
            deletion.furniture,
        )
    }

    @Test
    fun `connectivity includes horizontal rooms and linked floors but rejects islands`() {
        val connectedRooms =
            listOf(
                HouseRoom("GARDEN", 3, 3, 1, stableId = "garden"),
                HouseRoom("PARLOUR", 3, 4, 1, stableId = "parlour"),
                HouseRoom("SKILL_HALL_DOWN", 4, 4, 1, stableId = "stairs-down"),
                HouseRoom("SKILL_HALL_UP", 4, 4, 2, stableId = "stairs-up"),
            )
        val connected =
            HouseViewerLayout.connectedRoomIds(connectedRooms) { it.room.startsWith("SKILL_HALL") }
        assertEquals(connectedRooms.map(HouseRoom::stableId).toSet(), connected)

        val withIsland = connectedRooms + HouseRoom("BEDROOM", 7, 7, 1, stableId = "island")
        assertEquals(
            connected,
            HouseViewerLayout.connectedRoomIds(withIsland) { it.room.startsWith("SKILL_HALL") },
        )
    }
}
