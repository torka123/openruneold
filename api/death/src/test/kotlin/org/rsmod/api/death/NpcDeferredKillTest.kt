package org.rsmod.api.death

import dev.openrune.types.NpcServerType
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

/** S04 deferred dispatch reuses normal hooks; caller-owned completion guards remain required. */
@OptIn(InternalApi::class)
class NpcDeferredKillTest {
    @Test
    fun `personal recipients share one native event and preserve hook order without remains`() {
        val f = Fixture()
        val second = f.player(2, 22).also { f.players[2] = it }
        f.npc.hidden = true
        assertFalse(f.npc.isSlotAssigned)
        val event = f.death.nextLootTrackerEventId()
        for (player in listOf(f.hero, second)) {
            val context = f.context(player, event)
            assertTrue(f.death.dispatchDeferredKill(context))
            assertSame(context, f.seen.takeLast(2)[0])
            assertSame(context, f.seen.takeLast(2)[1])
        }
        assertEquals(listOf(0, 1, 0, 1), f.order)
        assertTrue(f.seen.all { it.lootTrackerEventId == event && it.dropCoords != f.npc.coords })
        verifyNoInteractions(f.objects, f.npcs)
    }

    @Test
    fun `unregistered replaced and suppressed recipients do not dispatch`() {
        val f = Fixture()
        val context = f.context(f.hero, f.death.nextLootTrackerEventId())
        f.players.remove(f.hero.slotId)
        assertFalse(f.death.dispatchDeferredKill(context))
        f.players[1] = f.player(1, 99)
        assertFalse(f.death.dispatchDeferredKill(context))
        f.players[1] = f.hero
        f.npc.suppressRewards = true
        assertFalse(f.death.dispatchDeferredKill(context))
        assertTrue(f.seen.isEmpty())
        verifyNoInteractions(f.objects, f.npcs)
    }

    @Test
    fun `invalid event fails before any hook and ordinary context defaults are unchanged`() {
        val f = Fixture()
        val context = NpcDeathKillContext(f.hero, f.npc, 0, f.npc.coords)
        assertFalse(context.dropTableHandled)
        assertFalse(context.killcountHandled)
        assertFailsWith<IllegalArgumentException> { f.death.dispatchDeferredKill(context) }
        assertTrue(f.seen.isEmpty())
    }

    private class Fixture {
        val players = PlayerList()
        val hero = player(1, 11).also { players[1] = it }
        val npc = Npc(NpcServerType(id = 1, name = "Deferred"), CoordGrid(3000, 3000))
        val objects = mock(ObjRepository::class.java)
        val npcs = mock(NpcRepository::class.java)
        val seen = mutableListOf<NpcDeathKillContext>()
        val order = mutableListOf<Int>()
        val death =
            NpcDeath(
                npcs,
                players,
                objects,
                setOf(NpcDeathDropHook { error("Deferred completion does not dispatch remains") }),
                linkedSetOf(
                    NpcDeathKillHook {
                        order += 0
                        seen += it
                    },
                    NpcDeathKillHook {
                        order += 1
                        seen += it
                    },
                ),
            )

        fun context(hero: Player, event: Int) =
            NpcDeathKillContext(hero, npc, event, npc.coords.translateX(4), true, true)

        fun player(slot: Int, identity: Long) =
            Player().apply {
                slotId = slot
                uuid = identity
                assignUid()
            }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()
}
