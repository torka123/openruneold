package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.game.entity.npc.NpcStateEvents

/**
 * S08 in docs/bosses/nex/NEX_OSRS_AUDIT.md: retained Wiki revisions15325291/15331654 establish
 * ordinary Defence260 and maximum reduction10. Exercise native actor configuration and immediate
 * mutation, without certifying any unresolved Turmoil boost/floor formula.
 */
class NexDefenceDrainIntegrationTest {
    @Test
    fun `prepared main actor clamps native defence immediately while guards remain ordinary`() {
        val f = NexMinionTestWorld(enterRoom = false)
        val boss = f.npc(11278)
        assertEquals(260, boss.baseDefenceLvl)
        assertEquals(250, boss.defenceDrainMinimum)
        boss.defenceLvl = 255
        assertEquals(255, boss.defenceLvl)
        boss.defenceLvl = 1
        assertEquals(250, boss.defenceLvl, "No world or player tick is required")
        boss.defenceLvl = 0
        assertEquals(250, boss.defenceLvl)
        boss.defenceLvl = 260
        assertEquals(260, boss.defenceLvl)
        for (id in 11283..11286) {
            val guard = f.npc(id)
            assertNull(guard.defenceDrainMinimum)
            guard.defenceLvl = 1
            assertEquals(1, guard.defenceLvl)
        }
    }

    @Test
    fun `native replacement callbacks see the minimum before any new actor can fight`() {
        val f = NexMinionTestWorld()
        val old = f.npc(11278)
        val session = f.session()
        var creations = 0
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            if (npc.type.id in 11278..11286) {
                creations++
                assertEquals(session.id, f.instances.instanceForNpc(npc))
                if (npc.type.id == 11278) {
                    assertEquals(250, npc.defenceDrainMinimum)
                    npc.defenceLvl = 1
                    assertEquals(250, npc.defenceLvl)
                } else assertNull(npc.defenceDrainMinimum)
            }
        }
        f.repository.del(old, Int.MAX_VALUE)
        f.tick(2)
        assertEquals(5, creations)
        assertNotSame(old, f.npc(11278))
        assertSame(session, f.session())
        assertEquals(250, f.npc(11278).defenceDrainMinimum)
    }

    @Test
    fun `phase forms preserve the policy and summoned reavers retain ordinary defaults`() {
        val f = NexMinionTestWorld()
        val reaver = f.summonReaver()
        val boss = f.npc(11278)
        assertNull(reaver.defenceDrainMinimum)
        reaver.defenceLvl = 1
        assertEquals(1, reaver.defenceLvl)
        for (form in 11278..11282) {
            boss.transmog(assertNotNull(ServerCacheManager.getNpc(form)), Int.MAX_VALUE)
            assertEquals(250, boss.defenceDrainMinimum)
            boss.defenceLvl = 260
            boss.defenceLvl = 1
            assertEquals(250, boss.defenceLvl)
        }
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
