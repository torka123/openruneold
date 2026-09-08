package org.rsmod.content.interfaces.gameframe.script

import org.rsmod.api.net.social.ClanBroadcastKey
import org.rsmod.api.net.social.ClanPermissionKey

/** One leaf created by revision-240 clientscript 4463. Order is enum 3825 category order. */
internal data class ClanNativePermissionRow(
    val label: String,
    val kind: Kind,
    val permission: ClanPermissionKey? = null,
    val broadcast: ClanBroadcastKey? = null,
) {
    internal enum class Kind {
        PermissionRank,
        BroadcastRank,
        GuestAccess,
        GuestCombat,
        GuestTotal,
        GuestQuestPoints,
        GuestIron,
        EventNotifications,
        MainWorldOnly,
        CollectionInterval,
        LevelEveryTen,
        LevelEveryFive,
        LevelEveryOne,
        Post99Interval,
        LootValue,
        CombatAchievementTaskTier,
        PlayerKillValue,
        LootKeyValue,
    }
}

internal object ClanNativePermissionRows {
    private fun permission(label: String, key: ClanPermissionKey) =
        ClanNativePermissionRow(
            label,
            ClanNativePermissionRow.Kind.PermissionRank,
            permission = key,
        )

    private fun broadcast(label: String, key: ClanBroadcastKey) =
        ClanNativePermissionRow(label, ClanNativePermissionRow.Kind.BroadcastRank, broadcast = key)

    private fun setting(label: String, kind: ClanNativePermissionRow.Kind) =
        ClanNativePermissionRow(label, kind)

    /** Category headers are not rows and therefore do not appear in this list. */
    val ALL: List<ClanNativePermissionRow> =
        listOf(
            // Special row built from struct 2943 before enum 3825 categories (script 4457).
            permission("Permission access", ClanPermissionKey.PermissionAccess),

            // Management (enum 3826)
            permission("Edit rank titles", ClanPermissionKey.EditRankTitles),
            permission("Assign member ranks", ClanPermissionKey.AssignMemberRanks),
            permission("Recruitment", ClanPermissionKey.Recruitment),
            permission("Expel members", ClanPermissionKey.ExpelMembers),
            permission("Edit public profile", ClanPermissionKey.EditPublicProfile),
            permission("Icons", ClanPermissionKey.EditMotif),
            // Chat (enum 3827)
            permission("Talk in chat", ClanPermissionKey.TalkInChat),
            permission("Kick from chat", ClanPermissionKey.KickGuests),
            permission("Ban from chat", ClanPermissionKey.BanGuests),
            setting("Guests may listen in chat", ClanNativePermissionRow.Kind.GuestAccess),
            setting("Guest minimum combat", ClanNativePermissionRow.Kind.GuestCombat),
            setting("Guest minimum skill total", ClanNativePermissionRow.Kind.GuestTotal),
            setting("Guest minimum QP", ClanNativePermissionRow.Kind.GuestQuestPoints),
            setting("Guest Iron status", ClanNativePermissionRow.Kind.GuestIron),
            // Events (enum 3829)
            permission("Organise events", ClanPermissionKey.OrganiseEvents),
            setting("Event starting notification", ClanNativePermissionRow.Kind.EventNotifications),
            permission("Initiate Clan Wars", ClanPermissionKey.InitiateClanWars),
            // Coffer (enum 3830)
            permission("Deposit into coffer", ClanPermissionKey.CofferDeposit),
            permission("Withdraw from coffer", ClanPermissionKey.CofferWithdraw),
            // Clan Hall (enum 3831)
            permission("Enter clan hall", ClanPermissionKey.HallEnter),
            permission("Edit clan hall", ClanPermissionKey.HallEditSettings),
            permission("Deposit in the party chest", ClanPermissionKey.HallPartyChestDeposit),
            permission("Pull the lever for the party chest", ClanPermissionKey.HallPullLever),
            permission("Get drops from party balloons", ClanPermissionKey.HallPartyBalloonLoot),
            // Notifications (enum 4228)
            broadcast("Joining/Leaving clan", ClanBroadcastKey.Membership),
            setting("Broadcasts from main world only", ClanNativePermissionRow.Kind.MainWorldOnly),
            broadcast("Quest broadcasts", ClanBroadcastKey.QuestCompletion),
            broadcast("Achievement diary broadcasts", ClanBroadcastKey.AchievementDiary),
            broadcast("Hardcore death broadcasts", ClanBroadcastKey.HardcoreStatus),
            broadcast("Collection log broadcasts", ClanBroadcastKey.CollectionLog),
            setting(
                "Collection log broadcast every x logs",
                ClanNativePermissionRow.Kind.CollectionInterval,
            ),
            broadcast("Personal best broadcasts", ClanBroadcastKey.PersonalBest),
            // Notifications - Skilling (enum 4229)
            broadcast("Level-up broadcasts", ClanBroadcastKey.LevelUp),
            setting("Level-up increments of 10", ClanNativePermissionRow.Kind.LevelEveryTen),
            setting("Level-up increments of 5", ClanNativePermissionRow.Kind.LevelEveryFive),
            setting("Level-up increments of 1", ClanNativePermissionRow.Kind.LevelEveryOne),
            broadcast("Post-99 XP broadcasts", ClanBroadcastKey.Post99Xp),
            setting("Post-99 XP broadcast intervals", ClanNativePermissionRow.Kind.Post99Interval),
            // Notifications - PvM (enum 4230)
            broadcast("Loot broadcasts", ClanBroadcastKey.LootOrPet),
            setting("Loot broadcast value", ClanNativePermissionRow.Kind.LootValue),
            broadcast("Combat Achievement tier broadcasts", ClanBroadcastKey.CombatAchievementTier),
            broadcast("Combat Achievement task broadcasts", ClanBroadcastKey.CombatAchievementTask),
            setting(
                "Combat Achievement task threshold",
                ClanNativePermissionRow.Kind.CombatAchievementTaskTier,
            ),
            // Notifications - PvP (enum 4231)
            broadcast("PK broadcasts", ClanBroadcastKey.PlayerKill),
            setting("PK broadcast value", ClanNativePermissionRow.Kind.PlayerKillValue),
            broadcast("Player defeat broadcasts", ClanBroadcastKey.PlayerDefeat),
            broadcast("Loot key broadcasts", ClanBroadcastKey.LootKey),
            setting("Loot key broadcast value", ClanNativePermissionRow.Kind.LootKeyValue),
            // Notifications - Leagues (enum 5731)
            broadcast("Shared league broadcasts", ClanBroadcastKey.LeagueShared),
            broadcast("Relic tier", ClanBroadcastKey.LeagueRelicTier),
            broadcast("Area unlocks", ClanBroadcastKey.LeagueAreaUnlock),
            broadcast("League rank", ClanBroadcastKey.LeagueRank),
            broadcast("Combat Masteries", ClanBroadcastKey.CombatMastery),
        )
}
