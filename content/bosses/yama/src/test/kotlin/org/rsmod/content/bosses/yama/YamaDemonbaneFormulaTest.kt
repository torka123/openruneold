package org.rsmod.content.bosses.yama

import java.util.EnumSet
import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.api.combat.formulas.accuracy.melee.MeleeAccuracyOperations
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.maxhit.melee.MeleeMaxHitOperations

class YamaDemonbaneFormulaTest {
    private val demon = EnumSet.of(CombatNpcAttributes.Demon)

    @Test
    fun `silverlight family modifies accuracy and damage exactly once`() {
        val weapon = EnumSet.of(CombatMeleeAttributes.Silverlight)
        assertEquals(160, MeleeAccuracyOperations.modifyAttackRoll(100, weapon, demon))
        assertEquals(160, MeleeMaxHitOperations.modifyBaseDamage(100, weapon, demon))
    }

    @Test
    fun `arclight modifies accuracy and damage exactly once`() {
        val weapon = EnumSet.of(CombatMeleeAttributes.Arclight)
        assertEquals(170, MeleeAccuracyOperations.modifyAttackRoll(100, weapon, demon))
        assertEquals(170, MeleeMaxHitOperations.modifyBaseDamage(100, weapon, demon))
    }

    @Test
    fun `emberlight modifies accuracy and damage exactly once`() {
        val weapon = EnumSet.of(CombatMeleeAttributes.Emberlight)
        assertEquals(170, MeleeAccuracyOperations.modifyAttackRoll(100, weapon, demon))
        assertEquals(170, MeleeMaxHitOperations.modifyBaseDamage(100, weapon, demon))
    }

    @Test
    fun `demonbane attributes do not modify non demons`() {
        val noNpcAttributes = EnumSet.noneOf(CombatNpcAttributes::class.java)
        CombatMeleeAttributes.entries
            .filter {
                it in
                    setOf(
                        CombatMeleeAttributes.Silverlight,
                        CombatMeleeAttributes.Arclight,
                        CombatMeleeAttributes.Emberlight,
                    )
            }
            .forEach { attribute ->
                val weapon = EnumSet.of(attribute)
                assertEquals(
                    100,
                    MeleeAccuracyOperations.modifyAttackRoll(100, weapon, noNpcAttributes),
                )
                assertEquals(
                    100,
                    MeleeMaxHitOperations.modifyBaseDamage(100, weapon, noNpcAttributes),
                )
            }
    }
}
