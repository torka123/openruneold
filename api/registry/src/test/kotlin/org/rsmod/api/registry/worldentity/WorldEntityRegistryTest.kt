package org.rsmod.api.registry.worldentity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.rsmod.events.EventBus
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntity.Companion.INVALID_SLOT
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.worldentity.NoopWorldEntityInfo
import org.rsmod.game.entity.worldentity.WorldEntityInfoProtocol
import org.rsmod.game.entity.worldentity.WorldEntityStateEvents

class WorldEntityRegistryTest {
    @Test
    fun `add claims first client slot and publishes one create event`() {
        val list = WorldEntityList()
        val eventBus = EventBus()
        val created = mutableListOf<WorldEntity>()
        eventBus.subscribeUnbound(WorldEntityStateEvents.Create::class.java) { created += entity }
        val registry = WorldEntityRegistry(list, eventBus)
        val entity = createEntity()

        assertEquals(WorldEntityRegistryResult.Add.Success, registry.add(entity))

        assertEquals(WorldEntityList.SLOT_PADDING, entity.slotId)
        assertSame(entity, list[WorldEntityList.SLOT_PADDING])
        assertEquals(1, registry.count())
        assertEquals(listOf(entity), created)
    }

    @Test
    fun `add fails without mutation or event when registry is full`() {
        val list = WorldEntityList()
        for (slot in WorldEntityList.SLOT_PADDING until WorldEntityList.CAPACITY) {
            list[slot] = createEntity(slot)
        }
        val eventBus = EventBus()
        val created = mutableListOf<WorldEntity>()
        eventBus.subscribeUnbound(WorldEntityStateEvents.Create::class.java) { created += entity }
        val registry = WorldEntityRegistry(list, eventBus)
        val rejected = createEntity()

        assertEquals(WorldEntityRegistryResult.Add.NoAvailableSlot, registry.add(rejected))

        assertEquals(INVALID_SLOT, rejected.slotId)
        assertEquals(WorldEntityList.CAPACITY - WorldEntityList.SLOT_PADDING, registry.count())
        assertTrue(created.isEmpty())
    }

    @Test
    fun `delete clears slot resets protocol and publishes one delete event`() {
        val list = WorldEntityList()
        val eventBus = EventBus()
        val deleted = mutableListOf<WorldEntity>()
        eventBus.subscribeUnbound(WorldEntityStateEvents.Delete::class.java) { deleted += entity }
        val registry = WorldEntityRegistry(list, eventBus)
        val entity = createEntity()
        registry.add(entity)
        entity.infoProtocol = TestInfoProtocol
        val slot = entity.slotId

        assertEquals(WorldEntityRegistryResult.Delete.Success, registry.del(entity))

        assertEquals(null, list[slot])
        assertEquals(INVALID_SLOT, entity.slotId)
        assertSame(NoopWorldEntityInfo, entity.infoProtocol)
        assertEquals(0, registry.count())
        assertEquals(listOf(entity), deleted)
    }

    @Test
    fun `repeated delete is rejected without publishing another event`() {
        val list = WorldEntityList()
        val eventBus = EventBus()
        val deleted = mutableListOf<WorldEntity>()
        eventBus.subscribeUnbound(WorldEntityStateEvents.Delete::class.java) { deleted += entity }
        val registry = WorldEntityRegistry(list, eventBus)
        val entity = createEntity()
        registry.add(entity)

        assertEquals(WorldEntityRegistryResult.Delete.Success, registry.del(entity))
        assertEquals(WorldEntityRegistryResult.Delete.UnexpectedSlot, registry.del(entity))

        assertEquals(0, registry.count())
        assertEquals(listOf(entity), deleted)
    }

    @Test
    fun `delete reports slot mismatch without mutating either entity`() {
        val list = WorldEntityList()
        val eventBus = EventBus()
        val deleted = mutableListOf<WorldEntity>()
        eventBus.subscribeUnbound(WorldEntityStateEvents.Delete::class.java) { deleted += entity }
        val registry = WorldEntityRegistry(list, eventBus)
        val entity = createEntity(1)
        registry.add(entity)
        entity.infoProtocol = TestInfoProtocol
        val slot = entity.slotId
        val occupant = createEntity(2)
        list[slot] = occupant

        val result = assertIs<WorldEntityRegistryResult.Delete.ListSlotMismatch>(registry.del(entity))

        assertSame(occupant, result.occupiedBy)
        assertSame(occupant, list[slot])
        assertEquals(slot, entity.slotId)
        assertSame(TestInfoProtocol, entity.infoProtocol)
        assertTrue(deleted.isEmpty())
    }

    private fun createEntity(id: Int = 1): WorldEntity =
        WorldEntity(
            id = id,
            sizeX = 1,
            sizeZ = 1,
            southWestZoneX = 0,
            southWestZoneZ = 0,
            fineX = 0,
            fineZ = 0,
        )

    private data object TestInfoProtocol : WorldEntityInfoProtocol {
        override fun updateCoord(level: Int, fineX: Int, fineZ: Int, teleport: Boolean) {}

        override fun updateAngle(angle: Int) {}

        override fun setSequence(seq: Int, delay: Int) {}

        override fun setVisibleOps(ops: Byte) {}

        override fun setSpecific(specific: Boolean) {}
    }
}
