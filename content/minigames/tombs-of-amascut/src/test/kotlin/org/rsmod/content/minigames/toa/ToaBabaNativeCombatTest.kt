package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.HitType

/** Native AI/NvP integration of the retained Ba-Ba tuning and callbacks, not new mechanics. */
@OptIn(InternalApi::class)
class ToaBabaNativeCombatTest {
    @Test
    fun `intro rejects combat and both native interaction routes share exact six tick cadence`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player, activate = false)
            val npc = f.baba(raid)
            val ready = raid.baba.specialEndsAt
            f.attack(npc, player)
            assertEquals(BabaState.INTRO, raid.baba.state)
            assertEquals(0, raid.baba.attackCounter)
            assertTrue(f.hits(player).isEmpty())
            assertTrue(f.accessRandom.bounds.isEmpty())
            f.dispatchLateCycle(ready - 1)
            assertEquals(BabaState.INTRO, raid.baba.state)
            f.dispatchLateCycle(ready)
            assertEquals(BabaState.NORMAL_COMBAT, raid.baba.state)

            f.attack(npc, player)
            val encounter = f.bossEncounters.of(npc)
            val hit = f.hits(player).single()
            assertEquals(1, hit.first)
            assertEquals(HitType.Melee, hit.second.type)
            assertEquals(assertNotNull(f.manager.combatProfile(npc)).maxHit, hit.second.damage)
            assertEquals(ready + 6, npc.actionDelay)
            assertEquals(1, raid.baba.attackCounter)
            assertEquals(1, encounter.phaseAbilityCount)
            f.attack(npc, player, op = true)
            f.advance(5)
            f.attack(npc, player)
            assertEquals(1, f.hits(player).size)
            assertEquals(1, raid.baba.attackCounter)
            f.advance(1)
            f.attack(npc, player, op = true)
            assertEquals(2, raid.baba.attackCounter)
            assertEquals(2, encounter.phaseAbilityCount)
            assertEquals(
                listOf(ready, ready + 6),
                encounter.attackTracesAfter(0).map { it.commitTick },
            )
            assertEquals(
                listOf(hit.second.damage + 1, hit.second.damage + 1),
                f.accessRandom.bounds,
            )
            assertTrue(f.selectionRandom.bounds.isEmpty(), "Singleton native rotation adds no RNG")
        }
    }

    @Test
    fun `invalid target resets at readiness but held action delay preserves interaction without draws`() {
        for (hidden in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                f.start(player)
                val raid = f.enterBaba(player)
                val npc = f.baba(raid)
                val mode = assertNotNull(npc.mode)
                npc.actionDelay = f.clock.cycle + 1
                if (hidden) player.hidden = true
                else player.statMap.setCurrentLevel("stat.hitpoints", 0)
                f.attack(npc, player)
                assertSame(
                    mode,
                    npc.mode,
                    "Cooldown returns before invalid-target reset: hidden=$hidden",
                )
                assertTrue(f.hits(player).isEmpty())
                assertEquals(0, raid.baba.attackCounter)
                f.advance(1)
                f.attack(npc, player)
                assertNull(npc.mode, "Native invalid target reset: hidden=$hidden")
                assertEquals(0, raid.baba.attackCounter)
                assertTrue(f.accessRandom.bounds.isEmpty())
                assertTrue(f.selectionRandom.bounds.isEmpty())
                assertEquals(0, f.bossEncounters.existing(npc)?.phaseAbilityCount ?: 0)
            }
        }
    }

    @Test
    fun `two accepted autos defer special until readiness and rolling thresholds take priority`() {
        for (rolling in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                f.start(player)
                val raid = f.enterBaba(player)
                val npc = f.baba(raid)
                f.attack(npc, player)
                f.advance(6)
                f.attack(npc, player)
                val encounter = f.bossEncounters.of(npc)
                val traces = encounter.attackTracesAfter(0).toList()
                if (rolling) raid.baba.pendingRollingThresholds += 1
                val ordinaryBounds = f.accessRandom.bounds.toList()
                f.advance(5)
                f.attack(npc, player)
                assertEquals(BabaState.NORMAL_COMBAT, raid.baba.state)
                assertEquals(2, raid.baba.attackCounter)
                f.advance(1)
                f.attack(npc, player)
                assertNotEquals(BabaState.NORMAL_COMBAT, raid.baba.state)
                if (rolling) {
                    assertEquals(BabaState.ROLLING_STONES_PREP, raid.baba.state)
                    assertEquals(1, raid.baba.activeRollingThreshold)
                    assertNull(raid.baba.lastSpecial)
                    assertEquals(f.clock.cycle + 4, npc.actionDelay)
                } else {
                    assertNotNull(raid.baba.lastSpecial)
                    assertTrue(npc.actionDelay > f.clock.cycle)
                }
                f.attack(npc, player, op = true)
                assertEquals(2, f.hits(player).size)
                assertEquals(2, encounter.phaseAbilityCount)
                assertEquals(traces, encounter.attackTracesAfter(0))
                assertEquals(ordinaryBounds, f.accessRandom.bounds)
                assertTrue(f.selectionRandom.bounds.isEmpty())
            }
        }
    }

    @Test
    fun `accepted callback retargets next auto while accuracy and damage retain original recipient`() {
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val partner = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, partner)
            f.start(leader, partySize = 2)
            val raid = f.enterBaba(leader)
            val npc = f.baba(raid)
            partner.statMap.setCurrentLevel("stat.hitpoints", 50)
            assertEquals(leader.uid, raid.baba.target)
            f.attack(npc, leader)
            assertEquals(partner.uid, raid.baba.target)
            assertEquals(1, raid.baba.attackCounter)
            assertEquals(listOf(leader), f.accuracyTargets)
            assertEquals(1, f.hits(leader).size)
            assertTrue(f.hits(partner).isEmpty())
            val firstBounds = f.accessRandom.bounds.toList()
            f.attack(npc, partner)
            assertEquals(1, raid.baba.attackCounter)
            f.advance(6)
            f.accurate = false
            f.attack(npc, partner)
            assertEquals(listOf(leader, partner), f.accuracyTargets)
            assertEquals(0, f.hits(partner).single().second.damage)
            assertEquals(2, raid.baba.attackCounter, "Accepted inaccurate launches still count")
            assertEquals(2, f.bossEncounters.of(npc).phaseAbilityCount)
            assertEquals(
                firstBounds,
                f.accessRandom.bounds,
                "Inaccurate default hit adds no damage draw",
            )
            assertTrue(f.selectionRandom.bounds.isEmpty())
        }
    }

    @Test
    fun `native prayer modifier snapshots launch while deadly prayer callback observes actual impact`() {
        for (quiet in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val party = f.manager.createParty(player)
                assertTrue(party.invocations.toggle(ToaInvocation.DEADLY_PRAYERS))
                if (quiet) assertTrue(party.invocations.toggle(ToaInvocation.QUIET_PRAYERS))
                f.start(player)
                val raid = f.enterBaba(player)
                val npc = f.baba(raid)
                val maxHit = assertNotNull(f.manager.combatProfile(npc)).maxHit
                VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 1)
                f.attack(npc, player)
                val protectedHit = f.hits(player).single().second
                assertEquals(if (quiet) maxHit * 10 / 100 else 0, protectedHit.damage)
                VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 0)
                f.advance(1)
                f.processHit(player, protectedHit)
                assertEquals(99 - protectedHit.damage, player.hitpoints)
                assertEquals(99, player.stat("stat.prayer"))

                f.advance(5)
                f.attack(npc, player)
                val unprotectedHit = f.hits(player).last().second
                assertEquals(maxHit, unprotectedHit.damage)
                VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 1)
                f.advance(1)
                f.processHit(player, unprotectedHit)
                assertEquals(99 - protectedHit.damage - maxHit, player.hitpoints)
                assertEquals(99 - maxHit / 5, player.stat("stat.prayer"))
                assertEquals(2, raid.baba.attackCounter)
            }
        }
    }

    @Test
    fun `room replacement retires old hit and real suspended death completes after native deletion only once`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val first = f.baba(raid)
            f.attack(first, player)
            val oldHit = f.hits(player).single().second
            val oldEncounter = f.bossEncounters.of(first)
            val generation = raid.baba.generation
            f.enterBaba(player)
            val second = f.baba(raid)
            assertNotSame(first, second)
            assertTrue(oldEncounter.isEnded)
            assertFalse(oldHit.isValidNow())
            assertTrue(raid.baba.generation > generation)
            assertEquals(0, raid.baba.attackCounter)
            f.processHit(player, oldHit)
            assertEquals(99, player.hitpoints)
            f.attack(second, player)
            assertEquals(1, f.bossEncounters.of(second).phaseAbilityCount)
            f.beginDeathWithDrops(second, player)
            assertNotNull(second.activeCoroutine)
            assertFalse(raid.completionPending)
            var budget = 100
            while (second.activeCoroutine != null && budget-- > 0) f.advanceDeath(second)
            assertNull(second.activeCoroutine, "Native death continuation must finish")
            assertFalse(second.isSlotAssigned)
            assertTrue(raid.completionPending)
            // Existing completePathBoss clears encounter resources after the DEFEATED handoff.
            assertEquals(BabaState.WAITING, raid.baba.state)
            val spirit =
                raid.npcRoles.entries.single { it.value == ToaNpcRole.COMPLETION_SPIRIT }.key
            // Replay the kill delivery after the real deletion; deleting an unassigned NPC is
            // invalid.
            val callback =
                NpcDeathKillContext(player, second, f.death.nextLootTrackerEventId(), second.coords)
            assertTrue(f.death.dispatchDeferredKill(callback))
            assertSame(
                spirit,
                raid.npcRoles.entries.single { it.value == ToaNpcRole.COMPLETION_SPIRIT }.key,
            )

            f.events.publish(SessionStateEvent.Logout(player))
            assertNull(f.manager.raidFor(player))
            assertFalse(f.manager.attackTargetValid(player, second))
            assertTrue(f.death.dispatchDeferredKill(callback))
            assertTrue(raid.completionPending, "Detached completion cannot resurrect the raid")
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
