package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Executes production room startup through native world queues; not a complete spawn parity test.
 */
@OptIn(InternalApi::class)
class NexStartupTest {
    @Test
    fun `startup creates only the Ancient Prison actors and never a Wilderness encounter`() {
        val created = mutableListOf<Npc>()
        val clock = MapClock()
        val events = EventBus()
        val players = PlayerList()
        val npcs = NpcList()
        val collision = CollisionFlagMap()
        for (x in 360..368) for (z in 647..653) collision.allocateIfAbsent(x * 8, z * 8, 0)
        val repository = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
        val regions =
            mock(RegionRepository::class.java) { call ->
                error("Nex startup requested allocated regions: ${call.method.name}")
            }
        val instances =
            InstanceManager(
                regions,
                repository,
                players,
                events,
                InstanceAreaResolver(),
                clock,
                collision,
                npcs,
            )
        installNexNativeInstanceScripts(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            instances,
            clock,
        )
        events.subscribeUnbound(NpcStateEvents.Create::class.java) { created += npc }
        val queues = WorldQueueList()
        val encounters =
            EncounterRegistry(DefaultGameRandom(42L)).apply {
                val spec = nexBossSpec()
                for (id in 11278..11282) register(id, spec, "nex")
            }
        val manager =
            NexEncounterManager(
                clock,
                npcs,
                players,
                mock(LocRepository::class.java),
                mock(AiPlayerInteractions::class.java),
                collision,
                DefaultGameRandom(42L),
                encounters,
                queues,
                mock(WorldRepository::class.java),
                mock(NexRewardService::class.java),
                instances,
            )
        manager.startup()
        assertTrue(created.isEmpty())
        WorldQueueListProcess(queues).process()
        assertEquals(listOf(11278, 11283, 11284, 11285, 11286), created.map { it.type.id })
        assertTrue(created.all { NEX_CHAMBER_ARENA.contains(it.coords) })
        assertTrue(created.all(manager::isEncounterNpc))
        assertEquals(5, npcs.count())
        assertEquals(5, created.map { it.uid }.toSet().size)
        created.forEach { assertSame(it, it.uid.resolve(npcs)) }
        val session = assertNotNull(instances.firstSession())
        assertTrue(session.regionIds.isEmpty())
        assertEquals(created, instances.npcsForInstance(session.id))
        created.forEach { assertEquals(session.id, instances.instanceForNpc(it)) }
        verifyNoInteractions(regions)

        val visitor =
            Player().apply {
                uuid = 900L
                observerUUID = 900L
                slotId = 1
                assignUid()
                coords = CoordGrid(2976, 3952)
                statMap.setCurrentLevel("stat.hitpoints", 99)
            }
        manager.tick(visitor)
        assertFalse(manager.isParticipant(visitor))
        WorldQueueListProcess(queues).process()
        assertEquals(5, created.size)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
