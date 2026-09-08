package org.rsmod.content.interfaces.journal.tab

import kotlin.test.Test
import kotlin.test.assertEquals

class AdventurePathProgressTest {
    @Test
    fun `fresh player has no completed tasks and focuses first task`() {
        val progress = AdventurePathProgress.calculate { 1 }
        assertEquals(0, progress.completedTasks)
        assertEquals(0, progress.completedPaths)
        assertEquals(1, progress.combatFocusedTask)
        assertEquals(1, progress.gathererFocusedTask)
    }

    @Test
    fun `level twenty combat stats complete the combat path`() {
        val progress =
            AdventurePathProgress.calculate { stat ->
                if (stat in setOf("attack", "strength", "defence", "ranged", "magic")) 20 else 1
            }
        assertEquals(15, progress.combatTasksDone)
        assertEquals(1, progress.completedPaths)
        assertEquals(0, progress.combatFocusedTask)
    }

    @Test
    fun `level five gathering stats complete the gatherer path`() {
        val progress =
            AdventurePathProgress.calculate { stat ->
                if (stat in setOf("fishing", "woodcutting", "mining")) 5 else 1
            }
        assertEquals(3, progress.gathererTasksDone)
        assertEquals(2, progress.completedPaths)
        assertEquals(0, progress.gathererFocusedTask)
    }
}
