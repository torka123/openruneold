package org.rsmod.game.entity

import dev.openrune.types.NpcServerType
import kotlin.test.*
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol

@OptIn(org.rsmod.annotations.InternalApi::class)
class NpcBodyModelsTest {
    @Test
    fun `pre-registration models are immutable and replay on each protocol attachment`() {
        val npc = Npc(NpcServerType(id = 12596, size = 3, hitpoints = 600))
        val models = mutableListOf(55952, 70000)
        npc.setBodyModels(models)
        models[0] = 1
        assertEquals(listOf(55952, 70000), npc.avatar.bodyModels)
        assertFailsWith<UnsupportedOperationException> {
            (npc.avatar.bodyModels as MutableList<Int>)[0] = 2
        }
        val first = RecordingInfo()
        npc.infoProtocol = first
        assertEquals(listOf(listOf(55952, 70000)), first.models)
        npc.setBodyModels(listOf(55952, 70000))
        assertEquals(1, first.models.size, "Identical set does not queue redundant appearance")
        val second = RecordingInfo()
        npc.infoProtocol = second
        assertEquals(first.models, second.models)
        assertEquals(12596, npc.visType.id)
        assertEquals(3, npc.size)
        assertEquals(600, npc.hitpoints)
    }

    @Test
    fun `explicit reset is idempotent and prevents stale replay`() {
        val npc = Npc(NpcServerType(id = 1))
        val info = RecordingInfo()
        npc.infoProtocol = info
        npc.setBodyModels(listOf(55940))
        npc.resetBodyModels()
        npc.resetBodyModels()
        assertNull(npc.avatar.bodyModels)
        assertEquals(1, info.resets)
        val replacement = RecordingInfo()
        npc.infoProtocol = replacement
        assertTrue(replacement.models.isEmpty())
        assertEquals(0, replacement.resets)
    }

    @Test
    fun `transmog preserves independent body override but native respawn clears it`() {
        val npc = Npc(NpcServerType(id = 14147))
        npc.slotId = 1
        npc.assignUid()
        val info = RecordingInfo()
        npc.infoProtocol = info
        npc.setBodyModels(listOf(55940))
        npc.transmog(NpcServerType(id = 14149), Int.MAX_VALUE)
        assertEquals(listOf(55940), npc.avatar.bodyModels)
        npc.setRespawnValues()
        assertNull(npc.avatar.bodyModels)
        assertEquals(1, info.resets)
        assertEquals(14147, npc.visType.id)
    }

    @Test
    fun `model bounds reject invalid packet counts without mutating valid state`() {
        val npc = Npc(NpcServerType(id = 1))
        npc.setBodyModels(List(255) { Int.MAX_VALUE })
        for (invalid in listOf(emptyList(), listOf(-1), List(256) { 1 })) {
            assertFailsWith<IllegalArgumentException> { npc.setBodyModels(invalid) }
            assertEquals(List(255) { Int.MAX_VALUE }, npc.avatar.bodyModels)
        }
    }

    private class RecordingInfo : NpcInfoProtocol by NoopNpcInfo {
        val models = mutableListOf<List<Int>>()
        var resets = 0

        override fun setBodyModels(models: List<Int>) {
            this.models += models
        }

        override fun resetBodyModels() {
            resets++
        }
    }
}
