package org.rsmod.content.areas.wilderness

internal const val MIN_WILDERNESS_COMBAT_LEVEL: Int = 3
internal const val MAX_WILDERNESS_COMBAT_LEVEL: Int = 126

internal data class WildernessCombatBracket(
    val wildernessLevel: Int,
    val minimumCombatLevel: Int,
    val maximumCombatLevel: Int,
)

internal object WildernessCombatLevelRules {
    fun bracket(combatLevel: Int, wildernessLevel: Int): WildernessCombatBracket {
        require(wildernessLevel >= 1) { "Wilderness level must be positive." }
        return WildernessCombatBracket(
            wildernessLevel = wildernessLevel,
            minimumCombatLevel =
                (combatLevel - wildernessLevel).coerceAtLeast(MIN_WILDERNESS_COMBAT_LEVEL),
            maximumCombatLevel =
                (combatLevel + wildernessLevel).coerceAtMost(MAX_WILDERNESS_COMBAT_LEVEL),
        )
    }

    fun permitsCombatLevel(
        attackerCombatLevel: Int,
        targetCombatLevel: Int,
        wildernessLevel: Int,
    ): Boolean {
        val bracket = bracket(attackerCombatLevel, wildernessLevel)
        return targetCombatLevel in bracket.minimumCombatLevel..bracket.maximumCombatLevel
    }
}
