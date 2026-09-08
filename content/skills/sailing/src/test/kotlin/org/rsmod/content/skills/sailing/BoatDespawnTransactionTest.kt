package org.rsmod.content.skills.sailing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.rsmod.api.registry.worldentity.WorldEntityRegistryResult
import org.rsmod.game.entity.WorldEntity

class BoatDespawnTransactionTest {
    @Test
    fun `successful registry deletion runs cleanup exactly once`() {
        var cleanups = 0
        val result = WorldEntityRegistryResult.Delete.Success

        val returned = completeBoatDespawn(result) { cleanups++ }

        assertSame(result, returned)
        assertEquals(1, cleanups)
    }

    @Test
    fun `unexpected slot leaves local boat state untouched`() {
        var cleanups = 0
        val result = WorldEntityRegistryResult.Delete.UnexpectedSlot

        val returned = completeBoatDespawn(result) { cleanups++ }

        assertSame(result, returned)
        assertEquals(0, cleanups)
    }

    @Test
    fun `slot mismatch leaves local boat state untouched`() {
        var cleanups = 0
        val occupant = createEntity()
        val result = WorldEntityRegistryResult.Delete.ListSlotMismatch(occupant)

        val returned = completeBoatDespawn(result) { cleanups++ }

        assertSame(result, returned)
        assertEquals(0, cleanups)
    }

    private fun createEntity(): WorldEntity =
        WorldEntity(
            id = 1,
            sizeX = 1,
            sizeZ = 1,
            southWestZoneX = 0,
            southWestZoneZ = 0,
            fineX = 0,
            fineZ = 0,
        )
}
