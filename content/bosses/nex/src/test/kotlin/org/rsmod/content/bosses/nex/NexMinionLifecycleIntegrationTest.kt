package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Real registered combat, NPC repository deletion and player hit queue, not source assertions. */
@OptIn(InternalApi::class)
class NexMinionLifecycleIntegrationTest {
    @Test
    fun `all minions have one DSL combat owner and preserve stationary guards`() {
        val f = NexMinionTestWorld()
        for (id in 11283..11286) {
            val guard = f.npc(id)
            assertNotNull(f.encounters.registeredSpec(guard))
            f.encounters.of(guard)
            assertTrue(guard.movementLocked)
        }
        val reaver = f.summonReaver()
        assertNotNull(f.encounters.registeredSpec(reaver))
        f.encounters.of(reaver)
        assertFalse(reaver.movementLocked)
        assertEquals(2, f.encounters.registeredBosses().size)
    }

    @Test
    fun `queued native magic applies damage through the player processor`() {
        val f = NexMinionTestWorld()
        f.reachGuard(11283)
        f.attack(f.npc(11283))
        f.processPlayerHits()
        assertEquals(79, f.player.hitpoints)
        assertTrue(f.hits().isEmpty())
    }

    @Test
    fun `departure cancels already launched minion magic before room post tick`() {
        val departures: List<(NexMinionTestWorld) -> Unit> =
            listOf(
                { it.player.coords = NEX_BANK_RETURN },
                { it.player.pendingLogout = true },
                { it.player.loggingOut = true },
                { it.manager.exit(it.player) },
                { it.manager.playerLoggedOut(it.player) },
                { it.player.statMap.setCurrentLevel("stat.hitpoints", 0) },
            )
        for (reaver in listOf(false, true)) for ((index, leave) in departures.withIndex()) {
            val f = NexMinionTestWorld()
            val source =
                if (reaver) f.summonReaver()
                else {
                    f.reachGuard(11283)
                    f.npc(11283)
                }
            f.attack(source)
            assertEquals(1, f.hits().size)
            leave(f)
            val hp = f.player.hitpoints
            f.processPlayerHits()
            assertEquals(hp, f.player.hitpoints, "departure=$index reaver=$reaver")
            assertTrue(f.hits().isEmpty())
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `source deletion and slot reuse cannot revive an old attack`() {
        val f = NexMinionTestWorld()
        f.reachGuard(11283)
        val guard = f.npc(11283)
        f.attack(guard)
        val old = assertNotNull(f.encounters.existing(guard))
        val hit = f.hits().single().second
        val slot = guard.slotId
        f.repository.del(guard, Int.MAX_VALUE)
        assertTrue(old.isEnded)
        assertFalse(hit.isValidNow())
        val replacement = Npc(guard.type, guard.coords).apply { respawns = false }
        // Exercise allocator wrap/reuse without constructing 32k unrelated entities.
        f.npcs[slot - 1] = f.npcs.getValue(slot - 1)
        f.repository.add(replacement, Int.MAX_VALUE)
        assertEquals(slot, replacement.slotId)
        assertNotSame(old, f.encounters.of(replacement))
        assertFalse(hit.isValidNow())
        f.processPlayerHits()
        assertEquals(99, f.player.hitpoints)
    }

    @Test
    fun `teardown is idempotent and fresh encounter does not inherit cooldown`() {
        val f = NexMinionTestWorld()
        f.reachGuard(11283)
        val guard = f.npc(11283)
        f.attack(guard)
        val old = assertNotNull(f.encounters.existing(guard))
        f.encounters.remove(guard)
        f.encounters.remove(guard)
        val fresh = f.encounters.of(guard)
        assertTrue(old.isEnded)
        assertNotSame(old, fresh)
        assertEquals(0, fresh.phaseAbilityCount)
        f.processPlayerHits()
        assertEquals(99, f.player.hitpoints)
    }

    @Test
    fun `target slot reuse invalidates previously fired magic`() {
        val f = NexMinionTestWorld()
        f.reachGuard(11283)
        f.attack(f.npc(11283))
        val replacement =
            Player().apply {
                slotId = 1
                uuid = 2
                observerUUID = 2
                assignUid()
            }
        f.players[1] = replacement
        f.processPlayerHits()
        assertEquals(99, f.player.hitpoints)
    }

    @Test
    fun `fired magic survives source death and phase handoff until actual deletion`() {
        val f = NexMinionTestWorld()
        f.reachGuard(11283)
        val guard = f.npc(11283)
        f.attack(guard)
        guard.hitpoints = 0
        f.manager.handleDeath(guard)
        f.processPlayerHits()
        assertEquals(79, f.player.hitpoints)
    }

    @Test
    fun `reaver death removes summon membership but preserves its fired native spell`() {
        val f = NexMinionTestWorld()
        val reaver = f.summonReaver()
        f.attack(reaver)
        reaver.hitpoints = 0
        assertEquals(NexEncounterManager.DeathKind.REAVER, f.manager.handleDeath(reaver))
        f.processPlayerHits()
        assertEquals(81, f.player.hitpoints)
    }

    @Test
    fun `unowned reaver cannot borrow active room combat registration`() {
        val f = NexMinionTestWorld()
        val owned = f.summonReaver()
        val outsider = Npc(owned.type, owned.coords).apply { respawns = false }
        f.repository.add(outsider, Int.MAX_VALUE)
        f.attack(outsider)
        assertTrue(f.hits().isEmpty())
        assertNull(f.encounters.existing(outsider))
        f.attack(owned)
        f.processPlayerHits()
        assertEquals(81, f.player.hitpoints)
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
