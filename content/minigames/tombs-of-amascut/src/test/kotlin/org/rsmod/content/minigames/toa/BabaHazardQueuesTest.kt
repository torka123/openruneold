package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/** Checkpoint 15166e776 telegraph order, native cancellation and retained UID rebinding. */
class BabaHazardQueuesTest {
    @Test
    fun `tied hazards dispatch at separate old boundaries with insertion order and rubble reservations`() {
        BabaHazardQueueFixture().use { f ->
            val trace = mutableListOf<String>()
            var coverLanded = false
            val tile = CoordGrid(3500, 9500)
            f.helper.queueShockwave(shockwave(106)) {
                assertTrue(coverLanded)
                trace += "shockwave"
            }
            f.helper.queueRubble(BabaPendingRubble(tile, 106, 0)) {
                assertTrue(f.state.pendingRubble.isEmpty(), "Reservation retires before resolution")
                coverLanded = true
                trace += "rubble"
            }
            repeat(2) { index ->
                f.helper.queueRock(rock(PlayerUid(1), 106)) {
                    assertFalse(coverLanded, "Rocks see cover before this cycle's rubble lands")
                    assertEquals(listOf(tile), f.state.pendingRubble.map { it.tile })
                    trace += "rock-$index"
                }
            }
            f.clock.cycle = 105
            f.processAll()
            assertTrue(trace.isEmpty())
            f.clock.cycle = 106
            f.process.process()
            assertTrue(trace.isEmpty(), "Default world processing cannot advance owned hazards")
            f.helper.processRocks()
            assertEquals(listOf("rock-0", "rock-1"), trace)
            f.helper.processRubble()
            trace += "between-rubble-and-shockwaves"
            f.helper.processShockwaves()
            assertEquals(
                listOf("rock-0", "rock-1", "rubble", "between-rubble-and-shockwaves", "shockwave"),
                trace,
            )
            f.processAll()
            assertEquals(5, trace.size)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `overdue rocks retain registration order rather than sorting their deadlines`() {
        BabaHazardQueueFixture().use { f ->
            val trace = mutableListOf<Int>()
            f.helper.queueRock(rock(PlayerUid(1), 112)) { trace += it.landsAt }
            f.helper.queueRock(rock(PlayerUid(1), 106)) { trace += it.landsAt }
            f.clock.cycle = 120
            f.helper.processRocks()
            assertEquals(listOf(112, 106), trace)
            assertTrue(f.state.pendingRocks.isEmpty())
        }
    }

    @Test
    fun `rebound rock callbacks receive the current UID and retirement cancels only that player's entries`() {
        BabaHazardQueueFixture().use { f ->
            val oldUid = PlayerUid(1)
            val newUid = PlayerUid(3)
            val departing = PlayerUid(2)
            val resolved = mutableListOf<BabaPendingRock>()
            val original = rock(oldUid, 106)
            f.helper.queueRock(original) { resolved += it }
            f.helper.queueRock(original) { resolved += it }
            f.helper.queueRock(rock(departing, 106)) { resolved += it }
            val snapshot = f.state.pendingRocks
            f.state.rebindPlayer(oldUid, newUid)
            f.state.retirePlayer(departing)
            assertEquals(listOf(oldUid, oldUid, departing), snapshot.map { it.target })
            assertEquals(listOf(newUid, newUid), f.state.pendingRocks.map { it.target })
            assertEquals(2, f.nativeQueues.size)
            f.clock.cycle = 106
            f.helper.processRocks()
            assertEquals(
                listOf(original.copy(target = newUid), original.copy(target = newUid)),
                resolved,
            )
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `native clear cancels the remaining due batch and nested telegraphs wait for the next snapshot`() {
        BabaHazardQueueFixture().use { f ->
            val trace = mutableListOf<String>()
            f.helper.queueRock(rock(PlayerUid(1), 106)) {
                trace += "first"
                f.helper.clear()
                f.helper.queueRock(rock(PlayerUid(1), 106)) { trace += "nested" }
            }
            f.helper.queueRock(rock(PlayerUid(2), 106)) { trace += "cancelled-rock" }
            f.helper.queueRubble(BabaPendingRubble(CoordGrid(3500, 9500), 106, 0)) {
                trace += "cancelled-rubble"
            }
            f.helper.queueShockwave(shockwave(106)) { trace += "cancelled-shockwave" }
            f.clock.cycle = 106
            f.processAll()
            assertEquals(listOf("first"), trace)
            assertEquals(1, f.nativeQueues.size)
            assertEquals(1, f.state.pendingRocks.size)
            assertTrue(f.state.pendingRubble.isEmpty())
            assertTrue(f.state.pendingShockwaves.isEmpty())
            f.helper.processRocks()
            assertEquals(listOf("first", "nested"), trace)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `phase transitions preserve hazards and native deletion session termination or reset closes every family`() {
        for (ending in listOf("native-delete", "session-end", "reset")) {
            BabaHazardQueueFixture().use { f ->
                f.state.startIntro()
                f.state.startNormalCombat()
                f.helper.queueRock(rock(PlayerUid(1), 106)) { fail("Retired rock executed") }
                f.helper.queueRubble(BabaPendingRubble(CoordGrid(3500, 9500), 106, 0)) {
                    fail("Retired rubble executed")
                }
                f.helper.queueShockwave(shockwave(106)) { fail("Retired shockwave executed") }
                f.state.state = BabaState.SHOCKWAVE
                f.state.startNormalCombat()
                assertEquals(3, f.nativeQueues.size, "Phase changes retain independent hazards")
                when (ending) {
                    "native-delete" -> f.phase.registry.remove(f.phase.npc)
                    "session-end" -> f.endSession()
                    else -> f.state.reset()
                }
                assertTrue(f.helper.isClosed, ending)
                assertEquals(0, f.nativeQueues.size, ending)
                assertTrue(f.state.pendingRocks.isEmpty())
                assertTrue(f.state.pendingRubble.isEmpty())
                assertTrue(f.state.pendingShockwaves.isEmpty())
                f.clock.cycle = 200
                f.processAll()
                assertFailsWith<IllegalStateException> {
                    f.helper.queueRock(rock(PlayerUid(1), 200)) {}
                }
            }
        }
    }

    @Test
    fun `retirement within a due batch suppresses later rocks and detached work releases reservations`() {
        BabaHazardQueueFixture().use { f ->
            val departing = PlayerUid(2)
            val trace = mutableListOf<String>()
            f.helper.queueRock(rock(PlayerUid(1), 106)) {
                trace += "first"
                f.state.retirePlayer(departing)
            }
            f.helper.queueRock(rock(departing, 106)) { trace += "departed" }
            f.clock.cycle = 106
            f.helper.processRocks()
            assertEquals(listOf("first"), trace)
            f.attached = false
            f.helper.queueRubble(BabaPendingRubble(CoordGrid(3500, 9500), 106, 0)) {
                trace += "detached-rubble"
            }
            f.helper.queueShockwave(shockwave(106)) { trace += "detached-shockwave" }
            f.processAll()
            assertEquals(listOf("first"), trace)
            assertTrue(f.state.pendingRubble.isEmpty())
            assertTrue(f.state.pendingShockwaves.isEmpty())
            assertEquals(0, f.nativeQueues.size)
        }
    }

    private fun rock(uid: PlayerUid, landsAt: Int) =
        BabaPendingRock(uid, CoordGrid(3500, 9500), landsAt, 30, BabaAssets.EXPLOSION, true)

    private fun shockwave(landsAt: Int) =
        BabaPendingShockwave(setOf(CoordGrid(3501, 9500)), emptySet(), landsAt, 35)

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}

/** Native queue processor, combat owner and InstanceManager termination without spatial stubs. */
internal class BabaHazardQueueFixture(id: UUID = UUID(0, 1), seed: Long = 42) : AutoCloseable {
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
        BabaHazardQueues(phase.encounter, session, nativeQueues, process, clock) {
            attached && state.isCurrent(generation)
        }

    init {
        state.bindHazards(helper)
    }

    fun processAll() {
        helper.processRocks()
        helper.processRubble()
        helper.processShockwaves()
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
