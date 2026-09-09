package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Executes production room startup through native world queues; not a complete spawn parity test.
 */
@OptIn(InternalApi::class)
class NexStartupTest {
    @Test
    fun `startup creates only the Ancient Prison actors and never a Wilderness encounter`() {
        val created = mutableListOf<Npc>()
        val repository =
            mock(NpcRepository::class.java) { invocation ->
                if (invocation.method.name == "add" && invocation.arguments.firstOrNull() is Npc) {
                    created += invocation.arguments[0] as Npc
                    null
                } else RETURNS_DEFAULTS.answer(invocation)
            }
        val clock = MapClock()
        val queues = WorldQueueList()
        val manager =
            NexEncounterManager(
                clock,
                repository,
                PlayerList(),
                mock(LocRepository::class.java),
                mock(AiPlayerInteractions::class.java),
                CollisionFlagMap(),
                DefaultGameRandom(42L),
                mock(NvPCombat::class.java),
                queues,
                mock(WorldRepository::class.java),
                mock(NexRewardService::class.java),
            )
        manager.startup()
        assertTrue(created.isEmpty())
        WorldQueueListProcess(queues).process()
        assertEquals(listOf(11278, 11283, 11284, 11285, 11286), created.map { it.type.id })
        assertTrue(created.all { NEX_CHAMBER_ARENA.contains(it.coords) })
        assertTrue(created.all(manager::isEncounterNpc))

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
