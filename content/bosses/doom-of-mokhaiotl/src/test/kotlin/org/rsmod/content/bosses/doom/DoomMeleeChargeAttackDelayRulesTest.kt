package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoomMeleeChargeAttackDelayRulesTest {
    @Test
    fun `only the first owned melee-charge swing claims the delay bypass`() {
        assertTrue(
            DoomMeleeChargeAttackDelayRules.mayClaim(
                state = DoomState.MELEE_CHARGE,
                bossOwnedByPlayer = true,
                alreadyClaimed = false,
            )
        )
        assertFalse(
            DoomMeleeChargeAttackDelayRules.mayClaim(
                state = DoomState.ACTIVE,
                bossOwnedByPlayer = true,
                alreadyClaimed = false,
            )
        )
        assertFalse(
            DoomMeleeChargeAttackDelayRules.mayClaim(
                state = DoomState.MELEE_CHARGE,
                bossOwnedByPlayer = false,
                alreadyClaimed = false,
            )
        )
        assertFalse(
            DoomMeleeChargeAttackDelayRules.mayClaim(
                state = DoomState.MELEE_CHARGE,
                bossOwnedByPlayer = true,
                alreadyClaimed = true,
            )
        )
    }
}
