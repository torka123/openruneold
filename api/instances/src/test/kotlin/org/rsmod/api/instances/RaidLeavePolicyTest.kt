package org.rsmod.api.instances

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaidLeavePolicyTest {
    @Test
    fun `all implemented raids use the custom logout tab`() {
        val expected =
            mapOf(
                "theatre_of_blood" to RaidInstanceType.TheatreOfBlood,
                "tombs_of_amascut" to RaidInstanceType.TombsOfAmascut,
                "chambers_of_xeric" to RaidInstanceType.ChambersOfXeric,
            )

        expected.forEach { (key, type) ->
            assertEquals(type, RaidInstanceType.fromKey(key))
            assertEquals(RaidLogoutTabPolicy.INTERFACE_ID, RaidLogoutTabPolicy.interfaceFor(key))
            assertTrue(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath(key))
        }

        assertEquals(RaidInstanceType.Yama, RaidInstanceType.fromKey("yama_domain"))
        assertEquals(
            RaidLogoutTabPolicy.INTERFACE_ID,
            RaidLogoutTabPolicy.interfaceFor("yama_domain"),
        )
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath("yama_domain"))
    }

    @Test
    fun `non raid instances retain the normal logout tab`() {
        assertNull(RaidInstanceType.fromKey("fight_caves"))
        assertNull(RaidLogoutTabPolicy.interfaceFor("fight_caves"))
        assertNull(RaidLogoutTabPolicy.interfaceFor("clan_mayhem"))
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath("fight_caves"))
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath("clan_mayhem"))
    }

    @Test
    fun `leave runs only after a successful or previously completed claim`() {
        assertTrue(RaidLeavePolicy.canLeaveAfterClaim(RaidRewardClaimResult.Claimed))
        assertTrue(RaidLeavePolicy.canLeaveAfterClaim(RaidRewardClaimResult.AlreadyClaimed))
        assertFalse(RaidLeavePolicy.canLeaveAfterClaim(RaidRewardClaimResult.NothingToClaim))
        assertFalse(RaidLeavePolicy.canLeaveAfterClaim(RaidRewardClaimResult.InventoryFull))
        assertFalse(RaidLeavePolicy.canLeaveAfterClaim(RaidRewardClaimResult.Busy))
    }

    @Test
    fun `raid keys are unique`() {
        assertEquals(
            RaidInstanceType.entries.size,
            RaidInstanceType.entries.map(RaidInstanceType::key).toSet().size,
        )
    }
}
