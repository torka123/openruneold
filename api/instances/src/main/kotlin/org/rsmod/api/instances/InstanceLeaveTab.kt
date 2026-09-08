package org.rsmod.api.instances

/** Non-raid instances that intentionally replace Logout with the shared Leave Instance tab. */
public enum class StandaloneInstanceType(public val key: String, public val displayName: String) {
    Kraken("kraken_cove", "Kraken"),
    Cerberus("cerberus_lair", "Cerberus"),
    Vardorvis("desert_treasure_ii_vardorvis", "Vardorvis"),
    Leviathan("desert_treasure_ii_leviathan", "The Leviathan"),
    Whisperer("desert_treasure_ii_whisperer", "The Whisperer"),
    DoomOfMokhaiotl("doom_of_mokhaiotl", "Doom of Mokhaiotl");

    public companion object {
        public fun fromKey(key: String): StandaloneInstanceType? =
            entries.firstOrNull { it.key == key }
    }
}

/** Shared UI policy. Standalone encounters remain separate from raid death/reward semantics. */
public object InstanceLogoutTabPolicy {
    public const val INTERFACE_ID: Int = RaidLogoutTabPolicy.INTERFACE_ID
    public const val LEAVE_COMPONENT: Int = RaidLogoutTabPolicy.LEAVE_COMPONENT

    public fun interfaceFor(instanceKey: String): Int? =
        if (
            RaidInstanceType.fromKey(instanceKey) != null ||
                StandaloneInstanceType.fromKey(instanceKey) != null
        ) {
            INTERFACE_ID
        } else {
            null
        }
}
