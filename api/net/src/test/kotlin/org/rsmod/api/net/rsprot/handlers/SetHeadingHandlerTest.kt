package org.rsmod.api.net.rsprot.handlers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import net.rsprot.protocol.game.incoming.misc.user.SetHeading
import org.rsmod.api.player.events.SailingEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

class SetHeadingHandlerTest {
    @Test
    fun `all sixteen client headings publish exactly once`() {
        val eventBus = EventBus()
        val events = mutableListOf<SailingEvent.SetHeading>()
        eventBus.subscribeUnbound(SailingEvent.SetHeading::class.java) { events += this }
        val handler = SetHeadingHandler(eventBus)
        val player = Player()

        for (heading in 0..15) {
            handler.handle(player, SetHeading(heading))
        }

        assertEquals((0..15).toList(), events.map { it.heading })
        events.forEach { assertSame(player, it.player) }
    }

    @Test
    fun `headings outside the unsigned four-bit range publish nothing`() {
        val eventBus = EventBus()
        val events = mutableListOf<SailingEvent.SetHeading>()
        eventBus.subscribeUnbound(SailingEvent.SetHeading::class.java) { events += this }
        val handler = SetHeadingHandler(eventBus)
        val player = Player()

        for (heading in INVALID_HEADINGS) {
            handler.handle(player, SetHeading(heading))
        }

        assertEquals(emptyList(), events)
    }

    private companion object {
        private val INVALID_HEADINGS =
            intArrayOf(Int.MIN_VALUE, -1_000, -1, 16, 17, 1_000, Int.MAX_VALUE)
    }
}
