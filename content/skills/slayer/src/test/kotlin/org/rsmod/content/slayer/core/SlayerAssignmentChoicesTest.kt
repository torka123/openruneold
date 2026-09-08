package org.rsmod.content.slayer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlayerAssignmentChoicesTest {
    @Test
    fun `structured wilderness boss and dungeon snapshot is accepted`() {
        assertTrue(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 7,
                taskMasterIds = listOf(7, 7, 7),
                taskIds = listOf(98, 49, 46),
                categoryIds = listOf(59, 27, 32),
                amounts = listOf(4, 30, 30),
            )
        )
    }

    @Test
    fun `forged master choice is rejected`() {
        assertFalse(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 7,
                taskMasterIds = listOf(7, 8, 7),
                taskIds = listOf(98, 46, 113),
                categoryIds = listOf(59, 32, 46),
                amounts = listOf(4, 30, 15),
            )
        )
    }

    @Test
    fun `disabled masters are rejected`() {
        assertFalse(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 10,
                taskMasterIds = listOf(10, 10, 10),
                taskIds = listOf(98, 46, 113),
                categoryIds = listOf(59, 32, 46),
                amounts = listOf(4, 30, 15),
            )
        )
    }

    @Test
    fun `wrong slot category is rejected`() {
        assertFalse(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 7,
                taskMasterIds = listOf(7, 7, 7),
                taskIds = listOf(46, 98, 113),
                categoryIds = listOf(32, 59, 46),
                amounts = listOf(30, 4, 15),
            )
        )
    }

    @Test
    fun `duplicate or incomplete categories are rejected`() {
        assertFalse(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 7,
                taskMasterIds = listOf(7, 7, 7),
                taskIds = listOf(98, 98, 113),
                categoryIds = listOf(59, 59, 46),
                amounts = listOf(4, 4, 15),
            )
        )
        assertFalse(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 7,
                taskMasterIds = listOf(7, 7),
                taskIds = listOf(98, 46),
                categoryIds = listOf(59, 32),
                amounts = listOf(4, 30),
            )
        )
    }

    @Test
    fun `mismatched task rows and category amounts are rejected`() {
        assertFalse(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 7,
                taskMasterIds = listOf(7, 7, 7),
                taskIds = listOf(46, 98, 113),
                categoryIds = listOf(59, 32, 46),
                amounts = listOf(4, 30, 15),
            )
        )
        assertFalse(
            SlayerAssignmentChoices.isSnapshotStructurallyValid(
                pendingMasterId = 7,
                taskMasterIds = listOf(7, 7, 7),
                taskIds = listOf(98, 46, 113),
                categoryIds = listOf(59, 32, 46),
                amounts = listOf(6, 30, 15),
            )
        )
    }

    @Test
    fun `selection preserves boss edgeville and cave slot order`() {
        val cave = CanonicalSlayerRegistry.get(32)!!
        val edge = CanonicalSlayerRegistry.get(27)!!
        val boss = CanonicalSlayerRegistry.get(59)!!
        val selected = SlayerAssignmentChoices.selectStructuredCategories(listOf(cave, edge, boss))
        assertEquals(listOf(boss, edge, cave), selected)
    }

    @Test
    fun `selection fails closed when a required slot has no candidate`() {
        val edge = CanonicalSlayerRegistry.get(32)!!
        val cave = CanonicalSlayerRegistry.get(46)!!
        assertEquals(null, SlayerAssignmentChoices.selectStructuredCategories(listOf(edge, cave)))
    }
}
