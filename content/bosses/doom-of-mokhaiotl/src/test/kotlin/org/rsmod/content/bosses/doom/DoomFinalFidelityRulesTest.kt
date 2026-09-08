package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class DoomFinalFidelityRulesTest {
    @Test
    fun `transition hits follow the live shield routing and interruption rules`() {
        val transition =
            DoomShieldTransitionRules.resolve(
                epochMatches = true,
                impactState = DoomState.SHIELD_TRANSITION,
                demonbane = false,
                impactDamage = 31,
            )
        assertEquals(DoomShieldTransitionDestination.NORMAL_HEALTH, transition.destination)

        val ordinaryShieldHit =
            DoomShieldTransitionRules.resolve(
                epochMatches = true,
                impactState = DoomState.SHIELDED,
                demonbane = false,
                impactDamage = 31,
            )
        assertEquals(DoomShieldTransitionDestination.SHIELD, ordinaryShieldHit.destination)
        assertEquals(31, ordinaryShieldHit.shieldDamage)
        assertTrue(ordinaryShieldHit.damagesShield)
        assertFalse(ordinaryShieldHit.resetsCharge)

        val demonbaneShieldHit =
            DoomShieldTransitionRules.resolve(
                epochMatches = true,
                impactState = DoomState.SHIELDED,
                demonbane = true,
                impactDamage = 31,
            )
        assertTrue(demonbaneShieldHit.resetsCharge)

        val zero =
            DoomShieldTransitionRules.resolve(
                epochMatches = true,
                impactState = DoomState.SHIELDED,
                demonbane = true,
                impactDamage = 0,
            )
        assertEquals(0, zero.shieldDamage)
        assertFalse(zero.damagesShield)
        assertFalse(zero.resetsCharge)

        val stale =
            DoomShieldTransitionRules.resolve(
                epochMatches = false,
                impactState = DoomState.SHIELDED,
                demonbane = true,
                impactDamage = 31,
            )
        assertEquals(DoomShieldTransitionDestination.STALE_PHASE, stale.destination)
    }

    @Test
    fun `volatile earth requires two distinct impact nodes`() {
        val first = CoordGrid(100, 200, 0)
        assertFalse(DoomVolatileEarthRules.isDistinctSelection(first, first))
        assertTrue(
            DoomVolatileEarthRules.isDistinctSelection(
                firstCentre = first,
                selectedCentre = first.translate(1, 0),
            )
        )
        assertEquals(first.translate(-1, -1), DoomVolatileEarthRules.shieldSouthWest(first))
    }

    @Test
    fun `deep-dive lifecycle constants preserve current live behavior`() {
        assertEquals(3, DoomRules.BOSS_SPAWN_DELAY_TICKS)
        assertEquals(32, DoomRules.HOLY_WATER_HITPOINTS)
        assertEquals(24, DoomRules.HOLY_WATER_PRAYER)
        assertEquals(250, DoomRules.HOLY_WATER_SPECIAL_ENERGY)
        assertFalse(DoomPersistenceRules.shouldCarryAcidInto(6))
        assertTrue(DoomPersistenceRules.shouldCarryAcidInto(5))
        assertTrue(DoomPersistenceRules.shouldCarryAcidInto(7))
    }

    @Test
    fun `larva explosion footprints are delve accurate`() {
        val origin = CoordGrid(100, 200, 0)
        assertEquals(9, DoomLarvaRules.explosionTiles(origin, giant = false).size)
        assertEquals(16, DoomLarvaRules.explosionTiles(origin, giant = true).size)
    }
}
