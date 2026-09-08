package org.rsmod.api.net.social

public object SocialNameNormalizer {
    public fun normalize(name: String): String =
        name
            .replace('_', ' ')
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .lowercase()

    public fun channelName(name: String): String =
        name.replace('_', ' ').filter { it.isLetterOrDigit() || it == ' ' }.trim().take(12)
}

public object SocialChatText {
    public const val MAX_LENGTH: Int = 80

    public fun normalize(raw: String): String? {
        val text =
            raw.trim().filter { character ->
                character == ' ' || (!character.isISOControl() && character != '\u0000')
            }
        return text.take(MAX_LENGTH).takeIf(String::isNotBlank)
    }
}

public object FriendChatCapacity {
    public const val MAX_MEMBERS: Int = 500

    public fun displacementCandidate(
        ranksByCharacterId: Map<Int, Int>,
        ownerCharacterId: Int,
        requesterRank: Int,
    ): Int? =
        ranksByCharacterId
            .asSequence()
            .filter { (characterId) -> characterId != ownerCharacterId }
            .minWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            ?.takeIf { it.value < requesterRank }
            ?.key
}

public object FriendChatKickRules {
    public fun isActive(expiresAtMillis: Long?, nowMillis: Long): Boolean =
        expiresAtMillis != null && expiresAtMillis > nowMillis

    public fun clearWhenEmpty(memberCount: Int): Boolean = memberCount == 0
}

public enum class FriendChatPermission(public val wireId: Int, public val label: String) {
    Anyone(-1, "Anyone"),
    Friend(0, "Any friends"),
    Recruit(1, "Recruit+"),
    Corporal(2, "Corporal+"),
    Sergeant(3, "Sergeant+"),
    Lieutenant(4, "Lieutenant+"),
    Captain(5, "Captain+"),
    General(6, "General+"),
    Owner(7, "Only me");

    public fun allows(rank: Int): Boolean = rank >= wireId

    public companion object {
        public fun fromWire(wireId: Int): FriendChatPermission? =
            entries.firstOrNull { it.wireId == wireId }
    }
}

public data class SocialIdentity(
    val characterId: Int,
    val displayName: String,
    val previousDisplayName: String,
)

public data class SocialFriend(val identity: SocialIdentity, val rank: Int)

public data class SocialRuntimeSnapshot(
    val friends: List<SocialFriend>,
    val ignoredCharacterIds: Set<Int>,
)

public data class FriendChatSettings(
    val ownerCharacterId: Int,
    val channelName: String,
    val enabled: Boolean,
    val joinPermission: FriendChatPermission,
    val talkPermission: FriendChatPermission,
    val kickPermission: FriendChatPermission,
)

public data class FriendChatJoinSnapshot(
    val owner: SocialIdentity,
    val settings: FriendChatSettings,
    val requesterRank: Int,
    val ownerIgnoresRequester: Boolean,
)

public data class FriendChatOnlineMember(
    val characterId: Int,
    val displayName: String,
    val worldId: Int,
    val rank: Int,
)

public data class FriendChatRankUpdate(val target: SocialIdentity, val rank: Int)
