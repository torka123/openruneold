package org.rsmod.content.other.special.attacks.magic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NightmareStaffSpecialDamageTest {
    @Test
    fun volatileSpecialUsesVisibleMagicScalingAndLevel98Cap() {
        assertEquals(49, NightmareStaffSpecialDamage.volatileBaseMaxHit(82))
        assertEquals(53, NightmareStaffSpecialDamage.volatileBaseMaxHit(89))
        assertEquals(57, NightmareStaffSpecialDamage.volatileBaseMaxHit(96))
        assertEquals(58, NightmareStaffSpecialDamage.volatileBaseMaxHit(98))
        assertEquals(58, NightmareStaffSpecialDamage.volatileBaseMaxHit(120))
    }

    @Test
    fun eldritchSpecialUsesVisibleMagicScalingAndLevel97Cap() {
        assertEquals(37, NightmareStaffSpecialDamage.eldritchBaseMaxHit(82))
        assertEquals(41, NightmareStaffSpecialDamage.eldritchBaseMaxHit(90))
        assertEquals(43, NightmareStaffSpecialDamage.eldritchBaseMaxHit(96))
        assertEquals(44, NightmareStaffSpecialDamage.eldritchBaseMaxHit(97))
        assertEquals(44, NightmareStaffSpecialDamage.eldritchBaseMaxHit(120))
    }

    @Test
    fun baseHitRemainsDefinedForInvalidlyLowVisibleMagic() {
        assertEquals(1, NightmareStaffSpecialDamage.volatileBaseMaxHit(0))
        assertEquals(1, NightmareStaffSpecialDamage.eldritchBaseMaxHit(-1))
    }

    @Test
    fun `specials land two ticks after casting without a projectile`() {
        assertEquals(30, NightmareStaffSpecialRules.IMPACT_CLIENT_DELAY)
        assertEquals(2, NightmareStaffSpecialRules.IMPACT_SERVER_DELAY)
    }

    @Test
    fun `Invocate restores half potential damage and caps prayer at 120`() {
        assertEquals(70, NightmareStaffSpecialRules.invocatePrayerLevel(50, 40))
        assertEquals(50, NightmareStaffSpecialRules.invocatePrayerLevel(50, 1))
        assertEquals(120, NightmareStaffSpecialRules.invocatePrayerLevel(110, 40))
        assertEquals(120, NightmareStaffSpecialRules.invocatePrayerLevel(120, 80))
    }

    @Test
    fun `only Volatile and Eldritch variants expose Nightmare specials`() {
        assertEquals(550, NightmareStaffSpecialRules.SPECIAL_ENERGY_COST)
        assertTrue("obj.nightmare_staff_volatile" in NightmareStaffSpecialRules.volatileVariants)
        assertTrue("obj.nightmare_staff_eldritch" in NightmareStaffSpecialRules.eldritchVariants)
        assertFalse("obj.nightmare_staff" in NightmareStaffSpecialRules.volatileVariants)
        assertFalse("obj.nightmare_staff" in NightmareStaffSpecialRules.eldritchVariants)
        assertFalse("obj.nightmare_staff_harmonised" in NightmareStaffSpecialRules.volatileVariants)
        assertFalse("obj.nightmare_staff_harmonised" in NightmareStaffSpecialRules.eldritchVariants)
    }
}
