package org.rsmod.content.minigames.corruptedgauntlet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorruptedGauntletLayoutGeneratorTest {
    @Test
    fun `same seed creates byte-for-byte stable seven by seven layout`() {
        val first = CorruptedGauntletLayoutGenerator.generate(0xC0FFEE)
        val second = CorruptedGauntletLayoutGenerator.generate(0xC0FFEE)
        assertEquals(first, second)
        assertEquals(49, first.rooms.size)
        assertEquals((0..6).toSet(), first.rooms.map { it.grid.x }.toSet())
        assertEquals((0..6).toSet(), first.rooms.map { it.grid.z }.toSet())
    }

    @Test
    fun `different seeds produce different room plans`() {
        assertNotEquals(
            CorruptedGauntletLayoutGenerator.generate(1),
            CorruptedGauntletLayoutGenerator.generate(2),
        )
    }

    @Test
    fun `start is cardinally adjacent to the centre boss and uses dedicated chunks`() {
        repeat(128) { seed ->
            val layout = CorruptedGauntletLayoutGenerator.generate(seed.toLong())
            assertEquals(1, layout.start.manhattanDistance(layout.boss))
            assertEquals(CorruptedGauntletRoomKind.START, layout[layout.start].kind)
            assertEquals(CorruptedGauntletRoomKind.BOSS, layout[layout.boss].kind)
            assertEquals(246, layout[layout.start].sourceChunkX)
            assertEquals(708, layout[layout.start].sourceChunkZ)
            assertEquals(246, layout[layout.boss].sourceChunkX)
            assertEquals(710, layout[layout.boss].sourceChunkZ)
        }
    }

    @Test
    fun `every seed has exactly two of each demi boss on eligible outer edge rooms`() {
        repeat(128) { seed ->
            val layout = CorruptedGauntletLayoutGenerator.generate(seed.toLong())
            val demiRooms = layout.rooms.filter { it.kind == CorruptedGauntletRoomKind.DEMI_BOSS }
            assertEquals(6, demiRooms.size)
            CorruptedGauntletDemiBoss.entries.forEach { boss ->
                assertEquals(2, demiRooms.count { it.demiBoss == boss })
            }
            assertTrue(
                demiRooms.all { room ->
                    val point = room.grid
                    val outer = point.x == 0 || point.x == 6 || point.z == 0 || point.z == 6
                    val middleOfEdge = point.x in 2..4 || point.z in 2..4
                    outer && middleOfEdge && room.shape == CorruptedGauntletRoomShape.EDGE
                }
            )
        }
    }

    @Test
    fun `one non-start boss-adjacent room guarantees three fishing nodes`() {
        repeat(128) { seed ->
            val layout = CorruptedGauntletLayoutGenerator.generate(seed.toLong())
            val fishRooms = layout.rooms.filter { it.guaranteedFishNodes > 0 }
            assertEquals(1, fishRooms.size)
            assertEquals(3, fishRooms.single().guaranteedFishNodes)
            assertEquals(1, fishRooms.single().grid.manhattanDistance(layout.boss))
            assertNotEquals(layout.start, fishRooms.single().grid)
        }
    }

    @Test
    fun `all room chunks resolve to the verified source region`() {
        repeat(64) { seed ->
            val layout = CorruptedGauntletLayoutGenerator.generate(seed.toLong())
            layout.rooms.forEach { room ->
                val region = (room.sourceChunkX / 8 shl 8) or (room.sourceChunkZ / 8)
                assertEquals(CorruptedGauntletConfig.MAP_REGION, region)
                assertTrue(room.rotation in 0..3)
            }
        }
    }
}
