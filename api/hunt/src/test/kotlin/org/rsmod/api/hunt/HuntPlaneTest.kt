package org.rsmod.api.hunt

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class HuntPlaneTest {
    @Test
    fun `hunt discovery never crosses planes`() {
        val source = CoordGrid(3200, 3600, 0)
        assertTrue(sameHuntPlane(source, CoordGrid(3200, 3600, 0)))
        assertFalse(sameHuntPlane(source, CoordGrid(3200, 3600, 1)))
        assertFalse(sameHuntPlane(source, CoordGrid(3200, 3600, 2)))
        assertFalse(sameHuntPlane(source, CoordGrid(3200, 3600, 3)))
    }
}
