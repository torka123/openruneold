package org.rsmod.content.bosses.dt2

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class LeviathanRulesTest {
    @Test
    fun `ordinary player damage is not capped and only outside aura enrage is reduced`() {
        assertEquals(
            73,
            Dt2Rules.leviathanPlayerDamage(73, enraged = false, insidePathfinder = false),
        )
        assertEquals(
            73,
            Dt2Rules.leviathanPlayerDamage(73, enraged = true, insidePathfinder = true),
        )
        assertEquals(
            36,
            Dt2Rules.leviathanPlayerDamage(73, enraged = true, insidePathfinder = false),
        )
    }

    @Test
    fun `front stun cap and exposed wound style modifiers are isolated`() {
        assertEquals(
            10,
            Dt2Rules.leviathanWoundDamage(80, 100, Dt2AttackStyle.MELEE, behind = false),
        )
        assertEquals(
            65,
            Dt2Rules.leviathanWoundDamage(3, 100, Dt2AttackStyle.RANGED, behind = true),
        )
        assertEquals(
            82,
            Dt2Rules.leviathanWoundDamage(41, 100, Dt2AttackStyle.MAGIC, behind = true),
        )
        assertEquals(
            80,
            Dt2Rules.leviathanWoundDamage(80, 100, Dt2AttackStyle.MELEE, behind = true),
        )
    }

    @Test
    fun `rear wound geometry follows all eight boss orientations`() {
        val southWest = CoordGrid(100, 100, 0)
        val center = CoordGrid(103, 103, 0)
        LeviathanFacing.entries.forEach { facing ->
            val behind = center.translate(-facing.x * 6, -facing.z * 6)
            val front = center.translate(facing.x * 6, facing.z * 6)
            assertTrue(
                Dt2Rules.leviathanBehindWound(behind, southWest, 7, facing),
                "Expected $behind behind a Leviathan facing $facing",
            )
            assertFalse(
                Dt2Rules.leviathanBehindWound(front, southWest, 7, facing),
                "Expected $front in front of a Leviathan facing $facing",
            )
        }
    }

    @Test
    fun `bite triggers only on tiles directly adjacent to the footprint`() {
        val boss = CoordGrid(100, 100, 0)
        assertTrue(Dt2Rules.leviathanAdjacentToFootprint(CoordGrid(99, 103, 0), boss, 7))
        assertTrue(Dt2Rules.leviathanAdjacentToFootprint(CoordGrid(107, 107, 0), boss, 7))
        assertFalse(Dt2Rules.leviathanAdjacentToFootprint(CoordGrid(98, 103, 0), boss, 7))
        assertFalse(Dt2Rules.leviathanAdjacentToFootprint(CoordGrid(103, 103, 0), boss, 7))
        assertFalse(Dt2Rules.leviathanAdjacentToFootprint(CoordGrid(99, 103, 1), boss, 7))
    }

    @Test
    fun `only a successful shadow hit during standard pre-enrage action can stun`() {
        assertTrue(
            Dt2Rules.leviathanCanStun(
                LeviathanAction.STANDARD_VOLLEY,
                enraged = false,
                successfulShadowHit = true,
            )
        )
        assertFalse(
            Dt2Rules.leviathanCanStun(
                LeviathanAction.STANDARD_VOLLEY,
                enraged = true,
                successfulShadowHit = true,
            )
        )
        assertFalse(
            Dt2Rules.leviathanCanStun(
                LeviathanAction.SMOKE_BLAST,
                enraged = false,
                successfulShadowHit = true,
            )
        )
        assertFalse(
            Dt2Rules.leviathanCanStun(
                LeviathanAction.STANDARD_VOLLEY,
                enraged = false,
                successfulShadowHit = false,
            )
        )
    }

    @Test
    fun `seeded orb selection is replayable randomized and tier constrained`() {
        val first = Dt2SeededRng(0x5eed)
        val second = Dt2SeededRng(0x5eed)
        val a =
            List(48) { Dt2Rules.leviathanStyle(4, enraged = false, first.nextInt(Int.MAX_VALUE)) }
        val b =
            List(48) { Dt2Rules.leviathanStyle(4, enraged = false, second.nextInt(Int.MAX_VALUE)) }
        assertEquals(a, b)
        assertEquals(Dt2AttackStyle.entries.toSet(), a.toSet())
        val enraged =
            List(48) { Dt2Rules.leviathanStyle(6, enraged = true, first.nextInt(Int.MAX_VALUE)) }
        assertFalse(Dt2AttackStyle.MELEE in enraged)
    }

    @Test
    fun `orb protection semantics distinguish pre-enrage aura and outside aura`() {
        assertEquals(
            0,
            Dt2Rules.leviathanOrbDamage(
                40,
                protected = true,
                enraged = false,
                insidePathfinder = false,
            ),
        )
        assertEquals(
            0,
            Dt2Rules.leviathanOrbDamage(
                40,
                protected = true,
                enraged = true,
                insidePathfinder = true,
            ),
        )
        assertEquals(
            10,
            Dt2Rules.leviathanOrbDamage(
                40,
                protected = true,
                enraged = true,
                insidePathfinder = false,
            ),
        )
        assertEquals(
            40,
            Dt2Rules.leviathanOrbDamage(
                40,
                protected = false,
                enraged = true,
                insidePathfinder = false,
            ),
        )
    }

    @Test
    fun `slow and fast tiers select their captured animations`() {
        assertEquals(10278, Dt2Rules.leviathanProjectileAnimation(0, enraged = false))
        assertEquals(10278, Dt2Rules.leviathanProjectileAnimation(2, enraged = false))
        assertEquals(10279, Dt2Rules.leviathanProjectileAnimation(3, enraged = false))
        assertEquals(10279, Dt2Rules.leviathanProjectileAnimation(0, enraged = true))
    }

    @Test
    fun `orb flight and body impact match captured packet values`() {
        fun endTime(tier: Int, enraged: Boolean, awakened: Boolean = false): Int =
            30 +
                Dt2Rules.leviathanProjectileLengthAdjustment(tier, enraged, awakened) +
                (5 * 4)

        assertEquals(150, endTime(0, enraged = false))
        assertEquals(150, endTime(1, enraged = false))
        assertEquals(120, endTime(2, enraged = false))
        assertEquals(90, endTime(3, enraged = false))
        assertEquals(90, endTime(4, enraged = false))
        assertEquals(60, endTime(5, enraged = false))
        assertEquals(150, endTime(0, enraged = true))
        assertEquals(90, endTime(0, enraged = true, awakened = true))
        assertEquals(550, Dt2Rules.LEVIATHAN_ORB_START_HEIGHT)
        assertEquals(100, Dt2Rules.LEVIATHAN_ORB_END_HEIGHT)
        assertEquals(100, Dt2Rules.LEVIATHAN_ORB_IMPACT_HEIGHT)
        assertEquals(2, Dt2Rules.LEVIATHAN_ORB_IMPACT_SLOT)
    }

    @Test
    fun `orb launch tile follows captured leviathan mouth offsets`() {
        val boss = CoordGrid(11518, 10689, 0)

        assertEquals(
            CoordGrid(11519, 10694, 0),
            Dt2Rules.leviathanProjectileSource(CoordGrid(11515, 10697, 0), boss, 7),
        )
        assertEquals(
            CoordGrid(11519, 10694, 0),
            Dt2Rules.leviathanProjectileSource(CoordGrid(11517, 10698, 0), boss, 7),
        )
        assertEquals(
            CoordGrid(11521, 10694, 0),
            Dt2Rules.leviathanProjectileSource(CoordGrid(11518, 10698, 0), boss, 7),
        )
        assertEquals(
            CoordGrid(11521, 10694, 0),
            Dt2Rules.leviathanProjectileSource(CoordGrid(11519, 10698, 0), boss, 7),
        )
    }

    @Test
    fun `special reduction is separate from ordinary damage`() {
        assertEquals(67, Dt2Rules.leviathanSpecialDamage(100))
        assertEquals(0, Dt2Rules.leviathanSpecialDamage(-1))
    }

    @Test
    fun `blocker orientation selects first special then specials alternate`() {
        assertEquals(
            LeviathanSpecial.LIGHTNING_BARRAGE,
            Dt2Rules.leviathanFirstSpecial(LeviathanDebrisAxis.EAST_WEST),
        )
        assertEquals(
            LeviathanSpecial.SMOKE_BLAST,
            Dt2Rules.leviathanFirstSpecial(LeviathanDebrisAxis.NORTH_SOUTH),
        )
        assertEquals(
            LeviathanSpecial.SMOKE_BLAST,
            Dt2Rules.leviathanNextSpecial(LeviathanSpecial.LIGHTNING_BARRAGE),
        )
        assertEquals(
            LeviathanSpecial.LIGHTNING_BARRAGE,
            Dt2Rules.leviathanNextSpecial(LeviathanSpecial.SMOKE_BLAST),
        )
    }

    @Test
    fun `stationary leviathan can engage the captured starting tile`() {
        val capturedStartDistance = 9
        val cacheMaximumInteractionDistance = 7 + 1

        assertTrue(capturedStartDistance > cacheMaximumInteractionDistance)
        assertEquals(12, Dt2Rules.LEVIATHAN_AP_RANGE)
        assertTrue(capturedStartDistance <= Dt2Rules.LEVIATHAN_MAX_OP_RANGE)
        assertTrue(
            Dt2Rules.LEVIATHAN_MAX_OP_RANGE >=
                Dt2Rules.LEVIATHAN_AP_RANGE + Dt2Rules.LEVIATHAN_SIZE - 1
        )
    }

    @Test
    fun `successful stun reset launches the queued retaliation`() {
        assertEquals(LeviathanSpecial.VOLLEY_ROAR, Dt2Rules.leviathanRetaliationAction(null))
        assertEquals(
            LeviathanSpecial.LIGHTNING_BARRAGE,
            Dt2Rules.leviathanRetaliationAction(LeviathanSpecial.LIGHTNING_BARRAGE),
        )
        assertEquals(
            LeviathanSpecial.SMOKE_BLAST,
            Dt2Rules.leviathanRetaliationAction(LeviathanSpecial.SMOKE_BLAST),
        )
    }

    @Test
    fun `pathfinder route starts at north west and moves clockwise one tile per step`() {
        val northWest = CoordGrid(10, 20, 0)
        val center = CoordGrid(12, 18, 0)
        val corners = Dt2Rules.leviathanPathfinderCorners(northWest, center)
        assertEquals(
            listOf(
                CoordGrid(10, 20, 0),
                CoordGrid(14, 20, 0),
                CoordGrid(14, 16, 0),
                CoordGrid(10, 16, 0),
            ),
            corners,
        )
        val route = Dt2Rules.leviathanClockwisePath(corners)
        assertEquals(northWest, route.first())
        route.zipWithNext().forEach { (from, to) ->
            assertEquals(1, max(abs(to.x - from.x), abs(to.z - from.z)))
        }
        assertEquals(
            1,
            max(abs(route.first().x - route.last().x), abs(route.first().z - route.last().z)),
        )
    }

    @Test
    fun `pathfinder aura is five by five normal and seven by seven awakened`() {
        val center = CoordGrid(50, 50, 0)
        assertTrue(
            Dt2Rules.leviathanInsidePathfinder(CoordGrid(52, 52, 0), center, awakened = false)
        )
        assertFalse(
            Dt2Rules.leviathanInsidePathfinder(CoordGrid(53, 50, 0), center, awakened = false)
        )
        assertTrue(
            Dt2Rules.leviathanInsidePathfinder(CoordGrid(53, 53, 0), center, awakened = true)
        )
        assertFalse(
            Dt2Rules.leviathanInsidePathfinder(CoordGrid(54, 50, 0), center, awakened = true)
        )
    }

    @Test
    fun `smoke cover must be landed between boss and player rather than merely nearby`() {
        val boss = CoordGrid(10, 10, 0)
        val player = CoordGrid(16, 10, 0)
        assertTrue(Dt2Rules.leviathanDebrisBlocksLine(boss, player, CoordGrid(13, 10, 0)))
        assertFalse(Dt2Rules.leviathanDebrisBlocksLine(boss, player, CoordGrid(13, 11, 0)))
        assertFalse(Dt2Rules.leviathanDebrisBlocksLine(boss, player, player))
    }

    @Test
    fun `awakened tornado begins at fifty percent and normal never enables it`() {
        assertFalse(Dt2Rules.leviathanTornadoActive(1_351, 2_700, awakened = true))
        assertTrue(Dt2Rules.leviathanTornadoActive(1_350, 2_700, awakened = true))
        assertFalse(Dt2Rules.leviathanTornadoActive(100, 900, awakened = false))
    }

    @Test
    fun `stun resets on wound or cumulative quarter hitpoints and advances tiers`() {
        assertFalse(Dt2Rules.leviathanStunResetsVolley(224, 900, woundHit = false))
        assertTrue(Dt2Rules.leviathanStunResetsVolley(225, 900, woundHit = false))
        assertTrue(Dt2Rules.leviathanStunResetsVolley(1, 900, woundHit = true))
        assertEquals(6, Dt2Rules.leviathanResetAttackCounter(1))
        assertEquals(14, Dt2Rules.leviathanResetAttackCounter(2))
        assertEquals(54, Dt2Rules.leviathanResetAttackCounter(99))
        assertEquals(4, Dt2Rules.leviathanPathfinderSpawnDelay(awakened = false))
        assertEquals(23, Dt2Rules.leviathanPathfinderSpawnDelay(awakened = true))
        assertEquals(3, Dt2Rules.leviathanRoarBlockerCount(awakened = false))
        assertEquals(5, Dt2Rules.leviathanRoarBlockerCount(awakened = true))
        assertEquals(7, Dt2Rules.LEVIATHAN_ROAR_BLOCKER_LAND_TICKS)
        assertEquals(50, Dt2Rules.LEVIATHAN_ROAR_BLOCKER_CLIENT_DELAY)
    }

    @Test
    fun `pathfinder applies inside accuracy damage and transition protection policies`() {
        assertEquals(
            20,
            Dt2Rules.leviathanPathfinderOutgoingDamage(
                0,
                100,
                Dt2AttackStyle.RANGED,
                enraged = true,
                insidePathfinder = true,
            ),
        )
        assertEquals(
            42,
            Dt2Rules.leviathanPathfinderOutgoingDamage(
                21,
                100,
                Dt2AttackStyle.MAGIC,
                enraged = true,
                insidePathfinder = true,
            ),
        )
        assertEquals(
            10,
            Dt2Rules.leviathanPathfinderOutgoingDamage(
                21,
                100,
                Dt2AttackStyle.MELEE,
                enraged = true,
                insidePathfinder = false,
            ),
        )
        assertTrue(
            Dt2Rules.leviathanInsidePathfinderOrTransition(
                enraged = true,
                pathfinderPresent = false,
                geometricInside = false,
            )
        )
        assertFalse(
            Dt2Rules.leviathanInsidePathfinderOrTransition(
                enraged = true,
                pathfinderPresent = true,
                geometricInside = false,
            )
        )
    }

    @Test
    fun `roar debris preserves captured count and client delay bounds`() {
        assertEquals(12, Dt2Rules.leviathanRoarTransientDebrisCount(0))
        assertEquals(36, Dt2Rules.leviathanRoarTransientDebrisCount(24))
        assertEquals(1, Dt2Rules.leviathanDebrisImpactTicks(20))
        assertEquals(2, Dt2Rules.leviathanDebrisImpactTicks(50))
        assertEquals(3, Dt2Rules.leviathanDebrisImpactTicks(80))
    }

    @Test
    fun `lightning rejects pool-adjacent and out-of-arena tiles`() {
        val boss = CoordGrid(100, 100, 0)
        val northWest = CoordGrid(90, 120, 0)
        val southEast = CoordGrid(120, 90, 0)
        assertFalse(
            Dt2Rules.leviathanLightningTileAllowed(
                CoordGrid(99, 103, 0),
                boss,
                7,
                northWest,
                southEast,
            )
        )
        assertFalse(
            Dt2Rules.leviathanLightningTileAllowed(
                CoordGrid(121, 103, 0),
                boss,
                7,
                northWest,
                southEast,
            )
        )
        assertTrue(
            Dt2Rules.leviathanLightningTileAllowed(
                CoordGrid(92, 103, 0),
                boss,
                7,
                northWest,
                southEast,
            )
        )
        assertEquals(1, Dt2Rules.leviathanLightningImpactTicks(25))
        assertEquals(2, Dt2Rules.leviathanLightningImpactTicks(26))
    }

    @Test
    fun `lightning current fills a two tile running step without redrawing its old tile`() {
        val previous = CoordGrid(100, 100, 0)
        assertEquals(
            listOf(CoordGrid(101, 100, 0), CoordGrid(102, 100, 0)),
            Dt2Rules.leviathanLightningTrail(previous, CoordGrid(102, 100, 0)),
        )
        assertEquals(
            listOf(CoordGrid(101, 101, 0), CoordGrid(102, 102, 0)),
            Dt2Rules.leviathanLightningTrail(previous, CoordGrid(102, 102, 0)),
        )
    }

    @Test
    fun `lightning current does not bridge teleports or planes`() {
        val current = CoordGrid(110, 110, 0)
        assertEquals(
            listOf(current),
            Dt2Rules.leviathanLightningTrail(CoordGrid(100, 100, 0), current),
        )
        assertEquals(
            listOf(current),
            Dt2Rules.leviathanLightningTrail(CoordGrid(110, 110, 1), current),
        )
    }

    @Test
    fun `tornado pauses for exactly seven cycles after contact`() {
        assertTrue(Dt2Rules.leviathanTornadoCanMove(100, Int.MIN_VALUE))
        assertFalse(Dt2Rules.leviathanTornadoCanMove(106, 100))
        assertTrue(Dt2Rules.leviathanTornadoCanMove(107, 100))
    }

    @Test
    fun `smoke maximums retain documented normal and conservative awakened floor`() {
        assertEquals(59, Dt2Rules.leviathanSmokeMaximumHit(awakened = false))
        assertEquals(70, Dt2Rules.leviathanSmokeMaximumHit(awakened = true))
    }

    @Test
    fun `enrage uses captured pathfinder delay and bounded recurrent debris cadence`() {
        assertEquals(4, Dt2Rules.leviathanPathfinderSpawnDelay(awakened = false))
        assertEquals(23, Dt2Rules.leviathanPathfinderSpawnDelay(awakened = true))
        assertEquals(4, Dt2Rules.leviathanEnrageDebrisInterval(0))
        assertEquals(12, Dt2Rules.leviathanEnrageDebrisInterval(8))
        assertEquals(16, Dt2Rules.LEVIATHAN_ENRAGE_PERSISTENT_DEBRIS_INTERVAL)
        assertEquals(7, Dt2Rules.LEVIATHAN_ENRAGE_PERSISTENT_DEBRIS_LAND_TICKS)
    }

    @Test
    fun `awakened combat levels match the current boss definition`() {
        assertEquals(525, Dt2Rules.LEVIATHAN_AWAKENED_STATS.attack)
        assertEquals(287, Dt2Rules.LEVIATHAN_AWAKENED_STATS.strength)
        assertEquals(280, Dt2Rules.LEVIATHAN_AWAKENED_STATS.defence)
        assertEquals(280, Dt2Rules.LEVIATHAN_AWAKENED_STATS.magic)
        assertEquals(260, Dt2Rules.LEVIATHAN_AWAKENED_STATS.ranged)
    }
}
