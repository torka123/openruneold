package org.rsmod.content.bosses.cerberus

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework

/** Exercises the real keyed instance hooks, protected movement and native NPC death/reveal. */
class CerberusNativeLifecycleTest {
    @AfterEach fun releaseFixtureMocks() = framework().clearInlineMocks()

    @Test
    fun `logout panel reports failed departure when native teleport validation refuses`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        val run = world.session(player)
        world.denyTeleport = true
        val refused =
            org.rsmod.api.instances.events.InstanceLeaveRequestEvent(
                player,
                CERBERUS_INSTANCE_KEY,
                run.id,
            )
        world.events.publish(refused)
        assertEquals(org.rsmod.api.instances.RaidLeaveResult.Failed, refused.result)
        assertSame(run, world.session(player))
        world.denyTeleport = false
        val accepted =
            org.rsmod.api.instances.events.InstanceLeaveRequestEvent(
                player,
                CERBERUS_INSTANCE_KEY,
                run.id,
            )
        world.events.publish(accepted)
        assertEquals(org.rsmod.api.instances.RaidLeaveResult.Left, accepted.result)
        assertNull(world.instances.sessionForPlayer(player))
    }

    @Test
    fun `denied protected entry cancels allocation without orphaning a player`() {
        val world = CerberusTestWorld()
        val player = world.player()
        world.denyTeleport = true
        assertFalse(world.start(player))
        assertEquals(CERBERUS_PUBLIC_EXIT, player.coords)
        assertTrue(world.instances.sessionsForKey(CERBERUS_INSTANCE_KEY).isEmpty())
        world.denyTeleport = false
        assertTrue(world.start(player))
        assertEquals(1, world.instances.npcsForInstance(world.session(player).id).size)
    }

    @Test
    fun `denied protected exit retains membership actor and delayed attacks`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.attack(player)
        val run = world.session(player)
        val boss = world.boss(player)
        val launched = world.hits(player).single()
        val tile = player.coords
        world.denyTeleport = true
        assertFalse(world.leave(player))
        assertSame(run, world.session(player))
        assertEquals(tile, player.coords)
        assertTrue(boss.isSlotAssigned)
        assertTrue(launched.isValidNow())
        world.tick(2)
        assertEquals(2, world.projectileCount())
        world.denyTeleport = false
        assertTrue(world.leave(player))
        assertEquals(CERBERUS_PUBLIC_EXIT, player.coords)
        assertFalse(launched.isValidNow())
        assertFalse(boss.isSlotAssigned)
    }

    @Test
    fun `native departures immediately invalidate hits and reclaim only the departing room`() {
        for (reason in listOf("teleport", "logout", "disconnect", "death")) {
            val world = CerberusTestWorld()
            val player = world.enter(1)
            val other = world.enter(2)
            world.wake(player)
            world.wake(other)
            world.force(player, "souls")
            world.tick(12)
            val run = world.session(player)
            val boss = world.boss(player)
            val ghosts = world.souls(player)
            val launched = world.hits(player)
            assertTrue(launched.isNotEmpty())
            val otherRun = world.session(other)
            val otherBoss = world.boss(other)
            world.end(player, reason)
            assertNull(world.instances.sessionForPlayer(player), reason)
            assertTrue(launched.none { it.isValidNow() }, reason)
            assertTrue(ghosts.none { it.isSlotAssigned }, reason)
            world.updates.clear()
            world.tick(30)
            assertNull(world.instances.sessionForId(run.id), reason)
            assertFalse(boss.isSlotAssigned, reason)
            assertEquals(0, world.projectileCount(), reason)
            assertSame(otherRun, world.session(other))
            assertSame(otherBoss, world.boss(other))
            assertTrue(world.hits(other).isEmpty())
        }
    }

    @Test
    fun `native death retains one actor and reveals it fifteen ticks after despawn twice`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        val boss = world.boss(player)
        val identity = boss.uid
        repeat(2) { round ->
            world.wake(player)
            boss.recordDamage(player, 600)
            world.kill(player)
            var waited = 0
            while (boss.isVisible && waited++ < 20) world.tick()
            assertFalse(boss.isVisible)
            assertTrue(world.status(player).contains("phase=RESPAWNING "), world.status(player))
            assertEquals(round + 1, world.kills.size)
            assertSame(boss, world.kills.last().npc)
            world.tick(CerberusRules.RESPAWN_TICKS - 1)
            assertFalse(boss.isVisible)
            world.tick()
            assertTrue(boss.isVisible)
            assertEquals(identity, boss.uid)
            assertEquals(600, boss.hitpoints)
            assertEquals(CerberusAssets.SITTING_NPC_ID, boss.visType.id)
            assertEquals(listOf(boss), world.instances.npcsForInstance(world.session(player).id))
            assertTrue(world.status(player).contains("counter=1 "), world.status(player))
        }
        assertTrue(world.leave(player))
        assertFalse(boss.isSlotAssigned)
        assertTrue(world.start(player))
        assertNotSame(boss, world.boss(player))
    }

    @Test
    fun `leave during native death animation cannot reveal a stale actor`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        val boss = world.boss(player)
        world.kill(player)
        world.tick()
        assertTrue(world.leave(player))
        assertTrue(world.start(player))
        val fresh = world.boss(player)
        world.tick(40)
        assertFalse(boss.isSlotAssigned)
        assertEquals(listOf(fresh), world.instances.npcsForInstance(world.session(player).id))
        assertTrue(world.kills.isEmpty())
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() = CerberusTestCache.ensureLoaded()
    }
}
