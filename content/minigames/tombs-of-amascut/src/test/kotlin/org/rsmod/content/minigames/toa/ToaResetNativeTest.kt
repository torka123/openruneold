package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.player.SessionStateEvent

/** Actual manager death, native departure/rejoin, and LateCycle reset ordering. */
@OptIn(InternalApi::class)
class ToaResetNativeTest {
    @Test
    fun `last logout holds an overdue wipe and rejoin resets once before old room mechanics`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            f.enterBaba(player)
            val original = f.baba(raid)
            val originalEncounter = f.bossEncounters.of(original)
            val diedAt = f.clock.cycle
            f.manager.markDeath(player)
            f.instances.handleDeath(player, diedAt)
            ToaDeathCleanupHook(f.manager).cleanup(player)
            val participant = assertNotNull(raid.participant(player.accountHash))
            assertEquals(1, participant.deaths)
            assertEquals(1, raid.attemptsUsed)
            assertTrue(participant.spectating)
            var oldRoomCallbacks = 0
            f.babaQueues(raid).armCompletion(diedAt + 1) { oldRoomCallbacks++ }

            f.events.publish(SessionStateEvent.Logout(player))
            f.players.remove(player.slotId)
            assertFalse(participant.connected)
            assertNull(f.instances.sessionForPlayer(player))
            assertSame(raid, f.manager.rejoinableRaid(player))
            f.advance(10)
            f.queueProcess.process()
            assertSame(original, f.baba(raid), "The normal world processor cannot perform a wipe")
            f.dispatchLateCycle()
            assertSame(original, f.baba(raid), "An overdue wipe stays pending while everyone is offline")
            assertTrue(original.isSlotAssigned)
            assertEquals(0, oldRoomCallbacks)
            assertEquals(1, raid.attemptsUsed)

            val returned =
                f.player(9).apply {
                    uuid = player.uuid
                    accountHash = player.accountHash
                    accountId = player.accountId
                    characterId = player.characterId
                    assignUid()
                }
            assertNotEquals(player.uid, returned.uid)
            assertTrue(f.manager.rejoin(returned, raid))
            assertSame(raid.instance, f.instances.sessionForPlayer(returned))
            assertEquals(returned.uid, participant.uid)
            assertTrue(participant.connected)
            assertTrue(participant.spectating)
            f.queueProcess.process()
            assertSame(original, f.baba(raid))
            assertEquals(0, oldRoomCallbacks)

            f.dispatchLateCycle()
            val replacement = f.baba(raid)
            assertNotSame(original, replacement)
            assertFalse(original.isSlotAssigned)
            assertTrue(originalEncounter.isEnded)
            assertEquals(0, oldRoomCallbacks, "Reset retires old room callbacks before room mechanics")
            assertFalse(participant.spectating)
            assertEquals(1, participant.deaths)
            assertEquals(1, raid.attemptsUsed, "Resuming a pending wipe does not spend another attempt")
            f.dispatchLateCycle()
            f.dispatchLateCycle(f.clock.cycle + 1)
            assertSame(replacement, f.baba(raid))
            assertEquals(1, raid.attemptsUsed)
            assertEquals(0, oldRoomCallbacks)
        }
    }

    @Test
    fun `a recovered participant cancels the pending wipe instead of merely postponing it`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            f.enterBaba(player)
            val original = f.baba(raid)
            val diedAt = f.clock.cycle
            f.manager.markDeath(player)
            f.instances.handleDeath(player, diedAt)
            ToaDeathCleanupHook(f.manager).cleanup(player)
            val participant = assertNotNull(raid.participant(player.accountHash))
            assertTrue(participant.spectating)
            assertEquals(1, raid.attemptsUsed)
            participant.spectating = false
            f.dispatchLateCycle(diedAt + 1)
            assertSame(original, f.baba(raid))
            assertTrue(original.isSlotAssigned)
            assertFalse(participant.spectating)

            // No new death is recorded: making the roster a wipe again cannot revive the cancelled task.
            participant.spectating = true
            f.dispatchLateCycle(diedAt + 10)
            assertSame(original, f.baba(raid))
            assertTrue(original.isSlotAssigned)
            assertTrue(participant.spectating)
            assertEquals(1, participant.deaths)
            assertEquals(1, raid.attemptsUsed)
        }
    }

    @Test
    fun `native session termination cancels its wipe before fresh raid admission`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val oldRaid = f.start(player)
            f.enterBaba(player)
            val oldBoss = f.baba(oldRaid)
            val diedAt = f.clock.cycle
            f.manager.markDeath(player)
            assertEquals(1, oldRaid.attemptsUsed)
            assertTrue(f.instances.terminate(oldRaid.instance))
            assertTrue(oldRaid.instance.isEnded)
            assertTrue(oldRaid.cleanupStarted)
            assertFalse(oldBoss.isSlotAssigned)
            val fresh = f.start(player, seed = 123456L)
            assertNotSame(oldRaid.instance, fresh.instance)
            assertSame(fresh, f.manager.raidFor(player))
            assertEquals(0, fresh.attemptsUsed)
            f.advance(diedAt + 1 - f.clock.cycle)
            f.queueProcess.process()
            f.dispatchLateCycle()
            assertSame(fresh, f.manager.raidFor(player))
            assertEquals(ToaRoom.LOBBY, fresh.currentRoom)
            assertEquals(0, fresh.attemptsUsed)
            assertFalse(fresh.instance.isEnded)
            f.enterBaba(player)
            val freshBoss = f.baba(fresh)
            f.dispatchLateCycle(f.clock.cycle + 10)
            assertSame(freshBoss, f.baba(fresh))
            assertTrue(freshBoss.isSlotAssigned)
            assertEquals(0, fresh.attemptsUsed)
            assertTrue(fresh.participants.values.none { it.spectating })
            assertEquals(1, oldRaid.attemptsUsed)
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
