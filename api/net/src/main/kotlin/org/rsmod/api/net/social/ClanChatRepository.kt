package org.rsmod.api.net.social

import jakarta.inject.Inject
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection

private const val EVENT_STARTING_REMINDER_SECONDS: Long = 60L * 60L

public class ClanChatRepository @Inject constructor(private val identities: FriendChatRepository) {
    public fun loadLoginSnapshot(
        connection: DatabaseConnection,
        characterId: Int,
    ): ClanLoginSnapshot {
        val ownId = findOwnClanId(connection, characterId)
        val guestId =
            connection
                .prepareStatement(
                    "SELECT guest_clan_id FROM social_channel_affinity WHERE character_id = ?"
                )
                .use { statement ->
                    statement.setInt(1, characterId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getLongOrNull(1) else null
                    }
                }
        return ClanLoginSnapshot(
            ownClan = ownId?.let { loadClan(connection, it) },
            guestClan = guestId?.takeIf { it != ownId }?.let { loadClan(connection, it) },
        )
    }

    public fun findOwnClanId(connection: DatabaseConnection, characterId: Int): Long? =
        connection
            .prepareStatement("SELECT clan_id FROM clan_members WHERE character_id = ?")
            .use { statement ->
                statement.setInt(1, characterId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }

    /** Returns ranked members who currently permit clan broadcasts. Guests are never included. */
    public fun broadcastRecipientIds(connection: DatabaseConnection, clanId: Long): Set<Int> =
        connection
            .prepareStatement(
                """
                SELECT m.character_id
                FROM clan_members m
                JOIN clan_member_notification_preferences p
                  ON p.clan_id = m.clan_id AND p.character_id = m.character_id
                WHERE m.clan_id = ? AND p.broadcasts_enabled
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.executeQuery().use { rows ->
                    buildSet { while (rows.next()) add(rows.getInt(1)) }
                }
            }

    /** Returns every ranked clan member for mandatory system notices; guests are excluded. */
    public fun rankedMemberIds(connection: DatabaseConnection, clanId: Long): Set<Int> =
        connection
            .prepareStatement("SELECT character_id FROM clan_members WHERE clan_id = ?")
            .use { statement ->
                statement.setLong(1, clanId)
                statement.executeQuery().use { rows ->
                    buildSet { while (rows.next()) add(rows.getInt(1)) }
                }
            }

    /**
     * Atomically claims and returns this member's eligible events starting within the next hour.
     * The claim is durable per member and event, so login and periodic refreshes cannot duplicate a
     * reminder.
     */
    public fun claimStartingEventReminders(
        connection: DatabaseConnection,
        characterId: Int,
        now: Instant = Instant.now(),
    ): List<ClanEventRecord> {
        val windowEnd = now.plusSeconds(EVENT_STARTING_REMINDER_SECONDS)
        return connection
            .prepareStatement(
                """
                WITH eligible AS (
                    SELECT e.id, e.event_type, e.event_subtype, e.activity_key, e.starts_at,
                           e.duration_ticks, e.minimum_join_rank, e.world_id
                    FROM clan_events e
                    JOIN clan_members m ON m.clan_id = e.clan_id
                    JOIN clan_member_notification_preferences p
                      ON p.clan_id = m.clan_id AND p.character_id = m.character_id
                    JOIN clan_broadcast_settings b ON b.clan_id = e.clan_id
                    WHERE m.character_id = ?
                      AND p.event_reminders_enabled
                      AND b.event_starting_notifications
                      AND e.minimum_join_rank <= m.rank
                      AND e.starts_at >= ?
                      AND e.starts_at < ?
                ), claimed AS (
                    INSERT INTO clan_event_notification_claims
                        (event_id, character_id, notified_at)
                    SELECT id, ?, ? FROM eligible
                    ON CONFLICT (event_id, character_id) DO NOTHING
                    RETURNING event_id
                )
                SELECT e.id, e.event_type, e.event_subtype, e.activity_key, e.starts_at,
                       e.duration_ticks, e.minimum_join_rank, e.world_id
                FROM eligible e
                JOIN claimed c ON c.event_id = e.id
                ORDER BY e.starts_at, e.id
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.setTimestamp(2, java.sql.Timestamp.from(now))
                statement.setTimestamp(3, java.sql.Timestamp.from(windowEnd))
                statement.setInt(4, characterId)
                statement.setTimestamp(5, java.sql.Timestamp.from(now))
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val type =
                                ClanEventType.entries.firstOrNull {
                                    it.storageId == rows.getInt("event_type")
                                } ?: continue
                            val subtype =
                                ClanEventSubtype.entries.firstOrNull {
                                    it.storageId == rows.getInt("event_subtype")
                                } ?: continue
                            add(
                                ClanEventRecord(
                                    id = rows.getLong("id"),
                                    type = type,
                                    subtype = subtype,
                                    activityKey = rows.getString("activity_key"),
                                    startsAtEpochMillis = rows.getTimestamp("starts_at").time,
                                    durationTicks = rows.getLong("duration_ticks"),
                                    minimumJoinRank = rows.getInt("minimum_join_rank"),
                                    worldId = rows.getInt("world_id"),
                                )
                            )
                        }
                    }
                }
            }
    }

    public fun lockClan(connection: DatabaseConnection, clanId: Long) {
        connection.prepareStatement("SELECT id FROM clans WHERE id = ? FOR UPDATE").use { statement
            ->
            statement.setLong(1, clanId)
            check(statement.executeQuery().use { it.next() }) {
                "Clan $clanId disappeared while applying a social mutation."
            }
        }
    }

    public fun findClanByName(
        connection: DatabaseConnection,
        requestedName: String,
    ): ClanSnapshot? {
        val normalized = SocialNameNormalizer.normalize(requestedName).take(12)
        if (normalized.isBlank()) return null
        val clanId =
            connection.prepareStatement("SELECT id FROM clans WHERE normalized_name = ?").use {
                statement ->
                statement.setString(1, normalized)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }
        return clanId?.let { loadClan(connection, it) }
    }

    public fun loadClan(connection: DatabaseConnection, clanId: Long): ClanSnapshot? {
        val header =
            connection
                .prepareStatement(
                    """
                    SELECT id, display_name, owner_character_id, creation_rune_day, home_world,
                           allow_guests, guests_can_talk, talk_rank, kick_rank, ban_rank,
                           expel_rank, assign_rank, edit_permissions_rank, state_version
                    FROM clans WHERE id = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setLong(1, clanId)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) return null
                        ClanHeader(
                            rows.getLong("id"),
                            rows.getString("display_name"),
                            rows.getInt("owner_character_id"),
                            rows.getInt("creation_rune_day"),
                            rows.getInt("home_world"),
                            rows.getBoolean("allow_guests"),
                            rows.getBoolean("guests_can_talk"),
                            rows.getInt("talk_rank"),
                            rows.getInt("kick_rank"),
                            rows.getInt("ban_rank"),
                            rows.getInt("expel_rank"),
                            rows.getInt("assign_rank"),
                            rows.getInt("edit_permissions_rank"),
                            rows.getLong("state_version"),
                        )
                    }
                }
        val members = ArrayList<ClanMemberSnapshot>()
        connection
            .prepareStatement(
                """
                SELECT c.id, c.display_name, c.previous_display_name,
                       m.last_seen_at, m.last_seen_visible,
                       m.rank, m.title_slot, m.muted, m.joined_rune_day,
                       COALESCE(c.online_central_world_id, 0) AS world_id
                FROM clan_members m
                JOIN account_characters c ON c.id = m.character_id
                WHERE m.clan_id = ?
                ORDER BY m.rank DESC, LOWER(c.display_name)
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        members +=
                            ClanMemberSnapshot(
                                characterId = rows.getInt("id"),
                                displayName = rows.getString("display_name") ?: "",
                                previousDisplayName = rows.getString("previous_display_name") ?: "",
                                rank = rows.getInt("rank"),
                                titleSlot = rows.getInt("title_slot"),
                                muted = rows.getBoolean("muted"),
                                joinedRuneDay = rows.getInt("joined_rune_day"),
                                worldId = rows.getInt("world_id"),
                                lastSeenEpochMillis = rows.getTimestamp("last_seen_at")?.time,
                                lastSeenVisible = rows.getBoolean("last_seen_visible"),
                            )
                    }
                }
            }
        val bans = ArrayList<ClanBanSnapshot>()
        connection
            .prepareStatement(
                """
                SELECT c.id, c.display_name
                FROM clan_bans b JOIN account_characters c ON c.id = b.character_id
                WHERE b.clan_id = ? ORDER BY LOWER(c.display_name)
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        bans += ClanBanSnapshot(rows.getInt("id"), rows.getString("display_name"))
                    }
                }
            }
        val titles = ArrayList<ClanRankTitle>()
        connection
            .prepareStatement(
                "SELECT rank_slot, title_id, title_name FROM clan_rank_titles WHERE clan_id = ? ORDER BY rank_slot"
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        titles +=
                            ClanRankTitle(
                                rows.getInt("rank_slot"),
                                rows.getInt("title_id"),
                                rows.getString("title_name"),
                            )
                    }
                }
            }
        val onlineGuests = ArrayList<ClanGuestPresence>()
        connection
            .prepareStatement(
                """
                SELECT c.id, c.display_name, p.world_id
                FROM social_chat_presence p
                JOIN account_characters c ON c.id = p.character_id
                WHERE p.guest_clan_id = ?
                  AND c.online_central_world_id = p.world_id
                ORDER BY LOWER(c.display_name)
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        onlineGuests +=
                            ClanGuestPresence(
                                rows.getInt("id"),
                                rows.getString("display_name") ?: "",
                                rows.getInt("world_id"),
                            )
                    }
                }
            }
        return ClanSnapshot(
            clanId = header.id,
            displayName = header.displayName,
            ownerCharacterId = header.ownerId,
            creationRuneDay = header.creationRuneDay,
            homeWorld = header.homeWorld,
            allowGuests = header.allowGuests,
            guestsCanTalk = header.guestsCanTalk,
            talkRank = header.talkRank,
            kickRank = header.kickRank,
            banRank = header.banRank,
            expelRank = header.expelRank,
            assignRank = header.assignRank,
            editPermissionsRank = header.editPermissionsRank,
            stateVersion = header.version,
            members = members,
            bans = bans,
            titles = titles,
            onlineGuests = onlineGuests,
            interests = loadInterests(connection, clanId),
            permissionSet = loadPermissionSet(connection, clanId),
            motif = loadMotif(connection, clanId),
            guestRequirements = loadGuestRequirements(connection, clanId),
            hallSettings = loadHallSettings(connection, clanId),
            cofferBalance = loadCofferBalance(connection, clanId),
            broadcastSettings = loadBroadcastSettings(connection, clanId),
        )
    }

    public fun joinGuest(
        connection: DatabaseConnection,
        characterId: Int,
        requestedName: String,
    ): ClanGuestJoinResult {
        clearExpiredGuestKicks(connection)
        val clan =
            findClanByName(connection, requestedName)
                ?: return ClanGuestJoinResult(null, banned = false, kickedUntilEpochMillis = null)
        val banned = clan.bans.any { it.characterId == characterId }
        val kickedUntil =
            connection
                .prepareStatement(
                    "SELECT expires_at FROM clan_guest_kicks WHERE clan_id = ? AND character_id = ?"
                )
                .use { statement ->
                    statement.setLong(1, clan.clanId)
                    statement.setInt(2, characterId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getTimestamp(1).time else null
                    }
                }
        if (!banned && kickedUntil == null && clan.allowGuests) {
            setGuestAffinity(connection, characterId, clan.clanId)
        }
        return ClanGuestJoinResult(clan, banned, kickedUntil)
    }

    public fun setGuestAffinity(connection: DatabaseConnection, characterId: Int, clanId: Long?) {
        connection
            .prepareStatement(
                """
                INSERT INTO social_channel_affinity (character_id, guest_clan_id)
                VALUES (?, ?)
                ON CONFLICT (character_id) DO UPDATE
                SET guest_clan_id = EXCLUDED.guest_clan_id, updated_at = CURRENT_TIMESTAMP
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                if (clanId == null) statement.setNull(2, java.sql.Types.BIGINT)
                else statement.setLong(2, clanId)
                statement.executeUpdate()
            }
    }

    public fun setFriendChatAffinity(
        connection: DatabaseConnection,
        characterId: Int,
        ownerCharacterId: Int?,
    ) {
        connection
            .prepareStatement(
                """
                INSERT INTO social_channel_affinity (character_id, friend_chat_owner_id)
                VALUES (?, ?)
                ON CONFLICT (character_id) DO UPDATE
                SET friend_chat_owner_id = EXCLUDED.friend_chat_owner_id, updated_at = CURRENT_TIMESTAMP
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                if (ownerCharacterId == null) statement.setNull(2, java.sql.Types.INTEGER)
                else statement.setInt(2, ownerCharacterId)
                statement.executeUpdate()
            }
    }

    public fun friendChatAffinity(connection: DatabaseConnection, characterId: Int): String? =
        connection
            .prepareStatement(
                """
                SELECT c.display_name
                FROM social_channel_affinity a
                JOIN account_characters c ON c.id = a.friend_chat_owner_id
                WHERE a.character_id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) else null
                }
            }

    public fun touchPresence(
        connection: DatabaseConnection,
        characterId: Int,
        worldId: Int,
        friendChatOwnerId: Int?,
        ownClanId: Long?,
        guestClanId: Long?,
        generation: Long,
    ) {
        connection
            .prepareStatement(
                """
                INSERT INTO social_chat_presence
                    (character_id, world_id, friend_chat_owner_id, own_clan_id, guest_clan_id,
                     session_generation, touched_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (character_id) DO UPDATE SET
                    world_id = EXCLUDED.world_id,
                    friend_chat_owner_id = COALESCE(EXCLUDED.friend_chat_owner_id, social_chat_presence.friend_chat_owner_id),
                    own_clan_id = EXCLUDED.own_clan_id,
                    guest_clan_id = EXCLUDED.guest_clan_id,
                    session_generation = EXCLUDED.session_generation,
                    touched_at = CURRENT_TIMESTAMP
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.setInt(2, worldId)
                statement.setNullableInt(3, friendChatOwnerId)
                statement.setNullableLong(4, ownClanId)
                statement.setNullableLong(5, guestClanId)
                statement.setLong(6, generation)
                statement.executeUpdate()
            }
    }

    public fun clearPresence(connection: DatabaseConnection, characterId: Int, generation: Long) {
        connection
            .prepareStatement(
                "DELETE FROM social_chat_presence WHERE character_id = ? AND session_generation = ?"
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.setLong(2, generation)
                statement.executeUpdate()
            }
        connection
            .prepareStatement(
                "UPDATE clan_members SET last_seen_at = CURRENT_TIMESTAMP WHERE character_id = ?"
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.executeUpdate()
            }
    }

    public fun markMemberLogin(connection: DatabaseConnection, characterId: Int) {
        connection
            .prepareStatement(
                """
                UPDATE clan_members
                SET last_login_at = CURRENT_TIMESTAMP, last_seen_at = CURRENT_TIMESTAMP
                WHERE character_id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.executeUpdate()
            }
    }

    public fun insertEvent(
        connection: DatabaseConnection,
        requestId: UUID,
        sourceWorldId: Int,
        channelKind: SocialChannelKind,
        channelId: Long,
        eventKind: Int,
        actorCharacterId: Int,
        actorName: String,
        actorCrown: Int,
        counter: Int,
        payload: String,
    ) {
        connection
            .prepareStatement(
                """
                INSERT INTO social_chat_events
                    (request_id, source_world_id, channel_kind, channel_id, event_kind,
                     actor_character_id, actor_name, actor_crown, world_message_counter, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (request_id) DO NOTHING
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setObject(1, requestId)
                statement.setInt(2, sourceWorldId)
                statement.setInt(3, channelKind.wireId)
                statement.setLong(4, channelId)
                statement.setInt(5, eventKind)
                statement.setInt(6, actorCharacterId)
                statement.setString(7, actorName.take(12))
                statement.setInt(8, actorCrown)
                statement.setInt(9, counter)
                statement.setString(10, payload.take(160))
                statement.executeUpdate()
            }
    }

    private fun loadInterests(connection: DatabaseConnection, clanId: Long): Set<ClanInterest> {
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
                            .firstOrNull { interest -> interest.storageId == rows.getInt(1) }
                            ?.let(interests::add)
                    }
                }
            }
        return interests
    }

    private fun loadPermissionSet(connection: DatabaseConnection, clanId: Long): ClanPermissionSet {
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

    private fun loadMotif(connection: DatabaseConnection, clanId: Long): ClanMotif =
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
                    check(rows.next()) { "Clan $clanId disappeared while loading its motif." }
                    ClanMotif(
                        primaryColour =
                            requireNotNull(
                                ClanMotifColour.entries.firstOrNull { colour ->
                                    colour.storageId == rows.getInt("motif_primary_colour")
                                }
                            ),
                        secondaryColour =
                            requireNotNull(
                                ClanMotifColour.entries.firstOrNull { colour ->
                                    colour.storageId == rows.getInt("motif_primary_colour")
                                }
                            ),
                        primarySymbol = motifIcon(rows, "motif_primary_symbol"),
                        secondarySymbol = motifIcon(rows, "motif_secondary_symbol"),
                    )
                }
            }

    private fun loadGuestRequirements(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanGuestRequirements =
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
                    check(rows.next()) {
                        "Clan $clanId disappeared while loading its guest requirements."
                    }
                    ClanGuestRequirements(
                        minimumCombatLevel = rows.getInt("guest_minimum_combat_level"),
                        minimumTotalLevel = rows.getInt("guest_minimum_total_level"),
                        minimumQuestPoints = rows.getInt("guest_minimum_quest_points"),
                        ironRequirement =
                            requireNotNull(
                                ClanGuestIronRequirement.entries.firstOrNull { requirement ->
                                    requirement.storageId == rows.getInt("guest_iron_requirement")
                                }
                            ),
                    )
                }
            }

    private fun loadHallSettings(connection: DatabaseConnection, clanId: Long): ClanHallSettings =
        connection
            .prepareStatement(
                """
                SELECT home_world_only, locked, player_capacity, theme_key, identity_key,
                       edit_state_version, edit_session_character_id, edit_session_started_at,
                       edit_session_expires_at
                FROM clan_hall_settings WHERE clan_id = ?
                """
                    .trimIndent()
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    check(rows.next()) { "Clan $clanId has no canonical Hall settings." }
                    ClanHallSettings(
                        homeWorldOnly = rows.getBoolean("home_world_only"),
                        locked = rows.getBoolean("locked"),
                        playerCapacity = rows.getInt("player_capacity"),
                        themeKey = rows.getString("theme_key"),
                        identityKey = rows.getString("identity_key"),
                        editStateVersion = rows.getLong("edit_state_version"),
                        editSessionCharacterId = rows.getIntOrNull("edit_session_character_id"),
                        editSessionStartedAtEpochMillis =
                            rows.getTimestamp("edit_session_started_at")?.time,
                        editSessionExpiresAtEpochMillis =
                            rows.getTimestamp("edit_session_expires_at")?.time,
                    )
                }
            }

    private fun loadCofferBalance(connection: DatabaseConnection, clanId: Long): Long =
        connection.prepareStatement("SELECT balance FROM clan_coffers WHERE clan_id = ?").use {
            it.setLong(1, clanId)
            it.executeQuery().use { rows ->
                check(rows.next()) { "Clan $clanId has no canonical coffer." }
                rows.getLong("balance")
            }
        }

    private fun loadBroadcastSettings(
        connection: DatabaseConnection,
        clanId: Long,
    ): ClanBroadcastSettings {
        val ranks = ClanBroadcastSettings.defaults().minimumRanks.toMutableMap()
        connection
            .prepareStatement(
                "SELECT broadcast_key, minimum_rank FROM clan_broadcast_permissions WHERE clan_id = ?"
            )
            .use {
                it.setLong(1, clanId)
                it.executeQuery().use { rows ->
                    while (rows.next()) {
                        ClanBroadcastKey.fromStorageKey(rows.getString(1))?.let { key ->
                            ranks[key] = rows.getInt(2)
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
                    check(rows.next()) { "Clan $clanId has no canonical broadcast settings." }
                    ClanBroadcastSettings(
                        minimumRanks = ranks,
                        levelInterval =
                            ClanLevelBroadcastInterval.entries.first { interval ->
                                interval.levels == rows.getInt("level_interval")
                            },
                        post99XpInterval = rows.getInt("post_99_xp_interval"),
                        collectionInterval =
                            ClanCollectionBroadcastInterval.entries.first { interval ->
                                interval.slots == rows.getInt("collection_interval")
                            },
                        minimumLootValue = rows.getInt("minimum_loot_value"),
                        minimumPlayerKillValue = rows.getInt("minimum_player_kill_value"),
                        minimumLootKeyValue = rows.getInt("minimum_loot_key_value"),
                        minimumCombatAchievementTier =
                            ClanCombatAchievementTier.entries.first { tier ->
                                tier.storageId == rows.getInt("minimum_combat_achievement_tier")
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

    private fun motifIcon(rows: ResultSet, column: String): ClanMotifIcon? {
        val id = rows.getInt(column)
        if (rows.wasNull()) return null
        return ClanMotifIcon.entries.firstOrNull { it.storageId == id }
    }

    private fun clearExpiredGuestKicks(connection: DatabaseConnection) {
        connection
            .prepareStatement("DELETE FROM clan_guest_kicks WHERE expires_at <= CURRENT_TIMESTAMP")
            .use { it.executeUpdate() }
    }

    private data class ClanHeader(
        val id: Long,
        val displayName: String,
        val ownerId: Int,
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
        val version: Long,
    )

    private fun ResultSet.getLongOrNull(column: Int): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getIntOrNull(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun java.sql.PreparedStatement.setNullableInt(index: Int, value: Int?) {
        if (value == null) setNull(index, java.sql.Types.INTEGER) else setInt(index, value)
    }

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)
    }
}
