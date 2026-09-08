package org.rsmod.api.dailyadventure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyAdventureEngineTest {
    private val engine = DailyAdventureEngine("test-private-salt")
    private val eligible: DailyAdventureEligibility = { true }

    @Test
    fun `catalogue has required definitions and reroll capacity`() {
        val grouped = DailyAdventureCatalogue.tasks.groupBy { it.path to it.difficulty }
        assertTrue(DailyAdventureCatalogue.tasks.count { it.path == DailyAdventurePath.Combat } >= 25)
        assertTrue(DailyAdventureCatalogue.tasks.count { it.path == DailyAdventurePath.Skilling } >= 25)
        DailyAdventurePath.entries.forEach { path ->
            assertTrue(grouped.getValue(path to DailyAdventureDifficulty.Easy).size >= 9)
            assertTrue(grouped.getValue(path to DailyAdventureDifficulty.Medium).size >= 9)
            assertTrue(grouped.getValue(path to DailyAdventureDifficulty.Hard).size >= 7)
        }
        assertEquals(
            DailyAdventureCatalogue.tasks.size,
            DailyAdventureCatalogue.tasks.map { it.id }.distinct().size,
        )
    }

    @Test
    fun `member generation is deterministic and preserves four four two composition`() {
        val first = DailyAdventureState()
        val second = DailyAdventureState()
        engine.ensureCurrentDay(first, ACCOUNT, DAY, member = true, eligible, NOW)
        engine.ensureCurrentDay(second, ACCOUNT, DAY, member = true, eligible, NOW)

        assertEquals(setOf(DailyAdventurePath.Combat, DailyAdventurePath.Skilling), first.activePaths)
        assertEquals(first.assignments, second.assignments)
        first.assignments.values.forEach { assignments ->
            assertEquals(10, assignments.size)
            assertEquals(4, assignments.count { it.difficulty == DailyAdventureDifficulty.Easy })
            assertEquals(4, assignments.count { it.difficulty == DailyAdventureDifficulty.Medium })
            assertEquals(2, assignments.count { it.difficulty == DailyAdventureDifficulty.Hard })
            assertEquals(assignments.size, assignments.map { it.taskId }.distinct().size)
            assertTrue(assignments.all { it.generatedDay == DAY })
        }
    }

    @Test
    fun `free account sees no assignments until one immutable selection`() {
        val state = DailyAdventureState()
        engine.ensureCurrentDay(state, ACCOUNT, DAY, member = false, eligible, NOW)
        assertTrue(state.activePaths.isEmpty())
        assertTrue(state.assignments.isEmpty())
        assertNull(state.selectedFreePath)

        assertEquals(
            DailyAdventureSelectionStatus.Selected,
            engine.selectFreePath(state, ACCOUNT, DAY, DailyAdventurePath.Combat, eligible, NOW),
        )
        assertEquals(setOf(DailyAdventurePath.Combat), state.activePaths)
        assertEquals(DailyAdventurePath.Combat, state.selectedFreePath)
        assertEquals(
            DailyAdventureSelectionStatus.AlreadySelected,
            engine.selectFreePath(state, ACCOUNT, DAY, DailyAdventurePath.Skilling, eligible, NOW),
        )
        assertFalse(DailyAdventurePath.Skilling in state.assignments)
    }

    @Test
    fun `midday membership upgrade preserves work raises shared cap and queues earned box`() {
        val state = DailyAdventureState()
        engine.ensureCurrentDay(state, ACCOUNT, DAY, member = false, eligible, NOW)
        engine.selectFreePath(state, ACCOUNT, DAY, DailyAdventurePath.Combat, eligible, NOW)
        state.assignments.getValue(DailyAdventurePath.Combat).first().progress = 7
        state.rerollsUsedToday = 2
        state.claimedPaths += DailyAdventurePath.Combat

        val result = engine.ensureCurrentDay(state, ACCOUNT, DAY, member = true, eligible, NOW + 1)
        assertTrue(result.upgradedToMember)
        assertTrue(result.memberBoxQueued)
        assertEquals(7, state.assignments.getValue(DailyAdventurePath.Combat).first().progress)
        assertEquals(2, state.rerollsUsedToday)
        assertEquals(3, state.rerollsRemaining())
        assertEquals(1, state.pendingMemberBoxes)
        assertEquals(setOf(DailyAdventurePath.Combat, DailyAdventurePath.Skilling), state.activePaths)

        engine.ensureCurrentDay(state, ACCOUNT, DAY, member = false, eligible, NOW + 2)
        assertTrue(state.memberDayEntitlement)
        assertEquals(5, state.rerollCap())
        assertEquals(2, state.activePaths.size)
    }

    @Test
    fun `partial progress reroll is same difficulty atomic shared and idempotent`() {
        val state = DailyAdventureState()
        engine.ensureCurrentDay(state, ACCOUNT, DAY, member = true, eligible, NOW)
        val path = DailyAdventurePath.Combat
        val assignment = state.assignments.getValue(path).first()
        val oldTask = assignment.taskId
        val difficulty = assignment.difficulty
        assignment.progress = 3

        val first = engine.reroll(state, ACCOUNT, DAY, path, assignment.slot, "request-1", eligible, NOW)
        assertEquals(DailyAdventureRerollStatus.Rerolled, first.status)
        assertNotEquals(oldTask, assignment.taskId)
        assertEquals(difficulty, assignment.difficulty)
        assertEquals(0, assignment.progress)
        assertEquals(1, state.rerollsUsedToday)
        assertEquals(4, state.rerollsRemaining())

        val duplicate = engine.reroll(state, ACCOUNT, DAY, path, assignment.slot, "request-1", eligible, NOW)
        assertEquals(first.replacementTaskId, duplicate.replacementTaskId)
        assertTrue(duplicate.duplicateRequest)
        assertEquals(1, state.rerollsUsedToday)
    }

    @Test
    fun `progress awards points once and duplicate canonical event is ignored`() {
        val definition =
            DailyAdventureTaskDefinition(
                id = "T01",
                path = DailyAdventurePath.Combat,
                difficulty = DailyAdventureDifficulty.Easy,
                name = "Test",
                description = "Test damage",
                goal = 10,
                reward = 15,
                matcher = { if (it.action == DailyAdventureAction.NpcDamage) it.amount else 0 },
            )
        val localEngine = DailyAdventureEngine("test-private-salt", listOf(definition))
        val state =
            DailyAdventureState(
                day = DAY,
                activePaths = mutableSetOf(DailyAdventurePath.Combat),
                assignments =
                    mutableMapOf(
                        DailyAdventurePath.Combat to
                            mutableListOf(
                                DailyAdventureAssignment(
                                    slot = 0,
                                    taskId = definition.id,
                                    difficulty = definition.difficulty,
                                    generatedDay = DAY,
                                    goal = definition.goal,
                                    reward = definition.reward,
                                )
                            )
                    ),
            )
        val event =
            DailyAdventureProgressEvent(
                id = "npc-hit:1",
                action = DailyAdventureAction.NpcDamage,
                amount = 10,
            )
        val first = localEngine.progress(state, DAY, event, NOW)
        val duplicate = localEngine.progress(state, DAY, event, NOW)
        assertEquals(15, first.pointsAwarded)
        assertEquals(15, state.taskPoints)
        assertTrue(state.assignments.getValue(DailyAdventurePath.Combat).single().complete)
        assertTrue(duplicate.duplicateEvent)
        assertEquals(15, state.taskPoints)
    }

    @Test
    fun `dimension requirements and per-dimension caps are enforced`() {
        val definition =
            DailyAdventureTaskDefinition(
                id = "T02",
                path = DailyAdventurePath.Skilling,
                difficulty = DailyAdventureDifficulty.Medium,
                name = "Dimensions",
                description = "Three skills",
                goal = 6,
                reward = 30,
                minimumDistinctDimensions = 3,
                perDimensionCap = 2,
                matcher = { it.amount },
            )
        val localEngine = DailyAdventureEngine("test-private-salt", listOf(definition))
        val assignment =
            DailyAdventureAssignment(
                slot = 0,
                taskId = definition.id,
                difficulty = definition.difficulty,
                generatedDay = DAY,
                goal = definition.goal,
                reward = definition.reward,
            )
        val state =
            DailyAdventureState(
                day = DAY,
                activePaths = mutableSetOf(DailyAdventurePath.Skilling),
                assignments = mutableMapOf(DailyAdventurePath.Skilling to mutableListOf(assignment)),
            )
        fun add(id: String, skill: String, amount: Long) =
            localEngine.progress(
                state,
                DAY,
                DailyAdventureProgressEvent(id, DailyAdventureAction.SkillingAction, amount, skill),
                NOW,
            )
        add("a", "mining", 10)
        assertEquals(2, assignment.progress)
        assertFalse(assignment.complete)
        add("b", "woodcutting", 2)
        add("c", "fishing", 2)
        assertEquals(6, assignment.progress)
        assertTrue(assignment.complete)
        assertEquals(30, state.taskPoints)
    }

    @Test
    fun `member claims each bonus but advances streak and grants box only once`() {
        val state = DailyAdventureState()
        engine.ensureCurrentDay(state, ACCOUNT, DAY, member = true, eligible, NOW)
        state.assignments.values.forEach { tasks -> tasks.take(8).forEach { it.complete = true } }

        val combat = engine.claim(state, DAY, DailyAdventurePath.Combat, NOW)
        val skilling = engine.claim(state, DAY, DailyAdventurePath.Skilling, NOW + 1)
        val duplicate = engine.claim(state, DAY, DailyAdventurePath.Combat, NOW + 2)
        assertEquals(DailyAdventureClaimStatus.Claimed, combat.status)
        assertEquals(DailyAdventureClaimStatus.Claimed, skilling.status)
        assertTrue(combat.streakAdvanced)
        assertFalse(skilling.streakAdvanced)
        assertEquals(100, combat.bonusPoints)
        assertEquals(100, skilling.bonusPoints)
        assertEquals(1, state.streak)
        assertEquals(1, state.pendingMemberBoxes)
        assertEquals(200, state.taskPoints)
        assertEquals(DailyAdventureClaimStatus.AlreadyClaimed, duplicate.status)
        assertEquals(200, state.taskPoints)
    }

    @Test
    fun `missed reset days consume protection then reset streak`() {
        val state = DailyAdventureState(
            day = DAY,
            streak = 14,
            bestStreak = 14,
            lastClaimDay = DAY,
            streakAdvancedDay = DAY,
            streakProtectionCharges = 1,
        )
        val protected = engine.ensureCurrentDay(state, ACCOUNT, DAY + 2, member = false, eligible, NOW)
        assertEquals(1, protected.protectionChargesConsumed)
        assertEquals(14, state.streak)
        assertEquals(0, state.streakProtectionCharges)

        engine.ensureCurrentDay(state, ACCOUNT, DAY + 4, member = false, eligible, NOW)
        assertEquals(0, state.streak)
        assertEquals(14, state.bestStreak)
    }

    @Test
    fun `streak reward boundaries use the post-claim streak`() {
        assertEquals(100, DailyAdventureRewards.streakBonus(1))
        assertEquals(100, DailyAdventureRewards.streakBonus(2))
        assertEquals(115, DailyAdventureRewards.streakBonus(3))
        assertEquals(135, DailyAdventureRewards.streakBonus(7))
        assertEquals(160, DailyAdventureRewards.streakBonus(14))
        assertEquals(200, DailyAdventureRewards.streakBonus(30))
        assertEquals(240, DailyAdventureRewards.streakBonus(60))
        assertEquals(300, DailyAdventureRewards.streakBonus(100))
    }

    private companion object {
        const val ACCOUNT: String = "00000000-0000-0000-0000-000000000001"
        const val DAY: Long = 20_000L
        const val NOW: Long = DAY * 86_400_000L
    }
}
