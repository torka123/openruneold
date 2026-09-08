package org.rsmod.content.minigames.toa

import dev.openrune.ParamReferences.param
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.SequenceServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.game.outgoing.camera.CamReset
import net.rsprot.protocol.game.outgoing.camera.CamShake
import org.rsmod.api.config.refs.params
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.output.VarpSync
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

internal data class HetLiveBridgeResult(
    val handled: Boolean,
    val challengeComplete: Boolean = false,
    val sealWorkByAccountHash: Map<Long, Int> = emptyMap(),
)

/**
 * Owns one deterministic Path of Het runtime per ToA instance and is the only adapter allowed to
 * translate [HetLiveEvent] values into native OpenRune entities and packets.
 */
@Singleton
internal class ToaHetOpenRuneBridge
@Inject
constructor(
    private val clock: MapClock,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
    private val supplies: ToaSupplies,
) {
    private data class State(
        val raid: ToaRaidSession,
        val seed: Long,
        val runtime: ToaHetLiveRuntime,
        val localToWorld: Map<HetTile, CoordGrid>,
        val worldToLocal: Map<CoordGrid, HetTile>,
        val activeLocs: MutableMap<HetTile, BoundLocInfo> = linkedMapOf(),
        val sealNpcs: MutableMap<HetTile, Npc> = linkedMapOf(),
        val orbNpcs: MutableMap<Int, Npc> = linkedMapOf(),
        var actionSerial: Int = 0,
        var sealStateVarpValue: Int = Int.MIN_VALUE,
    )

    private data class Pickaxe(val tier: HetPickaxeTier, val type: ItemServerType)

    private val states = mutableMapOf<InstanceId, State>()
    private val mirrorObjectType: String by lazy {
        RSCM.getReverseMapping(RSCMType.OBJ, HetLiveAssets.MIRROR_ITEM)
    }
    private val sealStateVarp by lazy {
        requireNotNull(ServerCacheManager.getVarp(HetLiveAssets.SEAL_STATE_VARP)) {
            "Missing revision-240 Het seal-state varp ${HetLiveAssets.SEAL_STATE_VARP}"
        }
    }

    fun validateAssets() {
        val locIds =
            HetRevision240RuntimeLayout.barrierLocs.flatMapTo(linkedSetOf()) {
                listOf(it.intact, it.transition)
            } +
                HetRevision240RuntimeLayout.CLEARED_BARRIER_LOC +
                HetRevision240RuntimeLayout.mirrorLocs.let {
                    setOf(it.movableClean, it.fixedClean, it.fixedDirty)
                } +
                setOf(
                    HetRevision240RuntimeLayout.CASTER_STATUE_LOC,
                    HetRevision240RuntimeLayout.SHIELDED_STATUE_LOC,
                )
        locIds.forEach { id ->
            requireNotNull(ServerCacheManager.getObject(id)) { "Missing revision-240 Het loc $id" }
        }
        requireNotNull(ServerCacheManager.getObject(HetLiveAssets.EMPTY_LOC)) {
            "Missing revision-240 Het empty loc ${HetLiveAssets.EMPTY_LOC}"
        }
        sealStateVarp
        requireNotNull(ServerCacheManager.getItem(HetLiveAssets.MIRROR_ITEM)) {
            "Missing revision-240 Het mirror item ${HetLiveAssets.MIRROR_ITEM}"
        }
        val movableMirror =
            requireNotNull(
                ServerCacheManager.getObject(HetRevision240RuntimeLayout.mirrorLocs.movableClean)
            )
        require(
            listOf("Pick-up", "Rotate-clockwise", "Rotate-anticlockwise", "Push") ==
                (0..3).map(movableMirror.actions::getOpOrNull)
        ) {
            "Revision-240 Het mirror action slots changed: ${movableMirror.actions}."
        }
        val dirtyMirror =
            requireNotNull(
                ServerCacheManager.getObject(HetRevision240RuntimeLayout.mirrorLocs.fixedDirty)
            )
        require(dirtyMirror.actions.getOpOrNull(0) == "Clean") {
            "Revision-240 dirty Het mirror op1 is not Clean."
        }
        setOf(45462, 45464).forEach { id ->
            require(ServerCacheManager.getObject(id)?.actions?.getOpOrNull(0) == "Break") {
                "Revision-240 Het barrier $id op1 is not Break."
            }
        }
        setOf(HetLiveAssets.SEAL_NPC, HetLiveAssets.VULNERABLE_SEAL_NPC).forEach { id ->
            require(ServerCacheManager.getNpc(id)?.actions?.getOpOrNull(0) == "Destroy") {
                "Revision-240 Het seal $id op1 is not Destroy."
            }
        }
        val mirrorItem = requireNotNull(ServerCacheManager.getItem(HetLiveAssets.MIRROR_ITEM))
        require(mirrorItem.interfaceOptions.getOrNull(0) == "Place") {
            "Revision-240 Het mirror held op1 is not Place."
        }
        require(mirrorItem.internalName == mirrorObjectType) {
            "Het mirror revision/internal mapping mismatch: ${mirrorItem.internalName} != $mirrorObjectType."
        }
        setOf(
                HetLiveAssets.SEAL_NPC,
                HetLiveAssets.VULNERABLE_SEAL_NPC,
                HetLiveAssets.FALLING_ORB_NPC,
            )
            .forEach { id ->
                requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Het npc $id" }
            }
        setOf(HetLiveAssets.PICK_OR_PLACE_ANIMATION, HetLiveAssets.PUSH_ANIMATION).forEach { id ->
            requireNotNull(ServerCacheManager.getAnim(id)) {
                "Missing revision-240 Het sequence $id"
            }
        }
        setOf(
                HetLiveAssets.BEAM_HORIZONTAL_GRAPHIC,
                HetLiveAssets.BEAM_VERTICAL_GRAPHIC,
                HetLiveAssets.BEAM_SOUTH_EAST_GRAPHIC,
                HetLiveAssets.BEAM_SOUTH_WEST_GRAPHIC,
                HetLiveAssets.BEAM_NORTH_WEST_GRAPHIC,
                HetLiveAssets.BEAM_NORTH_EAST_GRAPHIC,
                HetLiveAssets.BEAM_END_GRAPHIC,
                HetLiveAssets.SEAL_PROTECTED_GRAPHIC,
                HetLiveAssets.SHIELD_SUCCESS_GRAPHIC,
                HetLiveAssets.ORB_TELEGRAPH_GRAPHIC,
                HetLiveAssets.ORB_ACTIVE_GRAPHIC,
                HetLiveAssets.ROOF_COLLAPSE_GRAPHIC,
            )
            .forEach { id ->
                requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
                    "Missing revision-240 Het spotanim $id"
                }
            }
    }

    fun initialize(raid: ToaRaidSession): HetLiveBridgeResult {
        check(raid.challenge?.stage == ToaChallengeStage.ACTIVE) {
            "Het initialized before its red barrier was crossed."
        }
        cleanup(raid)
        val seed = raidSeed(raid)
        val runtime =
            ToaHetLiveRuntime(
                seed = seed,
                partySize = raid.startSnapshot.partySize,
                partyMemberIds = raid.startSnapshot.participantAccountHashes,
                groupSealScaling =
                    HetGroupSealScaling { partySize ->
                        HetSealHitpoints.SOLO +
                            ToaHetLiveRuntime.GROUP_SEAL_HP_INCREMENT_REFERENCE * (partySize - 1)
                    },
                hazardDamageMultiplier =
                    ToaRules.damageFactor(raid.raidLevel) *
                        ToaRules.pathFactor(raid.pathLevels[ToaPath.HET] ?: 0),
            )
        val localTiles = HetRevision240RuntimeLayout.allRoomTiles
        val localToWorld =
            localTiles.associateWith { local ->
                requireNotNull(resolve(raid, local)) {
                    "Unable to resolve Het local tile $local in instance ${raid.instance.id}."
                }
            }
        val state =
            State(
                raid = raid,
                seed = seed,
                runtime = runtime,
                localToWorld = localToWorld,
                worldToLocal = localToWorld.entries.associate { (local, tile) -> tile to local },
            )
        states[raid.instance.id] = state
        return apply(state, runtime.initialize(clock.cycle))
    }

    fun tick(raid: ToaRaidSession): HetLiveBridgeResult {
        val state = states[raid.instance.id] ?: return HetLiveBridgeResult(handled = false)
        val playerTiles =
            raid.participants.values
                .mapNotNull { participant ->
                    val player = participant.uid.resolve(players) ?: return@mapNotNull null
                    if (
                        !participant.connected ||
                            participant.spectating ||
                            raid.challenge?.stage != ToaChallengeStage.ACTIVE ||
                            !ToaChallengeRoomRules.isInside(
                                raid,
                                instances,
                                ToaRoom.HET,
                                player.coords,
                            ) ||
                            player.coords !in state.worldToLocal
                    ) {
                        return@mapNotNull null
                    }
                    participant.accountHash to state.worldToLocal.getValue(player.coords)
                }
                .toMap()
        return apply(state, state.runtime.tick(clock.cycle, playerTiles))
    }

    fun interactMirror(
        player: Player,
        raid: ToaRaidSession,
        loc: BoundLocInfo,
        action: HetLiveObjectAction,
    ): HetLiveBridgeResult {
        val state = activeState(player, raid) ?: return HetLiveBridgeResult(handled = false)
        val local = state.worldToLocal[loc.coords] ?: return HetLiveBridgeResult(handled = false)
        val pickaxe = if (action == HetLiveObjectAction.BREAK) findPickaxe(player) else null
        val request =
            HetLiveObjectRequest(
                actorId = actorId(player),
                locId = loc.id,
                tile = local,
                action = action,
                actorTile = state.worldToLocal[player.coords],
                pushDirection =
                    if (action == HetLiveObjectAction.PUSH) pushDirection(player.coords, loc.coords)
                    else null,
                inventoryHasSpace = player.inv.hasFreeSpace(),
                pickaxeTier = pickaxe?.tier,
            )
        return apply(state, state.runtime.objectInteraction(request))
    }

    fun mineLoc(player: Player, raid: ToaRaidSession, loc: BoundLocInfo): HetLiveBridgeResult {
        val state = activeState(player, raid) ?: return HetLiveBridgeResult(handled = false)
        val local = state.worldToLocal[loc.coords] ?: return HetLiveBridgeResult(handled = false)
        val pickaxe = findPickaxe(player)
        return apply(
            state,
            state.runtime.itemOnObject(
                HetLiveItemOnObjectRequest(
                    actorId = actorId(player),
                    targetLocId = loc.id,
                    targetTile = local,
                    pickaxeTier = pickaxe?.tier,
                    miningLevel = player.miningLvl.coerceAtLeast(1),
                    rngTicket = state.nextTicket(actorId(player)),
                )
            ),
        )
    }

    fun mineSeal(player: Player, raid: ToaRaidSession, npc: Npc): HetLiveBridgeResult {
        val state = activeState(player, raid) ?: return HetLiveBridgeResult(handled = false)
        if (npc !in state.sealNpcs.values || npc !in raid.spawnedNpcs) {
            return HetLiveBridgeResult(handled = false)
        }
        val pickaxe = findPickaxe(player)
        val actor = actorId(player)
        val events =
            if (pickaxe == null) {
                listOf(HetLiveEvent.Rejected(actor, HetLiveRejectReason.PICKAXE_REQUIRED))
            } else {
                state.runtime.mineSeal(
                    actorId = actor,
                    tier = pickaxe.tier,
                    miningLevel = player.miningLvl.coerceAtLeast(1),
                    rngTicket = state.nextTicket(actor),
                )
            }
        return apply(state, events)
    }

    fun placeMirror(player: Player, raid: ToaRaidSession): HetLiveBridgeResult {
        val state = activeState(player, raid) ?: return HetLiveBridgeResult(handled = false)
        val local = state.worldToLocal[player.coords]
        if (local == null) {
            player.mes("You cannot place the mirror here.")
            return HetLiveBridgeResult(handled = true)
        }
        return apply(
            state,
            state.runtime.placeMirror(
                HetLivePlaceMirrorRequest(
                    actorId = actorId(player),
                    tile = local,
                    direction = HetMirrorDirection.NORTH_EAST,
                )
            ),
        )
    }

    fun cleanup(raid: ToaRaidSession): HetLiveBridgeResult {
        val state = states[raid.instance.id] ?: return HetLiveBridgeResult(handled = false)
        val result = apply(state, state.runtime.cleanup())
        removeAllLocs(state)
        states.remove(raid.instance.id)
        return result
    }

    fun syncRejoin(player: Player, raid: ToaRaidSession) {
        val state = states[raid.instance.id]
        val expected =
            if (raid.currentRoom == ToaRoom.HET && state != null) {
                state.runtime.snapshot().heldMirrorCounts[actorId(player)] ?: 0
            } else {
                0
            }
        val current = supplies.ownedChallengeItemCount(player, raid, HetLiveAssets.MIRROR_ITEM)
        when {
            current > expected ->
                check(
                    supplies.consumeChallengeItem(
                        player,
                        raid,
                        HetLiveAssets.MIRROR_ITEM,
                        current - expected,
                    )
                ) {
                    "Unable to remove surplus Het mirrors for ${player.displayName}."
                }
            current < expected ->
                check(
                    supplies.grantChallengeItem(
                        player,
                        raid,
                        HetLiveAssets.MIRROR_ITEM,
                        expected - current,
                    )
                ) {
                    "Unable to restore Het mirror state for ${player.displayName}."
                }
        }
        if (state != null && raid.currentRoom == ToaRoom.HET) syncSealState(player, state)
    }

    private fun activeState(player: Player, raid: ToaRaidSession): State? {
        if (raid.currentPath != ToaPath.HET || raid.currentRoom != ToaRoom.HET) return null
        if (raid.challenge?.stage != ToaChallengeStage.ACTIVE) return null
        if (raid.participant(player.uid)?.spectating != false) return null
        if (!ToaChallengeRoomRules.isInside(raid, instances, ToaRoom.HET, player.coords))
            return null
        return states[raid.instance.id]
    }

    private fun apply(state: State, events: List<HetLiveEvent>): HetLiveBridgeResult {
        var complete = false
        val sealWork = linkedMapOf<Long, Int>()
        events.forEach { event ->
            when (event) {
                is HetLiveEvent.Rejected -> event.actorId?.let { reject(state, it, event.reason) }
                is HetLiveEvent.PhaseChanged -> phaseChanged(state, event.phase)
                is HetLiveEvent.SpawnLoc -> spawnLoc(state, event.loc)
                is HetLiveEvent.ReplaceLoc -> replaceLoc(state, event.loc)
                is HetLiveEvent.RemoveLoc -> removeLoc(state, event.loc.tile)
                is HetLiveEvent.ClearLoc -> clearLoc(state, event.loc)
                is HetLiveEvent.SpawnNpc -> spawnNpc(state, event.npcId, event.tile)
                is HetLiveEvent.TransformNpc -> transformNpc(state, event.tile, event.npcId)
                is HetLiveEvent.RemoveNpc -> removeNpc(state, event.tile)
                is HetLiveEvent.PlayerAnimation ->
                    player(state, event.actorId)?.anim(event.sequenceId)
                is HetLiveEvent.PickaxeAnimation -> animatePickaxe(state, event)
                is HetLiveEvent.MirrorItemDelta -> mirrorItemDelta(state, event)
                is HetLiveEvent.RemoveAllMirrorItems -> removeAllMirrors(state, event)
                is HetLiveEvent.Sound -> sound(state, event)
                is HetLiveEvent.Graphic -> graphic(state, event)
                is HetLiveEvent.Beam ->
                    event.graphics.forEach { beam ->
                        world.spotanimMap(
                            SpotanimType(beam.spotanimId),
                            state.world(beam.tile),
                            height = beam.height,
                            delay = beam.delay,
                        )
                    }
                is HetLiveEvent.DamagePlayer -> damage(state, event)
                is HetLiveEvent.BarrierWorked -> Unit
                is HetLiveEvent.SealWorked -> {
                    sealWorked(state, event)
                    sealWork[event.actorId] =
                        ((sealWork[event.actorId] ?: 0).toLong() + event.applied)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                }
                is HetLiveEvent.FallingOrbTelegraph -> Unit
                is HetLiveEvent.FallingOrbActivated -> activateOrb(state, event)
                is HetLiveEvent.FallingOrbMoved -> moveOrb(state, event)
                is HetLiveEvent.FallingOrbExploded -> removeOrb(state, event.orbId)
                HetLiveEvent.ShieldHit -> Unit
                is HetLiveEvent.CameraShake ->
                    onlinePlayers(state).forEach {
                        it.client.write(
                            CamShake(event.axis, event.random, event.amplitude, event.rate)
                        )
                    }
                HetLiveEvent.CameraReset ->
                    onlinePlayers(state).forEach { it.client.write(CamReset) }
                HetLiveEvent.ChallengeComplete -> complete = true
            }
        }
        if (complete) {
            apply(state, state.runtime.cleanup())
            removeAllLocs(state)
            states.remove(state.raid.instance.id)
        }
        return HetLiveBridgeResult(
            handled = true,
            challengeComplete = complete,
            sealWorkByAccountHash = sealWork,
        )
    }

    private fun phaseChanged(state: State, phase: HetLivePhase) {
        syncSealState(state, phase)
        when (phase) {
            HetLivePhase.ACTIVE -> message(state, "Direct the beam into the statue's shield.")
            HetLivePhase.VULNERABLE ->
                message(state, "The seal is exposed. Mine it before the light returns!")
            else -> Unit
        }
    }

    private fun syncSealState(state: State, phase: HetLivePhase) {
        val value = phase.sealStateVarpValue()
        if (state.sealStateVarpValue == value) return
        state.sealStateVarpValue = value
        onlinePlayers(state).forEach { VarpSync.writeVarp(it, sealStateVarp, value) }
    }

    private fun syncSealState(player: Player, state: State) {
        VarpSync.writeVarp(
            player,
            sealStateVarp,
            state.runtime.snapshot().phase.sealStateVarpValue(),
        )
    }

    private fun spawnLoc(state: State, runtimeLoc: HetRuntimeLoc) {
        removeLoc(state, runtimeLoc.tile)
        val shape = runtimeLoc.shape.toLocShape()
        val angle = runtimeLoc.angle.toLocAngle()
        val type =
            requireNotNull(ServerCacheManager.getObject(runtimeLoc.locId)) {
                "Missing Het loc ${runtimeLoc.locId}."
            }
        val info =
            LocInfo(
                layer = LocLayerConstants.of(shape.id),
                coords = state.world(runtimeLoc.tile),
                entity = LocEntity(runtimeLoc.locId, shape.id, angle.id),
            )
        check(locs.add(info, Int.MAX_VALUE)) { "Failed to spawn Het loc $runtimeLoc." }
        state.activeLocs[runtimeLoc.tile] = BoundLocInfo(info, type)
    }

    private fun replaceLoc(state: State, runtimeLoc: HetRuntimeLoc) {
        removeLoc(state, runtimeLoc.tile)
        spawnLoc(state, runtimeLoc)
    }

    private fun removeLoc(state: State, tile: HetTile) {
        val current = state.activeLocs.remove(tile) ?: return
        locs.del(current, Int.MAX_VALUE)
    }

    private fun clearLoc(state: State, runtimeLoc: HetRuntimeLoc) {
        removeLoc(state, runtimeLoc.tile)
        spawnLoc(state, runtimeLoc.copy(locId = HetLiveAssets.EMPTY_LOC))
    }

    private fun removeAllLocs(state: State) {
        state.activeLocs.keys.toList().forEach { removeLoc(state, it) }
    }

    private fun spawnNpc(state: State, npcId: Int, local: HetTile) {
        if (npcId == HetLiveAssets.FALLING_ORB_NPC) return
        removeNpc(state, local)
        val type = requireNotNull(ServerCacheManager.getNpc(npcId)) { "Missing Het npc $npcId." }
        val npc =
            Npc(type, state.world(local)).also {
                it.mode = null
                it.moveRestrict = MoveRestrict.NoMove
                it.movementLocked = true
                it.ignoreCombatInteractions = true
                npcs.add(it, Int.MAX_VALUE)
                it.respawns = false
                instances.attachNpc(state.raid.instance.id, it)
                state.raid.spawnedNpcs += it
                state.raid.npcRoles[it] = ToaNpcRole.HAZARD
            }
        state.sealNpcs[local] = npc
        syncSealHitpoints(state, npc)
    }

    private fun transformNpc(state: State, local: HetTile, npcId: Int) {
        val npc = state.sealNpcs[local] ?: return
        val type = requireNotNull(ServerCacheManager.getNpc(npcId)) { "Missing Het npc $npcId." }
        npc.transmog(type, Int.MAX_VALUE)
        syncSealHitpoints(state, npc)
    }

    private fun removeNpc(state: State, local: HetTile) {
        val npc = state.sealNpcs.remove(local) ?: return
        removeNativeNpc(state, npc)
    }

    private fun activateOrb(state: State, event: HetLiveEvent.FallingOrbActivated) {
        removeOrb(state, event.orbId)
        val type =
            requireNotNull(ServerCacheManager.getNpc(event.npcId)) {
                "Missing Het orb npc ${event.npcId}."
            }
        val npc =
            Npc(type, state.world(event.tile)).also {
                it.mode = NpcMode.None
                it.moveRestrict = MoveRestrict.NoMove
                it.ignoreCombatInteractions = true
                npcs.add(it, Int.MAX_VALUE)
                it.respawns = false
                instances.attachNpc(state.raid.instance.id, it)
                state.raid.spawnedNpcs += it
                state.raid.npcRoles[it] = ToaNpcRole.HAZARD
            }
        state.orbNpcs[event.orbId] = npc
    }

    private fun moveOrb(state: State, event: HetLiveEvent.FallingOrbMoved) {
        val npc = state.orbNpcs[event.orbId] ?: return
        PathingEntityCommon.telejump(npc, collision, state.world(event.to))
    }

    private fun removeOrb(state: State, orbId: Int) {
        val npc = state.orbNpcs.remove(orbId) ?: return
        removeNativeNpc(state, npc)
    }

    private fun removeNativeNpc(state: State, npc: Npc) {
        npc.mode = null
        npc.clearInteractionRoute()
        npc.abortRoute()
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        state.raid.spawnedNpcs.remove(npc)
        state.raid.npcRoles.remove(npc)
    }

    private fun animatePickaxe(state: State, event: HetLiveEvent.PickaxeAnimation) {
        val actor = player(state, event.actorId) ?: return
        val pickaxe = findPickaxe(actor)?.takeIf { it.tier == event.tier } ?: return
        val wallAnimation =
            if (event.wallAnimation) {
                pickaxe.type.paramOrNull(HetLiveParams.skillWallAnim)?.id
            } else {
                null
            }
        val sequence = wallAnimation ?: pickaxe.type.paramOrNull(params.skill_anim)?.id ?: return
        actor.anim(sequence)
    }

    private fun mirrorItemDelta(state: State, event: HetLiveEvent.MirrorItemDelta) {
        check(event.itemId == HetLiveAssets.MIRROR_ITEM) { "Unexpected Het item ${event.itemId}." }
        val actor = player(state, event.actorId) ?: return
        if (event.amount > 0) {
            check(supplies.grantChallengeItem(actor, state.raid, event.itemId, event.amount)) {
                "Het runtime granted a mirror without inventory capacity."
            }
        } else if (event.amount < 0) {
            check(supplies.consumeChallengeItem(actor, state.raid, event.itemId, -event.amount)) {
                "Het runtime consumed a mirror the player no longer owns."
            }
        }
    }

    private fun removeAllMirrors(state: State, event: HetLiveEvent.RemoveAllMirrorItems) {
        check(event.itemId == HetLiveAssets.MIRROR_ITEM) { "Unexpected Het item ${event.itemId}." }
        val actor = residentPlayer(state, event.actorId) ?: return
        supplies.removeIssuedChallengeItems(actor, state.raid, HetLiveAssets.MIRROR_ITEM)
    }

    private fun sound(state: State, event: HetLiveEvent.Sound) {
        onlinePlayers(state).forEach { it.soundSynth(event.soundId) }
    }

    private fun graphic(state: State, event: HetLiveEvent.Graphic) {
        world.spotanimMap(
            SpotanimType(event.spotanimId),
            state.world(event.tile),
            height = event.height,
            delay = event.delay,
        )
    }

    private fun damage(state: State, event: HetLiveEvent.DamagePlayer) {
        val actor = player(state, event.actorId) ?: return
        actor.queueImpactHit(
            delay = event.delay.coerceAtLeast(1),
            type = HitType.Typeless,
            damage = event.amount,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    private fun sealWorked(state: State, event: HetLiveEvent.SealWorked) {
        state.sealNpcs.values.forEach { npc ->
            npc.baseHitpointsLvl = event.maximumHitpoints
            npc.hitpoints = event.hitpoints
        }
    }

    private fun syncSealHitpoints(state: State, npc: Npc) {
        val snapshot = state.runtime.snapshot()
        npc.baseHitpointsLvl = snapshot.sealMaximumHitpoints
        npc.hitpoints = snapshot.sealHitpoints
    }

    private fun reject(state: State, actorId: Long, reason: HetLiveRejectReason) {
        val actor = player(state, actorId) ?: return
        actor.mes(
            when (reason) {
                HetLiveRejectReason.NOT_INITIALIZED -> "The Path of Het has not started."
                HetLiveRejectReason.ALREADY_INITIALIZED -> "The Path of Het is already active."
                HetLiveRejectReason.NOT_ACTIVE -> "You cannot do that while the room is changing."
                HetLiveRejectReason.INVALID_TARGET ->
                    "That cannot be used in the current puzzle layout."
                HetLiveRejectReason.INVENTORY_FULL ->
                    "You need a free inventory slot for the mirror."
                HetLiveRejectReason.MIRROR_NOT_HELD -> "You are not carrying a mirror."
                HetLiveRejectReason.DESTINATION_BLOCKED -> "The mirror cannot move there."
                HetLiveRejectReason.PICKAXE_REQUIRED -> "You need a pickaxe to mine this."
                HetLiveRejectReason.SEAL_PROTECTED -> "The light protects the seal."
                HetLiveRejectReason.ALREADY_COMPLETE -> "The Path of Het is already complete."
                HetLiveRejectReason.CLEANED_UP -> "The Path of Het is no longer active."
            }
        )
    }

    private fun findPickaxe(player: Player): Pickaxe? =
        sequenceOf(player.worn, player.inv)
            .flatMap { inventory -> inventory.asSequence().filterNotNull() }
            .mapNotNull { obj ->
                val type = ServerCacheManager.getItem(obj.id) ?: return@mapNotNull null
                val tier = pickaxeTier(type) ?: return@mapNotNull null
                val levelRequirement = type.paramOrNull(params.levelrequire) ?: 1
                if (player.miningLvl < levelRequirement) return@mapNotNull null
                Pickaxe(tier, type)
            }
            .maxByOrNull { it.tier.barrierPower }

    private fun pickaxeTier(type: ItemServerType): HetPickaxeTier? {
        if (!type.isContentType("content.mining_pickaxe")) return null
        val key =
            (type.internalName + " " + type.name).lowercase().replace('-', '_').replace(' ', '_')
        return when {
            "3rd_age" in key || "third_age" in key || "3a_pickaxe" in key ->
                HetPickaxeTier.THIRD_AGE
            "crystal" in key -> HetPickaxeTier.CRYSTAL
            "infernal" in key -> HetPickaxeTier.INFERNAL
            "dragon" in key || "zalcano_pickaxe" in key -> HetPickaxeTier.DRAGON
            "gilded" in key -> HetPickaxeTier.GILDED
            "rune" in key -> HetPickaxeTier.RUNE
            "adamant" in key -> HetPickaxeTier.ADAMANT
            "mithril" in key -> HetPickaxeTier.MITHRIL
            "black" in key -> HetPickaxeTier.BLACK
            "steel" in key -> HetPickaxeTier.STEEL
            "iron" in key -> HetPickaxeTier.IRON
            "bronze" in key -> HetPickaxeTier.BRONZE
            else -> null
        }
    }

    private fun pushDirection(player: CoordGrid, mirror: CoordGrid): HetDirection? {
        val deltaX = mirror.x - player.x
        val deltaZ = mirror.z - player.z
        return when {
            deltaX > 0 && kotlin.math.abs(deltaX) >= kotlin.math.abs(deltaZ) -> HetDirection.EAST
            deltaX < 0 && kotlin.math.abs(deltaX) >= kotlin.math.abs(deltaZ) -> HetDirection.WEST
            deltaZ > 0 -> HetDirection.NORTH
            deltaZ < 0 -> HetDirection.SOUTH
            else -> null
        }
    }

    private fun resolve(raid: ToaRaidSession, local: HetTile): CoordGrid? =
        instances.localCoord(
            raid.instance,
            ToaInstanceLayout.roomLocal(ToaRoom.HET, local.x, local.z),
        )

    private fun player(state: State, actorId: Long): Player? {
        val participant = state.raid.participant(actorId)?.takeIf { it.connected } ?: return null
        return participant.uid.resolve(players)
    }

    private fun residentPlayer(state: State, actorId: Long): Player? {
        return state.raid.participant(actorId)?.uid?.resolve(players)
    }

    private fun onlinePlayers(state: State): List<Player> =
        state.raid.participants.values
            .filter { it.connected && !it.spectating && it.currentRoom == ToaRoom.HET }
            .mapNotNull { it.uid.resolve(players) }
            .filter {
                state.raid.challenge?.stage == ToaChallengeStage.ACTIVE &&
                    ToaChallengeRoomRules.isInside(state.raid, instances, ToaRoom.HET, it.coords)
            }

    private fun message(state: State, text: String) = onlinePlayers(state).forEach { it.mes(text) }

    private fun State.world(local: HetTile): CoordGrid =
        requireNotNull(localToWorld[local]) { "Unresolved Het local tile $local." }

    private fun State.nextTicket(actorId: Long): Int {
        var value = seed xor actorId xor (actionSerial++.toLong() shl 32) xor clock.cycle.toLong()
        value = (value xor (value ushr 33)) * -49064778989728563L
        value = (value xor (value ushr 33)) * -4265267296055464877L
        return (value xor (value ushr 33)).toInt()
    }

    private fun raidSeed(raid: ToaRaidSession): Long = raid.startSnapshot.seed xor HET_SEED_SALT

    private fun actorId(player: Player): Long = player.accountHash

    private fun Int.toLocShape(): LocShape =
        when (this) {
            LocShape.CentrepieceStraight.id -> LocShape.CentrepieceStraight
            LocShape.CentrepieceDiagonal.id -> LocShape.CentrepieceDiagonal
            else -> error("Unsupported Het loc shape $this")
        }

    private fun Int.toLocAngle(): LocAngle =
        when (this) {
            0 -> LocAngle.West
            1 -> LocAngle.North
            2 -> LocAngle.East
            3 -> LocAngle.South
            else -> error("Unsupported Het loc angle $this")
        }

    private companion object {
        const val HET_SEED_SALT = 0x484554L
    }
}

private object HetLiveParams {
    val skillWallAnim = param<SequenceServerType>("skill_wall_anim")
}
