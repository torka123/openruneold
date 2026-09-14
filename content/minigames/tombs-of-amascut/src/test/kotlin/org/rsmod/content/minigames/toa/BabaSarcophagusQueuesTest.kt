package org.rsmod.content.minigames.toa

import kotlin.random.Random
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/**
 * Checkpoint db2b4f3f3 runner/energy boundaries using the existing native owned queue processor.
 */
class BabaSarcophagusQueuesTest {
    @Test
    fun `runner waits for eligible closed targets and replacement retains the full eight tick delay`() {
        Fixture().use { f ->
            var liveBaboon = false
            var closedTarget = false
            val selected = mutableListOf<Int>()
            val eligible = { liveBaboon && closedTarget }
            val launch = {
                assertEquals(-1, f.helper.runnerAt, "Assignment clears before directing the runner")
                selected += f.clock.cycle
                Unit
            }
            f.helper.armRunner(108, eligible, launch)
            f.clock.cycle = 108
            f.process.process()
            assertEquals(108, f.helper.runnerAt)
            f.helper.processRunner()
            assertTrue(selected.isEmpty())
            liveBaboon = true
            f.clock.cycle = 110
            f.helper.processRunner()
            assertEquals(108, f.helper.runnerAt)
            closedTarget = true
            f.helper.processRunner()
            assertEquals(listOf(110), selected)
            assertEquals(0, f.nativeQueues.size)
            // Dead-runner reconciliation/new discharge replaces the old delay before dispatch.
            f.helper.armRunner(118, eligible, launch)
            f.clock.cycle = 117
            f.helper.armRunner(125, eligible, launch)
            f.clock.cycle = 118
            f.helper.processRunner()
            assertEquals(listOf(110), selected)
            f.clock.cycle = 125
            f.helper.processRunner()
            assertEquals(listOf(110, 125), selected)
            assertEquals(-1, f.helper.runnerAt)
        }
    }

    @Test
    fun `room order controls tied energy launches despite reversed opening order and mutable runtime keys`() {
        Fixture().use { f ->
            val first = sarc(1)
            val second = sarc(2)
            val equalFirst = first.copy()
            assertEquals(first, equalFirst)
            val calls = mutableListOf<BabaSarcophagusRuntime>()
            for (runtime in listOf(second, equalFirst, first)) {
                f.helper.armEnergy(runtime, 103, { true }) {
                    assertEquals(103, f.helper.energyAt(runtime))
                    calls += runtime
                }
            }
            first.hitpoints = 0
            first.state = BabaSarcophagusState.OPEN
            assertEquals(103, f.helper.energyAt(first))
            assertEquals(103, f.helper.energyAt(equalFirst), "Equal values have distinct owners")
            f.clock.cycle = 103
            f.process.process()
            assertTrue(calls.isEmpty())
            for (runtime in listOf(first, second, equalFirst)) f.helper.processEnergy(runtime)
            assertEquals(3, calls.size)
            assertSame(first, calls[0])
            assertSame(second, calls[1])
            assertSame(equalFirst, calls[2])
            assertEquals(-1, f.helper.energyAt(first))
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `opening plus three and both successful energy intervals preserve overdue no target RNG`() {
        for (interval in listOf(6, 4)) {
            Fixture().use { f ->
                val sarc = sarc(1)
                var hasTarget = false
                val random = Random(42)
                val oracle = Random(42)
                val launches = mutableListOf<Pair<Int, Int>>()
                fun arm(deadline: Int) {
                    f.helper.armEnergy(sarc, deadline, { hasTarget }) {
                        assertEquals(deadline, f.helper.energyAt(sarc))
                        launches += f.clock.cycle to random.nextInt(3)
                        arm(f.clock.cycle + interval)
                    }
                }
                arm(103)
                f.clock.cycle = 102
                f.helper.processEnergy(sarc)
                f.clock.cycle = 120
                f.process.process()
                f.helper.processEnergy(sarc)
                assertEquals(103, f.helper.energyAt(sarc))
                assertTrue(launches.isEmpty())
                hasTarget = true
                f.helper.processEnergy(sarc)
                assertEquals(listOf(120 to oracle.nextInt(3)), launches)
                assertEquals(120 + interval, f.helper.energyAt(sarc))
                f.clock.cycle = 120 + interval - 1
                f.helper.processEnergy(sarc)
                assertEquals(1, launches.size)
                f.clock.cycle++
                f.helper.processEnergy(sarc)
                assertEquals(120 + interval to oracle.nextInt(3), launches.last())
                assertEquals(120 + interval * 2, f.helper.energyAt(sarc))
                assertEquals(oracle.nextInt(10_003), random.nextInt(10_003))
            }
        }
    }

    @Test
    fun `callback rearm survives the previous dispatch and cancellation suppresses a later room actor`() {
        Fixture().use { f ->
            val first = sarc(1)
            val second = sarc(2)
            val trace = mutableListOf<String>()
            f.helper.armRunner(103, { true }) {
                trace += "runner"
                f.helper.armRunner(103, { true }) { trace += "replacement-runner" }
            }
            f.helper.armEnergy(first, 103, { true }) {
                trace += "first"
                f.helper.clearEnergy(second)
                f.helper.armEnergy(first, 103, { true }) { trace += "replacement-energy" }
            }
            f.helper.armEnergy(second, 103, { true }) { fail("Canceled room actor fired") }
            f.clock.cycle = 103
            f.helper.processRunner()
            trace += "baboon-actions"
            f.helper.processEnergy(first)
            f.helper.processEnergy(second)
            assertEquals(listOf("runner", "baboon-actions", "first"), trace)
            assertEquals(103, f.helper.runnerAt)
            assertEquals(103, f.helper.energyAt(first))
            assertEquals(-1, f.helper.energyAt(second))
            f.helper.processRunner()
            f.helper.processEnergy(first)
            assertEquals("replacement-runner", trace[3])
            assertEquals("replacement-energy", trace[4])
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `failed energy retains its due deadline while explicit replacement or cleanup wins`() {
        for (replacement in listOf("retry", "rearm", "clear", "close")) {
            Fixture().use { f ->
                val sarc = sarc(1)
                var calls = 0
                f.helper.armEnergy(sarc, 103, { true }) {
                    calls++
                    if (calls == 1) {
                        when (replacement) {
                            "rearm" -> f.helper.armEnergy(sarc, 110, { true }) { calls++ }
                            "clear" -> f.helper.clearEnergy(sarc)
                            "close" -> f.helper.close()
                        }
                        throw IllegalStateException("Payload failed")
                    }
                }
                f.clock.cycle = 103
                assertFailsWith<IllegalStateException>(replacement) { f.helper.processEnergy(sarc) }
                assertEquals(
                    when (replacement) {
                        "retry" -> 103
                        "rearm" -> 110
                        else -> -1
                    },
                    f.helper.energyAt(sarc),
                    replacement,
                )
                f.clock.cycle = 110
                f.helper.processEnergy(sarc)
                assertEquals(if (replacement in listOf("retry", "rearm")) 2 else 1, calls)
                assertEquals(0, f.nativeQueues.size)
            }
        }
    }

    @Test
    fun `native cleanup closes every runner and energy owner while clear permits fresh reuse`() {
        for (ending in listOf("clear", "encounter", "session", "detach")) {
            Fixture().use { f ->
                val first = sarc(1)
                val second = sarc(2)
                f.helper.armRunner(108, { true }) { fail("Retired runner: $ending") }
                f.helper.armEnergy(first, 103, { true }) { fail("Retired energy: $ending") }
                f.helper.armEnergy(second, 106, { true }) { fail("Retired energy: $ending") }
                when (ending) {
                    "clear" -> f.helper.clear()
                    "encounter" -> f.phase.registry.remove(f.phase.npc)
                    "session" -> f.endSession()
                    "detach" -> f.attached = false
                }
                f.clock.cycle = 120
                f.process.process()
                f.helper.processRunner()
                f.helper.processEnergy(first)
                f.helper.processEnergy(second)
                assertEquals(0, f.nativeQueues.size, ending)
                assertEquals(-1, f.helper.runnerAt, ending)
                assertEquals(-1, f.helper.energyAt(first), ending)
                assertEquals(-1, f.helper.energyAt(second), ending)
                if (ending in listOf("encounter", "session")) {
                    assertTrue(f.helper.isClosed, ending)
                    assertFailsWith<IllegalStateException> { f.helper.armRunner(121, { true }) {} }
                    assertFailsWith<IllegalStateException> {
                        f.helper.armEnergy(first, 121, { true }) {}
                    }
                } else if (ending == "clear") {
                    var calls = 0
                    f.helper.armRunner(120, { true }) { calls++ }
                    f.helper.armEnergy(first, 120, { true }) { calls++ }
                    f.helper.processRunner()
                    f.helper.processEnergy(first)
                    assertEquals(2, calls)
                    assertFalse(f.helper.isClosed)
                }
            }
        }
    }

    private fun sarc(x: Int) = BabaSarcophagusRuntime(CoordGrid(3500 + x, 9500))

    private class Fixture : AutoCloseable {
        private val server = ToaNativeCombatFixture()
        private val session = server.start().instance
        val phase = BabaNativePhaseFixture()
        val clock = phase.clock
        val nativeQueues = WorldQueueList()
        val process = WorldQueueListProcess(nativeQueues)
        var attached = true
        val helper =
            BabaSarcophagusQueues(phase.encounter, session, nativeQueues, process, clock) {
                attached
            }

        fun endSession() = server.instances.terminate(session)

        override fun close() {
            try {
                helper.close()
                phase.registry.remove(phase.npc)
                server.close()
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
