package org.rsmod.api.combat.formulas

import java.util.EnumSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.formulas.accuracy.magic.MagicAccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.melee.MeleeAccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.ranged.RangedAccuracyOperations
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatRangedAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.attributes.CombatStaffAttributes
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations
import org.rsmod.api.combat.formulas.maxhit.melee.MeleeMaxHitOperations
import org.rsmod.api.combat.formulas.maxhit.ranged.RangedMaxHitOperations

class SlayerHelmCombatUnitTest {
    @Test
    fun `all slayer helmet forms receive their intended styles`() {
        val meleeVariants =
            listOf(
                "obj.nzone_black_mask",
                "obj.nzone_black_mask_10",
                "obj.nzone_black_mask_i",
                "obj.slayer_helm",
                "obj.slayer_helm_black",
                "obj.slayer_helm_twisted",
                "obj.slayer_helm_i",
                "obj.slayer_helm_i_black",
                "obj.slayer_helm_i_twisted",
                "obj.slayer_helm_i_jad",
                "obj.slayer_helm_i_verzik",
                "obj.slayer_helm_i_zuk",
                "obj.sw_slayer_helm_i_jad",
                "obj.pvpa_slayer_helm_i_zuk",
            )
        for (key in meleeVariants) {
            assertTrue(SlayerHelmCombatChecks.itemKeyProvidesMeleeTaskBoost(key), "$key melee")
        }

        val imbuedVariants =
            listOf(
                "obj.nzone_black_mask_i",
                "obj.slayer_helm_i",
                "obj.slayer_helm_i_black",
                "obj.slayer_helm_i_twisted",
                "obj.slayer_helm_i_jad",
                "obj.slayer_helm_i_verzik",
                "obj.slayer_helm_i_zuk",
                "obj.sw_slayer_helm_i_jad",
                "obj.pvpa_slayer_helm_i_zuk",
            )
        for (key in imbuedVariants) {
            assertTrue(
                SlayerHelmCombatChecks.itemKeyProvidesImbuedRangedMagicTaskBoost(key),
                "$key ranged/magic",
            )
        }

        assertFalse(
            SlayerHelmCombatChecks.itemKeyProvidesImbuedRangedMagicTaskBoost(
                "obj.slayer_helm_twisted"
            )
        )
        assertFalse(SlayerHelmCombatChecks.itemKeyProvidesMeleeTaskBoost("obj.rune_full_helm"))
    }

    @Test
    fun `helmet bonuses apply to active task targets only`() {
        val taskTarget = EnumSet.of(CombatNpcAttributes.SlayerTask)
        val offTaskTarget = EnumSet.noneOf(CombatNpcAttributes::class.java)
        val melee = EnumSet.of(CombatMeleeAttributes.BlackMask)
        val ranged = EnumSet.of(CombatRangedAttributes.BlackMaskI)
        val spell = EnumSet.of(CombatSpellAttributes.BlackMaskI)
        val staff = EnumSet.of(CombatStaffAttributes.BlackMaskI)

        assertEquals(1166, MeleeAccuracyOperations.modifyAttackRoll(1000, melee, taskTarget))
        assertEquals(1166, MeleeMaxHitOperations.modifyBaseDamage(1000, melee, taskTarget))
        assertEquals(1150, RangedAccuracyOperations.modifyAttackRoll(1000, 0, 1, ranged, taskTarget))
        assertEquals(1150, RangedMaxHitOperations.modifyBaseDamage(1000, 0, ranged, taskTarget))
        assertEquals(1150, MagicAccuracyOperations.modifySpellAttackRoll(1000, 0, spell, taskTarget))
        assertEquals(1150, MagicAccuracyOperations.modifyStaffAttackRoll(1000, staff, taskTarget))
        assertEquals(
            1150,
            MagicMaxHitOperations.modifySpellBaseDamage(1000, 99, 0, 0, spell, taskTarget),
        )
        assertEquals(1150, MagicMaxHitOperations.modifyStaffBaseDamage(1000, 0, 0, staff, taskTarget))

        assertEquals(1000, MeleeAccuracyOperations.modifyAttackRoll(1000, melee, offTaskTarget))
        assertEquals(1000, MeleeMaxHitOperations.modifyBaseDamage(1000, melee, offTaskTarget))
        assertEquals(1000, RangedAccuracyOperations.modifyAttackRoll(1000, 0, 1, ranged, offTaskTarget))
        assertEquals(1000, RangedMaxHitOperations.modifyBaseDamage(1000, 0, ranged, offTaskTarget))
        assertEquals(1000, MagicAccuracyOperations.modifySpellAttackRoll(1000, 0, spell, offTaskTarget))
        assertEquals(1000, MagicAccuracyOperations.modifyStaffAttackRoll(1000, staff, offTaskTarget))
        assertEquals(
            1000,
            MagicMaxHitOperations.modifySpellBaseDamage(1000, 99, 0, 0, spell, offTaskTarget),
        )
        assertEquals(1000, MagicMaxHitOperations.modifyStaffBaseDamage(1000, 0, 0, staff, offTaskTarget))
    }
}
