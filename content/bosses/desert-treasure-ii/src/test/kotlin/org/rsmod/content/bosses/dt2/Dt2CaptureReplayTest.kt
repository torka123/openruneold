package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.api.instances.RegionLocal

class Dt2CaptureReplayTest {
    @Test
    fun `capture-backed normal hitpoints match current encounters`() {
        assertEquals(900, Dt2Boss.WHISPERER.normalHitpoints)
        assertEquals(700, Dt2Boss.VARDORVIS.normalHitpoints)
        assertEquals(900, Dt2Boss.LEVIATHAN.normalHitpoints)
        assertEquals(485, Dt2Boss.DUKE.normalHitpoints)
    }

    @Test
    fun `awakened hitpoints are independent from normal baseline`() {
        assertEquals(2_700, Dt2Boss.WHISPERER.awakenedHitpoints)
        assertEquals(1_400, Dt2Boss.VARDORVIS.awakenedHitpoints)
        assertEquals(2_700, Dt2Boss.LEVIATHAN.awakenedHitpoints)
        assertEquals(1_697, Dt2Boss.DUKE.awakenedHitpoints)
    }

    @Test
    fun `every public command has a distinct compatibility alias and instance key`() {
        assertEquals(4, Dt2Boss.entries.map { it.command }.toSet().size)
        assertEquals(4, Dt2Boss.entries.map { it.alias }.toSet().size)
        assertEquals(4, Dt2Boss.entries.map { it.key }.toSet().size)
        Dt2Boss.entries.forEach {
            assertNotEquals(it.command, it.alias)
            assertTrue(it.key.startsWith(DT2_INSTANCE_PREFIX))
        }
    }

    @Test
    fun `normal whisperer replays three threshold specials in seeded order`() {
        val thresholds = Dt2Rules.whispererThresholds(Dt2Difficulty.NORMAL)
        assertEquals(listOf(720, 495, 270), thresholds)
        assertEquals(
            listOf(
                WhispererSpecial.CORRUPTED_SEEDS,
                WhispererSpecial.SCREECH,
                WhispererSpecial.SOUL_SIPHON,
            ),
            thresholds.indices.map { Dt2Rules.whispererSpecial(it, 0) },
        )
    }

    @Test
    fun `awakened whisperer has all seven capture thresholds`() {
        val thresholds = Dt2Rules.whispererThresholds(Dt2Difficulty.AWAKENED)
        assertEquals(7, thresholds.size)
        assertEquals(thresholds.sortedDescending(), thresholds)
        assertEquals(2_400, thresholds.first())
        assertEquals(800, thresholds.last())
    }

    @Test
    fun `vardorvis axes scale at exact normal capture boundaries`() {
        assertEquals(1, Dt2Rules.vardorvisAxeCount(691, awakened = false))
        assertEquals(2, Dt2Rules.vardorvisAxeCount(690, awakened = false))
        assertEquals(2, Dt2Rules.vardorvisAxeCount(231, awakened = false))
        assertEquals(3, Dt2Rules.vardorvisAxeCount(230, awakened = false))
    }

    @Test
    fun `awakened vardorvis always adds an axe`() {
        assertEquals(2, Dt2Rules.vardorvisAxeCount(1_400, awakened = true))
        assertEquals(3, Dt2Rules.vardorvisAxeCount(1_000, awakened = true))
        assertEquals(4, Dt2Rules.vardorvisAxeCount(400, awakened = true))
    }

    @Test
    fun `leviathan volley reaches one-tick attacks and awakened enrage stays one tick`() {
        assertEquals(4, Dt2Rules.leviathanVolleyTier(36))
        assertEquals(1, Dt2Rules.leviathanAttackRate(6, enraged = false, awakened = false))
        assertEquals(2, Dt2Rules.leviathanAttackRate(0, enraged = true, awakened = false))
        assertEquals(1, Dt2Rules.leviathanAttackRate(0, enraged = true, awakened = true))
    }

    @Test
    fun `revision 240 Leviathan room and protocol contract is explicit`() {
        assertEquals(8291, Dt2Boss.LEVIATHAN.regionId)
        assertEquals(RegionLocal(0, 32, 99, 19, 32), Dt2Boss.LEVIATHAN.enter)
        assertEquals(12214, Dt2Boss.LEVIATHAN.normalNpcId)
        assertEquals(12214, Dt2Boss.LEVIATHAN.awakenedNpcId)
        assertEquals(47593, Dt2Assets.LEVIATHAN_START_HANDHOLDS)
        assertEquals(49213, Dt2Assets.LEVIATHAN_EXIT_ROWBOAT)
        assertEquals(
            listOf(49213, 49214),
            Dt2Assets.LEVIATHAN_EXIT_ROWBOATS,
            "both live rowboat forms must leave the same active instance",
        )
        assertEquals(2487, Dt2Assets.LEVIATHAN_RANGED_PROJECTILE)
        assertEquals(2489, Dt2Assets.LEVIATHAN_MAGIC_PROJECTILE)
        assertEquals(36, LEVIATHAN_RESPAWN_TICKS)
        assertTrue(DT2_NPC_LIFETIME < Int.MAX_VALUE)
    }

    @Test
    fun `Leviathan and Duke copy the complete multi-plane scene`() {
        assertTrue(
            (Dt2Boss.LEVIATHAN.area() as org.rsmod.api.instances.InstanceArea.CopyRegions)
                .copyAllLevels
        )
        Dt2Boss.entries
            .filterNot { it == Dt2Boss.LEVIATHAN || it == Dt2Boss.DUKE }
            .forEach { boss ->
                assertTrue(
                    !(boss.area() as org.rsmod.api.instances.InstanceArea.CopyRegions).copyAllLevels
                )
            }
    }

    @Test
    fun `duke prep dose requirement differs only by difficulty`() {
        assertTrue((Dt2Boss.DUKE.area() as org.rsmod.api.instances.InstanceArea.CopyRegions).copyAllLevels)
        assertEquals(2, Dt2Rules.dukePoisonRequired(Dt2Difficulty.NORMAL))
        assertEquals(3, Dt2Rules.dukePoisonRequired(Dt2Difficulty.AWAKENED))
    }

    @Test
    fun `seeded scheduler reproduces capture replay and separates another seed`() {
        fun replay(seed: Long) = Dt2SeededRng(seed).let { rng -> List(24) { rng.nextInt(10_000) } }
        assertEquals(replay(3299), replay(3299))
        assertNotEquals(replay(3299), replay(3344))
    }
}
