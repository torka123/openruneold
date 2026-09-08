package org.rsmod.api.net.social

import org.rsmod.game.stat.PlayerSkillXPTable

public object ClanBroadcastLimits {
    public const val MINIMUM_LEVEL: Int = 50
    public const val MAX_VALUE_THRESHOLD: Int = Int.MAX_VALUE
    public const val DEFAULT_POST_99_XP_INTERVAL: Int = 10_000_000
    public const val POST_99_XP_INTERVAL_UNIT: Int = 1_000_000
    public const val MINIMUM_LEVEL_THRESHOLD: Int = 0
    public const val MAXIMUM_LEVEL_THRESHOLD: Int = 99
}

public enum class ClanBroadcastKey(public val storageKey: String) {
    Membership("membership"),
    QuestCompletion("quest_completion"),
    AchievementDiary("achievement_diary"),
    CollectionLog("collection_log"),
    HardcoreStatus("hardcore_status"),
    PersonalBest("personal_best"),
    LevelUp("level_up"),
    Post99Xp("post_99_xp"),
    LootOrPet("loot_or_pet"),
    CombatAchievementTier("combat_achievement_tier"),
    CombatAchievementTask("combat_achievement_task"),
    PlayerKill("player_kill"),
    PlayerDefeat("player_defeat"),
    LootKey("loot_key"),
    LeagueShared("league_shared"),
    LeagueRelicTier("league_relic_tier"),
    LeagueAreaUnlock("league_area_unlock"),
    LeagueRank("league_rank"),
    CombatMastery("combat_mastery");

    public companion object {
        private val byStorageKey: Map<String, ClanBroadcastKey> =
            entries.associateBy { it.storageKey }

        public fun fromStorageKey(key: String): ClanBroadcastKey? = byStorageKey[key]
    }
}

public enum class ClanLevelBroadcastInterval(public val levels: Int) {
    EveryLevel(1),
    EveryFiveLevels(5),
    EveryTenLevels(10),
}

public enum class ClanCollectionBroadcastInterval(public val slots: Int) {
    EverySlot(1),
    EveryTenSlots(10),
    EveryTwentyFiveSlots(25),
    EveryFiftySlots(50),
}

public enum class ClanCombatAchievementTier(public val storageId: Int) {
    All(-1),
    Easy(0),
    Medium(1),
    Hard(2),
    Elite(3),
    Master(4),
    Grandmaster(5),
}

public data class ClanBroadcastSettings(
    val minimumRanks: Map<ClanBroadcastKey, Int>,
    val levelInterval: ClanLevelBroadcastInterval = ClanLevelBroadcastInterval.EveryTenLevels,
    val post99XpInterval: Int = ClanBroadcastLimits.DEFAULT_POST_99_XP_INTERVAL,
    val collectionInterval: ClanCollectionBroadcastInterval =
        ClanCollectionBroadcastInterval.EverySlot,
    val minimumLootValue: Int = 0,
    val minimumPlayerKillValue: Int = 0,
    val minimumLootKeyValue: Int = 0,
    val minimumCombatAchievementTier: ClanCombatAchievementTier = ClanCombatAchievementTier.Easy,
    val mainGameWorldsOnly: Boolean = false,
    val eventStartingNotificationsEnabled: Boolean = true,
    val levelEveryTenMinimum: Int =
        if (levelInterval == ClanLevelBroadcastInterval.EveryTenLevels) 50 else 0,
    val levelEveryFiveMinimum: Int =
        if (levelInterval == ClanLevelBroadcastInterval.EveryFiveLevels) 50 else 0,
    val levelEveryOneMinimum: Int =
        if (levelInterval == ClanLevelBroadcastInterval.EveryLevel) 50 else 0,
) {
    public fun validationError(): String? {
        val expected = ClanBroadcastKey.entries.toSet()
        if (minimumRanks.keys != expected) return "Every clan broadcast rank must be configured."
        if (minimumRanks.values.any { !ClanRanks.isValidThreshold(it) }) {
            return "A clan broadcast rank is not valid."
        }
        val levelThresholds =
            listOf(levelEveryTenMinimum, levelEveryFiveMinimum, levelEveryOneMinimum)
        if (
            levelThresholds.any {
                it !in
                    ClanBroadcastLimits.MINIMUM_LEVEL_THRESHOLD..ClanBroadcastLimits
                            .MAXIMUM_LEVEL_THRESHOLD
            }
        ) {
            return "Clan level-broadcast thresholds must be between 0 and 99."
        }
        if (
            post99XpInterval < ClanBroadcastLimits.POST_99_XP_INTERVAL_UNIT ||
                post99XpInterval % ClanBroadcastLimits.POST_99_XP_INTERVAL_UNIT != 0
        ) {
            return "The post-99 XP interval must be a positive whole number of millions."
        }
        if (minimumLootValue < 0 || minimumPlayerKillValue < 0 || minimumLootKeyValue < 0) {
            return "Clan broadcast value thresholds cannot be negative."
        }
        return null
    }

    public companion object {
        public fun defaults(): ClanBroadcastSettings =
            ClanBroadcastSettings(
                minimumRanks = ClanBroadcastKey.entries.associateWith { ClanRanks.MEMBER }
            )
    }
}

public object ClanBroadcastRules {
    public fun shouldBroadcastLevel(level: Int, interval: ClanLevelBroadcastInterval): Boolean =
        level >= ClanBroadcastLimits.MINIMUM_LEVEL && level % interval.levels == 0

    /** Revision-240 exposes independent starting levels for 10, 5 and 1-level broadcasts. */
    public fun shouldBroadcastLevel(level: Int, settings: ClanBroadcastSettings): Boolean =
        enabledLevelThreshold(level, settings.levelEveryTenMinimum, 10) ||
            enabledLevelThreshold(level, settings.levelEveryFiveMinimum, 5) ||
            enabledLevelThreshold(level, settings.levelEveryOneMinimum, 1)

    private fun enabledLevelThreshold(level: Int, minimum: Int, increment: Int): Boolean =
        minimum > 0 && level >= minimum && level % increment == 0

    public fun shouldBroadcastCollectionSlot(
        slotCount: Int,
        interval: ClanCollectionBroadcastInterval,
        isRareItem: Boolean,
        isPet: Boolean,
    ): Boolean = isRareItem || isPet || (slotCount > 0 && slotCount % interval.slots == 0)

    public fun shouldBroadcastLoot(totalValue: Long, minimumValue: Int): Boolean =
        totalValue >= 0L && minimumValue > 0 && totalValue >= minimumValue.toLong()

    public fun shouldBroadcastPost99Xp(previousXp: Int, currentXp: Int, interval: Int): Boolean {
        if (interval <= 0 || currentXp <= previousXp) return false
        val level99Xp = PlayerSkillXPTable.getXPFromLevel(99)
        if (currentXp <= level99Xp) return false
        val eligiblePrevious = maxOf(previousXp, level99Xp)
        return eligiblePrevious / interval < currentXp / interval
    }
}
