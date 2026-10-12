package org.rsmod.content.areas.wilderness.interactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.config.constants
import org.rsmod.map.CoordGrid

class WildernessEnvironmentalRouteTableTest {
    @Test
    fun `environmental route identities are unique`() {
        val routes = WildernessEnvironmentalRouteTable.routes
        assertEquals(routes.size, routes.distinctBy { Triple(it.locId, it.option, it.source) }.size)
    }

    @Test
    fun `all wilderness slayer cave crevices land beyond the opposite obstacle`() {
        val routes = WildernessEnvironmentalRouteTable.routes.filter { it.locId == 53259 }
        assertEquals(8, routes.size)
        assertEquals(
            mapOf(
                CoordGrid(3334, 10119) to CoordGrid(3334, 10122),
                CoordGrid(3334, 10121) to CoordGrid(3334, 10118),
                CoordGrid(3342, 10149) to CoordGrid(3342, 10154),
                CoordGrid(3342, 10153) to CoordGrid(3342, 10148),
                CoordGrid(3434, 10091) to CoordGrid(3434, 10094),
                CoordGrid(3434, 10093) to CoordGrid(3434, 10090),
                CoordGrid(3435, 10112) to CoordGrid(3435, 10116),
                CoordGrid(3435, 10115) to CoordGrid(3435, 10111),
            ),
            routes.associate { it.source to it.destinationA },
        )
        assertTrue(routes.all { it.level == 77 && it.mode == WildernessTraversalMode.Tunnel })
    }

    @Test
    fun `revenant cave difficulties use current level tiers`() {
        val routes = WildernessEnvironmentalRouteTable.routes.filter { it.locId == 31561 }
        assertEquals(5, routes.size)
        assertEquals(setOf(65, 75, 89), routes.map { it.level }.toSet())
        assertEquals(1, routes.count { it.level == 65 })
        assertEquals(3, routes.count { it.level == 75 })
        assertEquals(1, routes.count { it.level == 89 })
        assertTrue(routes.all { it.revenantShortcut && it.failureDamage == 4..11 })
        assertEquals(
            mapOf(
                CoordGrid(3180, 10209) to (CoordGrid(3180, 10206) to CoordGrid(3180, 10212)),
                CoordGrid(3220, 10086) to (CoordGrid(3220, 10083) to CoordGrid(3220, 10089)),
                CoordGrid(3200, 10136) to (CoordGrid(3198, 10136) to CoordGrid(3202, 10136)),
                CoordGrid(3241, 10145) to (CoordGrid(3239, 10145) to CoordGrid(3243, 10145)),
                CoordGrid(3202, 10196) to (CoordGrid(3200, 10196) to CoordGrid(3204, 10196)),
            ),
            routes.associate { it.source to (it.destinationA to it.destinationB) },
        )
    }

    @Test
    fun `revenant shortcuts expose their exact blocked-pillar interaction distance`() {
        val routes = WildernessEnvironmentalRouteTable.routes.filter { it.locId == 31561 }
        assertEquals(listOf(3, 3, 2, 2, 2), routes.map { it.interactionDistance })
        for (route in routes) {
            assertEquals(route.destinationA, revenantShortcutApproach(route, route.destinationA))
            assertEquals(route.destinationB, revenantShortcutApproach(route, route.destinationB!!))
            assertEquals(null, revenantShortcutApproach(route, route.source))
        }
    }

    @Test
    fun `revenant shortcut approach accepts nearby diagonal arrival tiles`() {
        val vertical =
            WildernessEnvironmentalRouteTable.routes.first {
                it.locId == 31561 && it.source == CoordGrid(3180, 10209)
            }
        assertEquals(
            CoordGrid(3180, 10206),
            revenantShortcutApproach(vertical, CoordGrid(3179, 10206)),
        )
        assertEquals(
            CoordGrid(3180, 10212),
            revenantShortcutApproach(vertical, CoordGrid(3181, 10212)),
        )

        val horizontal =
            WildernessEnvironmentalRouteTable.routes.first {
                it.locId == 31561 && it.source == CoordGrid(3200, 10136)
            }
        assertEquals(
            CoordGrid(3198, 10136),
            revenantShortcutApproach(horizontal, CoordGrid(3198, 10135)),
        )
        assertEquals(
            CoordGrid(3202, 10136),
            revenantShortcutApproach(horizontal, CoordGrid(3202, 10137)),
        )
    }

    @Test
    fun `revenant shortcut approach rejects remote clicks`() {
        val route =
            WildernessEnvironmentalRouteTable.routes.first {
                it.locId == 31561 && it.source == CoordGrid(3180, 10209)
            }
        assertEquals(null, revenantShortcutApproach(route, CoordGrid(3180, 10202)))
        assertEquals(null, revenantShortcutApproach(route, CoordGrid(3176, 10209)))
    }

    @Test
    fun `revenant shortcut forced movement faces its landing tile`() {
        assertEquals(
            constants.em_face_north,
            revenantShortcutDirection(CoordGrid(1, 1), CoordGrid(1, 2)),
        )
        assertEquals(
            constants.em_face_south,
            revenantShortcutDirection(CoordGrid(1, 2), CoordGrid(1, 1)),
        )
        assertEquals(
            constants.em_face_east,
            revenantShortcutDirection(CoordGrid(1, 1), CoordGrid(2, 1)),
        )
        assertEquals(
            constants.em_face_west,
            revenantShortcutDirection(CoordGrid(2, 1), CoordGrid(1, 1)),
        )
    }

    @Test
    fun `current wilderness stepping stone requirements are enforced`() {
        val levels =
            WildernessEnvironmentalRouteTable.routes
                .filter { it.locId in setOf(14917, 14918, 53237) }
                .associate { it.locId to it.level }
        assertEquals(82, levels[14917])
        assertEquals(74, levels[14918])
        assertEquals(72, levels[53237])
    }

    @Test
    fun `all four ditch tiles use animated exact movement`() {
        val routes = WildernessEnvironmentalRouteTable.routes.filter { it.locId == 23271 }
        assertEquals(4, routes.size)
        assertTrue(routes.all { it.mode == WildernessTraversalMode.ExactMove })
    }

    @Test
    fun `wilderness traversal animations match the intended obstacle actions`() {
        val routeAnimations =
            WildernessEnvironmentalRouteTable.routes
                .filter { it.locId in setOf(23271, 16545, 26406, 26404, 14917, 14918, 53237) }
                .groupBy { it.locId }
                .mapValues { (_, routes) -> routes.map { it.animation }.toSet() }
        assertEquals(setOf(6132), routeAnimations.getValue(23271))
        assertEquals(setOf(740), routeAnimations.getValue(16545))
        assertEquals(setOf(741), routeAnimations.getValue(14917))
    }

    @Test
    fun `all ferox barrier halves use the correct two-way boundary tiles`() {
        assertEquals(
            mapOf(
                (39653 to CoordGrid(3123, 3628)) to
                    (CoordGrid(3122, 3628) to CoordGrid(3123, 3628)),
                (39652 to CoordGrid(3123, 3629)) to
                    (CoordGrid(3122, 3629) to CoordGrid(3123, 3629)),
                (39652 to CoordGrid(3134, 3617)) to
                    (CoordGrid(3134, 3616) to CoordGrid(3134, 3617)),
                (39653 to CoordGrid(3135, 3617)) to
                    (CoordGrid(3135, 3616) to CoordGrid(3135, 3617)),
                (39653 to CoordGrid(3134, 3639)) to
                    (CoordGrid(3134, 3639) to CoordGrid(3134, 3640)),
                (39652 to CoordGrid(3135, 3639)) to
                    (CoordGrid(3135, 3639) to CoordGrid(3135, 3640)),
                (39652 to CoordGrid(3154, 3634)) to
                    (CoordGrid(3154, 3634) to CoordGrid(3155, 3634)),
                (39653 to CoordGrid(3154, 3635)) to (CoordGrid(3154, 3635) to CoordGrid(3155, 3635)),
            ),
            FEROX_BARRIERS,
        )

        for ((_, endpoints) in FEROX_BARRIERS) {
            val (first, second) = endpoints
            assertEquals(second, feroxBarrierDestination(first, first, second))
            assertEquals(first, feroxBarrierDestination(second, first, second))
            assertEquals(1, first.chebyshevDistance(second))
        }
    }

    @Test
    fun `ferox barriers use the clip matched run-through sequence timing and direction`() {
        assertEquals(10409, FEROX_BARRIER_ANIMATION)
        assertEquals(30, FEROX_BARRIER_MOVE_CYCLES)
        assertEquals(1, FEROX_BARRIER_CROSS_TICKS)

        assertEquals(
            constants.em_face_east,
            feroxBarrierDirection(
                CoordGrid(3122, 3628),
                CoordGrid(3123, 3628),
                CoordGrid(3123, 3628),
            ),
        )
        assertEquals(
            constants.em_face_west,
            feroxBarrierDirection(
                CoordGrid(3122, 3628),
                CoordGrid(3123, 3628),
                CoordGrid(3122, 3628),
            ),
        )
        assertEquals(
            constants.em_face_north,
            feroxBarrierDirection(
                CoordGrid(3134, 3616),
                CoordGrid(3134, 3617),
                CoordGrid(3134, 3617),
            ),
        )
        assertEquals(
            constants.em_face_south,
            feroxBarrierDirection(
                CoordGrid(3134, 3616),
                CoordGrid(3134, 3617),
                CoordGrid(3134, 3616),
            ),
        )
    }

    @Test
    fun `resource gate walks normally in both directions and shuts behind the player`() {
        assertEquals(RESOURCE_GATE_INSIDE, resourceGateDestination(RESOURCE_GATE_OUTSIDE))
        assertEquals(RESOURCE_GATE_OUTSIDE, resourceGateDestination(RESOURCE_GATE_INSIDE))
        assertEquals(RESOURCE_GATE_OUTSIDE, resourceGateDestination(RESOURCE_GATE_SOURCE))
        assertTrue(resourceGateChargesEntry(RESOURCE_GATE_OUTSIDE))
        assertFalse(resourceGateChargesEntry(RESOURCE_GATE_INSIDE))
        assertFalse(resourceGateChargesEntry(RESOURCE_GATE_SOURCE))
        assertEquals(RESOURCE_GATE_SOURCE.x, RESOURCE_GATE_INSIDE.x)
        assertEquals(RESOURCE_GATE_SOURCE.x, RESOURCE_GATE_OUTSIDE.x)
        assertEquals(2, RESOURCE_GATE_INSIDE.chebyshevDistance(RESOURCE_GATE_OUTSIDE))
        assertEquals(3, RESOURCE_GATE_OPEN_ROTATIONS)
        assertEquals(2, RESOURCE_GATE_CROSS_TICKS)
        assertEquals(1, RESOURCE_GATE_FINISH_WALK_TICKS)
    }
}
