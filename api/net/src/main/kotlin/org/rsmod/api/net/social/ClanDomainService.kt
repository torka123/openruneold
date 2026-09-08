package org.rsmod.api.net.social

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.game.entity.Player

@Singleton
public class ClanDomainService
@Inject
constructor(
    private val db: GameDbManager,
    private val repository: ClanDomainRepository,
    private val clanChat: ClanChatService,
) {
    public suspend fun renameClan(
        player: Player,
        newName: String,
        now: Instant = Instant.now(),
    ): ClanManagementResult<String> =
        mutate(player) { repository.renameClan(it, player.characterId, newName, now) }

    public suspend fun permissions(player: Player): ClanManagementResult<ClanPermissionSet> =
        query {
            repository.getPermissions(it, player.characterId)
        }

    public suspend fun bans(player: Player): ClanManagementResult<List<ClanBanSnapshot>> = query {
        repository.getBans(it, player.characterId)
    }

    public suspend fun updateHomeWorld(player: Player, worldId: Int): ClanManagementResult<Int> =
        mutate(player) { repository.updateHomeWorld(it, player.characterId, worldId) }

    public suspend fun updatePermissions(
        player: Player,
        permissions: ClanPermissionSet,
    ): ClanManagementResult<ClanPermissionSet> =
        mutate(player) { repository.updatePermissions(it, player.characterId, permissions) }

    public suspend fun motif(clanId: Long): ClanManagementResult<ClanMotif> = query {
        repository.getMotif(it, clanId)
    }

    public suspend fun updateMotif(
        player: Player,
        motif: ClanMotif,
    ): ClanManagementResult<ClanMotif> =
        mutate(player) { repository.updateMotif(it, player.characterId, motif) }

    public suspend fun rankTitles(player: Player): ClanManagementResult<Map<Int, String>> = query {
        repository.getRankTitles(it, player.characterId)
    }

    public suspend fun rankTitles(clanId: Long): ClanManagementResult<Map<Int, String>> = query {
        repository.getRankTitlesByClan(it, clanId)
    }

    public suspend fun updateRankTitles(
        player: Player,
        titles: Map<Int, String>,
    ): ClanManagementResult<Map<Int, String>> =
        mutate(player) { repository.updateRankTitles(it, player.characterId, titles) }

    public suspend fun updateRankTitle(
        player: Player,
        slot: Int,
        titleId: Int,
    ): ClanManagementResult<Map<Int, String>> =
        mutate(player) { repository.updateRankTitle(it, player.characterId, slot, titleId) }

    public suspend fun events(player: Player): ClanManagementResult<List<ClanEventRecord>> = query {
        repository.listEvents(it, player.characterId)
    }

    public suspend fun events(clanId: Long): ClanManagementResult<List<ClanEventRecord>> = query {
        repository.listEventsByClan(it, clanId)
    }

    public suspend fun createEvent(
        player: Player,
        event: ClanEventDraft,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanEventRecord> =
        mutate(player) { repository.createEvent(it, player.characterId, event, now) }

    public suspend fun updateEvent(
        player: Player,
        eventId: Long,
        event: ClanEventDraft,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanEventRecord> =
        mutate(player) { repository.updateEvent(it, player.characterId, eventId, event, now) }

    public suspend fun deleteEvent(player: Player, eventId: Long): ClanManagementResult<Unit> =
        mutate(player) { repository.deleteEvent(it, player.characterId, eventId) }

    public suspend fun hallSettings(player: Player): ClanManagementResult<ClanHallSettings> =
        query {
            repository.getHallSettings(it, player.characterId)
        }

    public suspend fun beginHallEditSession(
        player: Player,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanHallSettings> =
        mutate(player) { repository.beginHallEditSession(it, player.characterId, now) }

    public suspend fun releaseHallEditSession(
        player: Player
    ): ClanManagementResult<ClanHallSettings> =
        mutate(player) { repository.releaseHallEditSession(it, player.characterId) }

    public suspend fun updateHallSettings(
        player: Player,
        settings: ClanHallSettings,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanHallSettings> =
        mutate(player) { repository.updateHallSettings(it, player.characterId, settings, now) }

    public suspend fun coffer(player: Player): ClanManagementResult<ClanCofferSnapshot> = query {
        repository.getCoffer(it, player.characterId)
    }

    public suspend fun depositCoffer(
        player: Player,
        amount: Long,
        requestId: UUID = UUID.randomUUID(),
    ): ClanManagementResult<ClanCofferSnapshot> =
        mutate(player) { repository.depositCoffer(it, player.characterId, amount, requestId) }

    public suspend fun withdrawCoffer(
        player: Player,
        amount: Long,
        requestId: UUID = UUID.randomUUID(),
    ): ClanManagementResult<ClanCofferSnapshot> =
        mutate(player) { repository.withdrawCoffer(it, player.characterId, amount, requestId) }

    public suspend fun partyChest(player: Player): ClanManagementResult<List<ClanPartyChestEntry>> =
        query {
            repository.listPartyChest(it, player.characterId)
        }

    public suspend fun addPartyChestItem(
        player: Player,
        itemId: Int,
        quantity: Long,
    ): ClanManagementResult<List<ClanPartyChestEntry>> =
        mutate(player) { repository.addPartyChestItem(it, player.characterId, itemId, quantity) }

    public suspend fun removePartyChestItem(
        player: Player,
        itemId: Int,
        quantity: Long,
    ): ClanManagementResult<List<ClanPartyChestEntry>> =
        mutate(player) { repository.removePartyChestItem(it, player.characterId, itemId, quantity) }

    public suspend fun pullPartyChestLever(
        player: Player,
        requestId: UUID = UUID.randomUUID(),
    ): ClanManagementResult<ClanPartyChestRelease> =
        mutate(player) { repository.pullPartyChestLever(it, player.characterId, requestId) }

    /** Server-owned Clan Hall lifecycle cleanup; not exposed as a player action. */
    public suspend fun archivePartyChestCleanup(
        clanId: Long,
        hallSessionId: String,
        reason: String,
    ): ClanManagementResult<Int> {
        val result = query {
            repository.archivePartyChestCleanup(it, clanId, hallSessionId, reason)
        }
        return when (result) {
            is ClanManagementResult.Denied -> result
            is ClanManagementResult.Success -> {
                clanChat.reloadClan(result.value.clanId)
                ClanManagementResult.Success(result.value.value)
            }
        }
    }

    public suspend fun broadcastSettings(
        player: Player
    ): ClanManagementResult<ClanBroadcastSettings> = query {
        repository.getBroadcastSettings(it, player.characterId)
    }

    public suspend fun updateBroadcastSettings(
        player: Player,
        settings: ClanBroadcastSettings,
    ): ClanManagementResult<ClanBroadcastSettings> =
        mutate(player) { repository.updateBroadcastSettings(it, player.characterId, settings) }

    public suspend fun notificationPreferences(
        player: Player
    ): ClanManagementResult<ClanMemberNotificationPreferences> = query {
        repository.getMemberNotificationPreferences(it, player.characterId)
    }

    public suspend fun updateNotificationPreferences(
        player: Player,
        preferences: ClanMemberNotificationPreferences,
    ): ClanManagementResult<ClanMemberNotificationPreferences> =
        mutate(player) {
            repository.updateMemberNotificationPreferences(it, player.characterId, preferences)
        }

    public suspend fun pendingRecruiterNotifications(
        player: Player,
        limit: Int = 50,
    ): ClanManagementResult<List<ClanRecruiterNotification>> = query {
        repository.listPendingRecruiterNotifications(it, player.characterId, limit)
    }

    public suspend fun markRecruiterNotificationsDelivered(
        player: Player,
        notificationIds: Set<Long>,
        now: Instant = Instant.now(),
    ): ClanManagementResult<Int> = query {
        repository.markRecruiterNotificationsDelivered(it, player.characterId, notificationIds, now)
    }

    public suspend fun domainSettings(player: Player): ClanManagementResult<ClanDomainSettings> =
        query {
            repository.getDomainSettings(it, player.characterId)
        }

    public suspend fun domainSettings(clanId: Long): ClanManagementResult<ClanDomainSettings> =
        query {
            repository.getDomainSettingsByClan(it, clanId)
        }

    public suspend fun updateDomainSettings(
        player: Player,
        settings: ClanDomainSettings,
    ): ClanManagementResult<ClanDomainSettings> =
        mutate(player) { repository.updateDomainSettings(it, player.characterId, settings) }

    public suspend fun withdrawApplication(
        player: Player,
        clanId: Long,
    ): ClanManagementResult<Unit> =
        mutate(player) { repository.withdrawApplication(it, player.characterId, clanId) }

    public suspend fun declineInvite(player: Player, clanId: Long): ClanManagementResult<Unit> =
        mutate(player) { repository.declineInvite(it, player.characterId, clanId) }

    public suspend fun successor(
        player: Player,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanOwnershipStatus> = query {
        repository.computeSuccessor(it, player.characterId, now)
    }

    public suspend fun requestSuccessorOwnership(
        player: Player,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanOwnershipStatus> =
        mutate(player) { repository.requestSuccessorOwnership(it, player.characterId, now) }

    public suspend fun finalizeSuccessorOwnership(
        player: Player,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanOwnershipFinalization> =
        mutate(player) { repository.finalizeSuccessorOwnership(it, player.characterId, now) }

    private suspend fun <T> query(
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

    private suspend fun <T> mutate(
        actor: Player,
        request: (DatabaseConnection) -> ClanManagementResult<ClanDomainMutation<T>>,
    ): ClanManagementResult<T> {
        val result = query(request)
        return when (result) {
            is ClanManagementResult.Denied -> result
            is ClanManagementResult.Success -> {
                clanChat.reloadClan(result.value.clanId, actor)
                ClanManagementResult.Success(result.value.value)
            }
        }
    }
}
