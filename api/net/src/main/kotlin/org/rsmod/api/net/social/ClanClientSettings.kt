package org.rsmod.api.net.social

import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.VarClan

/**
 * Revision-240 clan-setting wire map confirmed from CLANSETTING_GET (clientscript opcode 74).
 *
 * Rank-title groups are read by clientscript 4302, the clan motif by 4441, and permission rows by
 * 4456. Values are encoded in the same bit/rank form consumed by those scripts. Settings whose
 *       cache meaning has not been established are deliberately not emitted.
 */
public object ClanClientSettings {
    public fun encode(clan: ClanSnapshot): List<ClanSettingsFull.ClanSetting> = buildList {
        addAll(encodeRankTitles(clan.titles))
        add(ClanSettingsFull.IntClanSetting(MOTIF_SETTING_ID, ClanMotifCodec.encode(clan.motif)))
        add(
            ClanSettingsFull.IntClanSetting(
                GUEST_REQUIREMENTS_SETTING_ID,
                encodeGuestRequirements(clan.guestRequirements),
            )
        )
        addAll(encodeBroadcastSettings(clan.broadcastSettings))
        for ((permission, settingId) in PERMISSION_SETTING_IDS) {
            add(
                ClanSettingsFull.IntClanSetting(
                    settingId,
                    encodePermissionRank(clan.permissionSet[permission]),
                )
            )
        }
    }

    private fun encodeRankTitles(titles: List<ClanRankTitle>): List<ClanSettingsFull.ClanSetting> {
        val bySlot = titles.associateBy { it.slot }
        return RANK_TITLE_SETTING_IDS.mapIndexed { group, settingId ->
            var packed = 0
            repeat(RANK_TITLES_PER_SETTING) { position ->
                val slot = group * RANK_TITLES_PER_SETTING + position
                val titleId = bySlot[slot]?.titleId ?: 0
                packed = packed or ((titleId and TEN_BIT_MASK) shl (position * TEN_BITS))
            }
            ClanSettingsFull.IntClanSetting(settingId, packed)
        }
    }

    /**
     * Clientscript 4456 stores a clan rank in the high-level settings map at twice its wire byte.
     */
    private fun encodePermissionRank(rank: Int): Int = rank * 2

    private fun encodeGuestRequirements(requirements: ClanGuestRequirements): Int =
        (requirements.minimumCombatLevel and 0x7F) or
            ((requirements.minimumTotalLevel and 0xFFF) shl 7) or
            ((requirements.minimumQuestPoints and 0x7FF) shl 19) or
            ((requirements.ironRequirement.storageId and 0x3) shl 30)

    private fun encodeBroadcastSettings(
        settings: ClanBroadcastSettings
    ): List<ClanSettingsFull.ClanSetting> = buildList {
        for ((key, settingId) in BROADCAST_RANK_SETTING_IDS) {
            add(
                ClanSettingsFull.IntClanSetting(
                    settingId,
                    encodePermissionRank(settings.minimumRanks.getValue(key)),
                )
            )
        }
        add(ClanSettingsFull.IntClanSetting(LOOT_VALUE_SETTING_ID, settings.minimumLootValue))
        add(ClanSettingsFull.IntClanSetting(PK_VALUE_SETTING_ID, settings.minimumPlayerKillValue))
        add(
            ClanSettingsFull.IntClanSetting(LOOT_KEY_VALUE_SETTING_ID, settings.minimumLootKeyValue)
        )
        add(
            ClanSettingsFull.IntClanSetting(
                COMBAT_ACHIEVEMENT_TIER_SETTING_ID,
                settings.minimumCombatAchievementTier.storageId,
            )
        )
        add(ClanSettingsFull.IntClanSetting(LEVEL_TEN_SETTING_ID, settings.levelEveryTenMinimum))
        add(ClanSettingsFull.IntClanSetting(LEVEL_FIVE_SETTING_ID, settings.levelEveryFiveMinimum))
        add(ClanSettingsFull.IntClanSetting(LEVEL_ONE_SETTING_ID, settings.levelEveryOneMinimum))
        add(
            ClanSettingsFull.IntClanSetting(
                POST_99_INTERVAL_SETTING_ID,
                settings.post99XpInterval / ClanBroadcastLimits.POST_99_XP_INTERVAL_UNIT,
            )
        )
        add(
            ClanSettingsFull.IntClanSetting(
                COLLECTION_INTERVAL_SETTING_ID,
                if (settings.collectionInterval == ClanCollectionBroadcastInterval.EverySlot) 0
                else settings.collectionInterval.slots,
            )
        )
        add(
            ClanSettingsFull.IntClanSetting(
                EVENT_NOTIFICATION_SETTING_ID,
                settings.eventStartingNotificationsEnabled.toInt(),
            )
        )
        add(
            ClanSettingsFull.IntClanSetting(
                MAIN_WORLD_ONLY_SETTING_ID,
                settings.mainGameWorldsOnly.toInt(),
            )
        )
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    private const val MOTIF_SETTING_ID = 11
    private const val GUEST_REQUIREMENTS_SETTING_ID = 26
    private const val LOOT_VALUE_SETTING_ID = 31
    private const val COMBAT_ACHIEVEMENT_TIER_SETTING_ID = 101
    private const val PK_VALUE_SETTING_ID = 65
    private const val LEVEL_TEN_SETTING_ID = 66
    private const val LEVEL_FIVE_SETTING_ID = 67
    private const val LEVEL_ONE_SETTING_ID = 68
    private const val POST_99_INTERVAL_SETTING_ID = 69
    private const val LOOT_KEY_VALUE_SETTING_ID = 98
    private const val EVENT_NOTIFICATION_SETTING_ID = 110
    private const val COLLECTION_INTERVAL_SETTING_ID = 111
    private const val MAIN_WORLD_ONLY_SETTING_ID = 112
    private const val TEN_BITS = 10
    private const val TEN_BIT_MASK = 0x3FF
    private const val RANK_TITLES_PER_SETTING = 3

    private val RANK_TITLE_SETTING_IDS = intArrayOf(1, 2, 3, 4, 5, 102, 103, 104)

    /** Explicit native permission-row order; never derive this mapping from enum ordinal. */
    private val PERMISSION_SETTING_IDS: List<Pair<ClanPermissionKey, Int>> =
        listOf(
            ClanPermissionKey.PermissionAccess to 12,
            ClanPermissionKey.EditPublicProfile to 13,
            ClanPermissionKey.AssignMemberRanks to 14,
            ClanPermissionKey.ExpelMembers to 15,
            ClanPermissionKey.EditRankTitles to 16,
            ClanPermissionKey.Recruitment to 17,
            ClanPermissionKey.EditMotif to 25,
            ClanPermissionKey.BanGuests to 18,
            ClanPermissionKey.OrganiseEvents to 21,
            ClanPermissionKey.InitiateClanWars to 22,
            ClanPermissionKey.CofferDeposit to 19,
            ClanPermissionKey.CofferWithdraw to 20,
            ClanPermissionKey.HallEnter to 23,
            ClanPermissionKey.HallEditSettings to 24,
            ClanPermissionKey.HallPartyChestDeposit to 94,
            ClanPermissionKey.HallPullLever to 95,
            ClanPermissionKey.HallPartyBalloonLoot to 96,
        )

    /** Exact switch map from revision-240 [proc,clan_permission_get] (clientscript 4456). */
    private val BROADCAST_RANK_SETTING_IDS: List<Pair<ClanBroadcastKey, Int>> =
        listOf(
            ClanBroadcastKey.Membership to 27,
            ClanBroadcastKey.QuestCompletion to 28,
            ClanBroadcastKey.LootOrPet to 29,
            ClanBroadcastKey.LevelUp to 30,
            ClanBroadcastKey.CombatAchievementTier to 32,
            ClanBroadcastKey.CombatAchievementTask to 100,
            ClanBroadcastKey.CollectionLog to 58,
            ClanBroadcastKey.PersonalBest to 59,
            ClanBroadcastKey.PlayerKill to 60,
            ClanBroadcastKey.PlayerDefeat to 61,
            ClanBroadcastKey.AchievementDiary to 62,
            ClanBroadcastKey.HardcoreStatus to 63,
            ClanBroadcastKey.Post99Xp to 64,
            ClanBroadcastKey.LootKey to 97,
            ClanBroadcastKey.LeagueShared to 105,
            ClanBroadcastKey.LeagueRelicTier to 106,
            ClanBroadcastKey.LeagueAreaUnlock to 107,
            ClanBroadcastKey.LeagueRank to 108,
            ClanBroadcastKey.CombatMastery to 109,
        )
}

/** Revision-240 clan variables read with GET_VARCLAN rather than CLANSETTING_GET. */
public object ClanClientVars {
    public const val COFFER_BALANCE_ID: Int = 3

    public fun encodeCofferBalance(balance: Long): VarClan =
        VarClan(
            COFFER_BALANCE_ID,
            VarClan.VarClanIntData(balance.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()),
        )
}
