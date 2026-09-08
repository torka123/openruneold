package org.rsmod.content.minigames.toa

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.entity.player.PlayerUid

class ToaUniversalInvocationRulesTest {
    @Test
    fun `universal combat stats scale every five raid levels and by path`() {
        assertEquals(100, ToaRules.combatStat(100, raidLevel = 0, pathLevel = 0))
        assertEquals(102, ToaRules.combatStat(100, raidLevel = 5, pathLevel = 0))
        assertEquals(110, ToaRules.combatStat(100, raidLevel = 5, pathLevel = 1))
        assertEquals(0, ToaRules.combatStat(0, raidLevel = 600, pathLevel = 6))
    }

    @Test
    fun `timer tiers expire at greater than or equal boundary and apply published penalties`() {
        val tiers =
            listOf(
                ToaInvocation.WALK_FOR_IT to (40 * 100 to 20),
                ToaInvocation.JOG_FOR_IT to (35 * 100 to 30),
                ToaInvocation.RUN_FOR_IT to (30 * 100 to 40),
                ToaInvocation.SPRINT_FOR_IT to (25 * 100 to 50),
            )
        tiers.forEach { (invocation, expected) ->
            val invocations = setOf(invocation)
            assertEquals(expected.first, ToaRules.timeLimitTicks(invocations))
            assertEquals(expected.second, ToaRules.timePenaltyLevels(invocations))
            assertFalse(
                ToaRules.timerHasExpired(100, 100 + expected.first - 1, expected.first, false)
            )
            assertTrue(ToaRules.timerHasExpired(100, 100 + expected.first, expected.first, false))
            assertFalse(
                ToaRules.timerHasExpired(100, 100 + expected.first + 1, expected.first, true)
            )
        }
        assertFalse(ToaRules.timerHasExpired(null, Int.MAX_VALUE, 1, false))
    }

    @Test
    fun `help tiers are exact and always retain one scaled item`() {
        assertEquals(0.66, ToaRules.supplyMultiplier(setOf(ToaInvocation.NEED_SOME_HELP)))
        assertEquals(0.33, ToaRules.supplyMultiplier(setOf(ToaInvocation.NEED_LESS_HELP)))
        assertEquals(0.10, ToaRules.supplyMultiplier(setOf(ToaInvocation.NO_HELP_NEEDED)))
        assertEquals(1, ToaSupplyInvocationRules.scaled(1, setOf(ToaInvocation.NO_HELP_NEEDED)))
        assertEquals(6, ToaSupplyInvocationRules.scaled(10, setOf(ToaInvocation.NEED_SOME_HELP)))
    }

    @Test
    fun `on a diet removes wipe food`() {
        val diet = setOf(ToaInvocation.ON_A_DIET)
        assertFalse(ToaSupplyInvocationRules.givesHoneyLocust(diet))
        assertTrue(ToaSupplyInvocationRules.givesHoneyLocust(emptySet()))
    }

    @Test
    fun `quiet prayers subtracts ten percentage points`() {
        val quiet = setOf(ToaInvocation.QUIET_PRAYERS)
        assertEquals(90, ToaRules.prayerReductionPercent(100, quiet))
        assertEquals(75, ToaRules.prayerReductionPercent(85, quiet))
        assertEquals(65, ToaRules.prayerReductionPercent(75, quiet))
        assertEquals(75, ToaRules.prayerReductionPercent(75, emptySet()))
    }

    @Test
    fun `deadly prayers rounds down and excludes unprotected or self inflicted damage`() {
        val deadly = setOf(ToaInvocation.DEADLY_PRAYERS)
        assertEquals(1, ToaRules.deadlyPrayerDrain(9, true, deadly))
        assertEquals(2, ToaRules.deadlyPrayerDrain(10, true, deadly))
        assertEquals(0, ToaRules.deadlyPrayerDrain(4, true, deadly))
        assertEquals(0, ToaRules.deadlyPrayerDrain(50, false, deadly))
        assertEquals(0, ToaRules.deadlyPrayerDrain(50, true, emptySet()))
        assertEquals(0, ToaRules.deadlyPrayerDrain(50, true, deadly, selfInflicted = true))
    }

    @Test
    fun `attempts count only a true eligible team wipe`() {
        val first = ToaParticipant(PlayerUid(1), accountHash = 101L, spectating = true)
        val disconnected =
            ToaParticipant(PlayerUid(2), accountHash = 202L, connected = false, spectating = false)
        val participants = listOf(first, disconnected)

        assertFalse(ToaWipeRules.isTrueWipe(participants, completionPending = false))
        disconnected.spectating = true
        assertTrue(ToaWipeRules.isTrueWipe(participants, completionPending = false))
        assertFalse(ToaWipeRules.isTrueWipe(participants, completionPending = true))
        disconnected.rewardEligible = false
        assertTrue(ToaWipeRules.isTrueWipe(participants, completionPending = false))
    }

    @Test
    fun `walk the path grants exactly two one one and caps every path at six`() {
        val levels = TOA_ACTIVE_PATHS.associateWith { 0 }.toMutableMap()
        val completed = linkedSetOf<ToaPath>()
        val raisedCounts = mutableListOf<Int>()
        TOA_ACTIVE_PATHS.forEachIndexed { index, path ->
            completed += path
            raisedCounts +=
                ToaRules.levelRemainingPaths(levels, completed, Random(0x5EEDL + index)).size
        }

        assertEquals(listOf(2, 1, 1, 0), raisedCounts)
        assertEquals(4, levels.values.sum())

        val capped = TOA_ACTIVE_PATHS.associateWith { 6 }.toMutableMap()
        assertTrue(
            ToaRules.levelRemainingPaths(capped, setOf(ToaPath.SCABARAS), Random(42)).isEmpty()
        )
        assertEquals(setOf(6), capped.values.toSet())
    }
}
