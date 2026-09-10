package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework

class RoyalTitansTransitionLifecycleTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `duo departure before each teleport boundary preserves exact presentation frames`() {
        for (departure in listOf(2, 3, 6)) {
            val f = RoyalTitansTestWorld()
            val owner = f.enter()
            val partner = f.newPlayer(2)
            assertTrue(f.join(partner, owner.displayName))
            f.tick(6)
            val branda = f.npc(owner, 12596)
            val eldric = f.npc(owner, 14147)
            val fireLanding = f.tile(owner, 30, 35)
            val iceLanding = f.tile(owner, 30, 25)
            f.hit(owner, branda, 406)
            f.tick()
            val start = f.clock.cycle
            val actions = mutableMapOf<Int, Pair<Int, Int>>()
            repeat(15) { offset ->
                if (offset == departure) assertTrue(f.escape(owner))
                f.tick()
                if (f.clock.cycle - start in setOf(3, 4, 7, 15)) {
                    actions[f.clock.cycle - start] = branda.pendingSequence.id to eldric.pendingSequence.id
                }
                if (offset + 1 == 3) assertEquals(fireLanding, branda.coords)
                if (offset + 1 == 7) assertEquals(iceLanding, eldric.coords)
            }
            assertEquals(11966, actions.getValue(3).first)
            assertEquals(11983, actions.getValue(4).second)
            assertEquals(11963, actions.getValue(7).second)
            assertEquals(11981 to 11979, actions.getValue(15))
            assertTrue(f.manager.isActive(partner))
        }
    }

    @Test
    fun `empty native room during teleport cannot create landing floors or resurrect actors`() {
        for (departure in listOf(0, 3, 4, 6)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val branda = f.npc(player, 12596)
            val eldric = f.npc(player, 14147)
            f.hit(player, branda, 406)
            f.tick(1 + departure)
            val first = f.bossDeps.encounterRegistry.of(branda)
            val second = f.bossDeps.encounterRegistry.of(eldric)
            assertTrue(f.escape(player))
            assertTrue(first.isEnded && second.isEnded)
            assertFalse(f.bossDeps.worldQueues.isNotEmpty)
            f.tick(40)
            assertTrue(f.npcList.none())
            assertTrue(f.instances.sessionsForKey(ROYAL_TITANS_INSTANCE_KEY).isEmpty())
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
