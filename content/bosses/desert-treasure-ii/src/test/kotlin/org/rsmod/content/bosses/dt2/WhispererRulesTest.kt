package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

class WhispererRulesTest {
    @Test
    fun `private arena contract uses captured region safe entry spawn and public exit`() {
        assertEquals(10595, Dt2Boss.WHISPERER.regionId)
        assertEquals(RegionLocal(0, 41, 99, 32, 46), Dt2Boss.WHISPERER.enter)
        assertEquals(RegionLocal(0, 41, 99, 31, 32), Dt2Boss.WHISPERER.spawn)
        assertEquals(CoordGrid(2683, 6433, 0), Dt2Boss.WHISPERER.exit)
    }

    @Test
    fun `original start failure cannot regress to attack form`() {
        assertEquals(12203, Dt2Assets.WHISPERER_SPAWN_ID)
        assertEquals(12204, Dt2Assets.WHISPERER_ACTIVE_ID)
        assertTrue(Dt2Rules.whispererCanDisturb(12203, Dt2State.PREPARING))
        assertFalse(Dt2Rules.whispererCanDisturb(12204, Dt2State.PREPARING))
        assertFalse(Dt2Rules.whispererCanDisturb(12203, Dt2State.ACTIVE))
    }

    @Test
    fun `normal phase thresholds and both seeded rotations are deterministic`() {
        assertEquals(listOf(720, 495, 270), Dt2Rules.whispererThresholds(Dt2Difficulty.NORMAL))
        assertEquals(
            listOf(
                WhispererSpecial.CORRUPTED_SEEDS,
                WhispererSpecial.SCREECH,
                WhispererSpecial.SOUL_SIPHON,
            ),
            List(3) { Dt2Rules.whispererSpecial(it, 0) },
        )
        assertEquals(
            listOf(
                WhispererSpecial.SCREECH,
                WhispererSpecial.SOUL_SIPHON,
                WhispererSpecial.CORRUPTED_SEEDS,
            ),
            List(3) { Dt2Rules.whispererSpecial(it, 1) },
        )
    }

    @Test
    fun `standard volleys change after specials and enrage alternates ranged ranged magic magic`() {
        assertEquals(
            listOf(Dt2AttackStyle.MAGIC, Dt2AttackStyle.MAGIC, Dt2AttackStyle.MAGIC),
            Dt2Rules.whispererVolley(0, 0, false).styles,
        )
        assertEquals(
            listOf(Dt2AttackStyle.MAGIC, Dt2AttackStyle.MAGIC, Dt2AttackStyle.RANGED),
            Dt2Rules.whispererVolley(0, 1, false).styles,
        )
        assertEquals(
            listOf(Dt2AttackStyle.MAGIC, Dt2AttackStyle.RANGED, Dt2AttackStyle.MAGIC),
            Dt2Rules.whispererVolley(0, 3, false).styles,
        )
        assertEquals(
            listOf(
                Dt2AttackStyle.RANGED,
                Dt2AttackStyle.RANGED,
                Dt2AttackStyle.MAGIC,
                Dt2AttackStyle.MAGIC,
            ),
            List(4) { Dt2Rules.whispererVolley(it, 3, true).styles.single() },
        )
        assertEquals(10, Dt2Rules.whispererVolley(0, 0, false).attackRate)
        assertEquals(2, Dt2Rules.whispererVolley(0, 3, true).attackRate)
    }

    @Test
    fun `captured visuals maximum hits sanity and insanity contracts remain explicit`() {
        assertEquals(2444, Dt2Rules.whispererProjectile(Dt2AttackStyle.RANGED))
        assertEquals(2445, Dt2Rules.whispererProjectile(Dt2AttackStyle.MAGIC))
        assertEquals(40, Dt2Rules.whispererMaximumHit(Dt2AttackStyle.RANGED, false))
        assertEquals(36, Dt2Rules.whispererMaximumHit(Dt2AttackStyle.MAGIC, false))
        assertEquals(42, Dt2Rules.whispererMaximumHit(Dt2AttackStyle.MELEE, false))
        assertEquals(97, Dt2Rules.whispererSanityTick(100, true, false, 1))
        assertEquals(96, Dt2Rules.whispererSanityTick(100, true, true, 1))
        assertEquals(100, Dt2Rules.whispererSanityTick(99, false, false, 4))
        assertEquals(
            listOf(5, 10, 20, 40, 80, 160),
            List(6) { Dt2Rules.whispererInsanityDamage(it * 2) },
        )
    }

    @Test
    fun `special layouts and ten-second respawn contract are fixed`() {
        assertEquals(28, Dt2Rules.whispererSeedTiles.size)
        assertEquals(14, Dt2Rules.whispererHealthySeedTiles.size)
        assertEquals(8, Dt2Rules.whispererScreechPillars.size)
        assertEquals(
            6,
            Dt2Rules.whispererScreechPillars.count { Dt2Rules.whispererScreechStrength(it) == 1 },
        )
        assertEquals(
            1,
            Dt2Rules.whispererScreechPillars.count { Dt2Rules.whispererScreechStrength(it) == 2 },
        )
        assertEquals(
            1,
            Dt2Rules.whispererScreechPillars.count { Dt2Rules.whispererScreechStrength(it) == 3 },
        )
        assertEquals(12, Dt2Rules.whispererSoulTiles.size)
        assertEquals(17, WHISPERER_RESPAWN_TICKS)
    }
}
