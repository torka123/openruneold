package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Cache-independent timing and native session-cleanup checks for Kephri's timer owners. */
class KephriEncounterQueuesTest {
    @Test
    fun `recovery dispatches one overdue wave then anchors its next interval to actual dispatch`() {
        val session = ownedQueueTestSession()
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val helper = KephriEncounterQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        try {
            var bossPresent = false
            val events = mutableListOf<Int>()
            helper.armRecovery(105, ready = { bossPresent }) {
                events += clock.cycle
                helper.armRecovery(clock.cycle + 4, ready = { bossPresent }) {
                    events += clock.cycle
                    helper.clearRecovery()
                }
            }

            clock.cycle = 112
            helper.processRecovery()
            assertTrue(events.isEmpty(), "Missing source must retain overdue recovery readiness")
            assertEquals(105, helper.recoveryAt)

            bossPresent = true
            helper.processRecovery()
            assertEquals(listOf(112), events)
            assertEquals(116, helper.recoveryAt)
            assertEquals(1, queues.size, "A rearmed batch must wait for the next manager pass")

            clock.cycle = 115
            helper.processRecovery()
            assertEquals(listOf(112), events)
            clock.cycle = 116
            helper.processRecovery()
            assertEquals(listOf(112, 116), events)
            assertEquals(-1, helper.recoveryAt)
        } finally {
            session.resetState()
        }
        assertEquals(0, queues.size)
    }

    @Test
    fun `medic cadence waits for its first interval and rearms from actual dispatch`() {
        val session = ownedQueueTestSession(2)
        val clock = MapClock(200)
        val queues = WorldQueueList()
        val helper = KephriEncounterQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        try {
            var bossPresent = false
            val events = mutableListOf<Int>()
            fun armMedic(deadline: Int) {
                helper.armMedic(deadline, ready = { bossPresent }) {
                    events += clock.cycle
                    if (events.size == 1) armMedic(clock.cycle + 8)
                }
            }
            armMedic(208)

            clock.cycle = 210
            helper.processMedic()
            assertEquals(208, helper.medicAt)
            assertTrue(events.isEmpty())

            bossPresent = true
            helper.processMedic()
            assertEquals(listOf(210), events)
            assertEquals(218, helper.medicAt)
            clock.cycle = 218
            helper.processMedic()
            assertEquals(listOf(210, 218), events)
            assertEquals(-1, helper.medicAt)
        } finally {
            session.resetState()
        }
        assertEquals(0, queues.size)
    }

    @Test
    fun `death completion retains its plus three deadline until eligible and cleanup closes owners`() {
        val session = ownedQueueTestSession(3)
        val clock = MapClock(300)
        val queues = WorldQueueList()
        val helper = KephriEncounterQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        var playerAvailable = false
        val events = mutableListOf<String>()
        helper.armDeath(303, ready = { playerAvailable }) {
            assertEquals(-1, helper.deathAt, "Consume the timer before completing the boss")
            events += "complete"
        }

        clock.cycle = 305
        helper.processDeath()
        assertTrue(events.isEmpty())
        assertEquals(303, helper.deathAt)

        playerAvailable = true
        helper.processDeath()
        assertEquals(listOf("complete"), events)
        assertEquals(-1, helper.deathAt)

        helper.armRecovery(400, ready = { true }) {}
        helper.armMedic(401, ready = { true }) {}
        helper.armDeath(402, ready = { true }) {}
        assertEquals(3, queues.size)
        session.resetState()
        assertTrue(helper.isClosed)
        assertEquals(listOf(-1, -1, -1), listOf(helper.recoveryAt, helper.medicAt, helper.deathAt))
        assertEquals(0, queues.size)
        assertFailsWith<IllegalStateException> { helper.armDeath(500, ready = { true }) {} }
    }

    @Test
    fun `failed due callback retains its deadline unless callback replaces it`() {
        val session = ownedQueueTestSession(4)
        val clock = MapClock(400)
        val queues = WorldQueueList()
        val helper = KephriEncounterQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        try {
            val failure = IllegalStateException("interrupted recovery batch")
            var attempts = 0
            helper.armRecovery(401, ready = { true }) {
                attempts++
                if (attempts == 1) throw failure
                helper.clearRecovery()
            }
            clock.cycle = 405
            assertEquals(failure, assertFailsWith<IllegalStateException> { helper.processRecovery() })
            assertEquals(401, helper.recoveryAt)
            helper.processRecovery()
            assertEquals(2, attempts)
            assertEquals(-1, helper.recoveryAt)
        } finally {
            session.resetState()
        }
        assertEquals(0, queues.size)
    }
}
