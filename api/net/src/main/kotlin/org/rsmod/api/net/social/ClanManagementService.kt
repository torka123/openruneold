package org.rsmod.api.net.social

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.rsmod.annotations.InternalApi
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.ironman.isHardcoreIronman
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.social.writeSocialMessage
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

private var Player.clanApplicantQuestPoints by intVarp("varp.qp")

@Singleton
public class ClanManagementService
@Inject
constructor(
    private val db: GameDbManager,
    private val repository: ClanManagementRepository,
    private val clanChat: ClanChatService,
    private val playerList: PlayerList,
    private val serverConfig: ServerConfig,
) {
    private val drafts = ConcurrentHashMap<Int, Draft>()
    private val founderInvites = ConcurrentHashMap<Int, Int>()

    public suspend fun beginDraft(
        owner: Player,
        requestedName: String,
    ): ClanManagementResult<ClanCreationDraftView> {
        founderPlaytimeError(owner)?.let {
            return ClanManagementResult.Denied(it)
        }
        when (
            val eligibility = requestResult {
                repository.canFoundClan(it, owner.characterId, requestedName)
            }
        ) {
            is ClanManagementResult.Denied -> return eligibility
            is ClanManagementResult.Success -> Unit
        }
        val founder = owner.toFounder()
        val draft = Draft(SocialNameNormalizer.channelName(requestedName), founder)
        cancelDraft(owner.characterId)
        drafts[owner.characterId] = draft
        return ClanManagementResult.Success(draft.view())
    }

    public fun draft(owner: Player): ClanCreationDraftView? = drafts[owner.characterId]?.view()

    public suspend fun inviteFounder(
        owner: Player,
        requestedName: String,
    ): ClanManagementResult<ClanCreationDraftView> {
        val draft =
            drafts[owner.characterId]
                ?: return ClanManagementResult.Denied("Start a clan before inviting founders.")
        if (draft.accepted.size + draft.pending.size + 1 >= ClanLimits.MAX_FOUNDING_MEMBER_COUNT) {
            return ClanManagementResult.Denied("Your founding group is already full.")
        }
        val target =
            findOnline(requestedName)
                ?: return ClanManagementResult.Denied(
                    "That player must be online to accept a founder invite."
                )
        if (target.characterId == owner.characterId) {
            return ClanManagementResult.Denied("You are already the founding owner.")
        }
        if (draft.contains(target.characterId)) {
            return ClanManagementResult.Denied("That player is already in your founding group.")
        }
        founderPlaytimeError(target)?.let {
            return ClanManagementResult.Denied(it)
        }
        if (founderInvites[target.characterId] != null) {
            return ClanManagementResult.Denied("That player already has a pending founder invite.")
        }
        when (val eligibility = requestResult { repository.canFoundClan(it, target.characterId) }) {
            is ClanManagementResult.Denied -> return eligibility
            is ClanManagementResult.Success -> Unit
        }
        val currentTarget =
            findOnlineById(target.characterId)
                ?: return ClanManagementResult.Denied(
                    "That player logged out before the invite was sent."
                )
        val founder = currentTarget.toFounder()
        draft.pending[founder.characterId] = founder
        founderInvites[founder.characterId] = owner.characterId
        currentTarget.writeSocialMessage(
            "${owner.displayName} invited you to found clan '${draft.requestedName}'. Talk to the Clan Hub clerk to accept."
        )
        return ClanManagementResult.Success(draft.view())
    }

    public fun pendingFounderInvite(player: Player): String? {
        val ownerId = founderInvites[player.characterId] ?: return null
        val draft = drafts[ownerId] ?: return null
        return "${draft.owner.displayName}'s clan '${draft.requestedName}'"
    }

    public suspend fun acceptFounderInvite(
        player: Player
    ): ClanManagementResult<ClanCreationDraftView> {
        val ownerId =
            founderInvites[player.characterId]
                ?: return ClanManagementResult.Denied("You do not have a pending founder invite.")
        val draft =
            drafts[ownerId]
                ?: return ClanManagementResult.Denied("That founding group no longer exists.")
        founderPlaytimeError(player)?.let {
            return ClanManagementResult.Denied(it)
        }
        when (val eligibility = requestResult { repository.canFoundClan(it, player.characterId) }) {
            is ClanManagementResult.Denied -> return eligibility
            is ClanManagementResult.Success -> Unit
        }
        val founder = player.toFounder()
        draft.pending.remove(player.characterId)
        draft.accepted[player.characterId] = founder
        founderInvites.remove(player.characterId)
        findOnlineById(ownerId)
            ?.writeSocialMessage("${player.displayName} accepted your founding invitation.")
        return ClanManagementResult.Success(draft.view())
    }

    public fun declineFounderInvite(player: Player) {
        val ownerId = founderInvites.remove(player.characterId) ?: return
        drafts[ownerId]?.pending?.remove(player.characterId)
        findOnlineById(ownerId)
            ?.writeSocialMessage("${player.displayName} declined your founding invitation.")
    }

    public fun removeFounder(
        owner: Player,
        requestedName: String,
    ): ClanManagementResult<ClanCreationDraftView> {
        val draft =
            drafts[owner.characterId]
                ?: return ClanManagementResult.Denied("You do not have a founding group.")
        val normalized = SocialNameNormalizer.normalize(requestedName)
        val founder =
            (draft.pending.values + draft.accepted.values).firstOrNull {
                SocialNameNormalizer.normalize(it.displayName) == normalized
            } ?: return ClanManagementResult.Denied("That player is not in your founding group.")
        draft.pending.remove(founder.characterId)
        draft.accepted.remove(founder.characterId)
        founderInvites.remove(founder.characterId, owner.characterId)
        return ClanManagementResult.Success(draft.view())
    }

    public fun cancelDraft(ownerId: Int) {
        val old = drafts.remove(ownerId) ?: return
        for (founderId in old.pending.keys) founderInvites.remove(founderId, ownerId)
    }

    public suspend fun confirmClan(owner: Player): ClanManagementResult<ClanSnapshot> {
        val draft =
            drafts[owner.characterId]
                ?: return ClanManagementResult.Denied("You do not have a founding group.")
        val founderIds = listOf(owner.characterId) + draft.accepted.keys
        val founders = ArrayList<ClanFounder>(founderIds.size)
        for (characterId in founderIds) {
            val online =
                findOnlineById(characterId)
                    ?: return ClanManagementResult.Denied(
                        "Every accepted founder must be online when the clan is created."
                    )
            founderPlaytimeError(online)?.let {
                return ClanManagementResult.Denied(it)
            }
            founders += online.toFounder()
        }
        val result = requestResult {
            repository.createClan(
                it,
                ClanCreationRequest(
                    requestedName = draft.requestedName,
                    ownerCharacterId = owner.characterId,
                    founders = founders,
                    homeWorld = serverConfig.world,
                ),
            )
        }
        if (result is ClanManagementResult.Success) {
            cancelDraft(owner.characterId)
            for (founder in founders) {
                findOnlineById(founder.characterId)?.let(clanChat::prepareLogin)
            }
        }
        return result
    }

    public suspend fun searchDirectory(
        query: String
    ): ClanManagementResult<List<ClanDirectoryEntry>> = requestValue {
        repository.searchDirectory(it, query)
    }

    public suspend fun profile(name: String): ClanManagementResult<ClanProfile?> = requestValue {
        repository.loadProfile(it, name)
    }

    public suspend fun ownProfile(characterId: Int): ClanManagementResult<ClanProfile?> =
        requestValue {
            repository.loadOwnProfile(it, characterId)
        }

    /** OSRS only exposes combat and total level from the Members dropdown on the same world. */
    @OptIn(InternalApi::class)
    public fun memberForDisplay(member: ClanMemberSnapshot): ClanMemberSnapshot {
        val online = findOnlineById(member.characterId)
        if (online == null || member.worldId != serverConfig.world) {
            return member.copy(combatLevel = null, totalLevel = null)
        }
        return member.copy(
            combatLevel = online.combatLevel,
            totalLevel = online.recruitmentSkillLevels().sum(),
        )
    }

    public suspend fun submitApplication(
        player: Player,
        clanName: String,
        message: String,
    ): ClanManagementResult<ClanProfile> {
        val skillLevels = player.recruitmentSkillLevels()
        val combatLevel = player.combatLevel
        val questPoints = player.clanApplicantQuestPoints.coerceAtLeast(0)
        val isIron = player.isAnyIronman
        val isUltimateIron = player.isUltimateIronman
        val isHardcoreIron = player.isHardcoreIronman
        return requestResult {
            repository.submitApplication(
                it,
                player.characterId,
                clanName,
                message,
                combatLevel,
                skillLevels.sum(),
                questPoints,
                isIron,
                isUltimateIron,
                isHardcoreIron,
                skillLevels,
            )
        }
    }

    public suspend fun applications(player: Player): ClanManagementResult<List<ClanApplication>> =
        requestResult {
            repository.listApplications(it, player.characterId)
        }

    public suspend fun reviewApplication(
        actor: Player,
        targetName: String,
        approve: Boolean,
    ): ClanManagementResult<ClanSnapshot> {
        val result = requestResult {
            repository.reviewApplication(it, actor.characterId, targetName, approve)
        }
        if (result is ClanManagementResult.Success) {
            val target = findOnline(targetName)
            if (approve) {
                result.value.members
                    .firstOrNull {
                        SocialNameNormalizer.normalize(it.displayName) ==
                            SocialNameNormalizer.normalize(targetName)
                    }
                    ?.let { member ->
                        clanChat.publishSystemBroadcastToClan(
                            clanId = result.value.clanId,
                            actorCharacterId = member.characterId,
                            actorName = member.displayName,
                            actorCrown = target?.modLevel?.clientCode ?: 0,
                            eventToken =
                                "membership:join:${member.characterId}:${result.value.stateVersion}",
                            message = "has joined the clan.",
                        )
                    }
            }
            clanChat.reloadClan(result.value.clanId, actor)
            target?.let(clanChat::prepareLogin)
        }
        return result
    }

    public suspend fun inviteMember(
        actor: Player,
        targetName: String,
    ): ClanManagementResult<ClanInvite> {
        val result = requestResult {
            repository.inviteMember(
                it,
                actor.characterId,
                targetName,
                Instant.now().plus(INVITE_LIFETIME),
            )
        }
        if (result is ClanManagementResult.Success) {
            findOnline(targetName)
                ?.writeSocialMessage(
                    "${actor.displayName} invited you to join '${result.value.clanName}'. Talk to the Clan Hub clerk to accept."
                )
        }
        return result
    }

    public suspend fun pendingInvites(player: Player): ClanManagementResult<List<ClanInvite>> =
        requestValue {
            repository.pendingInvites(it, player.characterId)
        }

    public suspend fun acceptInvite(
        player: Player,
        clanName: String,
    ): ClanManagementResult<ClanSnapshot> {
        val result = requestResult { repository.acceptInvite(it, player.characterId, clanName) }
        if (result is ClanManagementResult.Success) {
            clanChat.publishSystemBroadcastToClan(
                clanId = result.value.clanId,
                actorCharacterId = player.characterId,
                actorName = player.displayName.ifBlank { player.username },
                actorCrown = player.modLevel.clientCode,
                eventToken = "membership:join:${player.characterId}:${result.value.stateVersion}",
                message = "has joined the clan.",
            )
            clanChat.prepareLogin(player)
            clanChat.reloadClan(result.value.clanId, player)
        }
        return result
    }

    public suspend fun setRank(
        actor: Player,
        targetName: String,
        rank: Int,
    ): ClanManagementResult<ClanSnapshot> {
        val result = requestResult {
            repository.setMemberRank(it, actor.characterId, targetName, rank)
        }
        if (result is ClanManagementResult.Success) clanChat.reloadClan(result.value.clanId, actor)
        return result
    }

    public suspend fun updateProfile(
        actor: Player,
        description: String,
        recruitmentOpen: Boolean,
        applicationsOpen: Boolean,
        allowGuests: Boolean,
        guestsCanTalk: Boolean,
        minimumCombatLevel: Int,
        minimumTotalLevel: Int,
    ): ClanManagementResult<ClanProfile> {
        val result = requestResult {
            repository.updateProfile(
                it,
                actor.characterId,
                description,
                recruitmentOpen,
                applicationsOpen,
                allowGuests,
                guestsCanTalk,
                minimumCombatLevel,
                minimumTotalLevel,
            )
        }
        if (result is ClanManagementResult.Success) clanChat.reloadClan(result.value.clanId, actor)
        return result
    }

    public suspend fun leaveClan(player: Player): ClanManagementResult<Long> {
        val result = requestResult { repository.leaveClan(it, player.characterId) }
        if (result is ClanManagementResult.Success) {
            clanChat.publishSystemBroadcastToClan(
                clanId = result.value,
                actorCharacterId = player.characterId,
                actorName = player.displayName.ifBlank { player.username },
                actorCrown = player.modLevel.clientCode,
                eventToken =
                    "membership:leave:${player.characterId}:${Instant.now().toEpochMilli()}",
                message = "has left the clan.",
            )
            clanChat.prepareLogin(player)
            clanChat.reloadClan(result.value, player)
        }
        return result
    }

    public suspend fun unban(
        actor: Player,
        targetName: String,
    ): ClanManagementResult<ClanSnapshot> {
        val result = requestResult { repository.unban(it, actor.characterId, targetName) }
        if (result is ClanManagementResult.Success) clanChat.reloadClan(result.value.clanId, actor)
        return result
    }

    private suspend fun <T> requestResult(
        request: (DatabaseConnection) -> ClanManagementResult<T>
    ): ClanManagementResult<T> = suspendCoroutine { continuation ->
        db.request(
            request = { connection -> GameDbResult.Ok(request(connection)) },
            response = { result ->
                continuation.resume(
                    result.fold(
                        onOk = { it },
                        onErr = {
                            ClanManagementResult.Denied(
                                "The clan service is temporarily unavailable."
                            )
                        },
                    )
                )
            },
        )
    }

    private suspend fun <T> requestValue(
        request: (DatabaseConnection) -> T
    ): ClanManagementResult<T> = suspendCoroutine { continuation ->
        db.request(
            request = { connection -> GameDbResult.Ok(request(connection)) },
            response = { result ->
                continuation.resume(
                    result.fold(
                        onOk = { ClanManagementResult.Success(it) },
                        onErr = {
                            ClanManagementResult.Denied(
                                "The clan service is temporarily unavailable."
                            )
                        },
                    )
                )
            },
        )
    }

    private fun findOnline(requestedName: String): Player? {
        val normalized = SocialNameNormalizer.normalize(requestedName)
        if (normalized.isBlank()) return null
        return playerList.firstOrNull {
            SocialNameNormalizer.normalize(it.displayName) == normalized ||
                SocialNameNormalizer.normalize(it.previousDisplayName) == normalized ||
                SocialNameNormalizer.normalize(it.username) == normalized
        }
    }

    private fun findOnlineById(characterId: Int): Player? =
        playerList.firstOrNull { it.characterId == characterId }

    private fun founderPlaytimeError(player: Player): String? =
        if (player.vars[PLAYTIME_VARP] < ClanLimits.MINIMUM_FOUNDER_PLAYTIME_TICKS) {
            "${player.displayName} needs at least 25 hours of playtime."
        } else {
            null
        }

    private fun Player.toFounder(): ClanFounder =
        ClanFounder(characterId, displayName.ifBlank { username }, vars[PLAYTIME_VARP])

    @OptIn(InternalApi::class)
    private fun Player.recruitmentSkillLevels(): List<Int> =
        RECRUITMENT_STATS.map { statMap.getBaseLevel(it).toInt() }

    private data class Draft(
        var requestedName: String,
        val owner: ClanFounder,
        val accepted: LinkedHashMap<Int, ClanFounder> = LinkedHashMap(),
        val pending: LinkedHashMap<Int, ClanFounder> = LinkedHashMap(),
    ) {
        fun contains(characterId: Int): Boolean =
            owner.characterId == characterId || characterId in accepted || characterId in pending

        fun view(): ClanCreationDraftView =
            ClanCreationDraftView(
                requestedName,
                owner,
                accepted.values.toList(),
                pending.values.toList(),
            )
    }

    private companion object {
        private const val PLAYTIME_VARP = "varp.playtime"
        private val INVITE_LIFETIME: Duration = Duration.ofDays(7)
        private val RECRUITMENT_STATS =
            listOf(
                "stat.attack",
                "stat.defence",
                "stat.strength",
                "stat.hitpoints",
                "stat.ranged",
                "stat.prayer",
                "stat.magic",
                "stat.cooking",
                "stat.woodcutting",
                "stat.fletching",
                "stat.fishing",
                "stat.firemaking",
                "stat.crafting",
                "stat.smithing",
                "stat.mining",
                "stat.herblore",
                "stat.agility",
                "stat.thieving",
                "stat.slayer",
                "stat.farming",
                "stat.runecrafting",
                "stat.hunter",
                "stat.construction",
                "stat.sailing",
            )
    }
}
