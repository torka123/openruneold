package org.rsmod.game.queue

import kotlin.test.*

class WorldQueueCancellationTest {
    @Test
    fun `cancel unlinks task immediately and is idempotent`() {
        val queues = WorldQueueList()
        val cancel = queues.addCancellable(100) { error("cancelled") }
        cancel.cancel()
        cancel.cancel()
        assertTrue(queues.isEmpty)
        assertFalse(queues.iterator().hasNext())
    }

    @Test
    fun `cancelling next item while processing keeps later items reachable`() {
        val queues = WorldQueueList()
        val calls = mutableListOf<String>()
        lateinit var cancel: WorldQueueList.Cancellation
        queues.add(1) {
            calls += "first"
            cancel.cancel()
        }
        cancel = queues.addCancellable(1) { error("cancelled") }
        queues.add(1) { calls += "last" }
        val iterator = queues.iterator()
        while (iterator.hasNext()) {
            val queue = iterator.next()
            iterator.remove()
            queue.action()
        }
        iterator.cleanUp()
        assertEquals(listOf("first", "last"), calls)
        assertTrue(queues.isEmpty)
        cancel.cancel()
    }

    @Test
    fun `cancel after execution does not remove another task`() {
        val queues = WorldQueueList()
        val cancel = queues.addCancellable(1) {}
        val iterator = queues.iterator()
        val queue = iterator.next()
        iterator.remove()
        queue.action()
        iterator.cleanUp()
        queues.add(20) {}
        cancel.cancel()
        assertEquals(1, queues.size)
    }
}
