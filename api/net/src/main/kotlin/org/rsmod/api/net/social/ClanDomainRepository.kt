package org.rsmod.api.net.social

import jakarta.inject.Inject
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection

public data class ClanDomainMutation<T>(val clanId: Long, val value: T)

public data class ClanCanonicalName(val displayName: String, val normalizedName: String)

/** OSRS clan names are one to twelve letters, digits, or spaces; underscores enter as spaces. */
public object ClanNameRules {
    public const val MAX_LENGTH: Int = 12
    public val CHANGE_COOLDOWN: Duration = Duration.ofDays(7)

    public fun canonicalize(requestedName: String): ClanCanonicalName? {
        val replaced = requestedName.replace('_', ' ')
        if (replaced.any { !it.isLetterOrDigit() && it != ' ' }) return null
        val displayName =
            replaced.trim().split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ")
        if (displayName.isEmpty() || displayName.length > MAX_LENGTH) return null
        val normalizedName = SocialNameNormalizer.normalize(displayName)
        return normalizedName.takeIf(String::isNotEmpty)?.let { ClanCanonicalName(displayName, it) }
    }
}

public data class ClanEventRecord(
    val id: Long,
    val type: ClanEventType,
    val subtype: ClanEventSubtype,
    val activityKey: String,
    val startsAtEpochMillis: Long,
    val durationTicks: Long,
    val minimumJoinRank: Int,
    val worldId: Int,
)

public data class ClanCofferSnapshot(
    val clanId: Long,
    val balance: Long,
    val transactions: List<ClanCofferTransaction>,
)

public data class ClanPartyChestEntry(
    val itemId: Int,
    val quantity: Long,
    val depositedBy: Int?,
    val depositedAtEpochMillis: Long,
)

public data class ClanPartyChestRelease(
    val coffer: ClanCofferSnapshot,
    val entries: List<ClanPartyChestEntry>,
)

public data class ClanDomainSettings(
    val interests: Set<ClanInterest>,
    val guestRequirements: ClanGuestRequirements,
    val broadcastSettings: ClanBroadcastSettings,
)

public data class ClanOwnershipStatus(
    val clanId: Long,
    val ownerCharacterId: Int,
    val requestedAtEpochMillis: Long?,
    val availableAtEpochMillis: Long?,
    val successor: ClanSuccessionCandidate?,
)

public data class ClanOwnershipFinalization(
    val clanId: Long,
    val deleted: Boolean,
    val status: ClanOwnershipStatus?,
)

public class ClanDomainRepository @Inject constructor() {
    public fun renameClan(
        connection: DatabaseConnection,
        actorId: Int,
        requestedName: String,
        now: Instant,
    ): ClanManagementResult<ClanDomainMutation<String>> {
        val name =
            ClanNameRules.canonicalize(requestedName)
                ?: return denied("Clan names may contain up to 12 letters, numbers, or spaces.")
        val clan =
            connection
                .prepareStatement(
                    """
                SELECT c.id, c.normalized_name, c.name_changed_at
                FROM clans c
                JOIN clan_members m ON m.clan_id = c.id AND m.character_id = ?
                WHERE c.owner_character_id = ? AND m.rank = ?
                FOR UPDATE OF c, m
                """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, actorId)
                    statement.setInt(2, actorId)
                    statement.setInt(3, ClanRanks.OWNER)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) {
                            return denied("Only the clan Owner can change its name.")
                        }
                        ClanNameState(
                            rows.getLong("id"),
                            rows.getString("normalized_name"),
                            rows.getTimestamp("name_changed_at")?.toInstant(),
                        )
                    }
                }
        if (clan.normalizedName == name.normalizedName) {
            return denied("That is already your clan's name.")
        }
        val availableAt = clan.changedAt?.plus(ClanNameRules.CHANGE_COOLDOWN)
        if (availableAt != null && now.isBefore(availableAt)) {
            val remaining = Duration.between(now, availableAt)
            val days = (remaining.toHours() + 23L) / 24L
            return denied("You can change the clan name again in $days day(s).")
        }

        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use {
            it.setString(1, name.normalizedName)
            it.executeQuery().close()
        }
        val occupied =
            connection
                .prepareStatement("SELECT 1 FROM clans WHERE normalized_name = ? AND id <> ?")
                .use {
                    it.setString(1, name.normalizedName)
                    it.setLong(2, clan.id)
                    it.executeQuery().use(ResultSet::next)
                }
        if (occupied) return denied("That clan name is already in use.")

        connection
            .prepareStatement(
                """
            UPDATE clans
            SET normalized_name = ?, display_name = ?, name_changed_at = ?,
                state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setString(1, name.normalizedName)
                it.setString(2, name.displayName)
                it.setTimestamp(3, Timestamp.from(now))
                it.setLong(4, clan.id)
                check(it.executeUpdate() == 1)
            }
        return success(clan.id, name.displayName)
    }

    public fun getPermissions(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<ClanPermissionSet> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadPermissions(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun getBans(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<List<ClanBanSnapshot>> {
        val clanId =
            memberClanId(connection, actorId) ?: return denied("You are not a member of a clan.")
        val bans = ArrayList<ClanBanSnapshot>()
        connection
            .prepareStatement(
                """
            SELECT b.character_id, c.display_name
            FROM clan_bans b JOIN account_characters c ON c.id = b.character_id
            WHERE b.clan_id = ? ORDER BY LOWER(c.display_name)
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        bans += ClanBanSnapshot(rows.getInt(1), rows.getString(2))
                    }
                }
            }
        return ClanManagementResult.Success(bans)
    }

    public fun updateHomeWorld(
        connection: DatabaseConnection,
        actorId: Int,
        worldId: Int,
    ): ClanManagementResult<ClanDomainMutation<Int>> {
        if (worldId !in 1..Short.MAX_VALUE) return denied("That home world is not valid.")
        val actor = authorized(connection, actorId, ClanPermissionKey.EditPublicProfile)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        connection
            .prepareStatement(
                """
            UPDATE clans SET home_world = ?, state_version = state_version + 1,
                updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, worldId)
                it.setLong(2, actor.value.clanId)
                check(it.executeUpdate() == 1)
            }
        return success(actor.value.clanId, worldId)
    }

    public fun updatePermissions(
        connection: DatabaseConnection,
        actorId: Int,
        permissions: ClanPermissionSet,
    ): ClanManagementResult<ClanDomainMutation<ClanPermissionSet>> {
        validatePermissions(permissions)?.let {
            return denied(it)
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.PermissionAccess)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        connection
            .prepareStatement(
                """
            INSERT INTO clan_permission_settings (clan_id, permission_key, minimum_rank)
            VALUES (?, ?, ?) ON CONFLICT (clan_id, permission_key) DO UPDATE
            SET minimum_rank = EXCLUDED.minimum_rank, updated_at = CURRENT_TIMESTAMP
            """
                    .trimIndent()
            )
            .use { statement ->
                permissions.minimumRanks.forEach { (key, rank) ->
                    statement.setLong(1, actor.value.clanId)
                    statement.setString(2, key.storageKey)
                    statement.setInt(3, rank)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        connection
            .prepareStatement(
                """
            UPDATE clans SET talk_rank = ?, kick_rank = ?, ban_rank = ?, expel_rank = ?,
                assign_rank = ?, invite_rank = ?, application_review_rank = ?,
                settings_change_rank = ?, edit_permissions_rank = ?,
                updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, permissions[ClanPermissionKey.TalkInChat])
                statement.setInt(2, permissions[ClanPermissionKey.KickGuests])
                statement.setInt(3, permissions[ClanPermissionKey.BanGuests])
                statement.setInt(4, permissions[ClanPermissionKey.ExpelMembers])
                statement.setInt(5, permissions[ClanPermissionKey.AssignMemberRanks])
                statement.setInt(6, permissions[ClanPermissionKey.Recruitment])
                statement.setInt(7, permissions[ClanPermissionKey.Recruitment])
                statement.setInt(8, permissions[ClanPermissionKey.EditPublicProfile])
                statement.setInt(9, permissions[ClanPermissionKey.PermissionAccess])
                statement.setLong(10, actor.value.clanId)
                check(statement.executeUpdate() == 1)
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, permissions)
    }

    public fun getMotif(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanManagementResult<ClanMotif> =
        loadMotif(connection, clanId)?.let { ClanManagementResult.Success(it) }
            ?: denied("That clan does not exist.")

    public fun updateMotif(
        connection: DatabaseConnection,
        actorId: Int,
        motif: ClanMotif,
    ): ClanManagementResult<ClanDomainMutation<ClanMotif>> {
        if (ClanMotifCodec.decode(ClanMotifCodec.encode(motif)) != motif) {
            return denied("That clan motif is not valid.")
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.EditMotif)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        connection
            .prepareStatement(
                """
            UPDATE clans SET motif_primary_colour = ?, motif_secondary_colour = ?,
                motif_primary_symbol = ?, motif_secondary_symbol = ?,
                state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, motif.primaryColour.storageId)
                it.setInt(2, motif.primaryColour.storageId)
                it.setNullableInt(3, motif.primarySymbol?.storageId)
                it.setNullableInt(4, motif.secondarySymbol?.storageId)
                it.setLong(5, actor.value.clanId)
                it.executeUpdate()
            }
        return success(actor.value.clanId, motif)
    }

    public fun getRankTitles(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<Map<Int, String>> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadRankTitles(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun getRankTitlesByClan(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanManagementResult<Map<Int, String>> =
        if (loadMotif(connection, clanId) == null) {
            denied("That clan does not exist.")
        } else {
            ClanManagementResult.Success(loadRankTitles(connection, clanId))
        }

    public fun updateRankTitles(
        connection: DatabaseConnection,
        actorId: Int,
        titles: Map<Int, String>,
    ): ClanManagementResult<ClanDomainMutation<Map<Int, String>>> {
        if (titles.keys != (0 until ClanDomainLimits.CUSTOM_RANK_SLOTS).toSet()) {
            return denied("Every custom rank-title slot must be present.")
        }
        val selections =
            titles.mapValues { (_, titleName) ->
                val titleId =
                    ClanRankTitleCatalog.id(titleName)
                        ?: return denied("That is not a supported clan rank title.")
                titleId to checkNotNull(ClanRankTitleCatalog.name(titleId))
            }
        val actor = authorized(connection, actorId, ClanPermissionKey.EditRankTitles)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        connection
            .prepareStatement(
                """
            INSERT INTO clan_rank_titles (clan_id, rank_slot, title_id, title_name)
            VALUES (?, ?, ?, ?) ON CONFLICT (clan_id, rank_slot) DO UPDATE
            SET title_id = EXCLUDED.title_id, title_name = EXCLUDED.title_name
            """
                    .trimIndent()
            )
            .use { statement ->
                selections.toSortedMap().forEach { (slot, selection) ->
                    statement.setLong(1, actor.value.clanId)
                    statement.setInt(2, slot)
                    statement.setInt(3, selection.first)
                    statement.setString(4, selection.second)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, selections.mapValues { it.value.second }.toSortedMap())
    }

    public fun updateRankTitle(
        connection: DatabaseConnection,
        actorId: Int,
        slot: Int,
        titleId: Int,
    ): ClanManagementResult<ClanDomainMutation<Map<Int, String>>> {
        if (slot !in 0 until ClanDomainLimits.CUSTOM_RANK_SLOTS) {
            return denied("Rank-title slots are 0 through 23.")
        }
        val titleName =
            ClanRankTitleCatalog.name(titleId)
                ?: return denied("That is not a supported clan rank title.")
        val actor = authorized(connection, actorId, ClanPermissionKey.EditRankTitles)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        connection
            .prepareStatement(
                """
            INSERT INTO clan_rank_titles (clan_id, rank_slot, title_id, title_name)
            VALUES (?, ?, ?, ?) ON CONFLICT (clan_id, rank_slot) DO UPDATE
            SET title_id = EXCLUDED.title_id, title_name = EXCLUDED.title_name
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, actor.value.clanId)
                it.setInt(2, slot)
                it.setInt(3, titleId)
                it.setString(4, titleName)
                check(it.executeUpdate() == 1)
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, loadRankTitles(connection, actor.value.clanId))
    }

    public fun listEvents(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<List<ClanEventRecord>> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadEvents(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun listEventsByClan(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanManagementResult<List<ClanEventRecord>> =
        if (loadMotif(connection, clanId) == null) {
            denied("That clan does not exist.")
        } else {
            ClanManagementResult.Success(loadEvents(connection, clanId))
        }

    public fun createEvent(
        connection: DatabaseConnection,
        actorId: Int,
        event: ClanEventDraft,
        now: Instant,
    ): ClanManagementResult<ClanDomainMutation<ClanEventRecord>> {
        ClanEventRules.validationError(event)?.let {
            return denied(it)
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.OrganiseEvents)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        eventEditingRestriction(connection, actor.value.clanId)?.let {
            return denied(it)
        }
        val startsAt = startInstant(event, now)
        val id =
            connection
                .prepareStatement(
                    """
                INSERT INTO clan_events
                    (clan_id, event_type, event_subtype, activity_key, starts_at, duration_ticks,
                     minimum_join_rank, world_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """
                        .trimIndent()
                )
                .use {
                    bindEvent(it, actor.value.clanId, actorId, event, startsAt)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getLong(1)
                    }
                }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, eventRecord(id, event, startsAt))
    }

    public fun updateEvent(
        connection: DatabaseConnection,
        actorId: Int,
        eventId: Long,
        event: ClanEventDraft,
        now: Instant,
    ): ClanManagementResult<ClanDomainMutation<ClanEventRecord>> {
        ClanEventRules.validationError(event)?.let {
            return denied(it)
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.OrganiseEvents)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        eventEditingRestriction(connection, actor.value.clanId)?.let {
            return denied(it)
        }
        lockEvent(connection, actor.value.clanId, eventId)
            ?: return denied("That clan event does not exist.")
        val startsAt = startInstant(event, now)
        connection
            .prepareStatement(
                """
            UPDATE clan_events SET event_type = ?, event_subtype = ?, activity_key = ?,
                starts_at = ?, duration_ticks = ?, minimum_join_rank = ?, world_id = ?,
                updated_by = ?, updated_at = CURRENT_TIMESTAMP
            WHERE clan_id = ? AND id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, event.type.storageId)
                it.setInt(2, event.subtype.storageId)
                it.setString(3, event.activityKey)
                it.setTimestamp(4, Timestamp.from(startsAt))
                it.setLong(5, event.durationTicks)
                it.setInt(6, event.minimumJoinRank)
                it.setInt(7, event.worldId)
                it.setInt(8, actorId)
                it.setLong(9, actor.value.clanId)
                it.setLong(10, eventId)
                it.executeUpdate()
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, eventRecord(eventId, event, startsAt))
    }

    public fun deleteEvent(
        connection: DatabaseConnection,
        actorId: Int,
        eventId: Long,
    ): ClanManagementResult<ClanDomainMutation<Unit>> {
        val actor = authorized(connection, actorId, ClanPermissionKey.OrganiseEvents)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        eventEditingRestriction(connection, actor.value.clanId)?.let {
            return denied(it)
        }
        lockEvent(connection, actor.value.clanId, eventId)
            ?: return denied("That clan event does not exist.")
        connection.prepareStatement("DELETE FROM clan_events WHERE clan_id = ? AND id = ?").use {
            it.setLong(1, actor.value.clanId)
            it.setLong(2, eventId)
            it.executeUpdate()
        }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, Unit)
    }

    public fun getHallSettings(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<ClanHallSettings> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadHall(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun beginHallEditSession(
        connection: DatabaseConnection,
        actorId: Int,
        now: Instant,
    ): ClanManagementResult<ClanDomainMutation<ClanHallSettings>> {
        val actor = authorized(connection, actorId, ClanPermissionKey.HallEditSettings)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        val current =
            lockHall(connection, actor.value.clanId)
                ?: return denied("That Clan Hall no longer exists.")
        ClanHallEditSessionRules.acquisitionDenial(current, actorId, now.toEpochMilli())?.let {
            return denied(it)
        }
        val expiresAt = now.plusMillis(ClanHallEditSessionRules.LEASE_MILLIS)
        connection
            .prepareStatement(
                """
            UPDATE clan_hall_settings SET edit_state_version = edit_state_version + 1,
                edit_session_character_id = ?, edit_session_started_at = ?,
                edit_session_expires_at = ?, updated_at = CURRENT_TIMESTAMP
            WHERE clan_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, actorId)
                it.setTimestamp(2, Timestamp.from(now))
                it.setTimestamp(3, Timestamp.from(expiresAt))
                it.setLong(4, actor.value.clanId)
                check(it.executeUpdate() == 1)
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, loadHall(connection, actor.value.clanId))
    }

    public fun releaseHallEditSession(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<ClanDomainMutation<ClanHallSettings>> {
        val clanId =
            memberClanId(connection, actorId) ?: return denied("You are not a member of a clan.")
        lockClan(connection, clanId)
        val current =
            lockHall(connection, clanId) ?: return denied("That Clan Hall no longer exists.")
        if (current.editSessionCharacterId != actorId) {
            return success(clanId, current)
        }
        connection
            .prepareStatement(
                """
            UPDATE clan_hall_settings SET edit_state_version = edit_state_version + 1,
                edit_session_character_id = NULL, edit_session_started_at = NULL,
                edit_session_expires_at = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE clan_id = ? AND edit_session_character_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.setInt(2, actorId)
                check(it.executeUpdate() == 1)
            }
        bump(connection, clanId)
        return success(clanId, loadHall(connection, clanId))
    }

    public fun updateHallSettings(
        connection: DatabaseConnection,
        actorId: Int,
        settings: ClanHallSettings,
        now: Instant = Instant.now(),
    ): ClanManagementResult<ClanDomainMutation<ClanHallSettings>> {
        settings.validationError()?.let {
            return denied(it)
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.HallEditSettings)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        val current =
            lockHall(connection, actor.value.clanId)
                ?: return denied("That Clan Hall no longer exists.")
        if (!ClanHallEditSessionRules.ownsLease(current, actorId, now.toEpochMilli())) {
            return denied("Open the Clan Hall editor before changing its settings.")
        }
        connection
            .prepareStatement(
                """
            UPDATE clan_hall_settings SET home_world_only = ?, locked = ?, player_capacity = ?,
                theme_key = ?, identity_key = ?, edit_state_version = edit_state_version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE clan_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setBoolean(1, settings.homeWorldOnly)
                it.setBoolean(2, settings.locked)
                it.setInt(3, settings.playerCapacity)
                it.setString(4, settings.themeKey)
                it.setString(5, settings.identityKey)
                it.setLong(6, actor.value.clanId)
                check(it.executeUpdate() == 1)
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, loadHall(connection, actor.value.clanId))
    }

    public fun getCoffer(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<ClanCofferSnapshot> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadCoffer(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun depositCoffer(
        connection: DatabaseConnection,
        actorId: Int,
        amount: Long,
        requestId: UUID,
    ): ClanManagementResult<ClanDomainMutation<ClanCofferSnapshot>> {
        if (amount <= 0) return denied("The coffer amount must be positive.")
        return mutateCoffer(connection, actorId, amount, requestId, ClanPermissionKey.CofferDeposit)
    }

    public fun withdrawCoffer(
        connection: DatabaseConnection,
        actorId: Int,
        amount: Long,
        requestId: UUID,
    ): ClanManagementResult<ClanDomainMutation<ClanCofferSnapshot>> {
        if (amount <= 0) return denied("The coffer amount must be positive.")
        return mutateCoffer(
            connection,
            actorId,
            -amount,
            requestId,
            ClanPermissionKey.CofferWithdraw,
        )
    }

    public fun listPartyChest(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<List<ClanPartyChestEntry>> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadPartyChest(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun addPartyChestItem(
        connection: DatabaseConnection,
        actorId: Int,
        itemId: Int,
        quantity: Long,
    ): ClanManagementResult<ClanDomainMutation<List<ClanPartyChestEntry>>> {
        if (itemId < 0 || quantity !in 1..Int.MAX_VALUE.toLong()) {
            return denied("That party-chest item is not valid.")
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.HallPartyChestDeposit)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        val current = lockChestItem(connection, actor.value.clanId, itemId)
        if (
            current == null &&
                partyChestCount(connection, actor.value.clanId) >=
                    ClanDomainLimits.MAX_PARTY_CHEST_UNIQUE_ITEMS
        ) {
            return denied("The clan party chest is full.")
        }
        val updated = (current ?: 0L) + quantity
        if (updated > Int.MAX_VALUE) return denied("That party-chest stack is full.")
        connection
            .prepareStatement(
                """
            INSERT INTO clan_hall_party_chest_items
                (clan_id, item_id, quantity, deposited_by, active)
            VALUES (?, ?, ?, ?, TRUE) ON CONFLICT (clan_id, item_id) DO UPDATE
            SET quantity = EXCLUDED.quantity, deposited_by = EXCLUDED.deposited_by,
                deposited_at = CURRENT_TIMESTAMP, active = TRUE
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, actor.value.clanId)
                it.setInt(2, itemId)
                it.setLong(3, updated)
                it.setInt(4, actorId)
                it.executeUpdate()
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, loadPartyChest(connection, actor.value.clanId))
    }

    public fun removePartyChestItem(
        connection: DatabaseConnection,
        actorId: Int,
        itemId: Int,
        quantity: Long,
    ): ClanManagementResult<ClanDomainMutation<List<ClanPartyChestEntry>>> {
        if (itemId < 0 || quantity <= 0) return denied("That party-chest item is not valid.")
        val actor = authorized(connection, actorId, ClanPermissionKey.HallPartyBalloonLoot)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        val current =
            lockChestItem(connection, actor.value.clanId, itemId)
                ?: return denied("That item is not in the clan party chest.")
        if (quantity > current) return denied("The clan party chest does not contain that many.")
        if (quantity == current) {
            connection
                .prepareStatement(
                    "DELETE FROM clan_hall_party_chest_items WHERE clan_id = ? AND item_id = ?"
                )
                .use {
                    it.setLong(1, actor.value.clanId)
                    it.setInt(2, itemId)
                    it.executeUpdate()
                }
        } else {
            connection
                .prepareStatement(
                    "UPDATE clan_hall_party_chest_items SET quantity = ? WHERE clan_id = ? AND item_id = ?"
                )
                .use {
                    it.setLong(1, current - quantity)
                    it.setLong(2, actor.value.clanId)
                    it.setInt(3, itemId)
                    it.executeUpdate()
                }
        }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, loadPartyChest(connection, actor.value.clanId))
    }

    public fun pullPartyChestLever(
        connection: DatabaseConnection,
        actorId: Int,
        requestId: UUID,
    ): ClanManagementResult<ClanDomainMutation<ClanPartyChestRelease>> {
        val actor = authorized(connection, actorId, ClanPermissionKey.HallPullLever)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        existingCofferTransaction(connection, requestId)?.let {
            if (it.clanId != actor.value.clanId) return denied("That coffer request is not valid.")
            return denied("That party drop has already been released.")
        }

        /*
         * [authorized] locks the clan row, serializing deposits with this release. Lock and copy
         * the exact active rows before debiting the coffer, then deactivate those same rows in
         * this database request so no item can be charged for without entering the release.
         */
        val entries = lockPartyChest(connection, actor.value.clanId)
        if (entries.isEmpty()) return denied("There are no items in the party chest.")
        val cost = ClanDomainLimits.PARTY_CHEST_LEVER_COST.toLong()
        val balance = lockCoffer(connection, actor.value.clanId)
        if (!ClanCofferRules.canApply(balance, -cost)) {
            return denied("The clan coffer does not contain enough coins.")
        }
        val updated = balance - cost
        connection
            .prepareStatement(
                "UPDATE clan_coffers SET balance = ?, updated_by = ?, " +
                    "updated_at = CURRENT_TIMESTAMP WHERE clan_id = ?"
            )
            .use {
                it.setLong(1, updated)
                it.setInt(2, actorId)
                it.setLong(3, actor.value.clanId)
                check(it.executeUpdate() == 1)
            }
        connection
            .prepareStatement(
                "INSERT INTO clan_coffer_transactions " +
                    "(request_id, clan_id, actor_character_id, transaction_type, amount, balance_after) " +
                    "VALUES (?, ?, ?, ?, ?, ?)"
            )
            .use {
                it.setObject(1, requestId)
                it.setLong(2, actor.value.clanId)
                it.setInt(3, actorId)
                it.setInt(4, ClanCofferTransactionType.Withdrawal.storageId)
                it.setLong(5, cost)
                it.setLong(6, updated)
                check(it.executeUpdate() == 1)
            }
        val cleared =
            connection
                .prepareStatement(
                    "UPDATE clan_hall_party_chest_items SET active = FALSE " +
                        "WHERE clan_id = ? AND active"
                )
                .use {
                    it.setLong(1, actor.value.clanId)
                    it.executeUpdate()
                }
        check(cleared == entries.size) { "Party chest changed during its locked release." }
        bump(connection, actor.value.clanId)
        return success(
            actor.value.clanId,
            ClanPartyChestRelease(loadCoffer(connection, actor.value.clanId), entries),
        )
    }

    /**
     * Archives and deactivates the live contents of a party chest when its hall session ends.
     *
     * The archive is append-only so cleanup remains recoverable and auditable. This method is
     * intentionally a system operation: player-facing deposits and withdrawals continue to use
     * their rank-gated methods above.
     */
    public fun archivePartyChestCleanup(
        connection: DatabaseConnection,
        clanId: Long,
        hallSessionId: String,
        reason: String,
    ): ClanManagementResult<ClanDomainMutation<Int>> {
        if (clanId <= 0L) return denied("That clan is not valid.")
        if (hallSessionId.length !in 1..64 || reason.length !in 1..64) {
            return denied("That clan hall cleanup request is not valid.")
        }

        val exists =
            connection
                .prepareStatement(
                    "SELECT clan_id FROM clan_hall_settings WHERE clan_id = ? FOR UPDATE"
                )
                .use {
                    it.setLong(1, clanId)
                    it.executeQuery().use { rows -> rows.next() }
                }
        if (!exists) return denied("That clan does not exist.")
        connection
            .prepareStatement(
                """
                INSERT INTO clan_hall_party_chest_cleanup_archive
                    (clan_id, item_id, quantity, deposited_by, deposited_at,
                     hall_session_id, cleanup_reason)
                SELECT clan_id, item_id, quantity, deposited_by, deposited_at, ?, ?
                FROM clan_hall_party_chest_items
                WHERE clan_id = ? AND active
                """
                    .trimIndent()
            )
            .use {
                it.setString(1, hallSessionId)
                it.setString(2, reason)
                it.setLong(3, clanId)
                it.executeUpdate()
            }
        val cleared =
            connection
                .prepareStatement(
                    "UPDATE clan_hall_party_chest_items SET active = FALSE " +
                        "WHERE clan_id = ? AND active"
                )
                .use {
                    it.setLong(1, clanId)
                    it.executeUpdate()
                }
        if (cleared > 0) bump(connection, clanId)
        return success(clanId, cleared)
    }

    public fun getBroadcastSettings(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<ClanBroadcastSettings> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadBroadcastSettings(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun updateBroadcastSettings(
        connection: DatabaseConnection,
        actorId: Int,
        settings: ClanBroadcastSettings,
    ): ClanManagementResult<ClanDomainMutation<ClanBroadcastSettings>> {
        settings.validationError()?.let {
            return denied(it)
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.EditPublicProfile)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        updateBroadcastSettings(connection, actor.value.clanId, settings)
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, settings)
    }

    public fun getMemberNotificationPreferences(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<ClanMemberNotificationPreferences> =
        connection
            .prepareStatement(
                """
            SELECT p.broadcasts_enabled, p.event_reminders_enabled,
                   p.recruiter_notifications_enabled
            FROM clan_member_notification_preferences p
            JOIN clan_members m ON m.clan_id = p.clan_id
              AND m.character_id = p.character_id
            WHERE p.character_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, actorId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) return denied("You are not a member of a clan.")
                    ClanManagementResult.Success(
                        ClanMemberNotificationPreferences(
                            broadcastsEnabled = rows.getBoolean(1),
                            eventRemindersEnabled = rows.getBoolean(2),
                            recruiterNotificationsEnabled = rows.getBoolean(3),
                        )
                    )
                }
            }

    public fun updateMemberNotificationPreferences(
        connection: DatabaseConnection,
        actorId: Int,
        preferences: ClanMemberNotificationPreferences,
    ): ClanManagementResult<ClanDomainMutation<ClanMemberNotificationPreferences>> {
        val clanId =
            memberClanId(connection, actorId) ?: return denied("You are not a member of a clan.")
        lockClan(connection, clanId)
        if (memberClanId(connection, actorId) != clanId) {
            return denied("You are not a member of that clan.")
        }
        val updated =
            connection
                .prepareStatement(
                    """
                UPDATE clan_member_notification_preferences
                SET broadcasts_enabled = ?, event_reminders_enabled = ?,
                    recruiter_notifications_enabled = ?, updated_at = CURRENT_TIMESTAMP
                WHERE clan_id = ? AND character_id = ?
                """
                        .trimIndent()
                )
                .use {
                    it.setBoolean(1, preferences.broadcastsEnabled)
                    it.setBoolean(2, preferences.eventRemindersEnabled)
                    it.setBoolean(3, preferences.recruiterNotificationsEnabled)
                    it.setLong(4, clanId)
                    it.setInt(5, actorId)
                    it.executeUpdate()
                }
        if (updated != 1) return denied("Your clan notification settings are unavailable.")
        bump(connection, clanId)
        return success(clanId, preferences)
    }

    public fun listPendingRecruiterNotifications(
        connection: DatabaseConnection,
        actorId: Int,
        limit: Int = 50,
    ): ClanManagementResult<List<ClanRecruiterNotification>> {
        if (limit !in 1..100) return denied("The notification limit must be from 1 to 100.")
        if (memberClanId(connection, actorId) == null) {
            return denied("You are not a member of a clan.")
        }
        val notifications = ArrayList<ClanRecruiterNotification>()
        connection
            .prepareStatement(
                """
            SELECT n.id, n.clan_id, n.recipient_character_id, n.applicant_character_id,
                   n.notification_kind, n.created_at, n.delivered_at
            FROM clan_recruiter_notifications n
            JOIN clan_members m ON m.clan_id = n.clan_id
              AND m.character_id = n.recipient_character_id
            WHERE n.recipient_character_id = ? AND n.delivered_at IS NULL
            ORDER BY n.id LIMIT ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, actorId)
                it.setInt(2, limit)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        val kind =
                            ClanRecruiterNotificationKind.entries.firstOrNull { value ->
                                value.storageId == rows.getInt("notification_kind")
                            } ?: continue
                        val applicantId =
                            rows.getInt("applicant_character_id").let { value ->
                                if (rows.wasNull()) null else value
                            }
                        notifications +=
                            ClanRecruiterNotification(
                                id = rows.getLong("id"),
                                clanId = rows.getLong("clan_id"),
                                recipientCharacterId = rows.getInt("recipient_character_id"),
                                applicantCharacterId = applicantId,
                                kind = kind,
                                createdAtEpochMillis =
                                    rows.getTimestamp("created_at").toInstant().toEpochMilli(),
                                deliveredAtEpochMillis =
                                    rows.getTimestamp("delivered_at")?.toInstant()?.toEpochMilli(),
                            )
                    }
                }
            }
        return ClanManagementResult.Success(notifications)
    }

    public fun markRecruiterNotificationsDelivered(
        connection: DatabaseConnection,
        actorId: Int,
        notificationIds: Set<Long>,
        now: Instant = Instant.now(),
    ): ClanManagementResult<Int> {
        if (notificationIds.size > 100 || notificationIds.any { it <= 0L }) {
            return denied("That notification selection is not valid.")
        }
        if (memberClanId(connection, actorId) == null) {
            return denied("You are not a member of a clan.")
        }
        if (notificationIds.isEmpty()) return ClanManagementResult.Success(0)
        var updated = 0
        connection
            .prepareStatement(
                """
            UPDATE clan_recruiter_notifications SET delivered_at = ?
            WHERE id = ? AND recipient_character_id = ? AND delivered_at IS NULL
            """
                    .trimIndent()
            )
            .use { statement ->
                notificationIds.sorted().forEach { id ->
                    statement.setTimestamp(1, Timestamp.from(now))
                    statement.setLong(2, id)
                    statement.setInt(3, actorId)
                    updated += statement.executeUpdate()
                }
            }
        return ClanManagementResult.Success(updated)
    }

    public fun getDomainSettings(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<ClanDomainSettings> =
        memberClanId(connection, actorId)?.let {
            ClanManagementResult.Success(loadDomainSettings(connection, it))
        } ?: denied("You are not a member of a clan.")

    public fun getDomainSettingsByClan(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanManagementResult<ClanDomainSettings> =
        if (loadMotif(connection, clanId) == null) {
            denied("That clan does not exist.")
        } else {
            ClanManagementResult.Success(loadDomainSettings(connection, clanId))
        }

    public fun updateDomainSettings(
        connection: DatabaseConnection,
        actorId: Int,
        settings: ClanDomainSettings,
    ): ClanManagementResult<ClanDomainMutation<ClanDomainSettings>> {
        if (settings.interests.size > ClanDomainLimits.INTERESTS_PER_CLAN) {
            return denied("That clan has too many interests.")
        }
        settings.guestRequirements.validationError()?.let {
            return denied(it)
        }
        settings.broadcastSettings.validationError()?.let {
            return denied(it)
        }
        val actor = authorized(connection, actorId, ClanPermissionKey.EditPublicProfile)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        connection.prepareStatement("DELETE FROM clan_interests WHERE clan_id = ?").use {
            it.setLong(1, actor.value.clanId)
            it.executeUpdate()
        }
        connection
            .prepareStatement("INSERT INTO clan_interests (clan_id, interest_id) VALUES (?, ?)")
            .use { statement ->
                settings.interests.sortedBy(ClanInterest::storageId).forEach {
                    statement.setLong(1, actor.value.clanId)
                    statement.setInt(2, it.storageId)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        updateGuestRequirements(connection, actor.value.clanId, settings.guestRequirements)
        updateBroadcastSettings(connection, actor.value.clanId, settings.broadcastSettings)
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, settings)
    }

    public fun withdrawApplication(
        connection: DatabaseConnection,
        characterId: Int,
        clanId: Long,
    ): ClanManagementResult<ClanDomainMutation<Unit>> {
        lockClan(connection, clanId)
        val removed =
            connection
                .prepareStatement(
                    "DELETE FROM clan_applications WHERE clan_id = ? AND character_id = ?"
                )
                .use {
                    it.setLong(1, clanId)
                    it.setInt(2, characterId)
                    it.executeUpdate()
                }
        if (removed == 0) return denied("You do not have an application with that clan.")
        notifyRecruiters(
            connection,
            clanId,
            characterId,
            ClanRecruiterNotificationKind.ApplicationWithdrawn,
        )
        bump(connection, clanId)
        return success(clanId, Unit)
    }

    public fun declineInvite(
        connection: DatabaseConnection,
        characterId: Int,
        clanId: Long,
    ): ClanManagementResult<ClanDomainMutation<Unit>> {
        lockClan(connection, clanId)
        val removed =
            connection
                .prepareStatement("DELETE FROM clan_invites WHERE clan_id = ? AND character_id = ?")
                .use {
                    it.setLong(1, clanId)
                    it.setInt(2, characterId)
                    it.executeUpdate()
                }
        if (removed == 0) return denied("You do not have an invitation from that clan.")
        bump(connection, clanId)
        return success(clanId, Unit)
    }

    public fun computeSuccessor(
        connection: DatabaseConnection,
        actorId: Int,
        now: Instant,
    ): ClanManagementResult<ClanOwnershipStatus> {
        val clanId =
            memberClanId(connection, actorId) ?: return denied("You are not a member of a clan.")
        val status = loadOwnership(connection, clanId, now)
        if (status.ownerCharacterId != actorId)
            return denied("Only the Owner may choose succession.")
        return ClanManagementResult.Success(status)
    }

    public fun requestSuccessorOwnership(
        connection: DatabaseConnection,
        actorId: Int,
        now: Instant,
    ): ClanManagementResult<ClanDomainMutation<ClanOwnershipStatus>> {
        val actor =
            lockOwner(connection, actorId)
                ?: return denied("Only the Owner may request ownership succession.")
        lockAllMembers(connection, actor.clanId)
        val status = loadOwnership(connection, actor.clanId, now)
        val successor = status.successor
        if (successor == null && memberCount(connection, actor.clanId) != 1) {
            return denied("No eligible ownership successor is available.")
        }
        val availableAt =
            now.plus(ClanDomainLimits.OWNER_RESIGNATION_DELAY_DAYS.toLong(), ChronoUnit.DAYS)
        connection
            .prepareStatement(
                """
            UPDATE clans SET owner_transfer_character_id = ?, owner_transfer_available_at = ?,
                owner_resignation_requested_at = ?, state_version = state_version + 1,
                updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setNullableInt(1, successor?.characterId)
                it.setTimestamp(2, Timestamp.from(availableAt))
                it.setTimestamp(3, Timestamp.from(now))
                it.setLong(4, actor.clanId)
                it.executeUpdate()
            }
        return success(actor.clanId, loadOwnership(connection, actor.clanId, now))
    }

    public fun finalizeSuccessorOwnership(
        connection: DatabaseConnection,
        actorId: Int,
        now: Instant,
    ): ClanManagementResult<ClanDomainMutation<ClanOwnershipFinalization>> {
        val actor =
            lockOwner(connection, actorId)
                ?: return denied("Only the Owner may finalize ownership succession.")
        lockAllMembers(connection, actor.clanId)
        val transfer =
            connection
                .prepareStatement(
                    """
                SELECT owner_transfer_character_id, owner_transfer_available_at
                FROM clans WHERE id = ? FOR UPDATE
                """
                        .trimIndent()
                )
                .use {
                    it.setLong(1, actor.clanId)
                    it.executeQuery().use { rows ->
                        if (!rows.next()) null
                        else rows.getNullableInt(1) to rows.getTimestamp(2)?.toInstant()
                    }
                } ?: return denied("That clan no longer exists.")
        val availableAt =
            transfer.second ?: return denied("No ownership succession or clan deletion is pending.")
        if (now.isBefore(availableAt))
            return denied("The seven-day ownership resignation delay has not ended.")
        val targetId = transfer.first
        if (targetId == null) {
            if (memberCount(connection, actor.clanId) != 1) {
                return denied(
                    "The clan is no longer empty; choose an eligible ownership successor."
                )
            }
            val deleted =
                connection
                    .prepareStatement("DELETE FROM clans WHERE id = ? AND owner_character_id = ?")
                    .use {
                        it.setLong(1, actor.clanId)
                        it.setInt(2, actorId)
                        it.executeUpdate()
                    }
            check(deleted == 1)
            return success(
                actor.clanId,
                ClanOwnershipFinalization(actor.clanId, deleted = true, status = null),
            )
        }
        val status = loadOwnership(connection, actor.clanId, now)
        if (status.successor?.characterId != targetId) {
            return denied("The selected successor is no longer eligible.")
        }
        connection
            .prepareStatement(
                "UPDATE clan_members SET rank = ? WHERE clan_id = ? AND character_id = ?"
            )
            .use {
                it.setInt(1, ClanRanks.DEPUTY_OWNER)
                it.setLong(2, actor.clanId)
                it.setInt(3, actorId)
                it.executeUpdate()
            }
        connection
            .prepareStatement(
                "UPDATE clan_members SET rank = ? WHERE clan_id = ? AND character_id = ?"
            )
            .use {
                it.setInt(1, ClanRanks.OWNER)
                it.setLong(2, actor.clanId)
                it.setInt(3, targetId)
                check(it.executeUpdate() == 1)
            }
        connection
            .prepareStatement(
                """
            UPDATE clans SET owner_character_id = ?, owner_transfer_character_id = NULL,
                owner_transfer_available_at = NULL, owner_resignation_requested_at = NULL,
                state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, targetId)
                it.setLong(2, actor.clanId)
                it.executeUpdate()
            }
        return success(
            actor.clanId,
            ClanOwnershipFinalization(
                actor.clanId,
                deleted = false,
                status = loadOwnership(connection, actor.clanId, now),
            ),
        )
    }

    private fun authorized(
        connection: DatabaseConnection,
        actorId: Int,
        permission: ClanPermissionKey,
    ): ClanManagementResult<Actor> =
        connection
            .prepareStatement(
                """
            SELECT m.clan_id, m.rank, p.minimum_rank
            FROM clan_members m
            JOIN clans c ON c.id = m.clan_id
            JOIN clan_permission_settings p
              ON p.clan_id = m.clan_id AND p.permission_key = ?
            WHERE m.character_id = ? FOR UPDATE OF c, m, p
            """
                    .trimIndent()
            )
            .use {
                it.setString(1, permission.storageKey)
                it.setInt(2, actorId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) return@use denied("You are not a member of a clan.")
                    val rank = rows.getInt("rank")
                    val threshold = rows.getInt("minimum_rank")
                    if (!ClanRanks.atLeast(rank, threshold)) {
                        denied(
                            "You do not have permission to " +
                                permission.storageKey.replace('_', ' ') +
                                "."
                        )
                    } else {
                        ClanManagementResult.Success(Actor(rows.getLong("clan_id"), rank))
                    }
                }
            }

    private fun validatePermissions(permissions: ClanPermissionSet): String? {
        permissions.validationError()?.let {
            return it
        }
        for ((key, rank) in permissions.minimumRanks) {
            if (
                !ClanRanks.atLeast(rank, key.minimumAllowedRank) &&
                    !(key.guestThresholdAllowed && rank == ClanRanks.GUEST)
            ) {
                return "The " + key.storageKey + " rank is below its minimum."
            }
        }
        return null
    }

    private fun mutateCoffer(
        connection: DatabaseConnection,
        actorId: Int,
        delta: Long,
        requestId: UUID,
        permission: ClanPermissionKey,
    ): ClanManagementResult<ClanDomainMutation<ClanCofferSnapshot>> {
        if (delta == 0L || delta == Long.MIN_VALUE) return denied("The coffer amount is not valid.")
        val actor = authorized(connection, actorId, permission)
        if (actor is ClanManagementResult.Denied) return actor
        actor as ClanManagementResult.Success
        existingCofferTransaction(connection, requestId)?.let {
            if (it.clanId != actor.value.clanId) return denied("That coffer request is not valid.")
            return success(actor.value.clanId, loadCoffer(connection, actor.value.clanId))
        }
        val balance = lockCoffer(connection, actor.value.clanId)
        if (!ClanCofferRules.canApply(balance, delta)) {
            return denied(
                if (delta < 0) "The clan coffer does not contain enough coins."
                else "The clan coffer is full."
            )
        }
        val updated = balance + delta
        connection
            .prepareStatement(
                """
            UPDATE clan_coffers SET balance = ?, updated_by = ?,
                updated_at = CURRENT_TIMESTAMP WHERE clan_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, updated)
                it.setInt(2, actorId)
                it.setLong(3, actor.value.clanId)
                it.executeUpdate()
            }
        connection
            .prepareStatement(
                """
            INSERT INTO clan_coffer_transactions
                (request_id, clan_id, actor_character_id, transaction_type, amount, balance_after)
            VALUES (?, ?, ?, ?, ?, ?)
            """
                    .trimIndent()
            )
            .use {
                it.setObject(1, requestId)
                it.setLong(2, actor.value.clanId)
                it.setInt(3, actorId)
                it.setInt(
                    4,
                    if (delta > 0) ClanCofferTransactionType.Deposit.storageId
                    else ClanCofferTransactionType.Withdrawal.storageId,
                )
                it.setLong(5, kotlin.math.abs(delta))
                it.setLong(6, updated)
                it.executeUpdate()
            }
        bump(connection, actor.value.clanId)
        return success(actor.value.clanId, loadCoffer(connection, actor.value.clanId))
    }

    private fun loadPermissions(connection: DatabaseConnection, clanId: Long): ClanPermissionSet {
        val ranks = ClanPermissionSet.defaults().minimumRanks.toMutableMap()
        connection
            .prepareStatement(
                "SELECT permission_key, minimum_rank FROM clan_permission_settings WHERE clan_id = ?"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        ClanPermissionKey.fromStorageKey(rows.getString(1))?.let { key ->
                            ranks[key] = rows.getInt(2)
                        }
                    }
                }
            }
        return ClanPermissionSet(ranks)
    }

    private fun loadMotif(connection: DatabaseConnection, clanId: Long): ClanMotif? =
        connection
            .prepareStatement(
                """
            SELECT motif_primary_colour, motif_secondary_colour,
                   motif_primary_symbol, motif_secondary_symbol
            FROM clans WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) null
                    else {
                        val primary =
                            ClanMotifColour.entries.find { colour ->
                                colour.storageId == rows.getInt(1)
                            } ?: return@use null
                        ClanMotif(primary, primary, motifIcon(rows, 3), motifIcon(rows, 4))
                    }
                }
            }

    private fun loadRankTitles(connection: DatabaseConnection, clanId: Long): Map<Int, String> {
        val titles =
            (0 until ClanDomainLimits.CUSTOM_RANK_SLOTS)
                .associateWith { "Rank " + (it + 1) }
                .toMutableMap()
        connection
            .prepareStatement(
                "SELECT rank_slot, title_name FROM clan_rank_titles WHERE clan_id = ?"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) titles[rows.getInt(1)] = rows.getString(2).orEmpty()
                }
            }
        return titles.toSortedMap()
    }

    private fun loadEvents(connection: DatabaseConnection, clanId: Long): List<ClanEventRecord> {
        val events = ArrayList<ClanEventRecord>()
        connection
            .prepareStatement(
                """
            SELECT id, event_type, event_subtype, activity_key, starts_at, duration_ticks,
                   minimum_join_rank, world_id
            FROM clan_events WHERE clan_id = ? ORDER BY starts_at, id
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        val type =
                            ClanEventType.entries.find { value ->
                                value.storageId == rows.getInt("event_type")
                            } ?: continue
                        val subtype =
                            ClanEventSubtype.entries.find { value ->
                                value.storageId == rows.getInt("event_subtype")
                            } ?: continue
                        events +=
                            ClanEventRecord(
                                rows.getLong("id"),
                                type,
                                subtype,
                                rows.getString("activity_key"),
                                rows.getTimestamp("starts_at").time,
                                rows.getLong("duration_ticks"),
                                rows.getInt("minimum_join_rank"),
                                rows.getInt("world_id"),
                            )
                    }
                }
            }
        return events
    }

    private fun lockHall(connection: DatabaseConnection, clanId: Long): ClanHallSettings? {
        val exists =
            connection
                .prepareStatement(
                    "SELECT clan_id FROM clan_hall_settings WHERE clan_id = ? FOR UPDATE"
                )
                .use {
                    it.setLong(1, clanId)
                    it.executeQuery().use(ResultSet::next)
                }
        return if (exists) loadHall(connection, clanId) else null
    }

    private fun loadHall(connection: DatabaseConnection, clanId: Long): ClanHallSettings =
        connection
            .prepareStatement(
                """
            SELECT home_world_only, locked, player_capacity, theme_key, identity_key,
                   edit_state_version, edit_session_character_id, edit_session_started_at,
                   edit_session_expires_at FROM clan_hall_settings WHERE clan_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    check(rows.next())
                    ClanHallSettings(
                        rows.getBoolean(1),
                        rows.getBoolean(2),
                        rows.getInt(3),
                        rows.getString(4),
                        rows.getString(5),
                        rows.getLong(6),
                        rows.getNullableInt(7),
                        rows.getTimestamp(8)?.time,
                        rows.getTimestamp(9)?.time,
                    )
                }
            }

    private fun loadCoffer(connection: DatabaseConnection, clanId: Long): ClanCofferSnapshot {
        val balance =
            connection.prepareStatement("SELECT balance FROM clan_coffers WHERE clan_id = ?").use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getLong(1)
                }
            }
        val history = ArrayList<ClanCofferTransaction>()
        connection
            .prepareStatement(
                """
            SELECT request_id, actor_character_id, transaction_type, amount,
                   balance_after, created_at
            FROM clan_coffer_transactions WHERE clan_id = ? ORDER BY id DESC LIMIT 100
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        history +=
                            ClanCofferTransaction(
                                rows.getObject(1, UUID::class.java).toString(),
                                rows.getInt(2),
                                if (rows.getInt(3) == 0) ClanCofferTransactionType.Deposit
                                else ClanCofferTransactionType.Withdrawal,
                                rows.getLong(4),
                                rows.getLong(5),
                                rows.getTimestamp(6).time,
                            )
                    }
                }
            }
        return ClanCofferSnapshot(clanId, balance, history)
    }

    private fun loadPartyChest(
        connection: DatabaseConnection,
        clanId: Long,
    ): List<ClanPartyChestEntry> {
        val entries = ArrayList<ClanPartyChestEntry>()
        connection
            .prepareStatement(
                """
            SELECT item_id, quantity, deposited_by, deposited_at
            FROM clan_hall_party_chest_items
            WHERE clan_id = ? AND active
            ORDER BY item_id
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        entries +=
                            ClanPartyChestEntry(
                                rows.getInt(1),
                                rows.getLong(2),
                                rows.getNullableInt(3),
                                rows.getTimestamp(4).time,
                            )
                    }
                }
            }
        return entries
    }

    private fun loadDomainSettings(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanDomainSettings {
        val interests = LinkedHashSet<ClanInterest>()
        connection
            .prepareStatement(
                "SELECT interest_id FROM clan_interests WHERE clan_id = ? ORDER BY interest_id"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        ClanInterest.entries
                            .find { value -> value.storageId == rows.getInt(1) }
                            ?.let(interests::add)
                    }
                }
            }
        val requirements =
            connection
                .prepareStatement(
                    """
                SELECT guest_minimum_combat_level, guest_minimum_total_level,
                       guest_minimum_quest_points, guest_iron_requirement
                FROM clans WHERE id = ?
                """
                        .trimIndent()
                )
                .use {
                    it.setLong(1, clanId)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        ClanGuestRequirements(
                            rows.getInt(1),
                            rows.getInt(2),
                            rows.getInt(3),
                            ClanGuestIronRequirement.entries.first { value ->
                                value.storageId == rows.getInt(4)
                            },
                        )
                    }
                }
        return ClanDomainSettings(
            interests,
            requirements,
            loadBroadcastSettings(connection, clanId),
        )
    }

    private fun loadBroadcastSettings(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanBroadcastSettings {
        val minimumRanks = ClanBroadcastSettings.defaults().minimumRanks.toMutableMap()
        connection
            .prepareStatement(
                "SELECT broadcast_key, minimum_rank FROM clan_broadcast_permissions WHERE clan_id = ?"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        ClanBroadcastKey.fromStorageKey(rows.getString(1))?.let { key ->
                            minimumRanks[key] = rows.getInt(2)
                        }
                    }
                }
            }
        return connection
            .prepareStatement(
                """
            SELECT level_interval, post_99_xp_interval, collection_interval,
                   minimum_loot_value, minimum_player_kill_value, minimum_loot_key_value,
                   minimum_combat_achievement_tier, main_game_worlds_only,
                   event_starting_notifications, level_every_ten_minimum,
                   level_every_five_minimum, level_every_one_minimum
            FROM clan_broadcast_settings WHERE clan_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    check(rows.next())
                    ClanBroadcastSettings(
                        minimumRanks = minimumRanks,
                        levelInterval =
                            ClanLevelBroadcastInterval.entries.first { value ->
                                value.levels == rows.getInt("level_interval")
                            },
                        post99XpInterval = rows.getInt("post_99_xp_interval"),
                        collectionInterval =
                            ClanCollectionBroadcastInterval.entries.first { value ->
                                value.slots == rows.getInt("collection_interval")
                            },
                        minimumLootValue = rows.getInt("minimum_loot_value"),
                        minimumPlayerKillValue = rows.getInt("minimum_player_kill_value"),
                        minimumLootKeyValue = rows.getInt("minimum_loot_key_value"),
                        minimumCombatAchievementTier =
                            ClanCombatAchievementTier.entries.first { value ->
                                value.storageId == rows.getInt("minimum_combat_achievement_tier")
                            },
                        mainGameWorldsOnly = rows.getBoolean("main_game_worlds_only"),
                        eventStartingNotificationsEnabled =
                            rows.getBoolean("event_starting_notifications"),
                        levelEveryTenMinimum = rows.getInt("level_every_ten_minimum"),
                        levelEveryFiveMinimum = rows.getInt("level_every_five_minimum"),
                        levelEveryOneMinimum = rows.getInt("level_every_one_minimum"),
                    )
                }
            }
    }

    private fun updateGuestRequirements(
        connection: DatabaseConnection,
        clanId: Long,
        value: ClanGuestRequirements,
    ) {
        connection
            .prepareStatement(
                """
            UPDATE clans SET guest_minimum_combat_level = ?, guest_minimum_total_level = ?,
                guest_minimum_quest_points = ?, guest_iron_requirement = ? WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, value.minimumCombatLevel)
                it.setInt(2, value.minimumTotalLevel)
                it.setInt(3, value.minimumQuestPoints)
                it.setInt(4, value.ironRequirement.storageId)
                it.setLong(5, clanId)
                it.executeUpdate()
            }
    }

    private fun updateBroadcastSettings(
        connection: DatabaseConnection,
        clanId: Long,
        value: ClanBroadcastSettings,
    ) {
        connection
            .prepareStatement(
                """
            UPDATE clan_broadcast_settings SET level_interval = ?, post_99_xp_interval = ?,
                collection_interval = ?, minimum_loot_value = ?, minimum_player_kill_value = ?,
                minimum_loot_key_value = ?, minimum_combat_achievement_tier = ?,
                main_game_worlds_only = ?, event_starting_notifications = ?,
                level_every_ten_minimum = ?, level_every_five_minimum = ?,
                level_every_one_minimum = ?, updated_at = CURRENT_TIMESTAMP WHERE clan_id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, value.levelInterval.levels)
                it.setInt(2, value.post99XpInterval)
                it.setInt(3, value.collectionInterval.slots)
                it.setInt(4, value.minimumLootValue)
                it.setInt(5, value.minimumPlayerKillValue)
                it.setInt(6, value.minimumLootKeyValue)
                it.setInt(7, value.minimumCombatAchievementTier.storageId)
                it.setBoolean(8, value.mainGameWorldsOnly)
                it.setBoolean(9, value.eventStartingNotificationsEnabled)
                it.setInt(10, value.levelEveryTenMinimum)
                it.setInt(11, value.levelEveryFiveMinimum)
                it.setInt(12, value.levelEveryOneMinimum)
                it.setLong(13, clanId)
                it.executeUpdate()
            }
        connection
            .prepareStatement(
                """
            INSERT INTO clan_broadcast_permissions (clan_id, broadcast_key, minimum_rank)
            VALUES (?, ?, ?) ON CONFLICT (clan_id, broadcast_key) DO UPDATE
            SET minimum_rank = EXCLUDED.minimum_rank, updated_at = CURRENT_TIMESTAMP
            """
                    .trimIndent()
            )
            .use { statement ->
                value.minimumRanks.forEach { (key, rank) ->
                    statement.setLong(1, clanId)
                    statement.setString(2, key.storageKey)
                    statement.setInt(3, rank)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
    }

    private fun loadOwnership(
        connection: DatabaseConnection,
        clanId: Long,
        now: Instant,
    ): ClanOwnershipStatus {
        val header =
            connection
                .prepareStatement(
                    """
                SELECT owner_character_id, owner_resignation_requested_at,
                       owner_transfer_available_at FROM clans WHERE id = ?
                """
                        .trimIndent()
                )
                .use {
                    it.setLong(1, clanId)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        Triple(
                            rows.getInt(1),
                            rows.getTimestamp(2)?.time,
                            rows.getTimestamp(3)?.time,
                        )
                    }
                }
        val candidates = ArrayList<ClanSuccessionCandidate>()
        connection
            .prepareStatement(
                """
            SELECT character_id, rank, joined_rune_day, last_login_at
            FROM clan_members WHERE clan_id = ? ORDER BY character_id
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        val id = rows.getInt(1)
                        if (id != header.first) {
                            candidates +=
                                ClanSuccessionCandidate(
                                    id,
                                    rows.getInt(2),
                                    rows.getInt(3).toLong() * ClanOwnershipRules.DAY_MILLIS,
                                    rows.getTimestamp(4).time,
                                )
                        }
                    }
                }
            }
        return ClanOwnershipStatus(
            clanId,
            header.first,
            header.second,
            header.third,
            ClanOwnershipRules.selectSuccessor(candidates, now.toEpochMilli()),
        )
    }

    private fun memberClanId(connection: DatabaseConnection, actorId: Int): Long? =
        connection.prepareStatement("SELECT clan_id FROM clan_members WHERE character_id = ?").use {
            it.setInt(1, actorId)
            it.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun memberCount(connection: DatabaseConnection, clanId: Long): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM clan_members WHERE clan_id = ?").use {
            it.setLong(1, clanId)
            it.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private fun lockOwner(connection: DatabaseConnection, actorId: Int): Actor? =
        connection
            .prepareStatement(
                """
            SELECT m.clan_id, m.rank FROM clan_members m
            JOIN clans c ON c.id = m.clan_id
            WHERE m.character_id = ? AND c.owner_character_id = ? AND m.rank = ?
            FOR UPDATE OF c, m
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, actorId)
                it.setInt(2, actorId)
                it.setInt(3, ClanRanks.OWNER)
                it.executeQuery().use { rows ->
                    if (rows.next()) Actor(rows.getLong(1), rows.getInt(2)) else null
                }
            }

    private fun lockClan(connection: DatabaseConnection, clanId: Long) {
        connection.prepareStatement("SELECT id FROM clans WHERE id = ? FOR UPDATE").use {
            it.setLong(1, clanId)
            check(it.executeQuery().use(ResultSet::next))
        }
    }

    private fun lockAllMembers(connection: DatabaseConnection, clanId: Long) {
        connection
            .prepareStatement(
                "SELECT character_id FROM clan_members WHERE clan_id = ? ORDER BY character_id FOR UPDATE"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows -> while (rows.next()) Unit }
            }
    }

    private fun lockEvent(connection: DatabaseConnection, clanId: Long, eventId: Long): Long? =
        connection
            .prepareStatement("SELECT id FROM clan_events WHERE clan_id = ? AND id = ? FOR UPDATE")
            .use {
                it.setLong(1, clanId)
                it.setLong(2, eventId)
                it.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }

    private fun eventEditingRestriction(connection: DatabaseConnection, clanId: Long): String? =
        connection
            .prepareStatement(
                "SELECT recruitment_open, applications_open FROM clans WHERE id = ? FOR UPDATE"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) {
                        "That clan no longer exists."
                    } else if (
                        rows.getBoolean("recruitment_open") || rows.getBoolean("applications_open")
                    ) {
                        EVENT_EDIT_RECRUITMENT_BLOCKED
                    } else {
                        null
                    }
                }
            }

    private companion object {
        const val EVENT_EDIT_RECRUITMENT_BLOCKED: String =
            "Stop inviting applications before entering Event Edit Mode."
    }

    private fun lockCoffer(connection: DatabaseConnection, clanId: Long): Long =
        connection
            .prepareStatement("SELECT balance FROM clan_coffers WHERE clan_id = ? FOR UPDATE")
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getLong(1)
                }
            }

    private fun lockPartyChest(
        connection: DatabaseConnection,
        clanId: Long,
    ): List<ClanPartyChestEntry> {
        val entries = ArrayList<ClanPartyChestEntry>()
        connection
            .prepareStatement(
                """
                SELECT item_id, quantity, deposited_by, deposited_at
                FROM clan_hall_party_chest_items
                WHERE clan_id = ? AND active
                ORDER BY item_id FOR UPDATE
                """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        entries +=
                            ClanPartyChestEntry(
                                rows.getInt(1),
                                rows.getLong(2),
                                rows.getNullableInt(3),
                                rows.getTimestamp(4).time,
                            )
                    }
                }
            }
        return entries
    }

    private fun existingCofferTransaction(
        connection: DatabaseConnection,
        requestId: UUID,
    ): ExistingCoffer? =
        connection
            .prepareStatement("SELECT clan_id FROM clan_coffer_transactions WHERE request_id = ?")
            .use {
                it.setObject(1, requestId)
                it.executeQuery().use { rows ->
                    if (rows.next()) ExistingCoffer(rows.getLong(1)) else null
                }
            }

    private fun lockChestItem(connection: DatabaseConnection, clanId: Long, itemId: Int): Long? =
        connection
            .prepareStatement(
                """
            SELECT quantity FROM clan_hall_party_chest_items
            WHERE clan_id = ? AND item_id = ? AND active FOR UPDATE
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.setInt(2, itemId)
                it.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }

    private fun partyChestCount(connection: DatabaseConnection, clanId: Long): Int =
        connection
            .prepareStatement(
                "SELECT COUNT(*) FROM clan_hall_party_chest_items WHERE clan_id = ? AND active"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }

    private fun notifyRecruiters(
        connection: DatabaseConnection,
        clanId: Long,
        applicantId: Int,
        kind: ClanRecruiterNotificationKind,
    ) {
        val threshold = loadPermissions(connection, clanId)[ClanPermissionKey.Recruitment]
        connection
            .prepareStatement(
                """
            INSERT INTO clan_recruiter_notifications
                (clan_id, recipient_character_id, applicant_character_id, notification_kind)
            SELECT m.clan_id, m.character_id, ?, ?
            FROM clan_members m JOIN clan_member_notification_preferences p
              ON p.clan_id = m.clan_id AND p.character_id = m.character_id
            WHERE m.clan_id = ? AND m.rank >= ? AND p.recruiter_notifications_enabled
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, applicantId)
                it.setInt(2, kind.storageId)
                it.setLong(3, clanId)
                it.setInt(4, threshold)
                it.executeUpdate()
            }
    }

    private fun bump(connection: DatabaseConnection, clanId: Long) {
        connection
            .prepareStatement(
                """
            UPDATE clans SET state_version = state_version + 1,
                updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                check(it.executeUpdate() == 1)
            }
    }

    private fun bindEvent(
        statement: java.sql.PreparedStatement,
        clanId: Long,
        actorId: Int,
        event: ClanEventDraft,
        startsAt: Instant,
    ) {
        statement.setLong(1, clanId)
        statement.setInt(2, event.type.storageId)
        statement.setInt(3, event.subtype.storageId)
        statement.setString(4, event.activityKey)
        statement.setTimestamp(5, Timestamp.from(startsAt))
        statement.setLong(6, event.durationTicks)
        statement.setInt(7, event.minimumJoinRank)
        statement.setInt(8, event.worldId)
        statement.setInt(9, actorId)
        statement.setInt(10, actorId)
    }

    private fun startInstant(event: ClanEventDraft, now: Instant): Instant =
        now.atZone(ZoneOffset.UTC)
            .toLocalDate()
            .plusDays(event.daysUntilStart.toLong())
            .atTime(event.startHourUtc, event.startMinuteUtc)
            .toInstant(ZoneOffset.UTC)

    private fun eventRecord(id: Long, event: ClanEventDraft, startsAt: Instant): ClanEventRecord =
        ClanEventRecord(
            id,
            event.type,
            event.subtype,
            event.activityKey,
            startsAt.toEpochMilli(),
            event.durationTicks,
            event.minimumJoinRank,
            event.worldId,
        )

    private fun motifIcon(rows: ResultSet, column: Int): ClanMotifIcon? {
        val id = rows.getInt(column)
        if (rows.wasNull()) return null
        return ClanMotifIcon.entries.find { it.storageId == id }
    }

    private fun java.sql.PreparedStatement.setNullableInt(index: Int, value: Int?) {
        if (value == null) setNull(index, java.sql.Types.INTEGER) else setInt(index, value)
    }

    private fun java.sql.PreparedStatement.setNullableTimestamp(index: Int, epochMillis: Long?) {
        if (epochMillis == null) {
            setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        } else {
            setTimestamp(index, Timestamp.from(Instant.ofEpochMilli(epochMillis)))
        }
    }

    private fun ResultSet.getNullableInt(column: Int): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun <T> success(clanId: Long, value: T): ClanManagementResult<ClanDomainMutation<T>> =
        ClanManagementResult.Success(ClanDomainMutation(clanId, value))

    private fun <T> denied(message: String): ClanManagementResult<T> =
        ClanManagementResult.Denied(message)

    private data class Actor(val clanId: Long, val rank: Int)

    private data class ClanNameState(
        val id: Long,
        val normalizedName: String,
        val changedAt: Instant?,
    )

    private data class ExistingCoffer(val clanId: Long)
}
