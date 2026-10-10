package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework

/** Native cooldown ownership preserves the retained 50-tick eligibility at 95c36b853. */
class BabaCombatQueuesTest {
    @Test
    fun `rubble cooldown survives native phases and replacement cancels the former deadline`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val queues = f.babaQueues(raid)
            val started = f.clock.cycle
            assertTrue(queues.rubbleAvailable())
            assertEquals(-1, queues.rubbleAt)
            queues.armRubbleCooldown(started + 50)
            for (phase in
                listOf(
                    BabaState.ROCKFALL,
                    BabaState.ROCK_THROW,
                    BabaState.NORMAL_COMBAT,
                    BabaState.ROLLING_STONES_PREP,
                    BabaState.ROLLING_STONES,
                    BabaState.ROLLING_STONES_RETURN,
                    BabaState.NORMAL_COMBAT,
                )) {
                f.advance(1)
                raid.baba.state = phase
                assertFalse(queues.rubbleAvailable(), phase.name)
                assertEquals(started + 50, queues.rubbleAt, phase.name)
            }
            val replaced = f.clock.cycle + 50
            queues.armRubbleCooldown(replaced)
            f.advance(started + 50 - f.clock.cycle)
            f.queueProcess.process()
            assertFalse(queues.processForced())
            assertFalse(queues.processCompletion())
            assertFalse(queues.rubbleAvailable(), "Original deadline was canceled")
            f.advance(replaced - 1 - f.clock.cycle)
            assertFalse(queues.rubbleAvailable())
            f.advance(1)
            f.queueProcess.process()
            assertEquals(replaced, queues.rubbleAt, "Default processing cannot consume readiness")
            assertTrue(queues.rubbleAvailable())
            assertEquals(-1, queues.rubbleAt)
            assertTrue(
                queues.rubbleAvailable(),
                "Eligibility remains until an actual cast rearms it",
            )
        }
    }

    @Test
    fun `explicit clear restores eligibility while native deletion reset and session end retire cooldown`() {
        for (ending in listOf("clear", "delete", "reset", "session")) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                f.start(player)
                val raid = f.enterBaba(player)
                val queues = f.babaQueues(raid)
                queues.armRubbleCooldown(f.clock.cycle + 50)
                when (ending) {
                    "clear" -> queues.clearRubbleCooldown()
                    "delete" -> f.npcs.del(f.baba(raid), Int.MAX_VALUE)
                    "reset" -> f.enterBaba(player)
                    "session" -> f.instances.terminate(raid.instance)
                }
                assertEquals(-1, queues.rubbleAt, ending)
                f.advance(100)
                f.queueProcess.process()
                assertEquals(ending == "clear", queues.rubbleAvailable(), ending)
                if (ending != "clear") {
                    queues.clearRubbleCooldown()
                    assertFalse(queues.rubbleAvailable(), "Clear cannot revive a closed owner")
                    assertFailsWith<IllegalStateException> {
                        queues.armRubbleCooldown(f.clock.cycle + 50)
                    }
                }
                if (ending == "reset") assertTrue(f.babaQueues(raid).rubbleAvailable())
            }
            framework().clearInlineMocks()
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
