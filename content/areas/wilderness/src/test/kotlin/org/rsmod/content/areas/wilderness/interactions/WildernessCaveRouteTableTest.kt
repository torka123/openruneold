package org.rsmod.content.areas.wilderness.interactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class WildernessCaveRouteTableTest {
    @Test
    fun `every coordinate and option identifies one route`() {
        val keys = WildernessCaveRouteTable.routes.map { Triple(it.locId, it.option, it.source) }
        assertEquals(keys.size, keys.distinct().size)
        keys.forEach { (locId, option, source) ->
            assertNotNull(WildernessCaveRouteTable.route(locId, option, source))
        }
    }

    @Test
    fun `cave and vertical tables never register the same coordinate event`() {
        val caveKeys =
            WildernessCaveRouteTable.routes.map { Triple(it.locId, it.option, it.source) }.toSet()
        val verticalKeys =
            WildernessVerticalRouteTable.routes
                .filter { it.registerHandler }
                .map { Triple(it.locId, it.option, it.source) }
                .toSet()
        assertTrue(caveKeys.intersect(verticalKeys).isEmpty())
    }

    @Test
    fun `corp public cave round trip returns outside the entrance`() {
        val enter =
            WildernessCaveRouteTable.route(
                CORPOREAL_BEAST_SURFACE_CAVE,
                1,
                CORPOREAL_BEAST_SURFACE_ENTRANCE_TILE,
            )
        val exits =
            listOf(CoordGrid(2963, 4254, 2), CoordGrid(2963, 4382, 2)).map { source ->
                WildernessCaveRouteTable.route(CORPOREAL_BEAST_CAVE_EXIT, 1, source)
            }
        assertEquals(CoordGrid(2964, 4254, 2), enter?.destination)
        assertTrue(exits.all { it?.destination == enter?.source })
        assertTrue(exits.all { it?.destination == CORPOREAL_BEAST_SURFACE_ENTRANCE_TILE })
    }

    @Test
    fun `corp public passage links the entrance room and boss lair both ways`() {
        val enterLair =
            WildernessCaveRouteTable.route(CORPOREAL_BEAST_PASSAGE, 1, CoordGrid(2971, 4254, 2))
        val leaveLair =
            WildernessCaveRouteTable.route(CORPOREAL_BEAST_PASSAGE, 1, CoordGrid(2971, 4382, 2))

        assertEquals(CoordGrid(2974, 4382, 2), enterLair?.destination)
        assertEquals(CoordGrid(2970, 4254, 2), leaveLair?.destination)
        assertEquals("BIDIRECTIONAL_PUBLIC_PASSAGE", enterLair?.directionality)
        assertEquals("BIDIRECTIONAL_PUBLIC_PASSAGE", leaveLair?.directionality)
    }

    @Test
    fun `all three revenant entrances share the fee and are intentionally one way`() {
        val entries =
            WildernessCaveRouteTable.routes.filter {
                it.locId == REVENANT_CAVE_LOW_ENTRANCE ||
                    it.locId == REVENANT_CAVE_MIDDLE_ENTRANCE ||
                    it.locId == REVENANT_CAVE_HIGH_ENTRANCE
            }
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.revenantFee })
        assertTrue(entries.all { it.directionality == "INTENTIONAL_ONE_WAY_ENTRANCE" })
    }

    @Test
    fun `both revenant exits land outside their matching surface trapdoors`() {
        val exits =
            WildernessCaveRouteTable.routes.filter {
                it.locId == REVENANT_CAVE_LOW_EXIT || it.locId == REVENANT_CAVE_HIGH_EXIT
            }
        assertEquals(2, exits.size)
        assertTrue(exits.none { it.revenantFee })
        assertTrue(exits.all { it.directionality == "INTENTIONAL_ONE_WAY_EXIT" })
        assertEquals(
            CoordGrid(3103, 3654),
            WildernessCaveRouteTable.route(REVENANT_CAVE_LOW_EXIT, 1, CoordGrid(3218, 10058))
                ?.destination,
        )
        assertEquals(
            CoordGrid(3122, 3806),
            WildernessCaveRouteTable.route(REVENANT_CAVE_HIGH_EXIT, 1, CoordGrid(3244, 10215))
                ?.destination,
        )
    }

    @Test
    fun `slayer cave exits use their matching surface stair markers`() {
        assertEquals(
            CoordGrid(3259, 3662),
            WildernessCaveRouteTable.route(
                    WILDERNESS_SLAYER_CAVE_SOUTH_EXIT,
                    1,
                    CoordGrid(3384, 10050),
                )
                ?.destination,
        )
        assertEquals(
            CoordGrid(3293, 3749),
            WildernessCaveRouteTable.route(
                    WILDERNESS_SLAYER_CAVE_NORTH_EXIT,
                    1,
                    CoordGrid(3405, 10146),
                )
                ?.destination,
        )
    }

    @Test
    fun `both surface slayer cave staircases enter and round trip to their matching exits`() {
        val south =
            WildernessCaveRouteTable.route(
                WILDERNESS_SLAYER_CAVE_SOUTH_ENTRANCE,
                1,
                CoordGrid(3259, 3664),
            )
        val north =
            WildernessCaveRouteTable.route(
                WILDERNESS_SLAYER_CAVE_NORTH_ENTRANCE,
                1,
                CoordGrid(3293, 3746),
            )
        assertEquals(CoordGrid(3384, 10053), south?.destination)
        assertEquals(CoordGrid(3405, 10145), north?.destination)
        assertEquals(
            CoordGrid(3259, 3662),
            WildernessCaveRouteTable.route(
                    WILDERNESS_SLAYER_CAVE_SOUTH_EXIT,
                    1,
                    CoordGrid(3384, 10050),
                )
                ?.destination,
        )
        assertEquals(
            CoordGrid(3293, 3749),
            WildernessCaveRouteTable.route(
                    WILDERNESS_SLAYER_CAVE_NORTH_EXIT,
                    1,
                    CoordGrid(3405, 10146),
                )
                ?.destination,
        )
    }
}
