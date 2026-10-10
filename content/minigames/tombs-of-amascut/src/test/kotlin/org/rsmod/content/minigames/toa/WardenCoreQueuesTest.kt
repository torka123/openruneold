package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList

/** Native owner semantics at the three retained core presentation boundaries. */
class WardenCoreQueuesTest {
    @Test
    fun `ejection keeps callback deadlines and overdue pair order while exposure waits for its own slot`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val helper = WardenCoreQueues(raid.instance, queues, process, f.clock) { true }
            val trace = mutableListOf<String>()
            helper.armCore(
                105,
                107,
                { true },
                { trace += "projectile:${helper.projectileAt}:${helper.spawnAt}" },
                {
                    trace += "spawn:${helper.projectileAt}:${helper.spawnAt}"
                    helper.armCoreReturn(f.clock.cycle + 21, { true }) { trace += "return" }
                },
            )
            f.advance(4)
            helper.processCoreEjection()
            assertTrue(trace.isEmpty())
            f.advance(6)
            process.process()
            assertTrue(trace.isEmpty(), "Default processing skips every owned entry")
            helper.processCoreEjection()
            assertEquals(listOf("projectile:105:107", "spawn:-1:107"), trace)
            assertEquals(-1, helper.projectileAt)
            assertEquals(-1, helper.spawnAt)
            assertEquals(
                131,
                helper.returnAt,
                "Exposure starts at actual spawn, not scheduled spawn",
            )
            helper.processCoreEjection()
            assertEquals(2, trace.size)
            f.advance(21)
            helper.processCoreEjection()
            assertEquals(2, trace.size, "Ejection dispatch cannot consume the later return slot")
            helper.processCoreReturn()
            assertEquals(listOf("projectile:105:107", "spawn:-1:107", "return"), trace)
            assertEquals(-1, helper.returnAt)
            helper.processCoreReturn()
            assertEquals(3, trace.size)
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `missing body and missing core retain overdue readiness without consuming deadlines`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val helper =
                WardenCoreQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    true
                }
            var body = false
            var core = false
            val trace = mutableListOf<String>()
            helper.armCore(105, 107, { body }, { trace += "projectile" }, { trace += "spawn" })
            helper.armCoreReturn(121, { core }) {
                assertEquals(-1, helper.returnAt)
                trace += "return"
            }
            f.advance(25)
            helper.processCoreEjection()
            helper.processCoreReturn()
            assertTrue(trace.isEmpty())
            assertEquals(105, helper.projectileAt)
            assertEquals(107, helper.spawnAt)
            assertEquals(121, helper.returnAt)
            body = true
            helper.processCoreEjection()
            assertEquals(listOf("projectile", "spawn"), trace)
            helper.processCoreReturn()
            assertEquals(121, helper.returnAt)
            core = true
            helper.processCoreReturn()
            assertEquals(listOf("projectile", "spawn", "return"), trace)
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `callback clear replacement and close win while unrelated handoff survives core clear`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val helper =
                WardenCoreQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    true
                }
            val trace = mutableListOf<String>()
            helper.armPhaseThree(101) {
                assertEquals(101, helper.phaseThreeAt)
                trace += "handoff"
            }
            helper.armCore(
                101,
                101,
                { true },
                {
                    trace += "old-projectile"
                    helper.armCore(
                        101,
                        101,
                        { true },
                        { trace += "new-projectile" },
                        { trace += "new-spawn" },
                    )
                },
                { error("Old spawn must be cancelled by replacement") },
            )
            f.advance(1)
            helper.processCoreEjection()
            assertEquals(listOf("old-projectile"), trace)
            assertEquals(101, helper.projectileAt)
            assertEquals(101, helper.spawnAt)
            helper.processCoreEjection()
            assertEquals(listOf("old-projectile", "new-projectile", "new-spawn"), trace)
            helper.clearCore()
            assertEquals(101, helper.phaseThreeAt)
            helper.processPhaseThree()
            assertEquals("handoff", trace.last())
            assertEquals(-1, helper.phaseThreeAt)

            helper.armCore(101, 101, { true }, { helper.close() }, { error("Closed spawn") })
            helper.processCoreEjection()
            assertTrue(helper.isClosed)
            assertEquals(0, queues.size)
            assertEquals(-1, helper.spawnAt)
            assertFailsWith<IllegalStateException> { helper.armCoreReturn(200, { true }) {} }
            assertFailsWith<IllegalStateException> { helper.armPhaseThree(200) {} }
        }
    }

    @Test
    fun `failed ejection retains ordered unfinished work and native session cleanup retires every owner`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            var attached = true
            val helper =
                WardenCoreQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    attached
                }
            val failure = IllegalStateException("interrupted payload")
            val trace = mutableListOf<String>()
            var attempt = 0
            helper.armCore(
                101,
                102,
                { true },
                {
                    attempt++
                    if (attempt == 1) throw failure
                    trace += "projectile"
                },
                { trace += "spawn" },
            )
            f.advance(3)
            assertSame(
                failure,
                assertFailsWith<IllegalStateException> { helper.processCoreEjection() },
            )
            assertEquals(101, helper.projectileAt)
            assertEquals(102, helper.spawnAt)
            assertEquals(2, queues.size)
            helper.processCoreEjection()
            assertEquals(listOf("projectile", "spawn"), trace)

            helper.armCore(
                104,
                105,
                { true },
                { error("Detached projectile") },
                { error("Detached spawn") },
            )
            attached = false
            f.advance(2)
            helper.processCoreEjection()
            assertEquals(-1, helper.projectileAt)
            assertEquals(-1, helper.spawnAt)
            attached = true
            helper.armCore(
                110,
                112,
                { true },
                { error("Terminated projectile") },
                { error("Terminated spawn") },
            )
            helper.armCoreReturn(140, { true }) { error("Terminated return") }
            helper.armPhaseThree(150) { error("Terminated handoff") }
            f.instances.terminate(raid.instance)
            assertTrue(helper.isClosed)
            assertEquals(0, queues.size)
            f.advance(100)
            helper.processCoreEjection()
            helper.processCoreReturn()
            helper.processPhaseThree()
            assertEquals(
                listOf(-1, -1, -1, -1),
                listOf(helper.projectileAt, helper.spawnAt, helper.returnAt, helper.phaseThreeAt),
            )
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
