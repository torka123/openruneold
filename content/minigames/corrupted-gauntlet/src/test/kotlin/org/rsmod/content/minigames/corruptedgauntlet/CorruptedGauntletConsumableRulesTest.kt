package org.rsmod.content.minigames.corruptedgauntlet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorruptedGauntletConsumableRulesTest {
    @Test
    fun `ordinary paddlefish and crystal combo food use distinct healing values`() {
        assertEquals(20, CorruptedGauntletConsumableRules.PADDLEFISH_HEAL)
        assertEquals(16, CorruptedGauntletConsumableRules.CRYSTAL_PADDLEFISH_HEAL)
    }

    @Test
    fun `egniol dose chain ends in an empty vial`() {
        val rules = CorruptedGauntletConsumableRules
        assertEquals(4, rules.egniolDose(CorruptedGauntletConfig.Obj.POTION_FOUR_DOSE))
        assertEquals(
            CorruptedGauntletConfig.Obj.POTION_THREE_DOSE,
            rules.nextEgniolItem(CorruptedGauntletConfig.Obj.POTION_FOUR_DOSE),
        )
        assertEquals(
            CorruptedGauntletConfig.Obj.POTION_TWO_DOSE,
            rules.nextEgniolItem(CorruptedGauntletConfig.Obj.POTION_THREE_DOSE),
        )
        assertEquals(
            CorruptedGauntletConfig.Obj.POTION_ONE_DOSE,
            rules.nextEgniolItem(CorruptedGauntletConfig.Obj.POTION_TWO_DOSE),
        )
        assertEquals(
            CorruptedGauntletConfig.Obj.VIAL,
            rules.nextEgniolItem(CorruptedGauntletConfig.Obj.POTION_ONE_DOSE),
        )
        assertNull(rules.egniolDose(-1))
        assertNull(rules.nextEgniolItem(-1))
    }

    @Test
    fun `egniol prayer restoration is one quarter base plus seven`() {
        assertEquals(7, CorruptedGauntletConsumableRules.egniolPrayerRestore(1))
        assertEquals(22, CorruptedGauntletConsumableRules.egniolPrayerRestore(60))
        assertEquals(31, CorruptedGauntletConsumableRules.egniolPrayerRestore(99))
    }

    @Test
    fun `tool storage offers only tools the player no longer carries`() {
        val rules = CorruptedGauntletConsumableRules
        assertEquals(rules.toolIds, rules.missingTools(emptySet()))
        assertEquals(
            listOf(CorruptedGauntletConfig.Obj.HARPOON),
            rules.missingTools(rules.toolIds.toSet() - CorruptedGauntletConfig.Obj.HARPOON),
        )
        assertEquals(emptyList(), rules.missingTools(rules.toolIds.toSet()))
    }
}
