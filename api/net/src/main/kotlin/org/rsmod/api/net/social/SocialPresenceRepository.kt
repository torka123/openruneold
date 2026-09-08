package org.rsmod.api.net.social

import jakarta.inject.Inject
import org.rsmod.api.db.DatabaseConnection

/** Shared transient presence projection used by every logical world. */
public class SocialPresenceRepository @Inject constructor() {
    public fun setFriendChannel(
        connection: DatabaseConnection,
        characterId: Int,
        worldId: Int,
        ownerCharacterId: Int?,
    ) {
        connection
            .prepareStatement(
                """
            INSERT INTO social_chat_presence
                (character_id, world_id, friend_chat_owner_id, session_generation, touched_at)
            VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (character_id) DO UPDATE SET
                world_id = EXCLUDED.world_id,
                friend_chat_owner_id = EXCLUDED.friend_chat_owner_id,
                touched_at = CURRENT_TIMESTAMP
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.setInt(2, worldId)
                if (ownerCharacterId == null) statement.setNull(3, java.sql.Types.INTEGER)
                else statement.setInt(3, ownerCharacterId)
                statement.executeUpdate()
            }
    }

    /**
     * Clears only an existing legacy-channel projection. This deliberately does not upsert: clan
     * logout cleanup may already have removed the shared row on another database worker, and a late
     * legacy cleanup must never recreate a ghost online session.
     */
    public fun clearFriendChannel(connection: DatabaseConnection, characterId: Int) {
        connection
            .prepareStatement(
                """
                UPDATE social_chat_presence
                SET friend_chat_owner_id = NULL, touched_at = CURRENT_TIMESTAMP
                WHERE character_id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.executeUpdate()
            }
    }

    public fun friendChannelMembers(
        connection: DatabaseConnection,
        ownerCharacterId: Int,
    ): List<FriendChatOnlineMember> {
        val members = ArrayList<FriendChatOnlineMember>()
        connection
            .prepareStatement(
                """
            SELECT c.id, c.display_name, p.world_id,
                   CASE
                     WHEN c.id = ? THEN 7
                     WHEN f.friend_character_id IS NULL THEN -1
                     ELSE COALESCE(r.rank, 0)
                   END AS channel_rank
            FROM social_chat_presence p
            JOIN account_characters c ON c.id = p.character_id
            LEFT JOIN character_friends f
              ON f.owner_character_id = ? AND f.friend_character_id = c.id
            LEFT JOIN friend_chat_ranks r
              ON r.owner_character_id = ? AND r.target_character_id = c.id
            WHERE p.friend_chat_owner_id = ?
              AND c.online_central_world_id = p.world_id
            ORDER BY LOWER(c.display_name)
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, ownerCharacterId)
                statement.setInt(2, ownerCharacterId)
                statement.setInt(3, ownerCharacterId)
                statement.setInt(4, ownerCharacterId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        members +=
                            FriendChatOnlineMember(
                                characterId = rows.getInt("id"),
                                displayName = rows.getString("display_name") ?: "",
                                worldId = rows.getInt("world_id"),
                                rank = rows.getInt("channel_rank"),
                            )
                    }
                }
            }
        return members
    }
}
