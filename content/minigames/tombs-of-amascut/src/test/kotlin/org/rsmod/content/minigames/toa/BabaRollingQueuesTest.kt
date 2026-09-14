package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList

/** Checkpoint 327407463 row selection and native release-before-cast ownership. */
class BabaRollingQueuesTest {
    @Test
    fun `both invocation cadences preserve rows and interleaved shared RNG across rolling thresholds`() {
        for (interval in listOf(8, 4)) {
            for (seed in listOf(7L, 42L)) {
                BabaRollingQueueFixture(seed = seed).use { f ->
                    val oracle = RollingOracle(seed)
                    f.state.startIntro()
                    f.state.startNormalCombat()
                    repeat(2) { threshold ->
                        val started = f.clock.cycle
                        f.state.pendingRollingThresholds += threshold + 1
                        assertEquals(threshold + 1, f.state.beginNextRollingPhase())
                        f.clock.cycle = started + 3
                        var expected = oracle.nextRow(1)
                        assertEquals(expected, f.state.beginRollingSet())
                        val origin = f.phase.encounter.phaseEnteredTick
                        val firstCast = started + 4
                        val beforeCasts = f.casts.size
                        val beforeReleases = f.releases.size
                        f.armSequence(firstCast, interval) { row ->
                            assertEquals(expected, row)
                            if (row.set < 10) {
                                expected = oracle.nextRow(row.set + 1)
                                assertEquals(expected.weakLane, f.state.weakLane)
                            }
                            // Geometry draws share the same stream between cast and release.
                            if (row.set % 3 == 0) {
                                assertEquals(oracle.random.nextInt(17), f.state.nextInt(17))
                            }
                        }
                        val lastRelease = firstCast + interval * 9 + 2
                        for (tick in firstCast - 1..lastRelease) f.dispatch(tick)
                        assertEquals(
                            (0..9).map { firstCast + it * interval },
                            f.casts.drop(beforeCasts).map { it.first },
                        )
                        assertEquals(
                            (0..9).map { firstCast + it * interval + 2 },
                            f.releases.drop(beforeReleases).map { it.first },
                        )
                        assertEquals(
                            f.casts.drop(beforeCasts).map { it.second },
                            f.releases.drop(beforeReleases).map { it.second },
                        )
                        assertEquals(origin, f.phase.encounter.phaseEnteredTick)
                        assertEquals(-1, f.state.nextBoulderAt)
                        assertEquals(-1, f.state.pendingBoulderSpawnAt)
                        assertEquals(0, f.nativeQueues.size)
                        assertEquals(
                            oracle.random.nextInt(Int.MAX_VALUE),
                            f.state.nextInt(Int.MAX_VALUE),
                        )
                        f.state.finishRollingPhase()
                        f.state.startNormalCombat()
                    }
                }
            }
        }
    }

    @Test
    fun `overdue cast waits for release and observes its cleared metadata at the same dispatch boundary`() {
        BabaRollingQueueFixture().use { f ->
            val row = BabaRollingRow(1, 2)
            val trace = mutableListOf<String>()
            f.helper.armRelease(106, row) {
                assertEquals(row, f.helper.pendingRow)
                assertEquals(106, f.state.pendingBoulderSpawnAt)
                trace += "release"
            }
            f.helper.armCast(108, { f.helper.pendingRow == null }) {
                assertNull(f.helper.pendingRow)
                assertEquals(-1, f.state.pendingBoulderSpawnAt)
                trace += "cast"
            }
            f.clock.cycle = 110
            f.process.process()
            assertTrue(trace.isEmpty())
            f.helper.processCast()
            assertTrue(trace.isEmpty(), "An overdue cast remains pending while a row is launching")
            assertEquals(108, f.state.nextBoulderAt)
            trace += "existing-boulders-moved"
            f.helper.processRelease()
            f.helper.processCast()
            assertEquals(listOf("existing-boulders-moved", "release", "cast"), trace)
            assertEquals(-1, f.state.nextBoulderAt)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `rearming during callbacks preserves replacement metadata and defers it to a new native snapshot`() {
        BabaRollingQueueFixture().use { f ->
            val first = BabaRollingRow(1, 2)
            val second = BabaRollingRow(2, 3)
            val trace = mutableListOf<String>()
            f.helper.armRelease(106, first) {
                assertEquals(first, f.helper.pendingRow)
                trace += "first-release"
                f.helper.armRelease(106, second) { trace += "second-release" }
            }
            f.helper.armCast(106, { true }) {
                trace += "first-cast"
                f.helper.armCast(106, { true }) { trace += "second-cast" }
            }
            f.dispatch(106)
            assertEquals(listOf("first-release", "first-cast"), trace)
            assertEquals(second, f.helper.pendingRow)
            assertEquals(106, f.state.pendingBoulderSpawnAt)
            assertEquals(106, f.state.nextBoulderAt)
            f.dispatch(106)
            assertEquals(
                listOf("first-release", "first-cast", "second-release", "second-cast"),
                trace,
            )
            assertNull(f.helper.pendingRow)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `rolling exit and native cleanup cancel pending release and cast without resuming a retired row`() {
        for (ending in
            listOf("return", "next-threshold", "reset", "native-delete", "session-end")) {
            BabaRollingQueueFixture().use { f ->
                f.state.startIntro()
                f.state.startNormalCombat()
                f.state.pendingRollingThresholds += 1
                f.state.beginNextRollingPhase()
                val row = f.state.beginRollingSet()
                f.helper.armRelease(106, row) { fail("Retired release executed: $ending") }
                f.helper.armCast(108, { true }) { fail("Retired cast executed: $ending") }
                when (ending) {
                    "return" -> f.state.finishRollingPhase()
                    "next-threshold" -> {
                        f.state.pendingRollingThresholds += 2
                        f.state.beginNextRollingPhase()
                    }
                    "reset" -> f.state.reset()
                    "native-delete" -> f.phase.registry.remove(f.phase.npc)
                    else -> f.endSession()
                }
                assertEquals(0, f.nativeQueues.size, ending)
                assertEquals(-1, f.state.nextBoulderAt)
                assertEquals(-1, f.state.pendingBoulderSpawnAt)
                assertEquals(0, f.state.pendingBoulderSet)
                assertEquals(-1, f.state.pendingWeakLane)
                f.dispatch(200)
                if (ending in listOf("return", "next-threshold")) assertFalse(f.helper.isClosed)
                else {
                    assertTrue(f.helper.isClosed)
                    assertFailsWith<IllegalStateException> { f.helper.armRelease(201, row) {} }
                    assertEquals(-1, f.helper.releaseAt)
                }
            }
        }
    }

    @Test
    fun `release-triggered early exit cancels a tied cast before it can select another row`() {
        BabaRollingQueueFixture().use { f ->
            f.state.state = BabaState.ROLLING_STONES_PREP
            val row = f.state.beginRollingSet()
            val trace = mutableListOf<String>()
            f.helper.armRelease(106, row) {
                assertEquals(row, f.helper.pendingRow)
                trace += "release"
                f.state.finishRollingPhase()
            }
            f.helper.armCast(106, { true }) { trace += "cancelled-cast" }
            f.dispatch(106)
            assertEquals(listOf("release"), trace)
            assertEquals(BabaState.ROLLING_STONES_RETURN, f.state.state)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `detached ownership consumes no row callback and drops native pending observations`() {
        BabaRollingQueueFixture().use { f ->
            f.helper.armRelease(106, BabaRollingRow(1, 2)) { fail("Detached release") }
            f.helper.armCast(106, { true }) { fail("Detached cast") }
            f.attached = false
            f.dispatch(106)
            assertEquals(-1, f.helper.castAt)
            assertEquals(-1, f.helper.releaseAt)
            assertNull(f.helper.pendingRow)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    /**
     * Literal pre-migration candidate selection, sharing every intervening draw with the oracle.
     */
    private class RollingOracle(seed: Long) {
        val random = Random(seed)
        private var previous = -1

        fun nextRow(set: Int): BabaRollingRow {
            val candidates =
                if (previous < 0) (1..3).toList() else (0..4).filter { abs(it - previous) <= 2 }
            val weak = candidates[random.nextInt(candidates.size)]
            previous = weak
            return BabaRollingRow(set, weak)
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

internal class BabaRollingQueueFixture(id: UUID = UUID(0, 1), seed: Long = 42) : AutoCloseable {
    private val server = ToaNativeCombatFixture()
    private val session = server.start().instance
    val phase = BabaNativePhaseFixture(id, seed)
    val state = phase.state
    val clock = phase.clock
    val nativeQueues = WorldQueueList()
    val process = WorldQueueListProcess(nativeQueues)
    var attached = true
    private val generation = state.token()
    val helper =
        BabaRollingQueues(phase.encounter, session, nativeQueues, process, clock) {
            attached && state.isCurrent(generation)
        }
    val casts = mutableListOf<Pair<Int, BabaRollingRow>>()
    val releases = mutableListOf<Pair<Int, BabaRollingRow>>()

    init {
        state.bindRolling(helper)
    }

    fun armSequence(deadline: Int, interval: Int, afterCast: (BabaRollingRow) -> Unit = {}) {
        helper.armCast(
            deadline,
            { state.state == BabaState.ROLLING_STONES && helper.pendingRow == null },
        ) {
            val row = BabaRollingRow(state.rollingSet, state.weakLane)
            casts += clock.cycle to row
            helper.armRelease(clock.cycle + 2, row) { released ->
                assertEquals(released.set, state.pendingBoulderSet)
                assertEquals(released.weakLane, state.pendingWeakLane)
                releases += clock.cycle to released
            }
            if (state.advanceRollingSetAfterCast(BabaTuning.BOULDER_MAX_SETS.value)) {
                armSequence(clock.cycle + interval, interval, afterCast)
            }
            afterCast(row)
        }
    }

    fun dispatch(tick: Int) {
        clock.cycle = tick
        helper.processRelease()
        helper.processCast()
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
