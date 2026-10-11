package org.rsmod.content.bosses.alchemicalhydra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HydraRulesTest {
    @Test
    fun `phase thresholds and vent requirements match the four forms`() {
        assertEquals(825, HydraRules.threshold(HydraPhase.GREEN))
        assertEquals(550, HydraRules.threshold(HydraPhase.BLUE))
        assertEquals(275, HydraRules.threshold(HydraPhase.RED))
        assertEquals(null, HydraRules.threshold(HydraPhase.FINAL))
        assertEquals(HydraVent.RED, HydraRules.requiredVent(HydraPhase.GREEN))
        assertEquals(HydraVent.GREEN, HydraRules.requiredVent(HydraPhase.BLUE))
        assertEquals(HydraVent.BLUE, HydraRules.requiredVent(HydraPhase.RED))
        assertEquals(null, HydraRules.requiredVent(HydraPhase.FINAL))
    }

    @Test
    fun `damage caps and attack rotation follow phase rules`() {
        assertEquals(7, HydraRules.incomingDamage(29, weakened = false))
        assertEquals(29, HydraRules.incomingDamage(29, weakened = true))
        assertEquals(25, HydraRules.capAtPhaseFloor(HydraPhase.GREEN, hitpoints = 850, damage = 40))
        assertEquals(40, HydraRules.capAtPhaseFloor(HydraPhase.FINAL, hitpoints = 850, damage = 40))
        assertFalse(HydraRules.specialDue(2))
        assertTrue(HydraRules.specialDue(3))
        assertTrue(HydraRules.specialDue(12))
        assertFalse(HydraRules.specialDue(13))
    }

    @Test
    fun `vent stacks scale hits and poison splash uses adjacent tiles`() {
        assertEquals(110, HydraRules.empoweredDamage(100, wrongVentStacks = 2))
        assertEquals(150, HydraRules.empoweredDamage(100, wrongVentStacks = 20))
        assertEquals(10, HydraRules.applyWrongVentStack(9))
        assertTrue(HydraRules.poisonSplash(10, 10, 11, 9))
        assertFalse(HydraRules.poisonSplash(10, 10, 12, 10))
        assertEquals(listOf(20 to 20, 22 to 20, 20 to 22), HydraRules.poisonCenters(20, 20, final = false))
        assertEquals(5, HydraRules.poisonCenters(20, 20, final = true).size)
    }
}
