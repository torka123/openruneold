package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Npc

/** Count ownership is tested with actual accepted NvP launches, never a mirrored test counter. */
@OptIn(InternalApi::class)
class AkkhaCombatPhasesTest {
    @Test
    fun `seven or six accepted autos survive prepared styles and a full shadow lure before reset`() {
        for (stay in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val party = f.manager.createParty(player)
                if (stay) assertTrue(party.invocations.toggle(ToaInvocation.STAY_VIGILANT))
                f.start(player)
                val raid = f.enterAkkha(player)
                val npc = f.boss(raid)
                val state = raid.akkha
                val native = f.bossEncounters.of(npc)
                val phases =
                    assertNotNull(native.stateOrNull<AkkhaCombatPhases>(AKKHA_COMBAT_PHASES))
                val budget = AkkhaInvocationRules.attacksBeforeSpecial(stay)
                player.coords = npc.coords.translate(-1, 0)
                repeat(budget) { index ->
                    if (index > 0) f.advance(npc.actionDelay - f.clock.cycle)
                    if (index == 2) {
                        state.queueCrossedThresholds(400, 320, 400)
                        assertEquals(80, state.beginNextShadow(f.clock.cycle))
                    }
                    if (index == 4) {
                        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, f.clock.cycle))
                        assertTrue(state.confirmLure(AkkhaQuadrant.BURN))
                    }
                    val style = AkkhaStyle.entries[index % AkkhaStyle.entries.size]
                    setStyle(state, npc, style)
                    val previous = phases.actionCount
                    f.attack(npc, player)
                    assertEquals(previous + 1, phases.actionCount, "stay=$stay auto=$index")
                    assertEquals(index + 1, state.attackCounter)
                    assertEquals(style.hitType, f.hits(player).last().second.type)
                    assertEquals(TOA_AKKHA_COMBAT_PHASE, native.currentPhaseName)
                    assertEquals(f.clock.cycle + 6, npc.actionDelay)
                    f.attack(npc, player, op = true)
                    assertEquals(index + 1, phases.actionCount, "Held AI cannot spend the budget")
                }
                assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
                assertEquals(budget, phases.actionCount)
                assertEquals(budget, f.hits(player).size)
                assertEquals(6, native.expectedAttackRate())
                val lastTick = native.lastAbilityTick
                val traces = native.recentAbilityCommitTicks()
                native.akkhaSpecialHistory().previous = AkkhaSpecial.MEMORY_BLAST
                f.advance(npc.actionDelay - f.clock.cycle)
                val oldStyle = state.style
                f.attack(npc, player)
                assertEquals(AkkhaSpecial.TRAILING_ORBS, native.akkhaSpecialHistory().previous)
                assertEquals(oldStyle.next(), state.style)
                assertEquals(0, phases.actionCount)
                assertEquals(0, state.attackCounter)
                assertEquals(
                    lastTick,
                    native.lastAbilityTick,
                    "Reset retains native cadence history",
                )
                assertEquals(traces, native.recentAbilityCommitTicks())
                assertEquals(budget, f.hits(player).size, "The special is not an ordinary commit")
                f.advance(npc.actionDelay - f.clock.cycle)
                f.attack(npc, player)
                assertEquals(1, phases.actionCount)
                assertEquals(1, state.attackCounter)
                assertEquals(0L, native.decisionDrawCount(TOA_AKKHA_STYLE_STREAM))
                assertTrue(f.selectionRandom.bounds.isEmpty())
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `blocked launch does not spend budget and reset with replacement cannot reuse the old native count`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterAkkha(player)
            val npc = f.boss(raid)
            val native = f.bossEncounters.of(npc)
            val phases = assertNotNull(native.stateOrNull<AkkhaCombatPhases>(AKKHA_COMBAT_PHASES))
            player.coords = npc.coords.translate(-4, 0)
            f.attack(npc, player)
            assertEquals(0, phases.actionCount)
            assertTrue(native.recentAbilityCommitTicks().isEmpty())
            player.coords = npc.coords.translate(-1, 0)
            f.attack(npc, player)
            assertEquals(1, phases.actionCount)
            val oldActionDelay = npc.actionDelay
            val oldLastTick = native.lastAbilityTick
            raid.akkha.startSpecial(listOf(AkkhaSpecial.MEMORY_BLAST))
            assertEquals(0, phases.actionCount)
            assertEquals(oldActionDelay, npc.actionDelay)
            assertEquals(oldLastTick, native.lastAbilityTick)
            assertEquals(AkkhaStyle.MELEE, raid.akkha.style)
            raid.akkha.finishSpecial()
            assertEquals(AkkhaStyle.RANGED, raid.akkha.style)
            raid.akkha.finishSpecial()
            assertEquals(AkkhaStyle.RANGED, raid.akkha.style)
            val freshRaid = f.enterAkkha(player)
            val fresh = f.boss(freshRaid)
            assertNotSame(npc, fresh)
            assertTrue(native.isEnded)
            assertEquals(0, freshRaid.akkha.attackCounter)
            assertEquals(0, f.bossEncounters.of(fresh).phaseAbilityCount)
            assertFailsWith<IllegalStateException> { phases.resetForSpecial() }
        }
    }

    private fun setStyle(state: AkkhaEncounter, npc: Npc, style: AkkhaStyle) {
        state.style = style
        npc.transmog(assertNotNull(ServerCacheManager.getNpc(style.npcId)), Int.MAX_VALUE)
        npc.apRangeOverride = akkhaAttackRange(style)
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
