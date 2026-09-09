package org.rsmod.api.game.process.world

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

class WorldQueueDeadlineTest {
    @Test
    fun `appended absolute work cannot run early regardless of queue position`() {
        for (hasTrailingWork in listOf(false, true)) {
            val clock = MapClock(100)
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val calls = mutableListOf<Int>()
            queues.add(1) {
                queues.addCancellable(0, ready = { clock.cycle >= 103 }) { calls += clock.cycle }
            }
            if (hasTrailingWork) queues.add(10) {}
            repeat(3) {
                process.process()
                assertTrue(calls.isEmpty())
                clock.tick()
            }
            process.process()
            assertEquals(listOf(103), calls)
            process.process()
            assertEquals(listOf(103), calls)
        }
    }

    @Test
    fun `relative countdown still includes its first processing tick`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        var calls = 0
        queues.add(2) { calls++ }
        process.process()
        assertEquals(0, calls)
        process.process()
        assertEquals(1, calls)
        assertTrue(queues.isEmpty)
    }

    @Test
    fun `cancellation physically removes a not yet ready task`() {
        val queues = WorldQueueList()
        val pending = queues.addCancellable(0, ready = { false }) { fail("Cancelled callback ran") }
        WorldQueueListProcess(queues).process()
        assertEquals(1, queues.size)
        pending.cancel()
        assertTrue(queues.isEmpty)
    }
}
