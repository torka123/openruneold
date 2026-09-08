package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals

class DoomShieldRuntimeRulesTest {
    @Test
    fun `only demonbane damage reaches the shield`() {
        assertEquals(
            0,
            DoomShieldRuntimeRules.acceptedDamage(
                demonbane = false,
                rolledDamage = 42,
                remainingShield = 500,
            ),
        )
        assertEquals(
            42,
            DoomShieldRuntimeRules.acceptedDamage(
                demonbane = true,
                rolledDamage = 42,
                remainingShield = 500,
            ),
        )
    }

    @Test
    fun `an accurate zero remains zero rather than manufacturing shield damage`() {
        assertEquals(
            0,
            DoomShieldRuntimeRules.acceptedDamage(
                demonbane = true,
                rolledDamage = 0,
                remainingShield = 500,
            ),
        )
    }

    @Test
    fun `shield damage clamps at remaining shield and breaks exactly at zero`() {
        assertEquals(
            12,
            DoomShieldRuntimeRules.acceptedDamage(
                demonbane = true,
                rolledDamage = 80,
                remainingShield = 12,
            ),
        )
        assertEquals(0, DoomShieldRuntimeRules.remainingShield(100, 100))
        assertEquals(0, DoomShieldRuntimeRules.remainingShield(40, 100))
    }

    @Test
    fun `stale shield impacts refund only the damage applied to the next phase`() {
        assertEquals(
            525,
            DoomShieldRuntimeRules.restoredHitpointsAfterStaleShieldImpact(
                currentHitpoints = 485,
                maximumHitpoints = 525,
                appliedDamage = 40,
            ),
        )
        assertEquals(
            525,
            DoomShieldRuntimeRules.restoredHitpointsAfterStaleShieldImpact(
                currentHitpoints = 520,
                maximumHitpoints = 525,
                appliedDamage = 40,
            ),
        )
    }

    @Test
    fun `shield entry and teardown preserve normal boss health`() {
        assertEquals(306, DoomShieldRuntimeRules.captureNormalHitpoints(-1, 306))
        assertEquals(280, DoomShieldRuntimeRules.captureNormalHitpoints(306, 280))
        assertEquals(
            306,
            DoomShieldRuntimeRules.captureNormalHitpoints(306, DoomRules.SHIELD_HITPOINTS),
            "the temporary 500-point shield pool must never replace saved boss health",
        )
        assertEquals(306, DoomShieldRuntimeRules.restoreNormalHitpoints(306, 575))
        assertEquals(575, DoomShieldRuntimeRules.restoreNormalHitpoints(600, 575))
        assertEquals(null, DoomShieldRuntimeRules.restoreNormalHitpoints(-1, 575))
    }
}
