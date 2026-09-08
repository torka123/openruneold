package org.rsmod.game.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.rsmod.map.CoordGrid

class WorldEntityListTest {
    @Test
    fun `allocator reserves root world slot zero`() {
        val entities = WorldEntityList()

        assertEquals(WorldEntityList.CAPACITY, entities.capacity)
        assertEquals(WorldEntityList.SLOT_PADDING, entities.slotPadding)
        assertNull(entities[0])
        assertEquals(1, entities.nextFreeSlot())

        entities[1] = createEntity(id = 1)

        assertEquals(2, entities.nextFreeSlot())
        assertNull(entities[0])
    }

    @Test
    fun `allocator exhausts protocol capacity and reuses a removed slot`() {
        val entities = WorldEntityList()
        for (slot in WorldEntityList.SLOT_PADDING until WorldEntityList.CAPACITY) {
            entities[slot] = createEntity(id = slot)
        }

        assertNull(entities.nextFreeSlot())

        val removedSlot = WorldEntityList.CAPACITY / 2
        entities.remove(removedSlot)

        assertEquals(removedSlot, entities.nextFreeSlot())

        val replacement = createEntity(id = -removedSlot)
        entities[removedSlot] = replacement

        assertSame(replacement, entities[removedSlot])
        assertNull(entities.nextFreeSlot())
        assertNull(entities[0])
    }

    @Test
    fun `find at resolves deck footprints and ignores outside coordinates`() {
        val entities = WorldEntityList()
        val first =
            createEntity(
                id = 1,
                sizeX = 1,
                sizeZ = 1,
                southWestZoneX = 100,
                southWestZoneZ = 200,
            )
        val second =
            createEntity(
                id = 2,
                sizeX = 2,
                sizeZ = 1,
                southWestZoneX = 300,
                southWestZoneZ = 400,
            )
        entities[1] = first
        entities[2] = second

        assertSame(first, entities.findAt(CoordGrid(800, 1_600, 0)))
        assertSame(first, entities.findAt(CoordGrid(807, 1_607, 3)))
        assertSame(second, entities.findAt(CoordGrid(2_400, 3_200, 0)))
        assertSame(second, entities.findAt(CoordGrid(2_415, 3_207, 3)))

        assertNull(entities.findAt(CoordGrid(808, 1_600, 0)))
        assertNull(entities.findAt(CoordGrid(2_416, 3_200, 0)))
        assertNull(entities.findAt(CoordGrid(2_400, 3_208, 0)))
    }

    private fun createEntity(
        id: Int,
        sizeX: Int = 1,
        sizeZ: Int = 1,
        southWestZoneX: Int = id and 0x3FF,
        southWestZoneZ: Int = (id ushr 10) and 0x3FF,
    ): WorldEntity =
        WorldEntity(
            id = id,
            sizeX = sizeX,
            sizeZ = sizeZ,
            southWestZoneX = southWestZoneX,
            southWestZoneZ = southWestZoneZ,
            fineX = 0,
            fineZ = 0,
        )
}
