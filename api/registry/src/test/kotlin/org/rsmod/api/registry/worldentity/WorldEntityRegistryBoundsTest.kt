package org.rsmod.api.registry.worldentity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.events.EventBus
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntity.Companion.INVALID_SLOT
import org.rsmod.game.entity.WorldEntityList

class WorldEntityRegistryBoundsTest {
    @Test
    fun `invalid fine z is rejected without registry mutation`() {
        val list = WorldEntityList()
        val events = EventBus()
        val registry = WorldEntityRegistry(list, events)
        val entity =
            WorldEntity(
                id = 1,
                sizeX = 1,
                sizeZ = 1,
                southWestZoneX = 0,
                southWestZoneZ = 0,
                fineX = 0,
                fineZ = 2_097_152,
            )

        assertEquals(
            WorldEntityRegistryResult.Add.InvalidEntity("fineZ=2097152"),
            registry.add(entity),
        )
        assertEquals(INVALID_SLOT, entity.slotId)
        assertEquals(0, registry.count())
    }

    @Test
    fun `all packed world entity bounds accept their inclusive limits`() {
        val entity =
            WorldEntity(
                id = 65_535,
                sizeX = 12,
                sizeZ = 12,
                southWestZoneX = 2_047,
                southWestZoneZ = 2_047,
                fineX = 2_097_151,
                fineZ = 2_097_151,
                minLevel = 0,
                maxLevel = 3,
                activeLevel = 3,
                projectedLevel = 3,
                angle = 2_047,
            )

        assertEquals(null, WorldEntityWireBounds.invalidReason(entity))
        assertTrue(WorldEntityWireBounds.isValidClientSlot(1))
        assertTrue(WorldEntityWireBounds.isValidClientSlot(4_095))
    }
}
