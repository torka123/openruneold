package org.rsmod.content.slayer.tower

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class SlayerTowerRoutesTest {
    @Test
    fun `tower bounds include all three playable floors`() {
        assertTrue(SlayerTowerRoutes.contains(CoordGrid(3430, 3550, 0)))
        assertTrue(SlayerTowerRoutes.contains(CoordGrid(3430, 3550, 1)))
        assertTrue(SlayerTowerRoutes.contains(CoordGrid(3430, 3550, 2)))
        assertFalse(SlayerTowerRoutes.contains(CoordGrid(3430, 3550, 3)))
    }

    @Test
    fun `up and down actions resolve regardless of punctuation`() {
        assertEquals(SlayerTowerDirection.Up, SlayerTowerRoutes.directionForAction("Climb-up"))
        assertEquals(SlayerTowerDirection.Up, SlayerTowerRoutes.directionForAction("Ascend"))
        assertEquals(SlayerTowerDirection.Down, SlayerTowerRoutes.directionForAction("Climb down"))
        assertEquals(SlayerTowerDirection.Down, SlayerTowerRoutes.directionForAction("Descend"))
    }

    @Test
    fun `ground and top floors choose the only valid direction`() {
        assertEquals(SlayerTowerDirection.Up, SlayerTowerRoutes.defaultDirection(0))
        assertNull(SlayerTowerRoutes.defaultDirection(1))
        assertEquals(SlayerTowerDirection.Down, SlayerTowerRoutes.defaultDirection(2))
    }

    @Test
    fun `routes never leave the tower floor range`() {
        assertEquals(1, SlayerTowerRoutes.destinationLevel(0, SlayerTowerDirection.Up))
        assertEquals(2, SlayerTowerRoutes.destinationLevel(1, SlayerTowerDirection.Up))
        assertEquals(1, SlayerTowerRoutes.destinationLevel(2, SlayerTowerDirection.Down))
        assertEquals(0, SlayerTowerRoutes.destinationLevel(1, SlayerTowerDirection.Down))
        assertNull(SlayerTowerRoutes.destinationLevel(0, SlayerTowerDirection.Down))
        assertNull(SlayerTowerRoutes.destinationLevel(2, SlayerTowerDirection.Up))
    }
}
