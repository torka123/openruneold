package org.rsmod.api.net.social

public object ClanDomainLimits {
    public const val CUSTOM_RANK_SLOTS: Int = 24
    public const val INTERESTS_PER_CLAN: Int = 3
    public const val MAX_QUEST_POINTS_REQUIREMENT: Int = 32_767
    public const val MAX_EVENT_DAYS_IN_ADVANCE: Int = 30
    public const val GAME_TICKS_PER_MINUTE: Long = 100L
    public const val GAME_TICKS_PER_DAY: Long = 24L * 60L * GAME_TICKS_PER_MINUTE
    public const val MAX_EVENT_DURATION_TICKS: Long = 30L * GAME_TICKS_PER_DAY
    public const val MAX_CLAN_HALL_PLAYERS: Int = 2_000
    public const val MAX_PARTY_CHEST_UNIQUE_ITEMS: Int = 216
    public const val PARTY_CHEST_LEVER_COST: Int = 1_000
    public const val MAX_COFFER_COINS: Long = 50_000_000L
    public const val OWNER_RESIGNATION_DELAY_DAYS: Int = 7
    public const val OWNER_INACTIVITY_DAYS: Int = 90
    public const val CLAN_HALL_KEY_LENGTH: Int = 32
}

public enum class ClanPermissionKey(
    public val storageKey: String,
    public val minimumAllowedRank: Int,
    public val guestThresholdAllowed: Boolean = false,
    public val defaultRank: Int,
) {
    PermissionAccess(
        "permission_access",
        ClanRanks.ADMINISTRATOR,
        defaultRank = ClanRanks.DEPUTY_OWNER,
    ),
    EditRankTitles(
        "edit_rank_titles",
        ClanRanks.ADMINISTRATOR,
        defaultRank = ClanRanks.ADMINISTRATOR,
    ),
    AssignMemberRanks(
        "assign_member_ranks",
        ClanRanks.ADMINISTRATOR,
        defaultRank = ClanRanks.ADMINISTRATOR,
    ),
    Recruitment("recruitment", ClanRanks.MEMBER, defaultRank = ClanRanks.ADMINISTRATOR),
    ExpelMembers("expel_members", ClanRanks.ADMINISTRATOR, defaultRank = ClanRanks.DEPUTY_OWNER),
    EditPublicProfile(
        "edit_public_profile",
        ClanRanks.ADMINISTRATOR,
        defaultRank = ClanRanks.DEPUTY_OWNER,
    ),
    EditMotif("edit_motif", ClanRanks.ADMINISTRATOR, defaultRank = ClanRanks.ADMINISTRATOR),
    TalkInChat(
        "talk_in_chat",
        ClanRanks.GUEST,
        guestThresholdAllowed = true,
        defaultRank = ClanRanks.GUEST,
    ),
    KickGuests("kick_guests", ClanRanks.MEMBER, defaultRank = ClanRanks.ADMINISTRATOR),
    BanGuests("ban_guests", ClanRanks.ADMINISTRATOR, defaultRank = ClanRanks.ADMINISTRATOR),
    OrganiseEvents("organise_events", ClanRanks.MEMBER, defaultRank = ClanRanks.ADMINISTRATOR),
    InitiateClanWars("initiate_clan_wars", ClanRanks.MEMBER, defaultRank = ClanRanks.ADMINISTRATOR),
    CofferDeposit("coffer_deposit", ClanRanks.MEMBER, defaultRank = ClanRanks.MEMBER),
    CofferWithdraw("coffer_withdraw", ClanRanks.MEMBER, defaultRank = ClanRanks.ADMINISTRATOR),
    HallEnter(
        "hall_enter",
        ClanRanks.GUEST,
        guestThresholdAllowed = true,
        defaultRank = ClanRanks.GUEST,
    ),
    HallEditSettings("hall_edit_settings", ClanRanks.MEMBER, defaultRank = ClanRanks.ADMINISTRATOR),
    HallPartyChestDeposit(
        "hall_party_chest_deposit",
        ClanRanks.GUEST,
        guestThresholdAllowed = true,
        defaultRank = ClanRanks.MEMBER,
    ),
    HallPullLever(
        "hall_pull_lever",
        ClanRanks.GUEST,
        guestThresholdAllowed = true,
        defaultRank = ClanRanks.ADMINISTRATOR,
    ),
    HallPartyBalloonLoot(
        "hall_party_balloon_loot",
        ClanRanks.GUEST,
        guestThresholdAllowed = true,
        defaultRank = ClanRanks.MEMBER,
    );

    public companion object {
        private val byStorageKey: Map<String, ClanPermissionKey> =
            entries.associateBy { it.storageKey }

        public fun fromStorageKey(key: String): ClanPermissionKey? = byStorageKey[key]
    }
}

public data class ClanPermissionSet(val minimumRanks: Map<ClanPermissionKey, Int>) {
    public operator fun get(key: ClanPermissionKey): Int =
        requireNotNull(minimumRanks[key]) { "Missing clan permission ${key.storageKey}." }

    public fun validationError(): String? {
        val missing = ClanPermissionKey.entries.toSet() - minimumRanks.keys
        if (missing.isNotEmpty()) {
            return "Missing clan permissions: ${missing.joinToString { it.storageKey }}."
        }
        val unknown = minimumRanks.keys - ClanPermissionKey.entries.toSet()
        if (unknown.isNotEmpty()) return "The clan permission set contains unknown keys."
        for ((key, rank) in minimumRanks) {
            if (!ClanRanks.isValidThreshold(rank, key.guestThresholdAllowed)) {
                return "The ${key.storageKey} rank is not valid."
            }
            if (!ClanRanks.atLeast(rank, key.minimumAllowedRank)) {
                return "The ${key.storageKey} rank is below its minimum."
            }
        }
        return null
    }

    public companion object {
        public fun defaults(): ClanPermissionSet =
            ClanPermissionSet(ClanPermissionKey.entries.associateWith { it.defaultRank })
    }
}

public enum class ClanInterest(public val storageId: Int) {
    FreeToPlay(0),
    NewbieFriendly(1),
    Socialising(2),
    Diversity(3),
    Pure(4),
    Pvp(5),
    BossHunting(6),
    Raiding(7),
    Slayer(8),
    Skilling(9),
    RolePlaying(10),
    IronPlayers(11),
    UltimateIronPlayers(12),
    HardcoreIronPlayers(13),
    CastleWars(14),
    SoulWars(15),
}

/** Revision-240 controlled paired palettes from enum 3819 and structs 2935 through 2942. */
public enum class ClanMotifColour(
    public val storageId: Int,
    public val primaryRgb: Int,
    public val secondaryRgb: Int,
) {
    Black(0, 0x282828, 0x202020),
    Orange(1, 0xD09038, 0xB57B24),
    Blue(2, 0x303068, 0x2D2860),
    Red(3, 0x981010, 0x781010),
    Green(4, 0x789008, 0x607800),
    Yellow(5, 0xD0C848, 0xB8B028),
    LightBlue(6, 0x98C8F8, 0x98A8B8),
    Purple(7, 0x9868B0, 0x805098),
}

public enum class ClanMotifIcon(public val storageId: Int) {
    DragonMedHelm(0),
    Compass(1),
    AxeAndPickaxe(2),
    PartyHat(3),
    Bones(4),
    WizardHat(5),
    HolyStarOfSaradomin(6),
    Fish(7),
    Tragedy(8),
    Heart(9),
    RobinHat(10),
    UnholySymbolOfZamorak(11),
    RakeAndSpade(12),
    Comedy(13),
    Swords(14),
    Arrows(15),
    IconOfGuthix(16),
    Hunter(17),
    Bob(18),
    Mask(19),
    Defence(20),
    Cabbage(21),
    Rose(22),
    Ring(23),
    Skull(24),
    Iron(25),
    UltimateIron(26),
}

public data class ClanMotif(
    val primaryColour: ClanMotifColour,
    val secondaryColour: ClanMotifColour,
    val primarySymbol: ClanMotifIcon?,
    val secondarySymbol: ClanMotifIcon?,
)

/** Native clan setting 11 / editing varp 3023 motif representation. */
public object ClanMotifCodec {
    private const val TEN_BIT_MASK: Int = 0x3FF

    public fun encode(motif: ClanMotif): Int =
        encodeSymbol(motif.primarySymbol) or
            (encodeSymbol(motif.secondarySymbol) shl 10) or
            (motif.primaryColour.storageId shl 20)

    public fun decode(packed: Int): ClanMotif? {
        if (packed ushr 30 != 0) return null
        val palette = colour((packed ushr 20) and TEN_BIT_MASK) ?: return null
        val primarySymbol = decodeSymbol(packed and TEN_BIT_MASK) ?: return null
        val secondarySymbol = decodeSymbol((packed ushr 10) and TEN_BIT_MASK) ?: return null
        return ClanMotif(palette, palette, primarySymbol.value, secondarySymbol.value)
    }

    private fun encodeSymbol(symbol: ClanMotifIcon?): Int = (symbol?.storageId ?: -1) + 1

    private fun decodeSymbol(encoded: Int): DecodedSymbol? =
        when (encoded) {
            0 -> DecodedSymbol(null)
            in 1..ClanMotifIcon.entries.size ->
                DecodedSymbol(ClanMotifIcon.entries.first { it.storageId == encoded - 1 })
            else -> null
        }

    private fun colour(storageId: Int): ClanMotifColour? =
        ClanMotifColour.entries.firstOrNull { it.storageId == storageId }

    private data class DecodedSymbol(val value: ClanMotifIcon?)
}

public enum class ClanGuestIronRequirement(public val storageId: Int) {
    AnyAccount(0),
    AnyIron(1),
    UltimateIronOnly(2),
    HardcoreIronOnly(3),
}

public data class ClanGuestCandidate(
    val combatLevel: Int,
    val totalLevel: Int,
    val questPoints: Int,
    val isIron: Boolean,
    val isUltimateIron: Boolean,
    val isHardcoreIron: Boolean,
)

public data class ClanGuestRequirements(
    val minimumCombatLevel: Int = 0,
    val minimumTotalLevel: Int = 0,
    val minimumQuestPoints: Int = 0,
    val ironRequirement: ClanGuestIronRequirement = ClanGuestIronRequirement.AnyAccount,
) {
    public fun validationError(): String? =
        when {
            minimumCombatLevel !in 0..ClanLimits.MAX_RECRUITMENT_COMBAT_LEVEL ->
                "The guest combat requirement is not valid."
            minimumTotalLevel !in 0..ClanLimits.MAX_RECRUITMENT_TOTAL_LEVEL ->
                "The guest total-level requirement is not valid."
            minimumQuestPoints !in 0..ClanDomainLimits.MAX_QUEST_POINTS_REQUIREMENT ->
                "The guest quest-point requirement is not valid."
            else -> null
        }

    public fun accepts(candidate: ClanGuestCandidate): Boolean {
        if (validationError() != null) return false
        if (
            candidate.combatLevel < minimumCombatLevel ||
                candidate.totalLevel < minimumTotalLevel ||
                candidate.questPoints < minimumQuestPoints
        ) {
            return false
        }
        return when (ironRequirement) {
            ClanGuestIronRequirement.AnyAccount -> true
            ClanGuestIronRequirement.AnyIron -> candidate.isIron
            ClanGuestIronRequirement.UltimateIronOnly -> candidate.isUltimateIron
            ClanGuestIronRequirement.HardcoreIronOnly -> candidate.isHardcoreIron
        }
    }
}

public data class ClanHallSettings(
    val homeWorldOnly: Boolean = false,
    val locked: Boolean = false,
    val playerCapacity: Int = ClanDomainLimits.MAX_CLAN_HALL_PLAYERS,
    val themeKey: String = "default",
    val identityKey: String = "standard",
    val editStateVersion: Long = 0,
    val editSessionCharacterId: Int? = null,
    val editSessionStartedAtEpochMillis: Long? = null,
    val editSessionExpiresAtEpochMillis: Long? = null,
) {
    public fun validationError(): String? {
        if (playerCapacity !in 1..ClanDomainLimits.MAX_CLAN_HALL_PLAYERS) {
            return "The Clan Hall capacity must be between 1 and 2000."
        }
        if (!validHallKey(themeKey) || !validHallKey(identityKey)) {
            return "The Clan Hall theme or identity is not valid."
        }
        if (editStateVersion < 0) return "The Clan Hall edit version is not valid."
        val editParts =
            listOf(
                editSessionCharacterId,
                editSessionStartedAtEpochMillis,
                editSessionExpiresAtEpochMillis,
            )
        if (editParts.count { it != null } !in setOf(0, 3)) {
            return "The Clan Hall edit session is incomplete."
        }
        if (
            editSessionStartedAtEpochMillis != null &&
                editSessionExpiresAtEpochMillis!! <= editSessionStartedAtEpochMillis
        ) {
            return "The Clan Hall edit session expiry is not valid."
        }
        return null
    }

    private fun validHallKey(key: String): Boolean =
        key.length in 1..ClanDomainLimits.CLAN_HALL_KEY_LENGTH &&
            key.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
}

/** Persisted lease rules for the single authoritative Clan Hall editor. */
public object ClanHallEditSessionRules {
    public const val LEASE_MILLIS: Long = 30L * 60L * 1_000L

    public fun activeEditor(settings: ClanHallSettings, nowEpochMillis: Long): Int? {
        val editor = settings.editSessionCharacterId ?: return null
        val startedAt = settings.editSessionStartedAtEpochMillis ?: return null
        val expiresAt = settings.editSessionExpiresAtEpochMillis ?: return null
        return editor.takeIf { startedAt <= nowEpochMillis && expiresAt > nowEpochMillis }
    }

    public fun acquisitionDenial(
        settings: ClanHallSettings,
        actorCharacterId: Int,
        nowEpochMillis: Long,
    ): String? {
        val active = activeEditor(settings, nowEpochMillis) ?: return null
        return if (active == actorCharacterId) null
        else "The Clan Hall is currently being edited by another clan member."
    }

    public fun ownsLease(
        settings: ClanHallSettings,
        actorCharacterId: Int,
        nowEpochMillis: Long,
    ): Boolean = activeEditor(settings, nowEpochMillis) == actorCharacterId
}

public enum class ClanCofferTransactionType(public val storageId: Int) {
    Deposit(0),
    Withdrawal(1),
}

public data class ClanCofferTransaction(
    val requestId: String,
    val characterId: Int,
    val type: ClanCofferTransactionType,
    val amount: Long,
    val balanceAfter: Long,
    val occurredAtEpochMillis: Long,
)

public object ClanCofferRules {
    public fun validBalance(coins: Long): Boolean = coins in 0..ClanDomainLimits.MAX_COFFER_COINS

    public fun canApply(balance: Long, delta: Long): Boolean {
        if (!validBalance(balance)) return false
        val result = balance + delta
        if ((delta > 0 && result < balance) || (delta < 0 && result > balance)) return false
        return validBalance(result)
    }
}

public enum class ClanFounderInvitationState(public val storageId: Int) {
    Pending(0),
    Accepted(1),
    Declined(2),
}

public data class ClanFounderInvitation(
    val characterId: Int,
    val displayName: String,
    val founderOrder: Int,
    val state: ClanFounderInvitationState,
    val authoritativePlaytimeTicks: Int,
)

public data class ClanCreationDraft(
    val owner: ClanFounder,
    val requestedName: String,
    val homeWorld: Int,
    val invitations: List<ClanFounderInvitation>,
)

public object ClanCreationDraftRules {
    public fun validationError(draft: ClanCreationDraft): String? {
        val maximumInvitations = ClanLimits.MAX_FOUNDING_MEMBER_COUNT - 1
        if (draft.invitations.size > maximumInvitations) {
            return "A clan draft may have at most four founder invitations."
        }
        if (draft.invitations.any { it.characterId == draft.owner.characterId }) {
            return "The clan owner cannot also be a founder invitation."
        }
        if (draft.invitations.map { it.characterId }.toSet().size != draft.invitations.size) {
            return "Every founder invitation must be unique."
        }
        if (draft.invitations.any { it.founderOrder !in 1..maximumInvitations }) {
            return "Founder invitation positions must be 1 through 4."
        }
        if (draft.invitations.map { it.founderOrder }.toSet().size != draft.invitations.size) {
            return "Every founder invitation position must be unique."
        }
        if (draft.invitations.any { it.state != ClanFounderInvitationState.Accepted }) {
            return "Every founder must accept before the clan is created."
        }
        val founders =
            listOf(draft.owner) +
                draft.invitations.map {
                    ClanFounder(it.characterId, it.displayName, it.authoritativePlaytimeTicks)
                }
        return ClanCreationRules.validate(
            ClanCreationRequest(
                draft.requestedName,
                draft.owner.characterId,
                founders,
                draft.homeWorld,
            )
        )
    }
}

public data class ClanMemberNotificationPreferences(
    val broadcastsEnabled: Boolean = true,
    val eventRemindersEnabled: Boolean = true,
    val recruiterNotificationsEnabled: Boolean = true,
)

public enum class ClanRecruiterNotificationKind(public val storageId: Int) {
    ApplicationSubmitted(0),
    ApplicationWithdrawn(1),
}

public data class ClanRecruiterNotification(
    val id: Long,
    val clanId: Long,
    val recipientCharacterId: Int,
    val applicantCharacterId: Int?,
    val kind: ClanRecruiterNotificationKind,
    val createdAtEpochMillis: Long,
    val deliveredAtEpochMillis: Long?,
)

public data class ClanOwnershipMember(val characterId: Int, val rank: Int)

public data class ClanSuccessionCandidate(
    val characterId: Int,
    val rank: Int,
    val joinedAtEpochMillis: Long,
    val lastLoginAtEpochMillis: Long,
)

public object ClanOwnershipRules {
    public const val DAY_MILLIS: Long = 86_400_000L

    public fun validationError(
        ownerCharacterId: Int,
        members: Collection<ClanOwnershipMember>,
    ): String? {
        val owners = members.filter { it.rank == ClanRanks.OWNER }
        if (owners.size != 1) return "A live clan must have exactly one Owner."
        if (owners.single().characterId != ownerCharacterId) {
            return "The clan owner must match the Owner-ranked member."
        }
        return null
    }

    public fun selectSuccessor(
        candidates: Collection<ClanSuccessionCandidate>,
        nowEpochMillis: Long,
    ): ClanSuccessionCandidate? {
        val activeAfter = nowEpochMillis - ClanDomainLimits.OWNER_INACTIVITY_DAYS * DAY_MILLIS
        return candidates
            .asSequence()
            .filter {
                ClanRanks.atLeast(it.rank, ClanRanks.ADMINISTRATOR) && it.rank != ClanRanks.OWNER
            }
            .filter { it.lastLoginAtEpochMillis >= activeAfter }
            .sortedWith(
                compareByDescending<ClanSuccessionCandidate> {
                        checkNotNull(ClanRanks.hierarchyIndex(it.rank))
                    }
                    .thenBy { it.joinedAtEpochMillis }
                    .thenBy { it.characterId }
            )
            .firstOrNull()
    }
}
