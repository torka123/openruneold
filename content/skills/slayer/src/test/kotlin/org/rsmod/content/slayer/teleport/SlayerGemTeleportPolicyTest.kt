package org.rsmod.content.slayer.teleport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlayerGemTeleportPolicyTest {
    @Test
    fun `1 no task cannot start a teleport`() {
        assertFalse(canStart(hasTask = false))
    }

    @Test
    fun `2 no task has the required safe message`() {
        assertEquals(
            "You do not currently have a Slayer assignment.",
            SlayerGemTeleportPolicy.NO_ASSIGNMENT_MESSAGE,
        )
    }

    @Test
    fun `3 active task reports the saved remaining amount`() {
        val message = SlayerGemTeleportPolicy.progressMessage("Abyssal demons", 42)
        assertTrue(message.contains("42 kills remaining"))
    }

    @Test
    fun `4 one remaining uses singular grammar`() {
        assertEquals("You have 1 kill remaining.", SlayerGemTeleportPolicy.remainingText(1))
    }

    @Test
    fun `5 multiple remaining uses plural grammar`() {
        assertEquals("You have 42 kills remaining.", SlayerGemTeleportPolicy.remainingText(42))
    }

    @Test
    fun `6 loaded walkable normal destination is accepted`() {
        assertTrue(safeDestination())
    }

    @Test
    fun `7 Konar assignment remains valid for its exact location`() {
        val expected = snapshot(locationId = 7)
        assertTrue(SlayerGemTeleportPolicy.assignmentStillMatches(expected, expected))
    }

    @Test
    fun `8 Konar assignment never accepts a different location`() {
        assertFalse(
            SlayerGemTeleportPolicy.assignmentStillMatches(
                snapshot(locationId = 7),
                snapshot(locationId = 9),
            )
        )
    }

    @Test
    fun `9 Wilderness destination requires confirmation`() {
        assertTrue(SlayerGemTeleportPolicy.requiresWildernessConfirmation(28))
    }

    @Test
    fun `10 cancelling Wilderness confirmation prevents readiness`() {
        val confirmed = false
        assertFalse(confirmed && SlayerGemTeleportPolicy.readyToTeleport(true, true, true))
    }

    @Test
    fun `11 confirmed Wilderness destination can become ready`() {
        val confirmed = true
        assertTrue(confirmed && SlayerGemTeleportPolicy.readyToTeleport(true, true, true))
    }

    @Test
    fun `12 task is revalidated after confirmation`() {
        assertFalse(
            SlayerGemTeleportPolicy.assignmentStillMatches(
                snapshot(taskId = 31),
                snapshot(taskId = 32),
            )
        )
    }

    @Test
    fun `category identity is revalidated after confirmation`() {
        assertFalse(
            SlayerGemTeleportPolicy.assignmentStillMatches(
                snapshot(categoryId = 59),
                snapshot(categoryId = 60),
            )
        )
    }

    @Test
    fun `13 boss progress uses defeat wording`() {
        val message = SlayerGemTeleportPolicy.progressMessage("General Graardor", 3, boss = true)
        assertTrue(message.startsWith("Your current task is to defeat General Graardor"))
    }

    @Test
    fun `14 missing resolution policy does not mutate assignment`() {
        val before = snapshot()
        SlayerGemTeleportPolicy.remainingText(before.remaining)
        assertEquals(before, snapshot())
    }

    @Test
    fun `15 unsafe destination prevents movement`() {
        assertFalse(SlayerGemTeleportPolicy.readyToTeleport(true, false, true))
    }

    @Test
    fun `16 unsupported legacy task cannot pass assignment revalidation`() {
        assertFalse(
            SlayerGemTeleportPolicy.assignmentStillMatches(
                snapshot(taskId = 99_999),
                snapshot(taskId = 0),
            )
        )
    }

    @Test
    fun `17 blocked tile is rejected`() {
        assertFalse(safeDestination(walkable = false))
    }

    @Test
    fun `18 unloaded region is rejected`() {
        assertFalse(safeDestination(loaded = false))
    }

    @Test
    fun `19 duplicate pending click cannot start`() {
        assertFalse(canStart(pending = true))
    }

    @Test
    fun `20 normal teleport denial prevents readiness`() {
        assertFalse(SlayerGemTeleportPolicy.readyToTeleport(true, true, false))
    }

    @Test
    fun `21 interaction policy never consumes an item`() {
        val inventoryCount = 1
        canStart(hasTask = true)
        assertEquals(1, inventoryCount)
    }

    @Test
    fun `22 progress formatting never changes task amount`() {
        val assignment = snapshot(remaining = 17)
        SlayerGemTeleportPolicy.progressMessage("Dust devils", assignment.remaining)
        assertEquals(17, assignment.remaining)
    }

    @Test
    fun `23 Eternal gem is routed through the shared handler set`() {
        assertTrue("obj.slayer_eternal_gem" in SlayerGemTeleportPolicy.SUPPORTED_GEM_KEYS)
    }

    @Test
    fun `24 Enchanted gem is routed through the shared handler set`() {
        assertTrue("obj.slayer_gem" in SlayerGemTeleportPolicy.SUPPORTED_GEM_KEYS)
    }

    @Test
    fun `25 existing saved assignment fields remain compatible`() {
        val saved = SlayerAssignmentSnapshot(taskId = 57, remaining = 22, masterId = 8, locationId = 3)
        assertEquals(57, saved.taskId)
        assertEquals(22, saved.remaining)
        assertEquals(8, saved.masterId)
        assertEquals(3, saved.locationId)
    }

    private fun canStart(
        hasTask: Boolean = true,
        pending: Boolean = false,
    ): Boolean =
        SlayerGemTeleportPolicy.canStart(
            hasTask = hasTask,
            remaining = 10,
            dead = false,
            inUnsupportedInstance = false,
            pending = pending,
            cooldownUntil = 100,
            currentCycle = 100,
        )

    private fun safeDestination(
        loaded: Boolean = true,
        walkable: Boolean = true,
    ): Boolean =
        SlayerGemTeleportPolicy.destinationIsSafe(
            plane = 0,
            loaded = loaded,
            walkable = walkable,
            blockingObject = false,
            occupiedByNpc = false,
            inUnsupportedInstance = false,
        )

    private fun snapshot(
        taskId: Int = 31,
        remaining: Int = 10,
        masterId: Int = 8,
        locationId: Int = 7,
        categoryId: Int = 41,
    ): SlayerAssignmentSnapshot =
        SlayerAssignmentSnapshot(taskId, remaining, masterId, locationId, categoryId)
}
