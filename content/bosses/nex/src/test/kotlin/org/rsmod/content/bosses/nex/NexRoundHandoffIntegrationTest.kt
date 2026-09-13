package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid

/**
 * M26 completed-kill ownership regression, mapped in docs/bosses/nex/NEX_OSRS_AUDIT.md. Capture3284
 * t549 -> t587 -> t790 supports consecutive rounds without re-entry in Grid Master private solo. It
 * does not prove public/private parity or the normal respawn duration. These tests preserve the
 * characterized 50-cycle reset and five-cycle opening, using native final death, world queues,
 * EndCycle, NPC deletion and encounter cleanup. Intermediate guard handoffs use the existing setup
 * helper; rewards are observed at the mocked service boundary.
 */
@OptIn(InternalApi::class)
class NexRoundHandoffIntegrationTest {
    @Test
    fun `two native kills retain occupants without movement or reentry and start fresh rounds`() {
        val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val partner = assertNotNull(f.partner)
        f.player.coords = NEX_SPAWN.translateX(-10)
        partner.coords = NEX_SPAWN.translateX(10)
        val firstTile = f.player.coords
        val secondTile = partner.coords
        f.beforeTeleport = { _, _, _ -> fail("Completed-kill handoff must not teleport occupants") }
        val rewardRounds = mutableListOf<RewardArguments>()

        repeat(2) { index ->
            val round = completeRound(f)
            assertEquals(index + 1, rewardArguments(f).size, "Reward completion must run once")
            rewardRounds += rewardArguments(f).last()
            advanceToDeadline(f, round)

            val fresh = assertFreshOpening(f, round)
            assertTrue(f.manager.isParticipant(f.player))
            assertTrue(f.manager.isParticipant(partner))
            assertEquals(NEX_DROP_DURATION, f.player.lootDropDuration)
            assertEquals(NEX_DROP_DURATION, partner.lootDropDuration)
            assertEquals(firstTile, f.player.coords)
            assertEquals(secondTile, partner.coords)
            f.tick(4)
            assertEquals("SPAWNING", fresh.currentPhaseName)
            f.tick()
            assertEquals("SMOKE", fresh.currentPhaseName)
            assertEquals(0, fresh.phaseAbilityCount)
        }

        val first = rewardRounds[0]
        val second = rewardRounds[1]
        val firstContribution = assertNotNull(first.contributions[f.player.uid])
        val secondContribution = assertNotNull(second.contributions[f.player.uid])
        assertTrue(firstContribution.damage > 0)
        assertNotSame(firstContribution, secondContribution)
        assertEquals(
            firstContribution.damage,
            secondContribution.damage,
            "Damage must not accumulate",
        )
        assertTrue(secondContribution.firstCycle > firstContribution.firstCycle)
        assertNotSame(first.rewarded, second.rewarded, "Per-kill reward ownership must be fresh")
        assertEquals(2, rewardArguments(f).size)
    }

    @Test
    fun `deadline excludes departed dead disconnected and reused player identities`() {
        val departures: List<Pair<String, (NexMinionTestWorld) -> Unit>> =
            listOf(
                "left chamber" to { it.player.coords = NEX_BANK_RETURN },
                "dead before callback" to
                    {
                        it.player.statMap.setCurrentLevel("stat.hitpoints", 0)
                    },
                "disconnected" to { it.player.clientDisconnected.set(true) },
                "pending logout" to { it.player.pendingLogout = true },
                "logging out" to { it.player.loggingOut = true },
                "missing player slot" to { it.players.remove(it.player.slotId) },
                "reused player slot" to
                    { f ->
                        f.players[f.player.slotId] = replacement(f.player, f, preserveUid = false)
                    },
            )
        for ((label, depart) in departures) {
            val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
            val partner = assertNotNull(f.partner)
            val partnerTile = partner.coords
            val round = completeRound(f)
            advanceBeforeDeadline(f, round)
            depart(f)
            f.tick()

            assertFreshOpening(f, round)
            assertFalse(f.manager.isParticipant(f.player), label)
            f.players[f.player.slotId]?.let { assertFalse(f.manager.isParticipant(it), label) }
            assertTrue(f.manager.isParticipant(partner), label)
            assertEquals(partnerTile, partner.coords, label)
            assertEquals(1, rewardArguments(f).size, label)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `cleanup callbacks cannot carry a departed or same uid replacement session`() {
        for (replaceIdentity in listOf(false, true)) {
            val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
            val partner = assertNotNull(f.partner)
            val round = completeRound(f)
            var cleanupCount = 0
            var replacementPlayer: Player? = null
            round.encounter.onCleanup {
                cleanupCount++
                if (replaceIdentity) {
                    // A matching packed UID alone must not defeat the captured Player identity.
                    replacementPlayer =
                        replacement(f.player, f, preserveUid = true).apply {
                            lootDropDuration = 45_678
                        }
                    f.players[f.player.slotId] = assertNotNull(replacementPlayer)
                } else {
                    // Membership can change without movement or a disconnected flag.
                    f.manager.playerLoggedOut(f.player)
                }
            }
            advanceToDeadline(f, round)

            assertFreshOpening(f, round)
            assertEquals(1, cleanupCount)
            assertFalse(f.manager.isParticipant(f.player))
            replacementPlayer?.let {
                assertEquals(f.player.uid, it.uid)
                assertNotSame(f.player, it)
                assertFalse(f.manager.isParticipant(it))
                assertEquals(
                    45_678,
                    it.lootDropDuration,
                    "Old cleanup must not mutate a replacement session",
                )
            }
            assertTrue(f.manager.isParticipant(partner))
            f.tick(6)
            assertEquals(1, cleanupCount)
            assertEquals(1, rewardArguments(f).size)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `native replacement actor creation can invalidate a captured occupant before enrolment`() {
        val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val partner = assertNotNull(f.partner)
        val partnerTile = partner.coords
        val round = completeRound(f)
        var replacementCreates = 0
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            if (npc.type.id in 11278..11286) {
                replacementCreates++
                // Exercise the native actor-creation callback after old-room cleanup.
                f.player.coords = NEX_BANK_RETURN
            }
        }
        advanceToDeadline(f, round)

        assertFreshOpening(f, round)
        assertEquals(5, replacementCreates)
        assertFalse(f.manager.isParticipant(f.player))
        assertEquals(NEX_BANK_RETURN, f.player.coords)
        assertTrue(f.manager.isParticipant(partner))
        assertEquals(partnerTile, partner.coords)
    }

    @Test
    fun `empty completed rounds and uncompleted source loss retain idle reset behavior`() {
        val empty = NexMinionTestWorld(nativeDeaths = true)
        val completed = completeRound(empty)
        advanceBeforeDeadline(empty, completed)
        empty.manager.playerLoggedOut(empty.player)
        empty.players.remove(empty.player.slotId)
        empty.tick()
        assertFalse(completed.boss.isSlotAssigned)
        assertTrue(completed.encounter.isEnded)
        val idle = empty.npc(11278)
        assertEquals("IDLE", empty.encounters.of(idle).currentPhaseName)
        assertFalse(empty.manager.isParticipant(empty.player))
        empty.tick(NEX_RESPAWN_TICKS + 5)
        assertSame(idle, empty.npc(11278))
        assertEquals(5, empty.npcs.count())
        assertEquals(1, rewardArguments(empty).size)
        framework().clearInlineMocks()

        val lost = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val partner = assertNotNull(lost.partner)
        val oldActors = lost.npcs.toList()
        val oldBoss = lost.npc(11278)
        val oldEncounter = lost.encounters.of(oldBoss)
        lost.repository.del(oldBoss, Int.MAX_VALUE)
        lost.tick()
        assertTrue(oldEncounter.isEnded)
        assertTrue(oldActors.none { it.isSlotAssigned })
        assertFalse(lost.manager.isParticipant(lost.player))
        assertFalse(lost.manager.isParticipant(partner))
        assertNull(lost.player.lootDropDuration)
        assertNull(partner.lootDropDuration)
        val fresh = lost.npc(11278)
        assertEquals("IDLE", lost.encounters.of(fresh).currentPhaseName)
        lost.tick(NEX_RESPAWN_TICKS + 5)
        assertSame(fresh, lost.npc(11278))
        assertEquals(5, lost.npcs.count())
        assertTrue(rewardArguments(lost).isEmpty())
    }

    @Test
    fun `retired round wrath and owned callbacks cannot affect its surviving occupant`() {
        val f = NexMinionTestWorld(nativeDeaths = true)
        f.player.coords = NEX_SPAWN.translateX(-1)
        val tile = f.player.coords
        val round = completeRound(f)
        val wrath = f.hits().single().second
        assertTrue(wrath.isValidNow())
        assertSame(round.boss, wrath.resolveNpcSource(f.npcs))
        var oldCallbackRan = false
        round.encounter.schedule(f.worldQueues, NEX_RESPAWN_TICKS + 10) { oldCallbackRan = true }
        advanceToDeadline(f, round)

        val fresh = assertFreshOpening(f, round)
        assertTrue(f.manager.isParticipant(f.player))
        assertEquals(tile, f.player.coords)
        assertFalse(wrath.isValidNow())
        f.processPlayerHits(2)
        assertEquals(99, f.player.hitpoints, "Native impact must reject the retired round")
        f.tick(12)
        assertFalse(oldCallbackRan, "Retired encounter work reached the replacement round")
        assertEquals("SMOKE", fresh.currentPhaseName)
        assertEquals(0, fresh.phaseAbilityCount)
        assertEquals(1, rewardArguments(f).size)
    }

    @Test
    fun `source loss after completed rewards stays idle without retaining occupants`() {
        val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val partner = assertNotNull(f.partner)
        val firstTile = f.player.coords
        val secondTile = partner.coords
        val round = completeRound(f)
        assertTrue(currentRun(f).roundCompleted)
        assertTrue(f.clock.cycle < round.deadline)
        assertEquals(1, rewardArguments(f).size)
        f.repository.del(round.boss, Int.MAX_VALUE)
        f.tick()

        assertTrue(round.encounter.isEnded)
        assertTrue(round.actors.none { it.isSlotAssigned })
        val fresh = f.npc(11278)
        assertNotSame(round.boss, fresh)
        assertEquals("IDLE", f.encounters.of(fresh).currentPhaseName)
        assertFalse(f.manager.isParticipant(f.player))
        assertFalse(f.manager.isParticipant(partner))
        assertNull(f.player.lootDropDuration)
        assertNull(partner.lootDropDuration)
        assertEquals(firstTile, f.player.coords)
        assertEquals(secondTile, partner.coords)
        f.tick(NEX_RESPAWN_TICKS + 5)
        assertSame(fresh, f.npc(11278))
        assertEquals("IDLE", f.encounters.of(fresh).currentPhaseName)
        assertEquals(5, f.npcs.count())
        assertEquals(1, rewardArguments(f).size)
    }

    @Test
    fun `native source deletion inside rewards cannot mark or start a completed round`() {
        val f = NexMinionTestWorld(duo = true, nativeDeaths = true)
        val partner = assertNotNull(f.partner)
        f.reachPhase(NexState.ZAROS)
        val current = currentRun(f)
        val boss = current.boss
        val encounter = current.encounter
        val actors = f.npcs.toList()
        var rewardCalls = 0
        doAnswer {
                rewardCalls++
                assertFalse(current.roundCompleted)
                assertEquals("DYING", encounter.currentPhaseName)
                f.repository.del(boss, Int.MAX_VALUE)
                0
            }
            .`when`(f.rewardService)
            .reward(current.contributions, current.rewarded, current.arena.spawn)
        boss.hitpoints = 1
        f.damage(boss, 1)
        var ticks = 0
        while (boss.isSlotAssigned && ticks++ < 40) f.tick()

        assertEquals(1, rewardCalls)
        assertFalse(
            current.roundCompleted,
            "Successful reward return cannot complete a retired source",
        )
        assertEquals("DYING", encounter.currentPhaseName)
        assertTrue(encounter.isEnded)
        assertTrue(actors.none { it.isSlotAssigned })
        assertFalse(f.manager.isParticipant(f.player))
        assertFalse(f.manager.isParticipant(partner))
        assertNull(f.player.lootDropDuration)
        assertNull(partner.lootDropDuration)
        val fresh = f.npc(11278)
        assertNotSame(boss, fresh)
        assertEquals("IDLE", f.encounters.of(fresh).currentPhaseName)
        f.tick(NEX_RESPAWN_TICKS + 5)
        assertSame(fresh, f.npc(11278))
        assertEquals("IDLE", f.encounters.of(fresh).currentPhaseName)
        assertEquals(5, f.npcs.count())
        assertEquals(1, rewardCalls)
        assertEquals(1, rewardArguments(f).size)
    }

    private fun currentRun(f: NexMinionTestWorld): NexEncounterManager.Run =
        NexEncounterManager::class.java.getDeclaredField("run").run {
            isAccessible = true
            get(f.manager) as NexEncounterManager.Run
        }

    private fun completeRound(f: NexMinionTestWorld): FinishedRound {
        f.reachPhase(NexState.ZAROS)
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        val actors = f.npcs.toList()
        boss.hitpoints = 1
        f.damage(boss, 1)
        var ticks = 0
        while (encounter.currentPhaseName != "RESETTING" && ticks++ < 40) f.tick()
        assertEquals(
            "RESETTING",
            encounter.currentPhaseName,
            "Native death and rewards must finish",
        )
        assertTrue(boss.hidden)
        assertTrue(boss.isSlotAssigned, "Wrath retains its original native source")
        assertEquals(-1, boss.lifecycleRespawnCycle, "No competing native reveal timer")
        // This fixture processes world queues immediately before advancing MapClock. The reward
        // callback therefore set its 50-cycle deadline one clock cycle before this observation.
        return FinishedRound(boss, encounter, actors, f.clock.cycle + NEX_RESPAWN_TICKS - 1)
    }

    private fun advanceBeforeDeadline(f: NexMinionTestWorld, round: FinishedRound) {
        val remaining = round.deadline - f.clock.cycle - 1
        assertTrue(remaining >= 0)
        f.tick(remaining)
        assertTrue(round.boss.isSlotAssigned, "Completed source retired before its reset deadline")
        assertEquals("RESETTING", round.encounter.currentPhaseName)
    }

    private fun advanceToDeadline(f: NexMinionTestWorld, round: FinishedRound) {
        advanceBeforeDeadline(f, round)
        f.tick()
    }

    private fun assertFreshOpening(f: NexMinionTestWorld, old: FinishedRound): BossEncounter {
        assertFalse(
            old.boss.isSlotAssigned,
            "Reset deadline must retire the completed native source",
        )
        assertTrue(
            old.actors.none { it.isSlotAssigned },
            "Old room actors leaked into the next round",
        )
        assertTrue(old.encounter.isEnded)
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        assertNotSame(old.boss, boss)
        assertNotSame(old.encounter, encounter)
        assertEquals("SPAWNING", encounter.currentPhaseName)
        assertEquals(0, encounter.phaseAbilityCount)
        assertEquals(5, f.npcs.count(), "Exactly one Nex and four bodyguards must be recreated")
        assertTrue(f.npcs.filter { it.type.id != 11278 }.all { !it.respawns })
        return encounter
    }

    private fun replacement(original: Player, f: NexMinionTestWorld, preserveUid: Boolean) =
        Player().apply {
            slotId = original.slotId
            uuid = if (preserveUid) original.uuid else 999L
            observerUUID = 999L
            assignUid()
            coords = original.coords
            currentMapClock = f.clock.cycle
            processedMapClock = f.clock.cycle
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }

    @Suppress("UNCHECKED_CAST")
    private fun rewardArguments(f: NexMinionTestWorld): List<RewardArguments> =
        mockingDetails(f.rewardService)
            .invocations
            .filter { it.method.name.startsWith("reward-") }
            .map {
                RewardArguments(
                    it.arguments[0] as Map<PlayerUid, NexContribution>,
                    it.arguments[1] as MutableSet<PlayerUid>,
                )
            }

    private data class FinishedRound(
        val boss: Npc,
        val encounter: BossEncounter,
        val actors: List<Npc>,
        val deadline: Int,
    )

    private data class RewardArguments(
        val contributions: Map<PlayerUid, NexContribution>,
        val rewarded: MutableSet<PlayerUid>,
    )

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
