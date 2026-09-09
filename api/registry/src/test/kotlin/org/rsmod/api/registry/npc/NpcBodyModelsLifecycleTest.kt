package org.rsmod.api.registry.npc

import dev.openrune.types.NpcServerType
import kotlin.test.*
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

class NpcBodyModelsLifecycleTest {
    @Test
    fun `registry clears models before release and severs the released protocol reference`() {
        val events = EventBus()
        val order = mutableListOf<String>()
        var released = false
        events.subscribeUnbound(NpcStateEvents.Delete::class.java) {
            assertNull(npc.avatar.bodyModels)
            order += "release"
            released = true
        }
        val registry = NpcRegistry(NpcList(), CollisionFlagMap(), events)
        val npc = Npc(NpcServerType(id = 12596), CoordGrid(2910, 9571))
        assertEquals(NpcRegistryResult.Add.Success, registry.add(npc))
        npc.infoProtocol =
            object : NpcInfoProtocol by NoopNpcInfo {
                override fun setBodyModels(models: List<Int>) {
                    order += "set"
                }

                override fun resetBodyModels() {
                    assertFalse(released)
                    order += "reset"
                }

                override fun disable() {
                    assertFalse(
                        released,
                        "A release listener may already have recycled this avatar",
                    )
                    order += "disable"
                }
            }
        npc.setBodyModels(listOf(55952))
        assertEquals(NpcRegistryResult.Delete.Success, registry.del(npc))
        assertEquals(listOf("set", "reset", "disable", "release"), order)
        assertSame(NoopNpcInfo, npc.infoProtocol)
        assertEquals(NpcRegistryResult.Delete.UnexpectedSlot, registry.del(npc))
        assertEquals(4, order.size)
        npc.setBodyModels(listOf(55940))
        assertEquals(4, order.size, "Deleted NPC must not mutate a recycled protocol avatar")
    }
}
