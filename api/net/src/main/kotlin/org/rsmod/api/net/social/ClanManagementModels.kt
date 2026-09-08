package org.rsmod.api.net.social

public object ClanLimits {
    public const val MIN_FOUNDING_MEMBER_COUNT: Int = 1
    public const val FOUNDING_MEMBER_COUNT: Int = 5
    public const val MAX_FOUNDING_MEMBER_COUNT: Int = 5
    public const val MINIMUM_FOUNDER_PLAYTIME_TICKS: Int = 150_000
    public const val MAX_MEMBERS: Int = 500
    public const val MAX_BANS: Int = 100
    public const val MAX_NAME_LENGTH: Int = 12
    public const val MAX_DESCRIPTION_LENGTH: Int = 120
    public const val MAX_RECRUITMENT_COMBAT_LEVEL: Int = 126
    public const val MAX_RECRUITMENT_TOTAL_LEVEL: Int = 2_500
}

public data class ClanFounder(
    val characterId: Int,
    val displayName: String,
    val playtimeTicks: Int,
)

public data class ClanCreationRequest(
    val requestedName: String,
    val ownerCharacterId: Int,
    val founders: List<ClanFounder>,
    val homeWorld: Int,
)

public data class ClanCreationDraftView(
    val requestedName: String,
    val owner: ClanFounder,
    val acceptedFounders: List<ClanFounder>,
    val pendingFounders: List<ClanFounder>,
)

public data class ClanDirectoryEntry(
    val clanId: Long,
    val displayName: String,
    val ownerDisplayName: String,
    val memberCount: Int,
    val recruitmentOpen: Boolean,
    val description: String,
)

public data class ClanProfile(
    val clanId: Long,
    val displayName: String,
    val ownerDisplayName: String,
    val memberCount: Int,
    val recruitmentOpen: Boolean,
    val applicationsOpen: Boolean,
    val minimumCombatLevel: Int,
    val minimumTotalLevel: Int,
    val description: String,
    val members: List<ClanMemberSnapshot>,
    val creationRuneDay: Int = 0,
    val homeWorld: Int = 0,
    val allowGuests: Boolean = true,
    val guestsCanTalk: Boolean = true,
    val interests: Set<ClanInterest> = emptySet(),
    val permissionSet: ClanPermissionSet = ClanPermissionSet.defaults(),
    val motif: ClanMotif = ClanMotif(ClanMotifColour.Black, ClanMotifColour.Black, null, null),
    val guestRequirements: ClanGuestRequirements = ClanGuestRequirements(),
    val hallSettings: ClanHallSettings = ClanHallSettings(),
    val cofferBalance: Long = 0,
    val broadcastSettings: ClanBroadcastSettings = ClanBroadcastSettings.defaults(),
)

public data class ClanApplication(
    val characterId: Int,
    val displayName: String,
    val message: String,
    val combatLevel: Int? = null,
    val totalLevel: Int? = null,
    val questPoints: Int? = null,
    val isIron: Boolean? = null,
    val isUltimateIron: Boolean? = null,
    val isHardcoreIron: Boolean? = null,
    val skillLevels: List<Int?> = List(24) { null },
)

public data class ClanInvite(
    val clanId: Long,
    val clanName: String,
    val inviterDisplayName: String,
)

public sealed interface ClanManagementResult<out T> {
    public data class Success<T>(val value: T) : ClanManagementResult<T>

    public data class Denied(val message: String) : ClanManagementResult<Nothing>
}

public object ClanCreationRules {
    public fun validate(request: ClanCreationRequest): String? {
        val display =
            request.requestedName
                .replace('_', ' ')
                .filter { it.isLetterOrDigit() || it == ' ' }
                .trim()
        if (display.isBlank()) return "Clan names must contain letters or numbers."
        if (display.length > ClanLimits.MAX_NAME_LENGTH) {
            return "Clan names may have at most 12 characters."
        }
        if (
            request.founders.size !in
                ClanLimits.MIN_FOUNDING_MEMBER_COUNT..ClanLimits.MAX_FOUNDING_MEMBER_COUNT
        ) {
            return "A clan requires between one and five founding players."
        }
        if (request.founders.map { it.characterId }.toSet().size != request.founders.size) {
            return "Every founding player must be unique."
        }
        if (request.founders.none { it.characterId == request.ownerCharacterId }) {
            return "The clan owner must be one of the founders."
        }
        if (request.founders.any { it.playtimeTicks < ClanLimits.MINIMUM_FOUNDER_PLAYTIME_TICKS }) {
            return "Every founder needs at least 25 hours of playtime."
        }
        return null
    }
}
