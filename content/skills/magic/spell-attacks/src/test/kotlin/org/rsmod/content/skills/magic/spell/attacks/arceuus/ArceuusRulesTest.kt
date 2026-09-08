package org.rsmod.content.skills.magic.spell.attacks.arceuus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArceuusRulesTest {
    @Test
    fun `all seven target spells have their OSRS combat contracts`() {
        assertEquals(7, ArceuusCombatRules.definitions.size)
        val grasps = ArceuusCombatRules.definitions.filter { it.effect == ArceuusCombatEffect.Grasp }
        assertEquals(listOf(12, 17, 24), grasps.map { it.maxHit })
        assertEquals(listOf(10, 4, 2), grasps.map { it.bindDenominator })
        assertEquals(listOf(2, 3, 4), grasps.map { it.bindTicks })
        val demonbane = ArceuusCombatRules.definitions.filter { it.effect == ArceuusCombatEffect.Demonbane }
        assertEquals(listOf(16, 23, 30), demonbane.map { it.maxHit })
    }

    @Test
    fun `utility and thrall catalogs cover every tier`() {
        assertEquals(10, ArceuusUtilitySpell.entries.size)
        assertEquals(9, ArceuusThralls.definitions.size)
        assertEquals(setOf(10878, 10879, 10880, 10881, 10882, 10883, 10884, 10885, 10886), ArceuusThralls.definitions.mapTo(mutableSetOf()) { it.npcId })
        assertEquals(listOf(2, 4, 6), ArceuusThralls.definitions.map { it.prayerDrain }.distinct())
        assertEquals(listOf(1, 2, 3), ArceuusThralls.definitions.map { it.maxHit }.distinct())
    }

    @Test
    fun `reanimation catalog maps every ensouled head once`() {
        assertEquals(23, ArceuusReanimations.definitions.size)
        assertEquals(46, ArceuusReanimations.byHead.size)
        assertEquals((7018..7039).toSet() + 11463, ArceuusReanimations.definitions.mapTo(mutableSetOf()) { it.npcId })
        assertTrue(ArceuusReanimations.definitions.all { it.prayerXp > 0.0 })
    }
}
