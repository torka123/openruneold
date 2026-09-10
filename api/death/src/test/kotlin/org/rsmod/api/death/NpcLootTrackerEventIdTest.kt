package org.rsmod.api.death

import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.PlayerList

class NpcLootTrackerEventIdTest {
    @Test
    fun `deferred callers share the existing death allocator without reusing an event`() {
        val death = allocator()
        assertEquals(listOf(1, 2, 3, 4), List(4) { death.nextLootTrackerEventId() })
    }

    @Test
    fun `existing overflow convention remains positive and skips zero`() {
        val death = allocator()
        val field = NpcDeath::class.java.getDeclaredField("lootTrackerEventId")
        field.isAccessible = true
        field.setInt(death, Int.MAX_VALUE - 1)
        assertEquals(Int.MAX_VALUE, death.nextLootTrackerEventId())
        assertEquals(1, death.nextLootTrackerEventId())
        assertEquals(2, death.nextLootTrackerEventId())
    }

    private fun allocator() =
        NpcDeath(
            mock(NpcRepository::class.java),
            PlayerList(),
            mock(ObjRepository::class.java),
            emptySet(),
            emptySet(),
        )
}
