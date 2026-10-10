package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.queue.WorldQueueList

class ToaResetQueuesTest {
    @Test
    fun `same session deadline replacement retains insertion order and one tick waits for its boundary`() {
        ToaNativeCombatFixture().use { f ->
            val first = f.start(f.player(1)).instance
            val second = f.start(f.player(2)).instance
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val reset = mutableListOf<InstanceId>()
            val helper = ToaResetQueues(queues, process, f.clock, { true }, reset::add)
            helper.schedule(first, 101)
            helper.schedule(second, 101)
            val snapshot = helper.pending
            helper.schedule(first, 104)
            assertEquals(listOf(first.id, second.id), helper.pending.keys.toList())
            assertEquals(mapOf(first.id to 101, second.id to 101), snapshot)
            assertEquals(2, queues.size, "Updating a deadline must not append another native task")
            helper.process()
            assertTrue(reset.isEmpty())
            f.advance(4)
            process.process()
            assertTrue(reset.isEmpty(), "Default world processing cannot consume raid reset tasks")
            helper.process()
            assertEquals(
                listOf(first.id, second.id),
                reset,
                "Order follows registration, not deadline",
            )
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
            helper.schedule(first, f.clock.cycle + 1)
            helper.process()
            assertEquals(2, reset.size)
            f.advance(1)
            helper.process()
            assertEquals(listOf(first.id, second.id, first.id), reset)
            helper.process()
            assertEquals(3, reset.size)
        }
    }

    @Test
    fun `paused wipes remain pending while nonwipe and stale ids dispatch for manager retirement`() {
        ToaNativeCombatFixture().use { f ->
            val paused = f.start(f.player(1)).instance
            val noWipe = f.start(f.player(2)).instance
            val stale = f.start(f.player(3)).instance
            val queues = WorldQueueList()
            val present = mutableSetOf(paused.id, noWipe.id)
            val wipes = mutableSetOf(paused.id)
            val blocked = mutableSetOf(paused.id)
            val visited = mutableListOf<InstanceId>()
            val resets = mutableListOf<InstanceId>()
            lateinit var helper: ToaResetQueues
            helper =
                ToaResetQueues(
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    canDispatch = { it !in present || it !in wipes || it !in blocked },
                    reset = { id ->
                        assertFalse(
                            id in helper.pending,
                            "Remove metadata before manager revalidation",
                        )
                        visited += id
                        if (id in present && id in wipes && id !in blocked) resets += id
                    },
                )
            helper.schedule(paused, 101)
            helper.schedule(noWipe, 101)
            helper.schedule(stale, 101)
            f.advance(10)
            helper.process()
            assertEquals(listOf(noWipe.id, stale.id), visited)
            assertTrue(resets.isEmpty())
            assertEquals(mapOf(paused.id to 101), helper.pending)
            assertEquals(1, queues.size)
            blocked.clear()
            helper.process()
            assertEquals(listOf(paused.id), resets)
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `old cleanup snapshot cannot cancel a different session replacing the same instance id`() {
        ToaNativeCombatFixture().use { f ->
            val template = f.start().instance
            val old = session(template, InstanceId(9001))
            val replacement = session(template, old.id)
            val queues = WorldQueueList()
            val visited = mutableListOf<InstanceId>()
            val helper =
                ToaResetQueues(
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    { true },
                    visited::add,
                )
            // Native cleanup snapshots its actions before running them. This earlier action
            // replaces the entry before the old entry's already-snapshotted cleanup runs.
            old.onCleanup { helper.schedule(replacement, 105) }
            helper.schedule(old, 101)
            old.resetState()
            assertEquals(mapOf(old.id to 105), helper.pending)
            assertEquals(1, queues.size)
            old.resetState()
            assertEquals(mapOf(old.id to 105), helper.pending)
            f.advance(4)
            helper.process()
            assertTrue(visited.isEmpty())
            f.advance(1)
            helper.process()
            assertEquals(listOf(old.id), visited)
            assertTrue(helper.pending.isEmpty())
            helper.schedule(replacement, 106)
            replacement.resetState()
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `callbacks cancel later due raids and new schedules wait for the next native snapshot`() {
        ToaNativeCombatFixture().use { f ->
            val first = f.start(f.player(1)).instance
            val second = f.start(f.player(2)).instance
            val queues = WorldQueueList()
            val visited = mutableListOf<InstanceId>()
            lateinit var helper: ToaResetQueues
            helper =
                ToaResetQueues(queues, WorldQueueListProcess(queues), f.clock, { true }) { id ->
                    assertFalse(id in helper.pending)
                    visited += id
                    when (visited.size) {
                        1 -> {
                            helper.cancel(second.id)
                            helper.schedule(first, f.clock.cycle)
                        }
                        2 -> {
                            helper.clear()
                            helper.schedule(second, f.clock.cycle)
                        }
                    }
                }
            helper.schedule(first, 101)
            helper.schedule(second, 101)
            f.advance(1)
            helper.process()
            assertEquals(listOf(first.id), visited)
            assertEquals(mapOf(first.id to 101), helper.pending)
            assertEquals(1, queues.size)
            helper.process()
            assertEquals(listOf(first.id, first.id), visited)
            assertEquals(mapOf(second.id to 101), helper.pending)
            helper.process()
            assertEquals(listOf(first.id, first.id, second.id), visited)
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `native session end cancels its reset while clear permits reuse and close prevents resurrection`() {
        ToaNativeCombatFixture().use { f ->
            val first = f.start(f.player(1)).instance
            val second = f.start(f.player(2)).instance
            val queues = WorldQueueList()
            val visited = mutableListOf<InstanceId>()
            val helper =
                ToaResetQueues(
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    { true },
                    visited::add,
                )
            helper.schedule(first, 101)
            helper.schedule(second, 102)
            f.instances.terminate(first)
            assertTrue(first.isEnded)
            assertEquals(mapOf(second.id to 102), helper.pending)
            assertEquals(1, queues.size)
            assertFailsWith<IllegalStateException> { helper.schedule(first, 103) }
            helper.clear()
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
            helper.schedule(second, 104)
            f.advance(4)
            helper.process()
            assertEquals(listOf(second.id), visited)
            helper.schedule(second, 105)
            helper.close()
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
            f.advance(10)
            helper.process()
            assertEquals(listOf(second.id), visited)
            assertFailsWith<IllegalStateException> { helper.schedule(second, 200) }
            helper.cancel(first.id)
            helper.clear()
            helper.close()
            f.instances.terminate(second)
            assertEquals(0, queues.size)
        }
    }

    private fun session(template: InstanceSession, id: InstanceId) =
        InstanceSession(
            id = id,
            regionIds = template.regionIds.toMutableSet(),
            owner = template.owner,
            key = template.key,
            spec = template.spec,
            placement = template.placement,
            access = template.access,
        )

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
