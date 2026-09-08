package org.rsmod.api.instances

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class InstanceLogoutTabPolicyTest {
    @Test
    fun `Kraken uses Leave Instance without becoming a raid`() {
        assertEquals(StandaloneInstanceType.Kraken, StandaloneInstanceType.fromKey("kraken_cove"))
        assertEquals(
            InstanceLogoutTabPolicy.INTERFACE_ID,
            InstanceLogoutTabPolicy.interfaceFor("kraken_cove"),
        )
        assertNull(RaidInstanceType.fromKey("kraken_cove"))
        assertNull(RaidLogoutTabPolicy.interfaceFor("kraken_cove"))
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath("kraken_cove"))
    }

    @Test
    fun `Cerberus uses Leave Instance without becoming a raid`() {
        assertEquals(
            StandaloneInstanceType.Cerberus,
            StandaloneInstanceType.fromKey("cerberus_lair"),
        )
        assertEquals(
            InstanceLogoutTabPolicy.INTERFACE_ID,
            InstanceLogoutTabPolicy.interfaceFor("cerberus_lair"),
        )
        assertNull(RaidInstanceType.fromKey("cerberus_lair"))
        assertNull(RaidLogoutTabPolicy.interfaceFor("cerberus_lair"))
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath("cerberus_lair"))
    }

    @Test
    fun `Leviathan uses Leave Instance without becoming a raid`() {
        val key = "desert_treasure_ii_leviathan"
        assertEquals(StandaloneInstanceType.Leviathan, StandaloneInstanceType.fromKey(key))
        assertEquals(
            InstanceLogoutTabPolicy.INTERFACE_ID,
            InstanceLogoutTabPolicy.interfaceFor(key),
        )
        assertNull(RaidInstanceType.fromKey(key))
        assertNull(RaidLogoutTabPolicy.interfaceFor(key))
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath(key))
    }

    @Test
    fun `Vardorvis uses Leave Instance without raid retention`() {
        val key = "desert_treasure_ii_vardorvis"
        assertEquals(StandaloneInstanceType.Vardorvis, StandaloneInstanceType.fromKey(key))
        assertEquals(
            InstanceLogoutTabPolicy.INTERFACE_ID,
            InstanceLogoutTabPolicy.interfaceFor(key),
        )
        assertNull(RaidInstanceType.fromKey(key))
        assertNull(RaidLogoutTabPolicy.interfaceFor(key))
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath(key))
    }

    @Test
    fun `ordinary non raid instances keep the normal logout tab`() {
        assertNull(InstanceLogoutTabPolicy.interfaceFor("fight_caves"))
        assertNull(InstanceLogoutTabPolicy.interfaceFor("clan_mayhem"))
    }

    @Test
    fun `whisperer replaces logout with the shared leave instance interface`() {
        val key = "desert_treasure_ii_whisperer"
        assertEquals(StandaloneInstanceType.Whisperer, StandaloneInstanceType.fromKey(key))
        assertEquals(
            InstanceLogoutTabPolicy.INTERFACE_ID,
            InstanceLogoutTabPolicy.interfaceFor(key),
        )
    }

    @Test
    fun `Doom replaces logout with Leave Instance without becoming a raid`() {
        val key = "doom_of_mokhaiotl"
        assertEquals(StandaloneInstanceType.DoomOfMokhaiotl, StandaloneInstanceType.fromKey(key))
        assertEquals(
            InstanceLogoutTabPolicy.INTERFACE_ID,
            InstanceLogoutTabPolicy.interfaceFor(key),
        )
        assertNull(RaidInstanceType.fromKey(key))
        assertNull(RaidLogoutTabPolicy.interfaceFor(key))
        assertFalse(RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath(key))
    }
}
