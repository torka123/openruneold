package org.rsmod.content.areas.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals

class WildernessCombatLevelRulesTest {
    @Test
    fun `combat brackets clamp to supported player levels`() {
        assertEquals(
            WildernessCombatBracket(30, 96, 126),
            WildernessCombatLevelRules.bracket(combatLevel = 126, wildernessLevel = 30),
        )
        assertEquals(
            WildernessCombatBracket(20, 80, 120),
            WildernessCombatLevelRules.bracket(combatLevel = 100, wildernessLevel = 20),
        )
        assertEquals(
            WildernessCombatBracket(56, 3, 59),
            WildernessCombatLevelRules.bracket(combatLevel = 3, wildernessLevel = 56),
        )
    }

    @Test
    fun `displayed bracket and pvp level validation always agree`() {
        for (wildernessLevel in 1..56) {
            for (attackerLevel in MIN_WILDERNESS_COMBAT_LEVEL..MAX_WILDERNESS_COMBAT_LEVEL) {
                val bracket =
                    WildernessCombatLevelRules.bracket(
                        combatLevel = attackerLevel,
                        wildernessLevel = wildernessLevel,
                    )
                val displayed = bracket.minimumCombatLevel..bracket.maximumCombatLevel
                for (targetLevel in MIN_WILDERNESS_COMBAT_LEVEL..MAX_WILDERNESS_COMBAT_LEVEL) {
                    assertEquals(
                        targetLevel in displayed,
                        WildernessCombatLevelRules.permitsCombatLevel(
                            attackerCombatLevel = attackerLevel,
                            targetCombatLevel = targetLevel,
                            wildernessLevel = wildernessLevel,
                        ),
                    )
                }
            }
        }
    }
}
