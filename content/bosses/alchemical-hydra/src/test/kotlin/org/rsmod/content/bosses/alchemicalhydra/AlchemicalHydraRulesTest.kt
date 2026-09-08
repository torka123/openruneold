package org.rsmod.content.bosses.alchemicalhydra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlchemicalHydraRulesTest {
    @Test
    fun `arena copies the complete hydra template and uses safe observation entry`() {
        assertEquals(7, HYDRA_OBSERVATION_ENTRY.localX)
        assertEquals(10, HYDRA_OBSERVATION_ENTRY.localZ)
        assertEquals(7, HYDRA_OBSERVATION_DESTINATION.localX)
        assertEquals(12, HYDRA_OBSERVATION_DESTINATION.localZ)
        assertEquals(60, HYDRA_ENTRY_CLIENT_CYCLES)
        assertEquals(1024, HYDRA_ENTRY_ANGLE)
        assertEquals(20, HYDRA_BOSS_SPAWN.localX)
        assertEquals(25, HYDRA_BOSS_SPAWN.localZ)
        assertFalse(AlchemicalHydraRules.inChamber(7, 12))
        assertTrue(AlchemicalHydraRules.inChamber(13, 18))
        assertEquals(HYDRA_PUBLIC_EXIT, HYDRA_AREA.exitCoord)
    }

    @Test
    fun `phase thresholds and transitions are exact`() {
        assertEquals(825, AlchemicalHydraRules.thresholds[HydraPhase.GREEN])
        assertEquals(550, AlchemicalHydraRules.thresholds[HydraPhase.BLUE])
        assertEquals(275, AlchemicalHydraRules.thresholds[HydraPhase.RED])
        assertEquals(25, AlchemicalHydraRules.capAtThreshold(HydraPhase.GREEN, 850, 80))
        assertTrue(AlchemicalHydraRules.reachesThreshold(HydraPhase.GREEN, 825))
        assertEquals(HydraPhase.FINAL, AlchemicalHydraRules.nextPhase(HydraPhase.RED))
        assertEquals(null, AlchemicalHydraRules.nextPhase(HydraPhase.FINAL))
    }

    @Test
    fun `vent order and seventy five percent reduction are explicit`() {
        assertEquals(HydraVent.RED, AlchemicalHydraRules.requiredVent(HydraPhase.GREEN))
        assertEquals(HydraVent.GREEN, AlchemicalHydraRules.requiredVent(HydraPhase.BLUE))
        assertEquals(HydraVent.BLUE, AlchemicalHydraRules.requiredVent(HydraPhase.RED))
        assertEquals(null, AlchemicalHydraRules.requiredVent(HydraPhase.FINAL))
        assertEquals(25, AlchemicalHydraRules.incomingDamage(100, weakened = false))
        assertEquals(100, AlchemicalHydraRules.incomingDamage(100, weakened = true))
        assertEquals(
            10,
            (1..20).fold(0) { stacks, _ -> AlchemicalHydraRules.applyWrongVentStack(stacks) },
        )
        assertEquals(150, AlchemicalHydraRules.empoweredDamage(100, 10))
    }

    @Test
    fun `attack cadence styles and special slots are deterministic`() {
        assertEquals(6, AlchemicalHydraRules.attackRate(HydraPhase.GREEN))
        assertEquals(4, AlchemicalHydraRules.attackRate(HydraPhase.FINAL))
        assertFalse(AlchemicalHydraRules.specialDue(2, 0))
        assertTrue(AlchemicalHydraRules.specialDue(3, 0))
        assertFalse(AlchemicalHydraRules.specialDue(11, 1))
        assertTrue(AlchemicalHydraRules.specialDue(12, 1))
        assertEquals(
            HydraAttackStyle.RANGED,
            AlchemicalHydraRules.nextStyleAfterAttack(HydraPhase.GREEN, HydraAttackStyle.MAGIC, 3),
        )
        assertEquals(
            HydraAttackStyle.RANGED,
            AlchemicalHydraRules.nextStyleAfterAttack(HydraPhase.FINAL, HydraAttackStyle.MAGIC, 1),
        )
    }

    @Test
    fun `documented phase max hits and multi head attacks are preserved`() {
        assertEquals(17, AlchemicalHydraRules.basicMaxHit(HydraPhase.GREEN, weakened = true))
        assertEquals(26, AlchemicalHydraRules.basicMaxHit(HydraPhase.GREEN, weakened = false))
        assertEquals(35, AlchemicalHydraRules.basicMaxHit(HydraPhase.BLUE, weakened = true))
        assertEquals(52, AlchemicalHydraRules.basicMaxHit(HydraPhase.RED, weakened = false))
        assertEquals(55, AlchemicalHydraRules.basicMaxHit(HydraPhase.FINAL, weakened = true))
        assertEquals(2, AlchemicalHydraRules.basicHitCount(HydraPhase.GREEN, weakened = true))
        assertEquals(2, AlchemicalHydraRules.basicHitCount(HydraPhase.BLUE, weakened = false))
        assertEquals(1, AlchemicalHydraRules.basicHitCount(HydraPhase.BLUE, weakened = true))
    }

    @Test
    fun `poison geometry lightning pursuit and cleanup membership are stable`() {
        assertEquals(5, AlchemicalHydraRules.poisonCenters(10, 20, five = true).size)
        assertTrue(AlchemicalHydraRules.isPoisonSplash(11, 21, 10, 20))
        assertFalse(AlchemicalHydraRules.isPoisonSplash(12, 20, 10, 20))
        assertEquals(11 to 19, AlchemicalHydraRules.stepToward(10, 20, 14, 17))
        assertFalse(AlchemicalHydraRules.shouldDiscardRun(42, 42))
        assertTrue(AlchemicalHydraRules.shouldDiscardRun(42, null))
    }

    @Test
    fun `revision 240 npc and interaction ids remain pinned`() {
        assertEquals(listOf(8615, 8619, 8620, 8621), AlchemicalHydraAssets.activeForms)
        assertEquals(listOf(8616, 8617, 8618), AlchemicalHydraAssets.transitionForms)
        assertEquals(listOf(34553, 34554, 34555, 34556), AlchemicalHydraAssets.doors)
        assertEquals(43, HYDRA_RESPAWN_TICKS)
    }
}
