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

    @Test
    fun `memory warnings hide and reappear retain exact deadlines before same tick completion`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queue = WorldQueueList()
            val process = WorldQueueListProcess(queue)
            val helper = AkkhaCombatQueues(raid.instance, queue, process, f.clock) { true }
            raid.akkha.combatQueues = helper
            val steps = MemoryBlastController.schedule(100, 0, 4, 4) { true }
            val hideAt = 100 + AkkhaTuning.MEMORY_HIDE_DELAY.value
            val reappearAt = steps.maxOf { it.resolvesAt } + AkkhaTuning.MEMORY_REAPPEAR_DELAY.value
            val trace = mutableListOf<Pair<String, Int>>()
            helper.armVisibility(
                hideAt,
                reappearAt,
                warn = { trace += "warn" to f.clock.cycle },
                disappear = { trace += "disappear" to f.clock.cycle },
                hide = {
                    raid.akkha.memoryHidden = true
                    trace += "hide" to f.clock.cycle
                },
                reappear = {
                    raid.akkha.memoryHidden = false
                    trace += "reappear" to f.clock.cycle
                },
            )
            helper.armCompletion(reappearAt) {
                trace += "completion" to f.clock.cycle
                raid.akkha.clearMemoryLifecycle()
            }
            assertEquals(104, raid.akkha.memoryHideAt)
            assertEquals(126, raid.akkha.memoryReappearAt)
            f.advance(1)
            assertFalse(helper.processVisibility())
            for (tick in listOf(102, 103, 104)) {
                f.advance(tick - f.clock.cycle)
                process.process()
                assertEquals(tick - 102, trace.size)
                assertTrue(helper.processVisibility())
                assertFalse(helper.processVisibility(), "Duplicate dispatch cannot replay $tick")
            }
            assertTrue(raid.akkha.memoryHidden)
            assertEquals(-1, raid.akkha.memoryHideAt)
            assertEquals(126, raid.akkha.memoryReappearAt)
            f.advance(125 - f.clock.cycle)
            assertFalse(helper.processVisibility())
            assertFalse(helper.processCompletion())
            f.advance(1)
            assertTrue(helper.processVisibility())
            assertFalse(raid.akkha.memoryHidden)
            assertTrue(helper.processCompletion())
            assertEquals(
                listOf(
                    "warn" to 102,
                    "disappear" to 103,
                    "hide" to 104,
                    "reappear" to 126,
                    "completion" to 126,
                ),
                trace,
            )
            assertEquals(-1, raid.akkha.memoryReappearAt)
            assertEquals(0, queue.size)
        }
    }

    @Test
    fun `overdue visibility drops old warnings and retains hide first ordering with completion cancellation`() {
        for (completeAfterHide in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val raid = f.start()
                val queue = WorldQueueList()
                val helper =
                    AkkhaCombatQueues(raid.instance, queue, WorldQueueListProcess(queue), f.clock) {
                        true
                    }
                raid.akkha.combatQueues = helper
                val trace = mutableListOf<String>()
                helper.armVisibility(
                    104,
                    126,
                    { trace += "warn" },
                    { trace += "disappear" },
                    { trace += "hide" },
                    { trace += "reappear" },
                )
                if (completeAfterHide) {
                    helper.armCompletion(126) {
                        trace += "completion"
                        raid.akkha.clearMemoryLifecycle()
                    }
                }
                f.advance(30)
                assertTrue(helper.processVisibility())
                assertEquals(listOf("hide"), trace, "Expired warnings do not delay an overdue hide")
                assertEquals(-1, helper.visibilityHideAt)
                assertEquals(126, helper.visibilityReappearAt)
                if (completeAfterHide) assertTrue(helper.processCompletion())
                f.advance(1)
                assertEquals(!completeAfterHide, helper.processVisibility())
                assertEquals(
                    if (completeAfterHide) listOf("hide", "completion")
                    else listOf("hide", "reappear"),
                    trace,
                    "Retain the existing hide-first edge; completion cancels pending reappearance",
                )
                assertEquals(-1, helper.visibilityReappearAt)
                assertEquals(0, queue.size)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `visibility rearm callback cleanup reset and native session termination cancel every pending edge`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queue = WorldQueueList()
            val helper =
                AkkhaCombatQueues(raid.instance, queue, WorldQueueListProcess(queue), f.clock) {
                    true
                }
            val trace = mutableListOf<String>()
            fun arm(prefix: String, hideAt: Int, reappearAt: Int, clearOnHide: Boolean = false) {
                helper.armVisibility(
                    hideAt,
                    reappearAt,
                    { trace += "$prefix-warn" },
                    { trace += "$prefix-disappear" },
                    {
                        trace += "$prefix-hide"
                        if (clearOnHide) helper.clearVisibility()
                    },
                    { trace += "$prefix-reappear" },
                )
            }
            arm("old", 104, 126)
            arm("new", 106, 128, clearOnHide = true)
            f.advance(6)
            assertTrue(helper.processVisibility())
            assertEquals(listOf("new-hide"), trace)
            assertEquals(-1, helper.visibilityReappearAt)
            assertEquals(0, queue.size, "Cleanup during hide cancels its newly queued reappearance")
            arm("reset", 110, 132)
            raid.akkha.combatQueues = helper
            raid.akkha.reset()
            f.advance(30)
            assertFalse(helper.processVisibility())
            assertEquals(0, queue.size)
            assertEquals(null, raid.akkha.combatQueues)
            assertEquals(-1, raid.akkha.memoryHideAt)

            val fresh =
                AkkhaCombatQueues(raid.instance, queue, WorldQueueListProcess(queue), f.clock) {
                    true
                }
            fresh.armVisibility(
                140,
                160,
                { trace += "ended-warn" },
                {},
                {},
                { trace += "ended-reappear" },
            )
            f.instances.terminate(raid.instance)
            f.advance(30)
            assertFalse(fresh.processVisibility())
            assertEquals(-1, fresh.visibilityHideAt)
            assertEquals(-1, fresh.visibilityReappearAt)
            assertEquals(0, queue.size)
            assertEquals(listOf("new-hide"), trace)
        }
    }

    @Test
    fun `anchored final intro completion chain advances one native snapshot per stage even when overdue`() {
        for (overdue in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val raid = f.start()
                val queue = WorldQueueList()
                val process = WorldQueueListProcess(queue)
                val helper = AkkhaCombatQueues(raid.instance, queue, process, f.clock) { true }
                val trace = mutableListOf<Pair<Int, Int>>()
                val startedAt = f.clock.cycle
                val offsets = AkkhaTuning.FINAL_STAND_STAGE_OFFSETS.value
                fun arm(stage: Int) {
                    helper.armCompletion(startedAt + offsets[stage]) {
                        trace += stage to f.clock.cycle
                        if (stage < offsets.lastIndex) arm(stage + 1)
                    }
                }
                assertEquals(listOf(0, 2, 4, 5), offsets)
                arm(1)
                if (overdue) f.advance(10)
                else {
                    f.advance(1)
                    assertFalse(helper.processCompletion())
                    f.advance(1)
                }
                process.process()
                assertTrue(trace.isEmpty())
                for (stage in 1..3) {
                    if (stage > 1) {
                        val next = if (overdue) f.clock.cycle + 1 else startedAt + offsets[stage]
                        f.advance(next - f.clock.cycle)
                    }
                    assertTrue(helper.processCompletion())
                    assertEquals(stage, trace.size)
                    assertEquals(
                        if (stage == 3) -1 else startedAt + offsets[stage + 1],
                        helper.completionAt,
                    )
                }
                assertEquals(
                    if (overdue) listOf(1 to 110, 2 to 111, 3 to 112)
                    else listOf(1 to 102, 2 to 104, 3 to 105),
                    trace,
                )
                assertFalse(helper.processCompletion())
                assertEquals(0, queue.size)
            }
            framework().clearInlineMocks()
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
