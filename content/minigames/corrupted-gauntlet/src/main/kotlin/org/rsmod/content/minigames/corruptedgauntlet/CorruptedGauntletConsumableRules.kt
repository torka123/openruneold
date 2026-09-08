package org.rsmod.content.minigames.corruptedgauntlet

internal data class CorruptedGauntletToolRecovery(val recovered: Int, val unavailable: Int)

internal object CorruptedGauntletConsumableRules {
    const val PADDLEFISH_HEAL = 20
    const val CRYSTAL_PADDLEFISH_HEAL = 16
    const val EGNIOL_RUN_ENERGY_PERCENT = 40
    const val STANDARD_STAMINA_RUN_ENERGY_PERCENT = 20

    val toolIds =
        listOf(
            CorruptedGauntletConfig.Obj.SCEPTRE,
            CorruptedGauntletConfig.Obj.AXE,
            CorruptedGauntletConfig.Obj.PICKAXE,
            CorruptedGauntletConfig.Obj.HARPOON,
            CorruptedGauntletConfig.Obj.PESTLE_AND_MORTAR,
        )

    val egniolDoseIds =
        setOf(
            CorruptedGauntletConfig.Obj.POTION_ONE_DOSE,
            CorruptedGauntletConfig.Obj.POTION_TWO_DOSE,
            CorruptedGauntletConfig.Obj.POTION_THREE_DOSE,
            CorruptedGauntletConfig.Obj.POTION_FOUR_DOSE,
        )

    fun egniolDose(itemId: Int): Int? =
        when (itemId) {
            CorruptedGauntletConfig.Obj.POTION_ONE_DOSE -> 1
            CorruptedGauntletConfig.Obj.POTION_TWO_DOSE -> 2
            CorruptedGauntletConfig.Obj.POTION_THREE_DOSE -> 3
            CorruptedGauntletConfig.Obj.POTION_FOUR_DOSE -> 4
            else -> null
        }

    fun nextEgniolItem(itemId: Int): Int? =
        when (itemId) {
            CorruptedGauntletConfig.Obj.POTION_FOUR_DOSE ->
                CorruptedGauntletConfig.Obj.POTION_THREE_DOSE
            CorruptedGauntletConfig.Obj.POTION_THREE_DOSE ->
                CorruptedGauntletConfig.Obj.POTION_TWO_DOSE
            CorruptedGauntletConfig.Obj.POTION_TWO_DOSE ->
                CorruptedGauntletConfig.Obj.POTION_ONE_DOSE
            CorruptedGauntletConfig.Obj.POTION_ONE_DOSE -> CorruptedGauntletConfig.Obj.VIAL
            else -> null
        }

    fun egniolPrayerRestore(basePrayer: Int): Int = basePrayer.coerceAtLeast(0) / 4 + 7

    fun missingTools(carriedItemIds: Set<Int>): List<Int> =
        toolIds.filterNot(carriedItemIds::contains)
}
