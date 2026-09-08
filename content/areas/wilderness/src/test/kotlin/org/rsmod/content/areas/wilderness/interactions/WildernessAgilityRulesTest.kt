package org.rsmod.content.areas.wilderness.interactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class WildernessAgilityRulesTest {
    @Test
    fun `course thirteen uses donor success and verified failure sequences`() {
        assertEquals(763, WildernessAgilitySequences.BALANCE_READY)
        assertEquals(762, WildernessAgilitySequences.BALANCE_WALK)
        assertEquals(749, WildernessAgilitySequences.PIPE_ENTER)
        assertEquals(749, WildernessAgilitySequences.PIPE_CRAWL)
        assertEquals(749, WildernessAgilitySequences.PIPE_EXIT)
        assertEquals(751, WildernessAgilitySequences.ROPE)
        assertEquals(1105, WildernessAgilitySequences.ROPE_MISS)
        assertEquals(741, WildernessAgilitySequences.STEPPING_STONE)
        assertEquals(764, WildernessAgilitySequences.LOG_STUMBLE)
        assertEquals(740, WildernessAgilitySequences.ROCK_CLIMB)
    }

    @Test
    fun `effective level access boundaries are boost aware`() {
        assertFalse(WildernessAgilityRules.canEnter(51))
        assertTrue(WildernessAgilityRules.canEnter(52))
        assertTrue(WildernessAgilityRules.canEnter(99))
        assertFalse(WildernessAgilityRules.canUsePipe(48))
        assertTrue(WildernessAgilityRules.canUsePipe(49))
    }

    @Test
    fun `ordered successes produce exactly one complete lap state`() {
        var stage = 0
        stage = WildernessAgilityRules.advance(stage, WildernessAgilityRules.Obstacle.Pipe, true)
        assertEquals(1, stage)
        stage = WildernessAgilityRules.advance(stage, WildernessAgilityRules.Obstacle.Rope, true)
        assertEquals(2, stage)
        stage = WildernessAgilityRules.advance(stage, WildernessAgilityRules.Obstacle.Stones, true)
        assertEquals(3, stage)
        stage = WildernessAgilityRules.advance(stage, WildernessAgilityRules.Obstacle.Log, true)
        assertEquals(4, stage)
        stage = WildernessAgilityRules.advance(stage, WildernessAgilityRules.Obstacle.Rocks, true)
        assertEquals(0, stage)
    }

    @Test
    fun `wrong order and skipped obstacles cannot advance`() {
        assertEquals(
            0,
            WildernessAgilityRules.advance(0, WildernessAgilityRules.Obstacle.Rope, true),
        )
        assertEquals(
            1,
            WildernessAgilityRules.advance(1, WildernessAgilityRules.Obstacle.Stones, true),
        )
        assertEquals(
            3,
            WildernessAgilityRules.advance(3, WildernessAgilityRules.Obstacle.Rocks, true),
        )
    }

    @Test
    fun `failed obstacles retain their retry stage`() {
        assertEquals(
            1,
            WildernessAgilityRules.advance(1, WildernessAgilityRules.Obstacle.Rope, false),
        )
        assertEquals(
            2,
            WildernessAgilityRules.advance(2, WildernessAgilityRules.Obstacle.Stones, false),
        )
        assertEquals(
            3,
            WildernessAgilityRules.advance(3, WildernessAgilityRules.Obstacle.Log, false),
        )
    }

    @Test
    fun `repeated completed obstacle cannot duplicate stage credit`() {
        assertFalse(WildernessAgilityRules.ordered(1, WildernessAgilityRules.Obstacle.Pipe))
        assertFalse(WildernessAgilityRules.ordered(2, WildernessAgilityRules.Obstacle.Rope))
        assertFalse(WildernessAgilityRules.ordered(3, WildernessAgilityRules.Obstacle.Stones))
        assertFalse(WildernessAgilityRules.ordered(4, WildernessAgilityRules.Obstacle.Log))
    }

    @Test
    fun `full lap xp preserves the decimal base total`() {
        val sum =
            WildernessAgilityRules.PIPE_XP +
                WildernessAgilityRules.ROPE_XP +
                WildernessAgilityRules.STONES_XP +
                WildernessAgilityRules.LOG_XP +
                WildernessAgilityRules.LAP_XP
        assertEquals(571.4, sum)
        assertEquals(WildernessAgilityRules.FULL_LAP_XP, sum)
    }

    @Test
    fun `course entrance aligns to the centreline before balanced movement`() {
        assertEquals(CoordGrid(2998, 3916), WildernessAgilityRules.ENTRANCE_SOUTH_APPROACH_TILE)
        assertEquals(CoordGrid(2998, 3932), WildernessAgilityRules.ENTRANCE_NORTH_APPROACH_TILE)
        assertEquals(
            16,
            WildernessAgilityRules.ENTRANCE_SOUTH_APPROACH_TILE.chebyshevDistance(
                WildernessAgilityRules.ENTRANCE_NORTH_APPROACH_TILE
            ),
        )
    }

    @Test
    fun `log crossing aligns on the safe east tile before moving west`() {
        assertEquals(CoordGrid(3002, 3945), WildernessAgilityRules.LOG_APPROACH_TILE)
        assertEquals(CoordGrid(2995, 3945), WildernessAgilityRules.LOG_EXIT_TILE)
    }

    @Test
    fun `rope and log damage use fifteen percent plus one and can kill`() {
        assertEquals(1, WildernessAgilityRules.failureDamage(1, 15))
        assertEquals(2, WildernessAgilityRules.failureDamage(10, 15))
        assertEquals(15, WildernessAgilityRules.failureDamage(99, 15))
        assertEquals(16, WildernessAgilityRules.failureDamage(100, 15))
    }

    @Test
    fun `stepping stone damage uses twenty percent plus one`() {
        assertEquals(1, WildernessAgilityRules.failureDamage(1, 20))
        assertEquals(3, WildernessAgilityRules.failureDamage(10, 20))
        assertEquals(20, WildernessAgilityRules.failureDamage(99, 20))
        assertEquals(21, WildernessAgilityRules.failureDamage(100, 20))
    }

    @Test
    fun `success curve has deterministic boundaries and nonzero level 99 failure`() {
        assertEquals(152, WildernessAgilityRules.successThreshold(49))
        assertEquals(157, WildernessAgilityRules.successThreshold(52))
        assertEquals(250, WildernessAgilityRules.successThreshold(99))
        assertTrue(WildernessAgilityRules.succeeds(99, 249))
        assertFalse(WildernessAgilityRules.succeeds(99, 250))
        assertFalse(WildernessAgilityRules.succeeds(99, 255))
    }

    @Test
    fun `reward tiers use all four exact lap boundaries`() {
        assertEquals(WildernessAgilityRules.RewardTier.One, WildernessAgilityRules.rewardTier(1))
        assertEquals(WildernessAgilityRules.RewardTier.One, WildernessAgilityRules.rewardTier(15))
        assertEquals(WildernessAgilityRules.RewardTier.Two, WildernessAgilityRules.rewardTier(16))
        assertEquals(WildernessAgilityRules.RewardTier.Two, WildernessAgilityRules.rewardTier(30))
        assertEquals(WildernessAgilityRules.RewardTier.Three, WildernessAgilityRules.rewardTier(31))
        assertEquals(WildernessAgilityRules.RewardTier.Three, WildernessAgilityRules.rewardTier(60))
        assertEquals(WildernessAgilityRules.RewardTier.Four, WildernessAgilityRules.rewardTier(61))
        assertEquals(
            WildernessAgilityRules.RewardTier.Four,
            WildernessAgilityRules.rewardTier(Int.MAX_VALUE),
        )
    }

    @Test
    fun `ticket redemption boundaries use exact xp bands`() {
        assertEquals(200.0, WildernessAgilityRules.ticketXp(1))
        assertEquals(2_000.0, WildernessAgilityRules.ticketXp(10))
        assertEquals(2_310.0, WildernessAgilityRules.ticketXp(11))
        assertEquals(10_500.0, WildernessAgilityRules.ticketXp(50))
        assertEquals(11_220.0, WildernessAgilityRules.ticketXp(51))
        assertEquals(22_000.0, WildernessAgilityRules.ticketXp(100))
        assertEquals(23_230.0, WildernessAgilityRules.ticketXp(101))
        assertEquals(0.0, WildernessAgilityRules.ticketXp(0))
    }

    @Test
    fun `very large ticket stacks do not overflow integer arithmetic`() {
        assertEquals(
            Int.MAX_VALUE.toLong().toDouble() * 230.0,
            WildernessAgilityRules.ticketXp(Int.MAX_VALUE),
        )
    }

    @Test
    fun `logout subtracts ten once and floors at zero`() {
        assertEquals(0, WildernessAgilityRules.logoutStreak(0))
        assertEquals(0, WildernessAgilityRules.logoutStreak(5))
        assertEquals(0, WildernessAgilityRules.logoutStreak(10))
        assertEquals(1, WildernessAgilityRules.logoutStreak(11))
        assertEquals(50, WildernessAgilityRules.logoutStreak(60))
        assertEquals(51, WildernessAgilityRules.logoutStreak(61))
    }

    @Test
    fun `course area contains surface dungeon ladders and failures`() {
        assertTrue(WildernessAgilityRules.isCourseArea(CoordGrid(3005, 3936)))
        assertTrue(WildernessAgilityRules.isCourseArea(CoordGrid(3005, 3963)))
        assertTrue(WildernessAgilityRules.isCourseArea(CoordGrid(3005, 10363)))
        assertTrue(WildernessAgilityRules.isCourseArea(CoordGrid(3005, 10352)))
        assertTrue(WildernessAgilityRules.isCourseArea(CoordGrid(3001, 10345)))
    }

    @Test
    fun `true course exits and unrelated planes are outside`() {
        assertFalse(WildernessAgilityRules.isCourseArea(CoordGrid(2998, 3931)))
        assertFalse(WildernessAgilityRules.isCourseArea(CoordGrid(2998, 3917)))
        assertFalse(WildernessAgilityRules.isCourseArea(CoordGrid(3005, 3963, 1)))
        assertFalse(WildernessAgilityRules.isCourseArea(CoordGrid(3200, 10352)))
    }
}
