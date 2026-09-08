package org.rsmod.content.minigames.clanhall

import org.rsmod.api.net.social.ClanHallEditSessionRules
import org.rsmod.api.net.social.ClanHallSettings
import org.rsmod.api.net.social.ClanPermissionKey
import org.rsmod.api.net.social.ClanPermissionSet
import org.rsmod.api.net.social.ClanRanks

internal enum class ClanHallRole {
    Member,
    Guest,
}

internal data class ClanHallEntryContext(
    val actorCharacterId: Int,
    val nowEpochMillis: Long,
    val role: ClanHallRole,
    val rank: Int,
    val currentWorld: Int,
    val homeWorld: Int,
    val occupants: Int,
    val rankedOccupants: Int,
    val recruitmentOpen: Boolean,
    val applicationsOpen: Boolean,
    val settings: ClanHallSettings,
    val permissions: ClanPermissionSet,
)

internal object ClanHallRules {
    fun entryDenial(context: ClanHallEntryContext): String? {
        val settingsError = context.settings.validationError()
        if (settingsError != null) return settingsError
        val permissionError = context.permissions.validationError()
        if (permissionError != null) return permissionError
        if (context.settings.homeWorldOnly && context.currentWorld != context.homeWorld) {
            return "This Clan Hall may only be entered on the clan's home world."
        }
        if (context.settings.locked && context.role == ClanHallRole.Guest) {
            return "This Clan Hall is currently locked to clan members."
        }
        if (context.recruitmentOpen || context.applicationsOpen) {
            return "Close clan recruitment and applications before entering the Clan Hall."
        }
        if (context.role == ClanHallRole.Guest && context.rankedOccupants == 0) {
            return "A ranked clan member must be inside before guests can enter the Clan Hall."
        }
        val editor = ClanHallEditSessionRules.activeEditor(context.settings, context.nowEpochMillis)
        if (editor != null && editor != context.actorCharacterId) {
            return "The Clan Hall is currently being edited."
        }
        if (context.occupants >= context.settings.playerCapacity) {
            return "This Clan Hall is full."
        }
        val required = context.permissions[ClanPermissionKey.HallEnter]
        if (!ClanRanks.atLeast(context.rank, required)) {
            return "Your clan rank is not high enough to enter this Clan Hall."
        }
        return null
    }

    fun permissionDenial(
        role: ClanHallRole,
        rank: Int,
        permissions: ClanPermissionSet,
        permission: ClanPermissionKey,
    ): String? {
        if (role == ClanHallRole.Guest && !permission.guestThresholdAllowed) {
            return "Guests cannot perform that Clan Hall action."
        }
        return if (ClanRanks.atLeast(rank, permissions[permission])) null
        else "Your clan rank is not high enough to perform that Clan Hall action."
    }
}
