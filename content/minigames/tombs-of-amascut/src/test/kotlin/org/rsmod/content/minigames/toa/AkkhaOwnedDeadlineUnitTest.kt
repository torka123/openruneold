package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceId
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Queue-owner timing/order tests do not require cache initialization or gameval data. */
class AkkhaOwnedDeadlineUnitTest {
    @Test
    fun `shadow charge retries overdue readiness and blast recharge use distinct owners`() {
        val session = ownedQueueTestSession()
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val helper = AkkhaPayloadQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        try {
            val events = mutableListOf<String>()
            var chargeReady = false
            helper.armShadowCharge(clock.cycle + 1, ready = { chargeReady }) {
                events += "charge"
                helper.armShadowBlast(clock.cycle + 2, ready = { true }) {
                    events += "blast"
                    helper.armShadowCharge(clock.cycle + 3, ready = { true }) {
                        events += "recharge"
                    }
                }
            }

            clock.cycle += 4
            helper.processShadowCharge()
            assertTrue(events.isEmpty())
            assertEquals(101, helper.shadowChargeAt)
            assertEquals(1, queues.size)

            chargeReady = true
            helper.processShadowCharge()
            assertEquals(listOf("charge"), events)
            assertEquals(-1, helper.shadowChargeAt)
            assertEquals(clock.cycle + 2, helper.shadowBlastAt)
            helper.processShadowBlast()
            assertEquals(listOf("charge"), events)

            clock.cycle += 2
            helper.processShadowBlast()
            assertEquals(listOf("charge", "blast"), events)
            assertEquals(clock.cycle + 3, helper.shadowChargeAt)

            clock.cycle += 3
            helper.processShadowCharge()
            assertEquals(listOf("charge", "blast", "recharge"), events)
            assertEquals(-1, helper.shadowChargeAt)
        } finally {
            session.resetState()
        }
        assertEquals(0, queues.size)
    }

    @Test
    fun `trail window precedes ordered orb passes and status dispatch clears its deadline`() {
        val session = ownedQueueTestSession()
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val helper = AkkhaPayloadQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        try {
            val events = mutableListOf<String>()
            var hasOrbs = true
            helper.armTrailWindow(
                startsAt = 102,
                endsAt = 104,
                onStart = { events += "trail-start" },
                onEnd = { events += "trail-end" },
            )
            helper.armStatusPass(101) {
                assertEquals(-1, helper.statusAt)
                events += "status"
            }

            fun processOrbs() =
                helper.processTrailOrbs(
                    hasOrbs = { hasOrbs },
                    ready = { clock.cycle >= 102 },
                    dispatch = { events += "orb-pass" },
                )

            processOrbs()
            clock.cycle = 101
            helper.processTrailWindow()
            helper.processStatusPass()
            processOrbs()
            assertEquals(listOf("status"), events)

            clock.cycle = 102
            helper.processTrailWindow()
            processOrbs()
            assertEquals(listOf("status", "trail-start", "orb-pass"), events)
            hasOrbs = false
            processOrbs()

            clock.cycle = 104
            helper.processTrailWindow()
            assertEquals(listOf("status", "trail-start", "orb-pass", "trail-end"), events)
            assertEquals(-1, helper.trailStartsAt)
            assertEquals(-1, helper.trailEndsAt)
        } finally {
            session.resetState()
        }
        assertEquals(0, queues.size)
    }

    @Test
    fun `Warden core owner preserves paired ejection order and return deadline`() {
        val session = ownedQueueTestSession(2)
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val helper = WardenCoreQueues(session, queues, WorldQueueListProcess(queues), clock) { true }
        try {
            val events = mutableListOf<String>()
            helper.armCore(
                projectileAt = 105,
                spawnAt = 107,
                ready = { true },
                launchProjectile = { events += "projectile" },
                spawnCore = {
                    events += "spawn"
                    helper.armCoreReturn(clock.cycle + 21, { true }) { events += "return" }
                },
            )
            clock.cycle = 107
            helper.processCoreEjection()
            assertEquals(listOf("projectile", "spawn"), events)
            assertEquals(128, helper.returnAt)
            clock.cycle = 128
            helper.processCoreReturn()
            assertEquals(listOf("projectile", "spawn", "return"), events)
        } finally {
            session.resetState()
        }
        assertEquals(0, queues.size)
    }

    @Test
    fun `raid reset owner retains registration order while replacing one deadline in place`() {
        val first = ownedQueueTestSession(3)
        val second = ownedQueueTestSession(4)
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val events = mutableListOf<InstanceId>()
        lateinit var helper: ToaResetQueues
        helper =
            ToaResetQueues(
                queues,
                WorldQueueListProcess(queues),
                clock,
                canDispatch = { true },
                reset = { id ->
                    assertFalse(id in helper.pending, "Remove reset metadata before callback")
                    events += id
                },
            )
        try {
            helper.schedule(first, 101)
            helper.schedule(second, 101)
            helper.schedule(first, 104)
            assertEquals(listOf(first.id, second.id), helper.pending.keys.toList())
            assertEquals(2, queues.size)
            clock.cycle = 104
            helper.process()
            assertEquals(listOf(first.id, second.id), events)
            assertTrue(helper.pending.isEmpty())
        } finally {
            helper.close()
            first.resetState()
            second.resetState()
        }
        assertEquals(0, queues.size)
    }
}
