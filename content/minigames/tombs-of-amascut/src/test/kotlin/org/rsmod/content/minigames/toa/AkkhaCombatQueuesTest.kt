package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList

class AkkhaCombatQueuesTest {
    @Test
    fun `separate owners preserve dispatch position and an overdue style or missing swap actor stays pending`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queue = WorldQueueList()
            val process = WorldQueueListProcess(queue)
            val helper = AkkhaCombatQueues(raid.instance, queue, process, f.clock) { true }
            val trace = mutableListOf<String>()
            var styleEligible = false
            var swapActorsPresent = false
            val due = f.clock.cycle + 1
            helper.armCompletion(due) { trace += "completion" }
            helper.armStyle(due, { styleEligible }) { trace += "style" }
            helper.armSwap(due, { swapActorsPresent }) { trace += "swap" }
            f.advance(1)
            process.process()
            assertTrue(trace.isEmpty(), "Default queue processing does not consume owned work")
            assertTrue(helper.processCompletion())
            assertFalse(helper.processStyle())
            assertFalse(helper.processSwap())
            assertEquals(listOf("completion"), trace)
            assertEquals(-1, helper.completionAt)
            assertEquals(due, helper.styleAt)
            assertEquals(due, helper.swapAt)
            f.advance(20)
            styleEligible = true
            assertTrue(helper.processStyle())
            assertFalse(helper.processSwap())
            swapActorsPresent = true
            trace += "pending-hit"
            assertTrue(helper.processSwap())
            trace += "orb-step"
            assertEquals(listOf("completion", "style", "pending-hit", "swap", "orb-step"), trace)
            assertEquals(-1, helper.styleAt)
            assertEquals(-1, helper.swapAt)
            assertFalse(helper.processSwap())
            assertFalse(helper.processCompletion())
            assertEquals(0, queue.size)
        }
    }

    @Test
    fun `rearm replaces earlier callbacks and generation reset and native session cleanup cancel them`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queue = WorldQueueList()
            val process = WorldQueueListProcess(queue)
            var attached = true
            val helper = AkkhaCombatQueues(raid.instance, queue, process, f.clock) { attached }
            val trace = mutableListOf<String>()
            helper.armCompletion(101) { trace += "stale" }
            helper.armCompletion(102) {
                trace += "current"
                helper.armCompletion(102) { trace += "nested" }
            }
            f.advance(1)
            assertFalse(helper.processCompletion())
            f.advance(1)
            assertTrue(helper.processCompletion())
            assertEquals(listOf("current"), trace, "New callbacks defer to the next owned snapshot")
            assertTrue(helper.processCompletion())
            assertEquals(listOf("current", "nested"), trace)
            helper.armSwap(103, { true }) { trace += "old-swap" }
            helper.clearSwap()
            helper.armStyle(103, { true }) { trace += "wrong-generation" }
            attached = false
            f.advance(1)
            assertFalse(helper.processStyle())
            assertFalse(helper.processSwap())
            assertEquals(listOf("current", "nested"), trace)
            attached = true
            helper.armCompletion(104) { trace += "ended-session" }
            helper.armStyle(104, { true }) { trace += "ended-style" }
            helper.armSwap(104, { true }) { trace += "ended-swap" }
            f.instances.terminate(raid.instance)
            f.advance(1)
            assertFalse(helper.processCompletion())
            assertFalse(helper.processStyle())
            assertFalse(helper.processSwap())
            assertEquals(0, queue.size)
            assertEquals(-1, helper.completionAt)
            assertEquals(-1, helper.styleAt)
            assertEquals(-1, helper.swapAt)
            assertEquals(listOf("current", "nested"), trace)
        }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
