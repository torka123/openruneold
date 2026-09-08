package org.rsmod.content.bosses.royaltitans

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoyalTitansRulesTest {
    @Test
    fun `transition occurs at exactly thirty five percent`() {
        assertFalse(RoyalTitansRules.shouldEnterArena(211, 600))
        assertTrue(RoyalTitansRules.shouldEnterArena(210, 600))
        assertTrue(RoyalTitansRules.shouldEnterArena(600, 210))
    }

    @Test
    fun `late join closes after either titan loses one quarter health`() {
        assertTrue(RoyalTitansRules.canJoin(450, 450, RoyalTitansStage.RANGED))
        assertFalse(RoyalTitansRules.canJoin(449, 600, RoyalTitansStage.RANGED))
        assertFalse(RoyalTitansRules.canJoin(600, 449, RoyalTitansStage.MELEE))
        assertFalse(RoyalTitansRules.canJoin(600, 600, RoyalTitansStage.ARENA))
    }

    @Test
    fun `transition damage cannot skip phase two`() {
        assertEquals(11, RoyalTitansRules.capTransitionDamage(221, 200, false))
        assertEquals(200, RoyalTitansRules.capTransitionDamage(221, 200, true))
    }

    @Test
    fun `area attack has one safe tile and final safe row opposes survivor`() {
        val safe = RoyalTitansRules.safeTile(null, 7)
        assertEquals(
            1,
            (29..33).sumOf { x ->
                (28..34).count { z -> RoyalTitansRules.isSafeTile(x, z, safe.first, safe.second) }
            },
        )
        assertEquals(28, RoyalTitansRules.safeTile(RoyalElement.FIRE, 0).second)
        assertEquals(34, RoyalTitansRules.safeTile(RoyalElement.ICE, 0).second)
        assertEquals(28, RoyalTitansRules.safeTile(null, 0).second)
        assertEquals(34, RoyalTitansRules.safeTile(null, 34).second)
    }

    @Test
    fun `wall spell gates match rsprox observed casts`() {
        assertTrue(RoyalTitansRules.attackableWall(RoyalElement.FIRE, "obj.47_water_blast"))
        assertTrue(RoyalTitansRules.attackableWall(RoyalElement.FIRE, "obj.82_ice_blitz"))
        assertFalse(RoyalTitansRules.attackableWall(RoyalElement.FIRE, "obj.23_water_bolt"))
        assertTrue(RoyalTitansRules.attackableWall(RoyalElement.ICE, "obj.59_fire_blast"))
        assertFalse(RoyalTitansRules.attackableWall(RoyalElement.ICE, "obj.53_earth_blast"))
    }

    @Test
    fun `elemental formula and attack delay are bounded`() {
        assertEquals(0.30, RoyalTitansRules.elementalMagicHitChance(-20, false))
        assertEquals(1.0, RoyalTitansRules.elementalMagicHitChance(70, false))
        assertEquals(1.0, RoyalTitansRules.elementalMagicHitChance(39, true))
        assertEquals(1, RoyalTitansRules.ELEMENTAL_SPLASH_HIT_DELAY_TICKS)
        assertEquals(4, RoyalTitansRules.elementalKillAttackDelay(1))
        assertEquals(2, RoyalTitansRules.elementalKillAttackDelay(3))
    }

    @Test
    fun `ranged accuracy multiplier only applies in documented windows`() {
        assertTrue(RoyalTitansRules.hasRangedAccuracyBoost(RoyalTitansStage.RANGED, false, 0))
        assertTrue(RoyalTitansRules.hasRangedAccuracyBoost(RoyalTitansStage.ARENA, true, 0))
        assertTrue(RoyalTitansRules.hasRangedAccuracyBoost(RoyalTitansStage.ARENA, false, 1))
        assertFalse(RoyalTitansRules.hasRangedAccuracyBoost(RoyalTitansStage.MELEE, false, 0))
    }

    @Test
    fun `elemental standard spell max hits match spell tiers`() {
        assertEquals(2, RoyalTitansRules.elementalSpellBaseMaxHit("obj.1_wind_strike"))
        assertEquals(10, RoyalTitansRules.elementalSpellBaseMaxHit("obj.7_water_bolt"))
        assertEquals(16, RoyalTitansRules.elementalSpellBaseMaxHit("obj.59_fire_blast"))
        assertEquals(21, RoyalTitansRules.elementalSpellBaseMaxHit("obj.65_wind_surge"))
    }

    @Test
    fun `captured transition and arena cadence remain exact`() {
        assertEquals(3, RoyalTitansRules.BRANDA_TRANSITION_TICKS)
        assertEquals(4, RoyalTitansRules.ELDRIC_TRANSITION_CAST_TICK)
        assertEquals(7, RoyalTitansRules.ELDRIC_TRANSITION_TICKS)
        assertEquals(7, RoyalTitansRules.ARENA_OPENING_TICKS)
        assertEquals(13, RoyalTitansRules.FINAL_OPENING_TICKS)
        assertEquals(36, RoyalTitansRules.FIRST_ARENA_CYCLE_TICKS)
        assertEquals(29, RoyalTitansRules.ARENA_CYCLE_TICKS)
        assertEquals(6, RoyalTitansRules.AREA_CHARGE_TICKS)
        assertEquals(2, RoyalTitansRules.AREA_PULSE_GAP)
    }

    @Test
    fun `successive same tick elemental kills shorten the next attack`() {
        assertEquals(listOf(4, 3, 2, 1, 0), (1..5).map(RoyalTitansRules::elementalKillAttackDelay))
        assertEquals(0, RoyalTitansRules.elementalKillAttackDelay(6))
    }

    @Test
    fun `final survivor stomp retains at most ten through prayer`() {
        assertEquals(10, RoyalTitansRules.finalStompDamage(26, true))
        assertEquals(26, RoyalTitansRules.finalStompDamage(26, false))
    }
}
