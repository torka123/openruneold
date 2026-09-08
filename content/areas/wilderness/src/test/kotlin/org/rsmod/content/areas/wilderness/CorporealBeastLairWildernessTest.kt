package org.rsmod.content.areas.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.area.checker.CORPOREAL_BEAST_LAIR_MAX_WILDERNESS_LEVEL
import org.rsmod.api.area.checker.CORPOREAL_BEAST_LAIR_MIN_WILDERNESS_LEVEL
import org.rsmod.api.area.checker.corporealBeastLairWildernessLevel
import org.rsmod.api.area.checker.isInCorporealBeastLair
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.map.CoordGrid

class CorporealBeastLairWildernessTest {
    @Test
    fun `public corporeal beast lair is wilderness on floor two`() {
        val lairTiles =
            listOf(
                CoordGrid(2944, 4224, 2),
                CoordGrid(2964, 4254, 2),
                CoordGrid(3007, 4287, 2),
                CoordGrid(2944, 4352, 2),
                CoordGrid(2964, 4382, 2),
                CoordGrid(3007, 4415, 2),
            )

        assertEquals(20, CORPOREAL_BEAST_LAIR_MIN_WILDERNESS_LEVEL)
        assertEquals(23, CORPOREAL_BEAST_LAIR_MAX_WILDERNESS_LEVEL)
        assertTrue(lairTiles.all { it.isInCorporealBeastLair() })
        assertTrue(lairTiles.all { it.isInWildernessBasic() })
        assertTrue(lairTiles.all { WildernessAreaScript.usesServerWildernessLevelHud(it) })
    }

    @Test
    fun `corp entrance chamber stays fixed at level 20`() {
        val entranceTiles =
            listOf(CoordGrid(2944, 4224, 2), CoordGrid(2964, 4254, 2), CoordGrid(3007, 4287, 2))

        assertTrue(entranceTiles.all { it.corporealBeastLairWildernessLevel() == 20 })
    }

    @Test
    fun `wilderness level rises every eight tiles from entrance to corp`() {
        val levels =
            listOf(
                    CoordGrid(2964, 4382, 2),
                    CoordGrid(2971, 4382, 2),
                    CoordGrid(2972, 4382, 2),
                    CoordGrid(2979, 4382, 2),
                    CoordGrid(2980, 4382, 2),
                    CoordGrid(2987, 4382, 2),
                    CoordGrid(2988, 4382, 2),
                    CoordGrid(2993, 4382, 2),
                )
                .map { it.corporealBeastLairWildernessLevel() }

        assertEquals(listOf(20, 20, 21, 21, 22, 22, 23, 23), levels)
    }

    @Test
    fun `adjacent map squares and wrong planes remain outside this rule`() {
        val safeTiles =
            listOf(
                CoordGrid(2964, 4382, 1),
                CoordGrid(2964, 4254, 1),
                CoordGrid(2964, 4288, 2),
                CoordGrid(2943, 4382, 2),
                CoordGrid(3008, 4382, 2),
                CoordGrid(2964, 4416, 2),
            )

        assertFalse(safeTiles.any { it.isInCorporealBeastLair() })
        assertTrue(safeTiles.all { it.corporealBeastLairWildernessLevel() == -1 })
    }
}
