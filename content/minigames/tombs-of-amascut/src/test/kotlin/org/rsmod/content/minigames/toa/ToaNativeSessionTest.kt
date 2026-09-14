package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RaidRewardClaimResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid

/** Actual admission, native membership/deletion and the retained raid-specific leave policies. */
@OptIn(InternalApi::class)
class ToaNativeSessionTest {
    @Test
    fun `late party admission failure publishes no raid and teleports no member`() {
        ToaNativeCombatFixture().use { f ->
            val party = f.formParty(3)
            val leader = party.first()
            val coords = party.map { it.coords }
            f.refuseRegionValidationAt = 2
            assertFalse(f.manager.start(leader))
            assertTrue(f.instances.sessionsForKey(TOA_INSTANCE_KEY).isEmpty())
            assertEquals(coords, party.map { it.coords })
            assertNull(f.manager.partyFor(leader)?.activeSession)
            for (member in party) {
                assertNull(f.instances.sessionForPlayer(member))
                assertNull(f.manager.raidFor(member))
                assertNull(f.manager.rejoinableRaid(member))
            }
            verifyNoInteractions(f.supplies)
        }
    }

    @Test
    fun `successful party admission shares native state and preserves the locked participant snapshot`() {
        ToaNativeCombatFixture().use { f ->
            val members = f.formParty(3)
            val raid = f.start(members.first(), partySize = members.size, seed = 9876L)
            assertEquals(
                members.map { it.accountHash }.toSet(),
                raid.startSnapshot.participantAccountHashes,
            )
            assertEquals(3, raid.startSnapshot.partySize)
            assertEquals(9876L, raid.startSnapshot.seed)
            assertEquals(2_000, raid.instance.spec.reclaimTicks)
            assertEquals(10, raid.instance.spec.graceTicks)
            assertFalse(raid.instance.spec.destroyWhenEmpty)
            for (member in members) {
                assertSame(raid.instance, f.instances.sessionForPlayer(member))
                assertSame(raid, f.manager.raidFor(member))
                assertTrue(assertNotNull(raid.participant(member.accountHash)).connected)
                verify(f.supplies).begin(member, raid)
            }
            f.enterBaba(members.first())
            val boss = f.baba(raid)
            assertEquals(raid.instance.id, f.instances.instanceForNpc(boss))
            assertSame(raid, f.manager.raidFor(boss))
        }
    }

    @Test
    fun `either logout listener order cleans once and reconnect binds only the new player identity`() {
        for (nativeFirst in listOf(false, true)) {
            try {
                ToaNativeCombatFixture(nativeLifecycleFirst = nativeFirst).use { f ->
                    val members = f.formParty(2)
                    val leader = members.first()
                    val departing = members.last()
                    val raid = f.start(leader, partySize = 2)
                    val participant = assertNotNull(raid.participant(departing.accountHash))
                    val originalOrder = raid.participants.values.map { it.accountHash }
                    clearInvocations(f.ui, f.supplies)
                    f.events.publish(SessionStateEvent.Logout(departing))
                    assertNull(f.instances.sessionForPlayer(departing), "nativeFirst=$nativeFirst")
                    assertEquals(TOA_ENTRANCE, departing.coords)
                    assertNull(f.manager.raidFor(departing))
                    assertSame(raid, f.manager.rejoinableRaid(departing))
                    assertFalse(participant.connected)
                    assertTrue(participant.rewardEligible)
                    assertFalse(participant.spectating)
                    assertEquals(0, participant.deaths)
                    assertSame(raid, f.manager.raidFor(leader))
                    verify(f.supplies, times(1)).suspendForDisconnect(departing, raid)
                    verify(f.ui, times(1)).close(departing)

                    f.players.remove(departing.slotId)
                    val replacement =
                        f.player(9).apply {
                            uuid = departing.uuid
                            accountHash = departing.accountHash
                            accountId = departing.accountId
                            characterId = departing.characterId
                            assignUid()
                        }
                    assertNotEquals(departing.uid, replacement.uid)
                    assertNull(
                        f.manager.raidFor(replacement),
                        "Lobby lookup cannot grant membership",
                    )
                    assertSame(raid, f.manager.rejoinableRaid(replacement))
                    assertTrue(f.manager.openLobby(replacement))
                    assertSame(raid, f.manager.raidFor(replacement))
                    assertEquals(replacement.uid, participant.uid)
                    assertTrue(participant.connected)
                    assertEquals(originalOrder, raid.participants.values.map { it.accountHash })
                    assertEquals(listOf(leader.uid, replacement.uid), raid.party.members.toList())
                    assertNull(f.manager.raidFor(departing))
                    f.manager.onLeave(departing, raid.instance.id)
                    f.manager.logout(departing)
                    assertTrue(
                        participant.connected,
                        "Stale logout must not retire the replacement",
                    )
                    verify(f.supplies, times(1)).suspendForDisconnect(departing, raid)
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `native death retains raid membership and a true wipe resets once on the following tick`() {
        ToaNativeCombatFixture().use { f ->
            val members = f.formParty(2)
            val raid = f.start(members.first(), partySize = 2)
            f.enterBaba(members.first())
            val oldBoss = f.baba(raid)
            val cycle = f.clock.cycle
            val cleanup = ToaDeathCleanupHook(f.manager)
            for ((index, player) in members.withIndex()) {
                f.manager.markDeath(player)
                f.instances.handleDeath(player, cycle)
                cleanup.cleanup(player)
                assertSame(raid.instance, f.instances.sessionForPlayer(player))
                assertSame(raid, f.manager.raidFor(player))
                val participant = assertNotNull(raid.participant(player.accountHash))
                assertTrue(participant.spectating)
                assertEquals(1, participant.deaths)
                f.manager.markDeath(player)
                assertEquals(1, participant.deaths, "Duplicate death at the same native tick")
                assertEquals(if (index == 0) 0 else 1, raid.attemptsUsed)
            }
            f.dispatchLateCycle(cycle)
            assertTrue(oldBoss.isSlotAssigned)
            assertTrue(raid.participants.values.all { it.spectating })
            f.dispatchLateCycle(cycle + 1)
            assertEquals(1, raid.attemptsUsed)
            assertFalse(oldBoss.isSlotAssigned)
            assertTrue(raid.participants.values.none { it.spectating })
            val replacement = f.baba(raid)
            f.dispatchLateCycle(cycle + 1)
            assertSame(replacement, f.baba(raid), "The queued wipe reset is consumed once")
        }
    }

    @Test
    fun `reward leave preserves busy hold and retires only after existing storage fallback succeeds`() {
        ToaNativeCombatFixture().use { f ->
            val members = f.formParty(2)
            val raid = f.start(members.first(), partySize = 2)
            val player = members.last()
            raid.state = ToaRaidState.REWARDS
            val coords = player.coords
            `when`(f.rewards.claimToInventoryForLeave(player, raid))
                .thenReturn(RaidRewardClaimResult.Busy)
            assertEquals(
                RaidLeaveResult.Busy,
                f.manager.leaveFromLogoutTab(player, raid.instance.id),
            )
            assertEquals(coords, player.coords)
            assertSame(raid, f.manager.raidFor(player))
            assertTrue(assertNotNull(raid.participant(player.accountHash)).rewardEligible)
            `when`(f.rewards.claimToInventoryForLeave(player, raid))
                .thenReturn(RaidRewardClaimResult.InventoryFull)
            `when`(f.rewards.detachToStorage(player, raid)).thenReturn(true)
            assertEquals(
                RaidLeaveResult.Left,
                f.manager.leaveFromLogoutTab(player, raid.instance.id),
            )
            verify(f.rewards).detachToStorage(player, raid)
            assertNull(f.instances.sessionForPlayer(player))
            assertNull(f.manager.raidFor(player))
            assertNull(f.manager.rejoinableRaid(player))
            assertEquals(TOA_ENTRANCE, player.coords)
            assertSame(raid, f.manager.raidFor(members.first()))
        }
    }

    @Test
    fun `external teleport resolves native leave by event instance after player index removal`() {
        ToaNativeCombatFixture().use { f ->
            val members = f.formParty(2)
            val raid = f.start(members.first(), partySize = 2)
            val player = members.last()
            val destination = CoordGrid(3222, 3218)
            player.coords = destination
            assertTrue(f.instances.leaveForExternalTeleport(player, destination, f.clock.cycle))
            assertEquals(destination, player.coords)
            assertNull(f.instances.sessionForPlayer(player))
            assertFalse(assertNotNull(raid.participant(player.accountHash)).rewardEligible)
            assertFalse(player.uid in raid.party.members)
            assertNull(f.manager.rejoinableRaid(player))
            assertSame(raid, f.manager.raidFor(members.first()))
            verify(f.supplies).terminalCleanup(player, raid)
        }
    }

    @Test
    fun `native deletion keeps the global kill hook and ended capture cannot affect a fresh raid`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            f.enterBaba(player)
            val boss = f.baba(raid)
            val helper =
                Npc(assertNotNull(ServerCacheManager.getNpc(BabaAssets.BABOON)), boss.coords)
            f.npcs.add(helper, Int.MAX_VALUE)
            helper.respawns = false
            f.instances.attachNpc(raid.instance.id, helper)
            raid.spawnedNpcs += helper
            raid.npcRoles[helper] = ToaNpcRole.HAZARD
            assertSame(raid, f.manager.raidFor(helper))
            val context = f.nativeDeath(helper, player)
            assertNull(f.instances.instanceForNpc(helper))
            assertNull(f.manager.raidFor(helper))
            assertFalse(raid.owns(helper), "Registered global kill hook ran after native deletion")
            f.death.dispatchDeferredKill(context)
            assertFalse(raid.owns(helper))

            val captured = assertNotNull(f.manager.captureNpcDeath(boss))
            assertTrue(f.instances.terminate(raid.instance))
            assertFalse(f.instances.terminate(raid.instance))
            verify(f.supplies, times(1)).terminalCleanupRaid(raid)
            assertFalse(boss.isSlotAssigned)
            assertNull(raid.party.activeSession)
            assertNull(f.manager.rejoinableRaid(player))
            val fresh = f.start(player, seed = 6543L)
            f.manager.handleNpcKill(player, boss, captured)
            f.manager.onLeave(player, raid.instance.id)
            assertSame(fresh, f.manager.raidFor(player))
            assertEquals(ToaRoom.LOBBY, fresh.currentRoom)
            assertTrue(fresh.completedPaths.isEmpty())
        }
    }

    @Test
    fun `native reclaim closes disconnected raid state and supply escrow at the existing deadline`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            f.enterBaba(player)
            val boss = f.baba(raid)
            val logoutTick = f.clock.cycle
            f.events.publish(SessionStateEvent.Logout(player))
            f.instances.tickReclaim(logoutTick + 1)
            f.instances.tickReclaim(logoutTick + 1_999)
            assertSame(raid, f.manager.rejoinableRaid(player))
            assertTrue(boss.isSlotAssigned)
            f.instances.tickReclaim(logoutTick + 2_000)
            assertTrue(raid.instance.isEnded)
            assertTrue(raid.cleanupStarted)
            assertFalse(boss.isSlotAssigned)
            assertNull(f.manager.rejoinableRaid(player))
            assertNull(raid.party.activeSession)
            verify(f.supplies, times(1)).terminalCleanup(raid, player.accountHash)
            verify(f.supplies, times(1)).terminalCleanupRaid(raid)
            f.instances.tickReclaim(logoutTick + 2_001)
            verify(f.supplies, times(1)).terminalCleanupRaid(raid)
        }
    }

    private fun ToaNativeCombatFixture.formParty(size: Int): List<Player> {
        val members = (1..size).map(::player)
        val leader = members.first()
        manager.createParty(leader)
        for (member in members.drop(1)) {
            assertTrue(manager.openPartyView(member, 0))
            assertTrue(manager.applyToViewedParty(member))
            assertTrue(manager.acceptApplicant(leader, 0))
        }
        return members
    }

    @AfterTest fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
