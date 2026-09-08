package org.rsmod.api.combat.formulas

import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations

class TomeOfFireFormulaTest {
    @Test
    fun `charged tome boosts only Standard fire spells by ten percent against npcs`() {
        val attributes =
            EnumSet.of(
                CombatSpellAttributes.StandardBook,
                CombatSpellAttributes.FireSpell,
                CombatSpellAttributes.FireTome,
            )

        assertEquals(0..33, pvmRange(30, attributes))

        attributes.remove(CombatSpellAttributes.StandardBook)
        assertEquals(0..30, pvmRange(30, attributes))

        attributes.add(CombatSpellAttributes.StandardBook)
        attributes.remove(CombatSpellAttributes.FireSpell)
        attributes.add(CombatSpellAttributes.WaterSpell)
        assertEquals(0..30, pvmRange(30, attributes))
    }

    @Test
    fun `searing pages calculate ten percent minimum before tome maximum boost`() {
        val attributes =
            EnumSet.of(
                CombatSpellAttributes.StandardBook,
                CombatSpellAttributes.FireSpell,
                CombatSpellAttributes.FireTome,
                CombatSpellAttributes.SearingPageTome,
            )

        assertEquals(3..33, pvmRange(30, attributes))
    }

    @Test
    fun `charged tome boosts Standard fire spells by fifty percent against players`() {
        val attributes =
            EnumSet.of(
                CombatSpellAttributes.StandardBook,
                CombatSpellAttributes.FireSpell,
                CombatSpellAttributes.FireTome,
            )

        assertEquals(
            0..45,
            MagicMaxHitOperations.modifySpellDamageRange(
                modifiedDamage = 30,
                spellAttributes = attributes,
            ),
        )

        attributes.add(CombatSpellAttributes.SearingPageTome)
        assertEquals(
            3..45,
            MagicMaxHitOperations.modifySpellDamageRange(
                modifiedDamage = 30,
                spellAttributes = attributes,
            ),
        )
    }

    private fun pvmRange(damage: Int, attributes: EnumSet<CombatSpellAttributes>): IntRange =
        MagicMaxHitOperations.modifySpellDamageRange(
            modifiedDamage = damage,
            baseDamage = damage,
            attackRate = 5,
            targetWeaknessPercent = 0,
            spellAttributes = attributes,
            npcAttributes = EnumSet.noneOf(CombatNpcAttributes::class.java),
        )
}
