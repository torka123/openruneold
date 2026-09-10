package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework

class RoyalTitansResetLifecycleTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `claimer departure preserves collapse deletion and both respawns for the partner`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, owner.displayName))
        f.tick(6)
        val branda = f.npc(owner, 12596)
        val eldric = f.npc(owner, 14147)
        f.hit(owner, branda, 600)
        f.hit(owner, eldric, 600)
        f.tick(2)
        f.manager.claim(owner, branda, ClaimChoice.PAGES)
        f.tick(20)
        assertTrue(f.escape(owner))
        f.tick()
        assertEquals(11992, branda.pendingSequence.id)
        assertEquals(11987, eldric.pendingSequence.id)
        f.tick(5)
        assertFalse(branda.isSlotAssigned || eldric.isSlotAssigned)
        f.tick(3)
        assertEquals(1, f.activeNpcs(partner).size)
        assertEquals(11966, f.npc(partner, 12596).pendingSequence.id)
        f.tick(2)
        assertEquals(2, f.activeNpcs(partner).size)
        assertEquals(11963, f.npc(partner, 14147).pendingSequence.id)
    }

    @Test
    fun `empty room cancels reinvigoration before collapse and after corpse deletion`() {
        for (departure in listOf(0, 20, 25, 26, 28)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val branda = f.npc(player, 12596)
            f.hit(player, branda, 600)
            f.hit(player, f.npc(player, 14147), 600)
            f.tick(2)
            f.manager.claim(player, branda, ClaimChoice.PAGES)
            f.tick(departure)
            assertTrue(f.escape(player))
            assertFalse(f.bossDeps.worldQueues.isNotEmpty)
            f.tick(60)
            assertTrue(f.npcList.none())
            assertTrue(f.instances.sessionsForKey(ROYAL_TITANS_INSTANCE_KEY).isEmpty())
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
