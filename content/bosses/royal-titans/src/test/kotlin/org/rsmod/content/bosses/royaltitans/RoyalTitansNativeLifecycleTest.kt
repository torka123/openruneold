package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.map.CoordGrid

class RoyalTitansNativeLifecycleTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `protected entry refusal leaves no allocated room or spawned actors`() {
        val f = RoyalTitansTestWorld()
        val player = f.newPlayer()
        val before = player.coords
        f.denyTeleport = true
        assertFalse(f.start(player))
        assertEquals(before, player.coords)
        assertNull(f.instances.sessionForPlayer(player))
        assertTrue(f.instances.sessionsForKey(ROYAL_TITANS_INSTANCE_KEY).isEmpty())
        assertTrue(f.npcList.none())
        f.denyTeleport = false
        assertTrue(f.start(player))
        assertEquals(1, f.npcList.count())
        f.tick(2)
        assertEquals(2, f.npcList.count())
    }

    @Test
    fun `refused join cannot consume the second slot or disturb the owner`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(owner))
        val partner = f.newPlayer(2)
        val before = partner.coords
        f.denyTeleport = true
        assertFalse(f.join(partner, owner.displayName))
        assertEquals(before, partner.coords)
        assertNull(partner.combatHitScope)
        assertEquals(setOf(owner.uuid), session.occupants)
        f.denyTeleport = false
        assertTrue(f.join(partner, owner.displayName))
        assertSame(session, f.instances.sessionForPlayer(partner))
    }

    @Test
    fun `logout panel exit refusal preserves membership scope and room`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        val scope = player.combatHitScope
        val before = player.coords
        f.denyTeleport = true
        val request =
            org.rsmod.api.instances.events.InstanceLeaveRequestEvent(
                player,
                ROYAL_TITANS_INSTANCE_KEY,
                session.id,
            )
        f.events.publish(request)
        assertEquals(org.rsmod.api.instances.RaidLeaveResult.Failed, request.result)
        assertSame(session, f.instances.sessionForPlayer(player))
        assertSame(scope, player.combatHitScope)
        assertEquals(before, player.coords)
        assertTrue(f.npcList.any())
        f.denyTeleport = false
        val accepted = request.copy(result = org.rsmod.api.instances.RaidLeaveResult.Unhandled)
        f.events.publish(accepted)
        assertEquals(org.rsmod.api.instances.RaidLeaveResult.Left, accepted.result)
        assertEquals(ROYAL_TITANS_OUTSIDE, player.coords)
        assertNull(f.instances.sessionForId(session.id))
    }

    @Test
    fun `disconnect during pending summons cannot retain a room or produce later helpers`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        f.hit(player, f.npc(player, 12596), 100)
        f.tick(2)
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        player.clientDisconnected.set(true)
        f.nativeLifecycle.onPostTick(player)
        assertFalse(f.manager.isActive(player))
        assertNull(player.combatHitScope)
        assertEquals(ROYAL_TITANS_OUTSIDE, player.coords)
        f.instances.tickReclaim(f.clock.cycle + 1)
        f.tick(60)
        assertNull(f.instances.sessionForId(session.id))
        assertTrue(f.npcList.none())
    }

    @Test
    fun `death respawn teleport records once and retries after the native room is gone`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        f.failDeaths = true
        player.statMap.setCurrentLevel("stat.hitpoints", 0)
        val respawn = CoordGrid(3222, 3218)
        player.coords = respawn
        assertTrue(f.instances.leaveForExternalTeleport(player, respawn, f.clock.cycle))
        assertNull(f.instances.sessionForId(session.id))
        assertEquals(1, f.deathAttempts.size)
        RoyalTitansDeathCleanup(f.manager).cleanup(player)
        f.instances.handleDeath(player, f.clock.cycle)
        assertEquals(1, f.deathAttempts.size)
        f.failDeaths = false
        f.tick(2)
        assertEquals(2, f.deathAttempts.size)
        assertEquals(f.deathAttempts.first(), f.deathAttempts.last())
        assertEquals(respawn, player.coords)
        assertFalse(f.manager.isActive(player))
    }

    @Test
    fun `a contributor in another room cannot claim a foreign corpse or consume their own claim`() {
        val f = RoyalTitansTestWorld()
        val first = f.enter()
        val second = f.newPlayer(2)
        assertTrue(f.start(second))
        f.tick(6)
        for (player in listOf(first, second)) {
            f.hit(player, f.npc(player, 12596), 600)
            f.hit(player, f.npc(player, 14147), 600)
        }
        f.tick(2)
        assertEquals(2, f.completions.size)
        f.manager.claim(second, f.npc(first, 14148), ClaimChoice.PAGES)
        assertEquals(0, f.objRegistry.count())
        f.manager.claim(second, f.npc(second, 14149), ClaimChoice.PAGES)
        assertEquals(1, f.objRegistry.count())
        f.manager.claim(first, f.npc(first, 14148), ClaimChoice.PAGES)
        assertEquals(2, f.objRegistry.count())
    }

    @Test
    fun `duplicate native join notification cannot replace combat scope or respawn Titans`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        val scope = player.combatHitScope
        val branda = f.npc(player, 12596)
        f.events.publish(
            org.rsmod.api.instances.events.InstancePlayerJoinEvent(
                player,
                ROYAL_TITANS_INSTANCE_KEY,
                session.id,
                session.owner,
            )
        )
        assertSame(scope, player.combatHitScope)
        assertEquals(listOf(branda), f.activeNpcs(player))
        f.tick(2)
        assertEquals(2, f.activeNpcs(player).size)
        assertSame(branda, f.npc(player, 12596))
    }

    @Test
    fun `native member leave immediately retires only that player's combat scope`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, owner.displayName))
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
        assertTrue(f.join(partner, owner.displayName))
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
        assertTrue(f.start(second))
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
