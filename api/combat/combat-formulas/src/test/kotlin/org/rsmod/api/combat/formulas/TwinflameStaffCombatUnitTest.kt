package org.rsmod.api.combat.formulas

import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.formulas.accuracy.magic.MagicAccuracyOperations
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations

class TwinflameStaffCombatUnitTest {
    @Test
    fun `Twinflame adds ten percent spell accuracy on the Standard spellbook`() {
        val attributes =
            EnumSet.of(
                CombatSpellAttributes.StandardBook,
                CombatSpellAttributes.TwinflameStaff,
            )
        val npcAttributes = EnumSet.noneOf(CombatNpcAttributes::class.java)

        assertEquals(
            11_000,
            MagicAccuracyOperations.modifySpellAttackRoll(
                attackRoll = 10_000,
                targetWeaknessPercent = 0,
                spellAttributes = attributes,
                npcAttributes = npcAttributes,
            ),
        )
        assertEquals(11_000, MagicAccuracyOperations.modifySpellAttackRoll(10_000, attributes))
    }

    @Test
    fun `Twinflame gives no accuracy bonus outside the Standard spellbook`() {
        val attributes = EnumSet.of(CombatSpellAttributes.TwinflameStaff)
        val npcAttributes = EnumSet.noneOf(CombatNpcAttributes::class.java)

        assertEquals(
            10_000,
            MagicAccuracyOperations.modifySpellAttackRoll(
                attackRoll = 10_000,
                targetWeaknessPercent = 0,
                spellAttributes = attributes,
                npcAttributes = npcAttributes,
            ),
        )
        assertEquals(10_000, MagicAccuracyOperations.modifySpellAttackRoll(10_000, attributes))
    }

    @Test
    fun `Twinflame adds ten percent spell damage on the Standard spellbook`() {
        val attributes =
            EnumSet.of(
                CombatSpellAttributes.StandardBook,
                CombatSpellAttributes.TwinflameStaff,
            )
        val npcAttributes = EnumSet.noneOf(CombatNpcAttributes::class.java)

        assertEquals(
            22,
            MagicMaxHitOperations.modifySpellBaseDamage(
                baseDamage = 20,
                sourceMagic = 99,
                sourceBaseMagicDmgBonus = 0,
                sourceMagicPrayerBonus = 0,
                spellAttributes = attributes,
                npcAttributes = npcAttributes,
            ),
        )
        assertEquals(
            22,
            MagicMaxHitOperations.modifySpellBaseDamage(
                baseDamage = 20,
                sourceMagic = 99,
                sourceBaseMagicDmgBonus = 0,
                sourceMagicPrayerBonus = 0,
                spellAttributes = attributes,
            ),
        )
    }
}
