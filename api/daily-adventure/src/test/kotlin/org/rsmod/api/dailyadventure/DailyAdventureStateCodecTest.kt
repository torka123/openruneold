package org.rsmod.api.dailyadventure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyAdventureStateCodecTest {
    @Test
    fun `state round trips account currency assignments receipts and scrolls`() {
        val assignment =
            DailyAdventureAssignment(
                slot = 0,
                taskId = "C01",
                difficulty = DailyAdventureDifficulty.Easy,
                generatedDay = 20_000,
                resolvedTarget = "monster",
                goal = 25,
                reward = 15,
                progress = 25,
                complete = true,
                rewardCredited = true,
                completedAtEpochMillis = 123_456,
                rerollCount = 1,
                dimensions = mutableMapOf("melee" to 25),
            )
        val assignments = MutableList(DailyAdventureEngine.TASKS_PER_PATH) { slot ->
            assignment.copy(slot = slot, taskId = "C${(slot + 1).toString().padStart(2, '0')}")
        }
        val state =
            DailyAdventureState(
                day = 20_000,
                memberDayEntitlement = true,
                activePaths = mutableSetOf(DailyAdventurePath.Combat),
                assignments = mutableMapOf(DailyAdventurePath.Combat to assignments),
                claimedPaths = mutableSetOf(DailyAdventurePath.Combat),
                seenTaskIds = mutableMapOf(DailyAdventurePath.Combat to mutableSetOf("C01", "C11")),
                rerollsUsedToday = 2,
                rerollSequence = 2,
                taskPoints = 9_000_000_000L,
                streak = 14,
                bestStreak = 20,
                lastClaimDay = 20_000,
                streakAdvancedDay = 20_000,
                streakProtectionCharges = 2,
                memberBoxGrantedDay = 20_000,
                pendingMemberBoxes = 1,
                updatedAtEpochMillis = 123_456,
                recentEventIds = mutableListOf("event-1"),
                recentRerollResults =
                    mutableMapOf(
                        "request-1" to
                            DailyAdventureRerollResult(
                                DailyAdventureRerollStatus.Rerolled,
                                "C01",
                                "C02",
                            )
                    ),
                activeScrolls = mutableMapOf("double_points" to 999_999),
                lifetime =
                    DailyAdventureLifetimeStats(
                        tasksCompleted = 40,
                        pathsClaimed = 5,
                        rerollsUsed = 9,
                        pointsEarned = 10_000,
                        pointsSpent = 1_000,
                        protectionChargesConsumed = 1,
                    ),
            )
        val decoded = assertNotNull(DailyAdventureStateCodec.decode(DailyAdventureStateCodec.encode(state)))
        assertEquals(state.day, decoded.day)
        assertEquals(state.taskPoints, decoded.taskPoints)
        assertEquals(state.activePaths, decoded.activePaths)
        assertEquals(state.assignments, decoded.assignments)
        assertEquals(state.recentRerollResults, decoded.recentRerollResults)
        assertEquals(state.activeScrolls, decoded.activeScrolls)
        assertEquals(state.lifetime, decoded.lifetime)
    }

    @Test
    fun `unknown schemas and invalid active assignment lists fail closed`() {
        assertNull(DailyAdventureStateCodec.decode(mapOf("schemaVersion" to 99)))
        val raw =
            mutableMapOf<String, Any>(
                "schemaVersion" to 1,
                "day" to 20_000L,
                "activePaths" to mutableListOf("Combat"),
                "assignments" to mutableMapOf<String, Any>("Combat" to mutableListOf<Any>()),
                "taskPoints" to -500L,
                "streakProtectionCharges" to 99,
            )
        val decoded = assertNotNull(DailyAdventureStateCodec.decode(raw))
        assertTrue(decoded.activePaths.isEmpty())
        assertTrue(decoded.assignments.isEmpty())
        assertEquals(0, decoded.taskPoints)
        assertEquals(DailyAdventureState.MAX_PROTECTION_CHARGES, decoded.streakProtectionCharges)
    }
}
