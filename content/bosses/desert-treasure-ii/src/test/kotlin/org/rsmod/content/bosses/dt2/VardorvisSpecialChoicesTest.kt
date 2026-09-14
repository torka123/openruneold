package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
class VardorvisSpecialChoicesTest {
    @Test
    fun `native choices match retained rules at the HP boundary for every previous and explicit input`() {
        for (seed in listOf(0L, 42L, -1L)) {
            val f = SelectionFixture(seed)
            var tick = 100
            for (hp in listOf(700, 570, 569, 1)) {
                for (previous in listOf(null) + VardorvisSpecial.entries) {
                    for (requested in
                        listOf(
                            null,
                            "unknown",
                            "AXES",
                            "HEAD_GAZE",
                            "STRANGLE",
                            "DARTING_SPIKES",
                            "sTrAnGlE",
                        )) {
                        f.encounter.vardorvisSpecialHistory().previous = previous
                        f.oracle.previous = previous
                        f.compare(
                            hp,
                            requested,
                            tick++,
                            "seed=$seed hp=$hp previous=$previous requested=$requested",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `native history preserves long mixed sequences and subsequent jitter and hazard RNG`() {
        val randomUnlockedResults = mutableSetOf<VardorvisSpecial>()
        for (seed in listOf(1L, 42L, Long.MIN_VALUE)) {
            val f = SelectionFixture(seed)
            repeat(256) { index ->
                val hp =
                    when (index % 6) {
                        0,
                        1 -> 570
                        2,
                        3 -> 569
                        4 -> 700
                        else -> 1
                    }
                val requested =
                    when (index % 17) {
                        0,
                        1 -> "STRANGLE"
                        2 -> "AXES"
                        3 -> "HEAD_GAZE"
                        4 -> "unrecognised"
                        5 -> "DARTING_SPIKES"
                        else -> null
                    }
                val chosen = f.compare(hp, requested, 100 + index, "seed=$seed opportunity=$index")
                if (hp < 570 && requested == null) randomUnlockedResults += chosen
            }
        }
        assertEquals(
            setOf(
                VardorvisSpecial.DARTING_SPIKES,
                VardorvisSpecial.HEAD_GAZE,
                VardorvisSpecial.STRANGLE,
            ),
            randomUnlockedResults,
        )
    }

    @Test
    fun `locked forced choices retain logical history and consume no selection draw`() {
        val f = SelectionFixture(42)
        assertEquals(
            VardorvisSpecial.STRANGLE,
            f.compare(570, "STRANGLE", 100, "locked forced strangle"),
        )
        assertEquals(VardorvisSpecial.STRANGLE, f.encounter.vardorvisSpecialHistory().previous)
        assertEquals(
            VardorvisSpecial.DARTING_SPIKES,
            f.compare(569, "STRANGLE", 101, "consecutive logical strangle coerces to darts"),
        )
        assertEquals(
            VardorvisSpecial.HEAD_GAZE,
            f.compare(570, "HEAD_GAZE", 102, "locked forced head"),
        )
        assertEquals(VardorvisSpecial.HEAD_GAZE, f.encounter.vardorvisSpecialHistory().previous)
        assertEquals(VardorvisSpecial.AXES, f.compare(700, "AXES", 103, "explicit-only axes"))
        assertEquals(
            VardorvisSpecial.DARTING_SPIKES,
            f.compare(570, null, 104, "locked random still draws"),
        )
    }

    @Test
    fun `branch choices interleave with native melee without changing cadence trace facing or exact movement`() {
        val f = Dt2NativeCombatFixture()
        // Manager integration is independent of this helper test. Install the selection adapter
        // before the first ordinary launch; a later registry lookup cannot replace its RNG.
        f.registry.remove(f.boss)
        val encounter = f.registry.of(f.boss, VardorvisSelectionRandom(f.run.rng))
        val oracle = LegacyOracle(42)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        assertEquals(105, f.boss.actionDelay)
        assertEquals("melee", encounter.lastAbilityName)
        assertEquals(listOf(100), encounter.recentAbilityCommitTicks())

        val collision = CollisionFlagMap()
        for (x in -8..8 step 8) for (z in -8..8 step 8) {
            collision.allocateIfAbsent(
                f.boss.coords.x + x,
                f.boss.coords.z + z,
                f.boss.coords.level,
            )
        }
        PathingEntityCommon.exactMove(
            f.boss,
            f.boss.coords.translateX(1),
            f.boss.coords,
            0,
            30,
            0,
            collision,
        )
        f.boss.movementLocked = false
        f.boss.lockFacing(f.player.coords, targetWidth = 2, targetLength = 3)
        val exactMove = assertNotNull(f.boss.pendingExactMove)
        val coords = f.boss.coords
        val facing = f.boss.faceLockSquare
        val phase = encounter.currentPhaseName
        val phaseTick = encounter.phaseEnteredTick
        val traces = encounter.recentAttackTraces()
        val count = encounter.phaseAbilityCount
        val rate = encounter.expectedAttackRate()
        val combatRolls = f.rollBounds.toList()

        for ((index, requested) in
            listOf(null, "STRANGLE", "STRANGLE", "AXES", "unknown").withIndex()) {
            f.boss.hitpoints = if (index % 2 == 0) 569 else 570
            assertEquals(
                oracle.next(f.boss.hitpoints, requested),
                encounter.selectVardorvisSpecial(f.clock.cycle, requested),
            )
            assertEquals(oracle.rng.nextInt(3), f.run.rng.nextInt(3))
            assertEquals(oracle.rng.nextInt(31), f.run.rng.nextInt(31))
            assertEquals("melee", encounter.lastAbilityName)
            assertEquals(100, encounter.lastAbilityTick)
            assertEquals(traces, encounter.recentAttackTraces())
            assertEquals(count, encounter.phaseAbilityCount)
            assertEquals(rate, encounter.expectedAttackRate())
            assertEquals(105, f.boss.actionDelay)
            assertEquals(phase, encounter.currentPhaseName)
            assertEquals(phaseTick, encounter.phaseEnteredTick)
            assertEquals(facing, f.boss.faceLockSquare)
            assertEquals(2, f.boss.faceLockWidth)
            assertEquals(3, f.boss.faceLockLength)
            assertEquals(coords, f.boss.coords)
            assertEquals(exactMove, f.boss.pendingExactMove)
            assertFalse(f.boss.movementLocked)
            assertEquals(combatRolls, f.rollBounds)
        }
        f.advance(4)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        assertEquals(traces, encounter.recentAttackTraces())
        f.advance(1)
        f.attack()
        assertEquals(2, f.run.attackCounter)
        assertEquals(110, f.boss.actionDelay)
        assertEquals(listOf(100, 105), encounter.recentAbilityCommitTicks())
        assertEquals("melee", encounter.lastAbilityName)
        assertEquals(oracle.rng.nextInt(Int.MAX_VALUE), f.run.rng.nextInt(Int.MAX_VALUE))
    }

    @AfterTest
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    private class SelectionFixture(seed: Long) {
        val rng = Dt2SeededRng(seed)
        val oracle = LegacyOracle(seed)
        val npc =
            Npc(
                    requireNotNull(ServerCacheManager.getNpc(Dt2Boss.VARDORVIS.normalNpcId)),
                    CoordGrid(1127, 3419),
                )
                .apply {
                    baseHitpointsLvl = 700
                    hitpoints = 700
                }
        val encounter = BossEncounter(npc, vardorvisBossSpec(), VardorvisSelectionRandom(rng))

        fun compare(hp: Int, requested: String?, tick: Int, context: String): VardorvisSpecial {
            npc.hitpoints = hp
            val expected = oracle.next(hp, requested)
            val actual = encounter.selectVardorvisSpecial(tick, requested)
            assertEquals(expected, actual, context)
            assertEquals(oracle.previous, encounter.vardorvisSpecialHistory().previous, context)
            assertEquals(oracle.rng.nextInt(3), rng.nextInt(3), "$context: subsequent jitter")
            for (bound in listOf(4, 31, Int.MAX_VALUE)) {
                assertEquals(
                    oracle.rng.nextInt(bound),
                    rng.nextInt(bound),
                    "$context: subsequent hazard bound=$bound",
                )
            }
            return actual
        }
    }

    /** The pre-migration selection and draw order from 313571b00, independent of the DSL helper. */
    private class LegacyOracle(seed: Long) {
        val rng = Dt2SeededRng(seed)
        var previous: VardorvisSpecial? = null

        fun next(hp: Int, requested: String?): VardorvisSpecial {
            val explicit =
                requested?.let { key ->
                    VardorvisSpecial.entries.firstOrNull { it.name.equals(key, true) }
                }
            val selected =
                explicit
                    ?: run {
                        val roll = rng.nextInt(Int.MAX_VALUE)
                        val choices =
                            when {
                                hp >= 570 -> listOf(VardorvisSpecial.DARTING_SPIKES)
                                previous == VardorvisSpecial.STRANGLE ->
                                    listOf(
                                        VardorvisSpecial.DARTING_SPIKES,
                                        VardorvisSpecial.HEAD_GAZE,
                                    )
                                else ->
                                    listOf(
                                        VardorvisSpecial.DARTING_SPIKES,
                                        VardorvisSpecial.HEAD_GAZE,
                                        VardorvisSpecial.STRANGLE,
                                    )
                            }
                        choices[Math.floorMod(roll, choices.size)]
                    }
            val result =
                if (selected == VardorvisSpecial.STRANGLE && previous == VardorvisSpecial.STRANGLE)
                    VardorvisSpecial.DARTING_SPIKES
                else selected
            previous = result
            return result
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
