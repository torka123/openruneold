package org.rsmod.content.systems.dailyadventure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.dailyadventure.DailyAdventureAssignment
import org.rsmod.api.dailyadventure.DailyAdventureCatalogue
import org.rsmod.api.dailyadventure.DailyAdventureDifficulty
import org.rsmod.api.dailyadventure.DailyAdventurePath
import org.rsmod.api.dailyadventure.DailyAdventureState

class NativeDailyAdventureViewProjectionTest {
    @Test
    fun `all ten real Skilling tasks populate native rows and selected details`() {
        val state = dailyState(DailyAdventurePath.Skilling)
        val selectedAssignment = state.assignments.getValue(DailyAdventurePath.Skilling)[7]
        selectedAssignment.progress = 12L
        val expected = DailyAdventureCatalogue.byId.getValue(selectedAssignment.taskId)

        val view =
            NativeDailyAdventureViewProjection.project(
                state,
                DailyAdventurePath.Skilling,
                selectedSlot = 7,
            )

        assertEquals(10, view.rows.size)
        assertEquals(expected.name, view.selected?.name)
        assertEquals(expected.description, view.selected?.description)
        assertEquals(12L, view.selected?.progress)
        assertEquals(expected.reward, view.selected?.reward)
        assertTrue(view.canRerollSelected)
    }

    @Test
    fun `view changes immediately when selected assignment is rerolled`() {
        val state = dailyState(DailyAdventurePath.Combat)
        val assignment = state.assignments.getValue(DailyAdventurePath.Combat)[0]
        val before =
            NativeDailyAdventureViewProjection.project(state, DailyAdventurePath.Combat, 0)
                .selected
        val replacement =
            DailyAdventureCatalogue.tasks.first {
                it.path == DailyAdventurePath.Combat &&
                    it.difficulty == assignment.difficulty &&
                    it.id != assignment.taskId
            }
        assignment.taskId = replacement.id
        assignment.goal = replacement.goal
        assignment.reward = replacement.reward
        assignment.progress = 0L

        val after =
            NativeDailyAdventureViewProjection.project(state, DailyAdventurePath.Combat, 0)
                .selected

        assertEquals(replacement.name, after?.name)
        assertEquals(replacement.description, after?.description)
        assertEquals(replacement.reward, after?.reward)
        assertFalse(before?.taskId == after?.taskId)
    }

    @Test
    fun `reset control is unavailable for complete tasks or exhausted rerolls`() {
        val state = dailyState(DailyAdventurePath.Combat)
        val selected = state.assignments.getValue(DailyAdventurePath.Combat)[0]
        selected.complete = true
        selected.progress = selected.goal
        assertFalse(
            NativeDailyAdventureViewProjection.project(state, DailyAdventurePath.Combat, 0)
                .canRerollSelected
        )

        selected.complete = false
        selected.progress = 0L
        state.rerollsUsedToday = state.rerollCap()
        assertFalse(
            NativeDailyAdventureViewProjection.project(state, DailyAdventurePath.Combat, 0)
                .canRerollSelected
        )
    }

    private fun dailyState(path: DailyAdventurePath): DailyAdventureState {
        val difficulties =
            listOf(
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Hard,
                DailyAdventureDifficulty.Hard,
            )
        val definitions = difficulties.map { difficulty ->
            DailyAdventureCatalogue.tasks.first { it.path == path && it.difficulty == difficulty }
        }
        return DailyAdventureState(day = 1L).also { state ->
            state.activePaths += path
            state.assignments[path] =
                definitions.mapIndexedTo(mutableListOf()) { slot, definition ->
                    DailyAdventureAssignment(
                        slot = slot,
                        taskId = definition.id,
                        difficulty = definition.difficulty,
                        generatedDay = 1L,
                        goal = definition.goal,
                        reward = definition.reward,
                    )
                }
        }
    }
}
