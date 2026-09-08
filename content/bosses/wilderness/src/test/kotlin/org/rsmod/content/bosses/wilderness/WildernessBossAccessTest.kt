package org.rsmod.content.bosses.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

class WildernessBossAccessTest {
    @Test
    fun `all six exact surface objects lead only to their paired cave`() {
        val expected =
            mapOf(
                CALVARION_ENTRANCE to (CoordGrid(3180, 3683) to CoordGrid(1886, 11539, 1)),
                SPINDEL_ENTRANCE to (CoordGrid(3183, 3744) to CoordGrid(1630, 11532, 2)),
                ARTIO_ENTRANCE to (CoordGrid(3115, 3676) to CoordGrid(1758, 11536)),
                VETION_ENTRANCE to (CoordGrid(3221, 3787) to CoordGrid(3294, 10195, 1)),
                VENENATIS_ENTRANCE to (CoordGrid(3321, 3794) to CoordGrid(3422, 10184, 2)),
                CALLISTO_ENTRANCE to (CoordGrid(3291, 3849) to CoordGrid(3358, 10316)),
            )
        assertEquals(expected.keys, WILDERNESS_BOSS_ENTRANCE_LINKS.keys)
        expected.forEach { (locId, route) ->
            val (source, destination) = route
            val link = WILDERNESS_BOSS_ENTRANCE_LINKS.getValue(locId).getValue(source)
            assertEquals(destination, link.destination)
            assertEquals(Direction.North, link.facing)
        }
    }

    @Test
    fun `paired boss entrance object ids are distinct and correctly assigned`() {
        assertEquals(46996, CALVARION_ENTRANCE)
        assertEquals(46995, VETION_ENTRANCE)
        assertEquals(47078, SPINDEL_ENTRANCE)
        assertEquals(47077, VENENATIS_ENTRANCE)
        assertEquals(47141, ARTIO_ENTRANCE)
        assertEquals(47140, CALLISTO_ENTRANCE)
        assertEquals(6, WILDERNESS_BOSS_ENTRANCE_LINKS.keys.distinct().size)
    }

    @Test
    fun `singles exits return beside their own exterior entrance`() {
        assertEquals(
            CoordGrid(3180, 3682),
            WILDERNESS_BOSS_EXIT_LINKS.getValue(CALVARION_EXIT).values.single().destination,
        )
        assertEquals(
            CoordGrid(3183, 3743),
            WILDERNESS_BOSS_EXIT_LINKS.getValue(SPINDEL_EXIT).values.single().destination,
        )
        assertEquals(
            CoordGrid(3115, 3675),
            WILDERNESS_BOSS_EXIT_LINKS.getValue(ARTIO_EXIT).values.single().destination,
        )
        assertTrue(
            WILDERNESS_BOSS_EXIT_LINKS.values
                .flatMap { it.values }
                .all { it.facing == Direction.South }
        )
    }

    @Test
    fun `strong boss exits share the verified escape cave selector`() {
        assertEquals(
            setOf(CoordGrid(3294, 10190, 1)),
            WILDERNESS_BOSS_ESCAPE_EXIT_SOURCES.getValue(CALVARION_EXIT),
        )
        assertEquals(
            setOf(CoordGrid(3422, 10183, 2)),
            WILDERNESS_BOSS_ESCAPE_EXIT_SOURCES.getValue(SPINDEL_EXIT),
        )
        assertEquals(
            setOf(CoordGrid(3358, 10315)),
            WILDERNESS_BOSS_ESCAPE_EXIT_SOURCES.getValue(ARTIO_EXIT),
        )
        assertEquals(
            listOf(CoordGrid(3342, 10252), CoordGrid(3361, 10295), CoordGrid(3375, 10254)),
            WILDERNESS_BOSS_ESCAPE_ARRIVALS,
        )
    }

    @Test
    fun `players following a boss exit within ten seconds receive the same escape route`() {
        var nextIndex = 0
        val selector = WildernessEscapeRouteSelector { size -> (nextIndex++ % size) }
        val source = CoordGrid(3358, 10315)
        val first = selector.select(source, currentTick = 100)
        assertEquals(first, selector.select(source, currentTick = 100 + ESCAPE_ROUTE_FOLLOW_TICKS))
        assertEquals(WILDERNESS_BOSS_ESCAPE_ARRIVALS[1], selector.select(source, currentTick = 117))
    }

    @Test
    fun `surface escape cave openings are destinations only`() {
        assertEquals(
            setOf(
                CoordGrid(3260, 3832),
                CoordGrid(3282, 3774),
                CoordGrid(3284, 3807),
                CoordGrid(3320, 3830),
            ),
            WILDERNESS_ESCAPE_CAVE_SURFACE_EXITS,
        )
        val surfaceArrivals = WILDERNESS_ESCAPE_CAVE_EXIT_LINKS.values.flatMap { it.values }
        assertEquals(4, surfaceArrivals.size)
        assertTrue(
            surfaceArrivals.all { link ->
                link.facing == Direction.South &&
                    WILDERNESS_ESCAPE_CAVE_SURFACE_EXITS.any {
                        it.chebyshevDistance(link.destination) == 1
                    }
            }
        )
    }

    @Test
    fun `six permanent boss spawns remain in six different correct rooms`() {
        val expectedNames =
            setOf(
                "npc.callisto_singles",
                "npc.venenatis_singles",
                "npc.vetion_single",
                "npc.callisto",
                "npc.venenatis",
                "npc.vetion",
            )
        assertEquals(expectedNames, WILDERNESS_BOSS_SPAWNS.map { it.internalName }.toSet())
        assertEquals(6, WILDERNESS_BOSS_SPAWNS.map { it.coords }.distinct().size)
        WILDERNESS_BOSS_SPAWNS.forEach { spawn ->
            assertTrue(
                spawn.room.contains(spawn.coords),
                "Spawn outside ${spawn.room.name}: $spawn",
            )
            assertTrue(
                spawn.coords.z > 10_000,
                "Boss accidentally spawned on the Wilderness surface: $spawn",
            )
        }
        assertNotEquals(ARTIO_ROOM, CALLISTO_ROOM)
        assertNotEquals(SPINDEL_ROOM, VENENATIS_ROOM)
        assertNotEquals(CALVARION_ROOM, VETION_ROOM)
    }

    @Test
    fun `spider bosses use full-footprint-safe arena anchors`() {
        val byName = WILDERNESS_BOSS_SPAWNS.associateBy { it.internalName }
        assertEquals(CoordGrid(1630, 11544, 2), byName.getValue("npc.venenatis_singles").coords)
        assertEquals(CoordGrid(3422, 10199, 2), byName.getValue("npc.venenatis").coords)
    }

    @Test
    fun `peek room map squares are isolated per cave`() {
        val rooms = WILDERNESS_BOSS_ROOMS_BY_ENTRANCE.values
        assertEquals(
            6,
            rooms.map { Triple(it.mapSquareX, it.mapSquareZ, it.level) }.distinct().size,
        )
        WILDERNESS_BOSS_SPAWNS.forEach { spawn ->
            assertEquals(1, rooms.count { it.contains(spawn.coords) })
        }
    }

    @Test
    fun `entry fee remains shared and cave traversal preserves wilderness teleport state`() {
        assertEquals("obj.coins", WILDERNESS_BOSS_ENTRANCE_FEE.currency)
        assertEquals(100_000, WILDERNESS_BOSS_ENTRANCE_FEE.amount)
        assertEquals("100,000 coins", WILDERNESS_BOSS_ENTRANCE_FEE.formatted())
        assertEquals("varp.wilderness_boss_fee_paid", WILDERNESS_BOSS_FEE_PAID_VARP)
        assertEquals("varp.wilderness_boss_fee_at_risk", WILDERNESS_BOSS_FEE_AT_RISK_VARP)
        assertEquals(TeleportType.Exempt, WILDERNESS_BOSS_CAVE_TRAVERSAL)
    }
}
