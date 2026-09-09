package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol

class RoyalTitansBodyModelsTest {
    @Test
    fun `each landing sets the captured body on its exact transition tick without new identity`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val b = f.npc(player, 12596)
        val e = f.npc(player, 14147)
        val identities = listOf(b.uid, e.uid)
        val updates = mutableListOf<Triple<Int, Int, List<Int>>>()
        for (npc in listOf(b, e)) {
            npc.infoProtocol =
                object : NpcInfoProtocol by NoopNpcInfo {
                    override fun setBodyModels(models: List<Int>) {
                        updates += Triple(f.clock.cycle, npc.id, models)
                    }
                }
        }
        f.hit(player, b, 406)
        f.tick()
        val start = f.clock.cycle
        f.tick(2)
        assertTrue(updates.isEmpty())
        f.tick()
        assertEquals(listOf(Triple(start + 3, 12596, listOf(55952))), updates)
        assertEquals(f.tile(player, 30, 35), b.coords)
        assertEquals(11966, b.pendingSequence.id)
        assertNull(e.avatar.bodyModels)
        f.tick(3)
        assertEquals(1, updates.size)
        f.tick()
        assertEquals(Triple(start + 7, 14147, listOf(55940)), updates.last())
        assertEquals(2, updates.size)
        assertEquals(f.tile(player, 30, 25), e.coords)
        assertEquals(11963, e.pendingSequence.id)
        assertEquals(identities, listOf(b.uid, e.uid))
        assertEquals(listOf(12596, 14147), listOf(b.visType.id, e.visType.id))
        assertEquals(listOf(3, 3), listOf(b.size, e.size))
    }

    @Test
    fun `both defeat orders reset only the transformed Titan in that same native update`() {
        for (firstBranda in listOf(true, false)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val b = f.npc(player, 12596)
            val e = f.npc(player, 14147)
            f.hit(player, b, 406)
            f.tick(10)
            val order = if (firstBranda) listOf(b, e) else listOf(e, b)
            for ((index, npc) in order.withIndex()) {
                val uid = npc.uid
                val updates = mutableListOf<Pair<Int, String>>()
                npc.infoProtocol =
                    object : NpcInfoProtocol by NoopNpcInfo {
                        override fun setTransmog(type: Int) {
                            updates += f.clock.cycle to "type:$type"
                        }

                        override fun resetBodyModels() {
                            updates += f.clock.cycle to "reset"
                        }
                    }
                f.hit(player, npc, npc.hitpoints)
                f.tick(2)
                val corpseId = if (npc === b) 14148 else 14149
                assertEquals(listOf("type:$corpseId", "reset"), updates.map { it.second })
                assertEquals(1, updates.map { it.first }.distinct().size)
                assertNull(npc.avatar.bodyModels)
                assertEquals(uid, npc.uid)
                assertEquals(corpseId, npc.visType.id)
                if (index == 0) assertNotNull(order[1].avatar.bodyModels)
            }
        }
    }

    @Test
    fun `leaving phase two clears retained models and a new instance has default bodies`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val b = f.npc(player, 12596)
        val e = f.npc(player, 14147)
        f.hit(player, b, 406)
        f.tick(10)
        assertNotNull(b.avatar.bodyModels)
        assertNotNull(e.avatar.bodyModels)
        assertTrue(f.manager.escape(player))
        f.tick(2)
        assertNull(b.avatar.bodyModels)
        assertNull(e.avatar.bodyModels)
        assertFalse(b.isSlotAssigned)
        assertFalse(e.isSlotAssigned)
        assertTrue(f.manager.enter(player))
        f.tick(6)
        assertNull(f.npc(player, 12596).avatar.bodyModels)
        assertNull(f.npc(player, 14147).avatar.bodyModels)
    }

    @Test
    fun `repeated phase two completions respawn clean bodies with no retained instance NPCs`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        repeat(4) {
            f.tick(6)
            val b = f.npc(player, 12596)
            val e = f.npc(player, 14147)
            assertNull(b.avatar.bodyModels)
            assertNull(e.avatar.bodyModels)
            f.hit(player, b, 406)
            f.tick(10)
            assertEquals(listOf(55952), b.avatar.bodyModels)
            assertEquals(listOf(55940), e.avatar.bodyModels)
            f.hit(player, b, b.hitpoints)
            f.hit(player, e, e.hitpoints)
            f.tick(2)
            f.manager.claim(player, b, ClaimChoice.PAGES)
            f.tick(31)
            assertFalse(b.isSlotAssigned || e.isSlotAssigned)
            assertNull(b.avatar.bodyModels)
            assertNull(e.avatar.bodyModels)
            assertEquals(2, f.activeNpcs(player).size)
            assertTrue(f.activeNpcs(player).all { npc -> npc.avatar.bodyModels == null })
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
