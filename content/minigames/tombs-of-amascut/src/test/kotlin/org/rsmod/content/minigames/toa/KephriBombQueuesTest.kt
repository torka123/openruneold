package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/** Checkpoint 63d79bed9 bomb staging through the native queue processor, without timing guesses. */
class KephriBombQueuesTest {
    @Test
    fun `native lock plus three prepares at actual lock plus three then lands one tick later`() {
        Fixture().use { f ->
            val target = KephriBombTargetRuntime(f.player.uid, 103, damage = 17, radius = 1)
            val trace = mutableListOf<String>()
            f.helper.scheduleTarget(target)
            val lock: (List<KephriBombTargetRuntime>) -> Unit = { batch ->
                assertTrue(f.helper.targets.isEmpty(), "The whole lock batch retires before launch")
                assertSame(target, batch.single())
                trace += "lock:${f.clock.cycle}"
                f.helper.scheduleBomb(
                    KephriBombRuntime(tile, f.clock.cycle + 4, target.damage, target.radius)
                )
            }
            val prepare: (KephriBombRuntime) -> Unit = { bomb ->
                assertTrue(bomb.hitQueued)
                assertTrue(f.helper.bombs.any { it === bomb })
                trace += "prepare:${f.clock.cycle}"
            }
            val land: (List<KephriBombRuntime>) -> Unit = { batch ->
                assertTrue(
                    f.helper.bombs.isEmpty(),
                    "Landing metadata retires before the visual batch",
                )
                assertEquals(17, batch.single().damage)
                trace += "land:${f.clock.cycle}"
            }
            f.clock.cycle = 103
            f.process.process()
            assertTrue(trace.isEmpty())
            assertEquals(listOf(target), f.helper.targets)
            f.helper.processBombs(lock, prepare, land)
            assertEquals(listOf("lock:103"), trace)
            f.clock.cycle = 105
            f.helper.processBombs(lock, prepare, land)
            assertFalse(f.helper.bombs.single().hitQueued)
            f.clock.cycle = 106
            f.helper.processBombs(lock, prepare, land)
            assertEquals(listOf("lock:103", "prepare:106"), trace)
            f.helper.processBombs(lock, prepare, land)
            assertEquals(2, trace.size, "Repeated processing cannot queue the same impact twice")
            f.clock.cycle = 107
            f.helper.processBombs(lock, prepare, land)
            assertEquals(listOf("lock:103", "prepare:106", "land:107"), trace)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `overdue equal entries retain insertion identity and all preparations precede one landing batch`() {
        Fixture().use { f ->
            val firstTarget = KephriBombTargetRuntime(f.player.uid, 103, 7, 0)
            val secondTarget = firstTarget.copy()
            val futureTarget = firstTarget.copy(locksAt = 140)
            val first = KephriBombRuntime(tile, 105, 11, 1)
            val second = first.copy()
            val preparedAlready = first.copy(damage = 3, hitQueued = true)
            f.helper.scheduleTarget(firstTarget)
            f.helper.scheduleTarget(secondTarget)
            f.helper.scheduleTarget(futureTarget)
            f.helper.scheduleBomb(first)
            f.helper.scheduleBomb(second)
            f.helper.scheduleBomb(preparedAlready)
            val trace = mutableListOf<String>()
            val prepared = mutableListOf<KephriBombRuntime>()
            var landingCalls = 0
            f.clock.cycle = 110
            f.helper.processBombs(
                lock = { batch ->
                    trace += "locks"
                    assertEquals(2, batch.size)
                    assertSame(firstTarget, batch[0])
                    assertSame(secondTarget, batch[1])
                    assertEquals(listOf(futureTarget), f.helper.targets)
                    batch.forEach {
                        f.helper.scheduleBomb(
                            KephriBombRuntime(tile, f.clock.cycle + 4, it.damage, it.radius)
                        )
                    }
                },
                prepare = {
                    trace += "prepare"
                    assertTrue(it.hitQueued)
                    prepared += it
                },
                land = { batch ->
                    landingCalls++
                    trace += "land"
                    assertEquals(3, batch.size)
                    assertSame(first, batch[0])
                    assertSame(second, batch[1])
                    assertSame(preparedAlready, batch[2])
                    assertTrue(batch.all { it.hitQueued })
                    assertEquals(2, f.helper.bombs.size)
                    assertTrue(f.helper.bombs.all { it.landsAt == 114 && !it.hitQueued })
                },
            )
            assertEquals(listOf("locks", "prepare", "prepare", "land"), trace)
            assertSame(first, prepared[0])
            assertSame(second, prepared[1])
            assertEquals(1, landingCalls)
        }
    }

    @Test
    fun `rebind preserves immutable target payload timing and retirement retains locked tile bombs`() {
        Fixture().use { f ->
            val oldUid = f.player.uid
            val replacement = f.server.player(2).uid
            val unrelated = f.server.player(3).uid
            val original = KephriBombTargetRuntime(oldUid, 103, 19, 1)
            f.helper.scheduleTarget(original)
            f.helper.scheduleTarget(original.copy(target = unrelated, locksAt = 120))
            f.helper.rebindPlayer(oldUid, replacement)
            f.helper.retirePlayer(oldUid)
            val rebound = f.helper.targets.first()
            assertEquals(original.copy(target = replacement), rebound)
            assertEquals(oldUid, original.target)
            f.clock.cycle = 103
            f.helper.processBombs(
                lock = { batch ->
                    assertEquals(replacement, batch.single().target)
                    f.helper.scheduleBomb(KephriBombRuntime(tile, 107, batch.single().damage, 1))
                },
                prepare = { fail("Newly locked bomb prepared too early") },
                land = { fail("Newly locked bomb landed too early") },
            )
            f.helper.retirePlayer(replacement)
            f.helper.retirePlayer(unrelated)
            assertTrue(f.helper.targets.isEmpty())
            val bomb = f.helper.bombs.single()
            val trace = mutableListOf<String>()
            f.clock.cycle = 107
            f.helper.processBombs(
                lock = { fail("Retired target launched") },
                prepare = {
                    assertSame(bomb, it)
                    trace += "prepare"
                },
                land = {
                    assertSame(bomb, it.single())
                    trace += "land"
                },
            )
            assertEquals(listOf("prepare", "land"), trace)
            f.clock.cycle = 120
            f.helper.processBombs(
                {},
                { fail("Duplicate preparation") },
                { fail("Duplicate landing") },
            )
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `clear during preparation cancels later due hits and landing while preserving independent bomber`() {
        Fixture().use { f ->
            val first = KephriBombRuntime(tile, 105, 4, 0)
            val second = first.copy()
            f.helper.scheduleBomb(first)
            f.helper.scheduleBomb(second)
            f.helper.scheduleTarget(KephriBombTargetRuntime(f.player.uid, 130, 3, 0))
            var bombers = 0
            f.helper.armBomber(108) { bombers++ }
            f.clock.cycle = 110
            f.helper.processBombs(
                lock = { fail("Future lock fired") },
                prepare = {
                    assertSame(first, it)
                    assertTrue(it.hitQueued)
                    assertFalse(second.hitQueued, "Each preparation marks only its own payload")
                    assertFailsWith<IllegalStateException> { f.helper.processBombs({}, {}, {}) }
                    f.helper.clearBombs()
                },
                land = { fail("Cleared landing batch fired") },
            )
            assertFalse(second.hitQueued)
            assertTrue(f.helper.targets.isEmpty())
            assertTrue(f.helper.bombs.isEmpty())
            assertEquals(108, f.helper.bomberAt)
            f.helper.processBomber()
            assertEquals(1, bombers)
            assertEquals(0, f.nativeQueues.size)
            // The dispatch context was released even though the callback attempted reentry.
            f.helper.scheduleTarget(KephriBombTargetRuntime(f.player.uid, 110, 5, 0))
            var locks = 0
            f.helper.processBombs({ locks += it.size }, {}, {})
            assertEquals(1, locks)
        }
    }

    @Test
    fun `bomber has a later independent boundary and advances plus twelve even for an empty target pass`() {
        Fixture().use { f ->
            var targetsPresent = false
            val calls = mutableListOf<Int>()
            fun arm(deadline: Int) {
                f.helper.armBomber(deadline) {
                    assertEquals(-1, f.helper.bomberAt)
                    calls += f.clock.cycle
                    if (targetsPresent)
                        f.helper.scheduleBomb(KephriBombRuntime(tile, f.clock.cycle + 4, 24, 1))
                    arm(f.clock.cycle + 12)
                }
            }
            arm(108)
            f.clock.cycle = 107
            f.helper.processBomber()
            assertTrue(calls.isEmpty())
            f.clock.cycle = 120
            f.process.process()
            f.helper.processBombs({}, {}, {})
            assertTrue(calls.isEmpty())
            f.helper.processBomber()
            assertEquals(listOf(120), calls)
            assertEquals(132, f.helper.bomberAt)
            assertTrue(f.helper.bombs.isEmpty())
            targetsPresent = true
            f.clock.cycle = 132
            f.helper.processBombs({}, { fail("Bomber has not launched yet") }, {})
            f.helper.processBomber()
            assertEquals(listOf(120, 132), calls)
            assertEquals(144, f.helper.bomberAt)
            val bomb = f.helper.bombs.single()
            assertEquals(136, bomb.landsAt)
            f.helper.clearBomber()
            assertEquals(listOf(bomb), f.helper.bombs, "Recovery end retains launched tile bombs")
            f.clock.cycle = 136
            var prepared = false
            f.helper.processBombs({}, { prepared = true }, { assertTrue(prepared) })
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `clear and native encounter or session cleanup cancel all stages without reviving old ownership`() {
        for (ending in listOf("clear", "encounter", "session", "detach")) {
            Fixture().use { f ->
                f.helper.scheduleTarget(KephriBombTargetRuntime(f.player.uid, 103, 7, 0))
                f.helper.scheduleBomb(KephriBombRuntime(tile, 107, 11, 1))
                f.helper.armBomber(108) { fail("Retired bomber: $ending") }
                when (ending) {
                    "clear" -> f.helper.clear()
                    "encounter" -> f.server.bossEncounters.remove(f.boss)
                    "session" -> f.server.instances.terminate(f.raid.instance)
                    "detach" -> f.attached = false
                }
                f.clock.cycle = 120
                f.process.process()
                f.helper.processBombs(
                    { fail("Retired lock: $ending") },
                    { fail("Retired hit: $ending") },
                    { fail("Retired landing: $ending") },
                )
                f.helper.processBomber()
                assertTrue(f.helper.targets.isEmpty(), ending)
                assertTrue(f.helper.bombs.isEmpty(), ending)
                assertEquals(-1, f.helper.bomberAt, ending)
                assertEquals(0, f.nativeQueues.size, ending)
                if (ending in listOf("encounter", "session")) {
                    assertTrue(f.helper.isClosed)
                    assertFailsWith<IllegalStateException> { f.helper.armBomber(121) {} }
                    assertFailsWith<IllegalStateException> {
                        f.helper.scheduleBomb(KephriBombRuntime(tile, 121, 1, 0))
                    }
                } else if (ending == "clear") {
                    assertFalse(f.helper.isClosed)
                    var resumed = 0
                    f.helper.armBomber(120) { resumed++ }
                    f.helper.processBomber()
                    assertEquals(1, resumed)
                }
            }
        }
    }

    private class Fixture : AutoCloseable {
        val server = ToaNativeCombatFixture()
        val player = server.player()
        val raid = server.start(player).also { server.enterKephri(player) }
        val boss = server.boss(raid)
        val clock = server.clock
        val nativeQueues = WorldQueueList()
        val process = WorldQueueListProcess(nativeQueues)
        var attached = true
        val helper =
            KephriBombQueues(
                server.bossEncounters.of(boss),
                raid.instance,
                nativeQueues,
                process,
                clock,
            ) {
                attached
            }

        override fun close() {
            try {
                helper.close()
                server.close()
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    companion object {
        private val tile = CoordGrid(3545, 5406)

        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
