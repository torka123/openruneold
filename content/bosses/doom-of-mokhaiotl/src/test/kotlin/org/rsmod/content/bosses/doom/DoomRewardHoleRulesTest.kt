package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class DoomRewardHoleRulesTest {
    @Test
    fun `first candidate is the boss centre for normal placements`() {
        val candidates =
            DoomRewardHoleRules.candidates(
                bossSouthWest = CoordGrid(30, 30, 0),
                bossSize = 5,
                arenaSouthWest = CoordGrid(20, 20, 0),
                arenaNorthEast = CoordGrid(40, 40, 0),
            )

        assertEquals(CoordGrid(32, 32, 0), candidates.first())
    }

    @Test
    fun `corner placements produce only in-arena fallback candidates`() {
        val southWest = CoordGrid(20, 20, 0)
        val northEast = CoordGrid(40, 40, 0)
        val candidates =
            DoomRewardHoleRules.candidates(
                bossSouthWest = CoordGrid(39, 39, 0),
                bossSize = 5,
                arenaSouthWest = southWest,
                arenaNorthEast = northEast,
            )

        assertEquals(CoordGrid(40, 40, 0), candidates.first())
        assertTrue(candidates.all { it.x in 20..40 && it.z in 20..40 && it.level == 0 })
        assertTrue(candidates.size > 1)
    }
}
