package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

class AkkhaPayloadQueuesTest {
    @Test
    fun `mixed memory deadlines preserve stage order first reveal observation and batch removal`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val helper = AkkhaPayloadQueues(raid.instance, queues, process, f.clock) { true }
            val first = step(AkkhaQuadrant.BURN, 102, 106, 108)
            val second = step(AkkhaQuadrant.FREEZE, 101, 104, 108)
            val future = step(AkkhaQuadrant.DARKNESS, 105, 107, 110)
            // Retained flags suppress duplicate output without retaining orphaned native tasks.
            future.revealed = true
            future.renderedWaveTick = 0
            helper.armMemory(listOf(first, second, future))
            val trace = mutableListOf<String>()
            var firstSounds = 0
            fun dispatch() {
                helper.processMemory(
                    reveal = {
                        if (helper.memorySteps.none { pending -> pending.revealed }) firstSounds++
                        trace += "reveal-${it.safeQuadrant}"
                        it.revealed = true
                    },
                    render = {
                        trace += "render-${it.safeQuadrant}"
                        it.renderedWaveTick = 0
                    },
                    resolve = { due ->
                        assertTrue(due.none { it in helper.memorySteps })
                        trace += "resolve-${due.joinToString { it.safeQuadrant.name }}"
                    },
                )
            }
            dispatch()
            assertTrue(trace.isEmpty())
            f.advance(1)
            process.process()
            assertTrue(trace.isEmpty(), "The normal world processor skips all payload owners")
            dispatch()
            assertEquals(listOf("reveal-FREEZE"), trace)
            f.advance(1)
            dispatch()
            f.advance(2)
            dispatch()
            f.advance(2)
            dispatch()
            f.advance(2)
            dispatch()
            assertEquals(
                listOf(
                    "reveal-FREEZE",
                    "reveal-BURN",
                    "render-FREEZE",
                    "render-BURN",
                    "resolve-BURN, FREEZE",
                ),
                trace,
            )
            assertEquals(0, firstSounds, "The caller observes the preexisting revealed metadata")
            assertEquals(listOf(future), helper.memorySteps)
            f.advance(2)
            dispatch()
            assertEquals("resolve-DARKNESS", trace.last())
            assertTrue(helper.memorySteps.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `overdue memory reveals all steps then renders all steps before one removed batch`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val helper =
                AkkhaPayloadQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    true
                }
            val first = step(AkkhaQuadrant.BURN, 104, 109, 110)
            val second = step(AkkhaQuadrant.BURN, 102, 107, 108)
            helper.armMemory(listOf(first, second))
            val snapshot = helper.memorySteps
            val trace = mutableListOf<String>()
            var firstSounds = 0
            f.advance(20)
            helper.processMemory(
                reveal = {
                    if (helper.memorySteps.none { pending -> pending.revealed }) firstSounds++
                    trace += "reveal-${if (it === first) 1 else 2}"
                    it.revealed = true
                },
                render = {
                    assertTrue(helper.memorySteps.all { pending -> pending.revealed })
                    trace += "render-${if (it === first) 1 else 2}"
                    it.renderedWaveTick = 0
                },
                resolve = {
                    assertEquals(
                        listOf(first, second),
                        it,
                        "Registration order survives overdue deadlines",
                    )
                    assertTrue(
                        helper.memorySteps.isEmpty(),
                        "Remove the whole batch before payloads",
                    )
                    assertEquals(0, queues.size)
                    trace += "resolve-batch"
                },
            )
            assertEquals(
                listOf("reveal-1", "reveal-2", "render-1", "render-2", "resolve-batch"),
                trace,
            )
            assertEquals(1, firstSounds)
            assertEquals(listOf(first, second), snapshot)
            helper.processMemory({ fail("duplicate reveal") }, { fail("duplicate render") }) {
                fail("duplicate resolution")
            }
        }
    }

    @Test
    fun `memory replacement from a reveal cancels later old stages and waits for fresh reveal dispatch`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val helper =
                AkkhaPayloadQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    true
                }
            val old = step(AkkhaQuadrant.BURN, 100, 100, 100)
            val cancelled = step(AkkhaQuadrant.FREEZE, 100, 100, 100)
            val replacement = step(AkkhaQuadrant.LIGHTNING, 100, 100, 100)
            helper.armMemory(listOf(old, cancelled))
            val trace = mutableListOf<String>()
            helper.processMemory(
                reveal = {
                    trace += "old-reveal"
                    helper.armMemory(listOf(replacement))
                },
                render = { fail("Old render survived replacement") },
                resolve = { fail("Old batch survived replacement") },
            )
            assertEquals(listOf("old-reveal"), trace)
            assertEquals(listOf(replacement), helper.memorySteps)
            assertEquals(3, queues.size)
            helper.processMemory(
                reveal = {
                    assertSame(replacement, it)
                    trace += "fresh-reveal"
                    it.revealed = true
                },
                render = {
                    trace += "fresh-render"
                    it.renderedWaveTick = 0
                },
                resolve = {
                    assertEquals(listOf(replacement), it)
                    trace += "fresh-resolve"
                },
            )
            assertEquals(
                listOf("old-reveal", "fresh-reveal", "fresh-render", "fresh-resolve"),
                trace,
            )
            helper.armMemory(listOf(step(AkkhaQuadrant.DARKNESS, 101, 102, 103)))
            helper.clearMemory()
            f.advance(10)
            helper.processMemory({ fail("cleared reveal") }, { fail("cleared render") }) {
                fail("cleared resolution")
            }
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `detonation uses rebound and retired origins at the same deadline and clears before callback`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val helper = AkkhaPayloadQueues(raid.instance, queues, process, f.clock) { true }
            val oldUid = PlayerUid(1)
            val retired = PlayerUid(2)
            val newUid = PlayerUid(3)
            val original =
                AkkhaDetonation(
                    linkedMapOf(oldUid to CoordGrid(3500, 9500), retired to CoordGrid(3501, 9500)),
                    resolvesAt = 106,
                    diagonal = true,
                )
            helper.armDetonation(original.copy(resolvesAt = 104))
            helper.armDetonation(original)
            helper.rebindPlayer(oldUid, newUid)
            helper.rebindPlayer(newUid, newUid)
            helper.retirePlayer(retired)
            val expected = original.copy(origins = mapOf(newUid to CoordGrid(3500, 9500)))
            assertEquals(expected, helper.detonation)
            assertEquals(1, queues.size)
            f.advance(5)
            helper.processDetonation { fail("Detonation deadline changed during rebind") }
            f.advance(1)
            process.process()
            assertEquals(expected, helper.detonation)
            helper.processDetonation {
                assertNull(helper.detonation)
                assertEquals(expected, it)
                helper.armDetonation(original.copy(resolvesAt = 106))
            }
            helper.retirePlayer(oldUid)
            helper.retirePlayer(retired)
            assertEquals(emptyMap(), assertNotNull(helper.detonation).origins)
            helper.processDetonation {
                assertNull(helper.detonation)
                assertTrue(
                    it.origins.isEmpty(),
                    "An empty marked roster retains its scheduled resolution",
                )
                assertEquals(106, it.resolvesAt)
                assertTrue(it.diagonal)
            }
            helper.armDetonation(original.copy(resolvesAt = 200))
            helper.clearDetonation()
            assertNull(helper.detonation)
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `final orb cap retains overdue deadline and callback rearm waits for another native snapshot`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val helper = AkkhaPayloadQueues(raid.instance, queues, process, f.clock) { true }
            var belowCap = false
            var spawns = 0
            helper.armFinalOrb(101, eligible = { belowCap }) {
                spawns++
                helper.armFinalOrb(f.clock.cycle, eligible = { true }) {
                    spawns++
                    helper.clearFinalOrb()
                }
            }
            f.advance(20)
            helper.processFinalOrb()
            assertEquals(0, spawns)
            assertEquals(101, helper.finalOrbAt)
            assertEquals(1, queues.size)
            belowCap = true
            process.process()
            assertEquals(0, spawns)
            helper.processFinalOrb()
            assertEquals(1, spawns)
            assertEquals(120, helper.finalOrbAt)
            assertEquals(1, queues.size)
            helper.processFinalOrb()
            assertEquals(2, spawns)
            assertEquals(-1, helper.finalOrbAt)
            assertEquals(0, queues.size)
            helper.processFinalOrb()
            assertEquals(2, spawns)
        }
    }

    @Test
    fun `clear permits reuse while detached delivery and native session termination cancel all payloads`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            var attached = true
            val helper =
                AkkhaPayloadQueues(raid.instance, queues, WorldQueueListProcess(queues), f.clock) {
                    attached
                }
            fun arm() {
                helper.armMemory(listOf(step(AkkhaQuadrant.BURN, 101, 102, 103)))
                helper.armDetonation(AkkhaDetonation(emptyMap(), 103, false))
                helper.armFinalOrb(103, { true }) { fail("Retired orb spawned") }
            }
            arm()
            assertEquals(5, queues.size)
            helper.clear()
            assertFalse(helper.isClosed)
            assertEquals(0, queues.size)
            arm()
            attached = false
            f.advance(10)
            helper.processMemory({ fail("Detached reveal") }, { fail("Detached render") }) {
                fail("Detached resolution")
            }
            helper.processDetonation { fail("Detached detonation") }
            helper.processFinalOrb()
            assertTrue(helper.memorySteps.isEmpty())
            assertNull(helper.detonation)
            assertEquals(-1, helper.finalOrbAt)
            assertEquals(0, queues.size)
            attached = true
            arm()
            f.instances.terminate(raid.instance)
            assertTrue(helper.isClosed)
            assertTrue(helper.memorySteps.isEmpty())
            assertNull(helper.detonation)
            assertEquals(-1, helper.finalOrbAt)
            assertEquals(0, queues.size)
            assertFailsWith<IllegalStateException> { helper.armMemory(emptyList()) }
            assertFailsWith<IllegalStateException> {
                helper.armDetonation(AkkhaDetonation(emptyMap(), 200, false))
            }
            assertFailsWith<IllegalStateException> { helper.armFinalOrb(200, { true }) {} }
            helper.clear()
            helper.close()
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `actual special completion clears memory lifecycle and encounter reset closes and detaches owners`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val state = raid.akkha
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val helper = AkkhaPayloadQueues(raid.instance, queues, process, f.clock) { true }
            val combat = AkkhaCombatQueues(raid.instance, queues, process, f.clock) { true }
            state.payloadQueues = helper
            state.combatQueues = combat
            state.startIntro()
            state.startNormalCombat()
            state.startSpecial(listOf(AkkhaSpecial.MEMORY_BLAST))
            helper.armMemory(listOf(step(AkkhaQuadrant.BURN, 101, 102, 103)))
            combat.armVisibility(104, 120, {}, {}, {}, {})
            state.memoryHidden = true
            assertEquals(1, state.memorySteps.size)
            assertEquals(104, state.memoryHideAt)
            state.finishSpecial()
            assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
            assertTrue(state.activeSpecials.isEmpty())
            assertTrue(state.memorySteps.isEmpty())
            assertFalse(state.memoryHidden)
            assertEquals(-1, state.memoryHideAt)
            assertEquals(-1, state.memoryReappearAt)
            assertFalse(
                helper.isClosed,
                "Special completion retains this encounter's payload owner",
            )
            assertEquals(0, queues.size)

            state.startSpecial(listOf(AkkhaSpecial.MEMORY_BLAST, AkkhaSpecial.DETONATE))
            helper.armMemory(listOf(step(AkkhaQuadrant.FREEZE, 101, 102, 103)))
            helper.armDetonation(AkkhaDetonation(emptyMap(), 103, true))
            helper.armFinalOrb(103, { true }) { fail("Reset final orb spawned") }
            combat.armVisibility(104, 120, {}, {}, {}, {})
            state.reset()
            assertTrue(helper.isClosed)
            assertNull(state.payloadQueues)
            assertNull(state.combatQueues)
            assertTrue(state.memorySteps.isEmpty())
            assertNull(state.detonation)
            assertEquals(-1, state.nextFinalOrbAt)
            assertEquals(0, queues.size)
            f.advance(30)
            helper.processMemory({ fail("Reset reveal") }, { fail("Reset render") }) {
                fail("Reset memory resolved")
            }
            helper.processDetonation { fail("Reset detonation resolved") }
            helper.processFinalOrb()
            assertFalse(combat.processVisibility())
        }
    }

    private fun step(quadrant: AkkhaQuadrant, revealAt: Int, blastAt: Int, resolvesAt: Int) =
        AkkhaMemoryStep(quadrant, revealAt, blastAt, resolvesAt)

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
