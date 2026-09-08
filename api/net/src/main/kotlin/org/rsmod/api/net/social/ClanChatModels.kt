package org.rsmod.api.net.social

/** Revision-240 clan-channel rank bytes in strict hierarchy order. */
public object ClanRanks {
    public const val GUEST: Int = -1
    public const val MEMBER: Int = 0
    public const val ADMINISTRATOR: Int = 100
    public const val DEPUTY_OWNER: Int = 125
    public const val OWNER: Int = 126
    public val LOWER_CUSTOM_RANKS: List<Int> =
        listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99)
    public val UPPER_CUSTOM_RANKS: List<Int> = listOf(101, 102, 103, 104, 105, 110, 115, 120, 124)
    public val CUSTOM_RANKS: List<Int> = LOWER_CUSTOM_RANKS + UPPER_CUSTOM_RANKS
    public val HIERARCHY: List<Int> =
        listOf(GUEST) +
            LOWER_CUSTOM_RANKS +
            listOf(ADMINISTRATOR) +
            UPPER_CUSTOM_RANKS +
            listOf(DEPUTY_OWNER, OWNER)
    public const val CUSTOM_COUNT: Int = 24

    public fun isFixed(rank: Int): Boolean =
        rank == GUEST || rank == ADMINISTRATOR || rank == DEPUTY_OWNER || rank == OWNER

    public fun hierarchyIndex(rank: Int): Int? = HIERARCHY.indexOf(rank).takeIf { it >= 0 }

    public fun atLeast(rank: Int, minimumRank: Int): Boolean {
        val actual = hierarchyIndex(rank) ?: return false
        val minimum = hierarchyIndex(minimumRank) ?: return false
        return actual >= minimum
    }

    public fun higherThan(rank: Int, otherRank: Int): Boolean {
        val actual = hierarchyIndex(rank) ?: return false
        val other = hierarchyIndex(otherRank) ?: return false
        return actual > other
    }

    public fun canActOn(actorRank: Int, targetRank: Int): Boolean =
        atLeast(actorRank, ADMINISTRATOR) &&
            higherThan(actorRank, targetRank) &&
            targetRank != OWNER

    public fun isCustom(rank: Int): Boolean = rank in CUSTOM_RANKS

    public fun isValidMemberRank(rank: Int): Boolean =
        isCustom(rank) || rank == ADMINISTRATOR || rank == DEPUTY_OWNER || rank == OWNER

    public fun isValidThreshold(rank: Int, allowGuest: Boolean = false): Boolean =
        (allowGuest && rank == GUEST) || isValidMemberRank(rank)

    public fun validAssignable(rank: Int): Boolean = isCustom(rank) || rank == ADMINISTRATOR

    public fun titleSlot(rank: Int): Int? = CUSTOM_RANKS.indexOf(rank).takeIf { it >= 0 }

    public fun rankForTitleSlot(slot: Int): Int? = CUSTOM_RANKS.getOrNull(slot)
}

public object ClanPermissions {
    public fun canMemberSpeak(rank: Int, muted: Boolean, minimumRank: Int): Boolean =
        !muted && ClanRanks.atLeast(rank, minimumRank)

    public fun canGuestListen(allowGuests: Boolean): Boolean = allowGuests

    public fun canGuestSpeak(allowGuests: Boolean, guestsCanTalk: Boolean): Boolean =
        allowGuests && guestsCanTalk

    public fun canKickGuest(actorRank: Int, minimumRank: Int): Boolean =
        ClanRanks.atLeast(actorRank, minimumRank)

    public fun canBanGuest(actorRank: Int, minimumRank: Int): Boolean =
        ClanRanks.atLeast(actorRank, minimumRank)

    public fun canExpelMember(actorRank: Int, targetRank: Int, minimumRank: Int): Boolean =
        ClanRanks.atLeast(actorRank, minimumRank) && ClanRanks.canActOn(actorRank, targetRank)

    public fun canMuteMember(actorRank: Int, targetRank: Int): Boolean =
        ClanRanks.atLeast(actorRank, ClanRanks.ADMINISTRATOR) &&
            ClanRanks.canActOn(actorRank, targetRank)

    public fun canEditPermissions(actorRank: Int, minimumRank: Int): Boolean =
        ClanRanks.atLeast(actorRank, minimumRank)
}

public enum class SocialChannelKind(public val wireId: Int) {
    FriendChat(0),
    OwnClan(1),
    GuestClan(2),
}

public data class ClanMemberSnapshot(
    val characterId: Int,
    val displayName: String,
    val previousDisplayName: String,
    val rank: Int,
    val titleSlot: Int,
    val muted: Boolean,
    val joinedRuneDay: Int,
    val worldId: Int,
    val lastSeenEpochMillis: Long? = null,
    val lastSeenVisible: Boolean = true,
    val combatLevel: Int? = null,
    val totalLevel: Int? = null,
)

public data class ClanBanSnapshot(val characterId: Int, val displayName: String)

public data class ClanGuestPresence(
    val characterId: Int,
    val displayName: String,
    val worldId: Int,
)

public data class ClanRankTitle(val slot: Int, val titleId: Int, val titleName: String)

public data class ClanSnapshot(
    val clanId: Long,
    val displayName: String,
    val ownerCharacterId: Int,
    val creationRuneDay: Int,
    val homeWorld: Int,
    val allowGuests: Boolean,
    val guestsCanTalk: Boolean,
    val talkRank: Int,
    val kickRank: Int,
    val banRank: Int,
    val expelRank: Int,
    val assignRank: Int,
    val editPermissionsRank: Int,
    val stateVersion: Long,
    val members: List<ClanMemberSnapshot>,
    val bans: List<ClanBanSnapshot>,
    val titles: List<ClanRankTitle>,
    val onlineGuests: List<ClanGuestPresence> = emptyList(),
    val interests: Set<ClanInterest> = emptySet(),
    val permissionSet: ClanPermissionSet = ClanPermissionSet.defaults(),
    val motif: ClanMotif = ClanMotif(ClanMotifColour.Black, ClanMotifColour.Black, null, null),
    val guestRequirements: ClanGuestRequirements = ClanGuestRequirements(),
    val hallSettings: ClanHallSettings = ClanHallSettings(),
    val cofferBalance: Long = 0,
    val broadcastSettings: ClanBroadcastSettings = ClanBroadcastSettings.defaults(),
) {
    public fun member(characterId: Int): ClanMemberSnapshot? =
        members.firstOrNull { it.characterId == characterId }
}

public data class ClanLoginSnapshot(val ownClan: ClanSnapshot?, val guestClan: ClanSnapshot?)

public data class ClanGuestJoinResult(
    val clan: ClanSnapshot?,
    val banned: Boolean,
    val kickedUntilEpochMillis: Long?,
)

public sealed interface ChatRoute {
    public data class Public(val message: String) : ChatRoute

    public data class FriendChat(val message: String) : ChatRoute

    public data class OwnClan(val message: String) : ChatRoute

    public data class GuestClan(val message: String) : ChatRoute

    public data object Empty : ChatRoute
}

/** Orders the longest prefixes first so clan aliases cannot fall through to Friends Chat. */
public object ChatPrefixRouter {
    public fun route(raw: String): ChatRoute {
        val text = raw.trim()
        if (text.isEmpty()) return ChatRoute.Empty
        val routed =
            when {
                text.startsWith("///") -> ChatRoute.GuestClan(text.drop(3).trimStart())
                text.startsWith("/gc ", ignoreCase = true) ->
                    ChatRoute.GuestClan(text.drop(4).trimStart())
                text.equals("/gc", ignoreCase = true) -> ChatRoute.Empty
                text.startsWith("//") -> ChatRoute.OwnClan(text.drop(2).trimStart())
                text.startsWith("/c ", ignoreCase = true) ->
                    ChatRoute.OwnClan(text.drop(3).trimStart())
                text.equals("/c", ignoreCase = true) -> ChatRoute.Empty
                text.startsWith("/f ", ignoreCase = true) ->
                    ChatRoute.FriendChat(text.drop(3).trimStart())
                text.equals("/f", ignoreCase = true) -> ChatRoute.Empty
                text.startsWith('/') -> ChatRoute.FriendChat(text.drop(1).trimStart())
                else -> ChatRoute.Public(text)
            }
        return when (routed) {
            is ChatRoute.FriendChat -> if (routed.message.isBlank()) ChatRoute.Empty else routed
            is ChatRoute.OwnClan -> if (routed.message.isBlank()) ChatRoute.Empty else routed
            is ChatRoute.GuestClan -> if (routed.message.isBlank()) ChatRoute.Empty else routed
            else -> routed
        }
    }
}
