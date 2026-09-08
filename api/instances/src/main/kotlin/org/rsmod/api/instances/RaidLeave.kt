package org.rsmod.api.instances

public enum class RaidInstanceType(
    public val key: String,
    public val displayName: String,
    public val retainsMembershipOnDeath: Boolean = true,
) {
    TheatreOfBlood("theatre_of_blood", "Theatre of Blood"),
    TombsOfAmascut("tombs_of_amascut", "Tombs of Amascut"),
    ChambersOfXeric("chambers_of_xeric", "Chambers of Xeric"),
    Yama("yama_domain", "Yama's Domain", retainsMembershipOnDeath = false);

    public companion object {
        public fun fromKey(key: String): RaidInstanceType? = entries.firstOrNull { it.key == key }
    }
}

public enum class RaidRewardClaimResult {
    Claimed,
    AlreadyClaimed,
    NothingToClaim,
    InventoryFull,
    Busy,
}

public data class RaidRewardClaim<out T>(
    public val result: RaidRewardClaimResult,
    public val reward: T? = null,
)

public enum class RaidLeaveResult {
    Unhandled,
    Left,
    InventoryFull,
    Busy,
    RewardUnavailable,
    Failed,
}

public object RaidLogoutTabPolicy {
    public const val INTERFACE_ID: Int = 1200
    public const val LEAVE_COMPONENT: Int = 8

    public fun interfaceFor(instanceKey: String): Int? =
        if (RaidInstanceType.fromKey(instanceKey) != null) INTERFACE_ID else null

    /** Raid content owns its death, spectator, wipe, and re-entry flow. */
    public fun retainsInstanceMembershipOnDeath(instanceKey: String): Boolean =
        RaidInstanceType.fromKey(instanceKey)?.retainsMembershipOnDeath == true
}

public object RaidLeavePolicy {
    public fun canLeaveAfterClaim(result: RaidRewardClaimResult): Boolean =
        result == RaidRewardClaimResult.Claimed || result == RaidRewardClaimResult.AlreadyClaimed
}
