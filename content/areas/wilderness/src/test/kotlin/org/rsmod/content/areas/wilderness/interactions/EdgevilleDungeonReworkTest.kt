package org.rsmod.content.areas.wilderness.interactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.rsmod.api.area.checker.EDGEVILLE_DUNGEON_WILDERNESS_LEVEL
import org.rsmod.api.area.checker.edgevilleDungeonWildernessLevel
import org.rsmod.api.area.checker.isInEdgevilleDungeon
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.api.config.constants
import org.rsmod.content.areas.wilderness.WildernessAreaScript
import org.rsmod.map.CoordGrid

class EdgevilleDungeonReworkTest {
    @Test
    fun `the playable Edgeville dungeon is fixed level 30 Wilderness`() {
        assertEquals(30, EDGEVILLE_DUNGEON_WILDERNESS_LEVEL)
        val dungeonTiles =
            listOf(
                CoordGrid(3091, 9799),
                CoordGrid(3097, 9833),
                CoordGrid(3097, 9866),
                CoordGrid(3095, 9907),
                CoordGrid(3121, 9893),
                CoordGrid(3163, 9877),
                CoordGrid(3119, 9964),
                CoordGrid(3088, 9971),
            )
        for (coords in dungeonTiles) {
            assertTrue(coords.isInEdgevilleDungeon(), coords.toString())
            assertTrue(coords.isInWildernessBasic(), coords.toString())
            assertEquals(
                EDGEVILLE_DUNGEON_WILDERNESS_LEVEL,
                coords.edgevilleDungeonWildernessLevel(),
                coords.toString(),
            )
        }
    }

    @Test
    fun `every Edgeville dungeon section uses the server driven level hud`() {
        assertTrue(
            WildernessAreaScript.usesServerWildernessLevelHud(CoordGrid(3097, 9866)),
            "The southern section is below the revision-240 client's Wilderness cutoff.",
        )
        assertTrue(
            WildernessAreaScript.usesServerWildernessLevelHud(CoordGrid(3119, 9964)),
            "The northern section must also display the fixed server level instead of the old formula.",
        )
        assertFalse(WildernessAreaScript.usesServerWildernessLevelHud(CoordGrid(3097, 3468)))
        assertEquals(55, WildernessAreaScript.PVP_SERVER_LEVEL_COMPONENT)
        assertEquals(56, WildernessAreaScript.PVP_SERVER_RANGE_COMPONENT)
        assertEquals(27, WildernessAreaScript.PVP_LEVEL_Y)
        assertEquals(44, WildernessAreaScript.PVP_RANGE_Y)
        assertEquals("Level: 30", WildernessAreaScript.serverWildernessLevelText(30))
        assertEquals(
            "Combat: 71-126",
            WildernessAreaScript.serverWildernessCombatRangeText(71..126),
        )
        assertTrue(
            WildernessAreaScript.shouldRefreshServerWildernessLevelHud(
                false,
                -1,
                -1,
                -1,
                30,
                71,
                126,
            )
        )
        assertFalse(
            WildernessAreaScript.shouldRefreshServerWildernessLevelHud(
                true,
                30,
                71,
                126,
                30,
                71,
                126,
            ),
            "Unchanged HUD state must not be resent every server tick.",
        )
        assertTrue(
            WildernessAreaScript.shouldRefreshServerWildernessLevelHud(
                true,
                30,
                71,
                126,
                30,
                72,
                126,
            ),
            "The combat bracket must refresh when combat level changes.",
        )
        assertTrue(
            WildernessAreaScript.shouldRefreshServerWildernessLevelHud(
                true,
                20,
                81,
                121,
                21,
                80,
                122,
            ),
            "The Corp lair level must refresh when the player crosses a level boundary.",
        )
    }

    @Test
    fun `fixed level 30 produces the displayed pvp combat range`() {
        assertEquals(
            71..126,
            WildernessAreaScript.wildernessCombatLevelRange(
                combatLevel = 101,
                wildernessLevel = EDGEVILLE_DUNGEON_WILDERNESS_LEVEL,
            ),
        )
        assertEquals(
            3..40,
            WildernessAreaScript.wildernessCombatLevelRange(combatLevel = 10, wildernessLevel = 30),
        )
    }

    @Test
    fun `adjacent unrelated underground areas are excluded`() {
        val excluded =
            listOf(
                CoordGrid(3075, 9890),
                CoordGrid(3071, 9833),
                CoordGrid(3136, 9833),
                CoordGrid(3176, 9900),
                CoordGrid(3097, 3468),
                CoordGrid(3097, 9866, 1),
            )
        for (coords in excluded) {
            assertFalse(coords.isInEdgevilleDungeon(), coords.toString())
            assertEquals(-1, coords.edgevilleDungeonWildernessLevel(), coords.toString())
        }
    }

    @Test
    fun `monkey bars resolve both directions with level and xp`() {
        assertEquals(15, EdgevilleDungeonShortcutRules.MONKEY_BARS_LEVEL)
        assertEquals(20.0, EdgevilleDungeonShortcutRules.MONKEY_BARS_XP)
        assertEquals(4, EdgevilleDungeonShortcutRules.MONKEY_BAR_SOURCES.size)
        val north =
            assertNotNull(
                EdgevilleDungeonShortcutRules.monkeyBars(
                    CoordGrid(3119, 9964),
                    CoordGrid(3119, 9963),
                )
            )
        assertEquals(CoordGrid(3119, 9970), north.destination)
        assertEquals(constants.em_face_north, north.direction)
        val south =
            assertNotNull(
                EdgevilleDungeonShortcutRules.monkeyBars(
                    CoordGrid(3120, 9969),
                    CoordGrid(3120, 9970),
                )
            )
        assertEquals(CoordGrid(3120, 9963), south.destination)
        assertEquals(constants.em_face_south, south.direction)
    }

    @Test
    fun `Moss Giant pipe resolves both directions at level 51`() {
        assertEquals(51, EdgevilleDungeonShortcutRules.PIPE_LEVEL)
        assertEquals(2, EdgevilleDungeonShortcutRules.PIPE_SOURCES.size)
        val east =
            assertNotNull(
                EdgevilleDungeonShortcutRules.pipe(CoordGrid(3150, 9906), CoordGrid(3149, 9906))
            )
        assertEquals(CoordGrid(3154, 9906), east.destination)
        assertEquals(constants.em_face_east, east.direction)
        val west =
            assertNotNull(
                EdgevilleDungeonShortcutRules.pipe(CoordGrid(3153, 9906), CoordGrid(3154, 9906))
            )
        assertEquals(CoordGrid(3149, 9906), west.destination)
        assertEquals(constants.em_face_west, west.direction)
    }
}
