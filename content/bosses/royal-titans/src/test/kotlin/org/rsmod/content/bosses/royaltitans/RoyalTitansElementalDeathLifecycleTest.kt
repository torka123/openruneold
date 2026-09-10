package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework

class RoyalTitansElementalDeathLifecycleTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `each elemental retains its full custom death animation before removal`() {
        for ((npcId, animation) in listOf(14150 to 11957, 14151 to 11951)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            val npc = f.summon(player).first { it.id == npcId }
            f.tick(3) // Finish the cache-priority spawn presentation before the death event.
            npc.hitpoints = 0
            f.manager.finishElementalDeath(npc)
            val duration = assertNotNull(ServerCacheManager.getAnim(animation)).tickDuration.coerceAtLeast(1)
            assertEquals(animation, npc.pendingSequence.id)
            f.tick(duration - 1)
            assertTrue(npc.isSlotAssigned)
            f.tick()
            assertFalse(npc.isSlotAssigned)
        }
    }

    @Test
    fun `native early corpse deletion must physically unlink its owned removal deadline`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val npc = f.summon(player).first { it.id == 14150 }
        f.tick(3)
        val encounter = f.bossDeps.encounterRegistry.of(npc)
        val before = f.bossDeps.worldQueues.size
        npc.hitpoints = 0
        f.manager.finishElementalDeath(npc)
        assertEquals(before + 1, f.bossDeps.worldQueues.size)
        f.npcs.del(npc, Int.MAX_VALUE)
        assertTrue(encounter.isEnded)
        assertEquals(before, f.bossDeps.worldQueues.size)
        f.tick(8)
        assertNull(f.bossDeps.encounterRegistry.existing(npc))
        assertFalse(npc.isSlotAssigned)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
