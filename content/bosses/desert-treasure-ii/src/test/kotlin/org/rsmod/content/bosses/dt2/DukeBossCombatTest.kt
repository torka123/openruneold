package org.rsmod.content.bosses.dt2

import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliate
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

@OptIn(InternalApi::class)
class DukeBossCombatTest {
    @Test
    fun `native owner dispatch completes in StartCycle before movement with no pursuit duplicate`() {
        val f = DukeFixture()
        f.wake()
        f.tick(4)
        f.player.coords = f.tile(3039, 6451)
        f.tick()
        assertEquals(105, f.clock.cycle)
        assertEquals(1, f.run.attackCounter)
        assertEquals(listOf(105), f.registry.of(f.boss).recentAbilityCommitTicks())
        assertEquals(
            HitType.Melee,
            f.hits.single().type,
            "StartCycle selected melee at the pre-movement tile",
        )
        assertEquals(110, f.boss.actionDelay)
        assertEquals(NpcMode.None, f.boss.mode)
        assertTrue(f.boss.movementLocked)
        assertTrue(f.boss.ignoreCombatInteractions)
        f.player.coords = f.tile(3039, 6445)
        f.events.publish(GameLifecycle.LateCycle)
        assertEquals(1, f.run.attackCounter)
        f.tick(2)
        assertEquals(1, f.hits.size, "Slam checks the new position at its existing +2 deadline")
    }

    @Test
    fun `enrage changes subsequent cadence without shortening an outstanding native deadline`() {
        val f = DukeFixture()
        f.wake()
        f.tick(5)
        assertEquals(110, f.boss.actionDelay)
        f.boss.hitpoints = 121
        f.tick(4)
        assertEquals(dukeInitialPhase(false), f.registry.of(f.boss).currentPhaseName)
        assertEquals(1, f.run.attackCounter)
        assertEquals(110, f.boss.actionDelay)
        f.tick()
        assertEquals(2, f.run.attackCounter)
        assertEquals(4, f.registry.of(f.boss).expectedAttackRate())
        assertEquals(114, f.boss.actionDelay)
        assertEquals(listOf(105, 110), f.registry.of(f.boss).recentAbilityCommitTicks())
    }

    @Test
    fun `healing to five ticks preserves the outstanding four tick deadline`() {
        val f = DukeFixture()
        f.wake()
        f.boss.hitpoints = 121
        f.tick(5)
        assertEquals(109, f.boss.actionDelay)
        f.boss.hitpoints = 485
        f.tick(3)
        assertEquals(1, f.run.attackCounter)
        assertEquals(109, f.boss.actionDelay)
        f.tick()
        assertEquals(2, f.run.attackCounter)
        assertEquals(114, f.boss.actionDelay)
        assertEquals(5, f.registry.of(f.boss).expectedAttackRate())
        assertEquals(listOf(105, 109), f.registry.of(f.boss).recentAbilityCommitTicks())
    }

    @Test
    fun `declarative intervals match retained rotation through HP changes and special deadlines`() {
        for (awakened in listOf(false, true)) {
            try {
                val f = DukeFixture(awakened)
                val oracle = DukeRotation()
                val hpLevels =
                    if (awakened) listOf(1697, 1272, 440, 441, 1290, 1289, 1104, 543, 1697)
                    else listOf(485, 363, 121, 122, 242, 121, 485)
                f.wake()
                var traceCount = 0
                var previousAction: DukeAction? = null
                var previousTick = f.clock.cycle
                repeat(900) { cycle ->
                    // Change thresholds during intervals, including both damage and healing.
                    f.boss.hitpoints = hpLevels[(cycle / 47) % hpLevels.size]
                    f.tick()
                    val traces = f.registry.of(f.boss).attackTracesAfter(previousTick)
                    assertTrue(traces.size <= 1)
                    for (trace in traces) {
                        val actual = DukeAction.valueOf(trace.ability.split('.')[1])
                        val expected = oracle.next(f.boss.hitpoints, awakened)
                        assertEquals(expected, actual, "awakened=$awakened cycle=$cycle")
                        assertEquals(oracle.standards, f.run.attackCounter)
                        if (actual == DukeAction.GAS)
                            assertNotEquals(DukeAction.GAZE, previousAction)
                        val expectedDelay =
                            when (actual) {
                                DukeAction.STANDARD -> DukeRules.rate(f.boss.hitpoints, awakened)
                                DukeAction.GAZE -> 6
                                DukeAction.GAS -> {
                                    val count = DukeRules.gasCount(f.boss.hitpoints, awakened)
                                    if (count == 1) DukeRules.rate(f.boss.hitpoints, awakened)
                                    else
                                        DukeRules.gasOffsets(count).last() +
                                            if (f.run.enraged) 3 else 4
                                }
                            }
                        assertEquals(
                            f.clock.cycle + expectedDelay,
                            f.boss.actionDelay,
                            "awakened=$awakened action=$actual cycle=$cycle",
                        )
                        previousAction = actual
                        traceCount++
                    }
                    previousTick = f.clock.cycle
                }
                assertTrue(
                    traceCount > 100,
                    "Differential coverage must span many latched intervals",
                )
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `busy native access defers only the ordinary opportunity while the mechanic timeline pulses`() {
        val f = DukeFixture()
        f.wake()
        f.tick(4)
        var timelineRan = false
        f.run.duke.timeline.at(105, f.run.generation) { timelineRan = true }
        f.boss.delay = 1000
        f.tick()
        assertTrue(timelineRan)
        assertEquals(0, f.run.attackCounter)
        assertEquals(105, f.boss.actionDelay)
        assertTrue(f.registry.of(f.boss).recentAbilityCommitTicks().isEmpty())
        f.boss.delay = 0
        f.tick()
        assertEquals(1, f.run.attackCounter)
        assertEquals(111, f.boss.actionDelay)
        assertEquals(listOf(106), f.registry.of(f.boss).recentAbilityCommitTicks())
    }

    @Test
    fun `finite busy delay expires after StartCycle and dispatch keeps the global action clock`() {
        val f = DukeFixture()
        f.wake()
        f.tick(4)
        assertEquals(104, f.boss.processedMapClock)
        f.boss.delay = 105
        f.tick()
        assertEquals(0, f.run.attackCounter, "StartCycle still sees the previous processed clock")
        assertTrue(f.launchClocks.isEmpty())
        assertEquals(105, f.boss.actionDelay)
        assertFalse(f.boss.isBusy, "The later native main pass advances the processed clock")
        f.tick()
        assertEquals(listOf(106 to 105), f.launchClocks)
        assertEquals(listOf(106), f.registry.of(f.boss).recentAbilityCommitTicks())
        assertEquals(
            111,
            f.boss.actionDelay,
            "BossCombat uses the global clock, not access.mapClock",
        )
        assertEquals(1, f.run.attackCounter)
    }

    @Test
    fun `native retaliation cannot overwrite deadline while normal player damage remains active`() {
        val f = DukeFixture()
        f.wake()
        f.tick(5)
        f.boss.queueCombatRetaliate(f.player)
        f.boss.combatDefaultRetaliate(mock(AiPlayerInteractions::class.java))
        assertEquals(NpcMode.None, f.boss.mode)
        assertEquals(110, f.boss.actionDelay)
        val hit =
            HitBuilder(
                    type = HitType.Melee,
                    damage = 10,
                    sourceUid = f.player.uid.packed,
                    sourceSlot = f.player.slotId,
                    isFromNpc = false,
                    isFromPlayer = true,
                    clientDelay = 0,
                    righthandType = null,
                    secondaryType = null,
                    targetHitmark = 0,
                    sourceHitmark = 0,
                    publicHitmark = null,
                    zeroDamageHitmarkLit = null,
                    zeroDamageHitmarkTint = null,
                    maxDamageHitmarkLit = null,
                    targetMaxDamageThreshold = Int.MAX_VALUE,
                    sourceMaxDamageThreshold = Int.MAX_VALUE,
                )
                .build()
        f.launcher.launch(f.boss) { processQueuedHit(hit) }
        assertEquals(475, f.boss.hitpoints)
        f.events.publish(NpcStateEvents.Respawn(f.boss))
        assertFalse(f.boss.ignoreCombatInteractions, "Native owner reset clears the override")
        f.encounter.prepare(f.run)
        assertTrue(f.boss.ignoreCombatInteractions)
        f.encounter.clear(f.run)
        assertFalse(f.boss.ignoreCombatInteractions, "Content cleanup also restores the default")
        assertNull(f.registry.existing(f.boss))
    }

    @AfterTest
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            DukeRuntimeTest.cache()
        }
    }
}
