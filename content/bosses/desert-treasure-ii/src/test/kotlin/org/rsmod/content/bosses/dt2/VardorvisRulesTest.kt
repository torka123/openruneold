package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VardorvisRulesTest {
    @Test
    fun `capture backed room entry spawn and plane are fixed`() {
        assertEquals(4405, Dt2Boss.VARDORVIS.regionId)
        assertEquals(0, Dt2Assets.VARDORVIS_SAFE_ENTRY.level)
        assertEquals(17, Dt2Assets.VARDORVIS_SAFE_ENTRY.regionZoneX)
        assertEquals(53, Dt2Assets.VARDORVIS_SAFE_ENTRY.regionZoneZ)
        assertEquals(31, Dt2Assets.VARDORVIS_SAFE_ENTRY.localX)
        assertEquals(36, Dt2Assets.VARDORVIS_SAFE_ENTRY.localZ)
        assertEquals(40, Dt2Boss.VARDORVIS.spawn.localX)
        assertEquals(25, Dt2Boss.VARDORVIS.spawn.localZ)
        assertNotEquals(Dt2Assets.VARDORVIS_SAFE_ENTRY, Dt2Boss.VARDORVIS.spawn)
        assertEquals(30, Dt2Assets.VARDORVIS_EXIT_TILE.localX)
        assertEquals(36, Dt2Assets.VARDORVIS_EXIT_TILE.localZ)
        assertEquals(47603, Dt2Assets.VARDORVIS_EXIT)
        assertEquals(47604, Dt2Assets.VARDORVIS_EXIT_DISABLED)
    }

    @Test
    fun `normal Vardorvis has exact hp respawn and attack cadence contracts`() {
        assertEquals(700, Dt2Boss.VARDORVIS.normalHitpoints)
        assertEquals(17, VARDORVIS_RESPAWN_TICKS)
        assertEquals(32, Dt2Rules.vardorvisNormalMaxHit(700))
        assertEquals(43, Dt2Rules.vardorvisNormalMaxHit(1))
        assertEquals(5, Dt2Rules.VARDORVIS_NORMAL_ATTACK_TICKS)
        assertEquals(3, Dt2Rules.VARDORVIS_AXE_ATTACK_INTERVAL)
    }

    @Test
    fun `strength rises and defence falls with missing health`() {
        assertEquals(270, Dt2Rules.vardorvisStrengthLevel(700))
        assertEquals(360, Dt2Rules.vardorvisStrengthLevel(1))
        assertEquals(215, Dt2Rules.vardorvisDefenceLevel(700))
        assertEquals(145, Dt2Rules.vardorvisDefenceLevel(1))
    }

    @Test
    fun `melee prayer mitigates seventy five percent and healing uses actual damage`() {
        assertEquals(32, Dt2Rules.vardorvisMeleeDamage(32, protected = false))
        assertEquals(8, Dt2Rules.vardorvisMeleeDamage(32, protected = true))
        assertEquals(0, Dt2Rules.vardorvisHeal(1))
        assertEquals(8, Dt2Rules.vardorvisHeal(17))
    }

    @Test
    fun `normal axe counts and captured paths are deterministic`() {
        assertEquals(1, Dt2Rules.vardorvisAxeCount(691, awakened = false))
        assertEquals(2, Dt2Rules.vardorvisAxeCount(690, awakened = false))
        assertEquals(3, Dt2Rules.vardorvisAxeCount(230, awakened = false))
        val lanes = Dt2Rules.vardorvisAxeLanes()
        assertEquals(8, lanes.size)
        assertEquals(VardorvisAxeLane(35, 25, 37, 25, 45, 25), lanes.first())
        assertTrue(
            lanes.all { it.movingStartX != it.movingEndX || it.movingStartZ != it.movingEndZ }
        )
        assertEquals(4, lanes.count { it.originX == 40 || it.originZ == 25 })
        assertEquals(4, lanes.count { it.originX != 40 && it.originZ != 25 })
        lanes.forEach { lane ->
            assertEquals(
                5,
                maxOf(kotlin.math.abs(lane.originX - 40), kotlin.math.abs(lane.originZ - 25)),
            )
            assertEquals(
                3,
                maxOf(
                    kotlin.math.abs(lane.movingStartX - 40),
                    kotlin.math.abs(lane.movingStartZ - 25),
                ),
            )
            assertEquals(80 - lane.originX, lane.movingEndX)
            assertEquals(50 - lane.originZ, lane.movingEndZ)
            listOf(
                    lane.originX to lane.originZ,
                    lane.movingStartX to lane.movingStartZ,
                    lane.movingEndX to lane.movingEndZ,
                )
                .forEach { (x, z) ->
                    assertTrue(x in 35..45)
                    assertTrue(z in 20..30)
                }
        }
    }

    @Test
    fun `axe movement advances one two two two one and sweeps a three wide lane`() {
        val westToEast = Dt2Rules.vardorvisAxeLanes().first()
        assertEquals(
            listOf(38 to 25, 40 to 25, 42 to 25, 44 to 25, 45 to 25),
            Dt2Rules.vardorvisAxeMovementTiles(westToEast),
        )
        assertTrue(Dt2Rules.vardorvisAxeSweptHit(39, 26, 38, 25, 40, 25))
        assertFalse(Dt2Rules.vardorvisAxeSweptHit(39, 27, 38, 25, 40, 25))

        val northWestToSouthEast = Dt2Rules.vardorvisAxeLanes()[1]
        val diagonalMovement = Dt2Rules.vardorvisAxeMovementTiles(northWestToSouthEast)
        assertEquals(38 to 27, diagonalMovement.first())
        assertEquals(45 to 20, diagonalMovement.last())
        assertTrue(Dt2Rules.vardorvisAxeSweptHit(39, 28, 38, 27, 40, 25))
        assertFalse(Dt2Rules.vardorvisAxeSweptHit(39, 29, 38, 27, 40, 25))
    }

    @Test
    fun `bleed lasts fourteen ticks and running accelerates pulses`() {
        assertTrue(Dt2Rules.vardorvisBleedDue(elapsed = 0, ticksSinceLast = 3, running = false))
        assertFalse(Dt2Rules.vardorvisBleedDue(elapsed = 0, ticksSinceLast = 2, running = false))
        assertTrue(Dt2Rules.vardorvisBleedDue(elapsed = 1, ticksSinceLast = 1, running = true))
        assertFalse(Dt2Rules.vardorvisBleedDue(elapsed = 14, ticksSinceLast = 99, running = true))
        assertEquals(3, Dt2Rules.VARDORVIS_BLEED_DAMAGE)
    }

    @Test
    fun `dart count and complementary half x geometry match documented thresholds`() {
        assertEquals(1, Dt2Rules.vardorvisDartCount(501))
        assertEquals(2, Dt2Rules.vardorvisDartCount(500))
        assertEquals(3, Dt2Rules.vardorvisDartCount(129))
        val first = Dt2Rules.vardorvisDartPattern(waveIndex = 0, horizontal = true)
        val second = Dt2Rules.vardorvisDartPattern(waveIndex = 1, horizontal = true)
        assertEquals(setOf(-2 to -1, -1 to -1, 0 to 0, 1 to 1, 2 to 1), first)
        assertEquals(setOf(0 to 0), first intersect second)
        assertEquals(9, (first + second).size)
        (first + second).forEach { (x, z) ->
            assertTrue(x in -2..2)
            assertTrue(z in -1..1)
        }

        val vertical = Dt2Rules.vardorvisDartPattern(waveIndex = 0, horizontal = false)
        assertEquals(first.mapTo(mutableSetOf()) { (x, z) -> z to x }, vertical)
        assertEquals(25, Dt2Rules.VARDORVIS_DART_MAX_HIT)
    }

    @Test
    fun `third dart uses supplied rolls for unique bounded tiles outside the full x`() {
        val rolls = listOf(0, 0, 47, -5)
        val first = Dt2Rules.vardorvisDartPattern(waveIndex = 0, horizontal = true)
        val second = Dt2Rules.vardorvisDartPattern(waveIndex = 1, horizontal = true)
        val third = Dt2Rules.vardorvisDartPattern(waveIndex = 2, horizontal = true, rolls = rolls)
        assertEquals(Dt2Rules.VARDORVIS_RANDOM_DART_COUNT, third.size)
        assertEquals(third, Dt2Rules.vardorvisDartPattern(2, true, rolls))
        assertTrue(third.none { it in first || it in second })
        assertTrue(third.all { (x, z) -> x in -2..2 && z in -2..2 })
    }

    @Test
    fun `arena edge reduces darts to one inward crack without a deadlock`() {
        assertTrue(Dt2Rules.vardorvisAtDartEdge(48, 25, 40, 25))
        assertTrue(Dt2Rules.vardorvisAtDartEdge(40, 17, 40, 25))
        assertFalse(Dt2Rules.vardorvisAtDartEdge(47, 25, 40, 25))
        assertEquals(-1 to 0, Dt2Rules.vardorvisEdgeDartOffset(48, 25, 40, 25))
        assertEquals(0 to 1, Dt2Rules.vardorvisEdgeDartOffset(40, 17, 40, 25))
    }

    @Test
    fun `head prayer result and body recovery contracts are explicit`() {
        assertFalse(Dt2Rules.vardorvisHeadUnlocked(570))
        assertTrue(Dt2Rules.vardorvisHeadUnlocked(569))
        assertEquals(10, Dt2Rules.VARDORVIS_HEAD_PRAYER_DRAIN)
        assertEquals(3, Dt2Rules.VARDORVIS_HEAD_PRAYER_DISABLE_TICKS)
        assertEquals(2, Dt2Rules.VARDORVIS_HEAD_BODY_RECOVERY_TICKS)
        assertEquals(12226, Dt2Assets.VARDORVIS_HEAD)
        assertEquals(2521, Dt2Assets.VARDORVIS_HEAD_PROJECTILE)
    }

    @Test
    fun `strangle count window and no consecutive selector are deterministic`() {
        assertEquals(4, Dt2Rules.vardorvisStrangleCount(569))
        assertEquals(8, Dt2Rules.vardorvisStrangleCount(0))
        assertEquals(5, Dt2Rules.VARDORVIS_STRANGLE_WINDOW_TICKS)
        repeat(20) { roll ->
            assertNotEquals(
                VardorvisSpecial.STRANGLE,
                Dt2Rules.vardorvisSelectSpecial(
                    roll,
                    previous = VardorvisSpecial.STRANGLE,
                    headUnlocked = true,
                ),
            )
        }
    }

    @Test
    fun `enrage threshold is exact and shortens scheduling`() {
        assertFalse(Dt2Rules.vardorvisEnraged(232))
        assertTrue(Dt2Rules.vardorvisEnraged(231))
        assertTrue(
            Dt2Rules.vardorvisSpecialDelay(enraged = true) <
                Dt2Rules.vardorvisSpecialDelay(enraged = false)
        )
    }
}
