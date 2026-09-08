package org.rsmod.game.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.entity.worldentity.WorldEntityInfoProtocol
import org.rsmod.map.CoordGrid

class WorldEntityTest {
    @Test
    fun `constructor and updates mask angles to protocol range`() {
        val entity = createEntity(angle = -1)
        val protocol = RecordingWorldEntityInfo()
        entity.infoProtocol = protocol

        assertEquals(WorldEntity.MAX_ANGLE, entity.angle)

        entity.updateAngle(WorldEntity.MAX_ANGLE + 18)
        entity.updateAngle(-2)

        assertEquals(WorldEntity.MAX_ANGLE - 1, entity.angle)
        assertEquals(listOf(17, WorldEntity.MAX_ANGLE - 1), protocol.angleUpdates)
    }

    @Test
    fun `tile to fine returns the center of positive zero and negative tiles`() {
        assertEquals(64, WorldEntity.tileToFine(0))
        assertEquals(192, WorldEntity.tileToFine(1))
        assertEquals(-64, WorldEntity.tileToFine(-1))
        assertEquals(411_456, WorldEntity.tileToFine(3_214))
    }

    @Test
    fun `coordinate update and teleport preserve state and forward distinct flags`() {
        val entity = createEntity()
        val protocol = RecordingWorldEntityInfo()
        entity.infoProtocol = protocol

        entity.updateCoord(level = 2, fineX = 1_600, fineZ = 3_264)

        assertEquals(2, entity.projectedLevel)
        assertEquals(1_600, entity.fineX)
        assertEquals(3_264, entity.fineZ)
        assertEquals(CoordGrid(12, 25, 2), entity.coords)
        assertEquals(CoordUpdate(2, 1_600, 3_264, teleport = false), protocol.coordUpdates.single())

        entity.teleport(level = 3, fineX = 448, fineZ = 576)

        assertEquals(3, entity.projectedLevel)
        assertEquals(448, entity.fineX)
        assertEquals(576, entity.fineZ)
        assertEquals(CoordGrid(3, 4, 3), entity.coords)
        assertEquals(
            listOf(
                CoordUpdate(2, 1_600, 3_264, teleport = false),
                CoordUpdate(3, 448, 576, teleport = true),
            ),
            protocol.coordUpdates,
        )
    }

    @Test
    fun `contains uses inclusive southwest and exclusive northeast deck boundaries`() {
        val entity =
            createEntity(
                sizeX = 2,
                sizeZ = 3,
                southWestZoneX = 100,
                southWestZoneZ = 200,
                minLevel = 1,
                maxLevel = 2,
            )

        assertTrue(entity.contains(CoordGrid(800, 1_600, 1)))
        assertTrue(entity.contains(CoordGrid(815, 1_623, 2)))

        assertFalse(entity.contains(CoordGrid(799, 1_600, 1)))
        assertFalse(entity.contains(CoordGrid(816, 1_600, 1)))
        assertFalse(entity.contains(CoordGrid(800, 1_599, 1)))
        assertFalse(entity.contains(CoordGrid(800, 1_624, 1)))
    }

    @Test
    fun `contains rejects coordinates outside active deck levels`() {
        val entity =
            createEntity(
                sizeX = 2,
                sizeZ = 3,
                southWestZoneX = 100,
                southWestZoneZ = 200,
                minLevel = 1,
                maxLevel = 2,
            )

        assertFalse(entity.contains(CoordGrid(800, 1_600, 0)))
        assertTrue(entity.contains(CoordGrid(800, 1_600, 1)))
        assertTrue(entity.contains(CoordGrid(800, 1_600, 2)))
        assertFalse(entity.contains(CoordGrid(800, 1_600, 3)))
    }

    private fun createEntity(
        sizeX: Int = 1,
        sizeZ: Int = 1,
        southWestZoneX: Int = 10,
        southWestZoneZ: Int = 20,
        minLevel: Int = 0,
        maxLevel: Int = WorldEntity.MAX_LEVEL,
        angle: Int = 0,
    ): WorldEntity =
        WorldEntity(
            id = 1,
            sizeX = sizeX,
            sizeZ = sizeZ,
            southWestZoneX = southWestZoneX,
            southWestZoneZ = southWestZoneZ,
            fineX = WorldEntity.tileToFine(3_200),
            fineZ = WorldEntity.tileToFine(3_200),
            minLevel = minLevel,
            maxLevel = maxLevel,
            angle = angle,
        )

    private data class CoordUpdate(
        val level: Int,
        val fineX: Int,
        val fineZ: Int,
        val teleport: Boolean,
    )

    private class RecordingWorldEntityInfo : WorldEntityInfoProtocol {
        val coordUpdates = mutableListOf<CoordUpdate>()
        val angleUpdates = mutableListOf<Int>()

        override fun updateCoord(level: Int, fineX: Int, fineZ: Int, teleport: Boolean) {
            coordUpdates += CoordUpdate(level, fineX, fineZ, teleport)
        }

        override fun updateAngle(angle: Int) {
            angleUpdates += angle
        }

        override fun setSequence(seq: Int, delay: Int) = Unit

        override fun setVisibleOps(ops: Byte) = Unit

        override fun setSpecific(specific: Boolean) = Unit
    }
}
