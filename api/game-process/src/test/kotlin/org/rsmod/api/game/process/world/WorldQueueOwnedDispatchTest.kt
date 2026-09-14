package org.rsmod.api.game.process.world

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

class WorldQueueOwnedDispatchTest {
    @Test
    fun `default processing and two owners advance only their own countdowns`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val first = queues.createOwner()
        val second = queues.createOwner()
        val calls = mutableListOf<String>()
        first.add(2) { calls += "first" }
        queues.add(2) { calls += "default" }
        second.add(1) { calls += "second" }

        repeat(3) { process.process() }
        assertEquals(listOf("default"), calls)
        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(2, queues.size)
        process.processOwned(first)
        assertEquals(listOf("default"), calls)
        process.processOwned(second)
        assertEquals(listOf("default", "second"), calls)
        process.processOwned(first)
        assertEquals(listOf("default", "second", "first"), calls)
        assertEquals(0L, first.revision)
        assertEquals(0L, second.revision)
        assertTrue(queues.isEmpty)
    }

    @Test
    fun `absolute deadlines dispatch in insertion order with readiness and additions snapshotted`() {
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val owner = queues.createOwner()
        val calls = mutableListOf<String>()
        var openedByAction = false
        owner.add(ready = { clock.cycle >= 110 }) {
            calls += "later deadline inserted first"
            openedByAction = true
            owner.add { calls += "nested" }
        }
        owner.add(ready = { clock.cycle >= 105 }) { calls += "earlier deadline inserted second" }
        owner.add(ready = { openedByAction }) { calls += "opened" }

        repeat(3) { process.processOwned(owner) }
        assertTrue(calls.isEmpty(), "Repeated dispatch cannot substitute for elapsed clock time")
        clock.cycle = 110
        process.processOwned(owner)
        assertEquals(
            listOf("later deadline inserted first", "earlier deadline inserted second"),
            calls,
        )
        assertEquals(2, owner.size)
        process.processOwned(owner)
        assertEquals(
            listOf(
                "later deadline inserted first",
                "earlier deadline inserted second",
                "opened",
                "nested",
            ),
            calls,
        )
        assertTrue(queues.isEmpty)
    }

    @Test
    fun `cancelling a later due callback suppresses it without retaining completed cancellations`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val owner = queues.createOwner()
        val calls = mutableListOf<String>()
        lateinit var self: WorldQueueList.Cancellation
        lateinit var next: WorldQueueList.Cancellation
        self =
            owner.add {
                calls += "first"
                self.cancel()
                next.cancel()
                assertEquals(
                    1,
                    owner.size,
                    "The current and cancelled due nodes are already unlinked",
                )
            }
        next = owner.add { fail("Cancelled due callback ran") }
        owner.add { calls += "last" }

        process.processOwned(owner)
        assertEquals(listOf("first", "last"), calls)
        assertTrue(queues.isEmpty)
        owner.add { calls += "new" }
        self.cancel()
        next.cancel()
        assertEquals(1, owner.size)
        process.processOwned(owner)
        assertEquals(listOf("first", "last", "new"), calls)
        assertEquals(0, owner.size)
    }

    @Test
    fun `clear from a callback retires its due batch and future work but allows a fresh batch`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val owner = queues.createOwner()
        val other = queues.createOwner()
        val calls = mutableListOf<String>()
        owner.add {
            calls += "reset"
            owner.clear()
            assertEquals(1L, owner.revision)
            assertEquals(0, owner.size)
            owner.add { calls += "replacement" }
        }
        owner.add { fail("Cleared due callback ran") }
        owner.add(100, ready = { false }) { fail("Cleared future callback ran") }
        other.add { calls += "other" }
        queues.add(1) { calls += "default" }

        process.processOwned(owner)
        assertEquals(listOf("reset"), calls)
        assertEquals(1, owner.size)
        assertEquals(3, queues.size)
        assertFalse(owner.isClosed)
        process.processOwned(owner)
        process.processOwned(other)
        process.process()
        assertEquals(listOf("reset", "replacement", "other", "default"), calls)
        assertTrue(queues.isEmpty)
        owner.clear()
        assertEquals(2L, owner.revision, "An empty reset still invalidates a captured revision")
    }

    @Test
    fun `close from a callback cancels the rest of its batch and permanently rejects additions`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val owner = queues.createOwner()
        var calls = 0
        owner.add {
            calls++
            owner.close()
        }
        owner.add { fail("Closed due callback ran") }
        owner.add(100) { fail("Closed future callback ran") }

        process.processOwned(owner)
        assertEquals(1, calls)
        assertTrue(owner.isClosed)
        assertEquals(1L, owner.revision)
        assertEquals(0, owner.size)
        assertTrue(queues.isEmpty)
        owner.close()
        owner.clear()
        assertEquals(1L, owner.revision)
        assertThrows(IllegalStateException::class.java) { owner.add { fail("Reopened owner") } }
        assertTrue(queues.isEmpty)
    }

    @Test
    fun `foreign processor rejects an owner before advancing or dispatching its work`() {
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val owner = queues.createOwner()
        val foreign = WorldQueueListProcess(WorldQueueList())
        var calls = 0
        val pending = owner.add(2) { calls++ }

        assertThrows(IllegalArgumentException::class.java) { foreign.processOwned(owner) }
        assertEquals(1, owner.size)
        assertEquals(0, calls)
        process.processOwned(owner)
        assertEquals(0, calls, "Foreign processing must not consume the first countdown tick")
        pending.cancel()
        assertEquals(0, owner.size)
        assertTrue(queues.isEmpty)
        process.processOwned(owner)
        assertEquals(0, calls)
    }

    @Test
    fun `ordinary nested append keeps native tail dependent speedup with owned nodes interspersed`() {
        for (trailingDefault in listOf(false, true)) for (trailingOwned in listOf(false, true)) {
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val owner = queues.createOwner()
            val calls = mutableListOf<String>()
            val case = "trailingDefault=$trailingDefault trailingOwned=$trailingOwned"
            owner.add(1, ready = { fail("Default processing evaluated owned readiness") }) {
                fail("Default processing dispatched owned work")
            }
            queues.add(1) {
                calls += "first"
                queues.add(1) { calls += "nested" }
            }
            if (trailingDefault) {
                owner.add { fail("Default processing dispatched interspersed owned work") }
                queues.add(1) { calls += "tail" }
            }
            if (trailingOwned) {
                owner.add { fail("Default processing dispatched trailing owned work") }
            }

            process.process()
            assertEquals(
                if (trailingDefault) listOf("first", "tail", "nested") else listOf("first"),
                calls,
                case,
            )
            process.process()
            assertEquals(
                if (trailingDefault) listOf("first", "tail", "nested")
                else listOf("first", "nested"),
                calls,
                case,
            )
            assertEquals(
                1 + (if (trailingDefault) 1 else 0) + (if (trailingOwned) 1 else 0),
                owner.size,
                case,
            )
            owner.close()
            assertTrue(queues.isEmpty)
        }
    }
}
