package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.net.social.ClanApplication
import org.rsmod.api.net.social.ClanBanSnapshot
import org.rsmod.api.net.social.ClanBroadcastLimits
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.net.social.ClanCollectionBroadcastInterval
import org.rsmod.api.net.social.ClanCombatAchievementTier
import org.rsmod.api.net.social.ClanDirectoryEntry
import org.rsmod.api.net.social.ClanDomainLimits
import org.rsmod.api.net.social.ClanDomainService
import org.rsmod.api.net.social.ClanDomainSettings
import org.rsmod.api.net.social.ClanEventActivities
import org.rsmod.api.net.social.ClanEventDraft
import org.rsmod.api.net.social.ClanEventRecord
import org.rsmod.api.net.social.ClanEventSubtype
import org.rsmod.api.net.social.ClanEventType
import org.rsmod.api.net.social.ClanGuestIronRequirement
import org.rsmod.api.net.social.ClanGuestRequirements
import org.rsmod.api.net.social.ClanHallSettings
import org.rsmod.api.net.social.ClanInterest
import org.rsmod.api.net.social.ClanManagementResult
import org.rsmod.api.net.social.ClanManagementService
import org.rsmod.api.net.social.ClanMemberSnapshot
import org.rsmod.api.net.social.ClanMotif
import org.rsmod.api.net.social.ClanMotifCodec
import org.rsmod.api.net.social.ClanMotifColour
import org.rsmod.api.net.social.ClanMotifIcon
import org.rsmod.api.net.social.ClanPermissionKey
import org.rsmod.api.net.social.ClanPermissionSet
import org.rsmod.api.net.social.ClanProfile
import org.rsmod.api.net.social.ClanRankTitleCatalog
import org.rsmod.api.net.social.ClanRanks
import org.rsmod.api.net.social.ClanRecruiterNotificationKind
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Complete server controller for the stock revision-240 clan interfaces (689-706).
 *
 * The native client remains responsible for drawing the controls. All authoritative reads and
 * writes in this controller go through [ClanManagementService], [ClanDomainService], or
 * [ClanChatService]; the local session only remembers selection/filter state.
 */
public class ClanNativeUiV4Script
@Inject
constructor(
    private val chat: ClanChatService,
    private val management: ClanManagementService,
    private val domain: ClanDomainService,
    private val sessions: ClanNativeUiV4Sessions,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    @OptIn(InternalApi::class)
    override fun ScriptContext.startup() {
        bindOpenEvents()
        bindSidePanels()
        bindInfo()
        bindMembers()
        bindDirectory()
        bindApplicants()
        bindEvents()
        bindSettings()
        onIfClose(ClanUi.HALL) {
            protectedAccess.launchLenient(player) {
                releaseHallEditor(openInfo = false, quiet = true)
            }
        }
        onPlayerLogout {
            protectedAccess.launchLenient(player) {
                releaseHallEditor(openInfo = false, quiet = true)
            }
        }
    }

    private fun ScriptContext.bindOpenEvents() {
        onIfOpen(ClanUi.INFO) { player.enableInfoEvents() }
        onIfOpen(ClanUi.MEMBERS) { player.enableMemberEvents() }
        onIfOpen(ClanUi.EVENTS) { player.enableEventEvents() }
        onIfOpen(ClanUi.EVENT_EDITOR) { player.enableEventEditorEvents() }
        onIfOpen(ClanUi.APPLICANTS) { player.enableApplicantEvents() }
        onIfOpen(ClanUi.HALL) { player.enableHallEvents() }
        onIfOpen(ClanUi.PERMISSIONS) { player.enablePermissionEvents() }
        onIfOpen(ClanUi.RANK_TITLES) { player.enableTitleEvents() }
        onIfOpen(ClanUi.BANNED) { player.enableBanEvents() }
        onIfOpen(ClanUi.INTERESTS) { player.enableInterestEvents() }
        onIfOpen(ClanUi.OUTFIT) { player.enableOutfitEvents() }
        onIfOpen(ClanUi.BOARD) { player.enableBoardEvents() }
    }

    private fun ScriptContext.bindSidePanels() {
        onIfOverlayButton(ClanUi.OWN_INFO) { openInfo(ClanUiScope.Own, modal = true) }
        onIfOverlayButton(ClanUi.GUEST_INFO) { openInfo(ClanUiScope.Guest, modal = true) }
        onIfOverlayButton(ClanUi.GUEST_SEARCH) { openDirectory(prompt = true) }
    }

    private fun ScriptContext.bindInfo() {
        onIfModalButton(ClanUi.INFO_CLOSE) { ifClose() }
        onIfModalButton(ClanUi.INFO_EVENTS) { openEvents() }
        onIfModalButton(ClanUi.INFO_MEMBERS) { openMembers() }
        onIfModalButton(ClanUi.INFO_APPLICATIONS) {
            if (session().scope == ClanUiScope.Own) openApplicants() else submitGuestApplication()
        }
        onIfModalButton(ClanUi.INFO_PERMISSIONS) { openPermissions() }
        onIfModalButton(ClanUi.INFO_TITLES) { openTitles() }
        onIfModalButton(ClanUi.INFO_BANNED) { openBans() }
        onIfModalButton(ClanUi.INFO_INTERESTS) { openInterests() }
        onIfModalButton(ClanUi.INFO_OUTFIT) { openOutfit() }
        onIfModalButton(ClanUi.INFO_NAME) { changeClanName() }
        onIfModalButton(ClanUi.INFO_HOMEWORLD) { changeHomeWorld() }
        onIfModalButton(ClanUi.INFO_TALK_RANK) { changeTalkRank() }
        onIfModalButton(ClanUi.INFO_ALLOW_GUESTS) {
            if (session().scope == ClanUiScope.Guest) joinViewedGuestClan() else toggleGuestAccess()
        }
        onIfModalButton(ClanUi.INFO_ABDICATION) { requestSuccession() }
        onIfModalButton(ClanUi.INFO_LEAVE) { leaveClanOrGuest() }
    }

    private fun ScriptContext.bindMembers() {
        onIfModalButton(ClanUi.MEMBERS_EXIT) { backToInfo() }
        onIfModalButton(ClanUi.MEMBERS_SEARCH) {
            session().memberQuery = stringDialog("Search clan members:").trim()
            refreshMemberSelection()
        }
        onIfModalButton(ClanUi.MEMBERS_MIN_RANK) {
            session().minimumRank = numberDialog("Minimum clan rank (-1 for Guest):")
            refreshMemberSelection()
        }
        onIfModalButton(ClanUi.MEMBERS_MAX_RANK) {
            session().maximumRank = numberDialog("Maximum clan rank (126 for Owner):")
            refreshMemberSelection()
        }
        onIfModalButton(ClanUi.MEMBERS_LIST) { event ->
            val option =
                when (event.op) {
                    IfButtonOp.Op1 -> 1
                    IfButtonOp.Op2 -> 2
                    IfButtonOp.Op3 -> 3
                    else -> return@onIfModalButton
                }
            manageMember(event.comsub, option)
        }
    }

    private fun ScriptContext.bindDirectory() {
        onIfModalButton(ClanUi.BOARD_RESET) { resetDirectoryFilters() }
        onIfModalButton(ClanUi.BOARD_SORT_NAME) { sortDirectory(ClanDirectorySort.Name) }
        onIfModalButton(ClanUi.BOARD_SORT_SIZE) { sortDirectory(ClanDirectorySort.Size) }
        onIfModalButton(ClanUi.BOARD_SORT_AGE) { sortDirectory(ClanDirectorySort.Age) }
        onIfModalButton(ClanUi.BOARD_FILTERS) { configureDirectoryFilter() }
        onIfModalButton(ClanUi.BOARD_REFRESH) { openDirectory(prompt = false) }
        onIfModalButton(ClanUi.BOARD_MY_CLAN) { openInfo(ClanUiScope.Own, modal = false) }
        onIfModalButton(ClanUi.BOARD_APPLICANTS) { openApplicants() }
        repeat(ClanUi.BOARD_ROWS) { index ->
            onIfModalButton(ClanUi.boardRow(index)) { openDirectoryEntry(index) }
        }
    }

    private fun ScriptContext.bindApplicants() {
        onIfModalButton(ClanUi.APPLICANTS_EXIT) { backToInfo() }
        onIfModalButton(ClanUi.APPLICANTS_REFRESH) { openApplicants() }
        onIfModalButton(ClanUi.APPLICANTS_RECRUIT) { toggleRecruitment() }
        onIfModalButton(ClanUi.APPLICANTS_COMBAT) { updateRecruitmentCombat() }
        onIfModalButton(ClanUi.APPLICANTS_TOTAL) { updateRecruitmentAdvanced() }
        onIfModalButton(ClanUi.APPLICANTS_ACCEPT) { reviewApplicant(approve = true) }
        onIfModalButton(ClanUi.APPLICANTS_REJECT) { reviewApplicant(approve = false) }
        repeat(ClanUi.APPLICANT_ROWS) { index ->
            onIfModalButton(ClanUi.applicantRow(index)) {
                val applicant = session().applicants.getOrNull(index)
                session().selectedApplicant = index.takeIf { applicant != null } ?: -1
                if (applicant != null) {
                    runClientScript(
                        ClanUi.APPLICANTS_DETAILS_SCRIPT,
                        applicant.nativeApplicantDetails(),
                    )
                }
            }
        }
    }

    private fun ScriptContext.bindEvents() {
        onIfModalButton(ClanUi.EVENTS_EXIT) { backToInfo() }
        onIfModalButton(ClanUi.EVENTS_CREATE) {
            session().selectedEvent = -1
            openEventEditor(null)
        }
        onIfModalButton(ClanUi.EVENTS_EDIT) {
            if (session().selectedEvent !in session().events.indices) {
                mes("Choose a clan event first.")
            } else {
                openEventEditor(session().events[session().selectedEvent])
            }
        }
        onIfModalButton(ClanUi.EVENTS_LIST) { event ->
            session().selectedEvent = event.comsub.coerceIn(-1, session().events.lastIndex)
        }
        onIfModalButton(ClanUi.EVENT_EDITOR_EXIT) { openEvents() }
        onIfModalButton(ClanUi.EVENT_EDITOR_CREATE) { saveEvent() }
        onIfModalButton(ClanUi.EVENT_EDITOR_DELETE) { deleteEvent() }
        onIfModalButton(ClanUi.EVENT_EDITOR_TYPE) { editEventType() }
        onIfModalButton(ClanUi.EVENT_EDITOR_SUBTYPE) { editEventSubtype() }
        onIfModalButton(ClanUi.EVENT_EDITOR_ACTIVITY) { editEventActivity() }
        onIfModalButton(ClanUi.EVENT_EDITOR_DATE) { editEventDate() }
        onIfModalButton(ClanUi.EVENT_EDITOR_TIME) { editEventTime() }
        onIfModalButton(ClanUi.EVENT_EDITOR_DURATION) { editEventDuration() }
        onIfModalButton(ClanUi.EVENT_EDITOR_WORLD) { editEventWorld() }
        onIfModalButton(ClanUi.EVENT_EDITOR_RANK) { editEventRank() }
    }

    private fun ScriptContext.bindSettings() {
        onIfModalButton(ClanUi.HALL_BACK) { releaseHallEditor(openInfo = true) }
        onIfModalButton(ClanUi.HALL_SAVE) { saveHall() }
        onIfModalButton(ClanUi.PERMISSIONS_EXIT) { backToInfo() }
        onIfModalButton(ClanUi.PERMISSIONS_LIST) { event -> selectPermission(event.comsub) }
        onIfModalButton(ClanUi.TITLES_CANCEL) { backToInfo() }
        onIfModalButton(ClanUi.TITLES_SAVE) { saveRankTitle() }
        onIfModalButton(ClanUi.BANNED_EXIT) { backToInfo() }
        onIfModalButton(ClanUi.BANNED_SEARCH) {
            session().banQuery = stringDialog("Search banned players:").trim()
            refreshBans()
        }
        onIfModalButton(ClanUi.BANNED_ADD) {
            val name = stringDialog("Player to ban from the guest clan:").trim()
            if (name.isNotEmpty()) chat.requestBanGuest(player, name)
        }
        onIfModalButton(ClanUi.BANNED_LIST) { event ->
            val banned = session().filteredBans.getOrNull(event.comsub)
            if (banned == null) {
                mes("That player is no longer in the current ban list.")
            } else {
                val result = management.unban(player, banned.displayName)
                report(result, "The player was unbanned.")
                if (result is ClanManagementResult.Success) openBans()
            }
        }
        onIfModalButton(ClanUi.INTERESTS_CANCEL) { backToInfo() }
        onIfModalButton(ClanUi.INTERESTS_LIST) { event -> toggleInterest(event.comsub) }
        onIfModalButton(ClanUi.INTERESTS_SAVE) { saveInterests() }
        onIfModalButton(ClanUi.OUTFIT_CANCEL) { cancelMotif() }
        onIfModalButton(ClanUi.OUTFIT_SAVE) { saveMotif() }
        onIfModalButton(ClanUi.OUTFIT_TOP_LEFT) { cycleMotif(primary = true, delta = -1) }
        onIfModalButton(ClanUi.OUTFIT_TOP_RIGHT) { cycleMotif(primary = true, delta = 1) }
        onIfModalButton(ClanUi.OUTFIT_BOTTOM_LEFT) { cycleMotif(primary = false, delta = -1) }
        onIfModalButton(ClanUi.OUTFIT_BOTTOM_RIGHT) { cycleMotif(primary = false, delta = 1) }
        onIfModalButton(ClanUi.OUTFIT_COLOURS) { event -> selectMotifColour(event.comsub) }
        onIfModalButton(ClanUi.OUTFIT_ICON_TOP) { selectMotifIcon(primary = true) }
        onIfModalButton(ClanUi.OUTFIT_ICON_BOTTOM) { selectMotifIcon(primary = false) }
    }

    private suspend fun ProtectedAccess.openInfo(scope: ClanUiScope, modal: Boolean) {
        val state = session()
        state.scope = scope
        val profile =
            when (scope) {
                ClanUiScope.Own -> valueOrMessage(management.ownProfile(player.characterId))
                ClanUiScope.Guest -> state.profile
            }
        if (profile == null) {
            if (scope == ClanUiScope.Guest) openDirectory(prompt = true)
            else mes("You are not a ranked member of a clan.")
            return
        }
        state.profile = profile
        state.members = profile.members
        if (modal) ifOpenMainModal(ClanUi.INFO) else ifOpenMain(ClanUi.INFO)
    }

    private suspend fun ProtectedAccess.openDirectory(prompt: Boolean) {
        val state = session()
        val query =
            if (prompt) stringDialog("Search by clan name or owner:").trim() else state.query
        val entries = valueOrMessage(management.searchDirectory(query)) ?: return
        state.query = query
        state.directorySource = entries
        state.directoryProfiles = buildMap {
            entries.forEach { entry ->
                when (val result = management.profile(entry.displayName)) {
                    is ClanManagementResult.Denied -> Unit
                    is ClanManagementResult.Success -> result.value?.let { put(entry.clanId, it) }
                }
            }
        }
        state.scope = ClanUiScope.Guest
        ifOpenMainModal(ClanUi.BOARD)
        renderDirectory()
        if (entries.isEmpty()) mes("No clans matched that search.")
    }

    private fun ProtectedAccess.renderDirectory() {
        val state = session()
        val filtered =
            state.directorySource.filter { entry ->
                state.directoryInterest == null ||
                    state.directoryProfiles[entry.clanId]
                        ?.interests
                        ?.contains(state.directoryInterest) == true
            }
        val sorted =
            when (state.directorySort) {
                ClanDirectorySort.Name -> filtered.sortedBy { it.displayName.lowercase() }
                ClanDirectorySort.Size -> filtered.sortedByDescending { it.memberCount }
                ClanDirectorySort.Age ->
                    filtered.sortedBy {
                        state.directoryProfiles[it.clanId]?.creationRuneDay ?: Int.MAX_VALUE
                    }
            }
        state.directory = sorted.take(ClanUi.BOARD_ROWS)
        state.directory.forEachIndexed { index, entry ->
            runClientScript(
                ClanUi.BOARD_ADD_ROW_SCRIPT,
                index,
                entry.recruitmentOpen.toInt(),
                entry.boardPayload(),
            )
        }
        if (state.directory.isEmpty() && state.directorySource.isNotEmpty()) {
            mes("No clans match the active recruitment-board filter.")
        }
    }

    private fun ProtectedAccess.sortDirectory(sort: ClanDirectorySort) {
        session().directorySort = sort
        renderDirectory()
    }

    private suspend fun ProtectedAccess.configureDirectoryFilter() {
        val input = stringDialog("Interest ID or name; leave blank for all clans:").trim()
        if (input.isBlank()) {
            session().directoryInterest = null
            renderDirectory()
            return
        }
        val interest =
            ClanInterest.entries.firstOrNull {
                it.storageId.toString() == input || it.name.equals(input, ignoreCase = true)
            }
        if (interest == null) {
            mes("That clan interest is not valid.")
            return
        }
        session().directoryInterest = interest
        renderDirectory()
    }

    private fun ProtectedAccess.resetDirectoryFilters() {
        session().directoryInterest = null
        session().directorySort = ClanDirectorySort.Name
        renderDirectory()
    }

    private suspend fun ProtectedAccess.openDirectoryEntry(index: Int) {
        val entry = session().directory.getOrNull(index) ?: return
        val profile = valueOrMessage(management.profile(entry.displayName))
        if (profile == null) {
            mes("That clan is no longer available.")
            openDirectory(prompt = false)
            return
        }
        session().scope = ClanUiScope.Guest
        session().profile = profile
        session().members = profile.members
        chat.requestJoinGuest(player, profile.displayName)
        mes("Loading ${profile.displayName}'s guest clan...")
        ifOpenMain(ClanUi.INFO)
    }

    private suspend fun ProtectedAccess.openMembers() {
        val profile = currentProfile() ?: return
        session().profile = profile
        session().members = profile.members.map(management::memberForDisplay)
        refreshMemberSelection()
        ifOpenMain(ClanUi.MEMBERS)
    }

    private fun ProtectedAccess.refreshMemberSelection() {
        val state = session()
        state.filteredMembers =
            state.members.filter { member ->
                member.displayName.contains(state.memberQuery, ignoreCase = true) &&
                    rankBetween(member.rank, state.minimumRank, state.maximumRank)
            }
        chat.refreshClient(player, if (state.scope == ClanUiScope.Own) 0 else 1)
        if (state.filteredMembers.isEmpty()) mes("No clan members match those filters.")
    }

    private suspend fun ProtectedAccess.manageMember(clientIndex: Int, option: Int) {
        if (session().scope != ClanUiScope.Own) {
            mes("Guest viewers cannot manage another clan's members.")
            return
        }
        val member = session().visibleMemberAt(clientIndex)
        if (member == null) {
            mes("That member is no longer in the authoritative clan member list.")
            return
        }
        when (option) {
            1 -> {
                val rank = numberDialog("New rank for ${member.displayName}:")
                if (!ClanRanks.validAssignable(rank) && rank != ClanRanks.DEPUTY_OWNER) {
                    mes(
                        "Choose a customizable rank, Administrator, or Deputy Owner. Owner uses ownership succession."
                    )
                    return
                }
                report(
                    management.setRank(player, member.displayName, rank),
                    "${member.displayName}'s rank was updated.",
                )
            }
            2 -> chat.requestSetMuted(player, member.displayName, !member.muted)
            3 -> {
                val confirmation = stringDialog("Type EXPEL to remove ${member.displayName}:")
                if (confirmation.equals("EXPEL", ignoreCase = true)) {
                    chat.requestKickOrExpel(player, 0, member.displayName)
                }
            }
            else -> mes("Use Set rank, Mute/Unmute, or Expel from the member context menu.")
        }
        openMembers()
    }

    private suspend fun ProtectedAccess.openApplicants() {
        requireOwnClan() ?: return
        val applications = valueOrMessage(management.applications(player)) ?: return
        val notices = valueOrMessage(domain.pendingRecruiterNotifications(player)) ?: return

        session().applicants = applications.take(ClanUi.APPLICANT_ROWS)
        session().selectedApplicant = applications.indices.firstOrNull() ?: -1
        ifOpenMain(ClanUi.APPLICANTS)
        session().applicants.forEachIndexed { index, applicant ->
            runClientScript(
                ClanUi.APPLICANTS_ADD_ROW_SCRIPT,
                index,
                applicant.nativeApplicantName(),
            )
        }
        session().applicants.firstOrNull()?.let {
            runClientScript(ClanUi.APPLICANTS_DETAILS_SCRIPT, it.nativeApplicantDetails())
        }
        if (applications.isEmpty()) mes("This clan has no pending applications.")
        if (notices.isNotEmpty()) {
            val submitted =
                notices.count { it.kind == ClanRecruiterNotificationKind.ApplicationSubmitted }
            if (submitted > 0) {
                val suffix = if (submitted == 1) "" else "s"
                mes("You have $submitted new clan application notification$suffix.")
            }
            when (
                val marked =
                    domain.markRecruiterNotificationsDelivered(
                        player,
                        notices.mapTo(HashSet()) { it.id },
                    )
            ) {
                is ClanManagementResult.Denied -> mes(marked.message)
                is ClanManagementResult.Success -> Unit
            }
        }
    }

    private suspend fun ProtectedAccess.reviewApplicant(approve: Boolean) {
        val applicant = session().applicants.getOrNull(session().selectedApplicant)
        if (applicant == null) {
            mes("Choose an applicant first.")
            return
        }
        val result = management.reviewApplication(player, applicant.displayName, approve)
        report(result, if (approve) "Application accepted." else "Application declined.")
        if (result is ClanManagementResult.Success) openApplicants()
    }

    private suspend fun ProtectedAccess.submitGuestApplication() {
        val profile = session().profile
        if (profile == null) {
            mes("Choose a clan from the recruitment board first.")
            return
        }
        when (
            stringDialog("Type APPLY to submit an application, or WITHDRAW to withdraw one:")
                .trim()
                .uppercase()
        ) {
            "APPLY" -> {
                val message = stringDialog("Optional application message:")
                report(
                    management.submitApplication(player, profile.displayName, message),
                    "Your application was submitted.",
                )
            }
            "WITHDRAW" ->
                report(
                    domain.withdrawApplication(player, profile.clanId),
                    "Your application was withdrawn.",
                )
            else -> mes("No application changes were made.")
        }
    }

    private suspend fun ProtectedAccess.toggleRecruitment() {
        val profile = requireOwnClan() ?: return
        val result =
            management.updateProfile(
                player,
                profile.description,
                !profile.recruitmentOpen,
                !profile.recruitmentOpen,
                profile.allowGuests,
                profile.guestsCanTalk,
                profile.minimumCombatLevel,
                profile.minimumTotalLevel,
            )
        report(
            result,
            if (profile.recruitmentOpen) "Recruitment closed." else "Recruitment opened.",
        )
        if (result is ClanManagementResult.Success) openApplicants()
    }

    private suspend fun ProtectedAccess.updateRecruitmentCombat() {
        requireOwnClan() ?: return
        val current = valueOrMessage(domain.domainSettings(player)) ?: return
        val requirements =
            current.guestRequirements.copy(
                minimumCombatLevel = numberDialog("Minimum combat level (0 for none):")
            )
        saveRecruitmentRequirements(current, requirements)
    }

    private suspend fun ProtectedAccess.updateRecruitmentAdvanced() {
        requireOwnClan() ?: return
        val current = valueOrMessage(domain.domainSettings(player)) ?: return
        val total = numberDialog("Minimum total level (0 for none):")
        val questPoints = numberDialog("Minimum quest points (0 for none):")
        val ironId =
            numberDialog("Ironman requirement: 0 Any account, 1 Any Iron, 2 Ultimate, 3 Hardcore:")
        val iron = ClanGuestIronRequirement.entries.firstOrNull { it.storageId == ironId }
        if (iron == null) {
            mes("That Ironman requirement is not valid.")
            return
        }
        val requirements =
            current.guestRequirements.copy(
                minimumTotalLevel = total,
                minimumQuestPoints = questPoints,
                ironRequirement = iron,
            )
        saveRecruitmentRequirements(current, requirements)
    }

    private suspend fun ProtectedAccess.saveRecruitmentRequirements(
        current: ClanDomainSettings,
        requirements: ClanGuestRequirements,
    ) {
        val validation = requirements.validationError()
        if (validation != null) {
            mes(validation)
            return
        }
        val confirmation = stringDialog("Type SAVE to update the recruitment requirements:")
        if (!confirmation.equals("SAVE", ignoreCase = true)) {
            mes("Recruitment requirements were not changed.")
            return
        }
        val result =
            domain.updateDomainSettings(player, current.copy(guestRequirements = requirements))
        report(result, "Clan recruitment requirements saved.")
        if (result is ClanManagementResult.Success) openApplicants()
    }

    private suspend fun ProtectedAccess.openEvents() {
        val profile = currentProfile() ?: return
        val events =
            valueOrMessage(
                if (session().scope == ClanUiScope.Own) domain.events(player)
                else domain.events(profile.clanId)
            ) ?: return
        session().events = events
        session().selectedEvent = events.indices.firstOrNull() ?: -1
        ifOpenMain(ClanUi.EVENTS)
        events.forEachIndexed { index, event ->
            runClientScript(
                ClanUi.EVENTS_ADD_ROW_SCRIPT,
                index,
                1,
                event.minimumJoinRank,
                0,
                event.eventPayload(),
            )
        }
        if (events.isEmpty()) mes("This clan has no scheduled events.")
    }

    private suspend fun ProtectedAccess.openEventEditor(existing: ClanEventRecord?) {
        val profile = currentProfile() ?: return
        if (profile.recruitmentOpen || profile.applicationsOpen) {
            mes(ClanUi.EVENT_EDIT_RECRUITMENT_BLOCKED)
            return
        }
        val now = Instant.now().atZone(ZoneOffset.UTC)
        session().eventDraft =
            if (existing == null) {
                ClanEventDraft(
                    ClanEventType.Bossing,
                    ClanEventSubtype.None,
                    ClanEventActivities.ToBeConfirmed.storageKey,
                    0,
                    now.hour,
                    now.minute,
                    ClanDomainLimits.GAME_TICKS_PER_DAY,
                    ClanRanks.GUEST,
                    1,
                )
            } else {
                val start =
                    Instant.ofEpochMilli(existing.startsAtEpochMillis).atZone(ZoneOffset.UTC)
                ClanEventDraft(
                    existing.type,
                    existing.subtype,
                    existing.activityKey,
                    ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate())
                        .toInt()
                        .coerceIn(0, 30),
                    start.hour,
                    start.minute,
                    existing.durationTicks,
                    existing.minimumJoinRank,
                    existing.worldId,
                )
            }
        ifOpenMain(ClanUi.EVENT_EDITOR)
    }

    private suspend fun ProtectedAccess.editEventType() {
        val draft = session().eventDraft ?: return
        val value = numberDialog("Event type: 0 Bossing, 1 Skilling, 2 PvP, 3 Social:")
        val type = ClanEventType.entries.firstOrNull { it.storageId == value }
        if (type == null) mes("That event type is not valid.")
        else session().eventDraft = draft.copy(type = type)
    }

    private suspend fun ProtectedAccess.editEventSubtype() {
        val draft = session().eventDraft ?: return
        val value = numberDialog("Event subtype 0-9:")
        val subtype = ClanEventSubtype.entries.firstOrNull { it.storageId == value }
        if (subtype == null) mes("That event subtype is not valid.")
        else session().eventDraft = draft.copy(subtype = subtype)
    }

    private suspend fun ProtectedAccess.editEventActivity() {
        val draft = session().eventDraft ?: return
        val input = stringDialog("Activity name:").trim()
        val activity =
            ClanEventActivities.all.firstOrNull {
                it.displayName.equals(input, ignoreCase = true) ||
                    it.storageKey.equals(input, ignoreCase = true)
            }
        if (activity == null) mes("That activity is not in the OSRS clan activity list.")
        else session().eventDraft = draft.copy(activityKey = activity.storageKey)
    }

    private suspend fun ProtectedAccess.editEventDate() {
        val draft = session().eventDraft ?: return
        session().eventDraft = draft.copy(daysUntilStart = numberDialog("Days from today (0-30):"))
    }

    private suspend fun ProtectedAccess.editEventTime() {
        val draft = session().eventDraft ?: return
        val hour = numberDialog("Start hour UTC (0-23):")
        val minute = numberDialog("Start minute UTC (0-59):")
        session().eventDraft = draft.copy(startHourUtc = hour, startMinuteUtc = minute)
    }

    private suspend fun ProtectedAccess.editEventDuration() {
        val draft = session().eventDraft ?: return
        val durationDays = numberDialog("Duration in days (1-30):")
        session().eventDraft =
            draft.copy(durationTicks = durationDays.toLong() * ClanDomainLimits.GAME_TICKS_PER_DAY)
    }

    private suspend fun ProtectedAccess.editEventWorld() {
        val draft = session().eventDraft ?: return
        session().eventDraft = draft.copy(worldId = numberDialog("World:"))
    }

    private suspend fun ProtectedAccess.editEventRank() {
        val draft = session().eventDraft ?: return
        session().eventDraft =
            draft.copy(
                minimumJoinRank = numberDialog("Minimum rank (-1 Guest, 0 Member, 126 Owner):")
            )
    }

    private suspend fun ProtectedAccess.saveEvent() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests may view clan events but cannot create or edit them.")
            return
        }
        requireOwnClan() ?: return
        val existing = session().events.getOrNull(session().selectedEvent)
        val draft = session().eventDraft
        if (draft == null) {
            mes("The event editor has no active draft.")
            return
        }
        val result =
            if (existing == null) domain.createEvent(player, draft)
            else domain.updateEvent(player, existing.id, draft)
        report(result, if (existing == null) "Clan event created." else "Clan event updated.")
        if (result is ClanManagementResult.Success) openEvents()
    }

    private suspend fun ProtectedAccess.deleteEvent() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests may view clan events but cannot delete them.")
            return
        }
        val event = session().events.getOrNull(session().selectedEvent)
        if (event == null) {
            mes("Choose an event to delete first.")
            return
        }
        val confirmation = stringDialog("Type DELETE to remove this clan event:")
        if (!confirmation.equals("DELETE", ignoreCase = true)) return
        val result = domain.deleteEvent(player, event.id)
        report(result, "Clan event deleted.")
        if (result is ClanManagementResult.Success) openEvents()
    }

    private suspend fun ProtectedAccess.promptEventDraft(
        existing: ClanEventRecord?
    ): ClanEventDraft? {
        val typeId = numberDialog("Event type: 0 Bossing, 1 Skilling, 2 PvP, 3 Social:")
        val type = ClanEventType.entries.firstOrNull { it.storageId == typeId }
        if (type == null) {
            mes("That event type is not valid.")
            return null
        }
        val subtypeId = numberDialog("Event subtype 0-9:")
        val subtype = ClanEventSubtype.entries.firstOrNull { it.storageId == subtypeId }
        if (subtype == null) {
            mes("That event subtype is not valid.")
            return null
        }
        val activityInput = stringDialog("Activity name (for example Tombs of Amascut):").trim()
        val activity =
            ClanEventActivities.all.firstOrNull {
                it.displayName.equals(activityInput, ignoreCase = true) ||
                    it.storageKey.equals(activityInput, ignoreCase = true)
            }
        if (activity == null) {
            mes("That activity is not in the OSRS clan activity list.")
            return null
        }
        val days = numberDialog("Days from today (0-30):")
        val hour = numberDialog("Start hour UTC (0-23):")
        val minute = numberDialog("Start minute UTC (0-59):")
        val durationDays = numberDialog("Duration in days (1-30):")
        val rank = numberDialog("Minimum rank (-1 Guest, 0 Member, 126 Owner):")
        val world = numberDialog("World:")
        return ClanEventDraft(
            type,
            subtype,
            activity.storageKey,
            days,
            hour,
            minute,
            durationDays.toLong() * ClanDomainLimits.GAME_TICKS_PER_DAY,
            rank,
            world,
        )
    }

    private suspend fun ProtectedAccess.openPermissions() {
        requireOwnClan() ?: return
        session().permissions = valueOrMessage(domain.permissions(player)) ?: return
        session().domainSettings = valueOrMessage(domain.domainSettings(player)) ?: return
        ifOpenMain(ClanUi.PERMISSIONS)
    }

    private suspend fun ProtectedAccess.selectPermission(clientIndex: Int) {
        val row = ClanUi.permissionRow(clientIndex)
        if (row == null) {
            mes("That permission row is not available.")
            return
        }
        when (row.kind) {
            ClanNativePermissionRow.Kind.PermissionRank -> updatePermissionRank(row)
            ClanNativePermissionRow.Kind.BroadcastRank -> updateBroadcastRank(row)
            ClanNativePermissionRow.Kind.GuestAccess -> updateGuestAccessFromPermissions()
            ClanNativePermissionRow.Kind.GuestCombat,
            ClanNativePermissionRow.Kind.GuestTotal,
            ClanNativePermissionRow.Kind.GuestQuestPoints,
            ClanNativePermissionRow.Kind.GuestIron -> updateGuestRequirement(row)
            else -> updateBroadcastValue(row)
        }
    }

    private suspend fun ProtectedAccess.updatePermissionRank(row: ClanNativePermissionRow) {
        val key = checkNotNull(row.permission)
        val current = session().permissions ?: valueOrMessage(domain.permissions(player)) ?: return
        val rank = numberDialog("Minimum rank for ${row.label}:")
        val result =
            domain.updatePermissions(
                player,
                ClanPermissionSet(current.minimumRanks + (key to rank)),
            )
        report(result, "${row.label} permission updated.")
        if (result is ClanManagementResult.Success) {
            session().permissions = result.value
            chat.refreshClient(player, 0)
            openPermissions()
        }
    }

    private suspend fun ProtectedAccess.updateBroadcastRank(row: ClanNativePermissionRow) {
        val key = checkNotNull(row.broadcast)
        val currentDomain = currentPermissionDomain() ?: return
        val rank = numberDialog("Minimum rank for ${row.label}:")
        saveBroadcastSettings(
            currentDomain.broadcastSettings.copy(
                minimumRanks = currentDomain.broadcastSettings.minimumRanks + (key to rank)
            ),
            "${row.label} rank updated.",
        )
    }

    private suspend fun ProtectedAccess.updateGuestAccessFromPermissions() {
        val profile = requireOwnClan() ?: return
        val allow =
            yesNo(
                stringDialog("Allow guests to listen in clan chat? Type YES or NO:"),
                profile.allowGuests,
            )
        val result =
            management.updateProfile(
                player,
                profile.description,
                profile.recruitmentOpen,
                profile.applicationsOpen,
                allow,
                profile.guestsCanTalk,
                profile.minimumCombatLevel,
                profile.minimumTotalLevel,
            )
        report(
            result,
            if (allow) "Guests may now listen in clan chat." else "Guest listening disabled.",
        )
        if (result is ClanManagementResult.Success) {
            chat.refreshClient(player, 0)
            openPermissions()
        }
    }

    private suspend fun ProtectedAccess.updateGuestRequirement(row: ClanNativePermissionRow) {
        val current = currentPermissionDomain() ?: return
        val requirements = current.guestRequirements
        val updated =
            when (row.kind) {
                ClanNativePermissionRow.Kind.GuestCombat ->
                    requirements.copy(
                        minimumCombatLevel =
                            numberDialog("Minimum guest combat level (0 disables):")
                    )
                ClanNativePermissionRow.Kind.GuestTotal ->
                    requirements.copy(
                        minimumTotalLevel = numberDialog("Minimum guest skill total (0 disables):")
                    )
                ClanNativePermissionRow.Kind.GuestQuestPoints ->
                    requirements.copy(
                        minimumQuestPoints =
                            numberDialog("Minimum guest quest points (0 disables):")
                    )
                ClanNativePermissionRow.Kind.GuestIron -> {
                    val id =
                        numberDialog("Iron requirement: 0 Any, 1 Any Iron, 2 Ultimate, 3 Hardcore:")
                    val requirement =
                        ClanGuestIronRequirement.entries.firstOrNull { it.storageId == id }
                    if (requirement == null) {
                        mes("That Iron status requirement is not valid.")
                        return
                    }
                    requirements.copy(ironRequirement = requirement)
                }
                else -> return
            }
        val result = domain.updateDomainSettings(player, current.copy(guestRequirements = updated))
        report(result, "${row.label} updated.")
        if (result is ClanManagementResult.Success) {
            session().domainSettings = result.value
            chat.refreshClient(player, 0)
            openPermissions()
        }
    }

    private suspend fun ProtectedAccess.updateBroadcastValue(row: ClanNativePermissionRow) {
        val current = currentPermissionDomain()?.broadcastSettings ?: return
        val updated =
            when (row.kind) {
                ClanNativePermissionRow.Kind.EventNotifications ->
                    current.copy(
                        eventStartingNotificationsEnabled =
                            yesNo(
                                stringDialog(
                                    "Notify members one hour before events? Type YES or NO:"
                                ),
                                current.eventStartingNotificationsEnabled,
                            )
                    )
                ClanNativePermissionRow.Kind.MainWorldOnly ->
                    current.copy(
                        mainGameWorldsOnly =
                            yesNo(
                                stringDialog(
                                    "Broadcast from main-game worlds only? Type YES or NO:"
                                ),
                                current.mainGameWorldsOnly,
                            )
                    )
                ClanNativePermissionRow.Kind.CollectionInterval -> {
                    val interval = numberDialog("Collection-log interval (0 All, 10, 25 or 50):")
                    val value =
                        if (interval == 0) ClanCollectionBroadcastInterval.EverySlot
                        else
                            ClanCollectionBroadcastInterval.entries.firstOrNull {
                                it.slots == interval
                            }
                    if (value == null) {
                        mes("Use 0, 10, 25 or 50 collection-log slots.")
                        return
                    }
                    current.copy(collectionInterval = value)
                }
                ClanNativePermissionRow.Kind.LevelEveryTen ->
                    current.copy(levelEveryTenMinimum = promptLevelThreshold("10-level"))
                ClanNativePermissionRow.Kind.LevelEveryFive ->
                    current.copy(levelEveryFiveMinimum = promptLevelThreshold("5-level"))
                ClanNativePermissionRow.Kind.LevelEveryOne ->
                    current.copy(levelEveryOneMinimum = promptLevelThreshold("every-level"))
                ClanNativePermissionRow.Kind.Post99Interval -> {
                    val millions = numberDialog("Post-99 XP interval in millions:")
                    val xp = millions.toLong() * ClanBroadcastLimits.POST_99_XP_INTERVAL_UNIT
                    if (millions <= 0 || xp > Int.MAX_VALUE) {
                        mes("Enter a positive whole number of millions that fits the XP counter.")
                        return
                    }
                    current.copy(post99XpInterval = xp.toInt())
                }
                ClanNativePermissionRow.Kind.LootValue ->
                    current.copy(minimumLootValue = promptNonNegativeValue("Minimum loot value:"))
                ClanNativePermissionRow.Kind.PlayerKillValue ->
                    current.copy(
                        minimumPlayerKillValue =
                            promptNonNegativeValue("Minimum player-kill value:")
                    )
                ClanNativePermissionRow.Kind.LootKeyValue ->
                    current.copy(
                        minimumLootKeyValue = promptNonNegativeValue("Minimum loot-key value:")
                    )
                ClanNativePermissionRow.Kind.CombatAchievementTaskTier -> {
                    val tier =
                        numberDialog("Minimum CA task tier (-1 All, 0 Easy through 5 Grandmaster):")
                    val value =
                        ClanCombatAchievementTier.entries.firstOrNull { it.storageId == tier }
                    if (value == null) {
                        mes("That Combat Achievement tier is not valid.")
                        return
                    }
                    current.copy(minimumCombatAchievementTier = value)
                }
                else -> return
            }
        saveBroadcastSettings(updated, "${row.label} updated.")
    }

    private suspend fun ProtectedAccess.promptLevelThreshold(label: String): Int =
        numberDialog("Starting level for $label broadcasts (0 disables, 1-99 enables):")
            .coerceIn(
                ClanBroadcastLimits.MINIMUM_LEVEL_THRESHOLD,
                ClanBroadcastLimits.MAXIMUM_LEVEL_THRESHOLD,
            )

    private suspend fun ProtectedAccess.promptNonNegativeValue(prompt: String): Int =
        numberDialog(prompt).coerceAtLeast(0)

    private suspend fun ProtectedAccess.currentPermissionDomain(): ClanDomainSettings? =
        session().domainSettings ?: valueOrMessage(domain.domainSettings(player))

    private suspend fun ProtectedAccess.saveBroadcastSettings(
        updated: org.rsmod.api.net.social.ClanBroadcastSettings,
        success: String,
    ) {
        val result = domain.updateBroadcastSettings(player, updated)
        report(result, success)
        if (result is ClanManagementResult.Success) {
            session().domainSettings =
                currentPermissionDomain()?.copy(broadcastSettings = result.value)
            chat.refreshClient(player, 0)
            openPermissions()
        }
    }

    private suspend fun ProtectedAccess.openHall() {
        requireOwnClan() ?: return
        session().hall = valueOrMessage(domain.beginHallEditSession(player)) ?: return
        ifOpenMain(ClanUi.HALL)
    }

    private suspend fun ProtectedAccess.saveHall() {
        val current = session().hall ?: valueOrMessage(domain.hallSettings(player)) ?: return
        val homeOnly =
            yesNo(stringDialog("Home-world only? Type YES or NO:"), current.homeWorldOnly)
        val locked = yesNo(stringDialog("Lock the Clan Hall? Type YES or NO:"), current.locked)
        val capacity = numberDialog("Clan Hall player capacity (1-2000):")
        val theme = stringDialog("Clan Hall theme key:").trim().ifBlank { current.themeKey }
        val identity =
            stringDialog("Clan Hall identity key:").trim().ifBlank { current.identityKey }
        val updated =
            current.copy(
                homeWorldOnly = homeOnly,
                locked = locked,
                playerCapacity = capacity,
                themeKey = theme,
                identityKey = identity,
            )
        val result = domain.updateHallSettings(player, updated)
        report(result, "Clan Hall settings saved.")
        if (result is ClanManagementResult.Success) {
            session().hall = result.value
            releaseHallEditor(openInfo = true)
        }
    }

    private suspend fun ProtectedAccess.releaseHallEditor(
        openInfo: Boolean,
        quiet: Boolean = false,
    ) {
        val result = domain.releaseHallEditSession(player)
        session().hall = null
        when (result) {
            is ClanManagementResult.Success -> if (openInfo) backToInfo()
            is ClanManagementResult.Denied -> {
                if (!quiet) mes(result.message)
                if (openInfo) backToInfo()
            }
        }
    }

    private suspend fun ProtectedAccess.openTitles() {
        val profile = currentProfile() ?: return
        session().titles =
            valueOrMessage(
                if (session().scope == ClanUiScope.Own) domain.rankTitles(player)
                else domain.rankTitles(profile.clanId)
            ) ?: return
        ifOpenMain(ClanUi.RANK_TITLES)
    }

    private suspend fun ProtectedAccess.saveRankTitle() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests may view rank titles but cannot edit them.")
            return
        }
        val current = session().titles ?: valueOrMessage(domain.rankTitles(player)) ?: return
        val slot = numberDialog("Custom rank-title slot (0-23):")
        if (slot !in 0 until 24) {
            mes("Rank-title slots are 0 through 23.")
            return
        }
        val currentId = ClanRankTitleCatalog.id(current[slot].orEmpty())
        val titleId =
            numberDialog(
                "Rank-title ID (1-${ClanRankTitleCatalog.selectable.size})" +
                    currentId?.let { "; current $it" }.orEmpty() +
                    ":"
            )
        val result = domain.updateRankTitle(player, slot, titleId)
        report(result, "Rank title saved.")
        if (result is ClanManagementResult.Success) {
            session().titles = result.value
            openTitles()
        }
    }

    private suspend fun ProtectedAccess.openInterests() {
        val profile = currentProfile() ?: return
        session().domainSettings =
            valueOrMessage(
                if (session().scope == ClanUiScope.Own) domain.domainSettings(player)
                else domain.domainSettings(profile.clanId)
            ) ?: return
        session().interestDraft = session().domainSettings?.interests.orEmpty()
        setInterestEditingVarp(session().interestDraft.orEmpty())
        ifOpenMain(ClanUi.INTERESTS)
        runClientScript(
            ClanUi.INTERESTS_OPEN_SCRIPT,
            if (session().scope == ClanUiScope.Own) 0 else 1,
        )
    }

    private fun ProtectedAccess.toggleInterest(clientIndex: Int) {
        val interest = ClanUi.interestAt(clientIndex) ?: return
        if (session().scope != ClanUiScope.Own) {
            mes("Guests may view clan interests but cannot edit them.")
            setInterestEditingVarp(session().interestDraft.orEmpty())
            return
        }
        val current = session().interestDraft.orEmpty()
        val updated =
            if (interest in current) current - interest
            else if (current.size >= ClanDomainLimits.INTERESTS_PER_CLAN) {
                mes("A clan may select at most ${ClanDomainLimits.INTERESTS_PER_CLAN} interests.")
                current
            } else {
                current + interest
            }
        session().interestDraft = updated
        setInterestEditingVarp(updated)
    }

    private suspend fun ProtectedAccess.saveInterests() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests may view clan interests but cannot edit them.")
            return
        }
        val current =
            session().domainSettings ?: valueOrMessage(domain.domainSettings(player)) ?: return
        val requested = session().interestDraft ?: current.interests
        if (requested.size > ClanDomainLimits.INTERESTS_PER_CLAN) {
            mes("A clan may select at most ${ClanDomainLimits.INTERESTS_PER_CLAN} interests.")
            setInterestEditingVarp(current.interests)
            return
        }
        val updated = current.copy(interests = requested)
        val result = domain.updateDomainSettings(player, updated)
        report(result, "Clan interests saved.")
        if (result is ClanManagementResult.Success) {
            session().domainSettings = result.value
            session().interestDraft = result.value.interests
            chat.refreshClient(player, 0)
            openInterests()
        }
    }

    private fun ProtectedAccess.setInterestEditingVarp(interests: Set<ClanInterest>) {
        val varp = requireNotNull(ServerCacheManager.getVarp(ClanUi.INTERESTS_EDITING_VARP))
        VarPlayerIntMapSetter.set(player, varp, interests.nativeInterestMask())
    }

    private suspend fun ProtectedAccess.openOutfit() {
        val profile = currentProfile() ?: return
        val motif = valueOrMessage(domain.motif(profile.clanId)) ?: return
        session().motif = motif
        session().motifDraft = motif
        setMotifEditingVarp(motif)
        ifOpenMain(ClanUi.OUTFIT)
        runClientScript(ClanUi.OUTFIT_OPEN_SCRIPT, if (session().scope == ClanUiScope.Own) 0 else 1)
    }

    private suspend fun ProtectedAccess.cancelMotif() {
        val saved = session().motif
        session().motifDraft = saved
        if (saved != null) setMotifEditingVarp(saved)
        backToInfo()
    }

    private suspend fun ProtectedAccess.saveMotif() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests may preview the clan motif but cannot edit it.")
            return
        }
        val motif = session().motifDraft ?: session().motif ?: return
        val result = domain.updateMotif(player, motif)
        report(result, "Clan motif saved.")
        if (result is ClanManagementResult.Success) {
            session().motif = result.value
            session().motifDraft = result.value
            openOutfit()
        }
    }

    private fun ProtectedAccess.cycleMotif(primary: Boolean, delta: Int) {
        val current = session().motifDraft ?: return
        val icons = ClanMotifIcon.entries
        val selected = if (primary) current.primarySymbol else current.secondarySymbol
        val encoded = ((selected?.storageId ?: -1) + 1 + delta).mod(icons.size + 1)
        val next = if (encoded == 0) null else icons.first { it.storageId == encoded - 1 }
        session().motifDraft =
            if (primary) current.copy(primarySymbol = next)
            else current.copy(secondarySymbol = next)
        redrawMotifDraft()
    }

    private fun ProtectedAccess.selectMotifColour(clientIndex: Int) {
        val current = session().motifDraft ?: return
        val colour = ClanMotifColour.entries.firstOrNull { it.storageId == clientIndex } ?: return
        session().motifDraft = current.copy(primaryColour = colour, secondaryColour = colour)
        redrawMotifDraft()
    }

    private suspend fun ProtectedAccess.selectMotifIcon(primary: Boolean) {
        val current = session().motifDraft ?: return
        val id =
            promptIconId(
                if (primary) "Primary symbol (-1 none, 0-26):"
                else "Secondary symbol (-1 none, 0-26):"
            ) ?: return
        val icon = ClanMotifIcon.entries.firstOrNull { it.storageId == id }
        session().motifDraft =
            if (primary) current.copy(primarySymbol = icon)
            else current.copy(secondarySymbol = icon)
        redrawMotifDraft()
    }

    private fun ProtectedAccess.redrawMotifDraft() {
        val draft = session().motifDraft ?: return
        setMotifEditingVarp(draft)
        runClientScript(ClanUi.OUTFIT_REDRAW_SCRIPT)
    }

    private fun ProtectedAccess.setMotifEditingVarp(motif: ClanMotif) {
        val varp = requireNotNull(ServerCacheManager.getVarp(ClanUi.OUTFIT_EDITING_VARP))
        VarPlayerIntMapSetter.set(player, varp, motif.nativeEditorPacked())
    }

    private suspend fun ProtectedAccess.openBans() {
        requireOwnClan() ?: return
        session().bans = valueOrMessage(domain.bans(player)) ?: return
        refreshBans()
        ifOpenMain(ClanUi.BANNED)
        if (session().bans.isEmpty()) mes("This clan has no banned guests.")
    }

    private fun ProtectedAccess.refreshBans() {
        val state = session()
        state.filteredBans =
            state.bans.filter { it.displayName.contains(state.banQuery, ignoreCase = true) }
        chat.refreshClient(player, 0)
        if (state.filteredBans.isEmpty() && state.banQuery.isNotBlank()) {
            mes("No banned players match that search.")
        }
    }

    private suspend fun ProtectedAccess.changeClanName() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests cannot change another clan's name.")
            return
        }
        val requested = stringDialog("Enter a new clan name:").trim()
        if (requested.isEmpty()) return
        val confirmation = stringDialog("Type CHANGE to rename the clan to '$requested':")
        if (!confirmation.equals("CHANGE", ignoreCase = true)) return
        val result = domain.renameClan(player, requested)
        val changedName = (result as? ClanManagementResult.Success)?.value ?: requested
        report(result, "Your clan is now named '$changedName'.")
        if (result is ClanManagementResult.Success) openInfo(ClanUiScope.Own, modal = false)
    }

    private suspend fun ProtectedAccess.changeHomeWorld() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests cannot change another clan's home world.")
            return
        }
        val world = numberDialog("Enter the clan home world:")
        val result = domain.updateHomeWorld(player, world)
        report(result, "Clan home world updated to $world.")
        if (result is ClanManagementResult.Success) openInfo(ClanUiScope.Own, modal = false)
    }

    private suspend fun ProtectedAccess.changeTalkRank() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests cannot change another clan's chat permissions.")
            return
        }
        val current = valueOrMessage(domain.permissions(player)) ?: return
        val rank = numberDialog("Minimum rank allowed to talk (-1 permits Guests):")
        val result =
            domain.updatePermissions(
                player,
                ClanPermissionSet(current.minimumRanks + (ClanPermissionKey.TalkInChat to rank)),
            )
        report(result, "Clan chat speaking rank updated.")
        if (result is ClanManagementResult.Success) openInfo(ClanUiScope.Own, modal = false)
    }

    private suspend fun ProtectedAccess.joinViewedGuestClan() {
        val profile = session().profile
        if (session().scope != ClanUiScope.Guest || profile == null) {
            mes("Choose a clan from the recruitment board first.")
            return
        }
        chat.requestJoinGuest(player, profile.displayName)
        mes("Joining ${profile.displayName} as a guest...")
    }

    private suspend fun ProtectedAccess.toggleGuestAccess() {
        if (session().scope != ClanUiScope.Own) {
            mes("Guests cannot change another clan's guest access.")
            return
        }
        val profile = requireOwnClan() ?: return
        val result =
            management.updateProfile(
                player,
                profile.description,
                profile.recruitmentOpen,
                profile.applicationsOpen,
                !profile.allowGuests,
                profile.guestsCanTalk,
                profile.minimumCombatLevel,
                profile.minimumTotalLevel,
            )
        report(
            result,
            if (profile.allowGuests) "Guest access disabled." else "Guest access enabled.",
        )
        if (result is ClanManagementResult.Success) openInfo(ClanUiScope.Own, modal = false)
    }

    private suspend fun ProtectedAccess.requestSuccession() {
        if (session().scope != ClanUiScope.Own) {
            mes("Only the clan Owner can begin ownership succession.")
            return
        }
        val current = valueOrMessage(domain.successor(player)) ?: return
        if (current.availableAtEpochMillis != null) {
            val finalized = valueOrMessage(domain.finalizeSuccessorOwnership(player)) ?: return
            if (finalized.deleted) {
                sessions.clear(player.characterId)
                ifClose()
                mes("Your empty clan has been deleted.")
            } else {
                mes("Clan ownership has transferred to the eligible successor.")
                openInfo(ClanUiScope.Own, modal = false)
            }
            return
        }
        val status = valueOrMessage(domain.requestSuccessorOwnership(player)) ?: return
        val successor = status.successor
        if (successor == null) {
            mes("Your empty clan will be deleted after the seven-day resignation delay.")
        } else {
            mes(
                "Ownership resignation is scheduled for character ${successor.characterId}; the clan keeps exactly one Owner until it finalizes."
            )
        }
    }

    private suspend fun ProtectedAccess.leaveClanOrGuest() {
        if (session().scope == ClanUiScope.Guest) {
            val guest = chat.guestClan(player)
            if (guest == null) {
                mes("You are viewing this clan, but you have not joined its guest channel.")
                return
            }
            val confirmation =
                stringDialog("Type LEAVE to leave ${guest.displayName}'s guest channel:")
            if (!confirmation.equals("LEAVE", ignoreCase = true)) {
                mes("You remain in ${guest.displayName}'s guest channel.")
                return
            }
            chat.leaveGuest(player)
            session().profile = null
            ifClose()
            return
        }
        val profile = requireOwnClan() ?: return
        val confirmation = stringDialog("Type LEAVE to leave your clan:")
        if (!confirmation.equals("LEAVE", ignoreCase = true)) {
            mes("You remain in your clan.")
            return
        }
        val actor = profile.members.firstOrNull { it.characterId == player.characterId }
        if (actor?.rank == ClanRanks.OWNER) {
            mes(
                "A clan must always have one Owner. Your ownership resignation will begin " +
                    "instead; after it finalizes, you can leave as a ranked member."
            )
            requestSuccession()
            return
        }
        val result = management.leaveClan(player)
        report(result, "You have left the clan.")
        if (result is ClanManagementResult.Success) {
            sessions.clear(player.characterId)
            ifClose()
        }
    }

    private suspend fun ProtectedAccess.requireOwnClan(): ClanProfile? {
        if (session().scope != ClanUiScope.Own) {
            mes("That screen is available only to ranked members of your own clan.")
            return null
        }
        return valueOrMessage(management.ownProfile(player.characterId))
    }

    private suspend fun ProtectedAccess.currentProfile(): ClanProfile? =
        if (session().scope == ClanUiScope.Own) requireOwnClan() else session().profile

    private fun ProtectedAccess.backToInfo() = ifOpenMain(ClanUi.INFO)

    private fun ProtectedAccess.session(): ClanUiV4Session =
        sessions.forCharacter(player.characterId)

    private fun <T> ProtectedAccess.valueOrMessage(result: ClanManagementResult<T>): T? =
        when (result) {
            is ClanManagementResult.Denied -> {
                mes(result.message)
                null
            }
            is ClanManagementResult.Success -> result.value
        }

    private fun ProtectedAccess.report(result: ClanManagementResult<*>, success: String) {
        when (result) {
            is ClanManagementResult.Denied -> mes(result.message)
            is ClanManagementResult.Success -> mes(success)
        }
    }

    private suspend fun ProtectedAccess.promptColour(title: String): ClanMotifColour? {
        val id = numberDialog(title)
        return ClanMotifColour.entries.firstOrNull { it.storageId == id }
            ?: run {
                mes("That motif colour is not valid.")
                null
            }
    }

    private suspend fun ProtectedAccess.promptIconId(title: String): Int? {
        val id = numberDialog(title)
        if (id == -1 || ClanMotifIcon.entries.any { it.storageId == id }) return id
        mes("That motif symbol is not valid.")
        return null
    }

    private fun Player.enableInfoEvents() =
        ClanUi.INFO_BUTTONS.forEach { ifSetEvents(it, -1..-1, IfEvent.Op1) }

    private fun Player.enableMemberEvents() {
        ifSetEvents(ClanUi.MEMBERS_EXIT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.MEMBERS_SEARCH, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.MEMBERS_MIN_RANK, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.MEMBERS_MAX_RANK, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.MEMBERS_LIST, 0..499, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3)
    }

    private fun Player.enableEventEvents() {
        ifSetEvents(ClanUi.EVENTS_EXIT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.EVENTS_CREATE, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.EVENTS_EDIT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.EVENTS_LIST, 0..99, IfEvent.Op1)
    }

    private fun Player.enableEventEditorEvents() =
        ClanUi.EVENT_EDITOR_BUTTONS.forEach { ifSetEvents(it, -1..-1, IfEvent.Op1) }

    private fun Player.enableApplicantEvents() {
        ClanUi.APPLICANT_BUTTONS.forEach { ifSetEvents(it, -1..-1, IfEvent.Op1) }
        ifSetEvents(ClanUi.APPLICANTS_COMBAT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.APPLICANTS_TOTAL, -1..-1, IfEvent.Op1)
        repeat(ClanUi.APPLICANT_ROWS) { ifSetEvents(ClanUi.applicantRow(it), -1..-1, IfEvent.Op1) }
    }

    private fun Player.enableHallEvents() {
        ifSetEvents(ClanUi.HALL_BACK, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.HALL_SAVE, -1..-1, IfEvent.Op1)
    }

    private fun Player.enablePermissionEvents() {
        ifSetEvents(ClanUi.PERMISSIONS_EXIT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.PERMISSIONS_LIST, 0 until ClanUi.PERMISSION_COMPONENT_COUNT, IfEvent.Op1)
    }

    private fun Player.enableTitleEvents() {
        ifSetEvents(ClanUi.TITLES_CANCEL, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.TITLES_SAVE, -1..-1, IfEvent.Op1)
    }

    private fun Player.enableBanEvents() {
        ifSetEvents(ClanUi.BANNED_EXIT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BANNED_SEARCH, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BANNED_ADD, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BANNED_LIST, 0..99, IfEvent.Op1)
    }

    private fun Player.enableInterestEvents() {
        ifSetEvents(ClanUi.INTERESTS_CANCEL, -1..-1, IfEvent.Op1)
        ifSetEvents(
            ClanUi.INTERESTS_LIST,
            0 until ClanUi.INTEREST_COMPONENT_COUNT,
            IfEvent.Op1,
        )
        ifSetEvents(ClanUi.INTERESTS_SAVE, -1..-1, IfEvent.Op1)
    }

    private fun Player.enableOutfitEvents() {
        ifSetEvents(ClanUi.OUTFIT_CANCEL, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_SAVE, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_TOP_LEFT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_TOP_RIGHT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_BOTTOM_LEFT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_BOTTOM_RIGHT, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_COLOURS, 0..7, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_ICON_TOP, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.OUTFIT_ICON_BOTTOM, -1..-1, IfEvent.Op1)
    }

    private fun Player.enableBoardEvents() {
        ifSetEvents(ClanUi.BOARD_RESET, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BOARD_SORT_NAME, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BOARD_SORT_SIZE, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BOARD_SORT_AGE, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BOARD_FILTERS, 0..63, IfEvent.Op1)
        ifSetEvents(ClanUi.BOARD_MY_CLAN, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BOARD_APPLICANTS, -1..-1, IfEvent.Op1)
        ifSetEvents(ClanUi.BOARD_REFRESH, -1..-1, IfEvent.Op1)
        repeat(ClanUi.BOARD_ROWS) {
            ifSetEvents(ClanUi.boardRow(it), -1..-1, IfEvent.Op1, IfEvent.Op2)
        }
    }
}

internal object ClanUi {
    const val INFO = "interface.clans_info"
    const val MEMBERS = "interface.clans_members"
    const val EVENTS = "interface.clans_events"
    const val EVENT_EDITOR = "interface.clans_events_create"
    const val APPLICANTS = "interface.clans_applicants"
    const val HALL = "interface.clans_hall"
    const val PERMISSIONS = "interface.clans_permissions"
    const val RANK_TITLES = "interface.clans_ranktitles"
    const val BANNED = "interface.clans_banned"
    const val INTERESTS = "interface.clans_interests"
    const val OUTFIT = "interface.clans_outfit"
    const val BOARD = "interface.clans_board"

    const val OWN_INFO = "component.clans_sidepanel:info"
    const val GUEST_INFO = "component.clans_guest_sidepanel:info"
    const val GUEST_SEARCH = "component.clans_guest_sidepanel:search"
    const val INFO_CLOSE = "component.clans_info:close"
    const val INFO_OUTFIT = "component.clans_info:outfit"
    const val INFO_NAME = "component.clans_info:name"
    const val INFO_HOMEWORLD = "component.clans_info:homeworld"
    const val INFO_ABDICATION = "component.clans_info:abdication"
    const val INFO_INTERESTS = "component.clans_info:interests"
    const val INFO_TALK_RANK = "component.clans_info:talkrank"
    const val INFO_ALLOW_GUESTS = "component.clans_info:allowunaffined"
    const val INFO_EVENTS = "component.clans_info:events"
    const val INFO_MEMBERS = "component.clans_info:members"
    const val INFO_APPLICATIONS = "component.clans_info:applications"
    const val INFO_HALL = "component.clans_info:hall"
    const val INFO_PERMISSIONS = "component.clans_info:permissions"
    const val INFO_TITLES = "component.clans_info:titles"
    const val INFO_BANNED = "component.clans_info:banned"
    const val INFO_LEAVE = "component.clans_info:quit"

    const val MEMBERS_EXIT = "component.clans_members:exit"
    const val MEMBERS_LIST = "component.clans_members:scrollcontents"
    const val MEMBERS_SEARCH = "component.clans_members:search"
    const val MEMBERS_MIN_RANK = "component.clans_members:minrank"
    const val MEMBERS_MAX_RANK = "component.clans_members:maxrank"
    const val EVENTS_EXIT = "component.clans_events:exit"
    const val EVENTS_CREATE = "component.clans_events:create"
    const val EVENTS_EDIT = "component.clans_events:edit"
    const val EVENTS_LIST = "component.clans_events:list_contents"
    const val EVENT_EDITOR_CREATE = "component.clans_events_create:create"
    const val EVENT_EDITOR_DELETE = "component.clans_events_create:delete"
    const val EVENT_EDITOR_EXIT = "component.clans_events_create:exit"
    const val EVENT_EDITOR_TYPE = "component.clans_events_create:type"
    const val EVENT_EDITOR_SUBTYPE = "component.clans_events_create:subtype"
    const val EVENT_EDITOR_ACTIVITY = "component.clans_events_create:activity"
    const val EVENT_EDITOR_DATE = "component.clans_events_create:date"
    const val EVENT_EDITOR_TIME = "component.clans_events_create:time"
    const val EVENT_EDITOR_DURATION = "component.clans_events_create:duration"
    const val EVENT_EDITOR_WORLD = "component.clans_events_create:world"
    const val EVENT_EDITOR_RANK = "component.clans_events_create:rank_to_view"
    const val APPLICANTS_RECRUIT = "component.clans_applicants:recruit"
    const val APPLICANTS_EXIT = "component.clans_applicants:exit"
    const val APPLICANTS_REFRESH = "component.clans_applicants:refresh"
    const val APPLICANTS_REJECT = "component.clans_applicants:reject"
    const val APPLICANTS_ACCEPT = "component.clans_applicants:accept"
    const val APPLICANTS_COMBAT = "component.clans_applicants:combatlevel"
    const val APPLICANTS_TOTAL = "component.clans_applicants:skilltotal"
    const val HALL_BACK = "component.clans_hall:back"
    const val HALL_SAVE = "component.clans_hall:save"
    const val PERMISSIONS_EXIT = "component.clans_permissions:exit"
    const val PERMISSIONS_INDEX = "component.clans_permissions:index_contents"
    const val PERMISSIONS_LIST = "component.clans_permissions:list_contents"
    val PERMISSION_ROWS = ClanNativePermissionRows.ALL
    const val TITLES_CANCEL = "component.clans_ranktitles:cancel"
    const val TITLES_SAVE = "component.clans_ranktitles:save"
    const val BANNED_EXIT = "component.clans_banned:exit"
    const val BANNED_SEARCH = "component.clans_banned:search"
    const val BANNED_ADD = "component.clans_banned:add"
    const val BANNED_LIST = "component.clans_banned:list_contents"
    const val INTERESTS_CANCEL = "component.clans_interests:cancel"
    const val INTERESTS_LIST = "component.clans_interests:list"
    const val INTERESTS_SAVE = "component.clans_interests:submit"
    const val INTERESTS_OPEN_SCRIPT = 4263
    const val INTERESTS_EDITING_VARP = 3023
    const val OUTFIT_CANCEL = "component.clans_outfit:cancel"
    const val OUTFIT_SAVE = "component.clans_outfit:save"
    const val OUTFIT_TOP_LEFT = "component.clans_outfit:arrow_top_left"
    const val OUTFIT_TOP_RIGHT = "component.clans_outfit:arrow_top_right"
    const val OUTFIT_BOTTOM_LEFT = "component.clans_outfit:arrow_bottom_left"
    const val OUTFIT_BOTTOM_RIGHT = "component.clans_outfit:arrow_bottom_right"
    const val OUTFIT_COLOURS = "component.clans_outfit:colour_list"
    const val OUTFIT_ICON_TOP = "component.clans_outfit:icon_top"
    const val OUTFIT_ICON_BOTTOM = "component.clans_outfit:icon_bottom"
    const val OUTFIT_OPEN_SCRIPT = 4431
    const val OUTFIT_REDRAW_SCRIPT = 4434
    const val OUTFIT_EDITING_VARP = 3023
    const val BOARD_MY_CLAN = "component.clans_board:myclan"
    const val BOARD_APPLICANTS = "component.clans_board:applicants"
    const val BOARD_REFRESH = "component.clans_board:refresh"
    const val BOARD_RESET = "component.clans_board:reset"
    const val BOARD_SORT_NAME = "component.clans_board:sort_name"
    const val BOARD_SORT_SIZE = "component.clans_board:sort_size"
    const val BOARD_SORT_AGE = "component.clans_board:sort_age"
    const val BOARD_FILTERS = "component.clans_board:filters"

    const val BOARD_ADD_ROW_SCRIPT = 4357
    const val APPLICANTS_ADD_ROW_SCRIPT = 4376
    const val APPLICANTS_DETAILS_SCRIPT = 4375
    const val EVENTS_ADD_ROW_SCRIPT = 4407
    const val EVENT_EDIT_RECRUITMENT_BLOCKED =
        "Stop inviting applications before entering Event Edit Mode."
    const val BOARD_ROWS = 45
    const val APPLICANT_ROWS = 20

    val INFO_BUTTONS =
        listOf(
            INFO_CLOSE,
            INFO_OUTFIT,
            INFO_NAME,
            INFO_HOMEWORLD,
            INFO_ABDICATION,
            INFO_INTERESTS,
            INFO_TALK_RANK,
            INFO_ALLOW_GUESTS,
            INFO_EVENTS,
            INFO_MEMBERS,
            INFO_APPLICATIONS,
            INFO_HALL,
            INFO_PERMISSIONS,
            INFO_TITLES,
            INFO_BANNED,
            INFO_LEAVE,
        )
    val EVENT_EDITOR_BUTTONS =
        listOf(
            EVENT_EDITOR_CREATE,
            EVENT_EDITOR_DELETE,
            EVENT_EDITOR_EXIT,
            EVENT_EDITOR_TYPE,
            EVENT_EDITOR_SUBTYPE,
            EVENT_EDITOR_ACTIVITY,
            EVENT_EDITOR_DATE,
            EVENT_EDITOR_TIME,
            EVENT_EDITOR_DURATION,
            EVENT_EDITOR_WORLD,
            EVENT_EDITOR_RANK,
        )
    val APPLICANT_BUTTONS =
        listOf(
            APPLICANTS_RECRUIT,
            APPLICANTS_EXIT,
            APPLICANTS_REFRESH,
            APPLICANTS_REJECT,
            APPLICANTS_ACCEPT,
        )

    fun boardRow(index: Int): String = "component.clans_board:${index.toString().padStart(2, '0')}"

    fun applicantRow(index: Int): String =
        "component.clans_applicants:${index.toString().padStart(2, '0')}"

    /* Clientscript 4263 creates three dynamic children per interest; the first owns Change. */
    fun interestAt(clientIndex: Int): ClanInterest? {
        if (clientIndex < 0 || clientIndex % INTEREST_DYNAMIC_CHILDREN != 0) return null
        val storageId = clientIndex / INTEREST_DYNAMIC_CHILDREN
        return ClanInterest.entries.firstOrNull { it.storageId == storageId }
    }

    private const val INTEREST_DYNAMIC_CHILDREN = 3
    val INTEREST_COMPONENT_COUNT = ClanInterest.entries.size * INTEREST_DYNAMIC_CHILDREN

    /* Clientscript 4463 builds eight dynamic children for every permission leaf. */
    fun permissionRow(clientIndex: Int): ClanNativePermissionRow? {
        if (clientIndex !in 0 until PERMISSION_ROWS.size * PERMISSION_DYNAMIC_CHILDREN) return null
        return PERMISSION_ROWS[clientIndex / PERMISSION_DYNAMIC_CHILDREN]
    }

    private const val PERMISSION_DYNAMIC_CHILDREN = 8
    const val PERMISSION_COMPONENT_COUNT = 54 * PERMISSION_DYNAMIC_CHILDREN
}

internal enum class ClanUiScope {
    Own,
    Guest,
}

internal enum class ClanDirectorySort {
    Name,
    Size,
    Age,
}

internal data class ClanUiV4Session(
    var scope: ClanUiScope = ClanUiScope.Own,
    var query: String = "",
    var directory: List<ClanDirectoryEntry> = emptyList(),
    var directorySource: List<ClanDirectoryEntry> = emptyList(),
    var directoryProfiles: Map<Long, ClanProfile> = emptyMap(),
    var directoryInterest: ClanInterest? = null,
    var directorySort: ClanDirectorySort = ClanDirectorySort.Name,
    var profile: ClanProfile? = null,
    var members: List<ClanMemberSnapshot> = emptyList(),
    var filteredMembers: List<ClanMemberSnapshot> = emptyList(),
    var memberQuery: String = "",
    var minimumRank: Int = ClanRanks.GUEST,
    var maximumRank: Int = ClanRanks.OWNER,
    var applicants: List<ClanApplication> = emptyList(),
    var selectedApplicant: Int = -1,
    var events: List<ClanEventRecord> = emptyList(),
    var selectedEvent: Int = -1,
    var eventDraft: ClanEventDraft? = null,
    var permissions: ClanPermissionSet? = null,
    var hall: ClanHallSettings? = null,
    var titles: Map<Int, String>? = null,
    var domainSettings: ClanDomainSettings? = null,
    var interestDraft: Set<ClanInterest>? = null,
    var motif: ClanMotif? = null,
    var motifDraft: ClanMotif? = null,
    var bans: List<ClanBanSnapshot> = emptyList(),
    var filteredBans: List<ClanBanSnapshot> = emptyList(),
    var banQuery: String = "",
)

internal fun ClanUiV4Session.visibleMemberAt(index: Int): ClanMemberSnapshot? =
    filteredMembers.getOrNull(index)

@Singleton
public class ClanNativeUiV4Sessions {
    private val values = ConcurrentHashMap<Int, ClanUiV4Session>()

    private fun newSession(): ClanUiV4Session = ClanUiV4Session()

    internal fun forCharacter(characterId: Int): ClanUiV4Session =
        values.computeIfAbsent(characterId) { newSession() }

    public fun clear(characterId: Int) {
        values.remove(characterId)
    }
}

private fun Boolean.toInt(): Int = if (this) 1 else 0

private fun rankBetween(rank: Int, minimum: Int, maximum: Int): Boolean {
    val actual = ClanRanks.hierarchyIndex(rank) ?: return false
    val low = ClanRanks.hierarchyIndex(minimum) ?: return false
    val high = ClanRanks.hierarchyIndex(maximum) ?: return false
    return actual in low..high
}

private fun yesNo(input: String, fallback: Boolean): Boolean =
    when (input.trim().uppercase()) {
        "YES",
        "Y",
        "ON",
        "TRUE" -> true
        "NO",
        "N",
        "OFF",
        "FALSE" -> false
        else -> fallback
    }

private fun ClanDirectoryEntry.boardPayload(): String =
    listOf(
            displayName,
            ownerDisplayName,
            memberCount.toString(),
            if (recruitmentOpen) "Recruiting" else "Closed",
            description,
            "",
            "",
        )
        .joinToString("|")

internal fun ClanApplication.nativeApplicantName(): String = displayName

internal fun ClanApplication.nativeApplicantDetails(): String =
    buildList {
            add(displayName)
            add(combatLevel?.toString().orEmpty())
            add(totalLevel?.toString().orEmpty())
            addAll(List(24) { index -> skillLevels.getOrNull(index)?.toString().orEmpty() })
        }
        .joinToString("|")

private fun ClanMotif.nativeEditorPacked(): Int = ClanMotifCodec.encode(this)

internal fun Set<ClanInterest>.nativeInterestMask(): Int =
    fold(0) { mask, interest -> mask or (1 shl interest.storageId) }

private fun ClanEventRecord.eventPayload(): String {
    val start = Instant.ofEpochMilli(startsAtEpochMillis).atZone(ZoneOffset.UTC)
    val activity = ClanEventActivities.find(activityKey)?.displayName ?: activityKey
    val durationDays = (durationTicks / ClanDomainLimits.GAME_TICKS_PER_DAY).coerceIn(1L, 30L)
    return listOf(
            start.toLocalDate().toString(),
            "%02d:%02d UTC".format(start.hour, start.minute),
            durationDays.toString(),
            worldId.toString(),
            activity,
            type.name,
            subtype.name,
            "",
            minimumJoinRank.toString(),
        )
        .joinToString("|")
}
