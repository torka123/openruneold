package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

class ToaPendingHitQueuesTest {
    @Test
    fun `overdue hits retain insertion order and equal payloads remain independent native entries`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val process = WorldQueueListProcess(queues)
            val delivered = mutableListOf<ToaPendingHit>()
            val remaining = mutableListOf<Int>()
            lateinit var helper: ToaPendingHitQueues
            helper =
                ToaPendingHitQueues(raid.instance, queues, process, f.clock, stale = { false }) {
                    source,
                    hit ->
                    assertNull(source)
                    delivered += hit
                    remaining += helper.pending.size
                }
            val later = hit(landsAt = 120, damage = 1)
            val earlier = hit(landsAt = 110, damage = 2)
            val future = hit(landsAt = 130, damage = 3)
            helper.schedule(later)
            helper.schedule(earlier)
            helper.schedule(earlier)
            helper.schedule(future)
            assertEquals(4, queues.size)
            f.advance(9)
            helper.process(null)
            assertTrue(delivered.isEmpty())
            f.advance(16)
            process.process()
            assertTrue(delivered.isEmpty(), "The default processor must skip owned entries")
            helper.process(null)
            assertEquals(listOf(later, earlier, earlier), delivered)
            assertEquals(listOf(3, 2, 1), remaining, "Metadata retires before each delivery")
            assertEquals(listOf(future), helper.pending)
            assertEquals(1, queues.size)
            helper.process(null)
            assertEquals(3, delivered.size)
            f.advance(5)
            helper.process(null)
            assertEquals(listOf(later, earlier, earlier, future), delivered)
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `rebind preserves payload deadlines and order while selective retirement cancels native work`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val delivered = mutableListOf<ToaPendingHit>()
            val helper =
                ToaPendingHitQueues(
                    raid.instance,
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    stale = { false },
                ) { _, pending ->
                    delivered += pending
                }
            val oldUid = PlayerUid(1)
            val newUid = PlayerUid(3)
            val retiredUid = PlayerUid(2)
            val first = hit(oldUid, 108, 11)
            val retired = hit(retiredUid, 103, 12)
            val last = hit(oldUid, 105, 13)
            helper.schedule(first)
            helper.schedule(retired)
            helper.schedule(last)
            val snapshot = helper.pending
            helper.rebindPlayer(oldUid, newUid)
            helper.rebindPlayer(newUid, newUid)
            helper.removeIf { it.target == retiredUid }
            assertEquals(listOf(first, retired, last), snapshot)
            assertEquals(
                listOf(first.copy(target = newUid), last.copy(target = newUid)),
                helper.pending,
            )
            assertEquals(2, queues.size)
            f.advance(10)
            helper.process(null)
            assertEquals(listOf(first.copy(target = newUid), last.copy(target = newUid)), delivered)
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `stale generations retire before their deadline and are rechecked after the native ready snapshot`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            var generation = 1
            val delivered = mutableListOf<Int>()
            val helper =
                ToaPendingHitQueues(
                    raid.instance,
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    stale = { it.generation != generation },
                ) { _, pending ->
                    delivered += pending.damage
                    generation++
                }
            helper.schedule(hit(landsAt = 500, generation = 0))
            helper.schedule(hit(landsAt = 101, damage = 1, generation = 1))
            helper.schedule(hit(landsAt = 101, damage = 2, generation = 1))
            helper.process(null)
            assertEquals(listOf(1, 2), helper.pending.map { it.damage })
            assertEquals(2, queues.size, "Stale work is cancelled despite its future deadline")
            assertTrue(delivered.isEmpty())
            f.advance(1)
            helper.process(null)
            assertEquals(
                listOf(1),
                delivered,
                "The second ready entry became stale during delivery",
            )
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `each dispatch keeps one nullable boss snapshot and callback additions wait for the next snapshot`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val first = source()
            val replacement = source()
            var currentBoss = first
            val delivered = mutableListOf<Pair<Npc?, Int>>()
            lateinit var helper: ToaPendingHitQueues
            helper =
                ToaPendingHitQueues(
                    raid.instance,
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    stale = { false },
                ) { snapshot, pending ->
                    delivered += snapshot to pending.damage
                    if (pending.damage == 1) {
                        currentBoss = replacement
                        helper.schedule(hit(damage = 3))
                    }
                }
            helper.schedule(hit(damage = 1))
            helper.schedule(hit(damage = 2))
            helper.process(currentBoss)
            assertEquals(listOf(1, 2), delivered.map { it.second })
            assertTrue(delivered.all { it.first === first })
            assertEquals(listOf(3), helper.pending.map { it.damage })
            helper.process(currentBoss)
            assertSame(replacement, delivered.last().first)
            assertEquals(3, delivered.last().second)
            helper.schedule(hit(damage = 4))
            helper.process(null)
            assertNull(
                delivered.last().first,
                "A missing boss still reaches the graphic-first payload",
            )
            assertEquals(4, delivered.last().second)
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `callback retirement and clear cancel later due entries while session teardown closes reused owner`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val retired = PlayerUid(2)
            val delivered = mutableListOf<Int>()
            lateinit var helper: ToaPendingHitQueues
            helper =
                ToaPendingHitQueues(
                    raid.instance,
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    stale = { false },
                ) { _, pending ->
                    delivered += pending.damage
                    when (pending.damage) {
                        1 -> helper.removeIf { it.target == retired }
                        3 -> {
                            helper.clear()
                            helper.schedule(hit(damage = 5))
                        }
                    }
                }
            helper.schedule(hit(damage = 1))
            helper.schedule(hit(target = retired, damage = 2))
            helper.schedule(hit(damage = 3))
            helper.schedule(hit(damage = 4))
            helper.process(null)
            assertEquals(listOf(1, 3), delivered)
            assertEquals(listOf(5), helper.pending.map { it.damage })
            assertEquals(1, queues.size)
            helper.process(null)
            assertEquals(listOf(1, 3, 5), delivered)
            helper.schedule(hit(damage = 6))
            helper.clear()
            helper.schedule(hit(damage = 7))
            assertEquals(1, queues.size, "Encounter reset clears but permits fresh scheduling")
            f.instances.terminate(raid.instance)
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
            helper.process(null)
            assertEquals(listOf(1, 3, 5), delivered)
            assertFailsWith<IllegalStateException> { helper.schedule(hit(damage = 8)) }
            assertTrue(helper.pending.isEmpty())
            helper.close()
            helper.clear()
            assertEquals(0, queues.size)
        }
    }

    @Test
    fun `delivery failure consumes only its own entry and releases the scoped source snapshot`() {
        ToaNativeCombatFixture().use { f ->
            val raid = f.start()
            val queues = WorldQueueList()
            val failure = IllegalStateException("delivery interrupted")
            val delivered = mutableListOf<Pair<Npc?, Int>>()
            val helper =
                ToaPendingHitQueues(
                    raid.instance,
                    queues,
                    WorldQueueListProcess(queues),
                    f.clock,
                    stale = { false },
                ) { snapshot, pending ->
                    delivered += snapshot to pending.damage
                    if (pending.damage == 1) throw failure
                }
            val boss = source()
            helper.schedule(hit(damage = 1))
            helper.schedule(hit(damage = 2))
            assertSame(failure, assertFailsWith<IllegalStateException> { helper.process(boss) })
            assertSame(boss, delivered.single().first)
            assertEquals(listOf(2), helper.pending.map { it.damage })
            assertEquals(1, queues.size)
            helper.process(null)
            assertEquals(listOf(1, 2), delivered.map { it.second })
            assertNull(delivered.last().first)
            assertTrue(helper.pending.isEmpty())
            assertEquals(0, queues.size)
        }
    }

    private fun hit(
        target: PlayerUid = PlayerUid(1),
        landsAt: Int = 100,
        damage: Int = 10,
        generation: Int = 0,
    ) =
        ToaPendingHit(
            target = target,
            tile = CoordGrid(3500, 9500),
            landsAt = landsAt,
            damage = damage,
            impactGraphic = ToaAssets.WARDEN_FLOOR,
            generation = generation,
        )

    private fun source() =
        Npc(
            assertNotNull(ServerCacheManager.getNpc(WardenObelisk.EXHAUSTED)),
            CoordGrid(3500, 9500),
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
