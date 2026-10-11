package org.rsmod.content.bosses.cerberus

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.player.vars.VarPlayerIntMapSetter

/** Real phase clock, cancellable queues and native instance departure, not source assertions. */
@OptIn(org.rsmod.annotations.InternalApi::class)
class CerberusEncounterScopeTest {
    @AfterEach fun releaseFixtureMocks() = framework().clearInlineMocks()

    @Test
    fun `standing and reset deadlines are cancelled immediately on departure`() {
        for (reset in listOf(false, true)) {
            val world = CerberusTestWorld()
            val player = world.enter()
            if (reset) {
                world.wake(player)
                player.coords = world.tile(player, 24, 24)
            } else {
                player.coords = world.tile(player, 43, 55)
            }
            world.tick()
            val boss = world.boss(player)
            val old = assertNotNull(world.deps.encounterRegistry.existing(boss))
            assertEquals(if (reset) "resetting" else "standing", old.currentPhaseName)
            assertEquals(1, world.deps.worldQueues.size)
            assertTrue(world.leave(player))
            assertTrue(old.isEnded)
            assertEquals(0, world.deps.worldQueues.size)
            assertTrue(world.start(player))
            val fresh = world.boss(player)
            world.tick(40)
            assertFalse(boss.isSlotAssigned)
            assertEquals(CerberusAssets.SITTING_NPC_ID, fresh.visType.id)
            assertEquals(0, world.deps.worldQueues.size)
        }
    }

    @Test
    fun `ending one room removes only its pending Soul callbacks`() {
        val world = CerberusTestWorld()
        val first = world.enter(1)
        val second = world.enter(2)
        world.wake(first)
        world.wake(second)
        world.force(first, "souls")
        val firstTasks = world.deps.worldQueues.size
        assertEquals(6, firstTasks)
        val ghosts = world.souls(first)
        world.force(second, "souls")
        assertEquals(firstTasks * 2, world.deps.worldQueues.size)
        assertTrue(world.leave(first))
        assertEquals(firstTasks, world.deps.worldQueues.size)
        assertTrue(ghosts.none { it.isSlotAssigned })
        world.tick(12)
        assertEquals(1, world.hits(second).size)
        assertTrue(world.hits(first).isEmpty())
        assertTrue(world.leave(second))
        assertEquals(0, world.deps.worldQueues.size)
        world.tick(40)
        assertEquals(0, world.deps.worldQueues.size)
    }

    @Test
    fun `boss death and reset cancel all Soul work rather than retain stale closures`() {
        for (die in listOf(false, true)) {
            val world = CerberusTestWorld()
            val player = world.enter()
            world.wake(player)
            world.force(player, "souls")
            val boss = world.boss(player)
            val old = assertNotNull(world.deps.encounterRegistry.existing(boss))
            val ghosts = world.souls(player)
            assertEquals(6, world.deps.worldQueues.size)
            if (die) {
                assertTrue(world.manager.beginBossDeath(boss))
            } else {
                player.coords = world.tile(player, 24, 24)
                world.tick()
            }
            assertTrue(old.isEnded)
            assertTrue(ghosts.none { it.isSlotAssigned })
            // A reset has only its new phase deadline. Death has none.
            assertEquals(if (die) 0 else 1, world.deps.worldQueues.size)
            world.updates.clear()
            world.tick(40)
            assertEquals(0, world.projectileCount())
            assertEquals(0, world.deps.worldQueues.size)
        }
    }

    @Test
    fun `native NPC deletion clears encounter summons and pending work`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.force(player, "souls")
        val boss = world.boss(player)
        val encounter = assertNotNull(world.deps.encounterRegistry.existing(boss))
        val ghosts = world.souls(player)
        assertEquals(ghosts.toSet(), encounter.owned().toSet())
        world.npcs.del(boss, Int.MAX_VALUE)
        assertTrue(encounter.isEnded)
        assertEquals(0, world.deps.worldQueues.size)
        assertTrue(ghosts.none { it.isSlotAssigned })
        assertTrue(world.instances.npcsForInstance(world.session(player).id).isEmpty())
        world.updates.clear()
        world.tick(40)
        assertEquals(0, world.projectileCount())
    }

    @Test
    fun `draft route timeout remains twenty four ticks before four tick sit`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        val boss = world.boss(player)
        val home = boss.coords
        val away = world.tile(player, 30, 40)
        boss.teleport(world.collision, away)
        player.coords = world.tile(player, 24, 24)
        world.tick()
        // The fixture deliberately does not process the boss route: characterize its timeout.
        world.tick(23)
        assertEquals(away, boss.coords)
        world.tick()
        assertEquals(home, boss.coords)
        assertEquals(CerberusAssets.SIT_ANIMATION, boss.pendingSequence.id)
        world.tick(3)
        assertEquals(CerberusAssets.RESETTING_NPC_ID, boss.visType.id)
        world.tick()
        assertEquals(CerberusAssets.SITTING_NPC_ID, boss.visType.id)
        assertEquals(0, world.deps.worldQueues.size)
    }

    @Test
    fun `protected Soul drain cannot land after teleporting out`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        for (prayer in listOf("magic", "missiles", "melee")) {
            VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrom$prayer", 1)
        }
        world.force(player, "souls")
        world.tick(12)
        assertEquals(1, world.projectileCount())
        assertTrue(world.hits(player).isEmpty())
        val prayer = player.statMap.getCurrentLevel("stat.prayer")
        world.end(player, "teleport")
        assertEquals(0, world.deps.worldQueues.size)
        world.tick(40)
        assertEquals(prayer, player.statMap.getCurrentLevel("stat.prayer"))
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() = CerberusTestCache.ensureLoaded()
    }
}
