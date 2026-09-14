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
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo

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
    fun `busy native access defers only the ordinary opportunity while owned mechanic queues dispatch`() {
        val f = DukeFixture()
        f.wake()
        f.tick(4)
        var effectRan = false
        requireNotNull(f.run.duke.taskOwner).add(ready = { f.clock.cycle >= 105 }) {
            effectRan = true
        }
        f.boss.delay = 1000
        f.tick()
        assertTrue(effectRan)
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

    @Test
    fun `owned bile deadline precedes ordinary and pool damage precedes drips with exact RNG`() {
        val f = DukeFixture(awakened = true)
        val oracle = Dt2SeededRng(42)
        f.wake()
        f.run.state = Dt2State.ACTIVE
        f.boss.hitpoints = 1000
        f.boss.delay = 1000
        // Invoke the existing gas launcher while ordinary native access is busy. Its four spits
        // reserve +11 for bile; the protected pillar avoids every vent and slam damage roll.
        f.encounter.launchAction(f.run, DukeAction.GAS)
        f.tick(10)
        assertEquals(111, f.boss.actionDelay)
        f.boss.delay = 0
        f.tick()
        assertEquals(116, f.boss.actionDelay, "Due bile must reserve the ordinary slot first")
        assertEquals(0, f.run.attackCounter)
        assertTrue(f.run.duke.pools.isEmpty(), "Bile's newly queued first drip waits one cycle")
        f.boss.delay = 1000
        f.tick()
        val first = f.player.coords
        val firstRotation = oracle.nextInt(4)
        assertEquals(
            LocInfo(2, first, LocEntity(30032, 10, firstRotation)),
            f.run.duke.pools[first],
        )
        assertEquals(121, f.run.duke.poisonTiles[first])
        assertTrue(f.hits.isEmpty(), "The first drip follows this cycle's pool damage check")
        f.tick(3)
        repeat(3) { oracle.nextInt(4) }
        f.hits.clear()
        f.player.previousCoords = f.tile(3034, 6449)
        val middle = f.tile(3034, 6450)
        f.player.lastProcessedCoord = middle
        f.boss.delay = 0
        val expectedDamage = 9 + oracle.nextInt(4)
        val middleRotation = oracle.nextInt(4)
        oracle.nextInt(10000) // Ordinary melee accuracy follows pool damage and new pool rotation.
        f.tick()
        assertEquals(116, f.clock.cycle)
        assertEquals(expectedDamage, f.hits.single().damage)
        assertEquals(
            LocInfo(2, middle, LocEntity(30032, 10, middleRotation)),
            f.run.duke.pools[middle],
        )
        assertEquals(1, f.run.attackCounter)
        assertEquals(listOf(116), f.registry.of(f.boss).recentAbilityCommitTicks())
        f.tick(4)
        repeat(4) { oracle.nextInt(4) }
        f.hits.clear()
        val renewedRotation = oracle.nextInt(4)
        oracle.nextInt(10000)
        f.tick()
        assertEquals(121, f.clock.cycle)
        assertTrue(f.hits.isEmpty(), "Expiry occurs before a tied drip and ordinary launch")
        assertEquals(130, f.run.duke.poisonTiles[first])
        assertEquals(
            LocInfo(2, first, LocEntity(30032, 10, renewedRotation)),
            f.run.duke.pools[first],
        )
        assertEquals(2, f.run.attackCounter)
        assertEquals(oracle.nextInt(Int.MAX_VALUE), f.run.rng.nextInt(Int.MAX_VALUE))
    }

    @Test
    fun `native session retains Duke death presentation after NPC delete then closes replaced owners`() {
        Dt2NativeLifecycleFixture(Dt2Boss.DUKE).use { f ->
            val run = f.enter()
            val boss = run.boss
            val owner = assertNotNull(run.duke.taskOwner)
            fun tile(x: Int, z: Int) = boss.coords.translate(x - 3036, z - 6452)
            fun tick(count: Int = 1) =
                repeat(count) {
                    f.clock.tick()
                    f.events.publish(GameLifecycle.StartCycle)
                    f.queueProcess.process()
                }
            assertSame(run, f.manager.beginBossDeath(boss))
            assertSame(owner, run.duke.taskOwner)
            assertEquals(2, owner.size)
            f.npcs.del(boss, Int.MAX_VALUE)
            assertFalse(boss.isSlotAssigned)
            f.manager.finishBossDeath(boss, run)
            assertEquals(Dt2State.RESPAWNING, run.state)
            assertFalse(owner.isClosed, "The session still owns death scenery after boss deletion")
            tick()
            assertNull(run.duke.scene[tile(3028, 6445)])
            tick()
            assertEquals(47550, run.duke.scene[tile(3028, 6445)]?.id)
            assertEquals(1, owner.size)
            tick()
            assertNull(run.duke.scene[tile(3039, 6433)])
            tick()
            assertEquals(47566, run.duke.scene[tile(3039, 6433)]?.id)
            assertEquals(0, owner.size)
            tick(DukeRules.RESPAWN_TICKS - 4)
            assertEquals(Dt2State.PREPARING, run.state)
            assertNotSame(boss, run.boss)
            assertTrue(owner.isClosed)
            val nextOwner = assertNotNull(run.duke.taskOwner)
            assertNotSame(owner, nextOwner)
            assertFalse(nextOwner.isClosed)
            var staleCallback = false
            nextOwner.add { staleCallback = true }
            f.instances.terminate(run.session)
            assertTrue(nextOwner.isClosed)
            assertEquals(0, nextOwner.size)
            f.queueProcess.processOwned(nextOwner)
            assertFalse(staleCallback)
            assertFailsWith<IllegalStateException> { nextOwner.add {} }
            assertEquals(0, f.worldQueues.size)
        }
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
