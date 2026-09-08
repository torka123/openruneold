package org.rsmod.api.dailyadventure

public enum class DailyAdventurePath {
    Combat,
    Skilling,
}

public enum class DailyAdventureDifficulty {
    Easy,
    Medium,
    Hard,
}

public enum class DailyAdventureAction {
    NpcKill,
    NpcDamage,
    NpcHit,
    BossKill,
    BossDamage,
    SpecialAttack,
    SlayerAssignment,
    BarrowsChest,
    RaidCompletion,
    CombatMinigameRound,
    PlayerKill,
    BountyTargetKill,
    PlayerDamage,
    SkillingAction,
    BaseExperience,
    AgilityLap,
    ConstructionHotspot,
    GiantsFoundryCommission,
    SkillingActivityRound,
}

public enum class DailyAdventureTag {
    Pvp,
    Boss,
    RaidOrMinigame,
    Wilderness,
    GenericKill,
    CombatStyle,
    Damage,
    RequiresOtherPlayers,
    TimedOrSpecial,
    MultiSkill,
    SpecialActivity,
}

public data class DailyAdventureProgressEvent(
    public val id: String,
    public val action: DailyAdventureAction,
    public val amount: Long = 1L,
    public val skill: String? = null,
    public val combatStyle: String? = null,
    public val category: String? = null,
    public val activity: String? = null,
    public val wilderness: Boolean = false,
    public val slayerTarget: Boolean = false,
    public val rewardQualified: Boolean = true,
    public val successful: Boolean = true,
    public val dimension: String? = null,
)

public data class DailyAdventureTaskDefinition(
    public val id: String,
    public val path: DailyAdventurePath,
    public val difficulty: DailyAdventureDifficulty,
    public val name: String,
    public val description: String,
    public val goal: Long,
    public val reward: Long,
    public val weight: Int = 100,
    public val requiredFeatures: Set<String> = emptySet(),
    public val tags: Set<DailyAdventureTag> = emptySet(),
    public val skills: Set<String> = emptySet(),
    public val requiredDimensions: Set<String> = emptySet(),
    public val minimumPerDimension: Long = 0L,
    public val minimumDistinctDimensions: Int = 0,
    public val perDimensionCap: Long? = null,
    public val matcher: (DailyAdventureProgressEvent) -> Long,
) {
    public fun isEligible(enabledFeatures: Set<String>): Boolean =
        enabledFeatures.containsAll(requiredFeatures)
}

public data class DailyAdventureAssignment(
    public val slot: Int,
    public var taskId: String,
    public val difficulty: DailyAdventureDifficulty,
    public val generatedDay: Long,
    public var resolvedTarget: String? = null,
    public var goal: Long,
    public var reward: Long,
    public var progress: Long = 0L,
    public var complete: Boolean = false,
    public var rewardCredited: Boolean = false,
    public var rerollCount: Int = 0,
    public var completedAtEpochMillis: Long? = null,
    public val dimensions: MutableMap<String, Long> = mutableMapOf(),
)

public data class DailyAdventureLifetimeStats(
    public var tasksCompleted: Long = 0L,
    public var pathsClaimed: Long = 0L,
    public var rerollsUsed: Long = 0L,
    public var pointsEarned: Long = 0L,
    public var pointsSpent: Long = 0L,
    public var protectionChargesConsumed: Long = 0L,
)

public data class DailyAdventureState(
    public val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    public var day: Long = UNINITIALIZED_DAY,
    public var memberDayEntitlement: Boolean = false,
    public var selectedFreePath: DailyAdventurePath? = null,
    public val activePaths: MutableSet<DailyAdventurePath> = mutableSetOf(),
    public val assignments:
        MutableMap<DailyAdventurePath, MutableList<DailyAdventureAssignment>> = mutableMapOf(),
    public val claimedPaths: MutableSet<DailyAdventurePath> = mutableSetOf(),
    public val seenTaskIds: MutableMap<DailyAdventurePath, MutableSet<String>> = mutableMapOf(),
    public var rerollsUsedToday: Int = 0,
    public var rerollSequence: Int = 0,
    public var taskPoints: Long = 0L,
    public var streak: Int = 0,
    public var bestStreak: Int = 0,
    public var lastClaimDay: Long? = null,
    public var streakAdvancedDay: Long? = null,
    public var streakProtectionCharges: Int = 0,
    public var memberBoxGrantedDay: Long? = null,
    public var pendingMemberBoxes: Int = 0,
    public var updatedAtEpochMillis: Long = 0L,
    public val recentEventIds: MutableList<String> = mutableListOf(),
    public val lifetime: DailyAdventureLifetimeStats = DailyAdventureLifetimeStats(),
    public val recentRerollResults: MutableMap<String, DailyAdventureRerollResult> = mutableMapOf(),
    public val activeScrolls: MutableMap<String, Long> = mutableMapOf(),
) {
    public fun rerollCap(): Int = if (memberDayEntitlement) MEMBER_REROLLS else FREE_REROLLS

    public fun rerollsRemaining(): Int = (rerollCap() - rerollsUsedToday).coerceAtLeast(0)

    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 1
        public const val UNINITIALIZED_DAY: Long = Long.MIN_VALUE
        public const val FREE_REROLLS: Int = 3
        public const val MEMBER_REROLLS: Int = 5
        public const val MAX_PROTECTION_CHARGES: Int = 3
        public const val MAX_RECENT_EVENTS: Int = 512
        public const val MAX_RECENT_REROLLS: Int = 64
    }
}

public enum class DailyAdventureSelectionStatus {
    Selected,
    AlreadySelected,
    MembersAlreadyActive,
    InvalidDay,
}

public data class DailyAdventureProgressResult(
    public val duplicateEvent: Boolean = false,
    public val changedAssignments: Int = 0,
    public val completedTaskIds: List<String> = emptyList(),
    public val pointsAwarded: Long = 0L,
)

public enum class DailyAdventureRerollStatus {
    Rerolled,
    InvalidPath,
    InvalidSlot,
    NoRerollsRemaining,
    ProgressAlreadyStarted,
    CompletedTask,
    NoEligibleReplacement,
}

public data class DailyAdventureRerollResult(
    public val status: DailyAdventureRerollStatus,
    public val previousTaskId: String? = null,
    public val replacementTaskId: String? = null,
    public val duplicateRequest: Boolean = false,
)

public enum class DailyAdventureClaimStatus {
    Claimed,
    InvalidPath,
    AlreadyClaimed,
    NotEnoughTasks,
}

public data class DailyAdventureClaimResult(
    public val status: DailyAdventureClaimStatus,
    public val bonusPoints: Long = 0L,
    public val streak: Int = 0,
    public val streakAdvanced: Boolean = false,
)

public data class DailyAdventureDayResult(
    public val reset: Boolean,
    public val upgradedToMember: Boolean,
    public val memberBoxQueued: Boolean,
    public val protectionChargesConsumed: Int,
)

public object DailyAdventureRewards {
    public fun streakBonus(streak: Int): Long =
        when (streak.coerceAtLeast(1)) {
            in 1..2 -> 100L
            in 3..6 -> 115L
            in 7..13 -> 135L
            in 14..29 -> 160L
            in 30..59 -> 200L
            in 60..99 -> 240L
            else -> 300L
        }
}
