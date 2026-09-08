package org.rsmod.api.instances

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class InstanceBoundsTest {
    @Test
    fun `large instance occupants remain tracked beyond the old entrance radius`() {
        val southWest = CoordGrid(6_432, 5_280, 0)
        val northEast = CoordGrid(6_752, 5_600, 0)
        val deepInsideLargeInstance = CoordGrid(6_650, 5_510, 0)

        assertTrue(isWithinInstanceBounds(deepInsideLargeInstance, southWest, northEast))
    }

    @Test
    fun `instance bounds use the same half-open edges as region allocation`() {
        val southWest = CoordGrid(6_432, 5_280, 0)
        val northEast = CoordGrid(6_752, 5_600, 0)

        assertTrue(isWithinInstanceBounds(southWest, southWest, northEast))
        assertTrue(
            isWithinInstanceBounds(
                CoordGrid(northEast.x - 1, northEast.z - 1, 0),
                southWest,
                northEast,
            )
        )
        assertFalse(isWithinInstanceBounds(CoordGrid(northEast.x, 5_400, 0), southWest, northEast))
        assertFalse(isWithinInstanceBounds(CoordGrid(6_500, northEast.z, 0), southWest, northEast))
    }
}
