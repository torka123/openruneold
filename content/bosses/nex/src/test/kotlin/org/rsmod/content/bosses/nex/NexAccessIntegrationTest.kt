package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.map.CoordGrid

/** Production coordinate dispatch and native protected movement, not rendered OSRS timing proof. */
@OptIn(InternalApi::class)
class NexAccessIntegrationTest {
    @Test
    fun `stationary barrier dispatch commits after native hook and movement`() {
        val f = NexMinionTestWorld(enterRoom = false)
        val origin = f.player.coords
        var observed = false
        f.beforeTeleport = { target, dest, type ->
            observed = true
            assertSame(f.player, target)
            assertEquals(origin, target.coords)
            assertEquals(NEX_ARENA_ENTRY, dest)
            assertEquals(TeleportType.Exempt, type)
            assertFalse(f.manager.isParticipant(target))
            assertNull(target.lootDropDuration)
        }
        assertTrue(f.passBarrier())
        assertTrue(observed)
        assertEquals(NEX_ARENA_ENTRY, f.player.coords)
        assertTrue(f.manager.isParticipant(f.player))
        assertEquals(NEX_DROP_DURATION, f.player.lootDropDuration)
        assertEquals("SPAWNING", f.encounters.of(f.npc(11278)).currentPhaseName)
    }

    @Test
    fun `arrived barrier waits the native cycle before entry`() {
        val f = NexMinionTestWorld(enterRoom = false)
        f.player.lastMovement = f.player.processedMapClock - 1
        assertTrue(f.passBarrier())
        assertFalse(f.manager.isParticipant(f.player))
        assertEquals(NEX_BANK_RETURN, f.player.coords)
        f.tick()
        assertTrue(f.manager.isParticipant(f.player))
        assertEquals(NEX_ARENA_ENTRY, f.player.coords)
    }

    @Test
    fun `busy or wrong coordinate cannot enter through barrier dispatch`() {
        val f = NexMinionTestWorld(enterRoom = false)
        assertFalse(f.passBarrier(coords = CoordGrid(3000, 3000, 0)))
        f.player.delay = f.clock.cycle + 5
        assertFalse(f.passBarrier())
        assertFalse(f.manager.isParticipant(f.player))
        assertEquals(NEX_BANK_RETURN, f.player.coords)
    }

    @Test
    fun `invalid barrier destination preserves unenrolled room and player`() {
        val f = NexMinionTestWorld(enterRoom = false)
        f.collision.deallocateIfPresent(NEX_ARENA_ENTRY.x, NEX_ARENA_ENTRY.z, NEX_ARENA_ENTRY.level)
        f.beforeTeleport = { _, _, _ -> fail("Invalid zone must not dispatch teleport hooks") }
        assertTrue(f.passBarrier())
        assertEquals(NEX_BANK_RETURN, f.player.coords)
        assertFalse(f.manager.isParticipant(f.player))
        assertNull(f.player.lootDropDuration)
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
    }

    @Test
    fun `physical barrier remains exempt from spell validation`() {
        val f = NexMinionTestWorld(enterRoom = false)
        f.teleportDenial = "Fixture spell restriction"
        assertTrue(f.passBarrier())
        assertTrue(f.manager.isParticipant(f.player))
        assertTrue(f.validatedTeleports.isEmpty())
    }

    @Test
    fun `room retired by native movement hook cannot receive membership`() {
        val f = NexMinionTestWorld(enterRoom = false)
        val old = f.npc(11278)
        f.beforeTeleport = { _, _, _ ->
            f.repository.del(old, Int.MAX_VALUE)
            f.events.publish(GameLifecycle.EndCycle)
        }
        assertTrue(f.passBarrier())
        assertFalse(f.manager.isParticipant(f.player))
        assertNull(f.player.lootDropDuration)
        assertNotSame(old, f.npc(11278))
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
    }

    @Test
    fun `administrative bank movement dispatches hook before departure cleanup`() {
        val f = NexMinionTestWorld()
        var observed = false
        f.teleportDenial = "Fixture spell restriction"
        f.beforeTeleport = { target, dest, type ->
            observed = true
            assertTrue(f.manager.isParticipant(target))
            assertEquals(NEX_ARENA_ENTRY, target.coords)
            assertEquals(NEX_BANK_TELEPORT, dest)
            assertEquals(TeleportType.Exempt, type)
        }
        assertTrue(f.travelBank())
        assertTrue(observed)
        assertEquals(NEX_BANK_TELEPORT, f.player.coords)
        assertFalse(f.manager.isParticipant(f.player))
        assertNull(f.player.lootDropDuration)
        assertTrue(f.validatedTeleports.isEmpty())
    }

    @Test
    fun `invalid administrative bank destination preserves participation`() {
        val f = NexMinionTestWorld()
        f.collision.deallocateIfPresent(
            NEX_BANK_TELEPORT.x,
            NEX_BANK_TELEPORT.z,
            NEX_BANK_TELEPORT.level,
        )
        assertFalse(f.travelBank())
        assertEquals(NEX_ARENA_ENTRY, f.player.coords)
        assertTrue(f.manager.isParticipant(f.player))
        assertEquals(NEX_DROP_DURATION, f.player.lootDropDuration)
    }

    @Test
    fun `registered nex command uses protected native movement and rejects busy access`() {
        val f = NexMinionTestWorld()
        var calls = 0
        f.beforeTeleport = { _, _, type ->
            calls++
            assertEquals(TeleportType.Exempt, type)
        }
        f.player.delay = f.clock.cycle + 1
        assertTrue(f.commands.execute(f.player, "nex", emptyList()))
        assertEquals(0, calls)
        assertTrue(f.manager.isParticipant(f.player))
        f.tick()
        assertTrue(f.commands.execute(f.player, "nex", emptyList()))
        assertEquals(1, calls)
        assertEquals(NEX_BANK_TELEPORT, f.player.coords)
        assertFalse(f.manager.isParticipant(f.player))
    }

    @Test
    fun `session removed by movement hook cannot acquire encounter membership`() {
        val f = NexMinionTestWorld(enterRoom = false)
        f.beforeTeleport = { target, _, _ -> f.players.remove(target.slotId) }
        assertTrue(f.passBarrier())
        assertFalse(f.manager.isParticipant(f.player))
        assertNull(f.player.lootDropDuration)
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)
    }

    @Test
    fun `active fight refuses barrier passage without native movement or membership`() {
        val f = NexMinionTestWorld()
        val other =
            org.rsmod.game.entity.Player().apply {
                slotId = 2
                uuid = 2L
                observerUUID = 2L
                assignUid()
                coords = NEX_BANK_RETURN
                statMap.setCurrentLevel("stat.hitpoints", 99)
            }
        f.players[2] = other
        f.beforeTeleport = { _, _, _ -> fail("Sealed barrier must not dispatch movement") }
        assertTrue(f.passBarrier(other))
        assertFalse(f.manager.isParticipant(other))
        assertEquals(NEX_BANK_RETURN, other.coords)
    }

    @Test
    fun `already at destination cannot be mistaken for accepted native movement`() {
        val f = NexMinionTestWorld(enterRoom = false)
        f.player.coords = NEX_ARENA_ENTRY
        f.collision.deallocateIfPresent(NEX_ARENA_ENTRY.x, NEX_ARENA_ENTRY.z, NEX_ARENA_ENTRY.level)
        assertFalse(f.enter())
        assertFalse(f.manager.isParticipant(f.player))
        assertNull(f.player.lootDropDuration)
        f.collision.allocateIfAbsent(NEX_ARENA_ENTRY.x, NEX_ARENA_ENTRY.z, NEX_ARENA_ENTRY.level)
        f.player.coords = NEX_BANK_RETURN
        assertTrue(f.enter())
        f.player.coords = NEX_BANK_RETURN
        f.teleportDenial = "Fixture refusal"
        assertFalse(f.exit())
        assertTrue(f.manager.isParticipant(f.player))
        assertEquals(NEX_DROP_DURATION, f.player.lootDropDuration)
        f.player.coords = NEX_BANK_TELEPORT
        assertFalse(f.travelBank())
        assertTrue(f.manager.isParticipant(f.player))
    }

    @Test
    fun `altar hook retiring the room cannot clear replacement ownership`() {
        val f = NexMinionTestWorld()
        f.beforeTeleport = { target, _, _ ->
            f.repository.del(f.npc(11278), Int.MAX_VALUE)
            f.events.publish(GameLifecycle.EndCycle)
            target.lootDropDuration = 731
        }
        assertFalse(f.exit())
        assertEquals(731, f.player.lootDropDuration)
        assertEquals(NEX_BANK_RETURN, f.player.coords)
    }

    @Test
    fun `administrative hook removing the session cannot commit stale departure`() {
        val f = NexMinionTestWorld()
        f.beforeTeleport = { target, _, _ ->
            f.players.remove(target.slotId)
            target.lootDropDuration = 731
        }
        assertFalse(f.travelBank())
        assertEquals(731, f.player.lootDropDuration)
    }

    @Test
    fun `altar validation refusal preserves participation and loot ownership`() {
        val f = NexMinionTestWorld()
        f.teleportDenial = "Fixture travel refusal"
        f.beforeTeleport = { _, _, _ -> fail("Rejected travel must not dispatch hooks") }
        assertTrue(f.altarExit())
        assertEquals(listOf(TeleportType.Standard), f.validatedTeleports)
        assertEquals(NEX_ARENA_ENTRY, f.player.coords)
        assertTrue(f.manager.isParticipant(f.player))
        assertEquals(NEX_DROP_DURATION, f.player.lootDropDuration)
    }

    @Test
    fun `altar movement precedes cleanup and preserves the other participant`() {
        val f = NexMinionTestWorld(duo = true)
        var observed = false
        f.beforeTeleport = { target, dest, type ->
            observed = true
            assertTrue(f.manager.isParticipant(target))
            assertEquals(NEX_ARENA_ENTRY, target.coords)
            assertEquals(NEX_BANK_RETURN, dest)
            assertEquals(TeleportType.Standard, type)
        }
        assertTrue(f.altarExit())
        assertTrue(observed)
        assertEquals(NEX_BANK_RETURN, f.player.coords)
        assertFalse(f.manager.isParticipant(f.player))
        assertNull(f.player.lootDropDuration)
        assertTrue(f.manager.isParticipant(requireNotNull(f.partner)))
        assertEquals(NEX_DROP_DURATION, f.partner.lootDropDuration)
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
