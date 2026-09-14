package org.rsmod.api.death

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.NpcRevealProcessor
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native death coroutine and repository respawn processor, with no boss-specific scheduler. */
@OptIn(InternalApi::class)
class NpcDeathRespawnTest {
    @Test
    fun `default death preserves cache cadence while a custom delay uses the same actor`() {
        for (delay in listOf(null, 7, 43)) {
            val f = Fixture()
            val original = f.npc
            f.die(delay)
            val finish = f.clock.cycle
            val expected = delay ?: f.npc.type.respawnRate
            assertEquals(finish + expected, f.npc.lifecycleRespawnCycle)
            repeat(expected - 1) { f.tick() }
            assertEquals(0, f.npc.hitpoints)
            f.tick()
            assertTrue(f.npc.hitpoints > 0)
            assertFalse(f.npc.hidden)
            assertSame(original, f.npcList.single())
            val respawnUid = f.npc.uid
            repeat(3) { f.tick() }
            assertEquals(respawnUid, f.npc.uid)
        }
    }

    @Test
    fun `temporary actors are deleted and never respawn even with a custom delay`() {
        val f = Fixture()
        f.npc.respawns = false
        f.die(7)
        assertFalse(f.npc.isSlotAssigned)
        repeat(10) { f.tick() }
        assertTrue(f.npcList.none())
    }

    @Test
    fun `deleting the actor during death cancels its coroutine and prevents a pending respawn`() {
        val f = Fixture()
        f.beginDeath(7)
        assertNotNull(f.npc.activeCoroutine)
        f.npcs.del(f.npc, Int.MAX_VALUE)
        repeat(30) { f.tick() }
        assertTrue(f.npcList.none())
        assertNull(f.npc.activeCoroutine)
        assertEquals(-1, f.npc.lifecycleRespawnCycle)
    }

    @Test
    fun `negative delay fails before death presentation or removal`() {
        val f = Fixture()
        assertFailsWith<IllegalArgumentException> { f.beginDeath(-1) }
        assertTrue(f.npc.isSlotAssigned)
        assertEquals(-1, f.npc.lifecycleRespawnCycle)
    }

    @AfterEach
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val players = PlayerList()
        val collision = CollisionFlagMap()
        val npcList = NpcList()
        val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
        val npc = Npc(assertNotNull(ServerCacheManager.getNpc(8615)), CoordGrid(3000, 3000))
        private val death =
            NpcDeath(npcs, players, mock(ObjRepository::class.java), emptySet(), emptySet())

        init {
            with(NpcDeathScript(death)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            npcs.add(npc, Int.MAX_VALUE)
        }

        fun beginDeath(delay: Int?) {
            npc.hitpoints = 0
            npc.currentMapClock = clock.cycle
            npc.processedMapClock = clock.cycle
            npc.launch {
                death.deathNoDrops(
                    StandardNpcAccess(
                        npc,
                        this,
                        StandardNpcAccessContext(
                            { DefaultGameRandom(1L) },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        ),
                    ),
                    deathAnimationId = 8257,
                    respawnDelay = delay,
                )
            }
        }

        fun die(delay: Int?) {
            beginDeath(delay)
            var limit = 100
            while (npc.activeCoroutine != null && limit-- > 0) tick()
            assertNull(npc.activeCoroutine, "Death coroutine failed to finish")
        }

        fun tick() {
            clock.cycle++
            if (!npc.isSlotAssigned) return
            npc.currentMapClock = clock.cycle
            npc.processedMapClock = clock.cycle
            if (npc.activeCoroutine != null) npc.advanceActiveCoroutine()
            NpcRevealProcessor(npcs).process(npc)
        }
    }
}
