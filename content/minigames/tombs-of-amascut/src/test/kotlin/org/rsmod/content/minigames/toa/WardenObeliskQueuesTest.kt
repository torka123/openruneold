package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.bosses.validation.SpecValidator
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.entity.Npc
import org.rsmod.game.queue.WorldQueueList

/** The retained Obelisk cadence and branch oracle, dispatched by actual native owners. */
class WardenObeliskQueuesTest {
    @Test
    fun `normal and accelerated intervals stay anchored and overdue dispatch emits only once`() {
        for (interval in listOf(40, 30)) {
            ToaNativeCombatFixture().use { f ->
                val raid = f.start()
                val queues = WorldQueueList()
                val process = WorldQueueListProcess(queues)
                val helper = WardenObeliskQueues(raid.instance, queues, process, f.clock) { true }
                val trace = mutableListOf<Pair<Int, Int>>()
                val first = f.clock.cycle + interval
                helper.arm(first, interval) { trace += f.clock.cycle to helper.nextAt }
                f.advance(interval - 1)
                assertFalse(helper.process())
                f.advance(1)
                process.process()
                assertTrue(trace.isEmpty(), "Default queue processing cannot consume owned work")
                assertTrue(helper.process())
                assertEquals(listOf(first to first), trace)
                assertEquals(first + interval, helper.nextAt)
                assertFalse(helper.process(), "Duplicate dispatch at the same clock cannot replay")

                f.advance(2 * interval + 5)
                assertTrue(helper.process())
                assertEquals(2, trace.size, "One payload even when multiple periods were missed")
                assertEquals(f.clock.cycle to first + interval, trace.last())
                assertEquals(first + 3 * interval, helper.nextAt)
                assertFalse(helper.process())
                f.advance(helper.nextAt - f.clock.cycle)
                assertTrue(helper.process())
                assertEquals(3, trace.size)
                assertEquals(first + 4 * interval, helper.nextAt)
                assertEquals(1, queues.size)
                helper.clear()
                assertEquals(-1, helper.nextAt)
                assertEquals(0, queues.size)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `callback clear rearm and close each prevent automatic recurrence from resurrecting old work`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val helper =
                WardenObeliskQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    true
                }
            val trace = mutableListOf<String>()
            helper.arm(101, 40) {
                trace += "clear"
                helper.clear()
            }
            f.advance(1)
            assertTrue(helper.process())
            assertEquals(-1, helper.nextAt)
            assertEquals(0, queues.size)

            helper.arm(102, 40) {
                trace += "rearm"
                helper.arm(102, 30) {
                    trace += "replacement"
                    helper.close()
                }
            }
            assertFailsWith<IllegalArgumentException> { helper.arm(999, 0) {} }
            assertEquals(102, helper.nextAt, "Invalid arm leaves the existing deadline intact")
            f.advance(1)
            assertTrue(helper.process())
            assertEquals(listOf("clear", "rearm"), trace)
            assertEquals(102, helper.nextAt, "The callback's replacement wins over old +40")
            assertEquals(1, queues.size, "New work defers to the next native snapshot")
            assertTrue(helper.process())
            assertEquals(listOf("clear", "rearm", "replacement"), trace)
            assertEquals(-1, helper.nextAt)
            assertEquals(0, queues.size)
            assertFalse(helper.process())
            assertFailsWith<IllegalStateException> { helper.arm(103, 40) {} }
            assertEquals(-1, helper.nextAt)
        }
    }

    @Test
    fun `a failing payload retains the old deadline unless its callback explicitly changes ownership`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val helper =
                WardenObeliskQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    true
                }
            val failure = IllegalStateException("payload interrupted")
            var attempts = 0
            helper.arm(101, 40) {
                attempts++
                if (attempts == 1) throw failure
            }
            f.advance(6)
            assertSame(failure, assertFailsWith<IllegalStateException> { helper.process() })
            assertEquals(101, helper.nextAt)
            assertEquals(1, queues.size)
            assertTrue(helper.process())
            assertEquals(2, attempts)
            assertEquals(141, helper.nextAt, "Successful retry advances from the old anchor")

            helper.arm(107, 40) {
                helper.arm(108, 30) {}
                throw failure
            }
            f.advance(1)
            assertSame(failure, assertFailsWith<IllegalStateException> { helper.process() })
            assertEquals(108, helper.nextAt)
            assertEquals(1, queues.size)
            f.advance(1)
            assertTrue(helper.process())
            assertEquals(138, helper.nextAt)

            helper.arm(109, 40) {
                helper.clear()
                throw failure
            }
            f.advance(1)
            assertSame(failure, assertFailsWith<IllegalStateException> { helper.process() })
            assertEquals(-1, helper.nextAt)
            assertEquals(0, queues.size)
            helper.arm(110, 40) {
                helper.close()
                throw failure
            }
            f.advance(1)
            assertSame(failure, assertFailsWith<IllegalStateException> { helper.process() })
            assertEquals(-1, helper.nextAt)
            assertEquals(0, queues.size)
            assertFalse(helper.process())
        }
    }

    @Test
    fun `detached generation and native session termination cancel work without a later callback`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            var attached = true
            val helper =
                WardenObeliskQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    attached
                }
            var launches = 0
            helper.arm(101, 40) { launches++ }
            attached = false
            f.advance(1)
            assertFalse(helper.process())
            assertEquals(-1, helper.nextAt)
            assertEquals(0, queues.size)
            attached = true
            helper.arm(102, 40) { launches++ }
            f.instances.terminate(raid.instance)
            f.advance(50)
            assertFalse(helper.process())
            assertEquals(0, launches)
            assertEquals(-1, helper.nextAt)
            assertEquals(0, queues.size)
            assertFailsWith<IllegalStateException> { helper.arm(200, 40) {} }
        }
    }

    @Test
    fun `native Obelisk rotation survives missing actor cadence and core pause but deletion resets identity`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            fun spawn(): Npc =
                Npc(
                        assertNotNull(ServerCacheManager.getNpc(WardenObelisk.EXHAUSTED)),
                        player.coords,
                    )
                    .also { f.npcs.add(it, Int.MAX_VALUE) }
            val source = spawn()
            val encounter = f.bossEncounters.of(source)
            assertTrue(SpecValidator.validate(wardenObeliskBossSpec()).isEmpty())
            encounter.lastAbilityTick = 88
            encounter.lastAbilityName = "previous"
            source.actionDelay = 200
            source.lockFacing(player.coords, targetWidth = 2, targetLength = 3)
            val facing = source.faceLockSquare
            val coords = source.coords
            val selectionDraws = f.selectionRandom.bounds.toList()
            val queues = WorldQueueList()
            val helper =
                WardenObeliskQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    true
                }
            var actorPresent = true
            val trace = mutableListOf<WardenObeliskSpecial>()
            val launch = { if (actorPresent) trace += encounter.selectWardenObelisk(f.clock.cycle) }
            helper.arm(110, 10, launch)
            f.advance(10)
            assertTrue(helper.process())
            assertEquals(listOf(WardenObeliskSpecial.CONVERGING_BEAM), trace)
            actorPresent = false
            f.advance(10)
            assertTrue(helper.process(), "Missing actor is still a session cadence dispatch")
            assertEquals(130, helper.nextAt)
            assertEquals(1, trace.size, "Missing actor cannot consume a branch")

            // Core exposure leaves the deadline untouched and does not dispatch this owner.
            f.advance(50)
            assertEquals(130, helper.nextAt)
            actorPresent = true
            helper.arm(f.clock.cycle + 10, 10, launch)
            assertFalse(helper.process())
            f.advance(10)
            assertTrue(helper.process())
            assertEquals(WardenObeliskSpecial.WINDMILL_BEAM, trace.last())
            assertNull(encounter.selectAbility(assertNotNull(encounter.currentPhase).selector, 180))
            f.attack(source, player)
            f.advance(10)
            assertTrue(helper.process())
            assertEquals(WardenObeliskSpecial.entries.toList(), trace)
            assertEquals(0, encounter.phaseAbilityCount)
            assertEquals(88, encounter.lastAbilityTick)
            assertEquals("previous", encounter.lastAbilityName)
            assertEquals(200, source.actionDelay)
            assertEquals(coords, source.coords)
            assertEquals(facing, source.faceLockSquare)
            assertEquals(2, source.faceLockWidth)
            assertEquals(3, source.faceLockLength)
            assertTrue(encounter.recentAbilityCommitTicks().isEmpty())
            assertTrue(encounter.recentAttackTraces().isEmpty())
            assertEquals(selectionDraws, f.selectionRandom.bounds)

            helper.clear()
            f.npcs.del(source, Int.MAX_VALUE)
            assertTrue(encounter.isEnded, "Registered native Delete retires branch ownership")
            assertFailsWith<IllegalStateException> { encounter.selectWardenObelisk(f.clock.cycle) }
            val replacement = spawn()
            val fresh = f.bossEncounters.of(replacement)
            assertNotSame(encounter, fresh)
            assertEquals(
                WardenObeliskSpecial.CONVERGING_BEAM,
                fresh.selectWardenObelisk(f.clock.cycle),
            )
            f.npcs.del(replacement, Int.MAX_VALUE)
            assertTrue(fresh.isEnded)
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
