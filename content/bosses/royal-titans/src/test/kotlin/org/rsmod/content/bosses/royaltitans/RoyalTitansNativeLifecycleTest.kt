package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.map.CoordGrid

class RoyalTitansNativeLifecycleTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `native member leave immediately retires only that player's combat scope`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.manager.join(partner, owner.displayName))
        val session = assertNotNull(f.instances.sessionForPlayer(owner))
        val departingScope = assertNotNull(partner.combatHitScope)
        f.instances.leave(partner, session, f.clock.cycle)
        assertFalse(f.manager.isActive(partner))
        assertNull(partner.combatHitScope)
        assertNotSame(departingScope, owner.combatHitScope)
        assertTrue(f.manager.isActive(owner))
        assertEquals(setOf(owner.uuid), session.occupants)
        assertTrue(f.npcList.any())
    }

    @Test
    fun `native death cleanup cannot retain an orphaned duo member`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.manager.join(partner, owner.displayName))
        partner.statMap.setCurrentLevel("stat.hitpoints", 0)
        f.instances.handleDeath(partner, f.clock.cycle)
        assertFalse(f.manager.isActive(partner))
        assertNull(partner.combatHitScope)
        assertTrue(f.manager.isActive(owner))
        assertEquals(1, f.instances.sessionForPlayer(owner)?.occupants?.size)
    }

    @Test
    fun `native logout retains region only for the shared deferred cleanup cycle`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        f.instances.handleLogout(player, f.clock.cycle)
        assertFalse(f.manager.isActive(player))
        assertNull(player.combatHitScope)
        assertEquals(ROYAL_TITANS_OUTSIDE, player.coords)
        assertSame(session, f.instances.sessionForId(session.id))
        f.instances.tickReclaim(f.clock.cycle + 1)
        assertNull(f.instances.sessionForId(session.id))
        assertTrue(f.instances.npcsForInstance(session.id).isEmpty())
        assertTrue(f.npcList.none())
    }

    @Test
    fun `native external teleport cancels membership without moving the destination`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        val destination = CoordGrid(3087, 3496)
        player.coords = destination
        assertTrue(f.instances.leaveForExternalTeleport(player, destination, f.clock.cycle))
        assertFalse(f.manager.isActive(player))
        assertNull(player.combatHitScope)
        assertEquals(destination, player.coords)
        assertNull(f.instances.sessionForId(session.id))
        assertTrue(f.npcList.none())
    }

    @Test
    fun `native termination retires one room without affecting its neighbour`() {
        val f = RoyalTitansTestWorld()
        val first = f.enter()
        val second = f.newPlayer(2)
        assertTrue(f.manager.enter(second))
        val firstSession = assertNotNull(f.instances.sessionForPlayer(first))
        val secondSession = assertNotNull(f.instances.sessionForPlayer(second))
        val survivor = f.npc(second, 12596)
        assertTrue(f.instances.terminate(firstSession))
        assertFalse(f.instances.terminate(firstSession))
        assertFalse(f.manager.isActive(first))
        assertNull(first.combatHitScope)
        assertTrue(f.manager.isActive(second))
        assertSame(secondSession, f.instances.sessionForPlayer(second))
        assertTrue(survivor.isSlotAssigned)
        assertTrue(f.instances.npcsForInstance(firstSession.id).isEmpty())
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
