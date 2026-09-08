package org.rsmod.content.other.consumables

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsumableActivityPolicyTest {
    @Test
    fun `ordinary unrestricted consumables remain usable outside an activity`() {
        assertTrue(
            ConsumableActivityPolicy.canConsume(
                active = null,
                minigameOnly = "",
                raidOnly = "",
                ownershipVars = 0,
            )
        )
    }

    @Test
    fun `raid consumable requires exact activity kind key and ownership token`() {
        val active =
            ConsumableActivityToken(
                kind = ConsumableActivityKind.RAID,
                activity = "tombs_of_amascut",
                ownershipVars = 0x5A001234,
            )

        assertTrue(
            ConsumableActivityPolicy.canConsume(
                active,
                minigameOnly = "",
                raidOnly = "tombs_of_amascut",
                ownershipVars = active.ownershipVars,
            )
        )
        assertFalse(
            ConsumableActivityPolicy.canConsume(
                active,
                minigameOnly = "",
                raidOnly = "tombs_of_amascut",
                ownershipVars = 0x5A005678,
            )
        )
        assertFalse(
            ConsumableActivityPolicy.canConsume(
                active.copy(activity = "another_raid"),
                minigameOnly = "",
                raidOnly = "tombs_of_amascut",
                ownershipVars = active.ownershipVars,
            )
        )
        assertFalse(
            ConsumableActivityPolicy.canConsume(
                active.copy(kind = ConsumableActivityKind.MINIGAME),
                minigameOnly = "",
                raidOnly = "tombs_of_amascut",
                ownershipVars = active.ownershipVars,
            )
        )
    }

    @Test
    fun `ambiguous dual restriction is always rejected`() {
        val active =
            ConsumableActivityToken(
                ConsumableActivityKind.RAID,
                "tombs_of_amascut",
                0x5A001234,
            )
        assertFalse(
            ConsumableActivityPolicy.canConsume(
                active,
                minigameOnly = "nightmare_zone",
                raidOnly = "tombs_of_amascut",
                ownershipVars = active.ownershipVars,
            )
        )
    }
}
