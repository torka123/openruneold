package org.rsmod.game.ui.collection

import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentEventMapTest {
    @Test
    fun `static component range remains negative one`() {
        val event = ComponentEventMap.Event.from(component = 17, start = -1, end = -1, events = 1L)

        assertEquals(-1, event.start)
        assertEquals(-1, event.end)
    }

    @Test
    fun `inventory component range remains unchanged`() {
        val event = ComponentEventMap.Event.from(component = 19, start = 0, end = 27, events = 1L)

        assertEquals(0, event.start)
        assertEquals(27, event.end)
    }
}
