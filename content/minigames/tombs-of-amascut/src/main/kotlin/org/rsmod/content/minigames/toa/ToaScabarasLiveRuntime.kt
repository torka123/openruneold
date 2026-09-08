package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.loc.LocLayerConstants

internal enum class ScabarasLiveObjectAction {
    PUSH,
    INSPECT,
    ACTIVATE,
    FLIP,
}

internal enum class ScabarasLiveNpcKind {
    OBELISK,
    OBELISK_DECOY,
    RANGED_SCARAB,
}

internal enum class ScabarasLiveEventKind {
    INITIALIZED,
    ACTION_RESULT,
    SEQUENCE_REQUESTED,
    SEQUENCE_FLASH,
    ADDITION_TARGET,
    PUZZLE_COMPLETED,
    CHALLENGE_COMPLETED,
    PENALTY,
    BARRIER_OPENED,
    OBELISKS_RESET,
    NPC_SPAWNED,
    NPC_KILLED,
    CLEANED_UP,
}

internal data class ScabarasLiveEvent(
    val kind: ScabarasLiveEventKind,
    val puzzle: ScabarasPuzzle? = null,
    val actionResult: ScabarasActionResult? = null,
    val tile: CoordGrid? = null,
    val tiles: List<Int> = emptyList(),
    val value: Int? = null,
    val secondaryValue: Int? = null,
    val npcKind: ScabarasLiveNpcKind? = null,
    val npcId: Int? = null,
    val npc: Npc? = null,
)

internal data class ScabarasLiveResult(
    val handled: Boolean,
    val challengeComplete: Boolean,
    val cancelNpcDamage: Boolean = false,
    val events: List<ScabarasLiveEvent> = emptyList(),
)

internal data class ScabarasLiveSnapshot(
    val instanceId: InstanceId,
    val cycle: Int,
    val partySize: Int,
    val solutions: ScabarasSolutions,
    val completedPuzzles: Set<ScabarasPuzzle>,
    val currentSequence: List<Int>,
    val sequencePlaybackActive: Boolean,
    val sequenceProgress: Int,
    val lights: List<Boolean>,
    val obeliskProgress: Int,
    val currentAdditionSum: Int,
    val matchingSolvedSymbols: Set<Int>,
    val activeLocCount: Int,
    val activeNpcCount: Int,
    val challengeComplete: Boolean,
)

internal data class ScabarasLiveLoc(
    val instanceId: InstanceId,
    val id: Int,
    val coords: CoordGrid,
    val shape: LocShape,
    val angle: LocAngle,
)

internal class ScabarasLiveNpc(
    val instanceId: InstanceId,
    val serial: Long,
    val spawnId: Int,
    val coords: CoordGrid,
    val kind: ScabarasLiveNpcKind,
    val lane: ScabarasLane? = null,
    val obeliskIndex: Int? = null,
) {
    var visibleId: Int = spawnId
    var native: Npc? = null
}

internal interface ScabarasLiveWorld {
    fun resolve(instanceId: InstanceId, local: ScabarasTile): CoordGrid?

    fun spawnLoc(loc: ScabarasLiveLoc): Boolean

    fun removeLoc(loc: ScabarasLiveLoc): Boolean

    fun spawnNpc(npc: ScabarasLiveNpc): Npc?

    fun transformNpc(npc: ScabarasLiveNpc, id: Int)

    fun removeNpc(npc: ScabarasLiveNpc)
}

/**
 * Live Path of Scabaras bridge.
 *
 * The deterministic [ScabarasEncounter] remains the only puzzle authority. This class owns the
 * per-instance presentation state and native repository entities, then returns all combat,
 * messaging, damage, camera and raid-transition work as events for the raid manager to consume.
 */
internal class ToaScabarasLiveRuntime internal constructor(private val world: ScabarasLiveWorld) {
    @Inject
    constructor(
        instances: InstanceManager,
        locs: LocRepository,
        npcs: NpcRepository,
    ) : this(RepositoryScabarasLiveWorld(instances, locs, npcs))

    private val states = mutableMapOf<InstanceId, State>()

    fun initialize(instanceId: InstanceId, seed: Long, partySize: Int): ScabarasLiveResult {
        cleanup(instanceId)
        val encounter = ScabarasEncounter(seed, partySize)
        val rooms =
            ScabarasLiveGeometry.PUZZLE_BASES.mapIndexed { index, base ->
                Room(
                    slot = ScabarasRevision240RuntimeLayout.sideRoomSlots[index],
                    puzzle = encounter.solutions.sideRooms[index],
                    base = base,
                    barrier = ScabarasLiveGeometry.BRANCH_FIRES[index],
                )
            }
        val localTiles = requiredLocalTiles(rooms)
        val resolved =
            localTiles.associateWith { local ->
                requireNotNull(world.resolve(instanceId, local)) {
                    "Unable to resolve Scabaras local tile $local in instance $instanceId."
                }
            }
        val state =
            State(
                instanceId = instanceId,
                encounter = encounter,
                rooms = rooms,
                localToWorld = resolved,
                nextScarabTick = SCARAB_INITIAL_DELAY,
            )
        states[instanceId] = state
        val events = mutableListOf<ScabarasLiveEvent>()
        try {
            spawnBarriers(state)
            rooms.forEach { room -> spawnRoom(state, room, events) }
            spawnMatchingBoards(state)
            syncLights(state)
            syncInitialMatching(state)
        } catch (failure: Throwable) {
            cleanup(instanceId)
            throw failure
        }
        events.add(
            0,
            ScabarasLiveEvent(kind = ScabarasLiveEventKind.INITIALIZED, value = partySize),
        )
        return response(state, handled = true, events = events)
    }

    fun tick(instanceId: InstanceId): ScabarasLiveResult {
        val state = states[instanceId] ?: return ScabarasLiveResult(false, false)
        state.cycle++
        val events = mutableListOf<ScabarasLiveEvent>()
        expireTemporaryLocs(state)
        displayNextSequenceTile(state, events)
        spawnRequiredScarabs(state, events)
        return response(state, handled = true, events = events)
    }

    fun onObjectInteraction(
        instanceId: InstanceId,
        action: ScabarasLiveObjectAction,
        locId: Int,
        coords: CoordGrid,
    ): ScabarasLiveResult {
        val state = states[instanceId] ?: return ScabarasLiveResult(false, false)
        if (
            action == ScabarasLiveObjectAction.PUSH &&
                locId == ScabarasRevision240RuntimeLayout.sequenceCache.buttonLoc
        ) {
            return pushSequenceButton(state, coords)
        }
        if (
            action == ScabarasLiveObjectAction.INSPECT &&
                locId == ScabarasRevision240RuntimeLayout.ADDITION_TABLET_LOC
        ) {
            return inspectAdditionTablet(state, coords)
        }
        if (
            action == ScabarasLiveObjectAction.FLIP &&
                locId == ScabarasRevision240RuntimeLayout.lightsCache.tileOffLoc
        ) {
            return flipLight(state, coords)
        }
        if (
            action == ScabarasLiveObjectAction.ACTIVATE &&
                locId in ScabarasRevision240RuntimeLayout.matchingTiles.map { it.hiddenButtonLoc }
        ) {
            return activateMatchingTile(state, locId, coords)
        }
        return response(state, handled = false)
    }

    /** Ground pressure plates are driven only by movement onto their tile. */
    fun onTileStep(instanceId: InstanceId, coords: CoordGrid): ScabarasLiveResult {
        val state = states[instanceId] ?: return ScabarasLiveResult(false, false)
        val sequenceRoom = state.activeRoom(ScabarasPuzzle.SEQUENCE)
        val sequenceTile =
            sequenceRoom?.let { state.offsetIndex(it, coords, ScabarasLiveGeometry.MEMORY_OFFSETS) }
        if (sequenceTile != null) return stepSequence(state, sequenceRoom, sequenceTile, coords)

        val lightRoom = state.activeRoom(ScabarasPuzzle.LIGHTS)
        val lightTile =
            lightRoom?.let { state.offsetIndex(it, coords, ScabarasLiveGeometry.LIGHT_OFFSETS) }
        if (lightTile != null) {
            val result = state.encounter.stepLight(lightTile)
            syncLights(state)
            return actionResponse(state, result)
        }

        val additionRoom = state.activeRoom(ScabarasPuzzle.ADDITION)
        val additionValue = additionRoom?.let { state.additionValue(it, coords) }
        if (additionValue != null) {
            return stepAddition(state, additionRoom, additionValue, coords)
        }
        return response(state, handled = false)
    }

    fun onNpcHit(instanceId: InstanceId, npc: Npc): ScabarasLiveResult {
        val state = states[instanceId] ?: return ScabarasLiveResult(false, false)
        val owned =
            state.npcs.firstOrNull { it.native === npc } ?: return response(state, handled = false)
        return strikeObelisk(state, owned)
    }

    internal fun onNpcHit(
        instanceId: InstanceId,
        npcId: Int,
        coords: CoordGrid,
    ): ScabarasLiveResult {
        val state = states[instanceId] ?: return ScabarasLiveResult(false, false)
        val owned =
            state.npcs.firstOrNull {
                it.coords == coords &&
                    it.visibleId == npcId &&
                    it.kind == ScabarasLiveNpcKind.OBELISK
            } ?: return response(state, handled = false)
        return strikeObelisk(state, owned)
    }

    fun onNpcKill(instanceId: InstanceId, npc: Npc): ScabarasLiveResult {
        val state = states[instanceId] ?: return ScabarasLiveResult(false, false)
        val owned =
            state.npcs.firstOrNull { it.native === npc } ?: return response(state, handled = false)
        return killNpc(state, owned)
    }

    internal fun onNpcKill(
        instanceId: InstanceId,
        npcId: Int,
        coords: CoordGrid,
    ): ScabarasLiveResult {
        val state = states[instanceId] ?: return ScabarasLiveResult(false, false)
        val owned =
            state.npcs.firstOrNull { it.coords == coords && it.visibleId == npcId }
                ?: return response(state, handled = false)
        return killNpc(state, owned)
    }

    fun cleanup(instanceId: InstanceId): ScabarasLiveResult {
        val state = states.remove(instanceId) ?: return ScabarasLiveResult(false, false)
        state.temporaryLocs.clear()
        state.locs.values.toList().forEach(world::removeLoc)
        state.locs.clear()
        state.npcs.toList().forEach(world::removeNpc)
        state.npcs.clear()
        return ScabarasLiveResult(
            handled = true,
            challengeComplete = state.encounter.isComplete,
            events = listOf(ScabarasLiveEvent(ScabarasLiveEventKind.CLEANED_UP)),
        )
    }

    fun snapshot(instanceId: InstanceId): ScabarasLiveSnapshot? {
        val state = states[instanceId] ?: return null
        return ScabarasLiveSnapshot(
            instanceId = instanceId,
            cycle = state.cycle,
            partySize = state.encounter.partySize,
            solutions = state.encounter.solutions,
            completedPuzzles = state.encounter.completedPuzzles,
            currentSequence = state.encounter.currentSequence,
            sequencePlaybackActive = state.sequencePlayback.isNotEmpty(),
            sequenceProgress = state.encounter.sequenceProgress,
            lights = state.encounter.lights,
            obeliskProgress = state.encounter.obeliskProgress,
            currentAdditionSum = state.encounter.currentAdditionSum,
            matchingSolvedSymbols = state.encounter.matchingSolvedSymbols,
            activeLocCount = state.locs.size,
            activeNpcCount = state.npcs.size,
            challengeComplete = state.encounter.isComplete,
        )
    }

    fun owns(instanceId: InstanceId, npc: Npc): Boolean =
        states[instanceId]?.npcs?.any { it.native === npc } == true

    private fun spawnBarriers(state: State) {
        (ScabarasLiveGeometry.BRANCH_FIRES + ScabarasLiveGeometry.EXIT_FIRES).forEach { local ->
            putLoc(
                state,
                FIRE_LOC,
                state.world(local),
                LocShape.CentrepieceStraight,
                LocAngle.South,
            )
        }
    }

    private fun spawnRoom(state: State, room: Room, events: MutableList<ScabarasLiveEvent>) {
        when (room.puzzle) {
            ScabarasPuzzle.SEQUENCE -> {
                putLoc(
                    state,
                    ScabarasRevision240RuntimeLayout.sequenceCache.buttonLoc,
                    state.world(room.base + ScabarasLiveGeometry.BUTTON_OFFSET),
                    LocShape.CentrepieceStraight,
                    LocAngle.West,
                )
                ScabarasLiveGeometry.MEMORY_OFFSETS.forEach { offset ->
                    putLoc(
                        state,
                        ScabarasRevision240RuntimeLayout.sequenceCache.tileUpLoc,
                        state.world(room.base + offset),
                        LocShape.GroundDecor,
                        LocAngle.West,
                    )
                }
            }
            ScabarasPuzzle.LIGHTS ->
                ScabarasLiveGeometry.LIGHT_OFFSETS.forEach { offset ->
                    putLoc(
                        state,
                        ScabarasRevision240RuntimeLayout.lightsCache.tileOffLoc,
                        state.world(room.base + offset),
                        LocShape.GroundDecor,
                        LocAngle.West,
                    )
                }
            ScabarasPuzzle.OBELISKS ->
                ScabarasLiveGeometry.OBELISK_OFFSETS.forEachIndexed { index, offset ->
                    val decoy = index == ScabarasLiveGeometry.OBELISK_OFFSETS.lastIndex
                    spawnNpc(
                        state = state,
                        id =
                            if (decoy) ScabarasRevision240RuntimeLayout.obelisks.hitNpc
                            else ScabarasRevision240RuntimeLayout.obelisks.attackableNpc,
                        coords = state.world(room.base + offset),
                        kind =
                            if (decoy) ScabarasLiveNpcKind.OBELISK_DECOY
                            else ScabarasLiveNpcKind.OBELISK,
                        obeliskIndex = index.takeUnless { decoy },
                        events = events,
                    )
                }
            ScabarasPuzzle.ADDITION -> {
                putLoc(
                    state,
                    ScabarasRevision240RuntimeLayout.ADDITION_TABLET_LOC,
                    state.world(room.base + ScabarasLiveGeometry.BUTTON_OFFSET),
                    LocShape.CentrepieceStraight,
                    LocAngle.West,
                )
                ScabarasLiveGeometry.SUM_OFFSETS.forEach { (value, offsets) ->
                    val cache = ScabarasRevision240RuntimeLayout.additionTiles[value - 1]
                    offsets.forEach { offset ->
                        putLoc(
                            state,
                            cache.groundLoc,
                            state.world(room.base + offset),
                            LocShape.GroundDecor,
                            LocAngle.North,
                        )
                    }
                }
            }
            ScabarasPuzzle.MATCHING -> error("Matching is not a side-room puzzle.")
        }
    }

    private fun spawnMatchingBoards(state: State) {
        ScabarasLiveGeometry.MATCHING_BASES.forEach { (board, base) ->
            val symbols = state.encounter.matchingSymbols(board)
            ScabarasLiveGeometry.MATCHING_OFFSETS.forEachIndexed { tile, offset ->
                val symbol = symbols[tile]
                val cache = ScabarasRevision240RuntimeLayout.matchingTiles[symbol]
                val solved = symbol in state.encounter.matchingSolvedSymbols
                putLoc(
                    state,
                    if (solved) cache.revealedSymbolLoc else cache.hiddenButtonLoc,
                    state.world(base + offset),
                    LocShape.GroundDecor,
                    LocAngle.North,
                )
            }
        }
    }

    private fun syncInitialMatching(state: State) {
        state.encounter.matchingSolvedSymbols.forEach { symbol ->
            ScabarasMatchingBoard.entries.forEach { board -> showMatched(state, board, symbol) }
        }
    }

    private fun pushSequenceButton(state: State, coords: CoordGrid): ScabarasLiveResult {
        val room = state.activeRoom(ScabarasPuzzle.SEQUENCE) ?: return response(state, false)
        if (coords != state.world(room.base + ScabarasLiveGeometry.BUTTON_OFFSET)) {
            return response(state, false)
        }
        if (state.sequencePlayback.isNotEmpty()) {
            val result =
                ScabarasActionResult(
                    puzzle = ScabarasPuzzle.SEQUENCE,
                    outcome = ScabarasActionOutcome.NOT_READY,
                    challengeComplete = state.encounter.isComplete,
                )
            return actionResponse(state, result)
        }
        val request = state.encounter.requestSequence()
        if (request.outcome != ScabarasActionOutcome.PROGRESS) {
            val result =
                ScabarasActionResult(
                    puzzle = ScabarasPuzzle.SEQUENCE,
                    outcome = request.outcome,
                    challengeComplete = state.encounter.isComplete,
                )
            return actionResponse(state, result)
        }
        clearSequenceEffects(state, room)
        state.sequencePlayback.addAll(request.tiles)
        return response(
            state,
            handled = true,
            events =
                listOf(
                    ScabarasLiveEvent(
                        kind = ScabarasLiveEventKind.SEQUENCE_REQUESTED,
                        puzzle = ScabarasPuzzle.SEQUENCE,
                        tiles = request.tiles,
                    )
                ),
        )
    }

    private fun inspectAdditionTablet(state: State, coords: CoordGrid): ScabarasLiveResult {
        val room = state.activeRoom(ScabarasPuzzle.ADDITION) ?: return response(state, false)
        if (coords != state.world(room.base + ScabarasLiveGeometry.BUTTON_OFFSET)) {
            return response(state, false)
        }
        return response(
            state,
            handled = true,
            events =
                listOf(
                    ScabarasLiveEvent(
                        kind = ScabarasLiveEventKind.ADDITION_TARGET,
                        puzzle = ScabarasPuzzle.ADDITION,
                        value = state.encounter.solutions.additionTarget,
                        secondaryValue = state.encounter.currentAdditionSum,
                    )
                ),
        )
    }

    private fun flipLight(state: State, coords: CoordGrid): ScabarasLiveResult {
        val room = state.activeRoom(ScabarasPuzzle.LIGHTS) ?: return response(state, false)
        val index =
            state.offsetIndex(room, coords, ScabarasLiveGeometry.LIGHT_OFFSETS)
                ?: return response(state, false)
        val result = state.encounter.forceFlipLight(index)
        syncLights(state)
        return actionResponse(state, result)
    }

    private fun stepSequence(
        state: State,
        room: Room,
        tile: Int,
        coords: CoordGrid,
    ): ScabarasLiveResult {
        if (state.sequencePlayback.isNotEmpty()) {
            val result =
                ScabarasActionResult(
                    puzzle = ScabarasPuzzle.SEQUENCE,
                    outcome = ScabarasActionOutcome.NOT_READY,
                    challengeComplete = state.encounter.isComplete,
                )
            return actionResponse(state, result)
        }
        val result = state.encounter.stepSequence(tile)
        putTemporaryLoc(
            state,
            ScabarasRevision240RuntimeLayout.sequenceCache.playerDownLoc,
            coords,
            LocShape.CentrepieceStraight,
            LocAngle.West,
            state.cycle + TEMPORARY_LOC_TICKS,
        )
        when (result.outcome) {
            ScabarasActionOutcome.FAILURE -> clearSequenceEffects(state, room)
            ScabarasActionOutcome.PUZZLE_COMPLETED,
            ScabarasActionOutcome.CHALLENGE_COMPLETED ->
                ScabarasLiveGeometry.MEMORY_OFFSETS.forEach { offset ->
                    putLoc(
                        state,
                        ScabarasRevision240RuntimeLayout.sequenceCache.sequenceDisplayDownLoc,
                        state.world(room.base + offset),
                        LocShape.CentrepieceStraight,
                        LocAngle.West,
                    )
                }
            else -> Unit
        }
        return actionResponse(state, result)
    }

    private fun stepAddition(
        state: State,
        room: Room,
        value: Int,
        coords: CoordGrid,
    ): ScabarasLiveResult {
        val result = state.encounter.stepAddition(value)
        val cache = ScabarasRevision240RuntimeLayout.additionTiles[value - 1]
        when (result.outcome) {
            ScabarasActionOutcome.PROGRESS -> {
                state.additionActiveTiles += coords
                putLoc(
                    state,
                    cache.activeEffectLoc,
                    coords,
                    LocShape.CentrepieceStraight,
                    LocAngle.North,
                )
            }
            ScabarasActionOutcome.FAILURE -> {
                state.additionActiveTiles.forEach {
                    removeLoc(state, it, LocShape.CentrepieceStraight)
                }
                state.additionActiveTiles.clear()
            }
            ScabarasActionOutcome.PUZZLE_COMPLETED,
            ScabarasActionOutcome.CHALLENGE_COMPLETED -> {
                ScabarasLiveGeometry.SUM_OFFSETS.forEach { (tileValue, offsets) ->
                    val tileCache = ScabarasRevision240RuntimeLayout.additionTiles[tileValue - 1]
                    offsets.forEach { offset ->
                        val tile = state.world(room.base + offset)
                        state.additionActiveTiles += tile
                        putLoc(
                            state,
                            tileCache.activeEffectLoc,
                            tile,
                            LocShape.CentrepieceStraight,
                            LocAngle.North,
                        )
                    }
                }
            }
            else -> Unit
        }
        return actionResponse(state, result)
    }

    private fun activateMatchingTile(
        state: State,
        locId: Int,
        coords: CoordGrid,
    ): ScabarasLiveResult {
        val selection = state.matchingSelection(coords) ?: return response(state, false)
        val symbol = state.encounter.matchingSymbols(selection.board)[selection.tile]
        val cache = ScabarasRevision240RuntimeLayout.matchingTiles[symbol]
        if (locId != cache.hiddenButtonLoc) return response(state, false)

        state.encounter.selectedMatchingSymbol(selection.board)?.let { previous ->
            hideMatching(state, selection.board, previous)
        }
        val opposite = selection.board.opposite()
        val oppositeBefore = state.encounter.selectedMatchingSymbol(opposite)
        val result = state.encounter.revealMatching(selection.board, selection.tile)
        when (result.outcome) {
            ScabarasActionOutcome.PROGRESS -> showRevealed(state, selection.board, symbol)
            ScabarasActionOutcome.MATCHED,
            ScabarasActionOutcome.CHALLENGE_COMPLETED -> {
                showMatched(state, selection.board, symbol)
                showMatched(state, opposite, symbol)
            }
            ScabarasActionOutcome.FAILURE -> {
                hideMatching(state, selection.board, symbol)
                oppositeBefore?.let { hideMatching(state, opposite, it) }
            }
            else -> Unit
        }
        return actionResponse(state, result)
    }

    private fun strikeObelisk(state: State, npc: ScabarasLiveNpc): ScabarasLiveResult {
        if (npc.kind != ScabarasLiveNpcKind.OBELISK || npc.visibleId != OBELISK_ACTIVE_NPC) {
            return response(state, handled = false)
        }
        val index = requireNotNull(npc.obeliskIndex)
        val result = state.encounter.strikeObelisk(index)
        if (result.outcome == ScabarasActionOutcome.FAILURE) {
            resetObeliskVisuals(state)
        } else {
            transformNpc(npc, OBELISK_HIT_NPC)
        }
        return actionResponse(state, result, cancelNpcDamage = true)
    }

    private fun killNpc(state: State, npc: ScabarasLiveNpc): ScabarasLiveResult {
        if (npc.kind != ScabarasLiveNpcKind.RANGED_SCARAB) return response(state, false)
        state.npcs.remove(npc)
        return response(
            state,
            handled = true,
            events =
                listOf(
                    ScabarasLiveEvent(
                        kind = ScabarasLiveEventKind.NPC_KILLED,
                        tile = npc.coords,
                        npcKind = npc.kind,
                        npcId = npc.visibleId,
                        npc = npc.native,
                    )
                ),
        )
    }

    private fun displayNextSequenceTile(state: State, events: MutableList<ScabarasLiveEvent>) {
        val tileIndex = state.sequencePlayback.removeFirstOrNull() ?: return
        val room = checkNotNull(state.room(ScabarasPuzzle.SEQUENCE))
        val tile = state.world(room.base + ScabarasLiveGeometry.MEMORY_OFFSETS[tileIndex])
        putTemporaryLoc(
            state,
            ScabarasRevision240RuntimeLayout.sequenceCache.sequenceDisplayDownLoc,
            tile,
            LocShape.CentrepieceStraight,
            LocAngle.West,
            state.cycle + TEMPORARY_LOC_TICKS,
        )
        events +=
            ScabarasLiveEvent(
                kind = ScabarasLiveEventKind.SEQUENCE_FLASH,
                puzzle = ScabarasPuzzle.SEQUENCE,
                tile = tile,
                value = tileIndex,
            )
    }

    private fun spawnRequiredScarabs(state: State, events: MutableList<ScabarasLiveEvent>) {
        if (state.encounter.isComplete || state.cycle < state.nextScarabTick) return
        state.nextScarabTick = state.cycle + SCARAB_RESPAWN_INTERVAL
        val requiredPerLane = (state.encounter.partySize + 1) / 2
        ScabarasLane.entries.forEach { lane ->
            val live =
                state.npcs.count { it.kind == ScabarasLiveNpcKind.RANGED_SCARAB && it.lane == lane }
            if (live >= requiredPerLane) return@forEach
            val firstRoom = state.rooms.first { it.slot.lane == lane && it.slot.firstInLane }
            val secondAnchor = firstRoom.puzzle in state.encounter.completedPuzzles
            val local = ScabarasLiveGeometry.scarabAnchor(lane, secondAnchor)
            val coords = state.world(local)
            putTemporaryLoc(
                state,
                ScabarasRevision240RuntimeLayout.RANGED_SCARAB_SWARM_LOC,
                coords,
                LocShape.CentrepieceStraight,
                LocAngle.West,
                state.cycle + TEMPORARY_LOC_TICKS,
            )
            spawnNpc(
                state = state,
                id = ScabarasRevision240RuntimeLayout.RANGED_SCARAB_NPC,
                coords = coords,
                kind = ScabarasLiveNpcKind.RANGED_SCARAB,
                lane = lane,
                events = events,
            )
        }
    }

    private fun actionResponse(
        state: State,
        result: ScabarasActionResult,
        cancelNpcDamage: Boolean = false,
    ): ScabarasLiveResult {
        val events =
            mutableListOf(
                ScabarasLiveEvent(ScabarasLiveEventKind.ACTION_RESULT, result.puzzle, result)
            )
        result.penalty?.let {
            events +=
                ScabarasLiveEvent(
                    kind = ScabarasLiveEventKind.PENALTY,
                    puzzle = result.puzzle,
                    actionResult = result,
                    value = result.fixedDamage,
                )
        }
        if (
            result.outcome == ScabarasActionOutcome.PUZZLE_COMPLETED ||
                result.outcome == ScabarasActionOutcome.CHALLENGE_COMPLETED
        ) {
            completePuzzlePresentation(state, result.puzzle, events)
        }
        if (result.challengeComplete && !state.challengeSignalled) {
            state.challengeSignalled = true
            removeAllBarriers(state, events)
            events +=
                ScabarasLiveEvent(
                    kind = ScabarasLiveEventKind.CHALLENGE_COMPLETED,
                    puzzle = result.puzzle,
                    actionResult = result,
                )
        }
        return response(state, true, cancelNpcDamage, events)
    }

    private fun completePuzzlePresentation(
        state: State,
        puzzle: ScabarasPuzzle,
        events: MutableList<ScabarasLiveEvent>,
    ) {
        retireCompletedRoomPresentation(state, puzzle)
        barriersUnlockedBy(state, puzzle).forEach { local ->
            val barrier = state.world(local)
            if (removeLoc(state, barrier, LocShape.CentrepieceStraight)) {
                events +=
                    ScabarasLiveEvent(
                        kind = ScabarasLiveEventKind.BARRIER_OPENED,
                        puzzle = puzzle,
                        tile = barrier,
                    )
            }
        }
        events += ScabarasLiveEvent(ScabarasLiveEventKind.PUZZLE_COMPLETED, puzzle)
    }

    private fun retireCompletedRoomPresentation(state: State, puzzle: ScabarasPuzzle) {
        val room = state.room(puzzle) ?: return
        val roomTiles =
            when (puzzle) {
                ScabarasPuzzle.SEQUENCE ->
                    buildSet {
                        add(state.world(room.base + ScabarasLiveGeometry.BUTTON_OFFSET))
                        ScabarasLiveGeometry.MEMORY_OFFSETS.forEach {
                            add(state.world(room.base + it))
                        }
                    }
                ScabarasPuzzle.LIGHTS ->
                    ScabarasLiveGeometry.LIGHT_OFFSETS.mapTo(linkedSetOf()) {
                        state.world(room.base + it)
                    }
                ScabarasPuzzle.OBELISKS ->
                    ScabarasLiveGeometry.OBELISK_OFFSETS.mapTo(linkedSetOf()) {
                        state.world(room.base + it)
                    }
                ScabarasPuzzle.ADDITION ->
                    buildSet {
                        add(state.world(room.base + ScabarasLiveGeometry.BUTTON_OFFSET))
                        ScabarasLiveGeometry.SUM_OFFSETS.values.flatten().forEach {
                            add(state.world(room.base + it))
                        }
                    }
                ScabarasPuzzle.MATCHING -> return
            }

        state.locs
            .filterKeys { it.coords in roomTiles }
            .toList()
            .forEach { (slot, loc) ->
                state.locs.remove(slot)
                state.temporaryLocs.remove(slot)
                world.removeLoc(loc)
            }
        if (puzzle == ScabarasPuzzle.SEQUENCE) {
            state.sequencePlayback.clear()
        }
        if (puzzle == ScabarasPuzzle.ADDITION) {
            state.additionActiveTiles.removeAll(roomTiles)
        }
        if (puzzle == ScabarasPuzzle.OBELISKS) {
            state.npcs
                .filter {
                    it.coords in roomTiles &&
                        it.kind in
                            setOf(ScabarasLiveNpcKind.OBELISK, ScabarasLiveNpcKind.OBELISK_DECOY)
                }
                .toList()
                .forEach { npc ->
                    state.npcs.remove(npc)
                    world.removeNpc(npc)
                }
        }
    }

    private fun barriersUnlockedBy(state: State, puzzle: ScabarasPuzzle): List<ScabarasTile> {
        val room = state.room(puzzle) ?: return emptyList()
        if (room.slot.firstInLane) {
            val oppositeFirst =
                state.rooms.single { it.slot.firstInLane && it.slot.lane != room.slot.lane }
            return listOf(oppositeFirst.barrier)
        }
        if (!state.encounter.canAttempt(ScabarasPuzzle.MATCHING)) return emptyList()
        return state.rooms.filterNot { it.slot.firstInLane }.map { it.barrier }
    }

    private fun removeAllBarriers(state: State, events: MutableList<ScabarasLiveEvent>) {
        (ScabarasLiveGeometry.BRANCH_FIRES + ScabarasLiveGeometry.EXIT_FIRES).forEach { local ->
            val tile = state.world(local)
            if (removeLoc(state, tile, LocShape.CentrepieceStraight)) {
                events +=
                    ScabarasLiveEvent(kind = ScabarasLiveEventKind.BARRIER_OPENED, tile = tile)
            }
        }
    }

    private fun syncLights(state: State) {
        val room = state.room(ScabarasPuzzle.LIGHTS) ?: return
        state.encounter.lights.forEachIndexed { index, enabled ->
            val tile = state.world(room.base + ScabarasLiveGeometry.LIGHT_OFFSETS[index])
            if (enabled) {
                putLoc(
                    state,
                    ScabarasRevision240RuntimeLayout.lightsCache.enabledEffectLoc,
                    tile,
                    LocShape.CentrepieceStraight,
                    LocAngle.North,
                )
            } else {
                removeLoc(state, tile, LocShape.CentrepieceStraight)
            }
        }
    }

    private fun clearSequenceEffects(state: State, room: Room) {
        ScabarasLiveGeometry.MEMORY_OFFSETS.forEach { offset ->
            removeLoc(state, state.world(room.base + offset), LocShape.CentrepieceStraight)
        }
    }

    private fun showRevealed(state: State, board: ScabarasMatchingBoard, symbol: Int) {
        val tile = state.matchingSymbolTile(board, symbol)
        val cache = ScabarasRevision240RuntimeLayout.matchingTiles[symbol]
        putLoc(state, cache.revealedSymbolLoc, tile, LocShape.GroundDecor, LocAngle.North)
    }

    private fun showMatched(state: State, board: ScabarasMatchingBoard, symbol: Int) {
        val tile = state.matchingSymbolTile(board, symbol)
        val cache = ScabarasRevision240RuntimeLayout.matchingTiles[symbol]
        putLoc(state, cache.revealedSymbolLoc, tile, LocShape.GroundDecor, LocAngle.North)
        putLoc(state, cache.matchedEffectLoc, tile, LocShape.CentrepieceStraight, LocAngle.North)
    }

    private fun hideMatching(state: State, board: ScabarasMatchingBoard, symbol: Int) {
        if (symbol in state.encounter.matchingSolvedSymbols) return
        val tile = state.matchingSymbolTile(board, symbol)
        val cache = ScabarasRevision240RuntimeLayout.matchingTiles[symbol]
        putLoc(state, cache.hiddenButtonLoc, tile, LocShape.GroundDecor, LocAngle.North)
        removeLoc(state, tile, LocShape.CentrepieceStraight)
    }

    private fun resetObeliskVisuals(state: State) {
        state.npcs
            .filter { it.kind == ScabarasLiveNpcKind.OBELISK }
            .forEach { transformNpc(it, OBELISK_ACTIVE_NPC) }
    }

    private fun spawnNpc(
        state: State,
        id: Int,
        coords: CoordGrid,
        kind: ScabarasLiveNpcKind,
        lane: ScabarasLane? = null,
        obeliskIndex: Int? = null,
        events: MutableList<ScabarasLiveEvent>,
    ): ScabarasLiveNpc {
        val npc =
            ScabarasLiveNpc(
                instanceId = state.instanceId,
                serial = state.nextNpcSerial++,
                spawnId = id,
                coords = coords,
                kind = kind,
                lane = lane,
                obeliskIndex = obeliskIndex,
            )
        npc.native = world.spawnNpc(npc)
        state.npcs += npc
        events +=
            ScabarasLiveEvent(
                kind = ScabarasLiveEventKind.NPC_SPAWNED,
                tile = coords,
                npcKind = kind,
                npcId = id,
                npc = npc.native,
            )
        return npc
    }

    private fun transformNpc(npc: ScabarasLiveNpc, id: Int) {
        if (npc.visibleId == id) return
        world.transformNpc(npc, id)
        npc.visibleId = id
    }

    private fun putLoc(state: State, id: Int, coords: CoordGrid, shape: LocShape, angle: LocAngle) {
        val slot = ScabarasLocSlot(coords, LocLayerConstants.of(shape.id))
        val current = state.locs[slot]
        if (current?.id == id && current.shape == shape && current.angle == angle) {
            state.temporaryLocs.remove(slot)
            return
        }
        if (current != null) world.removeLoc(current)
        val loc = ScabarasLiveLoc(state.instanceId, id, coords, shape, angle)
        check(world.spawnLoc(loc)) { "Unable to spawn Scabaras loc $loc." }
        state.locs[slot] = loc
        state.temporaryLocs.remove(slot)
    }

    private fun putTemporaryLoc(
        state: State,
        id: Int,
        coords: CoordGrid,
        shape: LocShape,
        angle: LocAngle,
        expiresAt: Int,
    ) {
        putLoc(state, id, coords, shape, angle)
        val slot = ScabarasLocSlot(coords, LocLayerConstants.of(shape.id))
        state.temporaryLocs[slot] = TemporaryLoc(id, expiresAt)
    }

    private fun removeLoc(state: State, coords: CoordGrid, shape: LocShape): Boolean {
        val slot = ScabarasLocSlot(coords, LocLayerConstants.of(shape.id))
        val loc = state.locs.remove(slot) ?: return false
        state.temporaryLocs.remove(slot)
        world.removeLoc(loc)
        return true
    }

    private fun expireTemporaryLocs(state: State) {
        val expired = state.temporaryLocs.filterValues { state.cycle >= it.expiresAt }.keys.toList()
        expired.forEach { slot ->
            val expected = state.temporaryLocs.remove(slot) ?: return@forEach
            val current = state.locs[slot]
            if (current?.id == expected.id) {
                state.locs.remove(slot)
                world.removeLoc(current)
            }
        }
    }

    private fun requiredLocalTiles(rooms: List<Room>): Set<ScabarasTile> = buildSet {
        addAll(ScabarasLiveGeometry.BRANCH_FIRES)
        addAll(ScabarasLiveGeometry.EXIT_FIRES)
        addAll(ScabarasLiveGeometry.SCARAB_ANCHORS.values.flatten())
        ScabarasLiveGeometry.MATCHING_BASES.values.forEach { base ->
            ScabarasLiveGeometry.MATCHING_OFFSETS.forEach { add(base + it) }
        }
        rooms.forEach { room ->
            when (room.puzzle) {
                ScabarasPuzzle.SEQUENCE -> {
                    add(room.base + ScabarasLiveGeometry.BUTTON_OFFSET)
                    ScabarasLiveGeometry.MEMORY_OFFSETS.forEach { add(room.base + it) }
                }
                ScabarasPuzzle.LIGHTS ->
                    ScabarasLiveGeometry.LIGHT_OFFSETS.forEach { add(room.base + it) }
                ScabarasPuzzle.OBELISKS ->
                    ScabarasLiveGeometry.OBELISK_OFFSETS.forEach { add(room.base + it) }
                ScabarasPuzzle.ADDITION -> {
                    add(room.base + ScabarasLiveGeometry.BUTTON_OFFSET)
                    ScabarasLiveGeometry.SUM_OFFSETS.values.flatten().forEach {
                        add(room.base + it)
                    }
                }
                ScabarasPuzzle.MATCHING -> Unit
            }
        }
    }

    private fun response(
        state: State,
        handled: Boolean,
        cancelNpcDamage: Boolean = false,
        events: List<ScabarasLiveEvent> = emptyList(),
    ): ScabarasLiveResult =
        ScabarasLiveResult(
            handled = handled,
            challengeComplete = state.encounter.isComplete,
            cancelNpcDamage = cancelNpcDamage,
            events = events,
        )

    private data class Room(
        val slot: ScabarasSideRoomSlot,
        val puzzle: ScabarasPuzzle,
        val base: ScabarasTile,
        val barrier: ScabarasTile,
    )

    private data class MatchingSelection(val board: ScabarasMatchingBoard, val tile: Int)

    private data class ScabarasLocSlot(val coords: CoordGrid, val layer: Int)

    private data class TemporaryLoc(val id: Int, val expiresAt: Int)

    private data class State(
        val instanceId: InstanceId,
        val encounter: ScabarasEncounter,
        val rooms: List<Room>,
        val localToWorld: Map<ScabarasTile, CoordGrid>,
        val locs: MutableMap<ScabarasLocSlot, ScabarasLiveLoc> = mutableMapOf(),
        val temporaryLocs: MutableMap<ScabarasLocSlot, TemporaryLoc> = mutableMapOf(),
        val npcs: MutableList<ScabarasLiveNpc> = mutableListOf(),
        val sequencePlayback: ArrayDeque<Int> = ArrayDeque(),
        val additionActiveTiles: MutableSet<CoordGrid> = linkedSetOf(),
        var cycle: Int = 0,
        var nextScarabTick: Int,
        var nextNpcSerial: Long = 1L,
        var challengeSignalled: Boolean = false,
    ) {
        fun world(local: ScabarasTile): CoordGrid = localToWorld.getValue(local)

        fun room(puzzle: ScabarasPuzzle): Room? = rooms.firstOrNull { it.puzzle == puzzle }

        fun activeRoom(puzzle: ScabarasPuzzle): Room? =
            room(puzzle)?.takeUnless { puzzle in encounter.completedPuzzles }

        fun offsetIndex(room: Room, coords: CoordGrid, offsets: List<ScabarasTileOffset>): Int? =
            offsets.indexOfFirst { world(room.base + it) == coords }.takeIf { it >= 0 }

        fun additionValue(room: Room, coords: CoordGrid): Int? =
            ScabarasLiveGeometry.SUM_OFFSETS.entries
                .firstOrNull { (_, offsets) -> offsets.any { world(room.base + it) == coords } }
                ?.key

        fun matchingSelection(coords: CoordGrid): MatchingSelection? {
            ScabarasLiveGeometry.MATCHING_BASES.forEach { (board, base) ->
                val tile =
                    ScabarasLiveGeometry.MATCHING_OFFSETS.indexOfFirst {
                        world(base + it) == coords
                    }
                if (tile >= 0) return MatchingSelection(board, tile)
            }
            return null
        }

        fun matchingSymbolTile(board: ScabarasMatchingBoard, symbol: Int): CoordGrid {
            val index = encounter.matchingSymbols(board).indexOf(symbol)
            check(index >= 0)
            val base = ScabarasLiveGeometry.MATCHING_BASES.getValue(board)
            return world(base + ScabarasLiveGeometry.MATCHING_OFFSETS[index])
        }
    }

    private companion object {
        const val FIRE_LOC = 45335
        const val OBELISK_ACTIVE_NPC = 11698
        const val OBELISK_HIT_NPC = 11699
        const val OBELISK_COUNT = 5
        const val SCARAB_INITIAL_DELAY = 30
        const val SCARAB_RESPAWN_INTERVAL = 10
        const val TEMPORARY_LOC_TICKS = 1
    }
}

internal object ScabarasLiveGeometry {
    val PUZZLE_BASES =
        listOf(
            ScabarasTile(19, 35),
            ScabarasTile(36, 35),
            ScabarasTile(19, 23),
            ScabarasTile(36, 23),
        )

    val BUTTON_OFFSET = ScabarasTileOffset(-1, 5)

    val MEMORY_OFFSETS =
        offsetsOf(2 to 4, 1 to 3, 2 to 2, 3 to 5, 3 to 3, 3 to 1, 4 to 4, 4 to 2, 5 to 3)

    val SUM_OFFSETS =
        linkedMapOf(
            1 to offsetsOf(1 to 1, 4 to 4, 5 to 4),
            2 to offsetsOf(2 to 2, 4 to 2, 4 to 5),
            3 to offsetsOf(2 to 5, 3 to 3, 5 to 1),
            4 to offsetsOf(4 to 1, 5 to 2, 3 to 4),
            5 to offsetsOf(3 to 1, 5 to 3),
            6 to offsetsOf(1 to 2, 2 to 3, 2 to 4),
            7 to offsetsOf(1 to 4, 3 to 2, 3 to 5),
            8 to offsetsOf(1 to 3, 4 to 3),
            9 to offsetsOf(1 to 5, 2 to 1, 5 to 5),
        )

    val OBELISK_OFFSETS = offsetsOf(1 to 6, 3 to 6, 5 to 6, 1 to 0, 3 to 0, 5 to 0)

    val LIGHT_OFFSETS = offsetsOf(1 to 1, 1 to 3, 1 to 5, 3 to 5, 5 to 5, 5 to 3, 5 to 1, 3 to 1)

    val MATCHING_BASES =
        mapOf(
            ScabarasMatchingBoard.SOUTH to ScabarasTile(48, 23),
            ScabarasMatchingBoard.NORTH to ScabarasTile(48, 35),
        )

    val MATCHING_OFFSETS =
        offsetsOf(1 to 1, 3 to 1, 5 to 1, 1 to 3, 3 to 3, 5 to 3, 1 to 5, 3 to 5, 5 to 5)

    val SCARAB_ANCHORS =
        mapOf(
            ScabarasLane.SOUTH to listOf(ScabarasTile(15, 26), ScabarasTile(32, 26)),
            ScabarasLane.NORTH to listOf(ScabarasTile(15, 38), ScabarasTile(32, 38)),
        )

    val BRANCH_FIRES =
        listOf(
            ScabarasTile(29, 38),
            ScabarasTile(44, 38),
            ScabarasTile(29, 26),
            ScabarasTile(44, 26),
        )

    val EXIT_FIRES = (24..40).map { z -> ScabarasTile(55, z) }

    fun scarabAnchor(lane: ScabarasLane, secondRoom: Boolean): ScabarasTile =
        SCARAB_ANCHORS.getValue(lane)[if (secondRoom) 1 else 0]

    private fun offsetsOf(vararg values: Pair<Int, Int>): List<ScabarasTileOffset> =
        values.map { (x, z) -> ScabarasTileOffset(x, z) }
}

internal data class ScabarasTileOffset(val x: Int, val z: Int)

private operator fun ScabarasTile.plus(offset: ScabarasTileOffset): ScabarasTile =
    ScabarasTile(x + offset.x, z + offset.z)

private fun ScabarasMatchingBoard.opposite(): ScabarasMatchingBoard =
    if (this == ScabarasMatchingBoard.NORTH) ScabarasMatchingBoard.SOUTH
    else ScabarasMatchingBoard.NORTH

private class RepositoryScabarasLiveWorld(
    private val instances: InstanceManager,
    private val locs: LocRepository,
    private val npcs: NpcRepository,
) : ScabarasLiveWorld {
    override fun resolve(instanceId: InstanceId, local: ScabarasTile): CoordGrid? {
        val session = instances.sessionForId(instanceId) ?: return null
        val roomLocal = ToaInstanceLayout.roomLocal(ToaRoom.SCABARAS, local.x, local.z)
        return instances.localCoord(session, roomLocal)
    }

    override fun spawnLoc(loc: ScabarasLiveLoc): Boolean {
        requireNotNull(ServerCacheManager.getObject(loc.id)) { "Missing Scabaras loc ${loc.id}." }
        val info =
            LocInfo(
                layer = LocLayerConstants.of(loc.shape.id),
                coords = loc.coords,
                entity = LocEntity(loc.id, loc.shape.id, loc.angle.id),
            )
        return locs.add(info, Int.MAX_VALUE)
    }

    override fun removeLoc(loc: ScabarasLiveLoc): Boolean {
        val current =
            locs.findAll(loc.coords).firstOrNull {
                it.id == loc.id && it.shape == loc.shape && it.angle == loc.angle
            } ?: return true
        return locs.del(current, Int.MAX_VALUE)
    }

    override fun spawnNpc(npc: ScabarasLiveNpc): Npc {
        val type =
            requireNotNull(ServerCacheManager.getNpc(npc.spawnId)) {
                "Missing Scabaras npc ${npc.spawnId}."
            }
        return Npc(type, npc.coords).also { native ->
            if (npc.kind == ScabarasLiveNpcKind.RANGED_SCARAB) {
                native.mode = type.defaultMode ?: NpcMode.None
            } else {
                native.mode = NpcMode.None
                native.moveRestrict = MoveRestrict.NoMove
                native.movementLocked = true
                native.abortRoute()
                native.clearInteractionRoute()
            }
            npcs.add(native, Int.MAX_VALUE)
            native.respawns = false
            instances.attachNpc(npc.instanceId, native)
        }
    }

    override fun transformNpc(npc: ScabarasLiveNpc, id: Int) {
        val native = npc.native ?: return
        val type = requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Scabaras npc $id." }
        native.transmog(type, Int.MAX_VALUE)
        if (npc.kind != ScabarasLiveNpcKind.RANGED_SCARAB) {
            native.mode = NpcMode.None
            native.moveRestrict = MoveRestrict.NoMove
            native.movementLocked = true
            native.abortRoute()
            native.clearInteractionRoute()
        }
    }

    override fun removeNpc(npc: ScabarasLiveNpc) {
        val native = npc.native ?: return
        native.mode = null
        native.abortRoute()
        native.clearInteractionRoute()
        if (native.isSlotAssigned) npcs.del(native, Int.MAX_VALUE)
    }
}
