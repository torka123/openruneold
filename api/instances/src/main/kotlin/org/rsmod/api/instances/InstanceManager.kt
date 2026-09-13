package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.InstancePersonalBestEvent
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.InstancePlayerJoinEvent
import org.rsmod.api.instances.events.InstancePlayerJoinUnboundEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveUnboundEvent
import org.rsmod.api.instances.events.InstanceStartedEvent
import org.rsmod.api.instances.events.InstanceTimeTickEvent
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.api.instances.region.enterCoord
import org.rsmod.api.instances.region.localCoord
import org.rsmod.api.instances.timer.InstanceKillTimer
import org.rsmod.api.instances.timer.InstanceTiming
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.events.KeyedEvent
import org.rsmod.game.MapClock
import org.rsmod.game.damage.DamageContributions
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal fun isWithinInstanceBounds(
    coords: CoordGrid,
    southWest: CoordGrid,
    northEast: CoordGrid,
): Boolean =
    coords.x >= southWest.x &&
        coords.x < northEast.x &&
        coords.z >= southWest.z &&
        coords.z < northEast.z

@Singleton
public class InstanceManager
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val eventBus: EventBus,
    private val areaResolver: InstanceAreaResolver,
    private val worldClock: MapClock,
    private val collision: CollisionFlagMap,
    private val npcList: NpcList,
) {
    private val nextId = AtomicLong(1)

    private val sessions = HashMap<InstanceId, InstanceSession>()
    private val regions = HashMap<InstanceId, Region>()
    private val regionToInstance = HashMap<Int, InstanceId>()
    private val ownerIndex = HashMap<Long, InstanceId>()
    private val playerIndex = HashMap<Long, InstanceId>()
    private val spawnedNpcs = HashMap<InstanceId, MutableList<Npc>>()
    // Public lookups are by entity object. Owning that identity survives transforms and UID
    // retirement, and cannot accidentally resolve a later NPC reusing the same slot.
    private val npcInstanceIndex = IdentityHashMap<Npc, InstanceId>()
    private val deferredLogoutReclaims = HashMap<InstanceId, Int>()
    // Native add/delete callbacks are synchronous. Retire authority immediately, but wait for
    // the outer repository operation before deleting actors or permitting overlapping ownership.
    private val fixedActorOperations = mutableSetOf<InstanceId>()
    private val closingFixedSessions = mutableSetOf<InstanceId>()
    private val failedFixedTeardowns = mutableSetOf<InstanceId>()
    private val retiringFixedNpcs = IdentityHashMap<Npc, InstanceId>()

    public sealed interface Result {
        public data class Created(val session: InstanceSession, val enter: CoordGrid) : Result

        public data class Joined(val session: InstanceSession, val enter: CoordGrid) : Result

        public data class Failed(val reason: String) : Result
    }

    public fun create(
        owner: Player,
        key: String,
        spec: InstanceSpec,
        access: InstanceAccess,
        currentTick: Int,
    ): Result {
        if (spec.area is InstanceArea.Fixed) {
            return Result.Failed("Fixed public areas must be owned by the server.")
        }
        val ownerId = owner.playerId()

        if (ownerIndex.containsKey(ownerId)) {
            return Result.Failed("You already have an active instance.")
        }

        if (spec.fee > 0 && !owner.invTakeFee(spec.fee)) {
            return Result.Failed("You need ${spec.fee} coins to create this instance.")
        }

        val placement =
            when (val resolved = areaResolver.resolve(spec.area)) {
                is InstanceAreaResolver.Result.Ready -> resolved.placement
            }

        val region = regionRepo.add((placement as InstancePlacement.Allocated).regionTemplate)
        if (region == null) {
            areaResolver.release(spec.area, placement)
            if (spec.fee > 0) owner.invAdd(owner.inv, "obj.coins", spec.fee)
            return Result.Failed("No instance space available, try again shortly.")
        }

        val instanceId = allocateId()
        val session =
            InstanceSession(
                id = instanceId,
                regionIds = mutableSetOf(region.uid),
                owner = ownerId,
                key = key,
                spec = spec,
                placement = placement,
                access = access,
            )
        sessions[instanceId] = session
        regions[instanceId] = region
        registerRegion(instanceId, region.uid)
        ownerIndex[ownerId] = instanceId

        startSession(session, currentTick)

        val spawned = mutableListOf<Npc>()
        if (!spec.spawnOnFirstJoin) {
            spawnSessionNpcs(session, region, spawned, currentTick)
        }
        spawnedNpcs[instanceId] = spawned

        return Result.Created(session, session.enterCoord(region))
    }

    public fun createServerOwned(
        key: String,
        spec: InstanceSpec,
        access: InstanceAccess,
        currentTick: Int,
    ): InstanceSession? {
        val placement =
            when (val resolved = areaResolver.resolve(spec.area)) {
                is InstanceAreaResolver.Result.Ready -> resolved.placement
            }

        if (placement is InstancePlacement.Fixed) {
            return createFixed(key, spec, access, placement, currentTick)
        }
        val region =
            regionRepo.add((placement as InstancePlacement.Allocated).regionTemplate)
                ?: run {
                    areaResolver.release(spec.area, placement)
                    return null
                }

        val instanceId = allocateId()
        val session =
            InstanceSession(
                id = instanceId,
                regionIds = mutableSetOf(region.uid),
                owner = SERVER_OWNER_ID,
                key = key,
                spec = spec,
                placement = placement,
                access = access,
                isServerOwned = true,
            )
        sessions[instanceId] = session
        regions[instanceId] = region
        registerRegion(instanceId, region.uid)
        regionRepo.protect(region)
        spawnedNpcs[instanceId] = mutableListOf()
        return session
    }

    public fun sessionForOwner(ownerId: Long): InstanceSession? {
        val instanceId = ownerIndex[ownerId] ?: return null
        val session = sessions[instanceId]
        if (session == null) {
            ownerIndex.remove(ownerId)
            return null
        }
        return session
    }

    public fun sessionOwnedBy(key: String?, ownerId: Long): InstanceSession? {
        val session = sessionForOwner(ownerId) ?: return null
        if (key != null && session.key != key) {
            return null
        }
        return session
    }

    public fun sessionForKeyAndMemberName(key: String, memberName: String): InstanceSession? {
        val trimmed = memberName.trim()
        return sessionsForKey(key).firstOrNull { sessionMatchesMember(it, trimmed) }
    }

    public fun sessionForMemberName(memberName: String): InstanceSession? {
        val trimmed = memberName.trim()
        return sessions.values.firstOrNull { sessionMatchesMember(it, trimmed) }
    }

    public fun sessionOwnedByName(key: String?, ownerName: String): InstanceSession? {
        if (key != null) {
            return sessionForKeyAndMemberName(key, ownerName)
        }
        return sessionForMemberName(ownerName)
    }

    public fun memberLabel(playerId: Long): String? {
        val player = playerList.firstOrNull { it.uuid == playerId } ?: return null
        return player.displayName.ifBlank { player.username }.ifBlank { null }
    }

    public fun firstSession(): InstanceSession? = sessions.values.firstOrNull(::isLive)

    public fun sessionsForKey(key: String): Collection<InstanceSession> =
        sessions.values.filter { it.key == key && isLive(it) }

    public fun sessionForPlayer(player: Player): InstanceSession? =
        indexedSession(player)?.takeIf { session ->
            isLive(session) &&
                (!session.isFixed || fixedOccupant(session, player)?.resolve(playerList) === player)
        }

    private fun indexedSession(player: Player): InstanceSession? =
        player.currentInstanceId()?.let(sessions::get)
            ?: playerIndex[player.playerId()]?.let(sessions::get)

    /** Live occupant objects; fixed areas never resolve an admission to a replacement login. */
    public fun playersIn(session: InstanceSession): List<Player> =
        if (isLive(session)) session.livePlayers(playerList) else emptyList()

    public fun sessionForId(id: InstanceId): InstanceSession? = sessions[id]?.takeIf(::isLive)

    public fun sessionForRegion(regionId: Int): InstanceSession? =
        regionToInstance[regionId]?.let(sessions::get)

    /** Resolves an original-map coordinate in this session's allocated or fixed placement. */
    public fun localCoord(session: InstanceSession, local: RegionLocal): CoordGrid? {
        if (!isLive(session)) return null
        val region = regions[session.id]
        if (!session.isFixed && region == null) return null
        return session.localCoord(region, local)
    }

    public fun contributionsFor(id: InstanceId): DamageContributions? =
        sessionForId(id)?.damageContributions

    public fun instanceForNpc(npc: Npc): InstanceId? = npcInstanceIndex[npc]

    public fun npcsForInstance(id: InstanceId): List<Npc> = spawnedNpcs[id]?.toList() ?: emptyList()

    public fun attachNpc(instanceId: InstanceId, npc: Npc) {
        val session = requireNotNull(sessionForId(instanceId)) { "Instance no longer exists" }
        require(npc.isSlotAssigned && npc.uid != NpcUid.NULL) {
            "Register the NPC before attaching it to an instance"
        }
        if (session.isFixed) require(npc.uid.resolve(npcList) === npc)
        val previous = npcInstanceIndex[npc]
        require(previous == null || previous == instanceId) {
            "NPC already belongs to instance $previous"
        }
        val owned = spawnedNpcs.getOrPut(instanceId) { mutableListOf() }
        if (owned.none { it === npc }) owned.add(npc)
        indexNpc(instanceId, npc)
    }

    /** Releases an encounter-owned helper without retaining it for the whole instance lifetime. */
    public fun detachNpc(instanceId: InstanceId, npc: Npc) {
        if (
            sessions[instanceId]?.isFixed == true &&
                npc.isSlotAssigned &&
                npc.uid.resolve(npcList) !== npc
        ) {
            // The lifecycle Delete listener runs before native collision/zone retirement.
            retiringFixedNpcs[npc] = instanceId
        } else if (!npc.isSlotAssigned) {
            retiringFixedNpcs.remove(npc)
        }
        spawnedNpcs[instanceId]?.removeAll { it === npc }
        if (npcInstanceIndex[npc] == instanceId) npcInstanceIndex.remove(npc)
    }

    public fun registerSessionNpc(player: Player, npc: Npc): Boolean {
        val instanceId = sessionForPlayer(player)?.id ?: return false
        attachNpc(instanceId, npc)
        sessionForId(instanceId)?.let { startBossKillTimer(it, npc, worldClock.cycle) }
        return true
    }

    public fun canSpawnBosses(player: Player): Boolean {
        val session = sessionForPlayer(player) ?: return true
        return !session.arenaExpired
    }

    public fun join(
        player: Player,
        session: InstanceSession,
        currentTick: Int,
        code: String? = null,
        forceAccess: Boolean = false,
    ): Result {
        if (!isLive(session)) return Result.Failed("That instance no longer exists.")
        if (session.isFixed) reconcileFixedOccupants(session, currentTick)
        if (!isLive(session)) return Result.Failed("That instance no longer exists.")
        val playerId = player.playerId()
        if (!forceAccess && !canJoin(playerId, session, code)) {
            return Result.Failed("You do not have access to that instance.")
        }
        if (session.state is SessionState.Grace) {
            return Result.Failed("That instance is ending and no longer accepts players.")
        }
        if (!session.occupants.contains(playerId) && session.isFull()) {
            return Result.Failed("That instance is full.")
        }
        val region = regions[session.id]
        if (!session.isFixed && region == null)
            return Result.Failed("That instance no longer exists.")
        if (!session.isServerOwned && !regionRepo.isValid(checkNotNull(region))) {
            destroy(session)
            return Result.Failed(
                "That instance is no longer available. Please create a new instance."
            )
        }
        abandonOwnedSessionIfEmpty(player, session, currentTick)
        if (!isLive(session)) return Result.Failed("That instance no longer exists.")
        if (!session.isFixed && session.occupants.isEmpty() && !session.arenaExpired) {
            resetNpcs(session, region, currentTick)
            startSession(session, currentTick)
        }
        return Result.Joined(session, session.enterCoord(region))
    }

    /** Call after the player has telejumped into the instance (e.g. after an enter transition). */
    public fun finalizeEntry(player: Player, session: InstanceSession, currentTick: Int) {
        if (session.isFixed) {
            finalizeFixedEntry(player, session, currentTick)
            return
        }
        val playerId = player.playerId()
        rememberRecoveryExit(player, session.placement.exitCoord)
        if (playerId in session.occupants) {
            return
        }
        assignOccupant(player, session)
        publishPlayerJoin(player, session)
    }

    /** Destroys an owned instance that was created but never entered (e.g. logout during fade). */
    public fun cancelPendingEntry(player: Player, currentTick: Int) {
        val playerId = player.playerId()
        val ownedId = ownerIndex[playerId] ?: return
        val session =
            sessions[ownedId]
                ?: run {
                    ownerIndex.remove(playerId)
                    return
                }
        if (playerId in session.occupants) {
            return
        }
        destroy(session)
    }

    private fun abandonOwnedSessionIfEmpty(
        player: Player,
        target: InstanceSession,
        currentTick: Int,
    ) {
        val playerId = player.playerId()
        val ownedId = ownerIndex[playerId] ?: return
        if (ownedId == target.id) {
            return
        }
        val owned = sessions[ownedId] ?: return
        if (playerId in owned.occupants) {
            leave(player, owned, currentTick)
        } else if (owned.owner == playerId) {
            destroy(owned)
        }
    }

    /**
     * Replaces instance-owned actors through the same pipeline as an empty-room rejoin. Membership,
     * session content and the room lifetime are retained. Intended for coordinated group respawns.
     */
    public fun resetNpcs(session: InstanceSession, currentTick: Int): Boolean {
        if (!isLive(session) || session.arenaExpired) return false
        if (session.state !is SessionState.Active) return false
        if (session.isFixed) {
            if (session.id in fixedActorOperations) return false
            withFixedActorOperation(session) { resetNpcs(session, null, currentTick) }
            return isLive(session)
        }
        val region = regions[session.id] ?: return false
        if (!session.isServerOwned && !regionRepo.isValid(region)) return false
        resetNpcs(session, region, currentTick)
        return true
    }

    private fun resetNpcs(session: InstanceSession, region: Region?, currentTick: Int) {
        if (session.isFixed) deleteFixedActors(session)
        else spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
        if (!isLive(session)) return
        // A native Delete callback may attach a fresh helper to the surviving room. Retain the
        // authoritative collector so that helper stays owned through the next reset/termination.
        val spawned =
            if (session.isFixed) spawnedNpcs.getOrPut(session.id) { mutableListOf() }
            else mutableListOf()
        if (session.isFixed) spawnedNpcs[session.id] = spawned
        spawnSessionNpcs(session, region, spawned, currentTick)
        spawnedNpcs[session.id] = spawned
    }

    private fun spawnSessionNpcs(
        session: InstanceSession,
        region: Region?,
        out: MutableList<Npc>,
        currentTick: Int,
    ) {
        if (session.arenaExpired) {
            return
        }
        val placement = session.placement
        for (entry in placement.npcSpawns) {
            if (session.isFixed && !isLive(session)) return
            val type = ServerCacheManager.getNpc(entry.npcType.asRSCM(RSCMType.NPC))
            if (type == null) {
                check(!session.isFixed) { "Missing fixed instance NPC: ${entry.npcType}" }
                continue
            }
            val npc = Npc(type, session.localCoord(region, entry.coord))
            npc.mode = type.defaultMode
            if (session.isFixed) {
                out += npc
                npcInstanceIndex[npc] = session.id
            }
            try {
                npcRepo.add(npc, Int.MAX_VALUE)
            } catch (error: Throwable) {
                if (session.isFixed && npc.uid.resolve(npcList) !== npc) {
                    // The native call did not return a complete registration. It may have run
                    // callbacks that retired the NPC before throwing; do not reopen these tiles.
                    failedFixedTeardowns += session.id
                }
                throw error
            }
            if (session.isFixed) {
                if (npc.uid.resolve(npcList) !== npc) {
                    failedFixedTeardowns += session.id
                    error("Fixed instance NPC lost during native creation; area remains closed")
                }
                if (!isLive(session)) return
                check(out.any { it === npc } && npcInstanceIndex[npc] == session.id) {
                    "Fixed instance NPC ownership changed during creation"
                }
            }
            indexNpc(session.id, npc)
            if (!session.isFixed) out += npc
            startBossKillTimer(session, npc, currentTick)
        }
    }

    private fun canJoin(playerId: Long, session: InstanceSession, code: String?): Boolean =
        when (val access = session.access) {
            is InstanceAccess.Private -> playerId == session.owner
            is InstanceAccess.Friends -> true
            is InstanceAccess.Code -> code != null && code == access.value
        }

    public fun leave(player: Player, session: InstanceSession, currentTick: Int): CoordGrid {
        removeOccupant(player, session, currentTick)
        if (!session.isFixed) clearRecoveryExit(player)
        return session.placement.exitCoord
    }

    /**
     * Detaches a player who teleports outside the bounds of their current dynamic region. Teleports
     * between floors or rooms inside the same region deliberately retain membership.
     */
    public fun leaveForExternalTeleport(
        player: Player,
        destination: CoordGrid,
        currentTick: Int,
    ): Boolean {
        val session = sessionForPlayer(player) ?: return false
        val remainsInside = contains(session, destination)
        if (remainsInside) return false
        removeOccupant(player, session, currentTick)
        if (!session.isFixed) clearRecoveryExit(player)
        eventBus.publish(
            InstancePlayerExternalTeleportEvent(player, session.key, session.id, session.owner)
        )
        return true
    }

    /** Immediately tears down a verified session and all of its instance-owned entities. */
    public fun terminate(session: InstanceSession): Boolean {
        if (!isLive(session)) return false
        destroy(session)
        return true
    }

    /**
     * Ends instance membership and moves the server-side player to the configured public exit
     * before character persistence runs. The recovery marker deliberately remains until the next
     * successful login, providing a second line of defence if saving or shutdown is interrupted.
     * Instance teardown is delayed one cycle so content logout hooks can finish against a valid
     * region regardless of event subscription order.
     */
    public fun leaveForLogout(player: Player, currentTick: Int): CoordGrid? {
        val session = sessionForDeparture(player)
        if (session != null) {
            if (session.isFixed) {
                val savedCoords = player.coords
                removeOccupant(player, session, currentTick, reclaimWhenEmpty = false)
                return savedCoords
            }
            val exit = session.placement.exitCoord
            rememberRecoveryExit(player, exit)
            removeOccupant(player, session, currentTick, reclaimWhenEmpty = false)
            deferredLogoutReclaims[session.id] = currentTick + 1
            if (exit != CoordGrid.ZERO) {
                player.coords = exit
            }
            return exit
        }
        cancelPendingEntry(player, currentTick)
        return null
    }

    public fun handleLogout(player: Player, currentTick: Int) {
        leaveForLogout(player, currentTick)
    }

    /** Returns true when an active or pending instance was ended by the disconnect. */
    public fun handleDisconnect(player: Player, currentTick: Int): Boolean {
        val pending =
            sessionForOwner(player.playerId())?.let { player.playerId() !in it.occupants } == true
        return leaveForLogout(player, currentTick) != null || pending
    }

    public fun handleDeath(player: Player, currentTick: Int) {
        val session = sessionForDeparture(player) ?: return
        if (RaidLogoutTabPolicy.retainsInstanceMembershipOnDeath(session.key)) return
        removeOccupant(player, session, currentTick)
        if (!session.isFixed) clearRecoveryExit(player)
    }

    public fun handleBossKill(npc: Npc, currentTick: Int) {
        val instanceId = instanceForNpc(npc) ?: return
        val session = sessionForId(instanceId) ?: return
        if (!countsAsFightBoss(session, npc)) {
            return
        }

        completeBossKillTimer(session, currentTick)

        if (session.state != SessionState.Active || !session.gracePending) {
            return
        }
        if (isBossAlive(session)) {
            return
        }
        beginGrace(session, currentTick)
    }

    public fun tickReclaim(currentTick: Int) {
        retiringFixedNpcs.entries.removeIf { !it.key.isSlotAssigned }
        for (id in closingFixedSessions.toList()) {
            if (id !in fixedActorOperations && id !in failedFixedTeardowns) {
                sessions[id]?.let(::destroyFixed)
            }
        }
        reclaimDeferredLogouts(currentTick)
        reconcileOccupants(currentTick)
        tickTimed(currentTick)
        val expired = sessions.values.filter { it.isExpired(currentTick) }
        expired.forEach(::destroy)
    }

    private fun reclaimDeferredLogouts(currentTick: Int) {
        val due = deferredLogoutReclaims.filterValues { currentTick >= it }.keys.toList()
        for (id in due) {
            deferredLogoutReclaims.remove(id)
            sessions[id]?.let { reclaimEmptySession(it, currentTick) }
        }
    }

    private fun reconcileOccupants(currentTick: Int) {
        for (session in sessions.values.toList()) {
            if (!isLive(session)) continue
            if (session.isFixed) {
                reconcileFixedOccupants(session, currentTick)
                continue
            }
            val region = regions[session.id] ?: continue
            val leavers =
                session.occupants.filter { occupant ->
                    val player = playerList.firstOrNull { it.uuid == occupant }
                    player == null ||
                        !isWithinInstanceBounds(player.coords, region.southWest, region.northEast)
                }
            for (occupant in leavers) {
                val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
                leaveForExternalTeleport(player, player.coords, currentTick)
            }
        }
    }

    private fun tickTimed(currentTick: Int) {
        for (session in sessions.values.toList()) {
            if (!isLive(session) || (session.isFixed && !session.fixedInitialized)) continue
            when (val state = session.state) {
                SessionState.Active -> tickActiveTimer(session, currentTick)
                is SessionState.Grace -> tickGraceTimer(session, currentTick, state)
                is SessionState.Reclaim -> Unit
            }
        }
    }

    private fun tickActiveTimer(session: InstanceSession, currentTick: Int) {
        val limit = session.spec.timeLimitTicks ?: return
        if (session.occupants.isEmpty()) {
            return
        }

        val elapsed = session.elapsedTicks(currentTick)
        val remaining = limit - elapsed

        publish(
            InstanceTimeTickEvent(
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
                spec = session.spec,
                currentTick = currentTick,
                startedAtTick = session.startedAtTick,
                elapsedTicks = elapsed,
                remainingTicks = remaining,
            )
        )

        InstanceTiming.notifyThresholds(
            remainingTicks = remaining,
            totalTicks = limit,
            sent = session.timeWarningsSent,
            players = InstanceTiming.playersIn(session, playerList),
        ) { duration ->
            "Your instance will end in $duration."
        }

        if (remaining <= 0) {
            if (session.markArenaExpired()) {
                messageOccupantsRed(session, INSTANCE_ARENA_EXPIRED_MESSAGE)
            }
            if (isBossAlive(session)) {
                session.markGracePending()
                return
            }
            beginGrace(session, currentTick)
        }
    }

    private fun startBossKillTimer(session: InstanceSession, npc: Npc, currentTick: Int) {
        if (
            !session.spec.recordsEligible ||
                !countsAsFightBoss(session, npc) ||
                !InstanceKillTimer.tracksKillTime(session)
        ) {
            return
        }
        session.bossFightStartTick = currentTick
    }

    private fun completeBossKillTimer(session: InstanceSession, currentTick: Int) {
        if (session.isServerOwned || !session.spec.recordsEligible) {
            session.bossFightStartTick = null
            return
        }

        val startTick = session.bossFightStartTick ?: return
        session.bossFightStartTick = null
        if (!InstanceKillTimer.tracksKillTime(session)) {
            return
        }
        val elapsed = currentTick - startTick
        for (player in InstanceTiming.playersIn(session, playerList)) {
            if (session.damageContributions.damageBy(player) <= 0) continue
            val improved = InstanceKillTimer.reportKillTime(player, session.key, elapsed)
            if (improved) {
                eventBus.publish(
                    InstancePersonalBestEvent(
                        player = player,
                        key = session.key,
                        elapsedTicks = elapsed,
                        formattedDuration = InstanceKillTimer.formatDuration(elapsed),
                    )
                )
            }
        }
    }

    private fun countsAsFightBoss(session: InstanceSession, npc: Npc): Boolean {
        val bossTypes = session.spec.bossNpcs ?: return false
        return bossTypes.any { bossType -> npcMatchesBossType(npc, bossType) }
    }

    private fun isBossAlive(session: InstanceSession): Boolean {
        val bossTypes = session.spec.bossNpcs
        val spawnTypes = session.placement.npcSpawns.map { it.npcType }.toSet()

        if (bossTypes.isNullOrEmpty() && spawnTypes.isEmpty()) {
            return false
        }

        val npcs = spawnedNpcs[session.id] ?: return false
        return npcs.any { npc ->
            npc.isSlotAssigned && npc.hitpoints > 0 && npcCountsAsBoss(npc, bossTypes, spawnTypes)
        }
    }

    private fun npcCountsAsBoss(
        npc: Npc,
        bossTypes: List<NpcServerType>?,
        spawnTypes: Set<String>,
    ): Boolean {
        if (bossTypes?.any { npcMatchesBossType(npc, it) } == true) {
            return true
        }

        return spawnTypes.any { spawnType -> npc.isType(spawnType) || npc.isVisType(spawnType) }
    }

    private fun npcMatchesBossType(npc: Npc, bossType: NpcServerType): Boolean {
        return npc.type == bossType ||
            npc.visType == bossType ||
            npc.isType(bossType.internalName) ||
            npc.isVisType(bossType.internalName)
    }

    private fun tickGraceTimer(
        session: InstanceSession,
        currentTick: Int,
        grace: SessionState.Grace,
    ) {
        val remaining = grace.deadlineTick - currentTick

        if (session.occupants.isNotEmpty()) {
            InstanceTiming.notifyThresholds(
                remainingTicks = remaining,
                totalTicks = session.spec.graceTicks,
                sent = session.graceWarningsSent,
                players = InstanceTiming.playersIn(session, playerList),
            ) { duration ->
                "You will be removed from the instance in $duration."
            }
        }

        if (remaining <= 0) {
            expireGrace(session, currentTick)
        }
    }

    private fun beginGrace(session: InstanceSession, currentTick: Int) {
        if (session.state is SessionState.Grace) {
            return
        }
        despawnSessionNpcs(session)
        session.enterGrace(currentTick)
        val graceLabel = InstanceTiming.formatRemaining(session.spec.graceTicks)
        messageOccupants(
            session,
            "The instance timer has ended. You have $graceLabel to collect loot before being removed.",
        )
    }

    private fun expireGrace(session: InstanceSession, currentTick: Int) {
        val exit = session.placement.exitCoord
        for (player in playersIn(session)) {
            if (session.isFixed) {
                val binding = fixedOccupant(session, player) ?: continue
                if (!isLive(session) || binding.resolve(playerList) !== player) continue
                player.coords = exit
                removeFixedOccupant(session, binding, currentTick, publishEvent = true)
                continue
            }
            leave(player, session, currentTick)
            player.coords = exit
        }
        if (session.isServerOwned) {
            if (session.state is SessionState.Grace) {
                resetServerOwned(session, currentTick)
            }
        } else {
            destroy(session)
        }
    }

    private fun resetServerOwned(session: InstanceSession, currentTick: Int) {
        if (session.isFixed) {
            if (!isLive(session) || session.id in fixedActorOperations) return
            withFixedActorOperation(session) {
                session.resetState()
                if (isLive(session)) resetNpcs(session, null, currentTick)
            }
            return
        }
        session.resetState()
        val region = regions[session.id] ?: return
        spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
        val spawned = mutableListOf<Npc>()
        spawnSessionNpcs(session, region, spawned, currentTick)
        spawnedNpcs[session.id] = spawned
    }

    private fun despawnSessionNpcs(session: InstanceSession) {
        if (session.isFixed) {
            if (session.id !in fixedActorOperations) {
                withFixedActorOperation(session) { deleteFixedActors(session) }
            }
            return
        }
        spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
    }

    private fun messageOccupants(session: InstanceSession, text: String) {
        for (player in InstanceTiming.playersIn(session, playerList)) {
            player.mes(text, ChatType.Engine)
        }
    }

    private fun messageOccupantsRed(session: InstanceSession, text: String) {
        messageOccupants(session, "<col=ff0000>$text</col>")
    }

    private fun destroy(session: InstanceSession) {
        if (session.isFixed) {
            closingFixedSessions += session.id
            if (session.id !in fixedActorOperations) destroyFixed(session)
            return
        }
        if (sessions.remove(session.id) == null) return
        // A specialized cleanup failure must not retain native entities, indexes or occupants.
        val cleanupFailure = runCatching { session.markEnded() }.exceptionOrNull()
        deferredLogoutReclaims.remove(session.id)
        publish(
            InstanceEndedEvent(
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
                spec = session.spec,
            )
        )
        areaResolver.release(session.spec.area, session.placement)
        spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
        for (regionId in session.regionIds) {
            regionToInstance.remove(regionId, session.id)
        }
        val region = regions.remove(session.id)
        if (session.isServerOwned && region != null) {
            regionRepo.unprotect(region)
        }
        ownerIndex.remove(session.owner)
        playerIndex.entries.removeIf { it.value == session.id }
        val exitCoord = session.placement.exitCoord
        for (occupant in session.occupants) {
            val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
            if (exitCoord != CoordGrid.ZERO) {
                player.attr[InstanceAttributes.LOGIN_EXIT_COORD] = exitCoord.packed
            }
            player.clearInstance()
        }
        session.occupants.clear()
        cleanupFailure?.let { throw it }
    }

    private fun removeOccupant(
        player: Player,
        session: InstanceSession,
        currentTick: Int,
        reclaimWhenEmpty: Boolean = true,
    ) {
        if (session.isFixed) {
            val occupant = fixedOccupant(session, player) ?: return
            removeFixedOccupant(session, occupant, currentTick, publishEvent = true)
            if (reclaimWhenEmpty && isLive(session)) reclaimEmptySession(session, currentTick)
            return
        }
        val playerId = player.playerId()
        session.removeOccupant(playerId, currentTick)
        clearOccupant(player)
        publishPlayerLeave(player, session)
        if (reclaimWhenEmpty) reclaimEmptySession(session, currentTick)
    }

    private fun reclaimEmptySession(session: InstanceSession, currentTick: Int) {
        if (
            session.spec.destroyWhenEmpty &&
                !session.isServerOwned &&
                session.occupants.isEmpty() &&
                session.state !is SessionState.Grace
        ) {
            destroy(session)
            return
        }
        if (
            session.isServerOwned &&
                session.occupants.isEmpty() &&
                session.state is SessionState.Grace
        ) {
            resetServerOwned(session, currentTick)
        }
    }

    private fun rememberRecoveryExit(player: Player, exit: CoordGrid) {
        if (exit != CoordGrid.ZERO) {
            player.attr[InstanceAttributes.LOGIN_EXIT_COORD] = exit.packed
        }
    }

    private fun clearRecoveryExit(player: Player) {
        player.attr.remove(InstanceAttributes.LOGIN_EXIT_COORD)
    }

    private fun assignOccupant(player: Player, session: InstanceSession) {
        val playerId = player.playerId()
        session.addOccupant(playerId)
        player.assignInstance(session.id)
        playerIndex[playerId] = session.id
    }

    private fun clearOccupant(player: Player) {
        player.clearInstance()
        playerIndex.remove(player.playerId())
    }

    private fun startSession(session: InstanceSession, currentTick: Int) {
        session.damageContributions.clear()
        session.markStarted(currentTick)
        publish(
            InstanceStartedEvent(
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
                spec = session.spec,
                startedAtTick = session.startedAtTick,
            )
        )
    }

    private fun publishPlayerJoin(player: Player, session: InstanceSession) {
        val binding = if (session.isFixed) fixedOccupant(session, player) else null
        publish(
            InstancePlayerJoinEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
        if (
            session.isFixed &&
                (!isLive(session) ||
                    binding == null ||
                    session.fixedOccupants[binding.uuid] !== binding ||
                    binding.resolve(playerList) !== player ||
                    !contains(session, player.coords))
        )
            return
        eventBus.publish(
            InstancePlayerJoinUnboundEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
    }

    private fun publishPlayerLeave(player: Player, session: InstanceSession) {
        publish(
            InstancePlayerLeaveEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
        eventBus.publish(
            InstancePlayerLeaveUnboundEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
    }

    private fun publish(event: KeyedEvent) {
        eventBus.publish(event)
    }

    private fun allocateId(): InstanceId = InstanceId(nextId.getAndIncrement())

    private fun registerRegion(instanceId: InstanceId, regionId: Int) {
        regionToInstance[regionId] = instanceId
        sessions[instanceId]?.registerRegion(regionId)
    }

    private fun indexNpc(instanceId: InstanceId, npc: Npc) {
        if (npc.uid == NpcUid.NULL) return
        npcInstanceIndex[npc] = instanceId
    }

    private fun untagAndDelete(npc: Npc) {
        npcInstanceIndex.remove(npc)
        if (!npc.isSlotAssigned) {
            return
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun Player.playerId(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }

    private fun sessionMatchesMember(session: InstanceSession, name: String): Boolean {
        if (!isLive(session)) return false
        if (session.isFixed) return playersIn(session).any { playerNameMatches(it, name) }
        if (playerNameMatches(session.owner, name)) {
            return true
        }
        return session.occupants.any { playerNameMatches(it, name) }
    }

    private fun playerNameMatches(playerId: Long, name: String): Boolean {
        val player = playerList.firstOrNull { it.uuid == playerId } ?: return false
        return playerNameMatches(player, name)
    }

    private fun playerNameMatches(player: Player, name: String): Boolean =
        player.displayName.equals(name, ignoreCase = true) ||
            player.username.equals(name, ignoreCase = true)

    private val InstanceSession.isFixed: Boolean
        get() = placement is InstancePlacement.Fixed

    private fun isLive(session: InstanceSession): Boolean =
        sessions[session.id] === session && !session.isEnded && session.id !in closingFixedSessions

    private fun contains(session: InstanceSession, coords: CoordGrid): Boolean =
        when (val placement = session.placement) {
            is InstancePlacement.Fixed -> placement.area.contains(coords)
            is InstancePlacement.Allocated ->
                regions[session.id]?.let {
                    isWithinInstanceBounds(coords, it.southWest, it.northEast)
                } ?: false
        }

    private fun fixedOccupant(session: InstanceSession, player: Player): FixedInstanceOccupant? =
        session.fixedOccupants[player.playerId()]?.takeIf { it.matches(player) }

    private fun sessionForDeparture(player: Player): InstanceSession? =
        indexedSession(player)?.takeIf {
            isLive(it) && (!it.isFixed || fixedOccupant(it, player) != null)
        }

    private fun createFixed(
        key: String,
        spec: InstanceSpec,
        access: InstanceAccess,
        placement: InstancePlacement.Fixed,
        currentTick: Int,
    ): InstanceSession? {
        // Includes retiring/failed ownership: old native cleanup must finish before replacement.
        if (
            sessions.values.any {
                val other = it.placement as? InstancePlacement.Fixed
                other != null && placement.area.overlaps(other.area)
            }
        )
            return null
        val session =
            InstanceSession(
                allocateId(),
                mutableSetOf(),
                SERVER_OWNER_ID,
                key,
                spec,
                placement,
                access,
                isServerOwned = true,
            )
        sessions[session.id] = session
        spawnedNpcs[session.id] = mutableListOf()
        if (!spec.spawnOnFirstJoin) initializeFixed(session, currentTick)
        return session.takeIf(::isLive)
    }

    private fun initializeFixed(session: InstanceSession, currentTick: Int) {
        if (session.fixedInitialized || !isLive(session)) return
        withFixedActorOperation(session) {
            session.fixedInitialized = true
            startSession(session, currentTick)
            if (isLive(session)) {
                spawnSessionNpcs(session, null, checkNotNull(spawnedNpcs[session.id]), currentTick)
            }
        }
    }

    private fun finalizeFixedEntry(player: Player, session: InstanceSession, currentTick: Int) {
        if (!isLive(session) || session.state !is SessionState.Active || session.arenaExpired)
            return
        if (player.uid.resolve(playerList) !== player || !contains(session, player.coords)) return
        reconcileFixedOccupants(session, currentTick)
        if (!isLive(session) || session.state !is SessionState.Active || session.arenaExpired)
            return
        if (player.uid.resolve(playerList) !== player || !contains(session, player.coords)) return
        val existing = sessionForPlayer(player)
        if (existing != null) return
        if (session.isFull()) return
        val occupant = FixedInstanceOccupant(player)
        session.fixedOccupants[occupant.uuid] = occupant
        session.addOccupant(occupant.uuid)
        player.attr[InstanceAttributes.CURRENT_INSTANCE_ID] = session.id.value
        playerIndex[occupant.uuid] = session.id
        initializeFixed(session, currentTick)
        if (!isLive(session) || session.fixedOccupants[occupant.uuid] !== occupant) return
        if (occupant.resolve(playerList) !== player || !contains(session, player.coords)) {
            removeFixedOccupant(session, occupant, currentTick, publishEvent = false)
            return
        }
        publishPlayerJoin(player, session)
    }

    private fun reconcileFixedOccupants(session: InstanceSession, currentTick: Int) {
        for (occupant in session.fixedOccupants.values.toList()) {
            if (!isLive(session)) return
            val player = occupant.resolve(playerList)
            if (player != null && contains(session, player.coords)) continue
            removeFixedOccupant(session, occupant, currentTick, publishEvent = true)
        }
    }

    private fun removeFixedOccupant(
        session: InstanceSession,
        occupant: FixedInstanceOccupant,
        currentTick: Int,
        publishEvent: Boolean,
    ) {
        if (session.fixedOccupants[occupant.uuid] !== occupant) return
        session.fixedOccupants.remove(occupant.uuid)
        session.removeOccupant(occupant.uuid, currentTick)
        playerIndex.remove(occupant.uuid, session.id)
        val player = occupant.player
        if (occupant.matches(player) && player.currentInstanceId() == session.id) {
            player.attr.remove(InstanceAttributes.CURRENT_INSTANCE_ID)
        }
        if (publishEvent) publishPlayerLeave(player, session)
    }

    private inline fun withFixedActorOperation(session: InstanceSession, action: () -> Unit) {
        check(fixedActorOperations.add(session.id)) { "Nested fixed actor operation" }
        var failure: Throwable? = null
        try {
            action()
        } catch (error: Throwable) {
            failure = error
            closingFixedSessions += session.id
        } finally {
            fixedActorOperations.remove(session.id)
            if (session.id in closingFixedSessions) {
                try {
                    destroyFixed(session)
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }

    private fun deleteFixedActors(session: InstanceSession) {
        val actors = fixedActors(session)
        var failure: Throwable? = null
        for (npc in actors) {
            if (
                spawnedNpcs[session.id]?.none { it === npc } != false &&
                    retiringFixedNpcs[npc] != session.id
            )
                continue
            try {
                // Delete events run after list removal, before UID retirement. Do not recursively
                // call del on an externally deleting actor while its native tail is still active.
                if (npc.uid.resolve(npcList) === npc) npcRepo.del(npc, Int.MAX_VALUE)
                if (!npc.isSlotAssigned) detachNpc(session.id, npc)
                else check(npc.uid.resolve(npcList) === npc) { "Incomplete native NPC deletion" }
            } catch (error: Throwable) {
                failedFixedTeardowns += session.id
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun destroyFixed(session: InstanceSession) {
        if (sessions[session.id] !== session || session.id in fixedActorOperations) return
        if (session.id in failedFixedTeardowns && session.isEnded) return
        val actors = fixedActors(session)
        if (
            session.id !in failedFixedTeardowns &&
                actors.any { it.isSlotAssigned && it.uid.resolve(npcList) !== it }
        )
            return // An external Delete callback is still running; reconcile after its native tail.
        closingFixedSessions += session.id
        fixedActorOperations += session.id
        var failure: Throwable? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        try {
            attempt { session.markEnded() }
            attempt {
                publish(InstanceEndedEvent(session.key, session.id, session.owner, session.spec))
            }
            attempt { deleteFixedActors(session) }
            for (occupant in session.fixedOccupants.values.toList()) {
                removeFixedOccupant(session, occupant, worldClock.cycle, publishEvent = false)
            }
            session.occupants.clear()
            deferredLogoutReclaims.remove(session.id)
            if (session.id !in failedFixedTeardowns) {
                spawnedNpcs.remove(session.id)
                sessions.remove(session.id, session)
                closingFixedSessions.remove(session.id)
                areaResolver.release(session.spec.area, session.placement)
            }
            // Native Delete listener failure can strand engine collision/zone state. Keep this
            // failed reservation closed and propagate the error; no existing retry can repair it.
        } finally {
            fixedActorOperations.remove(session.id)
        }
        failure?.let { throw it }
    }

    private fun fixedActors(session: InstanceSession): List<Npc> =
        (spawnedNpcs[session.id].orEmpty() +
                retiringFixedNpcs.entries.filter { it.value == session.id }.map { it.key })
            .distinct()

    private companion object {
        private const val SERVER_OWNER_ID: Long = 0L
    }
}
