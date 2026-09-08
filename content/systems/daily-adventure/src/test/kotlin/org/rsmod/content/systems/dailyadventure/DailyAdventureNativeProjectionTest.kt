package org.rsmod.content.systems.dailyadventure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.dailyadventure.DailyAdventureAssignment
import org.rsmod.api.dailyadventure.DailyAdventureDifficulty
import org.rsmod.api.dailyadventure.DailyAdventurePath
import org.rsmod.api.dailyadventure.DailyAdventureState

class DailyAdventureNativeProjectionTest {
    @Test
    fun `only the two Daily paths map to legacy path rows`() {
        assertEquals(DailyAdventurePath.Combat, DailyAdventureNativeProjection.pathForRow(0))
        assertEquals(DailyAdventurePath.Skilling, DailyAdventureNativeProjection.pathForRow(1))
        assertNull(DailyAdventureNativeProjection.pathForRow(2))
        assertNull(DailyAdventureNativeProjection.pathForRow(4))

        assertEquals(0, DailyAdventureNativeProjection.pathRow(10, 10))
        assertEquals(1, DailyAdventureNativeProjection.pathRow(49, 10))
        assertNull(DailyAdventureNativeProjection.pathRow(50, 10))
    }

    @Test
    fun `native task stride resolves all legacy rows without inventing Daily slots`() {
        assertEquals(0, DailyAdventureNativeProjection.taskRow(11))
        assertEquals(9, DailyAdventureNativeProjection.taskRow(119))
        assertEquals(10, DailyAdventureNativeProjection.taskRow(131))
        assertEquals(14, DailyAdventureNativeProjection.taskRow(179))
        assertNull(DailyAdventureNativeProjection.taskRow(12))
        assertNull(DailyAdventureNativeProjection.taskRow(191))
    }

    @Test
    fun `revision 240 client extensions remain disabled until the overlay is distributed`() {
        assertFalse(NativeDailyAdventureComponents.ClientExtensionsEnabled)
    }

    @Test
    fun `server-owned children are gated until the client interface contains them`() {
        val stockRevision240Children = 0 until NativeDailyAdventureComponents.RowBackgroundFirst
        assertFalse(
            NativeDailyAdventureComponents.hasRequiredClientChildren(stockRevision240Children.toList())
        )
        assertTrue(
            NativeDailyAdventureComponents.hasRequiredClientChildren((0..NativeDailyAdventureComponents.Reset).toList())
        )
    }

    @Test
    fun `stock completion label uses all ten assignments and caps at ten`() {
        val state = DailyAdventureState()
        state.assignments[DailyAdventurePath.Skilling] =
            (0 until 12).mapTo(mutableListOf()) { slot -> assignment(slot, complete = true) }

        assertEquals(
            10,
            NativeDailyAdventureComponents.completedTasks(state, DailyAdventurePath.Skilling),
        )
        assertEquals(0, NativeDailyAdventureComponents.completedTasks(state, DailyAdventurePath.Combat))
        assertEquals(0, NativeDailyAdventureComponents.completedTasks(state, null))
        assertEquals(9, NativeDailyAdventureComponents.StockNavigationIconSub)
        assertEquals(9, NativeDailyAdventureComponents.StockPathRewardTextSub)
        assertEquals(0, NativeDailyAdventureComponents.StockTasksDoneTextSub)
        assertEquals(9, NativeDailyAdventureComponents.StockTaskFocusIconSub)
    }

    @Test
    fun `projection exposes ten Combat and only three Skilling rows`() {
        val state = DailyAdventureState()
        state.assignments[DailyAdventurePath.Combat] =
            (0 until 10).mapTo(mutableListOf()) { slot -> assignment(slot, slot == 0 || slot == 9) }
        state.assignments[DailyAdventurePath.Skilling] =
            (0 until 10).mapTo(mutableListOf()) { slot -> assignment(slot, slot == 0 || slot == 2) }
        state.claimedPaths += DailyAdventurePath.Combat
        state.claimedPaths += DailyAdventurePath.Skilling

        val projection = DailyAdventureNativeProjection.project(state)

        assertEquals((1 shl 1) or (1 shl 10) or (1 shl 16) or (1 shl 18), projection.completedTasksMask)
        assertEquals(3, projection.completedPathsMask)
        assertEquals(2, projection.combatTasksDone)
        assertEquals(2, projection.skillingTasksDone)
        assertEquals(2, projection.combatFocusedTask)
        assertEquals(2, projection.skillingFocusedTask)
        assertEquals(7, projection.unrepresentedSkillingTasks)
    }

    private fun assignment(slot: Int, complete: Boolean): DailyAdventureAssignment =
        DailyAdventureAssignment(
            slot = slot,
            taskId = "task-$slot",
            difficulty = DailyAdventureDifficulty.Easy,
            generatedDay = 1L,
            goal = 10L,
            reward = 5L,
            progress = if (complete) 10L else 0L,
            complete = complete,
        )
}
