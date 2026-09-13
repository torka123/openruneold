package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.game.process.GameCycle
import org.rsmod.api.game.process.controller.ControllerMainProcess
import org.rsmod.api.game.process.npc.NpcMainProcess
import org.rsmod.api.game.process.npc.NpcPreTickProcess
import org.rsmod.api.game.process.player.PlayerIdShuffleProcess
import org.rsmod.api.game.process.player.PlayerInputProcess
import org.rsmod.api.game.process.player.PlayerLoginProcess
import org.rsmod.api.game.process.player.PlayerLogoutProcess
import org.rsmod.api.game.process.player.PlayerMainProcess
import org.rsmod.api.game.process.player.PlayerPostTickProcess
import org.rsmod.api.game.process.player.PlayerRouteRequestProcess
import org.rsmod.api.game.process.world.WorldMainTickProcess
import org.rsmod.api.game.process.world.WorldPostTickProcess

/**
 * Executes production lifecycle registration, real GameCycle publication, room state, NPC registry
 * deletion and BossEncounter teardown. Unrelated GameCycle processors are mocked; this is not a
 * rendered-client timing test, complete movement simulation or OSRS respawn-duration proof.
 */
class NexPublicRoomLifecycleTest {
    @Test
    fun `native world cycle resets an abandoned room without any online player callback`() {
        val f = NexMinionTestWorld()
        val previous = f.npcs.toList()
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        f.manager.playerLoggedOut(f.player)
        f.players.remove(f.player.slotId)
        assertEquals(0, f.players.count())
        val cycle = worldCycle(f)

        cycle.tick()
        assertEquals("RESETTING", encounter.currentPhaseName)
        assertTrue(boss.isSlotAssigned)
        cycle.tick()
        assertTrue(previous.none { it.isSlotAssigned })
        assertTrue(encounter.isEnded)
        val fresh = f.npc(11278)
        assertNotSame(boss, fresh)
        assertEquals("IDLE", f.encounters.of(fresh).currentPhaseName)
        assertEquals(5, f.npcs.count())
        repeat(10) { cycle.tick() }
        assertSame(fresh, f.npc(11278), "An empty idle room must not spawn duplicates")
    }

    @Test
    fun `world cycle prunes a lost player identity without needing their logout callback`() {
        val f = NexMinionTestWorld()
        val old = f.npc(11278)
        val encounter = f.encounters.of(old)
        f.players.remove(f.player.slotId)
        val cycle = worldCycle(f)
        cycle.tick()
        cycle.tick()
        assertFalse(old.isSlotAssigned)
        assertTrue(encounter.isEnded)
        assertFalse(f.manager.isParticipant(f.player))
        assertEquals(5, f.npcs.count())
    }

    @Test
    fun `native source deletion retires the old room without mutating its defence`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        val previous = f.npcs.toList()
        f.repository.del(boss, Int.MAX_VALUE)
        boss.defenceLvl = 1
        f.tick()
        assertEquals(1, boss.defenceLvl, "The ended room must not repair a retired NPC's stats")
        assertTrue(previous.none { it.isSlotAssigned })
        assertFalse(f.manager.isParticipant(f.player))
        assertNull(f.player.lootDropDuration)
        assertEquals(5, f.npcs.count())
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
    }

    @Test
    fun `player callbacks cannot activate the room and native cycle preserves opening deadline`() {
        val f = NexMinionTestWorld(enterRoom = false)
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        assertTrue(f.manager.enter(f.player))
        val start = f.clock.cycle
        f.tick(4)
        assertEquals("SPAWNING", encounter.currentPhaseName)
        // Advance just the injected clock: only the actual world event may consume this deadline.
        f.clock.tick()
        repeat(8) { f.lifecycle.onPostTick(f.player) }
        assertEquals("SPAWNING", encounter.currentPhaseName)
        f.events.publish(org.rsmod.api.game.process.GameLifecycle.EndCycle)
        assertEquals(start + 5, f.clock.cycle)
        assertEquals("SMOKE", encounter.currentPhaseName)
        assertFalse(boss.movementLocked)
    }

    @Test
    fun `departing partner does not reset the other participant or advance extra cycles`() {
        val f = NexMinionTestWorld(duo = true)
        val partner = requireNotNull(f.partner)
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        f.manager.playerLoggedOut(f.player)
        f.players.remove(f.player.slotId)
        f.tick(10)
        assertSame(boss, f.npc(11278))
        assertEquals("SMOKE", encounter.currentPhaseName)
        assertTrue(f.manager.isParticipant(partner))
        assertFalse(encounter.isEnded)
        f.manager.playerLoggedOut(partner)
        f.players.remove(partner.slotId)
        f.tick(2)
        assertTrue(encounter.isEnded)
        assertEquals(5, f.npcs.count())
    }

    @Test
    fun `abandoned opening and reentry do not reuse a retired room identity`() {
        val f = NexMinionTestWorld(enterRoom = false)
        repeat(3) {
            f.players[f.player.slotId] = f.player
            assertTrue(f.manager.enter(f.player))
            val old = f.npc(11278)
            val encounter = f.encounters.of(old)
            f.manager.playerLoggedOut(f.player)
            f.players.remove(f.player.slotId)
            f.tick(2)
            assertTrue(encounter.isEnded)
            assertFalse(old.isSlotAssigned)
            assertFalse(f.manager.isParticipant(f.player))
            assertEquals(5, f.npcs.count())
            assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
        }
    }

    private fun worldCycle(f: NexMinionTestWorld) =
        GameCycle(
            f.events,
            f.clock,
            mock(WorldMainTickProcess::class.java),
            mock(NpcPreTickProcess::class.java),
            mock(PlayerIdShuffleProcess::class.java),
            mock(PlayerInputProcess::class.java),
            mock(PlayerRouteRequestProcess::class.java),
            mock(NpcMainProcess::class.java),
            mock(ControllerMainProcess::class.java),
            mock(PlayerMainProcess::class.java),
            mock(PlayerLogoutProcess::class.java),
            mock(PlayerLoginProcess::class.java),
            mock(WorldPostTickProcess::class.java),
            mock(PlayerPostTickProcess::class.java),
        )

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
