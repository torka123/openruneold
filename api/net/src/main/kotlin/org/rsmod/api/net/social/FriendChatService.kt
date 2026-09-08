package org.rsmod.api.net.social

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import net.rsprot.protocol.game.outgoing.friendchat.MessageFriendChannel
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFull.FriendChatEntry
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFullV2
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelSingleUser
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.net.central.writeFriendRankUpdate
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.social.isChatMuted
import org.rsmod.api.social.writeSocialMessage
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

@Singleton
public class FriendChatService
@Inject
constructor(
    private val db: GameDbManager,
    private val database: GameDatabase,
    private val repository: FriendChatRepository,
    private val persistence: FriendChatPersistenceRepository,
    private val presence: SocialPresenceRepository,
    private val events: SocialChatEventRepository,
    private val runtime: SocialRuntimeService,
    private val playerList: PlayerList,
    private val playerRegistry: PlayerRegistry,
    private val serverConfig: ServerConfig,
    private val eventBus: EventBus,
) {
    private val channels = HashMap<Int, ActiveChannel>()
    private val membership = HashMap<Int, Int>()
    private val messageCounter = AtomicInteger(messageCounterSeed())

    public fun prepareLogin(player: Player): Map<String, Int> {
        if (player.characterId <= 0) return emptyMap()
        val loaded =
            try {
                database.withTransactionBlocking { connection ->
                    Pair(
                        repository.loadRuntime(
                            connection,
                            player.characterId,
                            player.displayName.ifBlank { player.username },
                        ),
                        persistence.lastOwnerName(connection, player.characterId),
                    )
                }
            } catch (_: Exception) {
                null
            }
        if (loaded != null) runtime.install(player.characterId, loaded.first)
        resetClient(player)
        loaded?.second?.let { requestJoin(player, it) }
        return runtime.ranksByName(player.characterId)
    }

    public fun resetClient(player: Player) {
        player.client.write(
            UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate)
        )
    }

    public fun refreshClient(player: Player) {
        val ownerId = membership[player.characterId]
        val channel = ownerId?.let(channels::get)
        if (channel == null) {
            resetClient(player)
            return
        }
        sendFull(player, channel)
    }

    public fun requestJoin(player: Player, requestedOwnerName: String?) {
        if (requestedOwnerName.isNullOrBlank()) {
            leave(player)
            return
        }
        val uid = player.uid
        val requesterId = player.characterId
        db.request(
            request = { connection ->
                val snapshot =
                    repository.loadJoinSnapshot(connection, requesterId, requestedOwnerName)
                val kickedUntil =
                    snapshot?.let {
                        persistence.kickExpiry(connection, it.owner.characterId, requesterId)
                    }
                val onlineMembers =
                    snapshot
                        ?.let { presence.friendChannelMembers(connection, it.owner.characterId) }
                        .orEmpty()
                GameDbResult.Ok(FriendJoinRequest(snapshot, kickedUntil, onlineMembers))
            },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { request ->
                        if (request.snapshot == null) {
                            current.writeSocialMessage("That player does not exist.")
                        } else {
                            join(
                                current,
                                request.snapshot,
                                request.kickedUntil,
                                request.onlineMembers,
                            )
                        }
                    },
                    onErr = {
                        current.writeSocialMessage("Unable to join that Friends Chat right now.")
                    },
                )
            },
        )
    }

    public fun leave(player: Player, notifyClient: Boolean = true, remember: Boolean = false) {
        val ownerId = membership.remove(player.characterId)
        if (notifyClient) resetClient(player)
        if (ownerId == null) {
            return
        }
        if (!remember) {
            db.request(
                request = { connection ->
                    persistence.setAffinity(connection, player.characterId, null)
                    GameDbResult.Ok(Unit)
                },
                response = {},
            )
        }
        db.request(
            request = { connection ->
                presence.clearFriendChannel(connection, player.characterId)
                events.publish(
                    connection = connection,
                    requestId = UUID.randomUUID(),
                    sourceWorldId = serverConfig.world,
                    channelKind = SocialChannelKind.FriendChat,
                    channelId = ownerId.toLong(),
                    eventKind = EVENT_STATE_REFRESH,
                    actorCharacterId = player.characterId,
                    actorName = player.displayName.ifBlank { player.username },
                    actorCrown = player.modLevel.clientCode,
                    counter = 0,
                    payload = "",
                )
                GameDbResult.Ok(Unit)
            },
            response = {},
        )
        val channel = channels[ownerId] ?: return
        val removed = channel.members.remove(player.characterId) ?: return
        val packet =
            UpdateFriendChatChannelSingleUser(
                UpdateFriendChatChannelSingleUser.RemovedFriendChatUser(
                    removed.name,
                    removed.worldId,
                )
            )
        for (memberId in channel.members.keys) {
            playerRegistry.findOnlineByCharacterId(memberId)?.client?.write(packet)
        }
        if (FriendChatKickRules.clearWhenEmpty(channel.members.size)) {
            channels.remove(ownerId)
            db.request(
                request = { connection ->
                    persistence.clearKicks(connection, ownerId)
                    GameDbResult.Ok(Unit)
                },
                response = {},
            )
        }
    }

    public fun requestKick(player: Player, targetName: String) {
        val ownerId = membership[player.characterId]
        val channel = ownerId?.let(channels::get)
        if (channel == null) {
            player.writeSocialMessage("You are not in a Friends Chat channel.")
            return
        }
        val kicker = channel.members[player.characterId] ?: return
        val target =
            channel.members.values.firstOrNull {
                SocialNameNormalizer.normalize(it.name) ==
                    SocialNameNormalizer.normalize(targetName)
            }
        if (target == null) {
            player.writeSocialMessage("That player is not in this channel.")
            return
        }
        if (target.characterId == channel.owner.characterId) {
            player.writeSocialMessage("The channel owner cannot be kicked.")
            return
        }
        if (!channel.settings.kickPermission.allows(kicker.rank) || kicker.rank <= target.rank) {
            player.writeSocialMessage("You do not have a high enough rank to kick that player.")
            return
        }
        val uid = player.uid
        db.request(
            request = { connection ->
                persistence.setKick(
                    connection,
                    channel.owner.characterId,
                    target.characterId,
                    Instant.now().plus(KICK_BAN_DURATION),
                )
                persistence.setAffinity(connection, target.characterId, null)
                presence.clearFriendChannel(connection, target.characterId)
                events.publish(
                    connection = connection,
                    requestId = UUID.randomUUID(),
                    sourceWorldId = serverConfig.world,
                    channelKind = SocialChannelKind.FriendChat,
                    channelId = channel.owner.characterId.toLong(),
                    eventKind = EVENT_MEMBER_REMOVED,
                    actorCharacterId = target.characterId,
                    actorName = target.name,
                    actorCrown = 0,
                    counter = 0,
                    payload = "You have been kicked from the channel.",
                )
                events.publish(
                    connection = connection,
                    requestId = UUID.randomUUID(),
                    sourceWorldId = serverConfig.world,
                    channelKind = SocialChannelKind.FriendChat,
                    channelId = channel.owner.characterId.toLong(),
                    eventKind = EVENT_STATE_REFRESH,
                    actorCharacterId = player.characterId,
                    actorName = player.displayName.ifBlank { player.username },
                    actorCrown = player.modLevel.clientCode,
                    counter = 0,
                    payload = "",
                )
                GameDbResult.Ok(Unit)
            },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = {
                        removeMemberLocally(
                            channel.owner.characterId,
                            target.characterId,
                            "You have been kicked from the channel.",
                        )
                    },
                    onErr = { current.writeSocialMessage("Unable to kick that player right now.") },
                )
            },
        )
    }

    public fun requestSetRank(player: Player, targetName: String, wireRank: Int) {
        val rank = if (wireRank == 255) -1 else wireRank
        if (rank !in -1..6) {
            player.writeSocialMessage("Invalid Friends Chat rank.")
            return
        }
        val uid = player.uid
        val ownerId = player.characterId
        val ownerName = player.displayName.ifBlank { player.username }
        val ownerCrown = player.modLevel.clientCode
        db.request(
            request = { connection ->
                val update = repository.setRank(connection, ownerId, targetName, rank)
                if (update != null) {
                    events.publish(
                        connection = connection,
                        requestId = UUID.randomUUID(),
                        sourceWorldId = serverConfig.world,
                        channelKind = SocialChannelKind.FriendChat,
                        channelId = ownerId.toLong(),
                        eventKind = EVENT_STATE_REFRESH,
                        actorCharacterId = ownerId,
                        actorName = ownerName,
                        actorCrown = ownerCrown,
                        counter = 0,
                        payload = "",
                    )
                }
                GameDbResult.Ok(update)
            },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { update ->
                        if (update == null) {
                            current.writeSocialMessage(
                                "You can only rank players on your friends list."
                            )
                            return@fold
                        }
                        runtime.updateFriendRank(ownerId, update.target, update.rank)
                        onFriendRelationshipChanged(ownerId, update.target, update.rank)
                        current.writeFriendRankUpdate(
                            update.target,
                            update.rank,
                            playerRegistry.findOnlineByCharacterId(update.target.characterId),
                            serverConfig,
                        )
                    },
                    onErr = { current.writeSocialMessage("Unable to change that rank right now.") },
                )
            },
        )
    }

    public fun sendMessage(player: Player, rawMessage: String) {
        if (player.isChatMuted()) {
            player.writeSocialMessage("You are muted and cannot send messages.")
            return
        }
        val message = SocialChatText.normalize(rawMessage) ?: return
        if (!runtime.allowMessage(player.characterId)) {
            player.writeSocialMessage("You are sending messages too quickly.")
            return
        }
        val ownerId = membership[player.characterId]
        val channel = ownerId?.let(channels::get)
        val sender = channel?.members?.get(player.characterId)
        if (channel == null || sender == null) {
            player.writeSocialMessage("You are not in a Friends Chat channel.")
            return
        }
        if (!channel.settings.talkPermission.allows(sender.rank)) {
            player.writeSocialMessage("You do not have a high enough rank to talk in this channel.")
            return
        }
        val counter = nextMessageCounter()
        for (recipientId in channel.members.keys) {
            if (
                recipientId != player.characterId &&
                    runtime.isIgnoring(recipientId, player.characterId)
            )
                continue
            val recipient = playerRegistry.findOnlineByCharacterId(recipientId) ?: continue
            recipient.client.write(
                MessageFriendChannel(
                    sender = player.displayName.ifBlank { player.username },
                    channelName = channel.settings.channelName,
                    worldId = serverConfig.world,
                    worldMessageCounter = counter,
                    chatCrownType = player.modLevel.clientCode,
                    message = message,
                )
            )
        }
        db.request(
            request = { connection ->
                events.publish(
                    connection = connection,
                    requestId = UUID.randomUUID(),
                    sourceWorldId = serverConfig.world,
                    channelKind = SocialChannelKind.FriendChat,
                    channelId = ownerId.toLong(),
                    eventKind = 0,
                    actorCharacterId = player.characterId,
                    actorName = player.displayName.ifBlank { player.username },
                    actorCrown = player.modLevel.clientCode,
                    counter = counter,
                    payload = message,
                )
                GameDbResult.Ok(Unit)
            },
            response = {},
        )
    }

    public fun deliverRemoteMessage(event: SocialChatEvent) {
        if (event.eventKind == EVENT_MEMBER_REMOVED) {
            removeMemberLocally(event.channelId.toInt(), event.actorCharacterId, event.payload)
            return
        }
        if (event.eventKind == EVENT_STATE_REFRESH) {
            refreshRemoteRoster(event.channelId.toInt())
            return
        }
        if (event.eventKind != 0) return
        val channel = channels[event.channelId.toInt()] ?: return
        for (recipientId in channel.members.keys) {
            if (
                recipientId != event.actorCharacterId &&
                    runtime.isIgnoring(recipientId, event.actorCharacterId)
            ) {
                continue
            }
            playerRegistry
                .findOnlineByCharacterId(recipientId)
                ?.client
                ?.write(
                    MessageFriendChannel(
                        sender = event.actorName,
                        channelName = channel.settings.channelName,
                        worldId = event.sourceWorldId,
                        worldMessageCounter = event.worldMessageCounter,
                        chatCrownType = event.actorCrown,
                        message = event.payload,
                    )
                )
        }
    }

    private fun refreshRemoteRoster(ownerId: Int) {
        val channel = channels[ownerId] ?: return
        db.request(
            request = { connection ->
                GameDbResult.Ok(
                    repository.loadSettings(connection, ownerId) to
                        presence.friendChannelMembers(connection, ownerId)
                )
            },
            response = { result ->
                result.fold(
                    onOk = { (settings, online) ->
                        if (settings == null) return@fold
                        channel.settings = settings
                        val localMembers =
                            channel.members.values.filter {
                                playerRegistry.findOnlineByCharacterId(it.characterId) != null
                            }
                        channel.members.clear()
                        for (member in online) {
                            if (
                                member.characterId != ownerId &&
                                    !settings.joinPermission.allows(member.rank)
                            )
                                continue
                            channel.members[member.characterId] =
                                ActiveMember(
                                    member.characterId,
                                    member.displayName,
                                    member.worldId,
                                    member.rank,
                                )
                        }
                        for (member in localMembers) channel.members[member.characterId] = member
                        membership
                            .filterValues { it == ownerId }
                            .keys
                            .mapNotNull(playerRegistry::findOnlineByCharacterId)
                            .forEach { sendFull(it, channel) }
                    },
                    onErr = {},
                )
            },
        )
    }

    public fun openSettings(player: Player) {
        val uid = player.uid
        val ownerId = player.characterId
        val ownerName = player.displayName.ifBlank { player.username }
        db.request(
            request = { connection ->
                repository.ensureSettings(connection, ownerId, ownerName)
                GameDbResult.Ok(repository.loadSettings(connection, ownerId))
            },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { settings ->
                        if (settings == null)
                            current.writeSocialMessage("Unable to load Friends Chat settings.")
                        else showSettings(current, settings)
                    },
                    onErr = { current.writeSocialMessage("Unable to load Friends Chat settings.") },
                )
            },
        )
    }

    public fun requestChannelName(player: Player, requestedName: String) {
        updateSettings(player) { connection, ownerId, ownerName ->
            repository.setChannelName(connection, ownerId, ownerName, requestedName)
        }
    }

    public fun requestChannelEnabled(player: Player, enabled: Boolean) {
        updateSettings(player) { connection, ownerId, ownerName ->
            repository.setEnabled(connection, ownerId, ownerName, enabled)
        }
    }

    public fun requestPermission(
        player: Player,
        field: FriendChatPermissionField,
        permission: FriendChatPermission,
    ) {
        updateSettings(player) { connection, ownerId, ownerName ->
            repository.setPermission(connection, ownerId, ownerName, field, permission)
        }
    }

    public fun onLogout(player: Player) {
        leave(player, notifyClient = false, remember = true)
        runtime.clear(player.characterId)
    }

    private fun join(
        player: Player,
        snapshot: FriendChatJoinSnapshot,
        kickedUntil: Long?,
        onlineMembers: List<FriendChatOnlineMember>,
    ) {
        if (!snapshot.settings.enabled) {
            player.writeSocialMessage("That player has not enabled a Friends Chat channel.")
            return
        }
        if (snapshot.ownerIgnoresRequester) {
            player.writeSocialMessage("You are not allowed to join this channel.")
            return
        }
        if (FriendChatKickRules.isActive(kickedUntil, System.currentTimeMillis())) {
            player.writeSocialMessage("You are temporarily banned from this channel.")
            return
        }
        if (!snapshot.settings.joinPermission.allows(snapshot.requesterRank)) {
            player.writeSocialMessage("You do not have a high enough rank to join this channel.")
            return
        }
        val channel =
            channels.getOrPut(snapshot.owner.characterId) {
                ActiveChannel(snapshot.owner, snapshot.settings, LinkedHashMap())
            }
        channel.settings = snapshot.settings
        for (online in onlineMembers) {
            if (
                online.characterId != snapshot.owner.characterId &&
                    !snapshot.settings.joinPermission.allows(online.rank)
            )
                continue
            channel.members.putIfAbsent(
                online.characterId,
                ActiveMember(online.characterId, online.displayName, online.worldId, online.rank),
            )
        }
        if (
            player.characterId !in channel.members &&
                channel.members.size >= FriendChatCapacity.MAX_MEMBERS
        ) {
            val displacedId =
                FriendChatCapacity.displacementCandidate(
                    channel.members.mapValues { it.value.rank },
                    channel.owner.characterId,
                    snapshot.requesterRank,
                )
            if (displacedId == null) {
                player.writeSocialMessage("This Friends Chat channel is full.")
                return
            }
            val evicted = playerRegistry.findOnlineByCharacterId(displacedId)
            if (evicted != null) {
                leave(evicted)
                evicted.writeSocialMessage(
                    "You have been removed to make room for a higher-ranked member."
                )
            } else {
                val removed = channel.members.remove(displacedId)
                if (removed != null) {
                    db.request(
                        request = { connection ->
                            persistence.setAffinity(connection, displacedId, null)
                            presence.clearFriendChannel(connection, displacedId)
                            events.publish(
                                connection = connection,
                                requestId = UUID.randomUUID(),
                                sourceWorldId = serverConfig.world,
                                channelKind = SocialChannelKind.FriendChat,
                                channelId = channel.owner.characterId.toLong(),
                                eventKind = EVENT_MEMBER_REMOVED,
                                actorCharacterId = displacedId,
                                actorName = removed.name,
                                actorCrown = 0,
                                counter = 0,
                                payload =
                                    "You have been removed to make room for a higher-ranked member.",
                            )
                            GameDbResult.Ok(Unit)
                        },
                        response = {},
                    )
                }
            }
        }
        if (membership[player.characterId] != snapshot.owner.characterId) {
            leave(player, remember = true)
        }
        val member =
            ActiveMember(
                characterId = player.characterId,
                name = player.displayName.ifBlank { player.username },
                worldId = serverConfig.world,
                rank =
                    if (player.characterId == snapshot.owner.characterId) 7
                    else snapshot.requesterRank,
            )
        val wasPresent = channel.members.put(player.characterId, member) != null
        membership[player.characterId] = snapshot.owner.characterId
        db.request(
            request = { connection ->
                persistence.setAffinity(connection, player.characterId, snapshot.owner.characterId)
                presence.setFriendChannel(
                    connection,
                    player.characterId,
                    serverConfig.world,
                    snapshot.owner.characterId,
                )
                events.publish(
                    connection = connection,
                    requestId = UUID.randomUUID(),
                    sourceWorldId = serverConfig.world,
                    channelKind = SocialChannelKind.FriendChat,
                    channelId = snapshot.owner.characterId.toLong(),
                    eventKind = EVENT_STATE_REFRESH,
                    actorCharacterId = player.characterId,
                    actorName = player.displayName.ifBlank { player.username },
                    actorCrown = player.modLevel.clientCode,
                    counter = 0,
                    payload = "",
                )
                GameDbResult.Ok(Unit)
            },
            response = {},
        )
        if (!wasPresent) {
            val update =
                UpdateFriendChatChannelSingleUser(
                    UpdateFriendChatChannelSingleUser.AddedFriendChatUser(
                        member.name,
                        member.worldId,
                        member.rank,
                        worldName(),
                    )
                )
            for (otherId in channel.members.keys) {
                if (otherId != player.characterId)
                    playerRegistry.findOnlineByCharacterId(otherId)?.client?.write(update)
            }
        }
        sendFull(player, channel)
    }

    public fun onFriendRelationshipChanged(ownerId: Int, target: SocialIdentity, rank: Int) {
        val channel = channels[ownerId] ?: return
        val member = channel.members[target.characterId] ?: return
        member.rank = rank
        if (!channel.settings.joinPermission.allows(rank)) {
            val targetPlayer = playerRegistry.findOnlineByCharacterId(target.characterId)
            if (targetPlayer != null) {
                leave(targetPlayer)
                targetPlayer.writeSocialMessage(
                    "You no longer meet this channel's entry requirement."
                )
            } else {
                channel.members.remove(target.characterId)
                membership.remove(target.characterId)
            }
            return
        }
        val packet =
            UpdateFriendChatChannelSingleUser(
                UpdateFriendChatChannelSingleUser.AddedFriendChatUser(
                    member.name,
                    member.worldId,
                    member.rank,
                    worldName(),
                )
            )
        for (memberId in channel.members.keys) {
            playerRegistry.findOnlineByCharacterId(memberId)?.client?.write(packet)
        }
    }

    private fun updateSettings(
        player: Player,
        operation: (org.rsmod.api.db.DatabaseConnection, Int, String) -> FriendChatSettings,
    ) {
        val uid = player.uid
        val ownerId = player.characterId
        val ownerName = player.displayName.ifBlank { player.username }
        val ownerCrown = player.modLevel.clientCode
        db.request(
            request = { connection ->
                val settings = operation(connection, ownerId, ownerName)
                events.publish(
                    connection = connection,
                    requestId = UUID.randomUUID(),
                    sourceWorldId = serverConfig.world,
                    channelKind = SocialChannelKind.FriendChat,
                    channelId = ownerId.toLong(),
                    eventKind = EVENT_STATE_REFRESH,
                    actorCharacterId = ownerId,
                    actorName = ownerName,
                    actorCrown = ownerCrown,
                    counter = 0,
                    payload = "",
                )
                GameDbResult.Ok(settings)
            },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { settings ->
                        applySettings(ownerId, settings)
                        showSettings(current, settings)
                    },
                    onErr = { current.writeSocialMessage("Unable to save Friends Chat settings.") },
                )
            },
        )
    }

    private fun applySettings(ownerId: Int, settings: FriendChatSettings) {
        val channel = channels[ownerId] ?: return
        channel.settings = settings
        if (!settings.enabled) {
            val players = channel.members.keys.mapNotNull(playerRegistry::findOnlineByCharacterId)
            for (member in players) {
                leave(member)
                member.writeSocialMessage("This Friends Chat channel has been disabled.")
            }
            return
        }
        val disallowed =
            channel.members.values
                .filter { it.characterId != ownerId && !settings.joinPermission.allows(it.rank) }
                .mapNotNull { playerRegistry.findOnlineByCharacterId(it.characterId) }
        for (member in disallowed) {
            leave(member)
            member.writeSocialMessage("You no longer meet this channel's entry requirement.")
        }
        for (memberId in channel.members.keys) {
            playerRegistry.findOnlineByCharacterId(memberId)?.let { sendFull(it, channel) }
        }
    }

    private fun showSettings(player: Player, settings: FriendChatSettings) {
        player.ifOpenMainModal(SETTINGS_INTERFACE, eventBus)
        player.ifSetText(
            SETTINGS_NAME,
            if (settings.enabled) settings.channelName else "Channel disabled",
        )
        player.ifSetText(SETTINGS_JOIN, settings.joinPermission.label)
        player.ifSetText(SETTINGS_TALK, settings.talkPermission.label)
        player.ifSetText(SETTINGS_KICK, settings.kickPermission.label)
        val ops =
            arrayOf(
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
                IfEvent.Op5,
                IfEvent.Op6,
                IfEvent.Op7,
                IfEvent.Op8,
                IfEvent.Op9,
            )
        player.ifSetEvents(SETTINGS_NAME, -1..-1, IfEvent.Op1, IfEvent.Op2)
        player.ifSetEvents(SETTINGS_JOIN, -1..-1, *ops)
        player.ifSetEvents(SETTINGS_TALK, -1..-1, *ops)
        player.ifSetEvents(SETTINGS_KICK, -1..-1, *ops)
    }

    private fun sendFull(player: Player, channel: ActiveChannel) {
        val entries =
            channel.members.values
                .sortedBy { SocialNameNormalizer.normalize(it.name) }
                .map { FriendChatEntry(it.name, it.worldId, it.rank, worldName()) }
        player.client.write(
            UpdateFriendChatChannelFullV2(
                UpdateFriendChatChannelFullV2.JoinUpdate(
                    channelOwner = channel.owner.displayName,
                    channelName = channel.settings.channelName,
                    kickRank = channel.settings.kickPermission.wireId,
                    entries = entries,
                )
            )
        )
    }

    private fun removeMemberLocally(ownerId: Int, targetId: Int, message: String) {
        val channel = channels[ownerId] ?: return
        val removed = channel.members.remove(targetId) ?: return
        membership.remove(targetId, ownerId)
        playerRegistry.findOnlineByCharacterId(targetId)?.let { target ->
            resetClient(target)
            if (message.isNotBlank()) target.writeSocialMessage(message)
        }
        val packet =
            UpdateFriendChatChannelSingleUser(
                UpdateFriendChatChannelSingleUser.RemovedFriendChatUser(
                    removed.name,
                    removed.worldId,
                )
            )
        for (memberId in channel.members.keys) {
            playerRegistry.findOnlineByCharacterId(memberId)?.client?.write(packet)
        }
        if (FriendChatKickRules.clearWhenEmpty(channel.members.size)) {
            channels.remove(ownerId)
            db.request(
                request = { connection ->
                    persistence.clearKicks(connection, ownerId)
                    GameDbResult.Ok(Unit)
                },
                response = {},
            )
        }
    }

    private fun worldName(): String = "${serverConfig.name} ${serverConfig.world}"

    private fun nextMessageCounter(): Int {
        while (true) {
            val current = messageCounter.get()
            val next = ((current + 1) and 0xFFFFFF).coerceAtLeast(1)
            if (messageCounter.compareAndSet(current, next)) return next
        }
    }

    private data class ActiveChannel(
        val owner: SocialIdentity,
        var settings: FriendChatSettings,
        val members: LinkedHashMap<Int, ActiveMember>,
    )

    private data class ActiveMember(
        val characterId: Int,
        val name: String,
        val worldId: Int,
        var rank: Int,
    )

    private data class FriendJoinRequest(
        val snapshot: FriendChatJoinSnapshot?,
        val kickedUntil: Long?,
        val onlineMembers: List<FriendChatOnlineMember>,
    )

    private companion object {
        private const val EVENT_STATE_REFRESH = 1
        private const val EVENT_MEMBER_REMOVED = 2
        private val KICK_BAN_DURATION: Duration = Duration.ofMinutes(60)
        private const val SETTINGS_INTERFACE = "interface.chatchannel_setup"
        private const val SETTINGS_NAME = "component.chatchannel_setup:opt_prefix"
        private const val SETTINGS_JOIN = "component.chatchannel_setup:opt_enter"
        private const val SETTINGS_TALK = "component.chatchannel_setup:opt_talk"
        private const val SETTINGS_KICK = "component.chatchannel_setup:opt_kick"

        private fun messageCounterSeed(): Int {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            return (((now.dayOfYear - 1) * 24 + now.hour) * 50_000) and 0xFFFFFF
        }
    }
}
