package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.SessionStateEvent

/** Native room dispatch preserves the checkpoint's timings and body-role lifecycle. */
@OptIn(InternalApi::class)
class AkkhaNativeScheduleTest {
    @Test
    fun `forced style retains accepted budget and blocking completion rearms overdue style only once`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val party = f.manager.createParty(player)
            assertTrue(party.invocations.toggle(ToaInvocation.STAY_VIGILANT))
            f.start(player)
            val raid = f.enterAkkha(player)
            val source = f.boss(raid)
            val state = raid.akkha
            val queues = assertNotNull(state.combatQueues)
            player.coords = source.coords.translate(-1, 0)
            f.attack(source, player)
            val native = f.bossEncounters.of(source)
            val styleAt = queues.styleAt
            f.advance(styleAt - f.clock.cycle)
            f.queueProcess.process()
            assertEquals(AkkhaStyle.MELEE, state.style)
            f.dispatchLateCycle()
            assertEquals(AkkhaStyle.RANGED, state.style)
            assertEquals(1, native.phaseAbilityCount)
            assertEquals(styleAt + 100, queues.styleAt)
            repeat(5) {
                if (source.actionDelay > f.clock.cycle)
                    f.advance(source.actionDelay - f.clock.cycle)
                f.attack(source, player)
            }
            assertEquals(6, state.attackCounter)
            native.akkhaSpecialHistory().previous = AkkhaSpecial.TRAILING_ORBS
            f.advance(source.actionDelay - f.clock.cycle)
            f.attack(source, player)
            assertEquals(AkkhaState.SPECIAL_ATTACK, state.state)
            assertEquals(setOf(AkkhaSpecial.MEMORY_BLAST), state.activeSpecials.toSet())
            assertEquals(0, state.attackCounter)
            val deadline = queues.completionAt
            assertTrue(deadline > f.clock.cycle)
            val overdue = maxOf(deadline, queues.styleAt) + 20
            f.advance(overdue - f.clock.cycle)
            f.queueProcess.process()
            assertEquals(AkkhaState.SPECIAL_ATTACK, state.state)
            f.dispatchLateCycle()
            assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
            assertEquals(AkkhaStyle.MAGIC, state.style)
            assertEquals(-1, queues.completionAt)
            assertEquals(overdue + 100, queues.styleAt)
            assertEquals(0, state.attackCounter)
            f.dispatchLateCycle(overdue + 1)
            assertEquals(AkkhaStyle.MAGIC, state.style, "No stale forced-style callback survives")
        }
    }

    @Test
    fun `final swap transfers live hitpoints and roles at plus one across successive bodies`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterAkkha(player)
            val outgoing = f.boss(raid)
            enterFinalStand(f, raid, outgoing)
            val queues = assertNotNull(raid.akkha.combatQueues)
            repeat(hitsBeforeFinalStandTeleport(1)) { f.hitNpc(player, outgoing, 1) }
            val swap = assertNotNull(raid.akkha.pendingFinalStandSwap)
            val incoming = raid.spawnedNpcs.single { it.hashCode() == swap.incomingBossActorKey }
            assertEquals(f.clock.cycle, queues.swapAt, "Native Modify arms before one-tick impact")
            val hp = outgoing.hitpoints
            val maximum = outgoing.baseHitpointsLvl
            f.queueProcess.process()
            assertSame(outgoing, f.boss(raid))
            assertSame(swap, raid.akkha.pendingFinalStandSwap)
            f.dispatchLateCycle()
            assertSame(incoming, f.boss(raid))
            assertEquals(ToaNpcRole.HAZARD, raid.npcRoles[outgoing])
            assertEquals(hp, incoming.hitpoints)
            assertEquals(maximum, incoming.baseHitpointsLvl)
            assertEquals(hp, outgoing.hitpoints)
            assertNull(raid.akkha.pendingFinalStandSwap)
            assertEquals(-1, queues.swapAt)
            assertEquals(0, f.bossEncounters.of(incoming).phaseAbilityCount)
            f.dispatchLateCycle(f.clock.cycle + 1)
            assertSame(incoming, f.boss(raid), "Completed swap cannot dispatch twice")
            // The same session owner also serves a subsequent, different body-role exchange.
            repeat(hitsBeforeFinalStandTeleport(1)) { f.hitNpc(player, incoming, 1) }
            assertNotNull(raid.akkha.pendingFinalStandSwap)
            assertSame(queues, raid.akkha.combatQueues)
            f.dispatchLateCycle()
            assertNotSame(incoming, f.boss(raid))
            assertNull(raid.akkha.pendingFinalStandSwap)
        }
    }

    @Test
    fun `offline pending swap pauses and native termination cancels it before fresh admission`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterAkkha(player)
            val outgoing = f.boss(raid)
            enterFinalStand(f, raid, outgoing)
            repeat(hitsBeforeFinalStandTeleport(1)) { f.hitNpc(player, outgoing, 1) }
            val swap = assertNotNull(raid.akkha.pendingFinalStandSwap)
            val queues = assertNotNull(raid.akkha.combatQueues)
            f.events.publish(SessionStateEvent.Logout(player))
            f.dispatchLateCycle(f.clock.cycle + 30)
            assertSame(swap, raid.akkha.pendingFinalStandSwap)
            assertSame(outgoing, f.boss(raid))
            f.instances.terminate(raid.instance)
            assertEquals(-1, queues.swapAt)
            assertFalse(queues.processSwap())
            assertNull(raid.akkha.pendingFinalStandSwap)
            f.start(player)
            val fresh = f.enterAkkha(player)
            assertNotSame(queues, fresh.akkha.combatQueues)
            assertEquals(0, fresh.akkha.attackCounter)
            assertEquals(AkkhaState.NORMAL_COMBAT, fresh.akkha.state)
        }
    }

    @Test
    fun `native removal of an incoming decoy retains the pending swap until terminal cleanup`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterAkkha(player)
            val outgoing = f.boss(raid)
            enterFinalStand(f, raid, outgoing)
            repeat(hitsBeforeFinalStandTeleport(1)) { f.hitNpc(player, outgoing, 1) }
            val swap = assertNotNull(raid.akkha.pendingFinalStandSwap)
            val incoming = raid.spawnedNpcs.single { it.hashCode() == swap.incomingBossActorKey }
            val queues = assertNotNull(raid.akkha.combatQueues)
            val deadline = queues.swapAt
            f.npcs.del(incoming, Int.MAX_VALUE)
            f.dispatchLateCycle(f.clock.cycle + 1)
            assertSame(swap, raid.akkha.pendingFinalStandSwap)
            assertSame(outgoing, f.boss(raid))
            assertEquals(deadline, queues.swapAt, "Missing actors do not consume the pending swap")
            f.instances.terminate(raid.instance)
            assertNull(raid.akkha.pendingFinalStandSwap)
            assertEquals(-1, queues.swapAt)
            assertFalse(queues.processSwap())
        }
    }

    private fun enterFinalStand(f: ToaNativeCombatFixture, raid: ToaRaidSession, source: Npc) {
        // Threshold and lure geometry are covered separately; run the real final-entry payload.
        val method =
            ToaRaidManager::class
                .java
                .getDeclaredMethod(
                    "beginAkkhaFinalStand",
                    ToaRaidSession::class.java,
                    Npc::class.java,
                )
        method.isAccessible = true
        val started = f.clock.cycle
        method.invoke(f.manager, raid, source)
        for (offset in listOf(2, 4, 5)) f.dispatchLateCycle(started + offset)
        assertEquals(AkkhaState.FINAL_STAND, raid.akkha.state)
        assertEquals(3, raid.akkha.finalStandDecoyActorKeys.size)
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
