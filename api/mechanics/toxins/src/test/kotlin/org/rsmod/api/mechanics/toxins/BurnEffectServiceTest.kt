package org.rsmod.api.mechanics.toxins

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList

class BurnEffectServiceTest {
    @Test
    fun `npc explicit-damage burn delegates to the shared stack implementation`() {
        val queues = WorldQueueList()
        val service = BurnEffectService(queues, StandardNpcHitModifier(EventBus()))
        val source = Npc(NpcServerType(id = 1, name = "burn source"))
        val target = Player().apply { pendingLogout = true }

        assertFalse(service.apply(source, target, damage = 8))
        assertEquals(0, service.stackCount(target))
        assertEquals(0, queues.size)
    }
}
