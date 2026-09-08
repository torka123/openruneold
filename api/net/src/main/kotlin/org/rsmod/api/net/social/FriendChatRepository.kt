package org.rsmod.api.net.social

import jakarta.inject.Inject
import org.rsmod.api.db.DatabaseConnection

public class FriendChatRepository @Inject constructor() {
    public fun resolveIdentity(
        connection: DatabaseConnection,
        requestedName: String,
    ): SocialIdentity? {
        val normalized = SocialNameNormalizer.normalize(requestedName)
        if (normalized.isBlank()) return null
        val sql =
            """
            SELECT id, display_name, previous_display_name
            FROM account_characters
            WHERE LOWER(REGEXP_REPLACE(REPLACE(COALESCE(display_name, ''), '_', ' '), '\s+', ' ', 'g')) = ?
               OR LOWER(REGEXP_REPLACE(REPLACE(COALESCE(previous_display_name, ''), '_', ' '), '\s+', ' ', 'g')) = ?
            ORDER BY CASE WHEN LOWER(REGEXP_REPLACE(REPLACE(COALESCE(display_name, ''), '_', ' '), '\s+', ' ', 'g')) = ? THEN 0 ELSE 1 END
            LIMIT 1
            """
                .trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, normalized)
            statement.setString(2, normalized)
            statement.setString(3, normalized)
            statement.executeQuery().use { rows -> if (!rows.next()) null else rows.toIdentity() }
        }
    }

    public fun ensureSettings(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
        ownerDisplayName: String,
    ) {
        val channelName = SocialNameNormalizer.channelName(ownerDisplayName).ifBlank { "Friends" }
        connection
            .prepareStatement(
                """
            INSERT INTO friend_chat_settings (owner_character_id, channel_name, enabled)
            VALUES (?, ?, FALSE)
            ON CONFLICT (owner_character_id) DO NOTHING
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, ownerCharacterId)
                statement.setString(2, channelName)
                statement.executeUpdate()
            }
    }

    public fun loadRuntime(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
        ownerDisplayName: String,
    ): SocialRuntimeSnapshot {
        ensureSettings(connection, ownerCharacterId, ownerDisplayName)
        val friends = ArrayList<SocialFriend>()
        connection
            .prepareStatement(
                """
            SELECT c.id, c.display_name, c.previous_display_name, COALESCE(r.rank, 0) AS friend_chat_rank
            FROM character_friends f
            JOIN account_characters c ON c.id = f.friend_character_id
            LEFT JOIN friend_chat_ranks r
              ON r.owner_character_id = f.owner_character_id
             AND r.target_character_id = f.friend_character_id
            WHERE f.owner_character_id = ?
            ORDER BY LOWER(c.display_name)
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, ownerCharacterId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        friends += SocialFriend(rows.toIdentity(), rows.getInt("friend_chat_rank"))
                    }
                }
            }
        val ignores = HashSet<Int>()
        connection
            .prepareStatement(
                "SELECT ignored_character_id FROM character_ignores WHERE owner_character_id = ?"
            )
            .use { statement ->
                statement.setInt(1, ownerCharacterId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) ignores += rows.getInt(1)
                }
            }
        return SocialRuntimeSnapshot(friends, ignores)
    }

    public fun loadJoinSnapshot(
        connection: DatabaseConnection,
        requesterCharacterId: Int,
        ownerName: String,
    ): FriendChatJoinSnapshot? {
        val owner = resolveIdentity(connection, ownerName) ?: return null
        ensureSettings(connection, owner.characterId, owner.displayName)
        val settings = loadSettings(connection, owner.characterId) ?: return null
        val rank =
            if (owner.characterId == requesterCharacterId) {
                7
            } else {
                connection
                    .prepareStatement(
                        """
                    SELECT COALESCE(r.rank, 0)
                    FROM character_friends f
                    LEFT JOIN friend_chat_ranks r
                      ON r.owner_character_id = f.owner_character_id
                     AND r.target_character_id = f.friend_character_id
                    WHERE f.owner_character_id = ? AND f.friend_character_id = ?
                    """
                            .trimIndent()
                    )
                    .use { statement ->
                        statement.setInt(1, owner.characterId)
                        statement.setInt(2, requesterCharacterId)
                        statement.executeQuery().use { rows ->
                            if (rows.next()) rows.getInt(1) else -1
                        }
                    }
            }
        val ignored =
            connection
                .prepareStatement(
                    "SELECT 1 FROM character_ignores WHERE owner_character_id = ? AND ignored_character_id = ?"
                )
                .use { statement ->
                    statement.setInt(1, owner.characterId)
                    statement.setInt(2, requesterCharacterId)
                    statement.executeQuery().use { it.next() }
                }
        return FriendChatJoinSnapshot(owner, settings, rank, ignored)
    }

    public fun loadSettings(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
    ): FriendChatSettings? =
        connection
            .prepareStatement(
                """
            SELECT channel_name, enabled, join_rank, talk_rank, kick_rank
            FROM friend_chat_settings WHERE owner_character_id = ?
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, ownerCharacterId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return@use null
                    FriendChatSettings(
                        ownerCharacterId = ownerCharacterId,
                        channelName = rows.getString("channel_name"),
                        enabled = rows.getBoolean("enabled"),
                        joinPermission =
                            FriendChatPermission.fromWire(rows.getInt("join_rank"))
                                ?: FriendChatPermission.Anyone,
                        talkPermission =
                            FriendChatPermission.fromWire(rows.getInt("talk_rank"))
                                ?: FriendChatPermission.Anyone,
                        kickPermission =
                            FriendChatPermission.fromWire(rows.getInt("kick_rank"))
                                ?: FriendChatPermission.Owner,
                    )
                }
            }

    public fun setChannelName(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
        ownerDisplayName: String,
        requestedName: String,
    ): FriendChatSettings {
        ensureSettings(connection, ownerCharacterId, ownerDisplayName)
        val name = SocialNameNormalizer.channelName(requestedName)
        require(name.isNotBlank()) { "Channel name must contain letters or numbers." }
        connection
            .prepareStatement(
                "UPDATE friend_chat_settings SET channel_name = ?, enabled = TRUE, updated_at = CURRENT_TIMESTAMP WHERE owner_character_id = ?"
            )
            .use { statement ->
                statement.setString(1, name)
                statement.setInt(2, ownerCharacterId)
                statement.executeUpdate()
            }
        return checkNotNull(loadSettings(connection, ownerCharacterId))
    }

    public fun setEnabled(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
        ownerDisplayName: String,
        enabled: Boolean,
    ): FriendChatSettings {
        ensureSettings(connection, ownerCharacterId, ownerDisplayName)
        connection
            .prepareStatement(
                "UPDATE friend_chat_settings SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE owner_character_id = ?"
            )
            .use { statement ->
                statement.setBoolean(1, enabled)
                statement.setInt(2, ownerCharacterId)
                statement.executeUpdate()
            }
        return checkNotNull(loadSettings(connection, ownerCharacterId))
    }

    public fun setPermission(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
        ownerDisplayName: String,
        field: FriendChatPermissionField,
        permission: FriendChatPermission,
    ): FriendChatSettings {
        ensureSettings(connection, ownerCharacterId, ownerDisplayName)
        val column =
            when (field) {
                FriendChatPermissionField.Join -> "join_rank"
                FriendChatPermissionField.Talk -> "talk_rank"
                FriendChatPermissionField.Kick -> "kick_rank"
            }
        connection
            .prepareStatement(
                "UPDATE friend_chat_settings SET $column = ?, updated_at = CURRENT_TIMESTAMP WHERE owner_character_id = ?"
            )
            .use { statement ->
                statement.setInt(1, permission.wireId)
                statement.setInt(2, ownerCharacterId)
                statement.executeUpdate()
            }
        return checkNotNull(loadSettings(connection, ownerCharacterId))
    }

    public fun setRank(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
        targetName: String,
        rank: Int,
    ): FriendChatRankUpdate? {
        val target = resolveIdentity(connection, targetName) ?: return null
        if (target.characterId == ownerCharacterId) return null
        val isFriend =
            connection
                .prepareStatement(
                    "SELECT 1 FROM character_friends WHERE owner_character_id = ? AND friend_character_id = ?"
                )
                .use { statement ->
                    statement.setInt(1, ownerCharacterId)
                    statement.setInt(2, target.characterId)
                    statement.executeQuery().use { it.next() }
                }
        if (!isFriend) return null
        if (rank < 0) {
            deleteRank(connection, ownerCharacterId, target.characterId)
        } else {
            require(rank in 0..6) { "Invalid Friends Chat rank." }
            connection
                .prepareStatement(
                    """
                INSERT INTO friend_chat_ranks (owner_character_id, target_character_id, rank)
                VALUES (?, ?, ?)
                ON CONFLICT (owner_character_id, target_character_id)
                DO UPDATE SET rank = EXCLUDED.rank, updated_at = CURRENT_TIMESTAMP
                """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, ownerCharacterId)
                    statement.setInt(2, target.characterId)
                    statement.setInt(3, rank)
                    statement.executeUpdate()
                }
        }
        return FriendChatRankUpdate(target, rank.coerceAtLeast(0))
    }

    public fun deleteRank(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
        targetCharacterId: Int,
    ) {
        connection
            .prepareStatement(
                "DELETE FROM friend_chat_ranks WHERE owner_character_id = ? AND target_character_id = ?"
            )
            .use { statement ->
                statement.setInt(1, ownerCharacterId)
                statement.setInt(2, targetCharacterId)
                statement.executeUpdate()
            }
    }

    private fun java.sql.ResultSet.toIdentity(): SocialIdentity =
        SocialIdentity(
            characterId = getInt("id"),
            displayName = getString("display_name") ?: "",
            previousDisplayName = getString("previous_display_name") ?: "",
        )
}

public enum class FriendChatPermissionField {
    Join,
    Talk,
    Kick,
}
