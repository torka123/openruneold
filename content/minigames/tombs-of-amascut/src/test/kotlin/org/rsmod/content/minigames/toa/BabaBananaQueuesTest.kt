package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/** Checkpoint db2b4f3f3 banana arming, expiry-first resolution and remove/reinsert ordering. */
class BabaBananaQueuesTest {
    @Test
    fun `native bananas wait for arming and occupancy while expiry wins over occupancy`() {
        Fixture().use { f ->
            var occupied = false
            var observations = 0
            val resolved = mutableListOf<Pair<BabaBananaPile, Boolean>>()
            val pile = BabaBananaPile(tile(0), 101, 150)
            f.helper.schedule(
                pile,
                {
                    observations++
                    occupied
                },
            ) { value, expired ->
                resolved += value to expired
            }
            f.helper.process()
            assertEquals(0, observations, "Unarmed piles do not inspect occupancy")
            f.clock.cycle = 101
            f.process.process()
            assertEquals(0, observations, "Default world dispatch cannot process banana owners")
            f.helper.process()
            assertEquals(1, observations)
            assertTrue(resolved.isEmpty())
            assertEquals(pile, f.helper.piles[pile.tile])
            f.clock.cycle = 149
            occupied = true
            f.helper.process()
            assertEquals(listOf(pile to false), resolved)
            assertTrue(f.helper.piles.isEmpty())
            f.helper.process()
            assertEquals(1, resolved.size)

            val expiry = BabaBananaPile(tile(1), 150, 151)
            f.helper.schedule(expiry, { fail("Expiry must short-circuit occupancy") }) {
                value,
                expired ->
                resolved += value to expired
            }
            f.clock.cycle = 151
            f.helper.process()
            assertEquals(expiry to true, resolved.last())
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `adjacent current and previous tile observations resolve in placement order`() {
        Fixture().use { f ->
            val previous = tile(0)
            val current = tile(1)
            val trace = mutableListOf<CoordGrid>()
            for (position in listOf(previous, current)) {
                f.helper.schedule(
                    BabaBananaPile(position, 101, 150),
                    { it.tile == current || it.tile == previous },
                ) { pile, expired ->
                    assertFalse(expired)
                    trace += pile.tile
                }
            }
            f.clock.cycle = 101
            f.helper.process()
            assertEquals(listOf(previous, current), trace)
            assertTrue(f.helper.piles.isEmpty())
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `replacement cancels the old expiry and reinserts the tile after existing piles`() {
        for (replacementExpiry in listOf(105, 110)) {
            Fixture().use { f ->
                val first = BabaBananaPile(tile(0), 101, 105)
                val second = BabaBananaPile(tile(1), 101, 105)
                val replacement = first.copy(expiresAt = replacementExpiry)
                val trace = mutableListOf<String>()
                f.helper.schedule(first, { false }) { _, _ -> trace += "stale-first" }
                f.helper.schedule(second, { false }) { _, expired ->
                    assertTrue(expired)
                    trace += "second"
                }
                val originalSnapshot = f.helper.piles
                f.helper.schedule(replacement, { false }) { value, expired ->
                    assertEquals(replacement, value)
                    assertTrue(expired)
                    trace += "replacement"
                }
                assertEquals(listOf(first.tile, second.tile), originalSnapshot.keys.toList())
                assertEquals(listOf(second.tile, first.tile), f.helper.piles.keys.toList())
                assertEquals(2, f.nativeQueues.size)
                f.clock.cycle = 105
                f.helper.process()
                if (replacementExpiry > 105) {
                    assertEquals(listOf("second"), trace)
                    assertEquals(replacement, f.helper.piles[first.tile])
                    f.clock.cycle = replacementExpiry
                    f.helper.process()
                }
                assertEquals(listOf("second", "replacement"), trace)
                assertTrue(f.helper.piles.isEmpty())
                assertEquals(0, f.nativeQueues.size)
            }
        }
    }

    @Test
    fun `replacement during a native due batch cancels the old callback and defers the new pile`() {
        Fixture().use { f ->
            val first = BabaBananaPile(tile(0), 101, 150)
            val second = BabaBananaPile(tile(1), 101, 150)
            val trace = mutableListOf<String>()
            f.helper.schedule(first, { true }) { _, _ ->
                trace += "first"
                assertEquals(second, f.helper.remove(second.tile))
                assertNull(f.helper.remove(second.tile))
                f.helper.schedule(second, { true }) { _, _ -> trace += "replacement" }
            }
            f.helper.schedule(second, { true }) { _, _ -> trace += "stale-second" }
            f.clock.cycle = 101
            f.helper.process()
            assertEquals(listOf("first"), trace)
            assertEquals(second, f.helper.piles[second.tile])
            assertEquals(1, f.nativeQueues.size)
            f.helper.process()
            assertEquals(listOf("first", "replacement"), trace)
            assertEquals(0, f.nativeQueues.size)
        }
    }

    @Test
    fun `normal special and rolling phases retain piles while explicit clear permits fresh placement`() {
        Fixture().use { f ->
            f.state.startIntro()
            f.state.startNormalCombat()
            val trace = mutableListOf<CoordGrid>()
            val old = BabaBananaPile(tile(0), 101, 150)
            f.helper.schedule(old, { false }) { pile, _ -> trace += pile.tile }
            f.state.state = BabaState.SHOCKWAVE
            f.state.startNormalCombat()
            f.state.pendingRollingThresholds += 1
            f.state.beginNextRollingPhase()
            f.state.beginRollingSet()
            f.state.finishRollingPhase()
            f.state.startNormalCombat()
            assertEquals(old, f.helper.piles[old.tile])
            assertEquals(1, f.nativeQueues.size)
            f.helper.clear()
            assertFalse(f.helper.isClosed)
            assertTrue(f.helper.piles.isEmpty())
            val fresh = BabaBananaPile(tile(1), 101, 160)
            f.helper.schedule(fresh, { true }) { pile, expired ->
                assertFalse(expired)
                trace += pile.tile
            }
            f.clock.cycle = 101
            f.helper.process()
            assertEquals(listOf(fresh.tile), trace)
            f.clock.cycle = 200
            f.helper.process()
            assertEquals(listOf(fresh.tile), trace)
        }
    }

    @Test
    fun `native deletion session termination and stale generation cannot resolve queued bananas`() {
        for (ending in listOf("native-delete", "session-end", "generation")) {
            Fixture().use { f ->
                f.helper.schedule(BabaBananaPile(tile(0), 101, 150), { true }) { _, _ ->
                    fail("Retired pile resolved: $ending")
                }
                when (ending) {
                    "native-delete" -> f.phase.registry.remove(f.phase.npc)
                    "session-end" -> f.endSession()
                    else -> f.state.reset()
                }
                if (ending != "generation") {
                    assertTrue(f.helper.isClosed)
                    assertEquals(0, f.nativeQueues.size)
                    assertFailsWith<IllegalStateException> {
                        f.helper.schedule(BabaBananaPile(tile(1), 101, 150), { true }) { _, _ -> }
                    }
                }
                f.clock.cycle = 101
                f.helper.process()
                assertTrue(f.helper.piles.isEmpty())
                assertEquals(0, f.nativeQueues.size)
            }
        }
    }

    @Test
    fun `bulk retirement cancels before tile cleanup and native deletion cleans each remaining pile once`() {
        Fixture().use { f ->
            val first = BabaBananaPile(tile(0), 101, 150)
            val second = BabaBananaPile(tile(1), 101, 150)
            f.helper.schedule(first, { true }) { _, _ -> fail("Retired trigger") }
            f.helper.schedule(second, { true }) { _, _ -> fail("Retired trigger") }
            f.phase.registry.remove(f.phase.npc)
            assertTrue(f.phase.encounter.isEnded)
            assertEquals(listOf(first to true, second to true), f.retired)
            assertTrue(f.helper.piles.isEmpty())
            f.helper.close()
            f.helper.clear()
            f.endSession()
            assertEquals(2, f.retired.size)
        }
        Fixture().use { f ->
            val removed = BabaBananaPile(tile(0), 101, 150)
            val cleared = BabaBananaPile(tile(1), 101, 150)
            f.helper.schedule(removed, { true }) { _, _ -> fail("Removed trigger") }
            f.helper.schedule(cleared, { true }) { _, _ -> fail("Cleared trigger") }
            assertEquals(removed, f.helper.remove(removed.tile))
            assertTrue(f.retired.isEmpty(), "Explicit removal keeps tile cleanup with its caller")
            f.helper.clear()
            assertEquals(listOf(cleared to false), f.retired)
            var expirations = 0
            val expired = BabaBananaPile(tile(2), 100, 100)
            f.helper.schedule(expired, { false }) { _, expiry ->
                assertTrue(expiry)
                expirations++
            }
            f.helper.process()
            f.helper.close()
            assertEquals(1, expirations)
            assertEquals(listOf(cleared to false), f.retired, "Normal expiry remains caller-owned")
        }
    }

    private fun tile(offset: Int) = CoordGrid(3500 + offset, 9500)

    private class Fixture : AutoCloseable {
        private val server = ToaNativeCombatFixture()
        private val session = server.start().instance
        val phase = BabaNativePhaseFixture()
        val state = phase.state
        val clock = phase.clock
        val nativeQueues = WorldQueueList()
        val process = WorldQueueListProcess(nativeQueues)
        val retired = mutableListOf<Pair<BabaBananaPile, Boolean>>()
        private val generation = state.token()
        val helper =
            BabaBananaQueues(
                phase.encounter,
                session,
                nativeQueues,
                process,
                clock,
                cleanupPile = { pile ->
                    assertEquals(
                        0,
                        nativeQueues.size,
                        "Cancel all native entries before tile cleanup",
                    )
                    retired += pile to phase.encounter.isEnded
                },
            ) {
                state.isCurrent(generation)
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

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
