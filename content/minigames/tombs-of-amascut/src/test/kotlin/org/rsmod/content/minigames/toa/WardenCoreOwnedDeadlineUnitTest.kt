package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Cache-independent coverage for the detached-body Wardens P3 handoff owner. */
class WardenCoreOwnedDeadlineUnitTest {
    @Test
    fun `phase three handoff uses its retained boundary and callback replacement wins`() {
        val session = ownedQueueTestSession(5)
        val clock = MapClock(500)
        val queues = WorldQueueList()
        val helper = WardenCoreQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        val events = mutableListOf<String>()
        try {
            helper.armPhaseThree(503) {
                assertEquals(503, helper.phaseThreeAt)
                events += "first-handoff"
                helper.armPhaseThree(509) { events += "replacement-handoff" }
            }

            clock.cycle = 507
            helper.processPhaseThree()
            assertEquals(listOf("first-handoff"), events)
            assertEquals(509, helper.phaseThreeAt)
            assertEquals(1, queues.size)

            clock.cycle = 509
            helper.processPhaseThree()
            assertEquals(listOf("first-handoff", "replacement-handoff"), events)
            assertEquals(-1, helper.phaseThreeAt)

            helper.armPhaseThree(520) { error("Session cleanup must suppress the handoff") }
            session.resetState()
            assertTrue(helper.isClosed)
            assertEquals(-1, helper.phaseThreeAt)
            assertEquals(0, queues.size)
        } finally {
            session.resetState()
        }
    }
}
