package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent

/** Real manager dispatch and native lifecycle; the retained cycle is only the expected oracle. */
@OptIn(InternalApi::class)
class KephriNativeCombatTest {
    @Test
    fun `first player hit arms plus four and only LateCycle can consume native opportunities`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterKephri(player)
            val npc = f.boss(raid)
            val encounter = f.bossEncounters.of(npc)
            assertEquals(KEPHRI_INACTIVE, encounter.currentPhaseName)
            f.attack(npc, player)
            f.dispatchLateCycle(f.clock.cycle + 20)
            assertFalse(raid.encounter.kephriActivated)
            assertEquals(0, encounter.phaseAbilityCount)

            val activatedAt = f.clock.cycle
            f.hitNpc(player, npc, 1)
            assertTrue(raid.encounter.kephriActivated)
            assertEquals(activatedAt + KephriEncounter.FIRST_ATTACK_DELAY, npc.actionDelay)
            f.dispatchLateCycle(npc.actionDelay - 1)
            assertEquals(0, encounter.phaseAbilityCount)
            f.advance(1)
            f.attack(npc, player)
            f.attack(npc, player, op = true)
            assertEquals(0, encounter.phaseAbilityCount, "Unowned native AI has no dispatch token")
            assertTrue(raid.encounter.kephriBombTargets.isEmpty())
            f.dispatchLateCycle()
            assertEquals(1, encounter.phaseAbilityCount)
            assertLaunch(raid, KephriAttackKind.BOMB, f.clock.cycle)
            val records = raid.encounter.kephriBombTargets.toList()
            f.attack(npc, player)
            f.dispatchLateCycle()
            assertEquals(1, encounter.phaseAbilityCount)
            assertEquals(records, raid.encounter.kephriBombTargets)
            assertTrue(
                f.hits(player).isEmpty(),
                "No cache-default magic hit alongside spatial bombs",
            )
            assertTrue(f.selectionRandom.bounds.isEmpty())
            assertTrue(f.accessRandom.bounds.isEmpty())
        }
    }

    @Test
    fun `all shield cycles resume through real recovery with retained bomb special counts and cadence`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val party = f.manager.createParty(player)
            assertTrue(party.invocations.toggle(ToaInvocation.LIVELY_LARVAE))
            f.start(player, seed = 42)
            val raid = f.enterKephri(player)
            val npc = f.boss(raid)
            f.hitNpc(player, npc, 1)
            for (shield in 0..KephriEncounter.FINAL_PHASE) {
                val encounter = f.bossEncounters.of(npc)
                assertEquals(kephriCombatPhase(shield), encounter.currentPhaseName)
                assertEquals(0, encounter.phaseAbilityCount)
                var untilSpecial =
                    when (shield) {
                        0 -> 3
                        1 -> 4
                        else -> 5
                    }
                var nextDung = true
                // Include the logical incubation slot in the first shield and repeated final dung.
                val count = if (shield == 0) 10 else 9
                repeat(count) { index ->
                    val due = npc.actionDelay
                    f.dispatchLateCycle(due - 1)
                    assertEquals(index, encounter.phaseAbilityCount, "shield=$shield before=$due")
                    val agile =
                        raid.npcRoles.count { (actor, role) ->
                            role == ToaNpcRole.ADD &&
                                actor.id == KephriScarabAgile.NPC &&
                                actor.isSlotAssigned &&
                                actor.hitpoints > 0
                        }
                    val expected = KephriAttackCycle.next(shield, untilSpecial, nextDung, agile)
                    untilSpecial = expected.attacksUntilSpecial
                    nextDung = expected.nextSpecialDung
                    f.dispatchLateCycle(due)
                    assertEquals(index + 1, encounter.phaseAbilityCount, "shield=$shield tick=$due")
                    assertLaunch(raid, expected.kind, due)
                    assertEquals(due + expected.nextDelay, npc.actionDelay)
                    assertEquals(expected.nextDelay, encounter.expectedAttackRate())
                    if (expected.kind == KephriAttackKind.MASS_INCUBATION) {
                        val batch =
                            raid.encounter.kephriEggBatches.single {
                                it.spawnsAt == due + KephriEggs.SPAWN_DELAY
                            }
                        val roll = (((42L xor (index + 1).toLong()) and Long.MAX_VALUE) % 3).toInt()
                        assertEquals(3 + roll, batch.darkCount)
                        assertTrue(batch.tiles.none { it in raid.encounter.kephriDungTiles })
                    }
                }
                if (shield < KephriEncounter.FINAL_PHASE) {
                    advanceShield(f, raid, player, npc)
                    assertEquals(shield + 1, raid.encounter.phase)
                    assertEquals(f.clock.cycle + KephriAttackCycle.DUNG_DELAY, npc.actionDelay)
                }
            }
        }
    }

    @Test
    fun `due spatial impact is queued and presented before the same tick ordinary launch`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterKephri(player)
            val npc = f.boss(raid)
            f.hitNpc(player, npc, 1)
            val due = npc.actionDelay
            val oldBomb = KephriBombRuntime(player.coords, due, damage = 12, radius = 0)
            raid.encounter.kephriBombs += oldBomb
            clearInvocations(f.world)
            f.dispatchLateCycle(due)
            assertTrue(oldBomb.hitQueued)
            assertFalse(oldBomb in raid.encounter.kephriBombs)
            assertEquals(1, f.deferredHits(player).size)
            assertEquals(12, f.deferredHits(player).single().second.builder.damage)
            assertLaunch(raid, KephriAttackKind.BOMB, due)
            val output = mockingDetails(f.world).invocations.map { it.method.name }
            val impact = output.indexOfFirst { it.startsWith("spotanimMap") }
            val launch = output.indexOfFirst { it.startsWith("projAnim") }
            assertTrue(impact >= 0 && launch > impact, "Impact before fresh launch: $output")
            f.attack(npc, player)
            assertEquals(1, f.bossEncounters.of(npc).phaseAbilityCount)
            assertEquals(1, raid.encounter.kephriBombTargets.size)
        }
    }

    @Test
    fun `native death and duplicate hook preserve the final three tick completion boundary`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterKephri(player)
            val npc = f.boss(raid)
            f.hitNpc(player, npc, 1)
            repeat(KephriEncounter.FINAL_PHASE) { advanceShield(f, raid, player, npc) }
            val encounter = f.bossEncounters.of(npc)
            f.beginDeathWithDrops(npc, player)
            var budget = 100
            while (npc.activeCoroutine != null && budget-- > 0) f.advanceDeath(npc)
            assertNull(npc.activeCoroutine)
            assertFalse(npc.isSlotAssigned)
            assertTrue(encounter.isEnded)
            assertTrue(raid.encounter.kephriDying)
            assertFalse(raid.completionPending)
            val due = raid.encounter.kephriCompletesAt
            assertEquals(f.clock.cycle + KephriEncounter.DEATH_COMPLETION_TICKS, due)
            val duplicate =
                NpcDeathKillContext(player, npc, f.death.nextLootTrackerEventId(), npc.coords)
            assertTrue(f.death.dispatchDeferredKill(duplicate))
            assertEquals(due, raid.encounter.kephriCompletesAt)
            assertFalse(
                raid.completionPending,
                "Duplicate kill hook cannot complete the retained death early",
            )
            f.dispatchLateCycle(due - 1)
            assertFalse(raid.completionPending)
            f.dispatchLateCycle(due)
            assertTrue(raid.completionPending)
            val spirit =
                raid.npcRoles.entries.single { it.value == ToaNpcRole.COMPLETION_SPIRIT }.key
            f.death.dispatchDeferredKill(duplicate)
            f.dispatchLateCycle(due + 1)
            assertSame(
                spirit,
                raid.npcRoles.entries.single { it.value == ToaNpcRole.COMPLETION_SPIRIT }.key,
            )
        }
    }

    @Test
    fun `logout pauses ordinary dispatch and native teardown replaces all prior phase state`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterKephri(player)
            val npc = f.boss(raid)
            f.hitNpc(player, npc, 1)
            f.dispatchLateCycle(npc.actionDelay)
            val old = f.bossEncounters.of(npc)
            val count = old.phaseAbilityCount
            f.events.publish(SessionStateEvent.Logout(player))
            clearInvocations(f.world)
            f.dispatchLateCycle(f.clock.cycle + 30)
            assertEquals(count, old.phaseAbilityCount)
            assertTrue(mockingDetails(f.world).invocations.isEmpty())
            assertNull(f.manager.raidFor(player))
            f.instances.terminate(raid.instance)
            assertTrue(old.isEnded)
            assertFalse(npc.isSlotAssigned)
            f.dispatchLateCycle(f.clock.cycle + 30)
            assertTrue(mockingDetails(f.world).invocations.isEmpty())
            f.start(player)
            val freshRaid = f.enterKephri(player)
            val fresh = f.boss(freshRaid)
            assertNotSame(npc, fresh)
            assertNotSame(old, f.bossEncounters.of(fresh))
            assertEquals(KEPHRI_INACTIVE, f.bossEncounters.of(fresh).currentPhaseName)
            f.hitNpc(player, fresh, 1)
            f.dispatchLateCycle(fresh.actionDelay)
            assertEquals(1, f.bossEncounters.of(fresh).phaseAbilityCount)
            assertLaunch(freshRaid, KephriAttackKind.BOMB, f.clock.cycle)
        }
    }

    private fun advanceShield(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        player: Player,
        npc: Npc,
    ) {
        val phase = raid.encounter.phase
        val hit = f.hitNpc(player, npc, npc.hitpoints)
        assertTrue(hit.damage >= 0)
        assertEquals(phase + 1, raid.encounter.phase)
        if (raid.encounter.phase == KephriEncounter.FINAL_PHASE) {
            assertFalse(raid.encounter.kephriRecovering)
            assertEquals(0, f.bossEncounters.of(npc).phaseAbilityCount)
            return
        }
        assertTrue(raid.encounter.kephriRecovering)
        assertEquals(KEPHRI_INACTIVE, f.bossEncounters.of(npc).currentPhaseName)
        assertEquals(Int.MAX_VALUE, npc.actionDelay)
        assertEquals(0, f.hitNpc(player, npc, 10).damage, "Recovery rejects incoming damage")
        var budget = 400
        while (raid.encounter.kephriRecovering && budget-- > 0) {
            f.dispatchLateCycle(f.clock.cycle + 1)
            // Resolve native adds through their registered kill owner; no route or swarm oracle.
            raid.npcRoles.entries
                .filter { it.value == ToaNpcRole.ADD }
                .map { it.key }
                .forEach { if (it.isSlotAssigned) f.nativeDeath(it, player) }
        }
        assertFalse(
            raid.encounter.kephriRecovering,
            "Finite native recovery for shield ${phase + 1}",
        )
        assertTrue(raid.encounter.kephriSwarms.isEmpty())
        assertEquals(0, f.bossEncounters.of(npc).phaseAbilityCount)
    }

    private fun assertLaunch(raid: ToaRaidSession, expected: KephriAttackKind, tick: Int) {
        val state = raid.encounter
        val emitted = buildSet {
            if (state.kephriBombTargets.any { it.locksAt == tick + KephriBombs.TARGET_LOCK_DELAY })
                add(KephriAttackKind.BOMB)
            if (
                state.kephriPendingDungStrikes.any {
                    it.resolvesAt == tick + KephriDungStrike.TELEGRAPH_TICKS
                }
            )
                add(KephriAttackKind.DUNG_STRIKE)
            if (state.kephriEggBatches.any { it.spawnsAt == tick + KephriEggs.SPAWN_DELAY })
                add(KephriAttackKind.MASS_INCUBATION)
        }
        assertEquals(setOf(expected), emitted, "Exactly one manager launch at tick=$tick")
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
