package org.rsmod.api.net.social

import jakarta.inject.Inject
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection

public data class SocialChatEvent(
    val id: Long,
    val sourceWorldId: Int,
    val channelKind: SocialChannelKind,
    val channelId: Long,
    val eventKind: Int,
    val actorCharacterId: Int,
    val actorName: String,
    val actorCrown: Int,
    val worldMessageCounter: Int,
    val payload: String,
)

public class SocialChatEventRepository @Inject constructor() {
    public fun publish(
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
    ): Boolean =
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
                statement.executeUpdate() == 1
            }

    public fun poll(
        connection: DatabaseConnection,
        worldId: Int,
        limit: Int = 100,
    ): List<SocialChatEvent> {
        connection
            .prepareStatement(
                "INSERT INTO social_chat_world_offsets (world_id) VALUES (?) ON CONFLICT (world_id) DO NOTHING"
            )
            .use { statement ->
                statement.setInt(1, worldId)
                statement.executeUpdate()
            }
        val events = ArrayList<SocialChatEvent>()
        var lastId = 0L
        connection
            .prepareStatement(
                """
            SELECT e.id, e.source_world_id, e.channel_kind, e.channel_id, e.event_kind,
                   COALESCE(e.actor_character_id, 0) actor_character_id, e.actor_name,
                   e.actor_crown, e.world_message_counter, e.payload
            FROM social_chat_events e
            JOIN social_chat_world_offsets o ON o.world_id = ?
            WHERE e.id > o.last_event_id AND e.source_world_id <> ?
            ORDER BY e.id LIMIT ?
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, worldId)
                statement.setInt(2, worldId)
                statement.setInt(3, limit.coerceIn(1, 500))
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val kind =
                            SocialChannelKind.entries.firstOrNull {
                                it.wireId == rows.getInt("channel_kind")
                            } ?: continue
                        lastId = rows.getLong("id")
                        events +=
                            SocialChatEvent(
                                id = lastId,
                                sourceWorldId = rows.getInt("source_world_id"),
                                channelKind = kind,
                                channelId = rows.getLong("channel_id"),
                                eventKind = rows.getInt("event_kind"),
                                actorCharacterId = rows.getInt("actor_character_id"),
                                actorName = rows.getString("actor_name"),
                                actorCrown = rows.getInt("actor_crown"),
                                worldMessageCounter = rows.getInt("world_message_counter"),
                                payload = rows.getString("payload"),
                            )
                    }
                }
            }
        if (lastId > 0L) {
            connection
                .prepareStatement(
                    "UPDATE social_chat_world_offsets SET last_event_id = GREATEST(last_event_id, ?), updated_at = CURRENT_TIMESTAMP WHERE world_id = ?"
                )
                .use { statement ->
                    statement.setLong(1, lastId)
                    statement.setInt(2, worldId)
                    statement.executeUpdate()
                }
        }
        return events
    }
}
