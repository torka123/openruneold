package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework

/** Draft characterization, not proof of the unresolved OSRS two/three-action selection rule. */
class RoyalTitansArenaCadenceTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `both Titan programs retain every ordinary and Blast animation over three rotations`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, 12596)
        val eldric = f.npc(player, 14147)
        f.hit(player, branda, 406)
        f.tick()
        val transition = f.clock.cycle
        val observed = mutableMapOf<Int, Pair<Int, Int>>()
        repeat(109) {
            f.tick()
            if (branda.pendingSequence.id in setOf(11981, 11982, 11978, 11974))
                observed[f.clock.cycle - transition] =
                    branda.pendingSequence.id to eldric.pendingSequence.id
        }
        assertEquals(
            mapOf(
                15 to (11981 to 11979),
                21 to (11982 to 11980),
                29 to (11978 to 11977),
                36 to (11974 to 11973),
                43 to (11974 to 11973),
                51 to (11981 to 11979),
                57 to (11982 to 11980),
                65 to (11978 to 11977),
                72 to (11974 to 11973),
                80 to (11981 to 11979),
                86 to (11982 to 11980),
                94 to (11978 to 11977),
                101 to (11974 to 11973),
                109 to (11981 to 11979),
            ),
            observed,
        )
    }

    @Test
    fun `duo owner departure must not shift the partners next charge or summon`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, owner.displayName))
        f.tick(6)
        val branda = f.npc(owner, 12596)
        f.hit(owner, branda, 406)
        f.tick(16)
        val charge = f.clock.cycle
        f.tick(2)
        assertTrue(f.escape(owner))
        f.tick(12)
        assertEquals(charge + 14, f.clock.cycle)
        assertEquals(11978, branda.pendingSequence.id)
        f.tick(22)
        assertEquals(charge + 36, f.clock.cycle)
        assertEquals(11981, branda.pendingSequence.id)
        assertTrue(f.manager.isActive(partner))
    }

    @Test
    fun `owner leaving immediately before a due summon retains that exact frame`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, owner.displayName))
        f.tick(6)
        val branda = f.npc(owner, 12596)
        f.hit(owner, branda, 406)
        f.tick(16)
        val charge = f.clock.cycle
        f.tick(13)
        assertTrue(f.escape(owner))
        f.tick()
        assertEquals(charge + 14, f.clock.cycle)
        assertEquals(11978, branda.pendingSequence.id)
    }

    @Test
    fun `both native phase deadlines are retired when an arena is destroyed`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, 12596)
        val eldric = f.npc(player, 14147)
        f.hit(player, branda, 406)
        f.tick(16)
        val first = assertNotNull(f.bossDeps.encounterRegistry.existing(branda))
        val second = assertNotNull(f.bossDeps.encounterRegistry.existing(eldric))
        assertNotSame(first, second)
        assertTrue(f.bossDeps.worldQueues.isNotEmpty)
        assertTrue(f.instances.terminate(assertNotNull(f.instances.sessionForPlayer(player))))
        assertTrue(first.isEnded)
        assertTrue(second.isEnded)
        assertFalse(f.bossDeps.worldQueues.isNotEmpty)
        f.tick(60)
        assertTrue(f.npcList.none())
    }

    @Test
    fun `corpse transformation cannot revive the defeated Titans clock in either kill order`() {
        for (firstId in listOf(12596, 14147)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val branda = f.npc(player, 12596)
            val eldric = f.npc(player, 14147)
            f.hit(player, branda, 406)
            f.tick(16)
            val charge = f.clock.cycle
            val defeated = if (firstId == 12596) branda else eldric
            val survivor = if (firstId == 12596) eldric else branda
            val deadClock = f.bossDeps.encounterRegistry.of(defeated)
            val survivorClock = f.bossDeps.encounterRegistry.of(survivor)
            f.hit(player, defeated, 600)
            f.tick(2)
            assertEquals("inactive", deadClock.currentPhaseName)
            assertEquals(500, defeated.hitpoints)
            f.tick(34)
            assertEquals(charge + 36, f.clock.cycle)
            assertEquals("charge", survivorClock.currentPhaseName)
            assertEquals(if (firstId == 12596) 11979 else 11981, survivor.pendingSequence.id)
            assertEquals("inactive", deadClock.currentPhaseName)
            f.hit(player, survivor, 600)
            f.tick(2)
            assertEquals("inactive", survivorClock.currentPhaseName)
            assertFalse(f.bossDeps.worldQueues.isNotEmpty)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
