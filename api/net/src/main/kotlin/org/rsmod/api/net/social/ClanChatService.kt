package org.rsmod.api.net.social

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannel
import net.rsprot.protocol.game.outgoing.clan.VarClanDisable
import net.rsprot.protocol.game.outgoing.clan.VarClanEnable
import org.rsmod.annotations.InternalApi
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.ironman.isHardcoreIronman
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.social.isChatMuted
import org.rsmod.api.social.writeSocialMessage
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

private var Player.clanGuestQuestPoints by intVarp("varp.qp")

private data class ClanBroadcastDelivery(val clanId: Long, val recipientIds: Set<Int>)

private val CLAN_EVENT_TIME_UTC: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

private val CLAN_GUEST_STATS =
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

/**
 * One authority boundary for modern member and guest clan chat. Persistent membership and settings
 * live in PostgreSQL; local clients are a projection of that state. The event outbox carries
 * idempotent messages between logical game worlds sharing the account database.
 */
@Singleton
public class ClanChatService
@Inject
constructor(
    private val db: GameDbManager,
    private val repository: ClanChatRepository,
    private val domain: ClanDomainRepository,
    private val events: SocialChatEventRepository,
    private val moderation: ClanModerationRepository,
    private val runtime: SocialRuntimeService,
    private val playerList: PlayerList,
    private val playerRegistry: PlayerRegistry,
    private val serverConfig: ServerConfig,
    private val friendChat: FriendChatService,
) {
    private val logger = InlineLogger()
    private val ownClanByPlayer = ConcurrentHashMap<Int, ClanSnapshot>()
    private val guestClanByPlayer = ConcurrentHashMap<Int, ClanSnapshot>()
    private val sessionGeneration = ConcurrentHashMap<Int, Long>()
    private val snapshotLoadsInFlight = ConcurrentHashMap.newKeySet<Int>()
    private val generationCounter = AtomicLong(System.currentTimeMillis())
    private val messageCounter = AtomicInteger(messageCounterSeed())
    private val pollInFlight = AtomicBoolean(false)
    private val eventReminderPollInFlight = AtomicBoolean(false)

    public fun prepareLogin(player: Player) {
        resetClient(player)
        val characterId = player.characterId
        if (characterId <= 0) return
        ownClanByPlayer.remove(characterId)
        guestClanByPlayer.remove(characterId)
        requestSnapshot(player, requestedClanType = null)
    }

    public fun resetClient(player: Player) {
        setOwnClientState(player, loaded = false)
        setGuestClientState(player, loaded = false)
        player.client.write(VarClanDisable)
        player.client.write(ClanChannelFull(OWN_CLAN_TYPE, ClanChannelFull.LeaveUpdate))
        player.client.write(ClanChannelFull(GUEST_CLAN_TYPE, ClanChannelFull.LeaveUpdate))
        player.client.write(ClanSettingsFull(OWN_CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
        player.client.write(ClanSettingsFull(GUEST_CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
    }

    /** Read-only projection used by Clan Hall entry checks; mutations stay inside this service. */
    public fun ownClan(player: Player): ClanSnapshot? = ownClanByPlayer[player.characterId]

    /** Read-only projection used by Clan Hall guest entry checks. */
    public fun guestClan(player: Player): ClanSnapshot? = guestClanByPlayer[player.characterId]

    /**
     * Publishes one idempotent broadcast to ranked members of the actor's own clan. The clan, actor
     * rank, configured minimum rank and recipient preferences are re-read on the database thread
     * before the outbox insert; guest-channel viewers never receive member broadcasts.
     */
    public fun publishBroadcast(
        player: Player,
        key: ClanBroadcastKey,
        eventToken: String,
        message: String,
        rule: (ClanBroadcastSettings) -> Boolean = { true },
    ) {
        val cachedClan = ownClanByPlayer[player.characterId] ?: return
        val cleanToken = eventToken.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanMessage = message.trim().takeIf { it.isNotEmpty() }?.take(160) ?: return
        val actorId = player.characterId
        val actorName = player.displayName.ifBlank { player.username }
        val actorCrown = player.modLevel.clientCode
        val counter = nextMessageCounter()
        val requestId =
            UUID.nameUUIDFromBytes(
                "clan-broadcast:${cachedClan.clanId}:$actorId:${key.storageKey}:$cleanToken"
                    .toByteArray(Charsets.UTF_8)
            )
        db.request(
            request = { connection ->
                val clanId = repository.findOwnClanId(connection, actorId)
                val clan =
                    clanId
                        ?.takeIf { it == cachedClan.clanId }
                        ?.let { repository.loadClan(connection, it) }
                val member = clan?.member(actorId)
                val minimumRank = clan?.broadcastSettings?.minimumRanks?.get(key)
                if (
                    clan == null ||
                        member == null ||
                        minimumRank == null ||
                        !ClanRanks.atLeast(member.rank, minimumRank) ||
                        !rule(clan.broadcastSettings)
                ) {
                    GameDbResult.Ok(null)
                } else {
                    val recipients = repository.broadcastRecipientIds(connection, clan.clanId)
                    val inserted =
                        events.publish(
                            connection = connection,
                            requestId = requestId,
                            sourceWorldId = serverConfig.world,
                            channelKind = SocialChannelKind.OwnClan,
                            channelId = clan.clanId,
                            eventKind = EVENT_BROADCAST,
                            actorCharacterId = actorId,
                            actorName = actorName,
                            actorCrown = actorCrown,
                            counter = counter,
                            payload = cleanMessage,
                        )
                    GameDbResult.Ok(
                        if (inserted) ClanBroadcastDelivery(clan.clanId, recipients) else null
                    )
                }
            },
            response = { result ->
                result.fold(
                    onOk = { delivery ->
                        if (delivery != null) {
                            deliverLocalBroadcast(
                                clanId = delivery.clanId,
                                recipientIds = delivery.recipientIds,
                                senderName = actorName,
                                sourceWorld = serverConfig.world,
                                counter = counter,
                                crown = actorCrown,
                                message = cleanMessage,
                            )
                        }
                    },
                    onErr = {},
                )
            },
        )
    }

    /**
     * Publishes a trusted clan-system message for the actor's current ranked clan. This is the
     * public integration point for non-mandatory server-owned Clan Hall activity. Recipients are
     * always re-read from persisted member notification preferences.
     */
    public fun publishSystemBroadcast(actor: Player, eventToken: String, message: String) {
        val clanId = ownClanByPlayer[actor.characterId]?.clanId ?: return
        publishSystemBroadcastToClan(
            clanId = clanId,
            actorCharacterId = actor.characterId,
            actorName = actor.displayName.ifBlank { actor.username },
            actorCrown = actor.modLevel.clientCode,
            eventToken = eventToken,
            message = message,
        )
    }

    /**
     * Publishes a mandatory system notice to every ranked member. Use only for OSRS-required
     * disclosures such as clan-coffer withdrawals; guests are still excluded.
     */
    public fun publishMandatorySystemNotice(actor: Player, eventToken: String, message: String) {
        val clanId = ownClanByPlayer[actor.characterId]?.clanId ?: return
        publishSystemBroadcastToClan(
            clanId = clanId,
            actorCharacterId = actor.characterId,
            actorName = actor.displayName.ifBlank { actor.username },
            actorCrown = actor.modLevel.clientCode,
            eventToken = eventToken,
            message = message,
            mandatory = true,
        )
    }

    /**
     * Trusted same-module path for membership mutations whose actor may no longer be a member when
     * the transaction commits (leaving or expulsion). Content modules must use the public overload.
     */
    internal fun publishSystemBroadcastToClan(
        clanId: Long,
        actorCharacterId: Int,
        actorName: String,
        actorCrown: Int,
        eventToken: String,
        message: String,
        mandatory: Boolean = false,
    ) {
        if (clanId <= 0L || actorCharacterId <= 0) return
        val cleanToken = eventToken.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanMessage = message.trim().takeIf { it.isNotEmpty() }?.take(160) ?: return
        val cleanActorName = actorName.trim().takeIf { it.isNotEmpty() }?.take(12) ?: return
        val counter = nextMessageCounter()
        val requestId =
            UUID.nameUUIDFromBytes(
                "clan-system-${if (mandatory) "mandatory" else "broadcast"}:$clanId:$actorCharacterId:$cleanToken"
                    .toByteArray(Charsets.UTF_8)
            )
        db.request(
            request = { connection ->
                val clan = repository.loadClan(connection, clanId)
                if (clan == null) {
                    GameDbResult.Ok(null)
                } else {
                    val recipients =
                        if (mandatory) repository.rankedMemberIds(connection, clanId)
                        else repository.broadcastRecipientIds(connection, clanId)
                    val inserted =
                        events.publish(
                            connection = connection,
                            requestId = requestId,
                            sourceWorldId = serverConfig.world,
                            channelKind = SocialChannelKind.OwnClan,
                            channelId = clanId,
                            eventKind = EVENT_BROADCAST,
                            actorCharacterId = actorCharacterId,
                            actorName = cleanActorName,
                            actorCrown = actorCrown,
                            counter = counter,
                            payload = cleanMessage,
                        )
                    GameDbResult.Ok(
                        if (inserted) ClanBroadcastDelivery(clanId, recipients) else null
                    )
                }
            },
            response = { result ->
                result.fold(
                    onOk = { delivery ->
                        if (delivery != null) {
                            deliverLocalBroadcast(
                                clanId = delivery.clanId,
                                recipientIds = delivery.recipientIds,
                                senderName = cleanActorName,
                                sourceWorld = serverConfig.world,
                                counter = counter,
                                crown = actorCrown,
                                message = cleanMessage,
                            )
                        }
                    },
                    onErr = {},
                )
            },
        )
    }

    public fun publishLevelBroadcast(
        player: Player,
        statKey: String,
        statDisplayName: String,
        level: Int,
    ) {
        publishBroadcast(
            player = player,
            key = ClanBroadcastKey.LevelUp,
            eventToken = "$statKey:$level",
            message = "has reached level $level in $statDisplayName.",
            rule = { ClanBroadcastRules.shouldBroadcastLevel(level, it) },
        )
    }

    public fun publishQuestBroadcast(player: Player, questKey: String, questName: String) {
        publishBroadcast(
            player,
            ClanBroadcastKey.QuestCompletion,
            questKey,
            "has completed the quest: $questName.",
        )
    }

    public fun publishCollectionLogBroadcast(
        player: Player,
        itemName: String,
        completedSlots: Int,
        isPet: Boolean,
        stableEventId: String,
    ) {
        publishBroadcast(
            player = player,
            key = ClanBroadcastKey.CollectionLog,
            eventToken = stableEventId,
            message =
                "received a new collection log item: $itemName ($completedSlots slots completed).",
            rule = {
                ClanBroadcastRules.shouldBroadcastCollectionSlot(
                    completedSlots,
                    it.collectionInterval,
                    isRareItem = false,
                    isPet = isPet,
                )
            },
        )
    }

    public fun publishLootBroadcast(
        player: Player,
        itemName: String,
        quantity: Int,
        totalValue: Long,
        stableEventId: String,
    ) {
        if (quantity <= 0 || totalValue < 0L) return
        publishBroadcast(
            player = player,
            key = ClanBroadcastKey.LootOrPet,
            eventToken = stableEventId,
            message = "received $quantity x $itemName worth $totalValue coins.",
            rule = { ClanBroadcastRules.shouldBroadcastLoot(totalValue, it.minimumLootValue) },
        )
    }

    public fun publishPlayerDefeatBroadcast(
        player: Player,
        killerName: String,
        stableEventId: String,
    ) {
        publishBroadcast(
            player,
            ClanBroadcastKey.PlayerDefeat,
            stableEventId,
            "was defeated by $killerName.",
        )
    }

    public fun sendOwnMessage(player: Player, rawMessage: String) {
        val clan = ownClanByPlayer[player.characterId]
        if (clan == null) {
            player.writeSocialMessage("You are not a member of a clan.")
            return
        }
        val member = clan.member(player.characterId)
        if (
            member == null ||
                !ClanPermissions.canMemberSpeak(member.rank, member.muted, clan.talkRank)
        ) {
            player.writeSocialMessage(
                "You do not have a high enough rank to talk in this clan chat."
            )
            return
        }
        sendClanMessage(player, clan, rawMessage, SocialChannelKind.OwnClan)
    }

    public fun sendGuestMessage(player: Player, rawMessage: String) {
        val clan = guestClanByPlayer[player.characterId]
        if (clan == null) {
            player.writeSocialMessage("You are not visiting a clan chat.")
            return
        }
        if (!ClanPermissions.canGuestSpeak(clan.allowGuests, clan.guestsCanTalk)) {
            player.writeSocialMessage("Guests are not allowed to talk in this clan chat.")
            return
        }
        sendClanMessage(player, clan, rawMessage, SocialChannelKind.GuestClan)
    }

    public fun requestJoinGuest(player: Player, requestedName: String) {
        val uid = player.uid
        val characterId = player.characterId
        val candidate = player.toClanGuestCandidate()
        val generation =
            sessionGeneration[characterId]
                ?: generationCounter.incrementAndGet().also { sessionGeneration[characterId] = it }
        val ownClanId = ownClanByPlayer[characterId]?.clanId
        val actorName = player.displayName.ifBlank { player.username }
        val actorCrown = player.modLevel.clientCode
        db.request(
            request = { connection ->
                val join = repository.joinGuest(connection, characterId, requestedName)
                val clan = join.clan
                if (
                    clan != null &&
                        !join.banned &&
                        join.kickedUntilEpochMillis == null &&
                        clan.allowGuests &&
                        clan.guestRequirements.accepts(candidate) &&
                        clan.member(characterId) == null
                ) {
                    repository.touchPresence(
                        connection,
                        characterId,
                        serverConfig.world,
                        null,
                        ownClanId,
                        clan.clanId,
                        generation,
                    )
                    publishStateRefresh(
                        connection,
                        SocialChannelKind.GuestClan,
                        clan.clanId,
                        characterId,
                        actorName,
                        actorCrown,
                    )
                }
                GameDbResult.Ok(join)
            },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { join ->
                        val clan = join.clan
                        when {
                            clan == null -> current.writeSocialMessage("That clan does not exist.")
                            join.banned ->
                                current.writeSocialMessage("You are banned from that clan.")
                            join.kickedUntilEpochMillis != null ->
                                current.writeSocialMessage(
                                    "You have been temporarily kicked from that clan chat."
                                )
                            !clan.allowGuests ->
                                current.writeSocialMessage("That clan is not accepting guests.")
                            !clan.guestRequirements.accepts(candidate) ->
                                current.writeSocialMessage(
                                    "You do not meet that clan's guest requirements."
                                )
                            clan.member(current.characterId) != null ->
                                current.writeSocialMessage(
                                    "You cannot join your own clan as a guest."
                                )
                            else -> {
                                guestClanByPlayer[current.characterId] = clan
                                sendGuest(current, clan)
                                refreshLocalClan(clan)
                            }
                        }
                    },
                    onErr = {
                        current.writeSocialMessage("Unable to join that clan chat right now.")
                    },
                )
            },
        )
    }

    public fun leaveGuest(player: Player) {
        val departedClan = guestClanByPlayer.remove(player.characterId)
        setGuestClientState(player, loaded = false)
        player.client.write(ClanChannelFull(GUEST_CLAN_TYPE, ClanChannelFull.LeaveUpdate))
        player.client.write(ClanSettingsFull(GUEST_CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
        val characterId = player.characterId
        val generation =
            sessionGeneration[characterId]
                ?: generationCounter.incrementAndGet().also { sessionGeneration[characterId] = it }
        val ownClanId = ownClanByPlayer[characterId]?.clanId
        val actorName = player.displayName.ifBlank { player.username }
        val actorCrown = player.modLevel.clientCode
        db.request(
            request = { connection ->
                repository.setGuestAffinity(connection, characterId, null)
                repository.touchPresence(
                    connection,
                    characterId,
                    serverConfig.world,
                    null,
                    ownClanId,
                    null,
                    generation,
                )
                val refreshed =
                    departedClan?.let {
                        publishStateRefresh(
                            connection,
                            SocialChannelKind.GuestClan,
                            it.clanId,
                            characterId,
                            actorName,
                            actorCrown,
                        )
                        repository.loadClan(connection, it.clanId)
                    }
                GameDbResult.Ok(refreshed)
            },
            response = { result -> result.fold(onOk = { it?.let(::refreshLocalClan) }, onErr = {}) },
        )
    }

    public fun requestKickOrExpel(player: Player, clanType: Int, targetName: String) {
        if (clanType !in GUEST_CLAN_TYPE..OWN_CLAN_TYPE) {
            player.writeSocialMessage("That clan channel is no longer available.")
            return
        }
        moderate(player) { connection ->
            moderation.kickOrExpel(
                connection,
                player.characterId,
                targetName,
                java.time.Instant.now().plus(java.time.Duration.ofMinutes(60)),
                expectedGuest = clanType < 0,
            )
        }
    }

    public fun refreshClient(player: Player, clanType: Int) {
        val cached =
            if (clanType < 0) guestClanByPlayer[player.characterId]
            else ownClanByPlayer[player.characterId]
        logger.info {
            "Clan panel refresh character=${player.characterId} type=$clanType " +
                "cached=${cached?.displayName ?: "none"}"
        }
        if (cached != null) {
            if (clanType < 0) sendGuest(player, cached) else sendOwn(player, cached)
            return
        }
        // The client can request a refresh after login, a world hop, or a missed delta. The old
        // implementation only consulted the local map, so one transient login-load failure left
        // the tab on "Connecting..." until another full relog. Reload the authoritative state.
        requestSnapshot(
            player,
            requestedClanType = if (clanType < 0) GUEST_CLAN_TYPE else OWN_CLAN_TYPE,
        )
    }

    public fun requestKickGuest(player: Player, targetName: String) {
        moderate(player) { connection ->
            moderation.kickGuest(
                connection,
                player.characterId,
                targetName,
                java.time.Instant.now().plus(java.time.Duration.ofMinutes(60)),
            )
        }
    }

    public fun requestBanGuest(player: Player, targetName: String) {
        moderate(player) { connection ->
            moderation.banGuest(connection, player.characterId, targetName)
        }
    }

    public fun requestSetMuted(player: Player, targetName: String, muted: Boolean) {
        moderate(player) { connection ->
            moderation.setMuted(connection, player.characterId, targetName, muted)
        }
    }

    private fun moderate(
        player: Player,
        operation: (org.rsmod.api.db.DatabaseConnection) -> ClanModerationResult,
    ) {
        val uid = player.uid
        db.request(
            request = { connection ->
                val result = operation(connection)
                val clan =
                    (result as? ClanModerationResult.Success)?.let {
                        repository.loadClan(connection, it.clanId)
                    }
                if (result is ClanModerationResult.Success) {
                    val removalKind =
                        when (result.action) {
                            ClanModerationAction.KickGuest,
                            ClanModerationAction.BanGuest -> SocialChannelKind.GuestClan
                            ClanModerationAction.ExpelMember -> SocialChannelKind.OwnClan
                            ClanModerationAction.MuteMember,
                            ClanModerationAction.UnmuteMember -> null
                        }
                    if (removalKind != null) {
                        events.publish(
                            connection = connection,
                            requestId = UUID.randomUUID(),
                            sourceWorldId = serverConfig.world,
                            channelKind = removalKind,
                            channelId = result.clanId,
                            eventKind = EVENT_MEMBER_REMOVED,
                            actorCharacterId = result.target.characterId,
                            actorName = result.target.displayName,
                            actorCrown = 0,
                            counter = 0,
                            payload =
                                if (result.action == ClanModerationAction.ExpelMember) {
                                    "You have been expelled from the clan."
                                } else {
                                    "You have been removed from the clan chat."
                                },
                        )
                    }
                    publishStateRefresh(
                        connection,
                        SocialChannelKind.OwnClan,
                        result.clanId,
                        player.characterId,
                        player.displayName.ifBlank { player.username },
                        player.modLevel.clientCode,
                    )
                }
                GameDbResult.Ok(result to clan)
            },
            response = { response ->
                val current = uid.resolve(playerList) ?: return@request
                response.fold(
                    onOk = { (result, clan) ->
                        when (result) {
                            is ClanModerationResult.Denied ->
                                current.writeSocialMessage(result.message)
                            is ClanModerationResult.Success -> {
                                if (result.action == ClanModerationAction.ExpelMember) {
                                    publishSystemBroadcastToClan(
                                        clanId = result.clanId,
                                        actorCharacterId = result.target.characterId,
                                        actorName = result.target.displayName,
                                        actorCrown = 0,
                                        eventToken =
                                            "membership:expel:${result.target.characterId}:${clan?.stateVersion ?: 0L}",
                                        message = "was expelled from the clan.",
                                    )
                                }
                                val target =
                                    playerRegistry.findOnlineByCharacterId(
                                        result.target.characterId
                                    )
                                if (target != null) {
                                    when (result.action) {
                                        ClanModerationAction.KickGuest,
                                        ClanModerationAction.BanGuest -> {
                                            if (
                                                guestClanByPlayer[target.characterId]?.clanId ==
                                                    result.clanId
                                            ) {
                                                guestClanByPlayer.remove(target.characterId)
                                                setGuestClientState(target, loaded = false)
                                                target.client.write(
                                                    ClanChannelFull(
                                                        GUEST_CLAN_TYPE,
                                                        ClanChannelFull.LeaveUpdate,
                                                    )
                                                )
                                                target.client.write(
                                                    ClanSettingsFull(
                                                        GUEST_CLAN_TYPE,
                                                        ClanSettingsFull.LeaveUpdate,
                                                    )
                                                )
                                            }
                                            target.writeSocialMessage(
                                                "You have been removed from the clan chat."
                                            )
                                        }
                                        ClanModerationAction.ExpelMember -> {
                                            ownClanByPlayer.remove(target.characterId)
                                            writeOwnClear(target)
                                            target.writeSocialMessage(
                                                "You have been expelled from the clan."
                                            )
                                        }
                                        ClanModerationAction.MuteMember,
                                        ClanModerationAction.UnmuteMember -> Unit
                                    }
                                }
                                if (clan != null) refreshLocalClan(clan)
                            }
                        }
                    },
                    onErr = {
                        current.writeSocialMessage("Unable to update clan moderation right now.")
                    },
                )
            },
        )
    }

    private fun refreshLocalClan(clan: ClanSnapshot) {
        playerRegistry.forEachOnline { player ->
            if (ownClanByPlayer[player.characterId]?.clanId == clan.clanId) {
                if (clan.member(player.characterId) == null) {
                    ownClanByPlayer.remove(player.characterId)
                    writeOwnClear(player)
                } else {
                    ownClanByPlayer[player.characterId] = clan
                    sendOwn(player, clan)
                }
            } else if (guestClanByPlayer[player.characterId]?.clanId == clan.clanId) {
                if (!clan.allowGuests || clan.bans.any { it.characterId == player.characterId }) {
                    guestClanByPlayer.remove(player.characterId)
                    setGuestClientState(player, loaded = false)
                    player.client.write(
                        ClanChannelFull(GUEST_CLAN_TYPE, ClanChannelFull.LeaveUpdate)
                    )
                    player.client.write(
                        ClanSettingsFull(GUEST_CLAN_TYPE, ClanSettingsFull.LeaveUpdate)
                    )
                } else {
                    guestClanByPlayer[player.characterId] = clan
                    sendGuest(player, clan)
                }
            }
        }
    }

    public fun onLogout(player: Player) {
        val generation = sessionGeneration.remove(player.characterId)
        val ownClanId = ownClanByPlayer.remove(player.characterId)?.clanId
        val guestClanId = guestClanByPlayer.remove(player.characterId)?.clanId
        if (generation != null) {
            db.request(
                request = { connection ->
                    repository.clearPresence(connection, player.characterId, generation)
                    ownClanId?.let {
                        publishStateRefresh(
                            connection,
                            SocialChannelKind.OwnClan,
                            it,
                            player.characterId,
                            player.displayName.ifBlank { player.username },
                            player.modLevel.clientCode,
                        )
                    }
                    guestClanId
                        ?.takeIf { it != ownClanId }
                        ?.let {
                            publishStateRefresh(
                                connection,
                                SocialChannelKind.GuestClan,
                                it,
                                player.characterId,
                                player.displayName.ifBlank { player.username },
                                player.modLevel.clientCode,
                            )
                        }
                    GameDbResult.Ok(Unit)
                },
                response = {},
            )
        }
    }

    public fun pollCrossWorld() {
        if (!pollInFlight.compareAndSet(false, true)) return
        db.request(
            request = { connection ->
                GameDbResult.Ok(events.poll(connection, serverConfig.world))
            },
            response = { result ->
                try {
                    result.fold(onOk = { list -> list.forEach(::deliverRemoteEvent) }, onErr = {})
                } finally {
                    pollInFlight.set(false)
                }
            },
        )
    }

    /** Delivers newly eligible one-hour event reminders to online ranked members. */
    public fun pollEventReminders() {
        if (!eventReminderPollInFlight.compareAndSet(false, true)) return
        val characterIds = buildList {
            playerRegistry.forEachOnline { player ->
                if (player.characterId > 0 && ownClanByPlayer.containsKey(player.characterId)) {
                    add(player.characterId)
                }
            }
        }
        if (characterIds.isEmpty()) {
            eventReminderPollInFlight.set(false)
            return
        }
        db.request(
            request = { connection ->
                GameDbResult.Ok(
                    characterIds.associateWith { characterId ->
                        repository.claimStartingEventReminders(connection, characterId)
                    }
                )
            },
            response = { result ->
                try {
                    result.fold(
                        onOk = { remindersByCharacter ->
                            remindersByCharacter.forEach { (characterId, reminders) ->
                                val player =
                                    playerRegistry.findOnlineByCharacterId(characterId)
                                        ?: return@forEach
                                sendStartingEventReminders(player, reminders)
                            }
                        },
                        onErr = {},
                    )
                } finally {
                    eventReminderPollInFlight.set(false)
                }
            },
        )
    }

    /** Reloads one clan from PostgreSQL and projects the authoritative state to local clients. */
    public fun reloadClan(clanId: Long, actor: Player? = null) {
        requestClanRefresh(clanId)
        if (actor == null || actor.characterId <= 0) return
        db.request(
            request = { connection ->
                publishStateRefresh(
                    connection,
                    SocialChannelKind.OwnClan,
                    clanId,
                    actor.characterId,
                    actor.displayName.ifBlank { actor.username },
                    actor.modLevel.clientCode,
                )
                publishStateRefresh(
                    connection,
                    SocialChannelKind.GuestClan,
                    clanId,
                    actor.characterId,
                    actor.displayName.ifBlank { actor.username },
                    actor.modLevel.clientCode,
                )
                GameDbResult.Ok(Unit)
            },
            response = {},
        )
    }

    private fun sendClanMessage(
        player: Player,
        clan: ClanSnapshot,
        rawMessage: String,
        channelKind: SocialChannelKind,
    ) {
        if (player.isChatMuted()) {
            player.writeSocialMessage("You are muted and cannot send messages.")
            return
        }
        val message = SocialChatText.normalize(rawMessage) ?: return
        if (!runtime.allowMessage(player.characterId)) {
            player.writeSocialMessage("You are sending messages too quickly.")
            return
        }
        val counter = nextMessageCounter()
        deliverLocalMessage(
            clanId = clan.clanId,
            senderCharacterId = player.characterId,
            senderName = player.displayName.ifBlank { player.username },
            sourceWorld = serverConfig.world,
            counter = counter,
            crown = player.modLevel.clientCode,
            message = message,
        )
        db.request(
            request = { connection ->
                events.publish(
                    connection,
                    UUID.randomUUID(),
                    serverConfig.world,
                    channelKind,
                    clan.clanId,
                    EVENT_MESSAGE,
                    player.characterId,
                    player.displayName.ifBlank { player.username },
                    player.modLevel.clientCode,
                    counter,
                    message,
                )
                GameDbResult.Ok(Unit)
            },
            response = {},
        )
    }

    private fun deliverRemoteEvent(event: SocialChatEvent) {
        if (event.eventKind == EVENT_MEMBER_REMOVED) {
            removeRemoteMember(event)
            return
        }
        if (event.channelKind == SocialChannelKind.FriendChat) {
            friendChat.deliverRemoteMessage(event)
            return
        }
        if (event.eventKind == EVENT_STATE_REFRESH) {
            requestClanRefresh(event.channelId)
            return
        }
        if (event.eventKind == EVENT_BROADCAST) {
            requestRemoteBroadcast(event)
            return
        }
        if (event.eventKind != EVENT_MESSAGE) return
        deliverLocalMessage(
            event.channelId,
            event.actorCharacterId,
            event.actorName,
            event.sourceWorldId,
            event.worldMessageCounter,
            event.actorCrown,
            event.payload,
        )
    }

    private fun requestRemoteBroadcast(event: SocialChatEvent) {
        if (event.channelKind != SocialChannelKind.OwnClan) return
        db.request(
            request = { GameDbResult.Ok(repository.broadcastRecipientIds(it, event.channelId)) },
            response = { result ->
                result.fold(
                    onOk = { recipients ->
                        deliverLocalBroadcast(
                            clanId = event.channelId,
                            recipientIds = recipients,
                            senderName = event.actorName,
                            sourceWorld = event.sourceWorldId,
                            counter = event.worldMessageCounter,
                            crown = event.actorCrown,
                            message = event.payload,
                        )
                    },
                    onErr = {},
                )
            },
        )
    }

    private fun removeRemoteMember(event: SocialChatEvent) {
        val target = playerRegistry.findOnlineByCharacterId(event.actorCharacterId) ?: return
        when (event.channelKind) {
            SocialChannelKind.FriendChat -> friendChat.deliverRemoteMessage(event)
            SocialChannelKind.OwnClan -> {
                if (ownClanByPlayer[target.characterId]?.clanId != event.channelId) return
                ownClanByPlayer.remove(target.characterId)
                writeOwnClear(target)
                if (event.payload.isNotBlank()) target.writeSocialMessage(event.payload)
            }
            SocialChannelKind.GuestClan -> {
                if (guestClanByPlayer[target.characterId]?.clanId != event.channelId) return
                guestClanByPlayer.remove(target.characterId)
                setGuestClientState(target, loaded = false)
                target.client.write(ClanChannelFull(GUEST_CLAN_TYPE, ClanChannelFull.LeaveUpdate))
                target.client.write(ClanSettingsFull(GUEST_CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
                if (event.payload.isNotBlank()) target.writeSocialMessage(event.payload)
            }
        }
    }

    private fun requestClanRefresh(clanId: Long) {
        db.request(
            request = { connection -> GameDbResult.Ok(repository.loadClan(connection, clanId)) },
            response = { result ->
                result.fold(
                    onOk = { clan ->
                        if (clan != null) refreshLocalClan(clan) else clearMissingClan(clanId)
                    },
                    onErr = {},
                )
            },
        )
    }

    private fun clearMissingClan(clanId: Long) {
        playerRegistry.forEachOnline { player ->
            if (ownClanByPlayer[player.characterId]?.clanId == clanId) {
                ownClanByPlayer.remove(player.characterId)
                writeOwnClear(player)
            }
            if (guestClanByPlayer[player.characterId]?.clanId == clanId) {
                guestClanByPlayer.remove(player.characterId)
                setGuestClientState(player, loaded = false)
                player.client.write(ClanChannelFull(GUEST_CLAN_TYPE, ClanChannelFull.LeaveUpdate))
                player.client.write(ClanSettingsFull(GUEST_CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
            }
        }
    }

    private fun deliverLocalMessage(
        clanId: Long,
        senderCharacterId: Int,
        senderName: String,
        sourceWorld: Int,
        counter: Int,
        crown: Int,
        message: String,
    ) {
        playerRegistry.forEachOnline { recipient ->
            val own = ownClanByPlayer[recipient.characterId]?.clanId == clanId
            val guest = guestClanByPlayer[recipient.characterId]?.clanId == clanId
            if (!own && !guest) return@forEachOnline
            if (
                recipient.characterId != senderCharacterId &&
                    runtime.isIgnoring(recipient.characterId, senderCharacterId)
            )
                return@forEachOnline
            recipient.client.write(
                MessageClanChannel(
                    clanType = if (own) OWN_CLAN_TYPE else GUEST_CLAN_TYPE,
                    name = senderName,
                    worldId = sourceWorld,
                    worldMessageCounter = counter,
                    chatCrownType = crown,
                    message = message,
                )
            )
        }
    }

    private fun deliverLocalBroadcast(
        clanId: Long,
        recipientIds: Set<Int>,
        senderName: String,
        sourceWorld: Int,
        counter: Int,
        crown: Int,
        message: String,
    ) {
        playerRegistry.forEachOnline { recipient ->
            if (recipient.characterId !in recipientIds) return@forEachOnline
            if (ownClanByPlayer[recipient.characterId]?.clanId != clanId) return@forEachOnline
            recipient.client.write(
                MessageClanChannel(
                    clanType = OWN_CLAN_TYPE,
                    name = senderName,
                    worldId = sourceWorld,
                    worldMessageCounter = counter,
                    chatCrownType = crown,
                    message = message,
                )
            )
        }
    }

    private fun sendStartingEventReminders(player: Player, reminders: List<ClanEventRecord>) {
        if (reminders.isEmpty()) return
        player.writeSocialMessage(
            "You have ${reminders.size} clan event reminder(s) starting within the next hour."
        )
        for (event in reminders) {
            val activity =
                ClanEventActivities.find(event.activityKey)?.displayName
                    ?: event.activityKey.replace('_', ' ')
            val startsAt =
                CLAN_EVENT_TIME_UTC.format(Instant.ofEpochMilli(event.startsAtEpochMillis))
            player.writeSocialMessage(
                "Clan event: $activity starts at $startsAt UTC on world ${event.worldId}."
            )
        }
    }

    private fun requestSnapshot(player: Player, requestedClanType: Int?) {
        val characterId = player.characterId
        if (characterId <= 0 || !snapshotLoadsInFlight.add(characterId)) return
        val uid = player.uid
        db.request(
            request = { connection ->
                val snapshot = repository.loadLoginSnapshot(connection, characterId)
                if (requestedClanType == null) {
                    repository.markMemberLogin(connection, characterId)
                }
                val pendingRecruiterNotices =
                    if (requestedClanType == null) {
                        when (
                            val pending =
                                domain.listPendingRecruiterNotifications(connection, characterId)
                        ) {
                            is ClanManagementResult.Denied -> 0
                            is ClanManagementResult.Success -> pending.value.size
                        }
                    } else {
                        0
                    }
                val startingEventReminders =
                    if (requestedClanType == null) {
                        repository.claimStartingEventReminders(connection, characterId)
                    } else {
                        emptyList()
                    }
                GameDbResult.Ok(Triple(snapshot, pendingRecruiterNotices, startingEventReminders))
            },
            response = { result ->
                snapshotLoadsInFlight.remove(characterId)
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { (snapshot, pendingRecruiterNotices, startingEventReminders) ->
                        logger.info {
                            "Clan snapshot loaded character=$characterId " +
                                "own=${snapshot.ownClan?.displayName ?: "none"} " +
                                "guest=${snapshot.guestClan?.displayName ?: "none"}"
                        }
                        installSnapshot(current, snapshot)
                        if (requestedClanType == null && pendingRecruiterNotices > 0) {
                            current.writeSocialMessage(
                                "You have $pendingRecruiterNotices pending recruiter application notification(s). Open Clan Settings -> Applicants to review them."
                            )
                        }
                        sendStartingEventReminders(current, startingEventReminders)
                        when {
                            requestedClanType == OWN_CLAN_TYPE && snapshot.ownClan == null ->
                                current.writeSocialMessage("You are not a member of a clan.")
                            requestedClanType == GUEST_CLAN_TYPE && snapshot.guestClan == null ->
                                current.writeSocialMessage(
                                    "You are not currently a guest of a clan."
                                )
                        }
                    },
                    onErr = { error ->
                        writeOwnClear(current)
                        writeGuestClear(current)
                        logger.warn {
                            "Clan snapshot load failed for character=$characterId: $error"
                        }
                        current.writeSocialMessage("Clan Chat did not load. Please try again.")
                    },
                )
            },
        )
    }

    private fun installSnapshot(player: Player, snapshot: ClanLoginSnapshot) {
        val characterId = player.characterId
        ownClanByPlayer.remove(characterId)
        guestClanByPlayer.remove(characterId)

        val own = snapshot.ownClan
        if (own == null) {
            writeOwnClear(player)
        } else {
            ownClanByPlayer[characterId] = own
            sendOwn(player, own)
        }

        val guest =
            snapshot.guestClan?.takeIf {
                it.allowGuests && it.bans.none { ban -> ban.characterId == characterId }
            }
        if (guest == null) {
            writeGuestClear(player)
        } else {
            guestClanByPlayer[characterId] = guest
            sendGuest(player, guest)
        }

        val generation = generationCounter.incrementAndGet()
        sessionGeneration[characterId] = generation
        publishPresence(player, generation)
    }

    private fun writeOwnClear(player: Player) {
        setOwnClientState(player, loaded = false)
        player.client.write(VarClanDisable)
        player.client.write(ClanSettingsFull(OWN_CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
        player.client.write(ClanChannelFull(OWN_CLAN_TYPE, ClanChannelFull.LeaveUpdate))
    }

    private fun writeGuestClear(player: Player) {
        setGuestClientState(player, loaded = false)
        player.client.write(ClanSettingsFull(GUEST_CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
        player.client.write(ClanChannelFull(GUEST_CLAN_TYPE, ClanChannelFull.LeaveUpdate))
    }

    private fun sendOwn(player: Player, clan: ClanSnapshot) {
        player.client.write(VarClanEnable)
        player.client.write(ClanClientVars.encodeCofferBalance(clan.cofferBalance))
        setOwnClientState(player, loaded = true, homeWorld = clan.homeWorld)
        sendSettings(player, OWN_CLAN_TYPE, clan)
        sendChannel(player, OWN_CLAN_TYPE, clan, includeGuests = false)
    }

    private fun sendGuest(player: Player, clan: ClanSnapshot) {
        setGuestClientState(player, loaded = true, homeWorld = clan.homeWorld)
        sendSettings(player, GUEST_CLAN_TYPE, clan)
        sendChannel(player, GUEST_CLAN_TYPE, clan, includeGuests = true)
    }

    private fun sendSettings(player: Player, clanType: Int, clan: ClanSnapshot) {
        logger.info {
            "Sending clan settings character=${player.characterId} type=$clanType " +
                "clan=${clan.displayName} members=${clan.members.size}"
        }
        player.client.write(
            ClanSettingsFull(
                clanType,
                ClanSettingsFull.JoinUpdate(
                    updateNum = clan.stateVersion.toInt(),
                    creationTime = clan.creationRuneDay * 1_440,
                    clanName = clan.displayName,
                    allowUnaffined = clan.allowGuests,
                    talkRank = clan.talkRank,
                    kickRank = clan.kickRank,
                    lootshareRank = ClanRanks.OWNER,
                    coinshareRank = ClanRanks.OWNER,
                    affinedMembers =
                        clan.members.map {
                            ClanSettingsFull.AffinedClanMember(
                                it.displayName,
                                it.rank,
                                it.titleSlot,
                                it.joinedRuneDay,
                                it.muted,
                            )
                        },
                    bannedMembers =
                        clan.bans.map { ClanSettingsFull.BannedClanMember(it.displayName) },
                    settings = ClanClientSettings.encode(clan),
                ),
            )
        )
    }

    private fun sendChannel(
        player: Player,
        clanType: Int,
        clan: ClanSnapshot,
        includeGuests: Boolean,
    ) {
        val onlineMembers =
            clan.members
                .filter { it.worldId > 0 }
                .map { ClanChannelFull.ClanMember(it.displayName, it.rank, it.worldId) }
                .toMutableList()
        if (includeGuests) {
            val includedGuestIds = HashSet<Int>()
            for (guest in clan.onlineGuests) {
                if (clan.members.any { it.characterId == guest.characterId }) continue
                includedGuestIds += guest.characterId
                onlineMembers +=
                    ClanChannelFull.ClanMember(guest.displayName, ClanRanks.GUEST, guest.worldId)
            }
            guestClanByPlayer.forEach { (characterId, joined) ->
                if (
                    joined.clanId != clan.clanId ||
                        clan.members.any { it.characterId == characterId } ||
                        !includedGuestIds.add(characterId)
                )
                    return@forEach
                val guest = playerRegistry.findOnlineByCharacterId(characterId) ?: return@forEach
                onlineMembers +=
                    ClanChannelFull.ClanMember(
                        guest.displayName.ifBlank { guest.username },
                        ClanRanks.GUEST,
                        serverConfig.world,
                    )
            }
        }
        logger.info {
            "Sending clan channel character=${player.characterId} type=$clanType " +
                "clan=${clan.displayName} online=${onlineMembers.size}"
        }
        player.client.write(
            ClanChannelFull(
                clanType,
                ClanChannelFull.JoinUpdate(
                    clanHash = clan.clanId,
                    updateNum = clan.stateVersion,
                    clanName = clan.displayName,
                    discardedBoolean = false,
                    kickRank = clan.kickRank,
                    talkRank =
                        if (clanType < 0 && clan.guestsCanTalk) ClanRanks.GUEST else clan.talkRank,
                    members = onlineMembers.sortedBy { SocialNameNormalizer.normalize(it.name) },
                ),
            )
        )
    }

    @OptIn(InternalApi::class)
    private fun Player.toClanGuestCandidate(): ClanGuestCandidate =
        ClanGuestCandidate(
            combatLevel = combatLevel,
            totalLevel = CLAN_GUEST_STATS.sumOf { statMap.getBaseLevel(it).toInt() },
            questPoints = clanGuestQuestPoints.coerceAtLeast(0),
            isIron = isAnyIronman,
            isUltimateIron = isUltimateIronman,
            isHardcoreIron = isHardcoreIronman,
        )

    private fun publishPresence(player: Player, generation: Long) {
        val own = ownClanByPlayer[player.characterId]?.clanId
        val guest = guestClanByPlayer[player.characterId]?.clanId
        db.request(
            request = { connection ->
                repository.touchPresence(
                    connection,
                    player.characterId,
                    serverConfig.world,
                    null,
                    own,
                    guest,
                    generation,
                )
                GameDbResult.Ok(Unit)
            },
            response = {},
        )
    }

    private fun publishStateRefresh(
        connection: org.rsmod.api.db.DatabaseConnection,
        channelKind: SocialChannelKind,
        clanId: Long,
        actorCharacterId: Int,
        actorName: String,
        actorCrown: Int,
    ) {
        events.publish(
            connection,
            UUID.randomUUID(),
            serverConfig.world,
            channelKind,
            clanId,
            EVENT_STATE_REFRESH,
            actorCharacterId,
            actorName,
            actorCrown,
            0,
            "",
        )
    }

    private fun nextMessageCounter(): Int {
        while (true) {
            val current = messageCounter.get()
            val next = ((current + 1) and 0xFFFFFF).coerceAtLeast(1)
            if (messageCounter.compareAndSet(current, next)) return next
        }
    }

    private companion object {
        private const val OWN_CLAN_TYPE = 0
        private const val GUEST_CLAN_TYPE = -1
        private const val EVENT_MESSAGE = 0
        private const val EVENT_STATE_REFRESH = 1
        private const val EVENT_MEMBER_REMOVED = 2
        private const val EVENT_BROADCAST = 3

        private fun messageCounterSeed(): Int {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            return (((now.dayOfYear - 1) * 24 + now.hour) * 50_000) and 0xFFFFFF
        }
    }
}
