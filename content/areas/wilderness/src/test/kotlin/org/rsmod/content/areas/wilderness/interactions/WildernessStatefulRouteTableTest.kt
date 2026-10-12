package org.rsmod.content.areas.wilderness.interactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class WildernessStatefulRouteTableTest {
    @Test
    fun `lever route identities are unique`() {
        val routes = WildernessLeverRouteTable.routes
        assertEquals(routes.size, routes.distinctBy { Triple(it.locId, it.source, 1) }.size)
        routes.forEach { assertNotEquals(it.source, it.destination) }
    }

    @Test
    fun `every bidirectional lever has a reverse source`() {
        val routes = WildernessLeverRouteTable.routes
        for (route in routes.filter { it.directionality == "BIDIRECTIONAL" }) {
            val reverse = routes.single { it.locId == route.reverseLocId }
            assertTrue(reverse.destination.chebyshevDistance(route.source) <= 2)
            assertTrue(route.destination.chebyshevDistance(reverse.source) <= 2)
        }
    }

    @Test
    fun `kbd entrance and exit levers are exact reciprocal routes`() {
        val entrance = WildernessLeverRouteTable.routes.single { it.locId == 1816 }
        val exit = WildernessLeverRouteTable.routes.single { it.locId == 1817 }
        assertEquals(CoordGrid(3067, 10253), entrance.source)
        assertEquals(CoordGrid(2273, 4680), entrance.destination)
        assertEquals(CoordGrid(2271, 4680), exit.source)
        assertEquals(CoordGrid(3067, 10254), exit.destination)
    }

    @Test
    fun `all discovered webs and Wilderness metal gate halves are covered`() {
        assertEquals(10, WEB_SOURCES.size)
        assertEquals(14, WILDERNESS_METAL_GATE_PAIRS.size)
        assertEquals(28, WILDERNESS_METAL_GATE_STATES.size)
        assertEquals(
            WILDERNESS_METAL_GATE_STATES.size,
            WILDERNESS_METAL_GATE_STATES.distinctBy {
                    Triple(it.closed.objectId, it.closed.coords, it.closed.shape)
                }
                .size,
        )
        assertEquals(
            WILDERNESS_METAL_GATE_STATES.size,
            WILDERNESS_METAL_GATE_STATES.distinctBy {
                    Triple(it.opened.objectId, it.opened.coords, it.opened.shape)
                }
                .size,
        )
        assertEquals(
            setOf(1568, 1569, 1727, 1728),
            WILDERNESS_METAL_GATE_STATES.map { it.closed.objectId }.toSet(),
        )
        assertEquals(
            setOf(1571, 1572),
            WILDERNESS_METAL_GATE_STATES.map { it.opened.objectId }.toSet(),
        )
        assertEquals(30, METAL_GATE_AUTOCLOSE_TICKS)
    }

    @Test
    fun `permanent knife is outside the western Lava Maze web and always cuts it`() {
        val web = CoordGrid(3030, 3852)
        assertTrue(web in WEB_SOURCES)
        assertEquals(CoordGrid(3031, 3849), LAVA_MAZE_WEB_KNIFE_SPAWN)
        assertTrue(LAVA_MAZE_WEB_KNIFE_SPAWN.chebyshevDistance(web) <= 3)
        assertTrue(canAttemptWebSlash(hasKnife = true, hasWildernessSword = false, slashBonus = 0))
        assertTrue(guaranteesWebSlash(hasKnife = true, hasWildernessSword = false))
        assertFalse(
            canAttemptWebSlash(hasKnife = false, hasWildernessSword = false, slashBonus = 0)
        )
        assertFalse(guaranteesWebSlash(hasKnife = false, hasWildernessSword = false))
    }

    @Test
    fun `every Wilderness metal gate has its exact side specific open state`() {
        val expected =
            listOf(
                gate(
                    "western surface gate",
                    1727,
                    CoordGrid(2948, 3904),
                    1728,
                    CoordGrid(2947, 3904),
                    LocAngle.South,
                    1571,
                    CoordGrid(2948, 3903),
                    1572,
                    CoordGrid(2947, 3903),
                    LocAngle.East,
                ),
                gate(
                    "north-west surface gate",
                    1727,
                    CoordGrid(3008, 3849),
                    1728,
                    CoordGrid(3008, 3850),
                    LocAngle.West,
                    1571,
                    CoordGrid(3007, 3849),
                    1572,
                    CoordGrid(3007, 3850),
                    LocAngle.South,
                ),
                gate(
                    "western central surface gate",
                    1727,
                    CoordGrid(3071, 3857),
                    1728,
                    CoordGrid(3071, 3856),
                    LocAngle.East,
                    1571,
                    CoordGrid(3072, 3857),
                    1572,
                    CoordGrid(3072, 3856),
                    LocAngle.North,
                ),
                gate(
                    "central surface metal gate",
                    1568,
                    CoordGrid(3075, 3867),
                    1569,
                    CoordGrid(3076, 3867),
                    LocAngle.North,
                    1571,
                    CoordGrid(3075, 3868),
                    1572,
                    CoordGrid(3076, 3868),
                    LocAngle.West,
                ),
                gate(
                    "eastern central surface gate",
                    1727,
                    CoordGrid(3202, 3856),
                    1728,
                    CoordGrid(3201, 3856),
                    LocAngle.South,
                    1571,
                    CoordGrid(3202, 3855),
                    1572,
                    CoordGrid(3201, 3855),
                    LocAngle.East,
                ),
                gate(
                    "north-east surface gate",
                    1727,
                    CoordGrid(3225, 3904),
                    1728,
                    CoordGrid(3224, 3904),
                    LocAngle.South,
                    1571,
                    CoordGrid(3225, 3903),
                    1572,
                    CoordGrid(3224, 3903),
                    LocAngle.East,
                ),
                gate(
                    "eastern surface gate",
                    1727,
                    CoordGrid(3337, 3896),
                    1728,
                    CoordGrid(3336, 3896),
                    LocAngle.South,
                    1571,
                    CoordGrid(3337, 3895),
                    1572,
                    CoordGrid(3336, 3895),
                    LocAngle.East,
                ),
                gate(
                    "south-west Wilderness dungeon gate",
                    1568,
                    CoordGrid(3022, 10312),
                    1569,
                    CoordGrid(3022, 10311),
                    LocAngle.East,
                    1571,
                    CoordGrid(3023, 10312),
                    1572,
                    CoordGrid(3023, 10311),
                    LocAngle.North,
                ),
                gate(
                    "western Wilderness dungeon gate",
                    1568,
                    CoordGrid(3040, 10308),
                    1569,
                    CoordGrid(3040, 10307),
                    LocAngle.East,
                    1571,
                    CoordGrid(3041, 10308),
                    1572,
                    CoordGrid(3041, 10307),
                    LocAngle.North,
                ),
                gate(
                    "northern Wilderness dungeon gate",
                    1568,
                    CoordGrid(3044, 10342),
                    1569,
                    CoordGrid(3044, 10341),
                    LocAngle.East,
                    1571,
                    CoordGrid(3045, 10342),
                    1572,
                    CoordGrid(3045, 10341),
                    LocAngle.North,
                ),
                gate(
                    "southern Wilderness dungeon gate",
                    1568,
                    CoordGrid(3105, 9944),
                    1569,
                    CoordGrid(3106, 9944),
                    LocAngle.North,
                    1571,
                    CoordGrid(3105, 9945),
                    1572,
                    CoordGrid(3106, 9945),
                    LocAngle.West,
                ),
                gate(
                    "Edgeville dungeon monkey-bars gate",
                    1568,
                    CoordGrid(3103, 9910),
                    1569,
                    CoordGrid(3103, 9909),
                    LocAngle.East,
                    1571,
                    CoordGrid(3104, 9910),
                    1572,
                    CoordGrid(3104, 9909),
                    LocAngle.North,
                ),
                gate(
                    "Edgeville dungeon members gate",
                    1727,
                    CoordGrid(3131, 9917),
                    1728,
                    CoordGrid(3132, 9917),
                    LocAngle.North,
                    1571,
                    CoordGrid(3131, 9918),
                    1572,
                    CoordGrid(3132, 9918),
                    LocAngle.West,
                ),
                gate(
                    "Edgeville dungeon eastern gate",
                    1568,
                    CoordGrid(3145, 9871),
                    1569,
                    CoordGrid(3145, 9870),
                    LocAngle.East,
                    1571,
                    CoordGrid(3146, 9871),
                    1572,
                    CoordGrid(3146, 9870),
                    LocAngle.North,
                ),
            )

        assertEquals(expected, WILDERNESS_METAL_GATE_PAIRS.map(::toExpectation))
    }

    @Test
    fun `open metal gates clear the closed doorway and round trip exactly`() {
        for (pair in WILDERNESS_METAL_GATE_PAIRS) {
            val closedTiles = pair.halves.map { it.closed.coords }.toSet()
            val openTiles = pair.halves.map { it.opened.coords }.toSet()
            assertTrue(closedTiles.intersect(openTiles).isEmpty(), pair.name)
            assertEquals(
                1,
                pair.left.closed.coords.chebyshevDistance(pair.right.closed.coords),
                pair.name,
            )
            assertEquals(
                1,
                pair.left.opened.coords.chebyshevDistance(pair.right.opened.coords),
                pair.name,
            )
            assertEquals(WildernessMetalGateHalf.Left, pair.left.half)
            assertEquals(WildernessMetalGateHalf.Right, pair.right.half)
            assertEquals(
                pair.left.closed.angle.turn(rotations = 3),
                pair.left.opened.angle,
                pair.name,
            )
            assertEquals(
                pair.right.closed.angle.turn(rotations = 1),
                pair.right.opened.angle,
                pair.name,
            )
            assertNotEquals(pair.left.opened.angle, pair.right.opened.angle, pair.name)
            for (state in pair.halves) {
                assertEquals(LocShape.WallStraight, state.closed.shape)
                assertEquals(state.closed.shape, state.opened.shape)
                assertEquals(state.closed.coords.level, state.opened.coords.level)
                when (state.half) {
                    WildernessMetalGateHalf.Left -> assertEquals(1571, state.opened.objectId)
                    WildernessMetalGateHalf.Right -> assertEquals(1572, state.opened.objectId)
                }
            }
        }
    }

    @Test
    fun `pirate and magic axe hut doors use their correct lock rules`() {
        assertEquals(5, LOCKED_DOORS.size)
        assertEquals(LockedDoorRule(39, 35.0), lockedDoorRule(11727))
        assertEquals(LockedDoorRule(23, 22.5), lockedDoorRule(11726))
        assertEquals(4, LOCKPICK_ANIMATION_TICKS)
        assertEquals(10, LOCKED_DOOR_AUTOCLOSE_TICKS)
    }

    @Test
    fun `every hut door has a fixed verified open identity`() {
        val expected =
            listOf(
                WildernessLockedDoorPlacement(
                    11727,
                    CoordGrid(3038, 3956),
                    LocShape.WallStraight,
                    LocAngle.West,
                ) to
                    WildernessLockedDoorPlacement(
                        14750,
                        CoordGrid(3037, 3956),
                        LocShape.WallStraight,
                        LocAngle.North,
                    ),
                WildernessLockedDoorPlacement(
                    11727,
                    CoordGrid(3041, 3959),
                    LocShape.WallStraight,
                    LocAngle.North,
                ) to
                    WildernessLockedDoorPlacement(
                        14750,
                        CoordGrid(3041, 3960),
                        LocShape.WallStraight,
                        LocAngle.East,
                    ),
                WildernessLockedDoorPlacement(
                    11727,
                    CoordGrid(3044, 3956),
                    LocShape.WallStraight,
                    LocAngle.East,
                ) to
                    WildernessLockedDoorPlacement(
                        14750,
                        CoordGrid(3045, 3956),
                        LocShape.WallStraight,
                        LocAngle.South,
                    ),
                WildernessLockedDoorPlacement(
                    11726,
                    CoordGrid(3190, 3957),
                    LocShape.WallStraight,
                    LocAngle.North,
                ) to
                    WildernessLockedDoorPlacement(
                        1547,
                        CoordGrid(3190, 3958),
                        LocShape.WallStraight,
                        LocAngle.East,
                    ),
                WildernessLockedDoorPlacement(
                    11726,
                    CoordGrid(3191, 3963),
                    LocShape.WallStraight,
                    LocAngle.South,
                ) to
                    WildernessLockedDoorPlacement(
                        1547,
                        CoordGrid(3191, 3962),
                        LocShape.WallStraight,
                        LocAngle.West,
                    ),
            )
        assertEquals(expected, LOCKED_DOORS.map { it.closed to it.opened })
        LOCKED_DOORS.forEach {
            assertEquals(LocShape.WallStraight, it.closed.shape)
            assertEquals(it.closed.shape, it.opened.shape)
            assertEquals(it.closed.coords.level, it.opened.coords.level)
            assertEquals(it.closed.angle.turn(rotations = 1), it.opened.angle)
            assertEquals(
                DoorTranslations.translateOpen(it.closed.coords, it.closed.shape, it.closed.angle),
                it.opened.coords,
            )
        }
    }

    @Test
    fun `hut door walking destination crosses the wall without changing the hinge`() {
        for (definition in LOCKED_DOORS) {
            val closed = definition.closed
            when (closed.angle) {
                LocAngle.West,
                LocAngle.East -> {
                    assertEquals(
                        closed.coords.translateX(1),
                        lockedDoorDestination(closed, closed.coords.translateX(-1)),
                    )
                    assertEquals(
                        closed.coords.translateX(-1),
                        lockedDoorDestination(closed, closed.coords.translateX(1)),
                    )
                }
                LocAngle.North,
                LocAngle.South -> {
                    assertEquals(
                        closed.coords.translateZ(1),
                        lockedDoorDestination(closed, closed.coords.translateZ(-1)),
                    )
                    assertEquals(
                        closed.coords.translateZ(-1),
                        lockedDoorDestination(closed, closed.coords.translateZ(1)),
                    )
                }
            }
        }
    }

    private fun gate(
        name: String,
        leftClosedId: Int,
        leftClosedCoords: CoordGrid,
        rightClosedId: Int,
        rightClosedCoords: CoordGrid,
        closedAngle: LocAngle,
        leftOpenId: Int,
        leftOpenCoords: CoordGrid,
        rightOpenId: Int,
        rightOpenCoords: CoordGrid,
        leftOpenAngle: LocAngle,
    ): GatePairExpectation =
        GatePairExpectation(
            name,
            GatePlacementExpectation(leftClosedId, leftClosedCoords, closedAngle),
            GatePlacementExpectation(rightClosedId, rightClosedCoords, closedAngle),
            GatePlacementExpectation(leftOpenId, leftOpenCoords, leftOpenAngle),
            GatePlacementExpectation(rightOpenId, rightOpenCoords, closedAngle.turn(rotations = 1)),
        )

    private fun toExpectation(pair: WildernessMetalGatePair): GatePairExpectation =
        GatePairExpectation(
            pair.name,
            pair.left.closed.toExpectation(),
            pair.right.closed.toExpectation(),
            pair.left.opened.toExpectation(),
            pair.right.opened.toExpectation(),
        )

    private fun WildernessMetalGatePlacement.toExpectation(): GatePlacementExpectation =
        GatePlacementExpectation(objectId, coords, angle)

    private data class GatePairExpectation(
        val name: String,
        val leftClosed: GatePlacementExpectation,
        val rightClosed: GatePlacementExpectation,
        val leftOpen: GatePlacementExpectation,
        val rightOpen: GatePlacementExpectation,
    )

    private data class GatePlacementExpectation(
        val objectId: Int,
        val coords: CoordGrid,
        val angle: LocAngle,
    )
}
