package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.SessionStateEvent

/** Native owned dispatch at the retained manager boundary, with actual spatial payloads. */
@OptIn(InternalApi::class)
class BabaNativeScheduleTest {
    @Test
    fun `thrown rock resolves before tied new rubble and reserves rubble until native landing`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val source = f.baba(raid)
            startSpecial(f, raid, source, BabaSpecial.ROCKFALL)
            val queues = assertNotNull(raid.baba.hazardQueues)
            val rubble = raid.baba.pendingRubble.first()
            player.coords = rubble.tile
            queues.clearRocks()
            queueRock(
                f,
                raid,
                source,
                BabaPendingRock(
                    player.uid,
                    rubble.tile,
                    rubble.landsAt,
                    17,
                    BabaAssets.EXPLOSION,
                    thrown = true,
                ),
            )
            f.advance(rubble.landsAt - f.clock.cycle)
            f.queueProcess.process()
            assertEquals(2, raid.baba.pendingRubble.size)
            assertTrue(raid.baba.rubble.isEmpty())
            f.dispatchLateCycle()
            assertTrue(raid.baba.pendingRubble.isEmpty())
            assertTrue(raid.baba.pendingRocks.isEmpty())
            val actor =
                raid.spawnedNpcs.single {
                    it.hashCode() ==
                        raid.baba.rubble.values.first { it.tile == rubble.tile }.actorKey
                }
            assertEquals(
                actor.baseHitpointsLvl,
                actor.hitpoints,
                "Same-tick rubble cannot block the earlier rock",
            )
            assertEquals(1, f.hits(player).size)
            assertEquals(
                1,
                f.hits(player).single().first,
                "Native delivery retains minimum one tick",
            )
            f.instances.terminate(raid.instance)
            assertTrue(queues.isClosed)
        }
    }

    @Test
    fun `each special completes at its retained deadline and Rockfall chains once into Rock Throw`() {
        for (special in BabaSpecial.entries) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                f.start(player)
                val raid = f.enterBaba(player)
                val source = f.baba(raid)
                val started = f.clock.cycle
                val duration = startSpecial(f, raid, source, special)
                assertEquals(
                    when (special) {
                        BabaSpecial.ROCKFALL -> 8
                        BabaSpecial.ROCK_THROW -> 7
                        BabaSpecial.SHOCKWAVE -> 4
                        BabaSpecial.BABOON_DISCHARGE -> 5
                    },
                    duration,
                )
                val state = raid.baba.state
                f.advance(duration)
                f.queueProcess.process()
                assertEquals(
                    state,
                    raid.baba.state,
                    "Global queue dispatch cannot run room-owned work",
                )
                f.dispatchLateCycle()
                if (special == BabaSpecial.ROCKFALL) {
                    assertEquals(BabaState.ROCK_THROW, raid.baba.state)
                    assertEquals(started + 15, f.babaQueues(raid).completionAt)
                    f.dispatchLateCycle(started + 14)
                    assertEquals(BabaState.ROCK_THROW, raid.baba.state)
                    f.dispatchLateCycle(started + 15)
                }
                assertEquals(BabaState.NORMAL_COMBAT, raid.baba.state)
                assertEquals(-1, f.babaQueues(raid).completionAt)
                assertEquals(0, raid.baba.attackCounter)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `rolling preparation consumes three ticks and return waits two without running the new state twice`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val source = f.baba(raid)
            raid.baba.pendingRollingThresholds += 1
            val started = f.clock.cycle
            f.attack(source, player)
            assertEquals(BabaState.ROLLING_STONES_PREP, raid.baba.state)
            f.dispatchLateCycle(started + 2)
            assertEquals(BabaState.ROLLING_STONES_PREP, raid.baba.state)
            f.dispatchLateCycle(started + 3)
            assertEquals(BabaState.ROLLING_STONES, raid.baba.state)
            assertEquals(started + 4, raid.baba.nextBoulderAt)
            assertEquals(-1, raid.baba.pendingBoulderSpawnAt)
            f.dispatchLateCycle(started + 4)
            assertEquals(started + 6, raid.baba.pendingBoulderSpawnAt)
            // Represent a drained final row; movement/row geometry has separate retained tests.
            raid.baba.rollingSet = BabaTuning.BOULDER_MAX_SETS.value
            raid.baba.nextBoulderAt = -1
            raid.baba.pendingBoulderSpawnAt = -1
            f.dispatchLateCycle(started + 5)
            assertEquals(BabaState.ROLLING_STONES_RETURN, raid.baba.state)
            val top = source.coords
            f.dispatchLateCycle(started + 6)
            assertEquals(BabaState.ROLLING_STONES_RETURN, raid.baba.state)
            f.dispatchLateCycle(started + 7)
            assertEquals(BabaState.NORMAL_COMBAT, raid.baba.state)
            assertEquals(top, source.coords, "Native pursuit resumes without a teleport snap")
            assertEquals(0, raid.baba.underTargetTicks)
        }
    }

    @Test
    fun `destroyed cover schedules forced Rockfall before a tied completion and replaces that completion`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val source = f.baba(raid)
            val started = f.clock.cycle
            startSpecial(f, raid, source, BabaSpecial.ROCKFALL)
            f.dispatchLateCycle(started + 6)
            val cover = raid.baba.rubble.values.first()
            val actor = raid.spawnedNpcs.first { it.hashCode() == cover.actorKey }
            actor.hitpoints = BabaTuning.RUBBLE_COVER_DAMAGE.value
            f.dispatchLateCycle(started + 8)
            val throwStarted = f.clock.cycle
            assertEquals(BabaState.ROCK_THROW, raid.baba.state)
            raid.baba.hazardQueues!!.clearRocks()
            queueRock(
                f,
                raid,
                source,
                BabaPendingRock(
                    player.uid,
                    cover.tile,
                    throwStarted + 5,
                    1,
                    BabaAssets.EXPLOSION,
                    thrown = true,
                ),
            )
            f.dispatchLateCycle(throwStarted + 5)
            assertFalse(actor.isSlotAssigned)
            f.dispatchLateCycle(throwStarted + 6)
            assertEquals(BabaState.ROCK_THROW, raid.baba.state)
            f.dispatchLateCycle(throwStarted + 7)
            assertEquals(BabaState.ROCKFALL, raid.baba.state)
            assertEquals(throwStarted + 15, f.babaQueues(raid).completionAt)
            f.dispatchLateCycle()
            assertEquals(BabaState.ROCKFALL, raid.baba.state)
            assertEquals(throwStarted + 15, f.babaQueues(raid).completionAt)
        }
    }

    @Test
    fun `overdue forced Rockfall stays held through rolling and preempts return when eligible again`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val source = f.baba(raid)
            val started = f.clock.cycle
            startSpecial(f, raid, source, BabaSpecial.ROCKFALL)
            f.dispatchLateCycle(started + 6)
            val cover = raid.baba.rubble.values.first()
            val actor = raid.spawnedNpcs.first { it.hashCode() == cover.actorKey }
            actor.hitpoints = BabaTuning.RUBBLE_COVER_DAMAGE.value
            f.dispatchLateCycle(started + 8)
            val thrown = f.clock.cycle
            raid.baba.hazardQueues!!.clearRocks()
            queueRock(
                f,
                raid,
                source,
                BabaPendingRock(
                    player.uid,
                    cover.tile,
                    thrown + 6,
                    1,
                    BabaAssets.EXPLOSION,
                    thrown = true,
                ),
            )
            f.dispatchLateCycle(thrown + 6)
            assertFalse(actor.isSlotAssigned)
            f.dispatchLateCycle(thrown + 7)
            assertEquals(BabaState.NORMAL_COMBAT, raid.baba.state)
            raid.baba.pendingRollingThresholds += 1
            f.advance(1)
            f.attack(source, player)
            f.dispatchLateCycle()
            assertEquals(BabaState.ROLLING_STONES_PREP, raid.baba.state)
            f.dispatchLateCycle(thrown + 11)
            assertEquals(BabaState.ROLLING_STONES, raid.baba.state)
            raid.baba.rollingSet = BabaTuning.BOULDER_MAX_SETS.value
            raid.baba.nextBoulderAt = -1
            raid.baba.pendingBoulderSpawnAt = -1
            f.dispatchLateCycle(thrown + 12)
            assertEquals(BabaState.ROLLING_STONES_RETURN, raid.baba.state)
            f.dispatchLateCycle(thrown + 13)
            assertEquals(BabaState.ROCKFALL, raid.baba.state)
            assertEquals(thrown + 21, f.babaQueues(raid).completionAt)
        }
    }

    @Test
    fun `offline pause retains completion and native teardown closes every old callback`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player, activate = false)
            val old = f.babaQueues(raid)
            val due = old.completionAt
            f.events.publish(SessionStateEvent.Logout(player))
            f.dispatchLateCycle(due + 20)
            assertEquals(BabaState.INTRO, raid.baba.state)
            assertEquals(due, old.completionAt)
            f.instances.terminate(raid.instance)
            assertEquals(-1, old.completionAt)
            assertFalse(old.processCompletion())
            assertFalse(old.processForced())
            f.start(player)
            val fresh = f.enterBaba(player, activate = false)
            assertNotSame(old, f.babaQueues(fresh))
            f.dispatchLateCycle(f.babaQueues(fresh).completionAt)
            assertEquals(BabaState.NORMAL_COMBAT, fresh.baba.state)
            assertEquals(BabaState.WAITING, raid.baba.state)
        }
    }

    private fun queueRock(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        source: Npc,
        rock: BabaPendingRock,
    ) {
        val method =
            ToaRaidManager::class
                .java
                .getDeclaredMethod(
                    "queueBabaRock",
                    ToaRaidSession::class.java,
                    Npc::class.java,
                    BabaPendingRock::class.java,
                )
        method.isAccessible = true
        method.invoke(f.manager, raid, source, rock)
    }

    private fun startSpecial(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        source: Npc,
        special: BabaSpecial,
    ): Int {
        // Select a content payload explicitly; separate native-choice tests cover selection/RNG.
        val method =
            ToaRaidManager::class
                .java
                .getDeclaredMethod(
                    "startBabaSpecial",
                    ToaRaidSession::class.java,
                    Npc::class.java,
                    BabaSpecial::class.java,
                )
        method.isAccessible = true
        return method.invoke(f.manager, raid, source, special) as Int
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
