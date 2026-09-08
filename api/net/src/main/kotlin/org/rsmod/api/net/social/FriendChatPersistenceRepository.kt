package org.rsmod.api.net.social

import jakarta.inject.Inject
import java.time.Instant
import org.rsmod.api.db.DatabaseConnection

public class FriendChatPersistenceRepository @Inject constructor() {
    public fun lastOwnerName(connection: DatabaseConnection, characterId: Int): String? =
        connection
            .prepareStatement(
                """
            SELECT c.display_name FROM social_channel_affinity a
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

    public fun setAffinity(connection: DatabaseConnection, characterId: Int, ownerId: Int?) {
        connection
            .prepareStatement(
                """
            INSERT INTO social_channel_affinity (character_id, friend_chat_owner_id)
            VALUES (?, ?)
            ON CONFLICT (character_id) DO UPDATE SET
                friend_chat_owner_id = EXCLUDED.friend_chat_owner_id,
                updated_at = CURRENT_TIMESTAMP
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                if (ownerId == null) statement.setNull(2, java.sql.Types.INTEGER)
                else statement.setInt(2, ownerId)
                statement.executeUpdate()
            }
    }

    public fun kickExpiry(connection: DatabaseConnection, ownerId: Int, characterId: Int): Long? {
        connection
            .prepareStatement("DELETE FROM friend_chat_kicks WHERE expires_at <= CURRENT_TIMESTAMP")
            .use { it.executeUpdate() }
        return connection
            .prepareStatement(
                "SELECT expires_at FROM friend_chat_kicks WHERE owner_character_id = ? AND character_id = ?"
            )
            .use { statement ->
                statement.setInt(1, ownerId)
                statement.setInt(2, characterId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getTimestamp(1).time else null
                }
            }
    }

    public fun setKick(
        connection: DatabaseConnection,
        ownerId: Int,
        characterId: Int,
        expiresAt: Instant,
    ) {
        connection
            .prepareStatement(
                """
            INSERT INTO friend_chat_kicks (owner_character_id, character_id, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT (owner_character_id, character_id) DO UPDATE SET expires_at = EXCLUDED.expires_at
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, ownerId)
                statement.setInt(2, characterId)
                statement.setTimestamp(3, java.sql.Timestamp.from(expiresAt))
                statement.executeUpdate()
            }
    }

    public fun clearKicks(connection: DatabaseConnection, ownerId: Int) {
        connection
            .prepareStatement("DELETE FROM friend_chat_kicks WHERE owner_character_id = ?")
            .use {
                it.setInt(1, ownerId)
                it.executeUpdate()
            }
    }
}
