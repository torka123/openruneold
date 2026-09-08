package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class DoomCarEyeSelectionTest {
    @Test
    fun `preferred eye is preserved when it produces a nonzero dash`() {
        val selected =
            DoomCarEyeSelection.select(
                bossSouthWest = CoordGrid(30, 30, 0),
                playerTile = CoordGrid(34, 32, 0),
                minimum = CoordGrid(24, 24, 0),
                maximum = CoordGrid(38, 38, 0),
            )

        assertEquals(CoordGrid(38, 32, 0), selected)
    }

    @Test
    fun `boundary clamp reflects an eye that would collapse onto the boss centre`() {
        val bossSouthWest = CoordGrid(36, 30, 0)
        val selected =
            DoomCarEyeSelection.select(
                bossSouthWest = bossSouthWest,
                playerTile = DoomCarGeometry.centre(bossSouthWest),
                minimum = CoordGrid(24, 30, 0),
                maximum = CoordGrid(38, 34, 0),
            )

        assertEquals(CoordGrid(34, 32, 0), selected)
        assertTrue(DoomCarGeometry.plan(bossSouthWest, selected, delve = 8).segments.isNotEmpty())
    }

    @Test
    fun `bounded compass fallback still guarantees a nonzero dash`() {
        val bossSouthWest = CoordGrid(36, 30, 0)
        val selected =
            DoomCarEyeSelection.select(
                bossSouthWest = bossSouthWest,
                playerTile = DoomCarGeometry.centre(bossSouthWest),
                minimum = CoordGrid(38, 28, 0),
                maximum = CoordGrid(38, 36, 0),
            )

        assertEquals(CoordGrid(38, 36, 0), selected)
        assertTrue(DoomCarGeometry.plan(bossSouthWest, selected, delve = 8).segments.isNotEmpty())
    }
}
