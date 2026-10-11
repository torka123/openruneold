package org.rsmod.content.bosses.grotesqueguardians

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrotesqueGuardiansRulesTest {
    @Test
    fun `room and spawn coordinates match the verified roof template`() {
        assertEquals(6727, GROTESQUE_SOURCE_REGION)
        assertEquals(33 to 23, GROTESQUE_ENTRY_LOCAL_X to GROTESQUE_ENTRY_LOCAL_Z)
        assertEquals(31 to 39, GROTESQUE_BELL_LOCAL_X to GROTESQUE_BELL_LOCAL_Z)
        assertEquals(37 to 29, GROTESQUE_DAWN_LOCAL_X to GROTESQUE_DAWN_LOCAL_Z)
        assertEquals(25 to 29, GROTESQUE_DUSK_LOCAL_X to GROTESQUE_DUSK_LOCAL_Z)
        assertEquals(
            Coord(3428, 3541, 2),
            Coord(GROTESQUE_PUBLIC_EXIT.x, GROTESQUE_PUBLIC_EXIT.z, GROTESQUE_PUBLIC_EXIT.level),
        )
    }

    @Test
    fun `only the active guardian accepts damage`() {
        assertTrue(GrotesqueGuardiansRules.canDamage(GuardianRole.DAWN, GrotesquePhase.DAWN_ONE))
        assertFalse(GrotesqueGuardiansRules.canDamage(GuardianRole.DUSK, GrotesquePhase.DAWN_ONE))
        assertTrue(GrotesqueGuardiansRules.canDamage(GuardianRole.DUSK, GrotesquePhase.DUSK_TWO))
        assertFalse(GrotesqueGuardiansRules.canDamage(GuardianRole.DAWN, GrotesquePhase.DUSK_TWO))
        assertTrue(GrotesqueGuardiansRules.canDamage(GuardianRole.DAWN, GrotesquePhase.DAWN_THREE))
        assertTrue(GrotesqueGuardiansRules.canDamage(GuardianRole.DUSK, GrotesquePhase.DUSK_FOUR))
    }

    @Test
    fun `threshold hits cannot skip a phase`() {
        assertEquals(3, GrotesqueGuardiansRules.capDamageAtThreshold(250, 100))
        assertEquals(0, GrotesqueGuardiansRules.capDamageAtThreshold(247, 100))
        assertEquals(1, GrotesqueGuardiansRules.capDawnFinalDamage(10, 50))
        assertEquals(0, GrotesqueGuardiansRules.capDawnFinalDamage(9, 50))
    }

    @Test
    fun `energy spheres advance deterministically and heal for ninety`() {
        assertEquals(SphereState.LOW, GrotesqueGuardiansRules.sphereState(0))
        assertEquals(SphereState.MEDIUM, GrotesqueGuardiansRules.sphereState(12))
        assertEquals(SphereState.HIGH, GrotesqueGuardiansRules.sphereState(24))
        assertEquals(SphereState.RETURNING, GrotesqueGuardiansRules.sphereState(36))
        assertEquals(337, (247 + GrotesqueGuardiansRules.SPHERE_HEAL).coerceAtMost(450))
    }

    @Test
    fun `debris damages a three by three area but only direct impact stuns`() {
        assertTrue(GrotesqueGuardiansRules.inDebrisArea(9, 11, 10, 10))
        assertFalse(GrotesqueGuardiansRules.inDebrisArea(8, 10, 10, 10))
        assertTrue(GrotesqueGuardiansRules.directlyUnderDebris(10, 10, 10, 10))
        assertFalse(GrotesqueGuardiansRules.directlyUnderDebris(9, 10, 10, 10))
    }

    @Test
    fun `phase four fixed first ranged hit and prison healing are bounded`() {
        assertEquals(9, GrotesqueGuardiansRules.phaseFourRangedFirstHit(true, false))
        assertEquals(4, GrotesqueGuardiansRules.phaseFourRangedFirstHit(true, true))
        assertEquals(0, GrotesqueGuardiansRules.phaseFourRangedFirstHit(false, false))
        assertEquals(7, GrotesqueGuardiansRules.phaseFourRangedSecondHit(true, 12, 7))
        assertEquals(450, GrotesqueGuardiansRules.healAfterPrisonDamage(440, 65))
    }

    private data class Coord(val x: Int, val z: Int, val level: Int)
}
