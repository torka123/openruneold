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
    fun `area attack has one safe tile and final safe row opposes survivor`() {
        val safe = RoyalTitansRules.safeTile(null) { 7 }
        assertEquals(
            1,
            (29..33).sumOf { x ->
                (28..34).count { z -> RoyalTitansRules.isSafeTile(x, z, safe.first, safe.second) }
            },
        )
        assertEquals(28, RoyalTitansRules.safeTile(RoyalElement.FIRE) { 0 }.second)
        assertEquals(34, RoyalTitansRules.safeTile(RoyalElement.ICE) { 0 }.second)
        assertEquals(28, RoyalTitansRules.safeTile(null) { 0 }.second)
        assertEquals(34, RoyalTitansRules.safeTile(null) { 34 }.second)
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
    fun `elemental attack delay is bounded by the remaining delay`() {
        assertEquals(1, RoyalTitansRules.ELEMENTAL_SPLASH_HIT_DELAY_TICKS)
        assertEquals(0, RoyalTitansRules.elementalKillAttackDelay(1))
        assertEquals(2, RoyalTitansRules.elementalKillAttackDelay(3))
    }

    @Test
    fun `wall removal includes only the selected piece and immediate neighbours`() {
        assertEquals(0..1, RoyalTitansRules.removedWallIndices(0))
        assertEquals(0..2, RoyalTitansRules.removedWallIndices(1))
        assertEquals(1..3, RoyalTitansRules.removedWallIndices(2))
        assertEquals(2..4, RoyalTitansRules.removedWallIndices(3))
        assertEquals(3..4, RoyalTitansRules.removedWallIndices(4))
    }

    @Test
    fun `captured transition offsets and individual pulse timing`() {
        assertEquals(3, RoyalTitansRules.BRANDA_TRANSITION_TICKS)
        assertEquals(4, RoyalTitansRules.ELDRIC_TRANSITION_CAST_TICK)
        assertEquals(7, RoyalTitansRules.ELDRIC_TRANSITION_TICKS)
        assertEquals(6, RoyalTitansRules.AREA_CHARGE_TICKS)
        assertEquals(2, RoyalTitansRules.AREA_PULSE_GAP)
    }

    @Test
    fun `successive same tick elemental kills shorten the next attack`() {
        var remaining = 5
        val delays =
            (1..5).map {
                remaining = RoyalTitansRules.elementalKillAttackDelay(remaining)
                remaining
            }
        assertEquals(listOf(4, 3, 2, 1, 0), delays)
        assertEquals(5, RoyalTitansRules.elementalKillAttackDelay(6))
        assertEquals(0, RoyalTitansRules.elementalKillAttackDelay(-1))
    }

    @Test
    fun `final survivor stomp retains at most ten through prayer`() {
        assertEquals(10, RoyalTitansRules.finalStompDamage(26, true))
        assertEquals(26, RoyalTitansRules.finalStompDamage(26, false))
    }
}
