package org.rsmod.content.minigames.cox

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RaidRewardClaimResult
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit as queueNpcHit
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocLayer
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class CoxRaidManager
@Inject
constructor(
    private val clock: MapClock,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val objs: ObjRepository,
    private val world: WorldRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val routeFactory: RouteFactory,
    private val rewards: CoxRewardService,
    private val ui: CoxUi,
    private val supplies: CoxSupplies,
    private val random: GameRandom,
    private val npcHitModifier: NpcHitModifier,
    private val instantHitProcessor: InstantPlayerHitProcessor,
) {
    private val log = InlineLogger()
    private val parties = linkedMapOf<PlayerUid, RaidParty>()
    private val membership = mutableMapOf<PlayerUid, RaidParty>()
    private val invitations = mutableMapOf<PlayerUid, RaidParty>()
    private val raids = mutableMapOf<InstanceId, CoxRaidSession>()
    private val participantRaids = mutableMapOf<PlayerUid, CoxRaidSession>()
    private val olmRoles = mutableMapOf<Npc, GreatOlmTarget>()
    private val puzzleProgress = mutableMapOf<InstanceId, Int>()
    private val pendingOlmRiseAnimations =
        mutableMapOf<InstanceId, MutableMap<GreatOlmTarget, Int>>()
    private val pendingOlmVisuals = mutableMapOf<InstanceId, MutableMap<GreatOlmTarget, Int>>()
    private val pendingOlmRetreatVisuals =
        mutableMapOf<InstanceId, MutableMap<GreatOlmTarget, Int>>()
    private val pendingOlmPhaseRespawns =
        mutableMapOf<InstanceId, PendingOlmPhaseRespawn>()
    private val pendingOlmHandRevives =
        mutableMapOf<InstanceId, MutableMap<GreatOlmTarget, Int>>()
    private val pendingMuttadileEmergence = mutableMapOf<InstanceId, PendingMuttadileEmergence>()
    private val pendingOlmCrystalBursts = mutableListOf<PendingOlmCrystalBurst>()
    private val pendingOlmFallingImpacts = mutableListOf<PendingOlmFallingImpact>()
    private val nextOlmFallingCrystalCycle = mutableMapOf<InstanceId, Int>()
    private val olmFallingCrystalWaveIndex = mutableMapOf<InstanceId, Int>()
    private val pendingRewardBeamSyncs = mutableListOf<PendingRewardBeamSync>()
    private val pendingRewardBeamTriggers = mutableListOf<PendingRewardBeamTrigger>()
    private val olmTestTargets = mutableMapOf<InstanceId, MutableList<Npc>>()

    fun validateAssets() {
        CoxLocs.all.forEach { id ->
            requireNotNull(ServerCacheManager.getObject(id)) { "Missing CoX loc $id" }
        }
        CoxNpcs.assets.forEach { id ->
            requireNotNull(ServerCacheManager.getNpc(id)) { "Missing CoX npc $id" }
        }
        requireNotNull(ServerCacheManager.getItem(CoxItems.KEYSTONE_CRYSTAL)) {
            "Missing CoX keystone item ${CoxItems.KEYSTONE_CRYSTAL}"
        }
        rewards.verifyDefinitions()
        supplies.verifyDefinitions { ServerCacheManager.getItem(it) != null }
        GreatOlmVisuals.sequenceIds.forEach { id ->
            requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) {
                "Missing Great Olm visual sequence $id"
            }
        }
        GreatOlmCrystalRules.spotanimIds.forEach { id ->
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
                "Missing Great Olm crystal spotanim $id"
            }
        }
        requireNotNull(ServerCacheManager.getNpc(OLM_TEST_TARGET_NPC)) {
            "Missing Olm test-target npc $OLM_TEST_TARGET_NPC"
        }
    }

    fun teleportLobby(player: Player): Boolean {
        val active = raidFor(player)
        if (active != null) {
            player.mes("Leave or complete your active raid before returning to the lobby.")
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, coxEntranceTeleportTile())
        player.mes("You arrive beside the Chambers of Xeric entrance.")
        return true
    }

    private fun coxEntranceTeleportTile(): CoordGrid {
        val entrance =
            (-4..4)
                .asSequence()
                .flatMap { zoneX ->
                    (-4..4).asSequence().map { zoneZ ->
                        org.rsmod.map.zone.ZoneKey.from(COX_LOBBY.translate(zoneX * 8, zoneZ * 8))
                    }
                }
                .distinct()
                .flatMap(locs::findAll)
                .filter { it.id == CoxLocs.ENTRANCE }
                .minByOrNull { it.coords.chebyshevDistance(COX_LOBBY) } ?: return COX_LOBBY
        return coxEntranceAdjacentCandidates(entrance.coords, COX_LOBBY).firstOrNull {
            !collision.isWalkBlocked(it)
        } ?: COX_LOBBY
    }

    fun openLobby(player: Player): Boolean {
        val raid = raidFor(player)
        if (raid != null) return rejoin(player, raid)
        val party = partyFor(player) ?: createParty(player)
        player.mes("Chambers party leader: ${label(party.leader)}. Members: ${party.members.size}.")
        player.mes("Use ::coxparty invite <name>, then ::coxparty start normal or challenge.")
        return true
    }

    fun createParty(player: Player): RaidParty {
        partyFor(player)?.let {
            return it
        }
        val party = RaidParty(player.uid)
        parties[player.uid] = party
        membership[player.uid] = party
        player.mes("You create a Chambers of Xeric party.")
        return party
    }

    fun invite(player: Player, targetName: String): Boolean {
        val party = partyFor(player) ?: createParty(player)
        if (party.leader != player.uid || party.activeSession != null) {
            player.mes("Only the party leader can invite players before the raid starts.")
            return false
        }
        if (party.members.size >= COX_MAX_PARTY_SIZE) {
            player.mes("Your raid party is full.")
            return false
        }
        val target = findPlayer(targetName)
        if (target == null || target.uid == player.uid) {
            player.mes("That player is not available.")
            return false
        }
        if (partyFor(target) != null) {
            player.mes("That player is already in a Chambers party.")
            return false
        }
        invitations[target.uid] = party
        target.mes("${player.displayName} invited you to a Chambers party. Use ::coxparty join.")
        player.mes("Invitation sent to ${target.displayName}.")
        return true
    }

    fun joinInvited(player: Player): Boolean {
        if (partyFor(player) != null) {
            player.mes("You are already in a Chambers party.")
            return false
        }
        val party = invitations.remove(player.uid)
        if (
            party == null || party.activeSession != null || party.members.size >= COX_MAX_PARTY_SIZE
        ) {
            player.mes("You do not have a valid Chambers party invitation.")
            return false
        }
        party.members += player.uid
        membership[player.uid] = party
        message(party, "${player.displayName} joins the Chambers party.")
        return true
    }

    fun kick(player: Player, targetName: String): Boolean {
        val party = partyFor(player) ?: return false
        if (party.leader != player.uid || party.activeSession != null) {
            player.mes("Only the leader can kick a member before the raid starts.")
            return false
        }
        val target = party.members.firstOrNull { label(it).equals(targetName, true) }
        if (target == null || target == party.leader) return false
        party.members.remove(target)
        membership.remove(target)
        target.resolve(players)?.mes("You were removed from the Chambers party.")
        message(party, "${label(target)} was removed from the Chambers party.")
        return true
    }

    fun leaveParty(player: Player): Boolean {
        val party = partyFor(player) ?: return false
        party.activeSession?.let {
            return leaveRaid(player, it, removeFromParty = true)
        }
        party.members.remove(player.uid)
        membership.remove(player.uid)
        if (party.leader == player.uid) transferOrDisband(party)
        player.mes("You leave the Chambers party.")
        return true
    }

    fun start(
        player: Player,
        difficulty: RaidDifficulty,
        seed: Long = clock.cycle.toLong(),
        olmOnly: Boolean = false,
    ): Boolean {
        val party = partyFor(player) ?: createParty(player)
        if (party.leader != player.uid) {
            player.mes("Only the party leader can start the raid.")
            return false
        }
        if (party.activeSession != null || raidFor(player) != null) {
            player.mes("Your party already has an active raid.")
            return false
        }
        val online = party.members.mapNotNull { it.resolve(players) }
        if (online.isEmpty()) return false
        if (online.any { instances.sessionForPlayer(it) != null }) {
            player.mes("Every party member must leave their current instance first.")
            return false
        }
        val layout = CoxLayoutGenerator.generate(seed, difficulty)
        check(CoxLayoutGenerator.isConnected(layout)) {
            "Generated disconnected CoX layout seed=$seed"
        }
        val spec =
            InstanceSettings(
                    maxPlayers = COX_MAX_PARTY_SIZE,
                    reclaimTicks = 2_000,
                    graceTicks = 10,
                    destroyWhenEmpty = false,
                    description =
                        "Chambers of Xeric donor " +
                            "${if (olmOnly) "Olm-only" else "fixed full"} " +
                            "${difficulty.name.lowercase(Locale.ROOT)} seed=$seed",
                    timeLimitTicks =
                        if (difficulty == RaidDifficulty.CHALLENGE) COX_CHALLENGE_LIMIT_TICKS
                        else null,
                )
                .withArea(CoxInstanceLayout.area(olmOnly), settingsRowId = 0)
        val created =
            try {
                instances.create(
                    player,
                    COX_INSTANCE_KEY,
                    spec,
                    InstanceAccess.Private,
                    clock.cycle,
                )
            } catch (error: Exception) {
                player.uuid?.let { ownerId ->
                    instances.sessionOwnedBy(COX_INSTANCE_KEY, ownerId)?.let(instances::terminate)
                }
                log.error(error) {
                    "[CoX] failed to create instance party=${party.id} difficulty=$difficulty " +
                        "olmOnly=$olmOnly seed=$seed"
                }
                player.mes(
                    "The Chambers raid could not be created safely. " +
                        "You remain outside; please try again."
                )
                return false
            }
        if (created !is InstanceManager.Result.Created) {
            player.mes(
                (created as? InstanceManager.Result.Failed)?.reason
                    ?: "The raid could not be created."
            )
            return false
        }
        val raid = CoxRaidSession(party, difficulty, layout, clock.cycle, olmOnly)
        raid.instance = created.session
        raid.state = RaidState.ACTIVE
        online.forEach { member -> raid.participants[member.uid] = RaidParticipant(member.uid) }
        raids[created.session.id] = raid
        party.activeSession = raid
        for ((index, member) in online.withIndex()) {
            val entry =
                if (index == 0) created.enter
                else
                    when (
                        val joined =
                            instances.join(member, created.session, clock.cycle, forceAccess = true)
                    ) {
                        is InstanceManager.Result.Joined -> joined.enter
                        else -> continue
                    }
            prepare(member)
            PathingEntityCommon.telejump(member, collision, entry)
            instances.finalizeEntry(member, created.session, clock.cycle)
            participantRaids[member.uid] = raid
            ui.open(member, raid, clock.cycle)
            member.mes(
                if (olmOnly) "You enter the donor Olm-only Chambers raid."
                else "You enter the donor fixed-layout Chambers raid."
            )
        }
        if (olmOnly) {
            raid.currentRoomIndex = raid.layout.placements.lastIndex
            raid.currentRoom.state = RaidRoomState.ACTIVE
            startOlm(raid)
        } else {
            raid.currentRoomIndex = 0
            raid.currentRoom.state = RaidRoomState.ACTIVE
            spawnDonorRaid(raid)
        }
        log.info {
            "[CoX] donor start party=${party.id} instance=${created.session.id} players=${online.size} " +
                "difficulty=$difficulty seed=$seed olmOnly=$olmOnly"
        }
        return true
    }

    fun rejoin(player: Player, raid: CoxRaidSession): Boolean {
        if (raid.state in setOf(RaidState.ABANDONED, RaidState.COMPLETE)) return false
        val result = instances.join(player, raid.instance, clock.cycle, forceAccess = true)
        if (result !is InstanceManager.Result.Joined) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason ?: "The raid is unavailable."
            )
            return false
        }
        val destination = donorStageEntry(raid)
        prepare(player)
        PathingEntityCommon.telejump(player, collision, destination)
        instances.finalizeEntry(player, raid.instance, clock.cycle)
        raid.participants.getOrPut(player.uid) { RaidParticipant(player.uid) }.connected = true
        participantRaids[player.uid] = raid
        ui.open(player, raid, clock.cycle)
        player.mes(
            "You rejoin your donor Chambers raid at ${raid.donorStage.name.lowercase().replace('_', ' ')}."
        )
        return true
    }

    fun advance(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        val room = raid.currentRoom
        if (room.state != RaidRoomState.COMPLETE) {
            player.mes("Complete ${room.room.displayName} before continuing.")
            return false
        }
        val next = raid.layout.room(room.index + 1) ?: return false
        activateRoom(raid, next.index)
        val destination = roomCoord(raid, next)
        forEachOnline(raid) { member ->
            prepare(member)
            PathingEntityCommon.telejump(member, collision, destination)
            member.mes(
                "You enter ${next.room.displayName} (${next.index + 1}/${raid.layout.placements.size})."
            )
        }
        return true
    }

    /** Handles the fixed donor raid's passages without moving the whole party at once. */
    fun usePassage(player: Player, loc: BoundLocInfo): Boolean {
        val raid = raidFor(player) ?: return false
        if (instances.sessionForId(raid.instance.id) !== raid.instance) {
            log.error {
                "[CoX] rejected passage in stale instance party=${raid.party.id} " +
                    "instance=${raid.instance.id} loc=${loc.id} coords=${loc.coords}"
            }
            abort(raid, "instance unavailable")
            return true
        }
        when (loc.id) {
            CoxLocs.LOWER_FLOOR_HOLE -> {
                raid.donorStage = CoxDonorStage.FLOOR_TWO
                PathingEntityCommon.telejump(
                    player,
                    collision,
                    donorCoord(raid, CoxDonorArea.FLOOR_TWO_SW, 15, 8),
                )
                player.mes("You descend to the lower floor of the Chambers.")
            }
            CoxLocs.OLM_FLOOR_HOLE -> {
                raid.donorStage = CoxDonorStage.OLM
                raid.currentRoomIndex = raid.layout.placements.lastIndex
                raid.currentRoom.state = RaidRoomState.ACTIVE
                if (raid.olm == null) startOlm(raid)
                PathingEntityCommon.telejump(
                    player,
                    collision,
                    donorCoord(raid, CoxDonorArea.OLM, 32, 25),
                )
                player.mes("You enter Great Olm's chamber.")
            }
            CoxLocs.MUTTADILE_BARRIER -> {
                if (donorAlive(raid, CoxDonorEncounter.MUTTADILES)) {
                    player.mes("The Muttadiles block the way.")
                    return false
                }
                crossBarrier(player, loc)
            }
            CoxLocs.SHIMMERING_BARRIER -> {
                if (!player.inv.any { it?.id == CoxItems.KEYSTONE_CRYSTAL }) {
                    player.mes("You'll need the keystone crystal to dispel this barrier.")
                    return false
                }
                if (!player.invDel(player.inv, CoxItems.KEYSTONE_CRYSTAL, 1).success) return false
                player.anim(832)
                locs.del(loc, Int.MAX_VALUE)
                raid.donorBlockers.remove(CoxDonorEncounter.TIGHTROPE)?.let {
                    raid.spawnedLocs.remove(it)
                }
                raid.participant(player.uid)?.let {
                    CoxPoints.award(raid, it, DeathlyNpcCombatRules.PUZZLE_DOOR_POINTS)
                }
                player.mes("You dispel the barrier. The path is now open.")
            }
            CoxLocs.TIGHTROPE -> return false // handled by the animated world interaction
            CoxLocs.VANGUARD_CRYSTAL -> {
                if (donorAlive(raid, CoxDonorEncounter.VANGUARDS)) {
                    player.mes("The Vanguard crystal remains sealed.")
                    return false
                }
                crossBarrier(player, loc)
            }
            CoxLocs.MYSTIC_BARRIER -> {
                if (donorAlive(raid, CoxDonorEncounter.SKELETAL_MYSTICS)) {
                    player.mes("The Skeletal mystics hold the barrier in place.")
                    return false
                }
                crossBarrier(player, loc)
            }
            CoxLocs.PASSAGE -> {
                if (isGuardianPassage(raid, loc) && donorAlive(raid, CoxDonorEncounter.GUARDIANS)) {
                    player.mes("The Guardians block the passage.")
                    return false
                }
                crossBarrier(player, loc)
            }
            else -> return false
        }
        return true
    }

    fun prepareOlmBarrierCrossing(player: Player, loc: BoundLocInfo): CoordGrid? {
        val raid = raidFor(player) ?: return null
        if (player.coords.z > loc.coords.z) {
            player.mes("You cannot pass the barrier from this side.")
            return null
        }
        if (raid.olm == null) startOlm(raid)
        val destination = olmBarrierDestination(player.coords)
        if (!collision.isZoneValid(destination)) {
            player.mes("The way into Great Olm's chamber is unavailable.")
            return null
        }
        return destination
    }

    fun activateOlmFor(player: Player) {
        val raid = raidFor(player) ?: return
        if (!isInsideOlmArena(raid, player)) return
        olmRoles.entries
            .firstOrNull {
                it.value == GreatOlmTarget.HEAD && it.key.isSlotAssigned && it.key.hitpoints > 0
            }
            ?.key
            ?.apPlayer2(player, interactions)
    }

    fun takeKeystone(player: Player, loc: BoundLocInfo): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.state in setOf(RaidState.ABANDONED, RaidState.COMPLETE)) return false
        if (player.inv.any { it?.id == CoxItems.KEYSTONE_CRYSTAL }) {
            player.mes("You already have the keystone crystal.")
            return false
        }
        if (!player.invAdd(player.inv, CoxItems.KEYSTONE_CRYSTAL, 1).success) {
            player.mes("Not enough space in your inventory.")
            return false
        }
        locs.del(loc, Int.MAX_VALUE)
        player.mes("You take the keystone crystal.")
        return true
    }

    /** Aggroes the guards and returns the donor's ten-tile tightrope destination. */
    fun prepareTightropeCrossing(player: Player, loc: BoundLocInfo): CoordGrid? {
        val raid = raidFor(player) ?: return null
        raid.donorEncounters
            .filter { (npc, encounter) ->
                encounter == CoxDonorEncounter.TIGHTROPE && npc.isSlotAssigned && npc.hitpoints > 0
            }
            .keys
            .forEach { npc -> npc.apPlayer2(player, interactions) }
        val vertical = loc.angle == LocAngle.West || loc.angle == LocAngle.East
        return if (vertical) {
            val step = if (loc.z < player.coords.z) -10 else 10
            player.coords.translate(0, step)
        } else {
            val step = if (loc.x < player.coords.x) -10 else 10
            player.coords.translate(step, 0)
        }
    }

    private fun spawnDonorRaid(raid: CoxRaidSession) {
        val scaledFor = raid.participants.size + 4

        val swimmer =
            spawnDonorNpc(
                raid,
                CoxNpcs.MUTTADILE_LARGE_UNDERWATER,
                CoxDonorArea.FLOOR_ONE_NW,
                20,
                19,
                CoxDonorEncounter.MUTTADILES,
            )
        swimmer.movementLocked = true
        raid.largeMuttadileUnlocked = false
        raid.largeMuttadile = swimmer
        pendingMuttadileEmergence.remove(raid.instance.id)
        spawnMuttadileMeatTree(raid)
        spawnDonorNpc(
            raid,
            CoxNpcs.MUTTADILE_SMALL,
            CoxDonorArea.FLOOR_ONE_NW,
            13,
            11,
            CoxDonorEncounter.MUTTADILES,
        )
        spawnDonorBlocker(
            raid,
            CoxDonorEncounter.MUTTADILES,
            CoxLocs.MUTTADILE_BARRIER,
            CoxDonorArea.FLOOR_ONE_NW,
            12,
            24,
            0,
        )

        val tightropeNpcCount = DeathlyNpcCombatRules.spawnsPerStyle(raid.participants.size)
        DeathlyNpcCombatRules.RANGER_SPAWNS
            .take(tightropeNpcCount)
            .zip(DeathlyNpcCombatRules.MAGE_SPAWNS.take(tightropeNpcCount))
            .forEach { (ranger, mage) ->
            spawnDonorNpc(
                raid,
                CoxNpcs.DEATHLY_RANGER,
                CoxDonorArea.FLOOR_ONE_NW,
                ranger.first,
                ranger.second,
                CoxDonorEncounter.TIGHTROPE,
            )
            spawnDonorNpc(
                raid,
                CoxNpcs.DEATHLY_MAGE,
                CoxDonorArea.FLOOR_ONE_NW,
                mage.first,
                mage.second,
                CoxDonorEncounter.TIGHTROPE,
            )
        }
        spawnDonorBlocker(
            raid,
            CoxDonorEncounter.TIGHTROPE,
            CoxLocs.SHIMMERING_BARRIER,
            CoxDonorArea.FLOOR_ONE_NW,
            27,
            47,
            2,
        )

        val scavCount = if (scaledFor < 5) 2 else if (scaledFor < 8) 3 else 4
        spawnDonorScavs(
            raid,
            CoxDonorEncounter.FLOOR_ONE_SCAVENGERS,
            CoxDonorArea.FLOOR_ONE_NW,
            scavCount,
            listOf(39 to 45, 41 to 51, 53 to 54, 56 to 47),
        )

        val vanguardSpawns = listOf(47 to 22, 53 to 19, 48 to 14)
        vanguardSpawns.forEachIndexed { index, tile ->
            val npc =
                spawnDonorNpc(
                    raid,
                    CoxNpcs.VANGUARD_DORMANT,
                    CoxDonorArea.FLOOR_ONE_NW,
                    tile.first,
                    tile.second,
                    CoxDonorEncounter.VANGUARDS,
                )
            raid.donorVanguardForms[npc] = CoxNpcs.VANGUARD_MELEE + index
            raid.donorVanguardWaypoints[npc] = index
        }
        spawnDonorBlocker(
            raid,
            CoxDonorEncounter.VANGUARDS,
            CoxLocs.VANGUARD_CRYSTAL,
            CoxDonorArea.FLOOR_ONE_NW,
            48,
            2,
            0,
        )

        spawnDonorScavs(
            raid,
            CoxDonorEncounter.FLOOR_TWO_SCAVENGERS,
            CoxDonorArea.FLOOR_TWO_SW,
            scavCount,
            listOf(13 to 34, 20 to 31, 8 to 45, 15 to 47),
        )
        spawnDonorScavs(
            raid,
            CoxDonorEncounter.FLOOR_TWO_SCAVENGERS,
            CoxDonorArea.FLOOR_TWO_SE,
            scavCount,
            listOf(9 to 10, 14 to 7, 16 to 4, 21 to 10),
        )

        val mysticCount = if (scaledFor <= 3) 3 else if (scaledFor < 7) 4 else 5
        val mysticSpawns = listOf(45 to 33, 42 to 44, 43 to 39, 46 to 44, 50 to 38)
        repeat(mysticCount) { index ->
            val tile = mysticSpawns[index]
            spawnDonorNpc(
                raid,
                CoxNpcs.SKELETAL_MYSTIC + (index % 3),
                CoxDonorArea.FLOOR_TWO_SW,
                tile.first,
                tile.second,
                CoxDonorEncounter.SKELETAL_MYSTICS,
            )
        }
        spawnDonorBlocker(
            raid,
            CoxDonorEncounter.SKELETAL_MYSTICS,
            CoxLocs.MYSTIC_BARRIER,
            CoxDonorArea.FLOOR_TWO_SW,
            57,
            41,
            0,
        )

        spawnDonorNpc(
                raid,
                CoxNpcs.GUARDIAN,
                CoxDonorArea.FLOOR_TWO_SE,
                21,
                38,
                CoxDonorEncounter.GUARDIANS,
            )
            .movementLocked = true
        spawnDonorNpc(
                raid,
                CoxNpcs.GUARDIAN_ALT,
                CoxDonorArea.FLOOR_TWO_SE,
                24,
                38,
                CoxDonorEncounter.GUARDIANS,
            )
            .movementLocked = true
        raid.spawnedObjs +=
            objs.add(
                GuardianRoomSupplies.IRON_PICKAXE,
                donorCoord(
                    raid,
                    CoxDonorArea.FLOOR_TWO_SE,
                    GuardianRoomSupplies.LOCAL_X,
                    GuardianRoomSupplies.LOCAL_Z,
                ),
                duration = Int.MAX_VALUE,
                reveal = 0,
            )
        message(
            raid.party,
            "The donor Chambers route is ready. Fight through both floors, then descend to Great Olm.",
        )
    }

    private fun spawnDonorScavs(
        raid: CoxRaidSession,
        encounter: CoxDonorEncounter,
        area: CoxDonorArea,
        count: Int,
        spawns: List<Pair<Int, Int>>,
    ) {
        repeat(count.coerceAtMost(spawns.size)) { index ->
            val tile = spawns[index]
            spawnDonorNpc(
                raid,
                CoxNpcs.SCAVENGER + (index % 2),
                area,
                tile.first,
                tile.second,
                encounter,
            )
        }
    }

    private fun spawnDonorNpc(
        raid: CoxRaidSession,
        id: Int,
        area: CoxDonorArea,
        x: Int,
        z: Int,
        encounter: CoxDonorEncounter,
    ): Npc {
        val npc = spawnNpc(raid, id, donorCoord(raid, area, x, z), raid.currentRoomIndex)
        raid.donorEncounters[npc] = encounter
        scaleDonorNpc(raid, npc)
        npc.apRangeOverride =
            when (id) {
                CoxNpcs.DEATHLY_RANGER,
                CoxNpcs.DEATHLY_MAGE,
                CoxNpcs.SKELETAL_MYSTIC,
                CoxNpcs.SKELETAL_MYSTIC_ALT,
                CoxNpcs.SKELETAL_MYSTIC_ALT_2 -> 10
                CoxNpcs.VANGUARD_RANGED,
                CoxNpcs.VANGUARD_MAGIC -> 8
                else -> 20
            }
        return npc
    }

    private fun scaleDonorNpc(raid: CoxRaidSession, npc: Npc) {
        val base = coxBaseStats(npc.visType.id)
        if (base == null) {
            npc.baseHitpointsLvl =
                CoxScaling.hitpoints(
                    npc.baseHitpointsLvl.coerceAtLeast(100),
                    raid.participants.size.coerceAtLeast(1),
                    raid.difficulty,
                )
            npc.hitpoints = npc.baseHitpointsLvl
            return
        }
        val partySize = raid.participants.size.coerceAtLeast(1)
        if (
            npc.visType.id == CoxNpcs.MUTTADILE_SMALL ||
                npc.visType.id == CoxNpcs.MUTTADILE_LARGE ||
                npc.visType.id == CoxNpcs.MUTTADILE_LARGE_UNDERWATER
        ) {
            npc.baseHitpointsLvl = MuttadileScaling.hitpoints(partySize, raid.difficulty)
            npc.hitpoints = npc.baseHitpointsLvl
            npc.baseAttackLvl =
                MuttadileScaling.combatStat(base.attack, partySize, raid.difficulty)
            npc.attackLvl = npc.baseAttackLvl
            npc.baseStrengthLvl =
                MuttadileScaling.combatStat(base.strength, partySize, raid.difficulty)
            npc.strengthLvl = npc.baseStrengthLvl
            npc.baseDefenceLvl =
                MuttadileScaling.defenceStat(base.defence, partySize, raid.difficulty)
            npc.defenceLvl = npc.baseDefenceLvl
            npc.baseMagicLvl =
                MuttadileScaling.combatStat(base.magic, partySize, raid.difficulty)
            npc.magicLvl = npc.baseMagicLvl
            npc.baseRangedLvl =
                MuttadileScaling.combatStat(base.ranged, partySize, raid.difficulty)
            npc.rangedLvl = npc.baseRangedLvl
            return
        }
        npc.baseHitpointsLvl = CoxScaling.hitpoints(base.hitpoints, partySize, raid.difficulty)
        npc.hitpoints = npc.baseHitpointsLvl
        npc.baseAttackLvl = CoxScaling.combatStat(base.attack, partySize, raid.difficulty)
        npc.attackLvl = npc.baseAttackLvl
        npc.baseStrengthLvl = CoxScaling.combatStat(base.strength, partySize, raid.difficulty)
        npc.strengthLvl = npc.baseStrengthLvl
        npc.baseDefenceLvl = CoxScaling.combatStat(base.defence, partySize, raid.difficulty)
        npc.defenceLvl = npc.baseDefenceLvl
        npc.baseMagicLvl = CoxScaling.combatStat(base.magic, partySize, raid.difficulty)
        npc.magicLvl = npc.baseMagicLvl
        npc.baseRangedLvl = CoxScaling.combatStat(base.ranged, partySize, raid.difficulty)
        npc.rangedLvl = npc.baseRangedLvl
    }

    private fun spawnDonorBlocker(
        raid: CoxRaidSession,
        encounter: CoxDonorEncounter,
        id: Int,
        area: CoxDonorArea,
        x: Int,
        z: Int,
        rotation: Int,
    ) {
        val type = requireNotNull(ServerCacheManager.getObject(id)) { "Missing CoX loc $id" }
        val loc =
            locs.add(
                donorCoord(raid, area, x, z),
                type,
                Int.MAX_VALUE,
                LocAngle[rotation],
                LocShape.CentrepieceStraight,
            )
        if (loc != null) {
            raid.donorBlockers[encounter] = loc
            raid.spawnedLocs += loc
        }
    }

    private fun spawnMuttadileMeatTree(raid: CoxRaidSession) {
        val tile =
            donorCoord(
                raid,
                CoxDonorArea.FLOOR_ONE_NW,
                MuttadileMeatTreeRules.LOCAL_X,
                MuttadileMeatTreeRules.LOCAL_Z,
            )
        val liveType =
            requireNotNull(ServerCacheManager.getObject(MuttadileMeatTreeRules.LIVE_LOC_ID))
        val liveLoc =
            locs.add(
                tile,
                liveType,
                Int.MAX_VALUE,
                LocAngle.South,
                LocShape.CentrepieceStraight,
            )
        raid.spawnedLocs += liveLoc
        raid.muttadileMeatTreeLoc = liveLoc
        raid.muttadileMeatTreeFelled = false

        val tree = spawnNpc(raid, MuttadileMeatTreeRules.NPC_ID, tile, raid.currentRoomIndex)
        val highestWoodcutting =
            raid.participants.keys
                .mapNotNull { it.resolve(players) }
                .maxOfOrNull { it.woodcuttingLvl }
                ?: 1
        tree.baseHitpointsLvl = MuttadileMeatTreeRules.health(highestWoodcutting)
        tree.hitpoints = tree.baseHitpointsLvl
        tree.movementLocked = true
        raid.muttadileMeatTree = tree
        raid.spawnedObjs +=
            objs.add(
                MuttadileMeatTreeRules.BRONZE_AXE,
                donorCoord(
                    raid,
                    CoxDonorArea.FLOOR_ONE_NW,
                    MuttadileMeatTreeRules.AXE_LOCAL_X,
                    MuttadileMeatTreeRules.AXE_LOCAL_Z,
                ),
                duration = Int.MAX_VALUE,
                reveal = 0,
            )
    }

    private fun donorAlive(raid: CoxRaidSession, encounter: CoxDonorEncounter): Boolean =
        raid.donorEncounters.any { (npc, group) ->
            group == encounter && npc.isSlotAssigned && npc.hitpoints > 0
        }

    private fun donorCoord(raid: CoxRaidSession, area: CoxDonorArea, x: Int, z: Int): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, CoxInstanceLayout.local(area, x, z))) {
            "Missing donor CoX coordinate $area ($x,$z)"
        }

    private fun donorStageEntry(raid: CoxRaidSession): CoordGrid =
        when (raid.donorStage) {
            CoxDonorStage.FLOOR_ONE -> donorCoord(raid, CoxDonorArea.FLOOR_ONE_SW, 3, 4)
            CoxDonorStage.FLOOR_TWO -> donorCoord(raid, CoxDonorArea.FLOOR_TWO_SW, 15, 8)
            CoxDonorStage.OLM -> donorCoord(raid, CoxDonorArea.OLM, 32, 25)
            CoxDonorStage.REWARDS -> donorCoord(raid, CoxDonorArea.OLM, 33, 53)
        }

    /** The donor map reuses loc 29789; only this passage is guarded by the Guardians. */
    private fun isGuardianPassage(raid: CoxRaidSession, loc: BoundLocInfo): Boolean =
        loc.coords == donorCoord(raid, CoxDonorArea.FLOOR_TWO_SE, 22, 36)

    private fun crossBarrier(player: Player, loc: BoundLocInfo) {
        val minX = loc.x
        val maxX = loc.x + loc.adjustedWidth - 1
        val minZ = loc.z
        val maxZ = loc.z + loc.adjustedLength - 1
        val distances = listOf(2, 3, 1, 4, 5, 6)
        val offsets = listOf(0, -1, 1, -2, 2)
        val horizontal =
            kotlin.math.abs(player.coords.x - loc.adjustedCentre.x) >=
                kotlin.math.abs(player.coords.z - loc.adjustedCentre.z)
        val candidates = buildList {
            for (distance in distances) for (offset in offsets) {
                if (horizontal) {
                    val x = if (player.coords.x <= minX) maxX + distance else minX - distance
                    add(CoordGrid(x, player.coords.z + offset, player.coords.level))
                } else {
                    val z = if (player.coords.z <= minZ) maxZ + distance else minZ - distance
                    add(CoordGrid(player.coords.x + offset, z, player.coords.level))
                }
            }
        }
        val destination =
            candidates.firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
        if (destination == null) {
            player.mes("The passage is blocked from this side.")
            return
        }
        PathingEntityCommon.telejump(player, collision, destination)
    }

    fun puzzleAction(player: Player, locId: Int): Boolean {
        val raid = raidFor(player) ?: return false
        val room = raid.currentRoom
        if (room.state != RaidRoomState.ACTIVE) return false
        if (room.room.category == CoxRoomCategory.RESOURCE) {
            if (!supplies.interact(player, raid, locId)) return false
            CoxPoints.award(raid, raid.participant(player.uid) ?: return false, 25)
            ui.sync(player, raid, clock.cycle)
            player.mes("You gain 25 raid points.")
            return true
        }
        val allowed =
            when (room.room) {
                CoxRoom.ICE_DEMON -> locId == CoxLocs.BRAZIER
                CoxRoom.JEWELLED_CRABS -> locId == CoxLocs.GLOWING_CRYSTAL
                CoxRoom.TIGHTROPE -> locId == CoxLocs.TIGHTROPE
                CoxRoom.THIEVING -> locId == CoxLocs.THIEVING_CHEST
                else -> false
            }
        if (!allowed) return false
        val progress = (puzzleProgress[raid.instance.id] ?: 0) + 1
        puzzleProgress[raid.instance.id] = progress
        CoxPoints.award(raid, raid.participant(player.uid) ?: return false, 100)
        if (progress >= COX_PUZZLE_ACTIONS) {
            if (room.room == CoxRoom.ICE_DEMON) {
                puzzleProgress[raid.instance.id] = 0
                spawnRoomNpcs(raid, room)
                message(
                    raid.party,
                    "The ice shield melts. Defeat the Ice Demon to open the way forward.",
                )
            } else {
                completeRoom(raid, player)
            }
        } else {
            player.mes("${room.room.displayName}: $progress/$COX_PUZZLE_ACTIONS progress.")
        }
        return true
    }

    fun claim(player: Player, chest: CoordGrid): CoxRewardRoll? {
        val raid = raidFor(player) ?: return null
        if (raid.state != RaidState.REWARDS) {
            player.mes("Great Olm must be defeated before the chest can be opened.")
            return null
        }
        val beam = rewardBeam(raid, player, chest)
        val claimed = rewards.claim(player, raid) ?: return null
        if (beam != null) world.locDelPrivate(player, beam)
        if (raid.participants.values.all(RaidParticipant::rewardClaimed)) {
            raid.state = RaidState.COMPLETE
        }
        return claimed
    }

    fun leaveFromLogoutTab(player: Player, expectedInstanceId: InstanceId): RaidLeaveResult {
        val raid = raidFor(player) ?: return RaidLeaveResult.Unhandled
        if (raid.instance.id != expectedInstanceId) return RaidLeaveResult.Unhandled
        if (raid.state == RaidState.REWARDS) {
            when (rewards.claimForLeave(player, raid)) {
                RaidRewardClaimResult.Claimed -> {
                    if (raid.participants.values.all(RaidParticipant::rewardClaimed)) {
                        raid.state = RaidState.COMPLETE
                    }
                }
                RaidRewardClaimResult.AlreadyClaimed -> Unit
                RaidRewardClaimResult.InventoryFull -> return RaidLeaveResult.InventoryFull
                RaidRewardClaimResult.Busy -> return RaidLeaveResult.Busy
                RaidRewardClaimResult.NothingToClaim -> {
                    player.mes("Your Chambers reward is not ready. You have not been moved.")
                    return RaidLeaveResult.RewardUnavailable
                }
            }
        }
        return if (leaveRaid(player, raid, removeFromParty = true)) {
            RaidLeaveResult.Left
        } else {
            RaidLeaveResult.Failed
        }
    }

    fun validateAttack(player: Player, npc: Npc): NpcAttackValidateResult {
        val raid = raidFor(npc) ?: return NpcAttackValidateResult.Pass
        if (raidFor(player) !== raid)
            return NpcAttackValidateResult.Deny("That creature belongs to another raid party.")
        val target = olmRoles[npc]
        if (npc.id == CoxNpcs.GUARDIAN || npc.id == CoxNpcs.GUARDIAN_ALT) {
            val weapon = getOrNull(player.righthand)
            if (weapon?.isContentType("content.mining_pickaxe") != true) {
                return NpcAttackValidateResult.Deny(
                    "The Guardians can only be harmed with an equipped pickaxe."
                )
            }
        }
        if (npc === raid.largeMuttadile && !raid.largeMuttadileUnlocked) {
            return NpcAttackValidateResult.Deny(
                "The Muttadile is underwater; your attacks cannot reach it."
            )
        }

        if (target != null && pendingOlmVisuals[raid.instance.id]?.containsKey(target) == true) {
            return NpcAttackValidateResult.Deny(
                "That part of Great Olm is still rising from the chamber."
            )
        }
        if (target != null && raid.olm?.canDamage(target) != true) {
            return NpcAttackValidateResult.Deny("That part of Great Olm cannot be harmed yet.")
        }
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }

    fun handleNpcKill(hero: Player?, npc: Npc) {
        val raid = raidFor(npc) ?: return
        raid.spawnedNpcs.remove(npc)
        val roomIndex = raid.npcRooms.remove(npc)
        val donorEncounter = raid.donorEncounters.remove(npc)
        raid.muttadileEating.remove(npc)
        raid.muttadileEatAttempted.remove(npc)
        val olmTarget = olmRoles.remove(npc)
        if (olmTarget != null) {
            handleOlmPartDeath(raid, olmTarget)
            return
        }
        if (donorEncounter != null) {
            raid.donorVanguardForms.remove(npc)
            raid.donorVanguardWaypoints.remove(npc)
            if (npc.id == CoxNpcs.MUTTADILE_SMALL) {
                val large = raid.largeMuttadile
                if (large != null && large.isSlotAssigned) {
                    large.transmog(
                        requireNotNull(ServerCacheManager.getNpc(CoxNpcs.MUTTADILE_LARGE)),
                        Int.MAX_VALUE,
                    )
                    val start = large.coords
                    val destination = MuttadileEmergence.destination(start)
                    raid.largeMuttadileUnlocked = false
                    large.movementLocked = true
                    large.anim(MuttadileEmergence.SEQUENCE, priority = 254)
                    pendingMuttadileEmergence[raid.instance.id] =
                        PendingMuttadileEmergence(
                            large,
                            destination,
                            clock.cycle + MuttadileEmergence.DURATION_TICKS,
                        )
                    message(raid.party, "The large Muttadile surges out of the water!")
                }
                return
            }
            if (!donorAlive(raid, donorEncounter)) {
                raid.completedDonorEncounters += donorEncounter
                if (donorEncounter != CoxDonorEncounter.TIGHTROPE) {
                    raid.donorBlockers.remove(donorEncounter)?.let { blocker ->
                        if (blocker in raid.spawnedLocs) {
                            locs.del(blocker, Int.MAX_VALUE)
                            raid.spawnedLocs.remove(blocker)
                        }
                    }
                }
                message(
                    raid.party,
                    "${donorEncounter.name.lowercase().replace('_', ' ')} complete. The way forward opens.",
                )
            }
            return
        }
        if (roomIndex == raid.currentRoomIndex && raid.npcRooms.values.none { it == roomIndex }) {
            completeRoom(raid, hero)
        }
    }

    fun awardCombatPoints(player: Player, npc: Npc, damage: Int) {
        val raid = raidFor(npc) ?: return
        if (raidFor(player) !== raid) return
        val participant = raid.participant(player.uid) ?: return
        val olmTarget = olmRoles[npc]
        val regeneratedHandPhase =
            raid.olm?.phase == GreatOlmPhase.PHASE_THREE &&
                olmTarget in setOf(GreatOlmTarget.LEFT_HAND, GreatOlmTarget.RIGHT_HAND)
        if (regeneratedHandPhase) return
        var pointableDamage = damage
        if (raid.donorEncounters[npc] == CoxDonorEncounter.MUTTADILES) {
            val cap =
                MuttadileScaling.combatDamageCap(
                    raid.participants.size.coerceAtLeast(1),
                    raid.difficulty,
                )
            pointableDamage =
                MuttadileScaling.pointableDamage(
                    raid.muttadileCombatDamageCredited,
                    damage,
                    cap,
                )
            raid.muttadileCombatDamageCredited += pointableDamage
        }
        CoxPoints.award(
            raid,
            participant,
            CoxPoints.forCombatDamage(pointableDamage, raid.olmOnly),
        )
    }

    fun isActiveMeatTree(player: Player, tree: Npc): Boolean {
        val raid = raidFor(player) ?: return false
        return raid.muttadileMeatTree === tree &&
            !raid.muttadileMeatTreeFelled &&
            tree.isSlotAssigned &&
            tree.hitpoints > 0
    }

    /** Returns null when chopping must stop, zero on a miss, or the actual tree damage. */
    fun chopMeatTree(player: Player, tree: Npc): Int? {
        if (!isActiveMeatTree(player, tree) || player.distanceTo(tree) > 1) return null
        val raid = raidFor(player) ?: return null
        val level = player.woodcuttingLvl.coerceIn(1, 99)
        if (random.of(1..100) > MuttadileMeatTreeRules.accuracyPercent(level)) return 0

        val before = tree.hitpoints
        val rolled = random.of(MuttadileMeatTreeRules.damageRange(level))
        val damage = rolled.coerceAtMost(before)
        val current = (before - damage).coerceAtLeast(0)
        tree.hitpoints = current.coerceAtLeast(1)
        showMeatTreeDamage(tree, before, current)
        val participant = raid.participant(player.uid)
        if (participant != null) {
            val available =
                (tree.baseHitpointsLvl - raid.muttadileTreePointsCredited).coerceAtLeast(0)
            val awarded = damage.coerceAtMost(available)
            raid.muttadileTreePointsCredited += awarded
            CoxPoints.award(raid, participant, awarded)
        }
        if (current == 0) fellMeatTree(raid, tree)
        return damage
    }

    private fun showMeatTreeDamage(tree: Npc, previousHealth: Int, currentHealth: Int) {
        val damage = (previousHealth - currentHealth).coerceAtLeast(0)
        val hitmarkGroup = hitmark_groups.regular_damage
        val lit = hitmarkGroup.lit.asRSCM(RSCMType.HITMARK)
        val tint = hitmarkGroup.tint?.asRSCM(RSCMType.HITMARK) ?: lit
        tree.showHitmark(Hitmark.fromNoSource(lit, lit, tint, damage, 0))
        val headbar = tree.visHeadbar(params.headbar)
        val maximum = tree.baseHitpointsLvl.coerceAtLeast(1)
        val fill = (currentHealth * headbar.segments / maximum).coerceIn(0, headbar.segments)
        tree.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun fellMeatTree(raid: CoxRaidSession, tree: Npc) {
        raid.muttadileMeatTreeFelled = true
        raid.muttadileMeatTree = null
        raid.spawnedNpcs.remove(tree)
        raid.npcRooms.remove(tree)
        if (tree.isSlotAssigned) npcs.del(tree, Int.MAX_VALUE)
        val liveLoc = raid.muttadileMeatTreeLoc
        if (liveLoc != null) {
            val deadType =
                requireNotNull(ServerCacheManager.getObject(MuttadileMeatTreeRules.DEAD_LOC_ID))
            val deadLoc =
                locs.add(
                    liveLoc.coords,
                    deadType,
                    Int.MAX_VALUE,
                    liveLoc.angle,
                    liveLoc.shape,
                )
            raid.spawnedLocs.remove(liveLoc)
            raid.spawnedLocs += deadLoc
            raid.muttadileMeatTreeLoc = deadLoc
        }
        raid.muttadileEating.keys.toList().forEach { finishMuttadileEating(raid, it) }
        message(raid.party, "The meat tree has been chopped down.")
    }

    private fun processMuttadileEating(raid: CoxRaidSession) {
        val tree = raid.muttadileMeatTree
        val active =
            raid.donorEncounters
                .filterValues { it == CoxDonorEncounter.MUTTADILES }
                .keys
                .filter {
                    it.isSlotAssigned &&
                        isAliveOlmTarget(it) &&
                        it.visType.id in setOf(CoxNpcs.MUTTADILE_SMALL, CoxNpcs.MUTTADILE_LARGE)
                }
        if (tree != null && tree.isSlotAssigned && !raid.muttadileMeatTreeFelled) {
            active
                .filter {
                    it !in raid.muttadileEatAttempted &&
                        MuttadileMeatTreeRules.shouldEat(it.hitpoints, it.baseHitpointsLvl)
                }
                .forEach { muttadile ->
                    raid.muttadileEatAttempted += muttadile
                    raid.muttadileEating[muttadile] =
                        MuttadileEatingAttempt(clock.cycle + MuttadileMeatTreeRules.GIVE_UP_TICKS)
                    muttadile.clearInteractionRoute()
                    message(raid.party, "The Muttadile moves towards the meat tree!")
                }
        }

        for ((muttadile, attempt) in raid.muttadileEating.toMap()) {
            if (!muttadile.isSlotAssigned || muttadile.hitpoints <= 0) {
                raid.muttadileEating.remove(muttadile)
                continue
            }
            val resumeAt = attempt.resumeCombatAt
            if (resumeAt != null) {
                if (clock.cycle >= resumeAt) finishMuttadileEating(raid, muttadile)
                continue
            }
            if (tree == null || !tree.isSlotAssigned || raid.muttadileMeatTreeFelled) {
                finishMuttadileEating(raid, muttadile)
                continue
            }
            if (muttadile.distanceTo(tree) <= 1) {
                muttadile.clearInteractionRoute()
                muttadile.faceNpc(tree)
                muttadile.anim(MuttadileMeatTreeRules.EAT_SEQUENCE, priority = 254)
                muttadile.heal(
                    MuttadileMeatTreeRules.healAmount(muttadile.baseHitpointsLvl),
                    showHitsplat = true,
                )
                attempt.resumeCombatAt =
                    clock.cycle + MuttadileMeatTreeRules.EAT_ANIMATION_TICKS
                continue
            }
            if (clock.cycle >= attempt.giveUpAt) {
                finishMuttadileEating(raid, muttadile)
                message(raid.party, "The Muttadile gives up trying to reach the meat tree.")
                continue
            }
            if (!muttadile.isBound && muttadile.routeDestination.isEmpty()) {
                // Route to the nearest legal side of the 2x2 tree. A fixed side makes the adult
                // walk around the whole tree when it approaches from another direction.
                val feedingTile =
                    MuttadileMeatTreeRules.feedingTile(
                        muttadile.coords,
                        muttadile.size,
                        tree.coords,
                    )
                muttadile.walkTo(routeFactory, feedingTile, passThroughEntities = true)
            }
        }
    }

    private fun finishMuttadileEating(raid: CoxRaidSession, muttadile: Npc) {
        raid.muttadileEating.remove(muttadile)
        muttadile.clearInteractionRoute()
        if (muttadile.isSlotAssigned && muttadile.hitpoints > 0) {
            nearestParticipant(raid, muttadile.coords)?.let {
                muttadile.apPlayer2(it, interactions)
            }
        }
    }

    fun markDeath(player: Player) {
        val raid = raidFor(player) ?: return
        val participant = raid.participant(player.uid) ?: return
        val deduction = CoxPoints.deductDeath(raid, participant)
        player.mes(
            "You lose ${deduction.personalLost} personal and ${deduction.teamLost} team raid points."
        )
    }

    fun respawnAfterDeath(player: Player) {
        val raid = partyFor(player)?.activeSession ?: return
        participantRaids[player.uid] = raid
        raid.participant(player.uid)?.connected = true
        PathingEntityCommon.telejump(player, collision, donorStageEntry(raid))
        ui.open(player, raid, clock.cycle)
    }

    fun logout(player: Player) {
        val raid = raidFor(player)
        if (raid == null) {
            participantRaids.remove(player.uid)
            return
        }
        instances.leaveForLogout(player, clock.cycle)
        leaveRaid(player, raid, removeFromParty = true, teleport = false)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        val raid = raidFor(player) ?: return
        if (raid.instance.id != instanceId) return
        participantRaids.remove(player.uid)
        raid.participant(player.uid)?.connected = false
        prepare(player)
        supplies.cleanupInventory(player)
        ui.close(player)
        raid.party.members.remove(player.uid)
        membership.remove(player.uid)
        player.mes("Your Chambers raid ended because you teleported out of its instance.")
        when {
            raid.party.members.isEmpty() -> abort(raid, "party left the instance")
            raid.party.leader == player.uid -> transferOrDisband(raid.party)
        }
    }

    fun tick() {
        processRewardBeamSyncs()
        processRewardBeamTriggers()
        processOlmVisualTransitions()
        processOlmPhaseRespawns()
        processOlmHandRevives()
        processOlmCrystalBursts()
        processOlmFallingImpacts()
        processOlmFallingCrystals()
        for (raid in raids.values.toList()) {
            if (raid.state in setOf(RaidState.ABANDONED, RaidState.COMPLETE)) continue

            processMuttadileEmergence(raid)
            processMuttadileEating(raid)

            if (clock.cycle % 5 == 0) forEachOnline(raid) { ui.sync(it, raid, clock.cycle) }
            if (clock.cycle % 5 == 0) {
                val online = raid.participants.keys.mapNotNull { it.resolve(players) }
                raid.donorEncounters.keys
                    .filter {
                        it.isSlotAssigned &&
                            isAliveOlmTarget(it) &&
                            (it !== raid.largeMuttadile ||
                                raid.largeMuttadileUnlocked ||
                                it.id == CoxNpcs.MUTTADILE_LARGE_UNDERWATER)
                    }
                    .forEach { npc ->
                        if (combatProfile(npc)?.style != CoxCombatStyle.PASSIVE) {
                            online
                                .minByOrNull { it.coords.chebyshevDistance(npc.coords) }
                                ?.takeIf { it.coords.chebyshevDistance(npc.coords) <= 12 }
                                ?.let { npc.apPlayer2(it, interactions) }
                        }
                    }
                if (!raid.vanguardsActivated) {
                    val dormant = raid.donorVanguardForms.keys.filter { it.isSlotAssigned }
                    if (
                        dormant.any { npc ->
                            online.any { it.coords.chebyshevDistance(npc.coords) <= 8 }
                        }
                    ) {
                        raid.vanguardsActivated = true
                        dormant.forEach { npc ->
                            npc.anim(7428)
                            npc.transmog(
                                requireNotNull(
                                    ServerCacheManager.getNpc(raid.donorVanguardForms.getValue(npc))
                                ),
                                Int.MAX_VALUE,
                            )
                            nearestParticipant(raid, npc.coords)?.let {
                                npc.apPlayer2(it, interactions)
                            }
                        }
                        raid.nextVanguardShuffle = clock.cycle + random.of(50..70)
                        message(raid.party, "The Vanguards awaken!")
                    }
                }
            }
        }
    }

    private fun processMuttadileEmergence(raid: CoxRaidSession) {
        val pending = pendingMuttadileEmergence[raid.instance.id] ?: return
        val large = pending.npc
        if (!large.isSlotAssigned || large.hitpoints <= 0 || large !== raid.largeMuttadile) {
            pendingMuttadileEmergence.remove(raid.instance.id)
            return
        }
        if (large.coords != pending.destination) {
            val next = MuttadileEmergence.nextStep(large.coords, pending.destination)
            PathingEntityCommon.forceWalkStep(large, collision, next)
            return
        }
        if (clock.cycle < pending.unlockCycle) return
        pendingMuttadileEmergence.remove(raid.instance.id)
        scaleDonorNpc(raid, large)
        large.movementLocked = false
        raid.largeMuttadileUnlocked = true
        nearestParticipant(raid, large.coords)?.let { large.apPlayer2(it, interactions) }
        message(raid.party, "The large Muttadile charges onto land!")
    }

    fun raidFor(player: Player): CoxRaidSession? =
        participantRaids[player.uid]
            ?: partyFor(player)?.activeSession?.takeIf { player.uid in it.participants }

    fun raidFor(npc: Npc): CoxRaidSession? =
        instances.instanceForNpc(npc)?.let(raids::get)?.takeIf { it.owns(npc) }

    fun partyFor(player: Player): RaidParty? = membership[player.uid]

    fun status(player: Player): String {
        val party = partyFor(player) ?: return "CoX: no party."
        val raid =
            party.activeSession
                ?: return "CoX party=${party.id} leader=${label(party.leader)} members=${party.members.size} waiting."
        return "CoX party=${party.id} instance=${raid.instance.id} ${raid.difficulty} state=${raid.state} " +
            "room=${raid.currentRoomIndex + 1}/${raid.layout.placements.size}:${raid.currentRoom.room.key} " +
            "points=${raid.teamPoints} seed=${raid.layout.seed}"
    }

    fun forceRoom(player: Player, room: CoxRoom): Boolean {
        var raid = raidFor(player)
        if (raid == null) {
            if (!start(player, RaidDifficulty.NORMAL, seed = room.ordinal.toLong() + 1))
                return false
            raid = raidFor(player) ?: return false
        }
        val placement = raid.layout.placements.firstOrNull { it.room == room }
        if (placement == null) {
            player.mes("That room is not in this layout; use ::coxdev start challenge first.")
            return false
        }
        clearRaidNpcs(raid)
        activateRoom(raid, placement.index)
        PathingEntityCommon.telejump(player, collision, roomCoord(raid, placement))
        return true
    }

    fun forceOlmPhase(player: Player, phase: Int): Boolean {
        val raid = raidFor(player) ?: return false
        val olmPlacement = raid.layout.placements.last()
        clearRaidNpcs(raid)
        activateRoom(raid, olmPlacement.index)
        while ((raid.olm?.phaseNumber ?: 0) < phase.coerceIn(1, 4)) {
            val state = raid.olm ?: break
            if (state.phase == GreatOlmPhase.FINAL_HEAD) break
            state.defeat(GreatOlmTarget.LEFT_HAND)
            state.defeat(GreatOlmTarget.RIGHT_HAND)
        }
        spawnOlmPhase(raid)
        PathingEntityCommon.telejump(player, collision, roomCoord(raid, olmPlacement))
        return true
    }

    fun setPoints(player: Player, amount: Int): Boolean {
        val raid = raidFor(player) ?: return false
        val participant = raid.participant(player.uid) ?: return false
        raid.teamPoints += amount.coerceAtLeast(0) - participant.personalPoints
        participant.personalPoints = amount.coerceAtLeast(0)
        ui.sync(player, raid, clock.cycle)
        player.mes(
            "CoX personal points=${participant.personalPoints}, team points=${raid.teamPoints}."
        )
        return true
    }

    fun forceComplete(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        completeRaid(raid)
        return true
    }

    fun forceRewards(player: Player, forceUnique: Boolean = false): Boolean {
        raidFor(player)?.let { abort(it, "administrator reward test reset") }
        if (!start(player, RaidDifficulty.NORMAL, olmOnly = true)) return false
        val raid = raidFor(player) ?: return false
        clearRaidNpcs(raid)
        val participant = raid.participant(player.uid) ?: return false
        participant.connected = true
        participant.rewardClaimed = false
        participant.pendingReward = null
        completeRaid(raid, forceUniqueFor = player.uid.takeIf { forceUnique })
        prepare(player)
        PathingEntityCommon.telejump(
            player,
            collision,
            donorCoord(raid, CoxDonorArea.OLM, 33, 53),
        )
        ui.open(player, raid, clock.cycle)
        player.mes(
            if (forceUnique)
                "CoX purple chest test ready. Search the ancient chest."
            else
                "CoX chest test ready. Search the ancient chest."
        )
        return true
    }

    fun reset(player: Player): Boolean {
        val raid = raidFor(player)
        if (raid != null) abort(raid, "administrator reset")
        return true
    }

    fun combatProfile(npc: Npc): CoxCombatProfile? =
        raidFor(npc)?.let { raid ->
            if (npc in raid.donorVanguardForms && !raid.vanguardsActivated) {
                combatProfile(CoxNpcs.VANGUARD_DORMANT)
            } else if (
                npc in raid.muttadileEating
            ) {
                CoxCombatProfile(
                    CoxCombatStyle.PASSIVE,
                    0,
                    MuttadileCombatRules.ATTACK_RATE,
                )
            } else if (
                npc === raid.largeMuttadile &&
                    !raid.largeMuttadileUnlocked &&
                    npc.id != CoxNpcs.MUTTADILE_LARGE_UNDERWATER
            ) {
                CoxCombatProfile(
                    CoxCombatStyle.PASSIVE,
                    0,
                    MuttadileCombatRules.ATTACK_RATE,
                )
            } else {
                val combatId = raid.donorVanguardForms[npc] ?: npc.visType.id
                val base = combatProfile(combatId)
                if (DeathlyNpcCombatRules.appliesTo(combatId)) {
                    base.copy(maxHit = DeathlyNpcCombatRules.maxHit(raid.participants.size))
                } else {
                    base
                }
            }
        }

    fun attackTargetValid(player: Player, npc: Npc): Boolean = raidFor(player)?.owns(npc) == true

    private fun activateRoom(raid: CoxRaidSession, index: Int) {
        val room = checkNotNull(raid.layout.room(index))
        raid.currentRoomIndex = index
        room.state = RaidRoomState.ACTIVE
        puzzleProgress[raid.instance.id] = 0
        when (room.room.category) {
            CoxRoomCategory.START -> completeRoom(raid, null)
            CoxRoomCategory.RESOURCE -> {
                forEachOnline(raid) {
                    it.mes("Gather and prepare raid supplies here before continuing.")
                }
                room.state = RaidRoomState.COMPLETE
            }
            CoxRoomCategory.PUZZLE ->
                forEachOnline(raid) {
                    it.mes("Solve ${room.room.displayName} to open the way forward.")
                }
            CoxRoomCategory.COMBAT,
            CoxRoomCategory.SCAVENGER -> spawnRoomNpcs(raid, room)
            CoxRoomCategory.OLM -> startOlm(raid)
        }
    }

    private fun spawnRoomNpcs(raid: CoxRaidSession, room: CoxRoomPlacement) {
        val ids = CoxNpcs.forRoom(room.room)
        if (ids.isEmpty()) {
            room.state = RaidRoomState.COMPLETE
            return
        }
        val center = roomCoord(raid, room)
        ids.forEachIndexed { index, id ->
            val tile =
                CoordGrid(center.x + (index % 3) * 3 - 3, center.z + (index / 3) * 3, center.level)
            val npc = spawnNpc(raid, id, tile, room.index)
            val hp =
                CoxScaling.hitpoints(
                    npc.baseHitpointsLvl.coerceAtLeast(100),
                    raid.participants.size,
                    raid.difficulty,
                )
            npc.baseHitpointsLvl = hp
            npc.hitpoints = hp
            npc.baseDefenceLvl =
                CoxScaling.combatStat(
                    npc.baseDefenceLvl.coerceAtLeast(100),
                    raid.participants.size,
                    raid.difficulty,
                )
            npc.defenceLvl = npc.baseDefenceLvl
            nearestParticipant(raid, npc.coords)?.let { npc.apPlayer2(it, interactions) }
        }
    }

    private fun startOlm(raid: CoxRaidSession) {
        raid.state = RaidState.OLM
        raid.donorStage = CoxDonorStage.OLM
        pendingOlmPhaseRespawns.remove(raid.instance.id)
        pendingOlmRiseAnimations.remove(raid.instance.id)
        pendingOlmVisuals.remove(raid.instance.id)
        pendingOlmHandRevives.remove(raid.instance.id)
        pendingOlmRetreatVisuals.remove(raid.instance.id)
        cleanupOlmCrystalHazards(raid)
        raid.olm =
            GreatOlmState(raid.participants.size, raid.difficulty).also(GreatOlmState::activate)
        spawnOlmPhase(raid)
        message(raid.party, "Great Olm rises. Destroy both hands before attacking the head.")
    }

    private fun spawnOlmPhase(raid: CoxRaidSession) {
        clearRaidNpcs(raid)
        val center = donorCoord(raid, CoxDonorArea.OLM, 38, 42)
        val state = raid.olm ?: return
        if (state.phase != GreatOlmPhase.FINAL_HEAD) {
            spawnOlmHand(raid, GreatOlmTarget.LEFT_HAND)
            spawnOlmHand(raid, GreatOlmTarget.RIGHT_HAND)
        }
        spawnOlmPart(raid, CoxNpcs.OLM_HEAD, center, GreatOlmTarget.HEAD)
    }

    private fun spawnOlmHand(raid: CoxRaidSession, target: GreatOlmTarget) {
        val (id, tile) =
            when (target) {
                GreatOlmTarget.LEFT_HAND ->
                    CoxNpcs.OLM_LEFT_HAND to donorCoord(raid, CoxDonorArea.OLM, 38, 37)
                GreatOlmTarget.RIGHT_HAND ->
                    CoxNpcs.OLM_RIGHT_HAND to donorCoord(raid, CoxDonorArea.OLM, 38, 47)
                GreatOlmTarget.HEAD -> error("Great Olm's head is not a hand")
            }
        spawnOlmPart(raid, id, tile, target)
    }

    private fun spawnOlmPart(raid: CoxRaidSession, id: Int, tile: CoordGrid, role: GreatOlmTarget) {
        val npc = spawnNpc(raid, id, tile, raid.currentRoomIndex)
        scaleDonorNpc(raid, npc)
        npc.movementLocked = true
        npc.lockFacingDirection(Direction.West)
        npc.apRangeOverride = 32
        olmRoles[npc] = role
        raiseOlmVisual(raid, role)
        if (role == GreatOlmTarget.HEAD)
            nearestOlmParticipant(raid, tile)?.let { npc.apPlayer2(it, interactions) }
    }

    private fun handleOlmPartDeath(raid: CoxRaidSession, target: GreatOlmTarget) {
        retreatOlmVisual(raid, target)
        val olm = raid.olm ?: return
        val phaseAtDeath = olm.phase
        val transition = olm.defeat(target)
        when (transition) {
            GreatOlmTransition.HAND_DEFEATED -> {
                if (phaseAtDeath == GreatOlmPhase.PHASE_THREE) {
                    pendingOlmHandRevives
                        .getOrPut(raid.instance.id) { mutableMapOf() }[target] =
                        clock.cycle +
                            COX_OLM_HAND_DEATH_TICKS +
                            COX_OLM_FINAL_HAND_REVIVE_WINDOW_TICKS
                }
            }
            GreatOlmTransition.NEXT_PHASE -> {
                pendingOlmHandRevives.remove(raid.instance.id)
                pendingOlmPhaseRespawns[raid.instance.id] =
                    PendingOlmPhaseRespawn(
                        phase = olm.phase,
                        dueCycle =
                            clock.cycle +
                                COX_OLM_HAND_DEATH_TICKS +
                                COX_OLM_PHASE_SUBMERGED_TICKS,
                    )
                message(
                    raid.party,
                    "Great Olm's hand set ${olm.phaseNumber} will rise shortly.",
                )
            }
            GreatOlmTransition.FINAL_HEAD -> {
                pendingOlmHandRevives.remove(raid.instance.id)
                nextOlmFallingCrystalCycle[raid.instance.id] = clock.cycle + COX_OLM_HAND_DEATH_TICKS
                olmFallingCrystalWaveIndex[raid.instance.id] = 0
                message(raid.party, "All three hand sets are down. Attack Great Olm's head!")
            }
            GreatOlmTransition.RAID_COMPLETE -> completeRaid(raid)
        }
    }

    fun olmBasicMaxHit(npc: Npc): Int {
        val raid = raidFor(npc) ?: return 27
        val phase = raid.olm?.phase ?: GreatOlmPhase.PHASE_ONE
        return when (raid.difficulty) {
            RaidDifficulty.NORMAL ->
                when (phase) {
                    GreatOlmPhase.PHASE_THREE -> 28
                    GreatOlmPhase.FINAL_HEAD -> 29
                    else -> 27
                }
            RaidDifficulty.CHALLENGE ->
                when (phase) {
                    GreatOlmPhase.PHASE_ONE,
                    GreatOlmPhase.PHASE_TWO -> 38
                    else -> 41
                }
        }
    }

    fun performOlmSpecial(source: Npc): Boolean {
        val raid = raidFor(source) ?: return false
        if (raid.state != RaidState.OLM || olmRoles[source] != GreatOlmTarget.HEAD) return false
        val online =
            raid.participants.keys
                .mapNotNull { it.resolve(players) }
                .filter { isInsideOlmArena(raid, it) }
        val targets = buildList<PathingEntity> {
            addAll(online)
            addAll(activeOlmTestTargets(source))
        }
        if (!canQueueOlmSpecial(source.isSlotAssigned, source.hitpoints, targets.size)) return false
        val special = raid.olm?.nextSpecial() ?: return false
        when (special) {
            GreatOlmSpecial.CRYSTALS -> {
                animateOlmLeftHandSpecial(raid, special)
                targets.forEach { (it as? Player)?.mes("Crystals burst from the chamber floor!") }
                queueOlmCrystalBurst(raid, source, targets)
            }
            GreatOlmSpecial.LIGHTNING -> {
                animateOlmLeftHandSpecial(raid, special)
                val target = targets[random.of(targets.indices)]
                (target as? Player)?.mes("Great Olm sends lightning through the chamber!")
                target.spotanim(1_356)
                queueOlmHit(target, source, delay = 2, damage = random.of(1..21))
            }
            GreatOlmSpecial.ACID -> {
                animateOlmHeadAttack(raid)
                val target = targets[random.of(targets.indices)]
                (target as? Player)?.mes(
                    "Great Olm smothers you in acid. It starts to drip off slowly."
                )
                listOf(1, 3, 5).forEach { delay ->
                    queueOlmHit(target, source, delay, random.of(3..6))
                }
            }
            GreatOlmSpecial.BURN -> {
                animateOlmHeadAttack(raid)
                val target = targets[random.of(targets.indices)]
                (target as? Player)?.mes("I will burn with you!")
                listOf(2, 10, 18, 26, 34).forEach { delay -> queueOlmHit(target, source, delay, 5) }
                targets
                    .filter { it !== target && it.coords.chebyshevDistance(target.coords) <= 1 }
                    .forEach {
                        (it as? Player)?.mes("The burn spreads to you!")
                        listOf(2, 10, 18, 26, 34).forEach { delay -> queueOlmHit(it, source, delay, 5) }
                    }
            }
            GreatOlmSpecial.PORTALS -> {
                animateOlmLeftHandSpecial(raid, special)
                targets.forEachIndexed { index, target ->
                    val partner = targets[(index + 1) % targets.size]
                    (target as? Player)?.mes("Paired portals tear open beneath the party!")
                    if (partner !== target) {
                        val damage =
                            (target.coords.chebyshevDistance(partner.coords) * 4).coerceAtMost(60)
                        if (damage > 0) queueOlmHit(target, source, delay = 2, damage = damage)
                    }
                }
            }
            GreatOlmSpecial.FALLING_CRYSTALS -> {
                animateOlmHeadAttack(raid)
                targets.forEach { (it as? Player)?.mes("Crystals rain down during the final phase!") }
                nextOlmFallingCrystalCycle.putIfAbsent(raid.instance.id, clock.cycle)
                olmFallingCrystalWaveIndex.putIfAbsent(raid.instance.id, 0)
            }
        }
        return true
    }

    fun animateOlmHeadBasicAttack(npc: Npc) {
        val raid = raidFor(npc) ?: return
        if (olmRoles[npc] != GreatOlmTarget.HEAD) return
        animateOlmHeadAttack(raid)
    }

    fun activeOlmTargets(npc: Npc): List<Player> {
        val raid = raidFor(npc) ?: return emptyList()
        return raid.participants.keys
            .mapNotNull { it.resolve(players) }
            .filter { isInsideOlmArena(raid, it) }
    }

    fun activeOlmTestTargets(npc: Npc): List<Npc> {
        val raid = raidFor(npc) ?: return emptyList()
        return olmTestTargets[raid.instance.id]
            ?.filter { it.isSlotAssigned && it.hitpoints > 0 }
            .orEmpty()
    }

    fun spawnOlmTestTargets(player: Player, requested: Int): Boolean {
        val raid = raidFor(player)
        if (raid == null || raid.state != RaidState.OLM || !isInsideOlmArena(raid, player)) {
            player.mes("You must be inside an active Great Olm chamber to spawn test bots.")
            return false
        }
        removeOlmTestTargets(raid)
        val count = requested.coerceIn(1, OLM_TEST_TARGET_LIMIT)
        val candidates =
            OLM_TEST_TARGET_OFFSETS
                .asSequence()
                .map { (x, z) -> player.coords.translate(x, z) }
                .filter { isInsideOlmArena(raid, it) }
                .filter { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
                .distinct()
                .take(count)
                .toList()
        if (candidates.isEmpty()) {
            player.mes("No clear tiles were found around you for Olm test bots.")
            return false
        }
        val type =
            requireNotNull(ServerCacheManager.getNpc(OLM_TEST_TARGET_NPC)) {
                "Missing Olm test-target npc $OLM_TEST_TARGET_NPC"
            }
        val targets = olmTestTargets.getOrPut(raid.instance.id) { mutableListOf() }
        candidates.forEach { tile ->
            val target = Npc(type, tile)
            target.mode = null
            target.movementLocked = true
            target.ignoreCombatInteractions = true
            target.suppressRewards = true
            target.respawns = false
            target.baseHitpointsLvl = OLM_TEST_TARGET_HITPOINTS
            target.hitpoints = OLM_TEST_TARGET_HITPOINTS
            npcs.add(target, Int.MAX_VALUE)
            instances.attachNpc(raid.instance.id, target)
            raid.spawnedNpcs += target
            targets += target
        }
        player.mes(
            "Spawned ${targets.size} Olm test bot${if (targets.size == 1) "" else "s"}. " +
                "Olm will include them in every basic multi-target attack."
        )
        return true
    }

    fun clearOlmTestTargets(player: Player): Boolean {
        val raid = raidFor(player)
        if (raid == null) {
            player.mes("You do not have an active Chambers raid.")
            return false
        }
        val removed = removeOlmTestTargets(raid)
        player.mes("Removed $removed Olm test bot${if (removed == 1) "" else "s"}.")
        return true
    }

    private fun removeOlmTestTargets(raid: CoxRaidSession): Int {
        val targets = olmTestTargets.remove(raid.instance.id).orEmpty()
        targets.forEach { target ->
            raid.spawnedNpcs.remove(target)
            raid.npcRooms.remove(target)
            if (target.isSlotAssigned) npcs.del(target, Int.MAX_VALUE)
        }
        return targets.size
    }

    private fun animateOlmLeftHandSpecial(raid: CoxRaidSession, special: GreatOlmSpecial) {
        val visual = findOlmVisual(raid, GreatOlmTarget.LEFT_HAND) ?: return
        val sequence = GreatOlmVisuals.leftHandSpecialSequence(special) ?: return
        world.locAnim(visual, olmSequence(sequence))
    }

    private fun animateOlmHeadAttack(raid: CoxRaidSession) {
        val phase = raid.olm?.phase ?: return
        val definition = GreatOlmVisuals[GreatOlmTarget.HEAD]
        val visual =
            locs
                .findAll(olmVisualTile(raid, GreatOlmTarget.HEAD))
                .firstOrNull { it.id == definition.idleLoc || it.id == definition.riseLoc }
                ?: return
        val sequence = GreatOlmVisuals.headAttackSequence(phase == GreatOlmPhase.FINAL_HEAD)
        world.locAnim(visual, olmSequence(sequence))
    }

    private fun queueOlmCrystalBurst(
        raid: CoxRaidSession,
        source: Npc,
        targets: List<PathingEntity>,
    ) {
        val warningType =
            requireNotNull(ServerCacheManager.getObject(CoxLocs.OLM_CRYSTAL_WARNING)) {
                "Missing Olm crystal warning loc ${CoxLocs.OLM_CRYSTAL_WARNING}"
            }
        targets.groupBy { it.coords }.forEach { (tile, markedTargets) ->
            if (
                pendingOlmCrystalBursts.any {
                    it.instanceId == raid.instance.id && it.loc.coords == tile
                }
            ) {
                return@forEach
            }
            val warning =
                locs.add(
                    tile,
                    warningType,
                    Int.MAX_VALUE,
                    LocAngle.North,
                    LocShape.CentrepieceStraight,
                )
            pendingOlmCrystalBursts +=
                PendingOlmCrystalBurst(
                    instanceId = raid.instance.id,
                    source = source,
                    markedTargets = markedTargets,
                    loc = warning,
                    impactAt = clock.cycle + GreatOlmCrystalRules.BURST_WARNING_TICKS,
                    removeAt =
                        clock.cycle +
                            GreatOlmCrystalRules.BURST_WARNING_TICKS +
                            GreatOlmCrystalRules.BURST_ERUPTION_TICKS,
                )
        }
    }

    private fun processOlmCrystalBursts() {
        val eruptionType =
            requireNotNull(ServerCacheManager.getObject(CoxLocs.OLM_CRYSTAL_ERUPTION)) {
                "Missing Olm crystal eruption loc ${CoxLocs.OLM_CRYSTAL_ERUPTION}"
            }
        pendingOlmCrystalBursts.toList().forEach { pending ->
            val raid = raids[pending.instanceId]
            if (
                raid == null ||
                    raid.state != RaidState.OLM ||
                    !pending.source.isSlotAssigned ||
                    pending.source.hitpoints <= 0
            ) {
                locs.del(pending.loc, Int.MAX_VALUE)
                pendingOlmCrystalBursts.remove(pending)
                return@forEach
            }
            if (!pending.erupted && clock.cycle >= pending.impactAt) {
                locs.change(pending.loc, eruptionType, Int.MAX_VALUE)
                pending.loc =
                    LocInfo(
                        pending.loc.layer,
                        pending.loc.coords,
                        LocEntity(
                            CoxLocs.OLM_CRYSTAL_ERUPTION,
                            LocShape.CentrepieceStraight.id,
                            LocAngle.North.id,
                        ),
                    )
                pending.erupted = true
                pending.markedTargets
                    .filter {
                        isAliveOlmTarget(it) &&
                            GreatOlmCrystalRules.burstHits(pending.loc.coords, it.coords)
                    }
                    .forEach {
                        applyOlmCrystalHit(it, pending.source, damage = random.of(30..50))
                    }
            }
            if (clock.cycle >= pending.removeAt) {
                locs.del(pending.loc, Int.MAX_VALUE)
                pendingOlmCrystalBursts.remove(pending)
            }
        }
    }

    private fun processOlmFallingCrystals() {
        nextOlmFallingCrystalCycle.toMap().forEach { (instanceId, dueCycle) ->
            if (clock.cycle < dueCycle) return@forEach
            val raid = raids[instanceId]
            val state = raid?.olm
            val source =
                raid?.spawnedNpcs?.firstOrNull {
                    olmRoles[it] == GreatOlmTarget.HEAD && it.isSlotAssigned && it.hitpoints > 0
                }
            if (
                raid == null ||
                    state?.phase != GreatOlmPhase.FINAL_HEAD ||
                    raid.state != RaidState.OLM ||
                    source == null
            ) {
                nextOlmFallingCrystalCycle.remove(instanceId)
                olmFallingCrystalWaveIndex.remove(instanceId)
                return@forEach
            }
            val targets = buildList<PathingEntity> {
                addAll(activeOlmTargets(source))
                addAll(activeOlmTestTargets(source))
            }
            if (targets.isNotEmpty()) {
                val waveIndex = olmFallingCrystalWaveIndex.getOrPut(instanceId) { 0 }
                val trackedTile =
                    if (GreatOlmCrystalRules.isTargetedFallingWave(waveIndex)) {
                        targets[random.of(targets.indices)].coords.also {
                            launchOlmFallingCrystal(raid, source, it)
                        }
                    } else {
                        null
                    }
                val randomTile = randomOlmArenaTile(raid)
                if (randomTile != trackedTile) launchOlmFallingCrystal(raid, source, randomTile)
                olmFallingCrystalWaveIndex[instanceId] = waveIndex + 1
            }
            nextOlmFallingCrystalCycle[instanceId] =
                clock.cycle + GreatOlmCrystalRules.FALLING_INTERVAL_TICKS
        }
    }

    private fun launchOlmFallingCrystal(
        raid: CoxRaidSession,
        source: Npc,
        tile: CoordGrid,
    ) {
        val start = GreatOlmCrystalRules.fallingSource(tile, eastOffset = random.of(2) == 0)
        val projectile =
            ProjAnim(
                spotanim = GreatOlmCrystalRules.FALLING_PROJECTILE,
                startHeight = GreatOlmCrystalRules.FALLING_PROJECTILE_START_HEIGHT,
                endHeight = 0,
                startTime = 0,
                endTime = GreatOlmCrystalRules.FALLING_PROJECTILE_END_TIME,
                angle = 0,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = start,
                endCoord = tile,
            )
        world.projAnim(projectile)
        world.spotanimMap(
            SpotanimType(GreatOlmCrystalRules.FALLING_SHADOW),
            tile,
            delay = GreatOlmCrystalRules.FALLING_SHADOW_DELAY,
        )
        world.spotanimMap(
            SpotanimType(GreatOlmCrystalRules.FALLING_IMPACT),
            tile,
            delay = projectile.clientCycles,
        )
        pendingOlmFallingImpacts +=
            PendingOlmFallingImpact(
                instanceId = raid.instance.id,
                source = source,
                tile = tile,
                impactAt = clock.cycle + projectile.serverCycles + 1,
            )
    }

    private fun processOlmFallingImpacts() {
        pendingOlmFallingImpacts.toList().forEach { pending ->
            if (clock.cycle < pending.impactAt) return@forEach
            pendingOlmFallingImpacts.remove(pending)
            val raid = raids[pending.instanceId] ?: return@forEach
            if (
                raid.state != RaidState.OLM ||
                    raid.olm?.phase != GreatOlmPhase.FINAL_HEAD ||
                    !pending.source.isSlotAssigned ||
                    pending.source.hitpoints <= 0
            ) {
                return@forEach
            }
            val targets = buildList<PathingEntity> {
                addAll(activeOlmTargets(pending.source))
                addAll(activeOlmTestTargets(pending.source))
            }
            targets.forEach { target ->
                val damageRange =
                    GreatOlmCrystalRules.fallingDamageRange(
                        target.coords.chebyshevDistance(pending.tile)
                    )
                if (damageRange != null) {
                    applyOlmCrystalHit(target, pending.source, damage = random.of(damageRange))
                }
            }
        }
    }

    private fun randomOlmArenaTile(raid: CoxRaidSession): CoordGrid {
        val southWest =
            donorCoord(
                raid,
                CoxDonorArea.OLM,
                GreatOlmCrystalRules.ARENA_MIN_LOCAL_X,
                GreatOlmCrystalRules.ARENA_MIN_LOCAL_Z,
            )
        val northEast =
            donorCoord(
                raid,
                CoxDonorArea.OLM,
                GreatOlmCrystalRules.ARENA_MAX_LOCAL_X,
                GreatOlmCrystalRules.ARENA_MAX_LOCAL_Z,
            )
        repeat(32) {
            val tile =
                CoordGrid(
                    random.of(southWest.x..northEast.x),
                    random.of(southWest.z..northEast.z),
                    southWest.level,
                )
            if (collision.isZoneValid(tile) && !collision.isWalkBlocked(tile)) return tile
        }
        return southWest.translate(4, 7)
    }

    private fun cleanupOlmCrystalHazards(raid: CoxRaidSession) {
        pendingOlmCrystalBursts
            .filter { it.instanceId == raid.instance.id }
            .forEach { locs.del(it.loc, Int.MAX_VALUE) }
        pendingOlmCrystalBursts.removeAll { it.instanceId == raid.instance.id }
        pendingOlmFallingImpacts.removeAll { it.instanceId == raid.instance.id }
        nextOlmFallingCrystalCycle.remove(raid.instance.id)
        olmFallingCrystalWaveIndex.remove(raid.instance.id)
    }
    private fun isAliveOlmTarget(target: PathingEntity): Boolean =
        when (target) {
            is Player -> target.hitpoints > 0
            is Npc -> target.isSlotAssigned && target.hitpoints > 0
        }
    private fun queueOlmHit(target: PathingEntity, source: Npc, delay: Int, damage: Int) {
        when (target) {
            is Player ->
                target.queueHit(
                    source = source,
                    delay = delay,
                    type = HitType.Typeless,
                    damage = damage,
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            is Npc ->
                target.queueNpcHit(
                    source = source,
                    delay = delay,
                    type = HitType.Typeless,
                    damage = damage,
                    modifier = npcHitModifier,
                )
        }
    }

    /**
     * Crystal hazards resolve during the manager's world cycle, after entity queues have already
     * advanced. Player strong queues reject a zero-cycle delay, so queuing an impact with delay 0
     * throws and disconnects the struck player. Apply player environmental damage immediately at
     * the visual impact instead; test NPCs still use the minimum legal one-cycle queue.
     */
    private fun applyOlmCrystalHit(target: PathingEntity, source: Npc, damage: Int) {
        when (target) {
            is Player ->
                target.takeInstantHit(
                    type = HitType.Typeless,
                    damage = damage,
                    processor = instantHitProcessor,
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            is Npc ->
                target.queueNpcHit(
                    source = source,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = damage,
                    modifier = npcHitModifier,
                )
        }
    }

    private fun completeRoom(raid: CoxRaidSession, hero: Player?) {
        val room = raid.currentRoom
        if (room.state == RaidRoomState.COMPLETE) return
        room.state = RaidRoomState.COMPLETE
        if (room.room.category == CoxRoomCategory.PUZZLE) {
            val recipient = hero?.let { raid.participant(it.uid) }
            if (recipient != null) CoxPoints.award(raid, recipient, COX_ROOM_POINTS)
        }
        message(raid.party, "${room.room.displayName} is complete. The way forward is open.")
    }

    private fun completeRaid(raid: CoxRaidSession, forceUniqueFor: PlayerUid? = null) {
        if (raid.state == RaidState.REWARDS || raid.state == RaidState.COMPLETE) return
        removeOlmTestTargets(raid)
        cleanupOlmCrystalHazards(raid)
        raid.state = RaidState.REWARDS
        raid.donorStage = CoxDonorStage.REWARDS
        raid.completedAt = clock.cycle
        raid.currentRoom.state = RaidRoomState.COMPLETE
        pendingOlmRiseAnimations.remove(raid.instance.id)
        pendingOlmVisuals.remove(raid.instance.id)
        pendingOlmRetreatVisuals.remove(raid.instance.id)
        pendingOlmPhaseRespawns.remove(raid.instance.id)
        pendingOlmHandRevives.remove(raid.instance.id)
        val elapsed = clock.cycle - raid.startedAt
        if (CoxChallengeRules.eligibleForTimedRewards(raid)) {
            raid.participants.values.forEach { participant ->
                CoxPoints.award(raid, participant, 5_000)
            }
            message(
                raid.party,
                "Challenge time achieved: every raider receives 5,000 bonus points.",
            )
        }
        rewards.prepare(raid, forceUniqueFor)
        val chestType = requireNotNull(ServerCacheManager.getObject(CoxLocs.ANCIENT_CHEST))
        val chestTile = donorCoord(raid, CoxDonorArea.OLM, 33, 55)
        val closedChest = locs.findAll(chestTile).firstOrNull { it.id == 30_027 }
        if (closedChest != null) {
            locs.change(closedChest, chestType, Int.MAX_VALUE)
        } else if (locs.findAll(chestTile).none { it.id == CoxLocs.ANCIENT_CHEST }) {
            val chest =
                locs.add(
                    chestTile,
                    chestType,
                    Int.MAX_VALUE,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
            raid.spawnedLocs += chest
        }
        val crystalTile = donorCoord(raid, CoxDonorArea.OLM, 32, 53)
        locs
            .findAll(crystalTile)
            .filter { it.id == CoxLocs.MUTTADILE_BARRIER }
            .toList()
            .forEach { locs.del(it, Int.MAX_VALUE) }
        forEachOnline(raid) { player ->
            // Send personal reward locs one cycle after completion. This matters for ::coxpc,
            // which completes the test raid before the player's dynamic-region rebuild reaches
            // the reward room; an immediate private loc update is otherwise sent to the old map
            // and the purple beam never appears.
            pendingRewardBeamSyncs +=
                PendingRewardBeamSync(
                    instanceId = raid.instance.id,
                    player = player.uid,
                    showAt = clock.cycle + 1,
                )
            val countKey =
                if (raid.difficulty == RaidDifficulty.CHALLENGE) COX_CHALLENGE_COMPLETION_COUNT
                else COX_COMPLETION_COUNT
            val pbKey =
                if (raid.difficulty == RaidDifficulty.CHALLENGE) COX_CHALLENGE_PERSONAL_BEST
                else COX_PERSONAL_BEST
            player.attr[countKey] = player.attr.getOrDefault(countKey, 0) + 1
            val old = player.attr[pbKey]
            if (old == null || elapsed < old) player.attr[pbKey] = elapsed
            val personal = raid.participant(player.uid)?.personalPoints ?: 0
            player.mes(
                "Chambers complete in ${elapsed / 100}:${(elapsed % 100 * 3 / 5).toString().padStart(2, '0')}. " +
                    "Personal points: $personal; team points: ${raid.teamPoints}. " +
                    "Search the ancient chest."
            )
        }
        log.info {
            "[CoX] complete party=${raid.party.id} instance=${raid.instance.id} difficulty=${raid.difficulty} ticks=$elapsed points=${raid.teamPoints}"
        }
    }

    private fun leaveRaid(
        player: Player,
        raid: CoxRaidSession,
        removeFromParty: Boolean,
        teleport: Boolean = true,
    ): Boolean {
        if (instances.sessionForPlayer(player)?.id == raid.instance.id)
            instances.leave(player, raid.instance, clock.cycle)
        prepare(player)
        if (teleport) PathingEntityCommon.telejump(player, collision, COX_LOBBY)
        else player.coords = COX_LOBBY
        participantRaids.remove(player.uid)
        raid.participant(player.uid)?.connected = false
        supplies.cleanupInventory(player)
        ui.close(player)
        if (removeFromParty) {
            raid.party.members.remove(player.uid)
            membership.remove(player.uid)
        }
        if (raid.party.members.isEmpty()) abort(raid, "party empty")
        else if (raid.party.leader == player.uid) transferOrDisband(raid.party)
        return true
    }

    private fun abort(raid: CoxRaidSession, reason: String) {
        if (raid.cleanupStarted) return
        raid.cleanupStarted = true
        raid.state = RaidState.ABANDONED
        clearRaidNpcs(raid)
        forEachOnline(raid) { player ->
            if (instances.sessionForPlayer(player)?.id == raid.instance.id)
                instances.leave(player, raid.instance, clock.cycle)
            prepare(player)
            PathingEntityCommon.telejump(player, collision, COX_LOBBY)
            supplies.cleanupInventory(player)
            ui.close(player)
            player.mes("Your Chambers raid has ended: $reason.")
        }
        clearRaidLocs(raid)
        clearRaidObjs(raid)
        raid.participants.keys.forEach(participantRaids::remove)
        raids.remove(raid.instance.id)
        raid.party.activeSession = null
        puzzleProgress.remove(raid.instance.id)
        pendingOlmRiseAnimations.remove(raid.instance.id)
        pendingOlmVisuals.remove(raid.instance.id)
        pendingOlmRetreatVisuals.remove(raid.instance.id)
        pendingOlmPhaseRespawns.remove(raid.instance.id)
        pendingOlmHandRevives.remove(raid.instance.id)
        pendingMuttadileEmergence.remove(raid.instance.id)
        pendingRewardBeamSyncs.removeAll { it.instanceId == raid.instance.id }
        pendingRewardBeamTriggers.removeAll { it.instanceId == raid.instance.id }
        supplies.cleanup(raid)
        instances.terminate(raid.instance)
        log.info {
            "[CoX] abort party=${raid.party.id} instance=${raid.instance.id} reason=$reason"
        }
    }

    private fun clearRaidNpcs(raid: CoxRaidSession) {
        val hadOlmParts = raid.spawnedNpcs.any { it in olmRoles }
        olmTestTargets.remove(raid.instance.id)
        raid.spawnedNpcs.toList().forEach { npc ->
            if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        }
        raid.spawnedNpcs.forEach(olmRoles::remove)
        raid.spawnedNpcs.clear()
        raid.npcRooms.clear()
        raid.donorEncounters.clear()
        raid.donorVanguardForms.clear()
        raid.donorVanguardWaypoints.clear()
        raid.largeMuttadile = null
        raid.largeMuttadileUnlocked = false
        raid.muttadileMeatTree = null
        raid.muttadileMeatTreeLoc = null
        raid.muttadileMeatTreeFelled = false
        raid.muttadileCombatDamageCredited = 0
        raid.muttadileTreePointsCredited = 0
        raid.muttadileEatAttempted.clear()
        raid.muttadileEating.clear()
        pendingMuttadileEmergence.remove(raid.instance.id)
        if (hadOlmParts) {
            pendingOlmRiseAnimations.remove(raid.instance.id)
            pendingOlmVisuals.remove(raid.instance.id)
            pendingOlmRetreatVisuals.remove(raid.instance.id)
            pendingOlmPhaseRespawns.remove(raid.instance.id)
            pendingOlmHandRevives.remove(raid.instance.id)
            cleanupOlmCrystalHazards(raid)
            GreatOlmTarget.entries.forEach { hideOlmVisual(raid, it) }
        }
    }

    private fun raiseOlmVisual(raid: CoxRaidSession, target: GreatOlmTarget) {
        pendingOlmRiseAnimations[raid.instance.id]?.remove(target)
        pendingOlmRetreatVisuals[raid.instance.id]?.remove(target)
        val definition = GreatOlmVisuals[target]
        setOlmVisual(raid, target, definition.riseLoc) ?: return
        // The donor sends the new loc first and waits one tick before animating it. Sending both
        // updates together races the client's loc replacement, so the hand simply pops into view.
        pendingOlmRiseAnimations
            .getOrPut(raid.instance.id) { mutableMapOf() }[target] =
            GreatOlmVisualTiming.riseAnimationCycle(clock.cycle)
        pendingOlmVisuals.getOrPut(raid.instance.id) { mutableMapOf() }[target] =
            GreatOlmVisualTiming.riseIdleCycle(clock.cycle)
    }

    private fun retreatOlmVisual(raid: CoxRaidSession, target: GreatOlmTarget) {
        pendingOlmRiseAnimations[raid.instance.id]?.remove(target)
        pendingOlmVisuals[raid.instance.id]?.remove(target)
        val definition = GreatOlmVisuals[target]
        // Animate the idle loc that nearby clients already know. Replacing it in this same tick
        // discards the retreat animation packet and makes the hand disappear instantly.
        val visual =
            findOlmVisual(raid, target)?.takeIf {
                it.id == definition.idleLoc || it.id == definition.riseLoc
            }
                ?: run {
                    log.warn {
                        "[CoX] missing active Olm retreat visual instance=${raid.instance.id} " +
                            "target=$target tile=${olmVisualTile(raid, target)}"
                    }
                    return
                }
        world.locAnim(visual, olmSequence(definition.retreatSequence))
        pendingOlmRetreatVisuals
            .getOrPut(raid.instance.id) { mutableMapOf() }[target] =
            clock.cycle + COX_OLM_HAND_DEATH_TICKS
    }

    private fun hideOlmVisual(raid: CoxRaidSession, target: GreatOlmTarget) {
        pendingOlmRiseAnimations[raid.instance.id]?.remove(target)
        pendingOlmVisuals[raid.instance.id]?.remove(target)
        pendingOlmRetreatVisuals[raid.instance.id]?.remove(target)
        setOlmVisual(raid, target, GreatOlmVisuals[target].dormantLoc)
    }

    private fun setOlmVisual(
        raid: CoxRaidSession,
        target: GreatOlmTarget,
        objectId: Int,
    ): LocInfo? {
        val tile = olmVisualTile(raid, target)
        val anchor =
            findOlmVisual(raid, target)
                ?: locs.findExact(tile, LocShape.CentrepieceStraight)
        if (anchor == null) {
            log.warn {
                "[CoX] missing Olm visual anchor instance=${raid.instance.id} " +
                    "target=$target tile=$tile object=$objectId"
            }
            return null
        }
        val type =
            requireNotNull(ServerCacheManager.getObject(objectId)) {
                "Missing Olm visual loc $objectId"
            }
        return locs.add(anchor.coords, type, Int.MAX_VALUE, anchor.angle, anchor.shape)
    }

    private fun findOlmVisual(raid: CoxRaidSession, target: GreatOlmTarget): LocInfo? =
        locs
            .findAll(olmVisualTile(raid, target))
            .firstOrNull { it.id in GreatOlmVisuals.locIds }

    private fun processOlmVisualTransitions() {
        for ((instanceId, pending) in pendingOlmRiseAnimations.toMap()) {
            val raid = raids[instanceId]
            if (raid == null) {
                pendingOlmRiseAnimations.remove(instanceId)
                continue
            }
            val ready = pending.filterValues { clock.cycle >= it }.keys.toList()
            ready.forEach { target ->
                val definition = GreatOlmVisuals[target]
                val visual =
                    findOlmVisual(raid, target)?.takeIf { it.id == definition.riseLoc }
                if (visual == null) {
                    log.warn {
                        "[CoX] Olm rise loc changed before animation instance=$instanceId " +
                            "target=$target expected=${definition.riseLoc}"
                    }
                } else {
                    world.locAnim(visual, olmSequence(definition.riseSequence))
                }
                pending.remove(target)
            }
            if (pending.isEmpty()) pendingOlmRiseAnimations.remove(instanceId)
        }
        for ((instanceId, pending) in pendingOlmVisuals.toMap()) {
            val raid = raids[instanceId]
            if (raid == null) {
                pendingOlmVisuals.remove(instanceId)
                continue
            }
            val ready = pending.filterValues { clock.cycle >= it }.keys.toList()
            ready.forEach { target ->
                setOlmVisual(raid, target, GreatOlmVisuals[target].idleLoc)
                pending.remove(target)
            }
            if (pending.isEmpty()) pendingOlmVisuals.remove(instanceId)
        }
        for ((instanceId, pending) in pendingOlmRetreatVisuals.toMap()) {
            val raid = raids[instanceId]
            if (raid == null) {
                pendingOlmRetreatVisuals.remove(instanceId)
                continue
            }
            val ready = pending.filterValues { clock.cycle >= it }.keys.toList()
            ready.forEach { target ->
                setOlmVisual(raid, target, GreatOlmVisuals[target].dormantLoc)
                pending.remove(target)
            }
            if (pending.isEmpty()) pendingOlmRetreatVisuals.remove(instanceId)
        }
    }

    private fun processOlmPhaseRespawns() {
        for ((instanceId, pending) in pendingOlmPhaseRespawns.toMap()) {
            if (clock.cycle < pending.dueCycle) continue
            pendingOlmPhaseRespawns.remove(instanceId)
            val raid = raids[instanceId] ?: continue
            val state = raid.olm ?: continue
            if (raid.state != RaidState.OLM || state.phase != pending.phase) continue
            spawnOlmHand(raid, GreatOlmTarget.LEFT_HAND)
            spawnOlmHand(raid, GreatOlmTarget.RIGHT_HAND)
            message(raid.party, "Great Olm's fresh pair of hands rises.")
        }
    }

    private fun processOlmHandRevives() {
        for ((instanceId, pending) in pendingOlmHandRevives.toMap()) {
            val raid = raids[instanceId]
            val state = raid?.olm
            if (raid == null || state == null || state.phase != GreatOlmPhase.PHASE_THREE) {
                pendingOlmHandRevives.remove(instanceId)
                continue
            }
            val ready = pending.filterValues { clock.cycle >= it }.keys.toList()
            ready.forEach { target ->
                pending.remove(target)
                if (state.canRevive(target)) {
                    state.revive(target)
                    spawnOlmHand(raid, target)
                    val label =
                        if (target == GreatOlmTarget.LEFT_HAND) "left hand" else "right hand"
                    message(raid.party, "Great Olm regenerates its $label!")
                }
            }
            if (pending.isEmpty()) pendingOlmHandRevives.remove(instanceId)
        }
    }

    private fun olmVisualTile(raid: CoxRaidSession, target: GreatOlmTarget): CoordGrid =
        when (target) {
            GreatOlmTarget.HEAD -> donorCoord(raid, CoxDonorArea.OLM, 38, 42)
            GreatOlmTarget.LEFT_HAND -> donorCoord(raid, CoxDonorArea.OLM, 38, 37)
            GreatOlmTarget.RIGHT_HAND -> donorCoord(raid, CoxDonorArea.OLM, 38, 47)
        }

    private fun isInsideOlmArena(raid: CoxRaidSession, player: Player): Boolean =
        isInsideOlmArena(raid, player.coords)

    private fun isInsideOlmArena(raid: CoxRaidSession, tile: CoordGrid): Boolean {
        val southWest = donorCoord(raid, CoxDonorArea.OLM, 24, 34)
        val northEast = donorCoord(raid, CoxDonorArea.OLM, 63, 63)
        return tile.level == southWest.level &&
            tile.x in southWest.x..northEast.x &&
            tile.z in southWest.z..northEast.z
    }

    private fun nearestOlmParticipant(raid: CoxRaidSession, tile: CoordGrid): Player? =
        raid.participants.keys
            .mapNotNull { it.resolve(players) }
            .filter { isInsideOlmArena(raid, it) }
            .minByOrNull { it.coords.chebyshevDistance(tile) }

    private fun olmSequence(id: Int): String =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) { "Missing Olm sequence $id" }

    private fun spawnNpc(raid: CoxRaidSession, id: Int, tile: CoordGrid, roomIndex: Int): Npc {
        val type = requireNotNull(ServerCacheManager.getNpc(id)) { "Missing CoX npc $id" }
        return Npc(type, tile).also { npc ->
            npc.mode = type.defaultMode
            npcs.add(npc, Int.MAX_VALUE)
            npc.respawns = false
            npc.scriptedDamageGate =
                id in
                    setOf(
                        CoxNpcs.GUARDIAN,
                        CoxNpcs.GUARDIAN_ALT,
                        CoxNpcs.VANGUARD_MELEE,
                        CoxNpcs.VANGUARD_RANGED,
                        CoxNpcs.VANGUARD_MAGIC,
                        CoxNpcs.OLM_LEFT_HAND,
                        CoxNpcs.OLM_RIGHT_HAND,
                        CoxNpcs.OLM_HEAD,
                    )
            instances.attachNpc(raid.instance.id, npc)
            raid.spawnedNpcs += npc
            raid.npcRooms[npc] = roomIndex
        }
    }

    private fun clearRaidLocs(raid: CoxRaidSession) {
        raid.spawnedLocs.toList().forEach { loc -> locs.del(loc, Int.MAX_VALUE) }
        raid.spawnedLocs.clear()
        raid.donorBlockers.clear()
    }

    private fun clearRaidObjs(raid: CoxRaidSession) {
        raid.spawnedObjs.toList().forEach { objs.del(it, Int.MAX_VALUE) }
        raid.spawnedObjs.clear()
    }

    private fun roomCoord(raid: CoxRaidSession, room: CoxRoomPlacement): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, CoxInstanceLayout.local(room))) {
            "Missing instance coordinate for CoX room ${room.room}"
        }

    private fun nearestParticipant(raid: CoxRaidSession, tile: CoordGrid): Player? =
        raid.participants.keys
            .mapNotNull { it.resolve(players) }
            .minByOrNull { it.coords.chebyshevDistance(tile) }

    private fun prepare(player: Player) {
        player.cancelActiveCoroutine()
        player.combatClearQueue()
        player.clearInteractionRoute()
    }

    private fun transferOrDisband(party: RaidParty) {
        val replacement = party.members.firstOrNull()
        parties.remove(party.leader)
        if (replacement != null) {
            party.leader = replacement
            parties[replacement] = party
            message(party, "${label(replacement)} is now the party leader.")
        }
    }

    private fun findPlayer(name: String): Player? =
        players.firstOrNull { it.displayName.equals(name, true) || it.username.equals(name, true) }

    private fun label(uid: PlayerUid): String = uid.resolve(players)?.displayName ?: uid.toString()

    private fun rewardBeam(
        raid: CoxRaidSession,
        player: Player,
        chest: CoordGrid,
    ): LocInfo? {
        val roll = raid.participant(player.uid)?.pendingReward ?: return null
        val id =
            if (roll.uniqueAwarded) CoxLocs.REWARD_BEAM_UNIQUE else CoxLocs.REWARD_BEAM_BASIC
        return LocInfo(
            LocLayer.Wall.id,
            chest,
            LocEntity(id, CoxRewardBeamRules.PRIMARY_SHAPE, CoxRewardBeamRules.ANGLE),
        )
    }

    private fun rewardBeamTrigger(id: Int, chest: CoordGrid): LocInfo =
        LocInfo(
            LocLayer.Ground.id,
            CoxRewardBeamRules.triggerTile(chest),
            LocEntity(id, CoxRewardBeamRules.TRIGGER_SHAPE, CoxRewardBeamRules.ANGLE),
        )

    private fun processRewardBeamSyncs() {
        val due = pendingRewardBeamSyncs.filter { clock.cycle >= it.showAt }
        if (due.isEmpty()) return
        pendingRewardBeamSyncs.removeAll(due.toSet())
        due.forEach { pending ->
            val raid = raids[pending.instanceId] ?: return@forEach
            if (raid.state != RaidState.REWARDS) return@forEach
            val player = pending.player.resolve(players) ?: return@forEach
            if (instances.sessionForPlayer(player)?.id != raid.instance.id) return@forEach
            val chest = donorCoord(raid, CoxDonorArea.OLM, 33, 55)
            val beam = rewardBeam(raid, player, chest) ?: return@forEach
            world.locAddPrivate(player, beam)
            val trigger = rewardBeamTrigger(beam.id, chest)
            world.locAddPrivate(player, trigger)
            pendingRewardBeamTriggers +=
                PendingRewardBeamTrigger(
                    instanceId = raid.instance.id,
                    player = player.uid,
                    loc = trigger,
                    removeAt = clock.cycle + CoxRewardBeamRules.TRIGGER_LIFETIME_TICKS,
                )
        }
    }

    private fun processRewardBeamTriggers() {
        val due = pendingRewardBeamTriggers.filter { clock.cycle >= it.removeAt }
        if (due.isEmpty()) return
        pendingRewardBeamTriggers.removeAll(due.toSet())
        due.forEach { pending ->
            pending.player.resolve(players)?.let { world.locDelPrivate(it, pending.loc) }
        }
    }

    private fun message(party: RaidParty, text: String) {
        party.members.forEach { it.resolve(players)?.mes(text) }
    }

    private inline fun forEachOnline(raid: CoxRaidSession, action: (Player) -> Unit) {
        raid.participants.keys.mapNotNull { it.resolve(players) }.forEach(action)
    }

    private companion object {
        private const val OLM_TEST_TARGET_NPC = 3106
        private const val OLM_TEST_TARGET_HITPOINTS = 10_000_000
        private const val OLM_TEST_TARGET_LIMIT = 5
        private val OLM_TEST_TARGET_OFFSETS =
            listOf(
                2 to 0,
                -2 to 0,
                0 to 2,
                0 to -2,
                2 to 2,
                -2 to 2,
                2 to -2,
                -2 to -2,
                3 to 0,
                -3 to 0,
            )
    }
}

private data class PendingOlmPhaseRespawn(
    val phase: GreatOlmPhase,
    val dueCycle: Int,
)

private data class PendingMuttadileEmergence(
    val npc: Npc,
    val destination: CoordGrid,
    val unlockCycle: Int,
)

private data class PendingOlmCrystalBurst(
    val instanceId: InstanceId,
    val source: Npc,
    val markedTargets: List<PathingEntity>,
    var loc: LocInfo,
    val impactAt: Int,
    val removeAt: Int,
    var erupted: Boolean = false,
)

private data class PendingOlmFallingImpact(
    val instanceId: InstanceId,
    val source: Npc,
    val tile: CoordGrid,
    val impactAt: Int,
)
private data class PendingRewardBeamTrigger(
    val instanceId: InstanceId,
    val player: PlayerUid,
    val loc: LocInfo,
    val removeAt: Int,
)

private data class PendingRewardBeamSync(
    val instanceId: InstanceId,
    val player: PlayerUid,
    val showAt: Int,
)
