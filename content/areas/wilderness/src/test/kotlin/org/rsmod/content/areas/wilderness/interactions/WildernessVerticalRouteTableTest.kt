package org.rsmod.content.areas.wilderness.interactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WildernessVerticalRouteTableTest {
    @Test
    fun `every registered route key is unique`() {
        val duplicates =
            WildernessVerticalRouteTable.routes
                .groupBy { Triple(it.locId, it.option, it.source) }
                .filterValues { it.size > 1 }
        assertTrue(duplicates.isEmpty(), "Duplicate vertical routes: $duplicates")
    }

    @Test
    fun `every bidirectional route has a reciprocal source and destination anchor`() {
        val routes = WildernessVerticalRouteTable.routes
        for (route in routes.filter { it.directionality == "BIDIRECTIONAL" }) {
            val reverseId = assertNotNull(route.reverseLocId, "Missing reverse id for $route")
            val reverseOption =
                assertNotNull(route.reverseOption, "Missing reverse option for $route")
            val reverse =
                routes.firstOrNull {
                    it.locId == reverseId &&
                        it.option == reverseOption &&
                        it.source.level == route.destinationAnchor.level &&
                        kotlin.math.abs(it.source.x - route.destinationAnchor.x) <= 1 &&
                        kotlin.math.abs(it.source.z - route.destinationAnchor.z) <= 1
                }
            assertNotNull(reverse, "Missing reverse route for $route")
            assertEquals(
                route.source.level,
                reverse.destinationAnchor.level,
                "Round-trip plane mismatch for $route",
            )
            assertTrue(
                kotlin.math.abs(route.source.x - reverse.destinationAnchor.x) <= 1 &&
                    kotlin.math.abs(route.source.z - reverse.destinationAnchor.z) <= 1,
                "Round-trip landing is not adjacent to its source for $route",
            )
        }
    }

    @Test
    fun `vertical destinations stay on valid planes`() {
        for (route in WildernessVerticalRouteTable.routes) {
            assertTrue(route.destinationAnchor.level in 0..3, "Invalid destination plane: $route")
        }
    }

    @Test
    fun `middle stairs and ladders map climb up to option two and down to option three`() {
        WildernessVerticalRouteTable.choices.forEach { choice ->
            assertEquals(2, choice.up.option)
            choice.down?.let { assertEquals(3, it.option) }
        }
    }

    @Test
    fun `kbd mirror ladders pair the surface and poison spider room objects`() {
        val expected =
            mapOf(
                Triple(18987, 1, org.rsmod.map.CoordGrid(3017, 3849)) to
                    org.rsmod.map.CoordGrid(3069, 10255),
                Triple(18988, 1, org.rsmod.map.CoordGrid(3069, 10256)) to
                    org.rsmod.map.CoordGrid(3017, 3848),
                Triple(18989, 1, org.rsmod.map.CoordGrid(3069, 3856)) to
                    org.rsmod.map.CoordGrid(3017, 10248),
                Triple(18990, 1, org.rsmod.map.CoordGrid(3017, 10249)) to
                    org.rsmod.map.CoordGrid(3069, 3855),
            )
        expected.forEach { (identity, destination) ->
            val route =
                WildernessVerticalRouteTable.route(
                    locId = identity.first,
                    option = identity.second,
                    source = identity.third,
                )
            assertNotNull(route)
            assertEquals(destination, route.destinationAnchor)
            assertTrue(!route.preserveApproachOffset)
        }
    }

    @Test
    fun `brain boat ladder is blocked at both exact map positions only`() {
        assertEquals(
            setOf(
                22274 to org.rsmod.map.CoordGrid(3363, 3625, 0),
                22275 to org.rsmod.map.CoordGrid(3363, 3625, 2),
            ),
            WildernessVerticalRouteTable.blockedRoutes,
        )
        assertTrue(
            WildernessVerticalRouteTable.routes.none { it.locId == 22274 || it.locId == 22275 }
        )
    }
}
