package org.rsmod.content.other.special.attacks.ranged

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlowpipeAndWebweaverSpecialDamageTest {
    @Test
    fun toxicSiphonHealsHalfOfTheProspectiveRawDamage() {
        assertEquals(10, ToxicBlowpipeSpecialDamage.heal(20))
        assertEquals(2, ToxicBlowpipeSpecialDamage.heal(5))
        assertEquals(0, ToxicBlowpipeSpecialDamage.heal(0))
        assertEquals(0, ToxicBlowpipeSpecialDamage.heal(-1))
    }

    @Test
    fun swarmCapsEachHitAtCeilingOfFortyPercentOfNormalMaximum() {
        assertEquals(0, WebweaverBowSpecialDamage.maxHit(0))
        assertEquals(1, WebweaverBowSpecialDamage.maxHit(1))
        assertEquals(1, WebweaverBowSpecialDamage.maxHit(2))
        assertEquals(2, WebweaverBowSpecialDamage.maxHit(3))
        assertEquals(2, WebweaverBowSpecialDamage.maxHit(5))
        assertEquals(3, WebweaverBowSpecialDamage.maxHit(6))
        assertEquals(4, WebweaverBowSpecialDamage.maxHit(10))
    }

    @Test
    fun swarmUsesOneThenThreeHitCadence() {
        assertEquals(listOf(1, 2, 2, 2), List(4, WebweaverBowSpecialTiming::hitDelay))
    }

    @Test
    fun swarmAttemptsFourDamagePoisonOnlyFromSuccessfulRolledHits() {
        assertEquals(4, WebweaverBowSpecialPoison.INITIAL_DAMAGE)
        assertFalse(WebweaverBowSpecialPoison.shouldAttempt(damage = 0, rolled = true))
        assertFalse(WebweaverBowSpecialPoison.shouldAttempt(damage = 1, rolled = false))
        assertTrue(WebweaverBowSpecialPoison.shouldAttempt(damage = 1, rolled = true))
    }
}
