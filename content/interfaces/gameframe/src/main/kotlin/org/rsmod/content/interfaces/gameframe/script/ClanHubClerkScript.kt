package org.rsmod.content.interfaces.gameframe.script

import jakarta.inject.Inject
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.rsmod.api.net.social.ClanApplication
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.net.social.ClanDomainService
import org.rsmod.api.net.social.ClanManagementResult
import org.rsmod.api.net.social.ClanManagementService
import org.rsmod.api.net.social.ClanPermissionKey
import org.rsmod.api.net.social.ClanPermissionSet
import org.rsmod.api.net.social.ClanProfile
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Edgeville access point for the revision-240 modern Clan system. */
public class ClanHubClerkScript
@Inject
constructor(
    private val management: ClanManagementService,
    private val clanDomain: ClanDomainService,
    private val clanChat: ClanChatService,
    private val creationUi: ClanCreationNativeUi,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(CLERK) { talkToClerk(it.npc) }
    }

    private suspend fun ProtectedAccess.talkToClerk(npc: Npc) {
        startDialogue(npc) {
            handleFounderInvitation()
            handleMemberInvitation()
            mainMenu()
        }
    }

    private suspend fun Dialogue.handleFounderInvitation() {
        val invitation = management.pendingFounderInvite(player) ?: return
        when (
            choice3(
                "Accept the founder invitation.",
                1,
                "Decline the founder invitation.",
                2,
                "Decide later.",
                3,
                title = invitation,
            )
        ) {
            1 ->
                report(
                    awaitClan { management.acceptFounderInvite(player) },
                    "You joined the founding group.",
                )
            2 -> {
                management.declineFounderInvite(player)
                chatNpc(neutral, "The founding invitation has been declined.")
            }
        }
    }

    private suspend fun Dialogue.handleMemberInvitation() {
        val invites =
            when (val result = awaitClan { management.pendingInvites(player) }) {
                is ClanManagementResult.Denied -> return
                is ClanManagementResult.Success -> result.value
            }
        val invite = invites.firstOrNull() ?: return
        when (
            choice3(
                "Accept ${invite.clanName}'s invitation.",
                1,
                "Decline ${invite.clanName}'s invitation.",
                2,
                "Decide later.",
                3,
                title = "Invited by ${invite.inviterDisplayName}",
            )
        ) {
            1 ->
                report(
                    awaitClan { management.acceptInvite(player, invite.clanName) },
                    "You joined ${invite.clanName}.",
                )
            2 ->
                report(
                    awaitClan { clanDomain.declineInvite(player, invite.clanId) },
                    "The clan invitation has been declined.",
                )
        }
    }

    private suspend fun Dialogue.mainMenu() {
        chatNpc(neutral, "Welcome. I can help you create, find, or manage a modern clan.")
        when (
            choice4(
                "Form a clan.",
                1,
                "View another clan.",
                2,
                "Manage my clan.",
                3,
                "Nothing, thanks.",
                4,
            )
        ) {
            1 -> formClan()
            2 -> viewAnotherClan()
            3 -> manageClan()
        }
    }

    private suspend fun Dialogue.formClan() {
        val existingClan =
            when (val result = awaitClan { management.ownProfile(player.characterId) }) {
                is ClanManagementResult.Denied -> {
                    chatNpc(neutral, result.message)
                    return
                }
                is ClanManagementResult.Success -> result.value
            }
        if (existingClan != null) {
            chatNpc(
                neutral,
                "You are already a ranked member of ${existingClan.displayName}. " +
                    "Manage or leave that clan before forming another one.",
            )
            return
        }
        // Close the complete dialogue modal before opening the side-panel form. Closing only the
        // chatmenu child leaves the protected dialogue waiting behind the clan form and makes later
        // inputs look like dead clicks.
        access.ifClose()
        creationUi.open(access)
    }

    private suspend fun Dialogue.viewAnotherClan() {
        val query = access.stringDialog("Search by clan name or owner:")
        val entries =
            when (val result = awaitClan { management.searchDirectory(query) }) {
                is ClanManagementResult.Denied -> {
                    chatNpc(neutral, result.message)
                    return
                }
                is ClanManagementResult.Success -> result.value
            }
        if (entries.isEmpty()) {
            chatNpc(neutral, "No clans matched that search.")
            return
        }
        val summary =
            entries.take(5).joinToString("<br>") {
                "${it.displayName} - ${it.ownerDisplayName} - ${it.memberCount}/500${if (it.recruitmentOpen) " - Recruiting" else ""}"
            }
        chatNpc(neutral, summary)
        val name = access.stringDialog("Enter the clan name to view:")
        val profile =
            when (val result = awaitClan { management.profile(name) }) {
                is ClanManagementResult.Denied -> {
                    chatNpc(neutral, result.message)
                    return
                }
                is ClanManagementResult.Success -> result.value
            }
        if (profile == null) {
            chatNpc(neutral, "That clan does not exist.")
            return
        }
        showProfile(profile)
        when (
            choice4(
                "Join as a guest.",
                1,
                "Submit an application.",
                2,
                "View members and ranks.",
                3,
                "Back.",
                4,
            )
        ) {
            1 -> clanChat.requestJoinGuest(player, profile.displayName)
            2 -> {
                val message = access.stringDialog("Optional application message:")
                report(
                    awaitClan {
                        management.submitApplication(player, profile.displayName, message)
                    },
                    "Your application has been submitted.",
                )
            }
            3 -> {
                val members =
                    profile.members.take(10).joinToString("<br>") {
                        "${it.displayName} - rank ${it.rank}"
                    }
                chatNpc(neutral, members.ifBlank { "This clan has no ranked members." })
            }
        }
    }

    private suspend fun Dialogue.showProfile(profile: ClanProfile) {
        chatNpc(
            neutral,
            "${profile.displayName}<br>Owner: ${profile.ownerDisplayName}<br>Members: ${profile.memberCount}/500<br>Requirements: ${profile.minimumCombatLevel} combat, ${profile.minimumTotalLevel} total<br>${profile.description.ifBlank { "No description set." }}",
        )
    }

    private suspend fun Dialogue.manageClan() {
        val profile =
            when (val result = awaitClan { management.ownProfile(player.characterId) }) {
                is ClanManagementResult.Denied -> {
                    chatNpc(neutral, result.message)
                    return
                }
                is ClanManagementResult.Success -> result.value
            }
        if (profile == null) {
            chatNpc(neutral, "You are not a ranked member of a clan.")
            return
        }
        showProfile(profile)
        when (
            choice5(
                "Applications and invitations.",
                1,
                "Member ranks and moderation.",
                2,
                "Recruitment settings.",
                3,
                "Ownership or leave clan.",
                4,
                "Back.",
                5,
            )
        ) {
            1 -> recruitmentManagement()
            2 -> memberManagement()
            3 -> settingsManagement()
            4 -> ownershipManagement()
        }
    }

    private suspend fun Dialogue.recruitmentManagement() {
        when (choice3("Review an application.", 1, "Invite a player.", 2, "Back.", 3)) {
            1 -> {
                val applications =
                    when (val result = awaitClan { management.applications(player) }) {
                        is ClanManagementResult.Denied -> {
                            chatNpc(neutral, result.message)
                            return
                        }
                        is ClanManagementResult.Success -> result.value
                    }
                showApplications(applications)
                if (applications.isEmpty()) return
                val target = access.stringDialog("Enter the applicant's name:")
                val approve = choice2("Approve application.", true, "Reject application.", false)
                report(
                    awaitClan { management.reviewApplication(player, target, approve) },
                    if (approve) "Application approved." else "Application rejected.",
                )
            }
            2 -> {
                val target = access.stringDialog("Enter the player's name to invite:")
                report(
                    awaitClan { management.inviteMember(player, target) },
                    "Clan invitation sent.",
                )
            }
        }
    }

    private suspend fun Dialogue.showApplications(applications: List<ClanApplication>) {
        if (applications.isEmpty()) {
            chatNpc(neutral, "Your clan has no pending applications.")
            return
        }
        chatNpc(
            neutral,
            applications.take(8).joinToString("<br>") {
                "${it.displayName}${if (it.message.isBlank()) "" else ": ${it.message}"}"
            },
        )
    }

    private suspend fun Dialogue.memberManagement() {
        when (
            choice5(
                "Assign a rank.",
                1,
                "Mute or unmute a member.",
                2,
                "Kick, expel, or ban a player.",
                3,
                "Remove a guest ban.",
                4,
                "Back.",
                5,
            )
        ) {
            1 -> {
                val target = access.stringDialog("Enter the member's name:")
                val rankText =
                    access.stringDialog("Rank: 0-14 custom, 100 administrator, 125 deputy owner:")
                val rank = rankText.toIntOrNull()
                if (rank == null) chatNpc(neutral, "That rank is not valid.")
                else report(awaitClan { management.setRank(player, target, rank) }, "Rank updated.")
            }
            2 -> {
                val target = access.stringDialog("Enter the member's name:")
                val muted = choice2("Mute member.", true, "Unmute member.", false)
                clanChat.requestSetMuted(player, target, muted)
            }
            3 -> {
                val target = access.stringDialog("Enter the player name:")
                when (choice3("Kick guest.", 1, "Expel member.", 2, "Ban guest.", 3)) {
                    1 -> clanChat.requestKickGuest(player, target)
                    2 -> clanChat.requestKickOrExpel(player, 0, target)
                    3 -> clanChat.requestBanGuest(player, target)
                }
            }
            4 -> {
                val target = access.stringDialog("Enter the banned player's name:")
                report(awaitClan { management.unban(player, target) }, "The guest ban was removed.")
            }
        }
    }

    private suspend fun Dialogue.settingsManagement() {
        when (
            choice3(
                "Profile and recruitment settings.",
                1,
                "Rank permission settings.",
                2,
                "Back.",
                3,
            )
        ) {
            1 -> profileSettings()
            2 -> permissionSettings()
        }
    }

    private suspend fun Dialogue.profileSettings() {
        val description = access.stringDialog("Clan description (maximum 120 characters):")
        val recruitment =
            choice2("List clan as recruiting.", true, "Do not list clan as recruiting.", false)
        val applications = choice2("Accept applications.", true, "Close applications.", false)
        val allowGuests =
            choice2("Allow guests to join the channel.", true, "Close the guest channel.", false)
        val guestsCanTalk =
            if (allowGuests) {
                choice2("Let guests talk.", true, "Guests may only listen.", false)
            } else {
                false
            }
        val minimumCombat =
            access.stringDialog("Minimum combat level to apply (0-126):").trim().toIntOrNull()
        val minimumTotal =
            access.stringDialog("Minimum total level to apply (0-2500):").trim().toIntOrNull()
        if (minimumCombat == null || minimumTotal == null) {
            chatNpc(neutral, "Both recruitment requirements must be valid numbers.")
            return
        }
        report(
            awaitClan {
                management.updateProfile(
                    player,
                    description,
                    recruitment,
                    applications,
                    allowGuests,
                    guestsCanTalk,
                    minimumCombat,
                    minimumTotal,
                )
            },
            "Clan recruitment settings updated.",
        )
    }

    private suspend fun Dialogue.permissionSettings() {
        val current =
            when (val result = awaitClan { clanDomain.permissions(player) }) {
                is ClanManagementResult.Denied -> {
                    chatNpc(neutral, result.message)
                    return
                }
                is ClanManagementResult.Success -> result.value
            }
        chatNpc(
            neutral,
            "Use -1 for everyone where offered, 0-14 for custom ranks, 100 for administrator, 125 for deputy owner, or 126 for owner.",
        )
        fun parse(value: String): Int? = value.trim().toIntOrNull()
        val talk =
            parse(access.stringDialog("Minimum member rank to talk (-1, 0-14, 100, 125, or 126):"))
        val kick = parse(access.stringDialog("Minimum rank to kick guests:"))
        val ban = parse(access.stringDialog("Minimum rank to ban guests:"))
        val expel = parse(access.stringDialog("Minimum rank to expel members:"))
        val assign = parse(access.stringDialog("Minimum rank to assign member ranks:"))
        val recruitment =
            parse(access.stringDialog("Minimum rank to recruit or review applications:"))
        val settings = parse(access.stringDialog("Minimum rank to edit clan permissions:"))
        val values = listOf(talk, kick, ban, expel, assign, recruitment, settings)
        if (values.any { it == null }) {
            chatNpc(neutral, "Every permission must be a valid rank number.")
            return
        }
        val updated =
            ClanPermissionSet(
                current.minimumRanks +
                    mapOf(
                        ClanPermissionKey.TalkInChat to talk!!,
                        ClanPermissionKey.KickGuests to kick!!,
                        ClanPermissionKey.BanGuests to ban!!,
                        ClanPermissionKey.ExpelMembers to expel!!,
                        ClanPermissionKey.AssignMemberRanks to assign!!,
                        ClanPermissionKey.Recruitment to recruitment!!,
                        ClanPermissionKey.PermissionAccess to settings!!,
                    )
            )
        report(
            awaitClan { clanDomain.updatePermissions(player, updated) },
            "Clan rank permissions updated.",
        )
    }

    private suspend fun Dialogue.ownershipManagement() {
        when (choice3("Manage ownership resignation.", 1, "Leave the clan.", 2, "Back.", 3)) {
            1 -> {
                when (val current = awaitClan { clanDomain.successor(player) }) {
                    is ClanManagementResult.Denied -> chatNpc(neutral, current.message)
                    is ClanManagementResult.Success -> {
                        if (current.value.availableAtEpochMillis != null) {
                            val result = awaitClan { clanDomain.finalizeSuccessorOwnership(player) }
                            val success =
                                (result as? ClanManagementResult.Success)?.value?.let {
                                    if (it.deleted) "Your empty clan has been deleted."
                                    else "Ownership has transferred to the eligible successor."
                                } ?: "Ownership resignation finalized."
                            report(result, success)
                        } else {
                            val result = awaitClan { clanDomain.requestSuccessorOwnership(player) }
                            val success =
                                (result as? ClanManagementResult.Success)?.value?.let {
                                    if (it.successor == null) {
                                        "Your empty clan will be deleted after seven days."
                                    } else {
                                        "Ownership will transfer to the eligible successor after seven days."
                                    }
                                } ?: "Ownership resignation scheduled."
                            report(result, success)
                        }
                    }
                }
            }
            2 -> {
                if (choice2("Leave my clan.", true, "Stay in my clan.", false)) {
                    report(awaitClan { management.leaveClan(player) }, "You have left the clan.")
                }
            }
        }
    }

    /**
     * Clan persistence requests complete asynchronously through the database gateway. Keep the
     * player's game coroutine registered while waiting so the next dialogue Continue or menu click
     * can resume the same interaction.
     */
    private suspend fun <T> Dialogue.awaitClan(request: suspend () -> T): T {
        val outcome = AtomicReference<Result<T>?>(null)
        request.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome.set(result)
                }
            }
        )
        while (outcome.get() == null) {
            delay()
        }
        return checkNotNull(outcome.get()).getOrThrow()
    }

    private suspend fun Dialogue.report(result: ClanManagementResult<*>, success: String) {
        when (result) {
            is ClanManagementResult.Denied -> chatNpc(neutral, result.message)
            is ClanManagementResult.Success -> chatNpc(neutral, success)
        }
    }

    private companion object {
        private const val CLERK = "npc.clan_clerk"
    }
}
