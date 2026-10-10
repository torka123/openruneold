package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.game.entity.Npc

/** Actual manager cast and selection integration of the retained native cooldown mapping. */
class BabaCooldownNativeTest {
    @Test
    fun `ordinary special opportunity consumes cooldown readiness at the exact boundary`() {
        for (offset in listOf(49, 50)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                f.start(player, seed = 42)
                val raid = f.enterBaba(player)
                val source = f.baba(raid)
                val encounter = f.bossEncounters.of(source)
                val queues = f.babaQueues(raid)
                val started = f.clock.cycle
                queues.armRubbleCooldown(started + 50)
                // Existing history makes the cooling-down branch a singleton discharge choice.
                encounter.babaSpecialHistory().previous = BabaSpecial.SHOCKWAVE
                repeat(BabaTuning.SPECIAL_AFTER_AUTOS.value) {
                    if (source.actionDelay > f.clock.cycle)
                        f.advance(source.actionDelay - f.clock.cycle)
                    f.attack(source, player)
                }
                assertEquals(BabaTuning.SPECIAL_AFTER_AUTOS.value, raid.baba.attackCounter)
                f.advance(started + offset - f.clock.cycle)
                f.queueProcess.process()
                assertEquals(started + 50, queues.rubbleAt)
                assertNotNull(f.manager.performBabaSpecialIfDue(source))
                val selected = encounter.babaSpecialHistory().previous
                if (offset == 49) {
                    assertEquals(BabaSpecial.BABOON_DISCHARGE, selected)
                    assertEquals(started + 50, queues.rubbleAt)
                } else {
                    assertTrue(
                        selected in setOf(BabaSpecial.ROCKFALL, BabaSpecial.BABOON_DISCHARGE)
                    )
                    assertEquals(
                        if (selected == BabaSpecial.ROCKFALL) f.clock.cycle + 50 else -1,
                        queues.rubbleAt,
                        "Selection consumed native due readiness; only a Rockfall rearms it",
                    )
                }
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `destroyed cover forces Rockfall during cooldown and restarts fifty ticks without selecting again`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val source = f.baba(raid)
            val queues = f.babaQueues(raid)
            val history = f.bossEncounters.of(source).babaSpecialHistory()
            history.previous = BabaSpecial.SHOCKWAVE
            val started = f.clock.cycle
            startSpecial(f, raid, source, BabaSpecial.ROCKFALL)
            assertEquals(started + 50, queues.rubbleAt)
            f.dispatchLateCycle(started + 6)
            val cover = raid.baba.rubble.values.first()
            val actor = raid.spawnedNpcs.first { it.hashCode() == cover.actorKey }
            actor.hitpoints = BabaTuning.RUBBLE_COVER_DAMAGE.value
            f.dispatchLateCycle(started + 8)
            assertEquals(BabaState.ROCK_THROW, raid.baba.state)
            assertEquals(
                started + 50,
                queues.rubbleAt,
                "Chained Throw does not restart Rockfall cooldown",
            )
            assertNotNull(raid.baba.hazardQueues).clearRocks()
            queueRock(
                f,
                raid,
                source,
                BabaPendingRock(
                    player.uid,
                    cover.tile,
                    started + 13,
                    1,
                    BabaAssets.EXPLOSION,
                    thrown = true,
                ),
            )
            f.dispatchLateCycle(started + 13)
            assertFalse(actor.isSlotAssigned)
            assertFalse(queues.rubbleAvailable())
            f.dispatchLateCycle(started + 15)
            assertEquals(BabaState.ROCKFALL, raid.baba.state)
            assertEquals(started + 65, queues.rubbleAt)
            assertEquals(
                BabaSpecial.SHOCKWAVE,
                history.previous,
                "Forced cast bypasses ordinary branch history",
            )
            f.dispatchLateCycle()
            assertEquals(
                started + 65,
                queues.rubbleAt,
                "Same-tick retry cannot restart forced work",
            )
            f.advance(started + 50 - f.clock.cycle)
            assertFalse(queues.rubbleAvailable())
            f.advance(15)
            assertTrue(queues.rubbleAvailable())
        }
    }

    private fun startSpecial(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        source: Npc,
        special: BabaSpecial,
    ) {
        ToaRaidManager::class
            .java
            .getDeclaredMethod(
                "startBabaSpecial",
                ToaRaidSession::class.java,
                Npc::class.java,
                BabaSpecial::class.java,
            )
            .apply { isAccessible = true }
            .invoke(f.manager, raid, source, special)
    }

    private fun queueRock(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        source: Npc,
        rock: BabaPendingRock,
    ) {
        ToaRaidManager::class
            .java
            .getDeclaredMethod(
                "queueBabaRock",
                ToaRaidSession::class.java,
                Npc::class.java,
                BabaPendingRock::class.java,
            )
            .apply { isAccessible = true }
            .invoke(f.manager, raid, source, rock)
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
