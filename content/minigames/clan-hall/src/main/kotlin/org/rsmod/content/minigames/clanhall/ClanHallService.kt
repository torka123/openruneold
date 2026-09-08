package org.rsmod.content.minigames.clanhall

import dev.openrune.ServerCacheManager
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.net.social.ClanDomainService
import org.rsmod.api.net.social.ClanManagementResult
import org.rsmod.api.net.social.ClanManagementService
import org.rsmod.api.net.social.ClanPartyChestEntry
import org.rsmod.api.net.social.ClanPermissionKey
import org.rsmod.api.net.social.ClanSnapshot
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
@OptIn(UncheckedType::class)
internal class ClanHallService
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val chat: ClanChatService,
    private val domain: ClanDomainService,
    private val management: ClanManagementService,
    private val serverConfig: ServerConfig,
    private val collision: CollisionFlagMap,
    private val locs: LocRepository,
    private val objects: ObjRepository,
) {
    private val sessionsByClan = mutableMapOf<Long, InstanceId>()
    private val clanBySession = mutableMapOf<InstanceId, Long>()
    private val occupantsBySession = mutableMapOf<InstanceId, MutableMap<Long, Occupant>>()
    private val balloonDropsBySession = mutableMapOf<InstanceId, BalloonDrop>()
    private val balloonPrizesBySession = mutableMapOf<InstanceId, MutableSet<Obj>>()

    suspend fun enterPreferred(player: Player): Boolean =
        when {
            chat.ownClan(player) != null -> enter(player, ClanHallRole.Member)
            chat.guestClan(player) != null -> enter(player, ClanHallRole.Guest)
            else -> deny(player, "Join a clan, or visit a guest clan, before entering a Clan Hall.")
        }

    suspend fun enter(player: Player, requestedRole: ClanHallRole): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            return deny(player, "Finish what you are doing before entering a Clan Hall.")
        }
        val existing = instances.sessionForPlayer(player)
        if (existing != null) {
            return if (existing.key == CLAN_HALL_INSTANCE_KEY) {
                deny(player, "You are already inside a Clan Hall.")
            } else {
                deny(player, "Leave your current instance before entering a Clan Hall.")
            }
        }

        val clan =
            when (requestedRole) {
                ClanHallRole.Member -> chat.ownClan(player)
                ClanHallRole.Guest -> chat.guestClan(player)
            }
                ?: return deny(
                    player,
                    if (requestedRole == ClanHallRole.Member) {
                        "You are not a ranked member of a clan."
                    } else {
                        "You are not currently visiting a guest clan."
                    },
                )
        val member = clan.member(player.characterId)
        val rank = member?.rank ?: -1
        if (requestedRole == ClanHallRole.Member && member == null) {
            return deny(player, "Your clan membership is still loading; try again shortly.")
        }

        val profile =
            when (val result = management.profile(clan.displayName)) {
                is ClanManagementResult.Denied ->
                    return deny(
                        player,
                        "Clan recruitment settings are unavailable; try again shortly.",
                    )
                is ClanManagementResult.Success ->
                    result.value ?: return deny(player, "That clan is no longer available.")
            }
        val live = sessionForClan(clan.clanId)
        val occupantCount = live?.occupants?.size ?: 0
        val rankedOccupants =
            live
                ?.let { occupantsBySession[it.id] }
                ?.values
                ?.count { it.role == ClanHallRole.Member } ?: 0
        val denial =
            ClanHallRules.entryDenial(
                ClanHallEntryContext(
                    actorCharacterId = player.characterId,
                    nowEpochMillis = System.currentTimeMillis(),
                    role = requestedRole,
                    rank = rank,
                    currentWorld = serverConfig.world,
                    homeWorld = clan.homeWorld,
                    occupants = occupantCount,
                    rankedOccupants = rankedOccupants,
                    recruitmentOpen = profile.recruitmentOpen,
                    applicationsOpen = profile.applicationsOpen,
                    settings = clan.hallSettings,
                    permissions = clan.permissionSet,
                )
            )
        if (denial != null) return deny(player, denial)

        val session =
            live
                ?: createSession(clan)
                ?: return deny(player, "No Clan Hall is available right now.")
        val joined = instances.join(player, session, clock.cycle, forceAccess = true)
        if (joined !is InstanceManager.Result.Joined) {
            return deny(
                player,
                (joined as? InstanceManager.Result.Failed)?.reason
                    ?: "You could not enter that Clan Hall.",
            )
        }

        val occupant = Occupant(clan.clanId, player.characterId, requestedRole, rank)
        occupantsBySession.getOrPut(session.id, ::mutableMapOf)[player.characterId.toLong()] =
            occupant
        PathingEntityCommon.telejump(player, collision, joined.enter)
        instances.finalizeEntry(player, session, clock.cycle)
        player.mes("You enter ${clan.displayName}'s Clan Hall.")
        return true
    }

    fun leave(player: Player): Boolean {
        val session = instances.sessionForPlayer(player) ?: return false
        if (session.key != CLAN_HALL_INSTANCE_KEY) return false
        val exit = instances.leave(player, session, clock.cycle)
        if (player.isSlotAssigned) PathingEntityCommon.telejump(player, collision, exit)
        player.mes("You leave the Clan Hall.")
        return true
    }

    suspend fun membershipEnded(player: Player, instanceId: InstanceId) {
        val session = instances.sessionForId(instanceId)
        val roles = occupantsBySession[instanceId] ?: return
        val removed = roles.remove(player.characterId.toLong()) ?: return
        if (removed.role == ClanHallRole.Member) {
            releaseEditLeaseIfOwned(player)
        }
        if (
            removed.role == ClanHallRole.Member &&
                roles.values.none { it.role == ClanHallRole.Member }
        ) {
            clearBalloonDrop(instanceId)
            archiveAbandonedPartyChest(removed.clanId, instanceId)
        }
        if (session == null || session.occupants.isEmpty()) {
            clearBalloonDrop(instanceId)
            session?.let(instances::terminate)
            removeSession(instanceId)
        }
    }

    fun instanceEnded(instanceId: InstanceId) {
        clearBalloonDrop(instanceId)
        removeSession(instanceId)
    }

    suspend fun useCoffer(access: ProtectedAccess) {
        val occupant = memberOccupant(access.player) ?: return
        if (access.player.isAnyIronman) {
            access.mes("Iron players cannot deposit into or withdraw from a clan coffer.")
            return
        }
        val snapshot = resultOrMessage(access, domain.coffer(access.player)) ?: return
        access.mes("The clan coffer currently holds ${snapshot.balance} coins.")
        when (access.stringDialog("Type DEPOSIT, WITHDRAW, or CANCEL:").trim().uppercase()) {
            "DEPOSIT" -> depositCoffer(access)
            "WITHDRAW" -> withdrawCoffer(access)
            "CANCEL",
            "" -> Unit
            else -> access.mes("That is not a valid coffer action.")
        }
        check(occupant.role == ClanHallRole.Member)
    }

    suspend fun openPartyChest(access: ProtectedAccess) {
        val occupant = occupant(access.player) ?: return
        val entries = resultOrMessage(access, domain.partyChest(access.player)) ?: return
        showPartyChest(access, entries)
        if (access.player.isAnyIronman) {
            access.mes("Iron players cannot place items in the clan party chest.")
            return
        }
        val denial =
            permissionDenial(access.player, occupant, ClanPermissionKey.HallPartyChestDeposit)
        if (denial != null) {
            access.mes(denial)
            return
        }
        if (
            !access.stringDialog("Type DEPOSIT to add an item, or CANCEL:").equals("DEPOSIT", true)
        ) {
            return
        }
        val slot = access.numberDialog("Inventory slot to deposit (1-28):") - 1
        val item = if (slot in access.player.inv.indices) access.player.inv[slot] else null
        if (item == null) {
            access.mes("There is no item in that inventory slot.")
            return
        }
        if (!getInvObj(item).tradeable) {
            access.mes("Only tradeable items can be placed in the clan party chest.")
            return
        }
        val amount = access.numberDialog("How many would you like to deposit?")
        if (amount <= 0 || amount > item.count && getInvObj(item).stackable) {
            access.mes("That is not a valid amount.")
            return
        }
        if (access.player.invDel(access.player.inv, item.id, amount).failure) {
            access.mes("You do not have that many of this item.")
            return
        }
        when (val result = domain.addPartyChestItem(access.player, item.id, amount.toLong())) {
            is ClanManagementResult.Success -> {
                access.mes("The items are now committed to the clan party chest.")
                showPartyChest(access, result.value)
            }
            is ClanManagementResult.Denied -> {
                check(
                    access.player.invAdd(access.player.inv, item.id, amount, strict = false).success
                ) {
                    "Clan party chest deposit rollback failed for ${access.player.characterId}."
                }
                access.mes(result.message)
            }
        }
    }

    suspend fun checkPartyChestPermissions(access: ProtectedAccess) {
        val occupant = occupant(access.player) ?: return
        val clan = clanSnapshot(access.player, occupant.role) ?: return
        val deposit = permissionLabel(clan, ClanPermissionKey.HallPartyChestDeposit)
        val pull = permissionLabel(clan, ClanPermissionKey.HallPullLever)
        val loot = permissionLabel(clan, ClanPermissionKey.HallPartyBalloonLoot)
        access.mes(
            "Party chest permissions: deposit $deposit; pull lever $pull; loot balloons $loot."
        )
    }

    suspend fun pullPartyChestLever(access: ProtectedAccess) {
        val occupant = occupant(access.player) ?: return
        val denial = permissionDenial(access.player, occupant, ClanPermissionKey.HallPullLever)
        if (denial != null) {
            access.mes(denial)
            return
        }
        val session = instances.sessionForPlayer(access.player) ?: return
        if (balloonDropsBySession.containsKey(session.id)) {
            access.mes("A clan party drop is already active.")
            return
        }
        val entries = resultOrMessage(access, domain.partyChest(access.player)) ?: return
        if (entries.isEmpty()) {
            access.mes("There are no items in the party chest.")
            return
        }
        val coffer = resultOrMessage(access, domain.coffer(access.player)) ?: return
        if (coffer.balance < ClanHallBalloonRules.LEVER_COST) {
            access.mes("The clan coffer does not contain enough coins.")
            return
        }

        /*
         * Spawn an uncommitted visual plan first. Balloon interactions remain disabled until the
         * authoritative database operation succeeds. This means a failed layout or failed DB
         * request cannot consume either the 1,000 coins or any chest item.
         */
        val pending = prepareBalloonDrop(session, entries)
        if (pending == null) {
            access.mes("There is not enough safe space for a clan party drop.")
            return
        }
        val requestId = UUID.randomUUID()
        when (val result = domain.pullPartyChestLever(access.player, requestId)) {
            is ClanManagementResult.Denied -> {
                clearBalloonDrop(session.id)
                access.mes(result.message)
            }
            is ClanManagementResult.Success -> {
                if (!commitBalloonDrop(session.id, result.value.entries)) {
                    clearBalloonDrop(session.id)
                    error(
                        "Validated Clan Hall balloon plan could not be committed after DB release."
                    )
                }
                access.mes(
                    "You pull the lever. 1,000 coins are taken from the coffer and the balloons are released."
                )
                chat.publishSystemBroadcast(
                    actor = access.player,
                    eventToken = "party-drop:$requestId",
                    message = "released a clan party drop.",
                )
            }
        }
    }

    suspend fun burstPartyBalloon(access: ProtectedAccess, loc: BoundLocInfo) {
        val occupant = occupant(access.player) ?: return
        val denial =
            permissionDenial(access.player, occupant, ClanPermissionKey.HallPartyBalloonLoot)
        if (denial != null) {
            access.mes(denial)
            return
        }
        if (access.player.isAnyIronman) {
            access.mes("Iron players cannot take items from clan party balloons.")
            return
        }
        val session = instances.sessionForPlayer(access.player) ?: return
        val drop = balloonDropsBySession[session.id]
        val active = drop?.active?.get(loc.coords)
        if (drop == null || !drop.committed || active == null || active.loc.id != loc.id) {
            access.mes("That balloon is not part of an active clan party drop.")
            return
        }
        burstBalloon(session.id, access.player, active)
    }

    private fun prepareBalloonDrop(
        session: InstanceSession,
        entries: List<ClanPartyChestEntry>,
    ): BalloonDrop? {
        val preview = balloonQueue(entries)
        if (preview.isEmpty) return null
        val safeTiles = safeBalloonTiles(session)
        val activeCount =
            minOf(
                    preview.remainingBalloonCount,
                    safeTiles.size.toLong(),
                    ClanHallBalloonRules.MAX_ACTIVE_BALLOONS.toLong(),
                )
                .toInt()
        if (activeCount <= 0) return null

        val drop = BalloonDrop(queue = emptyBalloonQueue())
        balloonDropsBySession[session.id] = drop
        for (tile in safeTiles.take(activeCount)) {
            val loc = balloonLoc(tile, ClanHallBalloonRules.activeId(drop.visualSequence++))
            if (!locs.add(loc, Int.MAX_VALUE)) {
                clearBalloonDrop(session.id)
                return null
            }
            drop.active[tile] = ActiveBalloon(loc, prize = null)
        }
        return drop
    }

    private fun commitBalloonDrop(
        instanceId: InstanceId,
        entries: List<ClanPartyChestEntry>,
    ): Boolean {
        val drop = balloonDropsBySession[instanceId] ?: return false
        val queue = balloonQueue(entries)
        if (queue.isEmpty) return false
        drop.queue = queue
        val iterator = drop.active.entries.iterator()
        while (iterator.hasNext()) {
            val active = iterator.next().value
            val prize = queue.takeNext()
            if (prize == null) {
                locs.del(active.loc, Int.MAX_VALUE)
                iterator.remove()
            } else {
                active.prize = prize
            }
        }
        drop.committed = drop.active.isNotEmpty()
        return drop.committed
    }

    private fun burstBalloon(instanceId: InstanceId, player: Player, active: ActiveBalloon) {
        val drop = balloonDropsBySession[instanceId] ?: return
        if (drop.active.remove(active.loc.coords) !== active) return
        val prize = active.prize ?: return
        locs.del(active.loc, Int.MAX_VALUE)

        val popped = balloonLoc(active.loc.coords, ClanHallBalloonRules.poppedId(active.loc.id))
        val poppedAdded =
            locs.add(popped, ClanHallBalloonRules.POPPED_DURATION_TICKS) {
                val current = balloonDropsBySession[instanceId] ?: return@add
                current.popped.remove(active.loc.coords)
                spawnNextBalloon(instanceId, active.loc.coords)
                finishBalloonDropIfComplete(instanceId)
            }
        if (poppedAdded) {
            drop.popped[active.loc.coords] = popped
        } else {
            spawnNextBalloon(instanceId, active.loc.coords)
        }

        val groundItem =
            objects.add(
                invObj = InvObj(prize.itemId, prize.count),
                coords = active.loc.coords,
                duration = ClanHallBalloonRules.PRIZE_GROUND_DURATION_TICKS,
                receiver = player,
                reveal = ClanHallBalloonRules.PRIZE_GROUND_DURATION_TICKS,
            )
        balloonPrizesBySession.getOrPut(instanceId, ::mutableSetOf) += groundItem
        player.mes("You burst the balloon and find an item inside.")
        finishBalloonDropIfComplete(instanceId)
    }

    private fun spawnNextBalloon(instanceId: InstanceId, tile: CoordGrid) {
        val drop = balloonDropsBySession[instanceId] ?: return
        if (drop.queue.isEmpty) return
        val loc = balloonLoc(tile, ClanHallBalloonRules.activeId(drop.visualSequence++))
        if (!locs.add(loc, Int.MAX_VALUE)) return
        val prize = checkNotNull(drop.queue.takeNext())
        drop.active[tile] = ActiveBalloon(loc, prize)
    }

    private fun finishBalloonDropIfComplete(instanceId: InstanceId) {
        val drop = balloonDropsBySession[instanceId] ?: return
        if (drop.queue.isEmpty && drop.active.isEmpty() && drop.popped.isEmpty()) {
            balloonDropsBySession.remove(instanceId)
        }
    }

    private fun clearBalloonDrop(instanceId: InstanceId) {
        balloonDropsBySession.remove(instanceId)?.let { drop ->
            drop.active.values.forEach { locs.del(it.loc, Int.MAX_VALUE) }
            drop.popped.values.forEach { locs.del(it, Int.MAX_VALUE) }
        }
        balloonPrizesBySession.remove(instanceId)?.forEach { objects.del(it, Int.MAX_VALUE) }
    }

    private fun safeBalloonTiles(session: InstanceSession): List<CoordGrid> {
        val chest = instances.localCoord(session, ClanHallAssets.PARTY_CHEST_LOCAL)
        val lever = instances.localCoord(session, ClanHallAssets.PARTY_LEVER_LOCAL)
        val entry = instances.localCoord(session, ClanHallAssets.HALL_ENTRY)
        val excluded = setOfNotNull(chest, lever, entry)
        val centreX = ClanHallAssets.PARTY_CHEST_LOCAL.localX
        val centreZ = ClanHallAssets.PARTY_CHEST_LOCAL.localZ
        val locals =
            buildList {
                    for (localX in 27..44) {
                        for (localZ in 14..31) {
                            add(
                                RegionLocal(
                                    level = 0,
                                    regionZoneX = ClanHallAssets.SOURCE_MAP_X,
                                    regionZoneZ = ClanHallAssets.SOURCE_MAP_Z,
                                    localX = localX,
                                    localZ = localZ,
                                )
                            )
                        }
                    }
                }
                .sortedWith(
                    compareBy<RegionLocal> {
                            kotlin.math.abs(it.localX - centreX) +
                                kotlin.math.abs(it.localZ - centreZ)
                        }
                        .thenBy(RegionLocal::localZ)
                        .thenBy(RegionLocal::localX)
                )
        val layer = LocLayerConstants.of(LocShape.CentrepieceStraight.id)
        val candidates = locals.mapNotNull { instances.localCoord(session, it) }
        return ClanHallBalloonRules.safeTiles(candidates) { tile ->
            tile !in excluded &&
                collision.isZoneValid(tile) &&
                !collision.isWalkBlocked(tile) &&
                locs.findAll(tile).none { it.layer == layer }
        }
    }

    private fun balloonQueue(entries: List<ClanPartyChestEntry>): ClanHallBalloonPrizeQueue =
        ClanHallBalloonPrizeQueue.from(entries) { itemId -> getInvObj(InvObj(itemId, 1)).stackable }

    private fun emptyBalloonQueue(): ClanHallBalloonPrizeQueue =
        ClanHallBalloonPrizeQueue.from(emptyList()) { false }

    private fun balloonLoc(coords: CoordGrid, id: Int): LocInfo {
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing revision-240 Clan Hall balloon location definition: $id"
        }
        val shape = LocShape.CentrepieceStraight
        val angle = LocAngle.North
        return LocInfo(LocLayerConstants.of(shape.id), coords, LocEntity(id, shape.id, angle.id))
    }

    private suspend fun depositCoffer(access: ProtectedAccess) {
        val amount = access.numberDialog("How many coins would you like to deposit?")
        if (amount <= 0) return
        if (access.player.invDel(access.player.inv, "obj.coins", amount).failure) {
            access.mes("You do not have that many coins.")
            return
        }
        when (val result = domain.depositCoffer(access.player, amount.toLong())) {
            is ClanManagementResult.Success ->
                access.mes("You deposit $amount coins. New balance: ${result.value.balance}.")
            is ClanManagementResult.Denied -> {
                check(access.player.invAdd(access.player.inv, "obj.coins", amount).success) {
                    "Clan coffer deposit rollback failed for ${access.player.characterId}."
                }
                access.mes(result.message)
            }
        }
    }

    private suspend fun withdrawCoffer(access: ProtectedAccess) {
        val amount = access.numberDialog("How many coins would you like to withdraw?")
        if (amount <= 0) return
        val requestId = UUID.randomUUID()
        when (val result = domain.withdrawCoffer(access.player, amount.toLong(), requestId)) {
            is ClanManagementResult.Denied -> access.mes(result.message)
            is ClanManagementResult.Success -> {
                if (access.player.invAdd(access.player.inv, "obj.coins", amount).success) {
                    access.mes("You withdraw $amount coins. New balance: ${result.value.balance}.")
                    chat.publishMandatorySystemNotice(
                        actor = access.player,
                        eventToken = "coffer-withdraw:$requestId",
                        message = "withdrew $amount coins from the clan coffer.",
                    )
                } else {
                    val rollback = domain.depositCoffer(access.player, amount.toLong())
                    check(rollback is ClanManagementResult.Success) {
                        "Clan coffer withdrawal rollback failed for ${access.player.characterId}."
                    }
                    access.mes("You do not have enough inventory space for those coins.")
                }
            }
        }
    }

    private fun createSession(clan: ClanSnapshot): InstanceSession? {
        val session =
            instances.createServerOwned(
                key = CLAN_HALL_INSTANCE_KEY,
                spec = clanHallSpec(clan.hallSettings.playerCapacity),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            ) ?: return null
        sessionsByClan[clan.clanId] = session.id
        clanBySession[session.id] = clan.clanId
        occupantsBySession[session.id] = mutableMapOf()
        return session
    }

    private fun sessionForClan(clanId: Long): InstanceSession? {
        val id = sessionsByClan[clanId] ?: return null
        return instances.sessionForId(id)
            ?: run {
                removeSession(id)
                null
            }
    }

    private fun removeSession(instanceId: InstanceId) {
        val clanId = clanBySession.remove(instanceId)
        if (clanId != null) sessionsByClan.remove(clanId, instanceId)
        occupantsBySession.remove(instanceId)
    }

    private suspend fun archiveAbandonedPartyChest(clanId: Long, instanceId: InstanceId) {
        /* The repository implementation atomically archives rows before clearing the live chest. */
        domain.archivePartyChestCleanup(
            clanId = clanId,
            hallSessionId = instanceId.value.toString(),
            reason = "last_non_guest_left",
        )
    }

    private suspend fun releaseEditLeaseIfOwned(player: Player) {
        val clan = chat.ownClan(player) ?: return
        if (clan.hallSettings.editSessionCharacterId == player.characterId) {
            domain.releaseHallEditSession(player)
        }
    }

    private fun occupant(player: Player): Occupant? {
        val session = instances.sessionForPlayer(player)
        if (session == null || session.key != CLAN_HALL_INSTANCE_KEY) {
            player.mes("You must be inside a Clan Hall to do that.")
            return null
        }
        return occupantsBySession[session.id]?.get(player.characterId.toLong())
            ?: run {
                player.mes("Your Clan Hall membership is no longer active.")
                null
            }
    }

    private fun memberOccupant(player: Player): Occupant? {
        val value = occupant(player) ?: return null
        if (value.role != ClanHallRole.Member) {
            player.mes("Guests cannot use a clan coffer.")
            return null
        }
        return value
    }

    private fun clanSnapshot(player: Player, role: ClanHallRole): ClanSnapshot? =
        if (role == ClanHallRole.Member) chat.ownClan(player) else chat.guestClan(player)

    private fun permissionDenial(
        player: Player,
        occupant: Occupant,
        permission: ClanPermissionKey,
    ): String? {
        val clan = clanSnapshot(player, occupant.role) ?: return "That clan is no longer available."
        if (clan.clanId != occupant.clanId) return "Your Clan Hall membership is no longer valid."
        return ClanHallRules.permissionDenial(
            occupant.role,
            occupant.rank,
            clan.permissionSet,
            permission,
        )
    }

    private fun permissionLabel(clan: ClanSnapshot, key: ClanPermissionKey): String =
        "rank ${clan.permissionSet[key]}+"

    private fun showPartyChest(access: ProtectedAccess, entries: List<ClanPartyChestEntry>) {
        access.mes("Clan party chest: ${entries.size}/216 unique item slots used.")
        entries.take(8).forEach { access.mes("Item ${it.itemId}: ${it.quantity}") }
        if (entries.size > 8) access.mes("...and ${entries.size - 8} more item types.")
    }

    private fun <T> resultOrMessage(access: ProtectedAccess, result: ClanManagementResult<T>): T? =
        when (result) {
            is ClanManagementResult.Success -> result.value
            is ClanManagementResult.Denied -> {
                access.mes(result.message)
                null
            }
        }

    private fun deny(player: Player, message: String): Boolean {
        player.mes(message)
        return false
    }

    private data class Occupant(
        val clanId: Long,
        val characterId: Int,
        val role: ClanHallRole,
        val rank: Int,
    )

    private data class ActiveBalloon(val loc: LocInfo, var prize: ClanHallBalloonPrize?)

    private data class BalloonDrop(
        var queue: ClanHallBalloonPrizeQueue,
        val active: LinkedHashMap<CoordGrid, ActiveBalloon> = linkedMapOf(),
        val popped: MutableMap<CoordGrid, LocInfo> = mutableMapOf(),
        var visualSequence: Int = 0,
        var committed: Boolean = false,
    )
}
