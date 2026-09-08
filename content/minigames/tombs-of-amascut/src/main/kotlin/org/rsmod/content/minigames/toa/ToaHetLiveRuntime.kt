package org.rsmod.content.minigames.toa

import kotlin.math.floor

internal object HetLiveAssets {
    const val MIRROR_ITEM = 27296
    const val EMPTY_LOC = 29733
    const val SEAL_STATE_VARP = 1683

    const val SEAL_NPC = 11706
    const val VULNERABLE_SEAL_NPC = 11707
    const val FALLING_ORB_NPC = 11708

    const val PICK_OR_PLACE_ANIMATION = 827
    const val PUSH_ANIMATION = 810

    const val BEAM_HORIZONTAL_GRAPHIC = 2114
    const val BEAM_VERTICAL_GRAPHIC = 2064
    const val BEAM_SOUTH_EAST_GRAPHIC = 2116
    const val BEAM_SOUTH_WEST_GRAPHIC = 2117
    const val BEAM_NORTH_WEST_GRAPHIC = 2118
    const val BEAM_NORTH_EAST_GRAPHIC = 2119
    const val BEAM_END_GRAPHIC = 2120
    const val SEAL_PROTECTED_GRAPHIC = 2122
    const val SHIELD_SUCCESS_GRAPHIC = 2123
    const val ORB_TELEGRAPH_GRAPHIC = 382
    const val ORB_ACTIVE_GRAPHIC = 379
    const val ROOF_COLLAPSE_GRAPHIC = 60

    const val MIRROR_PICK_SOUND = 2581
    const val MIRROR_PLACE_SOUND = 2739
    const val MIRROR_PUSH_SOUND = 86
    const val MIRROR_ROTATE_SOUND = 6543
    const val BEAM_SOUND = 6540
    const val BEAM_HIT_SOUND = 6546
    const val SHIELD_HIT_SOUND = 6542
    const val WALLS_MOVE_SOUND = 6535
    const val BARRIER_BREAK_SOUND = 6547
    const val ORB_SPAWN_SOUND = 6545
    const val ORB_IMPACT_SOUND = 179
    const val CHALLENGE_COMPLETE_SOUND = 6539

    val CASTER_TILE = HetTile(28, 31)
    val BEAM_START_TILE = HetTile(28, 32)
    val SHIELD_TILE = HetTile(34, 31)
    val SHIELD_HIT_TILE = HetTile(35, 32)
    val SEAL_TILE = HetTile(31, 31)

    val ROOF_COLLAPSE_TILES =
        listOf(
            HetTile(30, 27),
            HetTile(28, 29),
            HetTile(26, 27),
            HetTile(29, 35),
            HetTile(26, 35),
            HetTile(38, 36),
        )

    fun beamGraphic(direction: HetDirection): Int =
        when (direction) {
            HetDirection.NORTH,
            HetDirection.SOUTH -> BEAM_VERTICAL_GRAPHIC
            HetDirection.EAST,
            HetDirection.WEST -> BEAM_HORIZONTAL_GRAPHIC
        }

    fun reflectedBeamGraphic(direction: HetMirrorDirection): Int =
        when (direction) {
            HetMirrorDirection.NORTH_EAST -> BEAM_NORTH_EAST_GRAPHIC
            HetMirrorDirection.SOUTH_EAST -> BEAM_SOUTH_EAST_GRAPHIC
            HetMirrorDirection.SOUTH_WEST -> BEAM_SOUTH_WEST_GRAPHIC
            HetMirrorDirection.NORTH_WEST -> BEAM_NORTH_WEST_GRAPHIC
        }
}

internal enum class HetLivePhase {
    UNINITIALIZED,
    RISING,
    ACTIVE,
    EXPOSING,
    VULNERABLE,
    COMPLETE,
    CLEANED,
}

internal fun HetLivePhase.sealStateVarpValue(): Int =
    when (this) {
        HetLivePhase.RISING,
        HetLivePhase.ACTIVE,
        HetLivePhase.EXPOSING -> HetLiveAssets.SEAL_NPC
        HetLivePhase.VULNERABLE -> HetLiveAssets.VULNERABLE_SEAL_NPC
        HetLivePhase.UNINITIALIZED,
        HetLivePhase.COMPLETE,
        HetLivePhase.CLEANED -> -1
    }

internal enum class HetLiveObjectAction {
    PICK_UP,
    ROTATE_CLOCKWISE,
    ROTATE_ANTICLOCKWISE,
    PUSH,
    CLEAN,
    BREAK,
}

internal enum class HetLiveDamageSource {
    BEAM,
    FALLING_ORB,
}

internal enum class HetLiveRejectReason {
    NOT_INITIALIZED,
    ALREADY_INITIALIZED,
    NOT_ACTIVE,
    INVALID_TARGET,
    INVENTORY_FULL,
    MIRROR_NOT_HELD,
    DESTINATION_BLOCKED,
    PICKAXE_REQUIRED,
    SEAL_PROTECTED,
    ALREADY_COMPLETE,
    CLEANED_UP,
}

internal data class HetRuntimeLoc(
    val locId: Int,
    val tile: HetTile,
    val shape: Int,
    val angle: Int,
)

internal data class HetBeamGraphic(
    val tile: HetTile,
    val spotanimId: Int,
    val delay: Int,
    val height: Int = 255,
)

internal data class HetLiveObjectRequest(
    val actorId: Long,
    val locId: Int,
    val tile: HetTile,
    val action: HetLiveObjectAction,
    val actorTile: HetTile? = null,
    val pushDirection: HetDirection? = null,
    val inventoryHasSpace: Boolean = true,
    val pickaxeTier: HetPickaxeTier? = null,
)

internal data class HetLiveItemOnObjectRequest(
    val actorId: Long,
    val targetLocId: Int,
    val targetTile: HetTile,
    val pickaxeTier: HetPickaxeTier?,
    val miningLevel: Int = 1,
    val rngTicket: Int = 0,
)

internal data class HetLivePlaceMirrorRequest(
    val actorId: Long,
    val tile: HetTile,
    val direction: HetMirrorDirection,
)

internal sealed interface HetLiveEvent {
    data class Rejected(val actorId: Long?, val reason: HetLiveRejectReason) : HetLiveEvent

    data class PhaseChanged(val phase: HetLivePhase, val roomSetIndex: Int) : HetLiveEvent

    data class SpawnLoc(val loc: HetRuntimeLoc) : HetLiveEvent

    data class ReplaceLoc(val previousLocId: Int, val loc: HetRuntimeLoc) : HetLiveEvent

    data class RemoveLoc(val loc: HetRuntimeLoc) : HetLiveEvent

    /** Replaces a puzzle loc with OSRS's explicit empty loc so client solvers clear the tile. */
    data class ClearLoc(val loc: HetRuntimeLoc) : HetLiveEvent

    data class SpawnNpc(val npcId: Int, val tile: HetTile) : HetLiveEvent

    data class TransformNpc(val previousNpcId: Int, val npcId: Int, val tile: HetTile) :
        HetLiveEvent

    data class RemoveNpc(val npcId: Int, val tile: HetTile) : HetLiveEvent

    data class PlayerAnimation(val actorId: Long, val sequenceId: Int) : HetLiveEvent

    /** The manager resolves the revision-240 item's skill_anim/skill_wall_anim parameter. */
    data class PickaxeAnimation(
        val actorId: Long,
        val tier: HetPickaxeTier,
        val wallAnimation: Boolean,
    ) : HetLiveEvent

    data class MirrorItemDelta(val actorId: Long, val itemId: Int, val amount: Int) : HetLiveEvent

    data class RemoveAllMirrorItems(val actorId: Long, val itemId: Int) : HetLiveEvent

    data class Sound(val soundId: Int, val tile: HetTile? = null) : HetLiveEvent

    data class Graphic(
        val spotanimId: Int,
        val tile: HetTile,
        val delay: Int = 0,
        val height: Int = 0,
    ) : HetLiveEvent

    data class Beam(val trace: HetBeamTrace, val graphics: List<HetBeamGraphic>) : HetLiveEvent

    data class DamagePlayer(
        val actorId: Long,
        val amount: Int,
        val source: HetLiveDamageSource,
        val delay: Int = 0,
    ) : HetLiveEvent

    data class BarrierWorked(
        val actorId: Long,
        val tile: HetTile,
        val applied: Int,
        val remaining: Int,
    ) : HetLiveEvent

    data class SealWorked(
        val actorId: Long,
        val applied: Int,
        val hitpoints: Int,
        val maximumHitpoints: Int,
    ) : HetLiveEvent

    data class FallingOrbTelegraph(
        val orbId: Int,
        val tile: HetTile,
        val direction: HetDirection,
        val activationCycle: Int,
    ) : HetLiveEvent

    data class FallingOrbActivated(val orbId: Int, val npcId: Int, val tile: HetTile) :
        HetLiveEvent

    data class FallingOrbMoved(val orbId: Int, val from: HetTile, val to: HetTile) : HetLiveEvent

    data class FallingOrbExploded(val orbId: Int, val tile: HetTile) : HetLiveEvent

    data object ShieldHit : HetLiveEvent

    data class CameraShake(val axis: Int, val random: Int, val amplitude: Int, val rate: Int) :
        HetLiveEvent

    data object CameraReset : HetLiveEvent

    data object ChallengeComplete : HetLiveEvent
}

internal data class HetLiveSnapshot(
    val phase: HetLivePhase,
    val cycle: Int,
    val roomSetIndex: Int,
    val sealHitpoints: Int,
    val sealMaximumHitpoints: Int,
    val activeLocs: Map<HetTile, HetRuntimeLoc>,
    val heldMirrorCounts: Map<Long, Int>,
    val fallingOrbCount: Int,
)

internal enum class HetRoomLocKind(
    val code: String,
    val settledLocId: Int,
    val transitionLocId: Int,
    val shape: Int,
    val breakable: Boolean = false,
    val mirror: Boolean = false,
) {
    REGULAR_A("RA", 45458, 45459, 10),
    REGULAR_B("RB", 45460, 45461, 10),
    BREAKABLE_A("BA", 45462, 45463, 10, breakable = true),
    BREAKABLE_B("BB", 45464, 45465, 10, breakable = true),
    MOVABLE_MIRROR("M", 45455, 45455, 11, mirror = true),
    FIXED_MIRROR("F", 45456, 45456, 11, mirror = true),
    CLEARED_BARRIER("C", 45466, 45466, 10),
}

internal data class HetRoomLocSpec(val kind: HetRoomLocKind, val tile: HetTile, val angle: Int) {
    init {
        require(angle in 0..3)
        require(HetRevision240RuntimeLayout.isPlacementSafe(tile))
    }

    fun runtimeLoc(settled: Boolean, dirty: Boolean = false): HetRuntimeLoc {
        val id =
            when {
                dirty -> HetRevision240RuntimeLayout.mirrorLocs.fixedDirty
                settled -> kind.settledLocId
                else -> kind.transitionLocId
            }
        return HetRuntimeLoc(id, tile, kind.shape, angle)
    }

    fun mirrorDirection(): HetMirrorDirection = HetMirrorDirection.entries[angle]
}

internal data class HetLiveRoomSetSpec(val locs: List<HetRoomLocSpec>) {
    init {
        require(locs.isNotEmpty())
        require(locs.map { it.tile }.distinct().size == locs.size)
        require(locs.count { it.kind == HetRoomLocKind.MOVABLE_MIRROR } in 1..3)
    }
}

/**
 * The donor contributes only server-generated coordinates and orientation. All loc identifiers
 * below resolve through the revision-240 families proven by [HetRevision240RuntimeLayout].
 */
internal object HetLiveRoomSets {
    private val kinds = HetRoomLocKind.entries.associateBy { it.code }

    val all: List<HetLiveRoomSetSpec> =
        listOf(
            decode(
                "BB2@23,29 M1@27,23 BA0@24,29 BA2@25,29 BA0@26,29 BB0@27,29 RB1@30,26 RA1@30,27 RA3@30,28 RA1@30,29 RB3@30,30 F0@26,25 RB2@26,34 RA2@27,34 RA2@28,34 RA2@29,34 RB0@30,34 BB2@28,37 BA0@29,37 BA0@30,37 BA0@31,37 F1@25,38 M3@27,41 RB2@32,27 RA2@33,27 RA2@34,27 RA2@35,27 RB0@36,27 RB2@34,30 RA2@35,30 RA2@36,30 RA2@37,30 RB0@38,30 F3@36,25 BB2@37,36 BA2@38,36 BA2@39,36 BB0@32,37 RB1@34,34 RA3@34,35 RA3@34,36 RA3@34,37 RB3@34,38 F2@37,37 BA0@40,36 BB0@41,36 M2@41,37"
            ),
            decode(
                "RB2@23,33 M1@27,23 BB2@27,28 BA2@28,28 BA2@29,28 BA2@30,28 BB0@31,28 F0@25,29 RA2@24,33 RA2@25,33 RA2@26,33 RB0@27,33 RB1@27,35 RA1@27,36 RA1@27,37 RA3@27,38 RB3@27,39 BB1@31,34 BA3@31,35 BA1@31,36 BA1@31,37 BB3@31,38 F1@29,34 M3@27,41 BB1@38,30 BA1@38,31 RB1@34,27 RA1@34,28 RA3@34,29 RB3@34,30 BB2@35,26 BA2@36,26 BA2@37,26 BA2@38,26 BB0@39,26 F3@35,24 RB1@34,36 RA1@34,37 RA3@34,38 RA1@34,39 RB2@34,35 RA2@35,35 RA2@36,35 RA2@37,35 RB0@38,35 BA1@38,32 BA1@38,33 BB3@38,34 RB3@34,40 F3@41,32 M2@41,37"
            ),
            decode(
                "M1@27,23 RB2@30,30 RA2@31,30 F0@25,29 RB1@26,33 RA3@26,34 RA1@26,35 RA3@26,36 RB3@26,37 RB2@28,37 RA0@29,37 RA0@30,37 RA0@31,37 M3@27,41 RA0@32,30 RA2@33,30 RA0@34,30 RA2@35,30 RA2@36,30 RA0@37,30 RA2@38,30 RB0@39,30 RB1@39,31 RB1@34,24 RA3@34,25 RA3@34,26 RB3@34,27 F3@38,31 RB0@32,37 RA3@39,32 RA1@39,33 RA1@39,34 RB3@39,35 BB1@33,36 BA1@33,37 BA1@33,38 BB3@33,39 BB1@35,36 BA1@35,37 BA1@35,38 BB3@35,39 BB2@32,34 BA0@33,34 BA0@34,34 BA2@35,34 BA0@36,34 BB0@37,34 BB2@37,38 BA0@38,38 BA2@39,38 BB0@40,38 F2@41,36 F1@40,34 M2@41,37"
            ),
            decode(
                "BB2@23,29 M1@27,23 BA2@24,29 BA2@25,29 BA0@26,29 BB0@27,29 BB1@30,26 BA1@30,27 BA3@30,28 BA3@30,29 BB3@30,30 F1@27,25 RB2@26,34 RA0@27,34 RA0@28,34 RA0@29,34 RB0@30,34 RB2@28,37 RA2@29,37 RA0@30,37 RA2@31,37 F1@25,32 M3@27,41 RB2@32,27 RA0@33,27 RA0@34,27 RA0@35,27 RB0@36,27 RB2@34,30 RA0@35,30 RA2@36,30 RA0@37,30 RB0@38,30 F2@36,29 BB2@37,36 BA0@38,36 BA2@39,36 RB0@32,37 RB1@34,34 RA3@34,35 RA3@34,36 RA3@34,37 RB3@34,38 F2@39,33 BA2@40,36 BB0@41,36 M2@41,37"
            ),
            decode(
                "RB2@23,33 M1@27,23 BB2@27,28 BA0@28,28 BA0@29,28 BA0@30,28 BB0@31,28 F0@27,25 RA0@24,33 RA0@25,33 RA0@26,33 RB0@27,33 RB1@27,35 RA1@27,36 RA1@27,37 RA1@27,38 RB3@27,39 BB1@31,34 BA1@31,35 BA1@31,36 BA1@31,37 BB3@31,38 M3@27,41 BB1@38,30 BA1@38,31 RB1@34,27 RA3@34,28 RA3@34,29 RB3@34,30 BB2@35,26 BA0@36,26 BA2@37,26 BA2@38,26 BB0@39,26 F1@36,28 RB1@34,36 RA1@34,37 RA3@34,38 RA3@34,39 RB2@34,35 RA2@35,35 RA2@36,35 RA2@37,35 RB0@38,35 BA1@38,32 BA3@38,33 BB3@38,34 RB3@34,40 F3@40,28 F2@40,38 M2@41,37"
            ),
        )

    private fun decode(encoded: String): HetLiveRoomSetSpec =
        HetLiveRoomSetSpec(
            encoded.split(' ').map { token ->
                val at = token.indexOf('@')
                val head = token.substring(0, at)
                val code = head.dropLast(1)
                val angle = head.last().digitToInt()
                val coordinate = token.substring(at + 1).split(',')
                HetRoomLocSpec(
                    kind = requireNotNull(kinds[code]) { "Unknown Het room loc code: $code" },
                    tile = HetTile(coordinate[0].toInt(), coordinate[1].toInt()),
                    angle = angle,
                )
            }
        )
}

internal data class HetOrbPath(val tile: HetTile, val direction: HetDirection)

internal object HetLiveOrbPaths {
    val all: List<HetOrbPath> =
        "E@23,37 W@22,34 S@24,39 N@22,34 S@25,40 W@23,27 E@22,34 N@23,27 S@30,42 W@22,30 E@25,40 N@22,30 E@30,42 W@25,24 S@23,37 N@25,24 E@24,39 W@27,23 S@35,42 N@27,23 W@35,42 W@31,22 S@37,41 N@31,22 W@37,41 N@34,22 S@40,39 N@37,23 W@40,39 W@37,23 S@39,40 N@39,24 W@39,40 W@39,24 S@41,36 N@40,25 W@41,36 W@40,25 S@42,34 W@40,27 W@42,34 N@40,27"
            .split(' ')
            .map { token ->
                val direction =
                    when (token.first()) {
                        'N' -> HetDirection.NORTH
                        'E' -> HetDirection.EAST
                        'S' -> HetDirection.SOUTH
                        'W' -> HetDirection.WEST
                        else -> error("Unknown Het orb direction in $token")
                    }
                val coordinate = token.substringAfter('@').split(',')
                HetOrbPath(HetTile(coordinate[0].toInt(), coordinate[1].toInt()), direction)
            }
}

private data class HetLiveLocState(
    val roomOwned: Boolean,
    val spec: HetRoomLocSpec?,
    var loc: HetRuntimeLoc,
)

private data class HetLiveOrbState(
    val id: Int,
    var tile: HetTile,
    val direction: HetDirection,
    val activationCycle: Int,
    var active: Boolean = false,
)

private sealed interface HetPendingAction {
    data class ClearBarrier(val tile: HetTile) : HetPendingAction

    data class PushMirror(val actorId: Long, val tile: HetTile, val direction: HetDirection) :
        HetPendingAction

    data class PlaceMirror(
        val actorId: Long,
        val mirrorId: Int,
        val tile: HetTile,
        val direction: HetMirrorDirection,
    ) : HetPendingAction

    data object ShieldImpact : HetPendingAction

    data object RevealSeal : HetPendingAction

    data object ResetCamera : HetPendingAction
}

private class HetLiveRng(seed: Long) {
    private var state = if (seed == 0L) -7046029254386353131L else seed

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        var value = state
        value = value xor (value shl 13)
        value = value xor (value ushr 7)
        value = value xor (value shl 17)
        state = value
        return ((value ushr 1) % bound.toLong()).toInt()
    }
}

internal class ToaHetLiveRuntime(
    private val seed: Long,
    private val partySize: Int = 1,
    private val partyMemberIds: Set<Long> = emptySet(),
    private val groupSealScaling: HetGroupSealScaling = HetGroupSealScaling {
        error("A verified group seal scaling rule must be supplied for multi-player Het.")
    },
    private val hazardDamageMultiplier: Double = 1.0,
    private val roomSets: List<HetLiveRoomSetSpec> = HetLiveRoomSets.all,
    private val orbPaths: List<HetOrbPath> = HetLiveOrbPaths.all,
) {
    companion object {
        const val ROOM_RISE_TICKS = 2
        const val BEAM_CADENCE_TICKS = 9
        const val FIRST_BEAM_DELAY_TICKS = 8
        const val ORB_CADENCE_TICKS = 7
        const val ORB_ACTIVATION_TICKS = 4
        const val BARRIER_WORK = 7
        const val GROUP_SEAL_HP_INCREMENT_REFERENCE = 96

        private const val WALL_CLEAR_DELAY_TICKS = 2
        private const val MIRROR_ACTION_DELAY_TICKS = 1
        private const val SHIELD_IMPACT_DELAY_TICKS = 1
        private const val ROOM_CLEAR_DELAY_TICKS = 2
        private const val CAMERA_RESET_DELAY_TICKS = 4
        private const val MOVABLE_MIRROR_ID_BASE = 10_000
        private const val FIXED_MIRROR_ID_BASE = 20_000
    }

    private val rng = HetLiveRng(seed xor -3335678366873096957L)
    private val activeLocs = linkedMapOf<HetTile, HetLiveLocState>()
    private val roomLocTiles = linkedSetOf<HetTile>()
    private val heldMirrorIds = linkedMapOf<Long, MutableList<Int>>()
    private val orbs = linkedMapOf<Int, HetLiveOrbState>()
    private val pendingActions = sortedMapOf<Int, MutableList<HetPendingAction>>()
    private val pendingBarrierClears = linkedSetOf<HetTile>()
    private val reservedMirrorTiles = linkedSetOf<HetTile>()
    private val pendingMirrorActors = linkedSetOf<Long>()

    private var phase = HetLivePhase.UNINITIALIZED
    private var currentCycle = 0
    private var roomSetIndex = -1
    private var riseUntilCycle = Int.MAX_VALUE
    private var rotateAtCycle = Int.MAX_VALUE
    private var sealVulnerableUntilCycle = Int.MIN_VALUE
    private var nextBeamCycle = Int.MAX_VALUE
    private var nextOrbCycle = Int.MAX_VALUE
    private var nextOrbId = 1
    private var sealNpcId = HetLiveAssets.SEAL_NPC
    private var encounter: HetEncounter? = null

    private val sealMaximumHitpoints = HetSealHitpoints.maximum(partySize, groupSealScaling)
    private var sealHitpoints = sealMaximumHitpoints

    init {
        require(partySize in 1..8)
        require(hazardDamageMultiplier > 0.0)
        require(roomSets.isNotEmpty())
        require(orbPaths.isNotEmpty())
        require(HetLiveAssets.CASTER_TILE == HetTile(3676 - 3648, 5279 - 5248))
        require(HetLiveAssets.SHIELD_TILE == HetTile(3682 - 3648, 5279 - 5248))
        require(HetLiveAssets.SEAL_TILE == HetTile(3679 - 3648, 5279 - 5248))
        validateRevision240Families()
    }

    fun initialize(cycle: Int = 0): List<HetLiveEvent> {
        if (phase == HetLivePhase.CLEANED) {
            return listOf(HetLiveEvent.Rejected(null, HetLiveRejectReason.CLEANED_UP))
        }
        if (phase != HetLivePhase.UNINITIALIZED) {
            return listOf(HetLiveEvent.Rejected(null, HetLiveRejectReason.ALREADY_INITIALIZED))
        }
        require(cycle >= 0)
        currentCycle = cycle
        roomSetIndex = Math.floorMod(seed, roomSets.size.toLong()).toInt()
        nextOrbCycle = cycle + ORB_CADENCE_TICKS

        val events = mutableListOf<HetLiveEvent>()
        events +=
            spawnCoreLoc(
                HetRevision240RuntimeLayout.CASTER_STATUE_LOC,
                HetLiveAssets.CASTER_TILE,
                angle = 1,
            )
        events +=
            spawnCoreLoc(
                HetRevision240RuntimeLayout.SHIELDED_STATUE_LOC,
                HetLiveAssets.SHIELD_TILE,
                angle = 3,
            )
        events += HetLiveEvent.SpawnNpc(HetLiveAssets.SEAL_NPC, HetLiveAssets.SEAL_TILE)
        events +=
            HetLiveEvent.Graphic(HetLiveAssets.SEAL_PROTECTED_GRAPHIC, HetLiveAssets.SEAL_TILE)
        events += installRoom(cycle)
        return events
    }

    fun tick(cycle: Int, playerTiles: Map<Long, HetTile> = emptyMap()): List<HetLiveEvent> {
        if (phase == HetLivePhase.UNINITIALIZED) {
            return listOf(HetLiveEvent.Rejected(null, HetLiveRejectReason.NOT_INITIALIZED))
        }
        if (phase == HetLivePhase.CLEANED) {
            return listOf(HetLiveEvent.Rejected(null, HetLiveRejectReason.CLEANED_UP))
        }
        require(cycle > currentCycle) { "Het runtime ticks must be strictly increasing." }
        currentCycle = cycle
        val events = mutableListOf<HetLiveEvent>()

        events += drainPendingActions(cycle)
        if (phase == HetLivePhase.RISING && cycle >= riseUntilCycle) {
            events += settleRoom()
        }
        if (phase == HetLivePhase.VULNERABLE && cycle >= rotateAtCycle && sealHitpoints > 0) {
            roomSetIndex = (roomSetIndex + 1) % roomSets.size
            events +=
                HetLiveEvent.TransformNpc(
                    sealNpcId,
                    HetLiveAssets.SEAL_NPC,
                    HetLiveAssets.SEAL_TILE,
                )
            sealNpcId = HetLiveAssets.SEAL_NPC
            events +=
                HetLiveEvent.Graphic(HetLiveAssets.SEAL_PROTECTED_GRAPHIC, HetLiveAssets.SEAL_TILE)
            events += installRoom(cycle)
        }

        if (phase == HetLivePhase.ACTIVE && cycle >= nextBeamCycle) {
            while (nextBeamCycle <= cycle) {
                val firingCycle = nextBeamCycle
                nextBeamCycle += BEAM_CADENCE_TICKS
                events += fireBeam(firingCycle, playerTiles)
                if (phase != HetLivePhase.ACTIVE) break
            }
        }

        if (phase != HetLivePhase.COMPLETE) {
            while (nextOrbCycle <= cycle) {
                events += spawnOrbPair(nextOrbCycle)
                nextOrbCycle += ORB_CADENCE_TICKS
            }
            events += tickOrbs(cycle, playerTiles)
        }
        return events
    }

    fun objectInteraction(request: HetLiveObjectRequest): List<HetLiveEvent> {
        interactionRejection(request.actorId)?.let {
            return listOf(it)
        }
        if (request.tile in reservedMirrorTiles) {
            return rejected(request.actorId, HetLiveRejectReason.NOT_ACTIVE)
        }
        val state = activeLocs[request.tile]
        if (state == null || state.loc.locId != request.locId) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        return when (request.action) {
            HetLiveObjectAction.PICK_UP -> pickUpMirror(request, state)
            HetLiveObjectAction.ROTATE_CLOCKWISE -> rotateMirror(request, state, clockwise = true)
            HetLiveObjectAction.ROTATE_ANTICLOCKWISE ->
                rotateMirror(request, state, clockwise = false)
            HetLiveObjectAction.PUSH -> pushMirror(request, state)
            HetLiveObjectAction.CLEAN -> cleanMirror(request, state)
            HetLiveObjectAction.BREAK ->
                itemOnObject(
                    HetLiveItemOnObjectRequest(
                        actorId = request.actorId,
                        targetLocId = request.locId,
                        targetTile = request.tile,
                        pickaxeTier = request.pickaxeTier,
                    )
                )
        }
    }

    fun itemOnObject(request: HetLiveItemOnObjectRequest): List<HetLiveEvent> {
        interactionRejection(request.actorId, allowVulnerable = true)?.let {
            return listOf(it)
        }
        val tier =
            request.pickaxeTier
                ?: return rejected(request.actorId, HetLiveRejectReason.PICKAXE_REQUIRED)
        if (
            request.targetTile == HetLiveAssets.SEAL_TILE &&
                (request.targetLocId in HetRevision240RuntimeLayout.verifiedBrokenSealLocs ||
                    request.targetLocId == sealNpcId)
        ) {
            return mineSeal(request.actorId, tier, request.miningLevel, request.rngTicket)
        }
        if (phase != HetLivePhase.ACTIVE) {
            return rejected(request.actorId, HetLiveRejectReason.NOT_ACTIVE)
        }

        val state = activeLocs[request.targetTile]
        if (request.targetTile in pendingBarrierClears) {
            return rejected(request.actorId, HetLiveRejectReason.NOT_ACTIVE)
        }
        val spec = state?.spec
        if (
            state == null ||
                state.loc.locId != request.targetLocId ||
                spec == null ||
                !spec.kind.breakable
        ) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        val activeEncounter = requireNotNull(encounter)
        val applied = activeEncounter.workBarrier(request.targetTile, tier)
        if (applied <= 0) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        val remaining = activeEncounter.remainingBarrierWork(request.targetTile)
        val events =
            mutableListOf<HetLiveEvent>(
                HetLiveEvent.PickaxeAnimation(request.actorId, tier, wallAnimation = true),
                HetLiveEvent.BarrierWorked(request.actorId, request.targetTile, applied, remaining),
            )
        if (remaining == 0) {
            pendingBarrierClears += request.targetTile
            schedule(
                currentCycle + WALL_CLEAR_DELAY_TICKS,
                HetPendingAction.ClearBarrier(request.targetTile),
            )
        }
        return events
    }

    fun placeMirror(request: HetLivePlaceMirrorRequest): List<HetLiveEvent> {
        interactionRejection(request.actorId)?.let {
            return listOf(it)
        }
        val mirrorId =
            heldMirrorIds[request.actorId]?.firstOrNull()
                ?: return rejected(request.actorId, HetLiveRejectReason.MIRROR_NOT_HELD)
        val activeEncounter = requireNotNull(encounter)
        if (
            request.actorId in pendingMirrorActors ||
                request.tile in reservedMirrorTiles ||
                !activeEncounter.canPlaceMirror(mirrorId, request.tile)
        ) {
            return rejected(request.actorId, HetLiveRejectReason.DESTINATION_BLOCKED)
        }
        pendingMirrorActors += request.actorId
        reservedMirrorTiles += request.tile
        schedule(
            currentCycle + MIRROR_ACTION_DELAY_TICKS,
            HetPendingAction.PlaceMirror(request.actorId, mirrorId, request.tile, request.direction),
        )
        return listOf(
            HetLiveEvent.PlayerAnimation(request.actorId, HetLiveAssets.PICK_OR_PLACE_ANIMATION)
        )
    }

    fun mineSeal(
        actorId: Long,
        tier: HetPickaxeTier,
        miningLevel: Int,
        rngTicket: Int,
    ): List<HetLiveEvent> {
        interactionRejection(actorId, allowVulnerable = true)?.let {
            return listOf(it)
        }
        if (phase != HetLivePhase.VULNERABLE || currentCycle >= sealVulnerableUntilCycle) {
            return rejected(actorId, HetLiveRejectReason.SEAL_PROTECTED)
        }
        val rolled = HetSealWork.range(tier, miningLevel).roll(rngTicket)
        val applied = minOf(sealHitpoints, rolled)
        if (applied <= 0) {
            return rejected(actorId, HetLiveRejectReason.SEAL_PROTECTED)
        }
        sealHitpoints -= applied
        val events =
            mutableListOf<HetLiveEvent>(
                HetLiveEvent.PickaxeAnimation(actorId, tier, wallAnimation = false),
                HetLiveEvent.SealWorked(actorId, applied, sealHitpoints, sealMaximumHitpoints),
            )
        if (sealHitpoints == 0) {
            events += completeChallenge()
        }
        return events
    }

    fun cleanup(): List<HetLiveEvent> {
        if (phase == HetLivePhase.CLEANED) return emptyList()
        val events = mutableListOf<HetLiveEvent>()
        activeLocs.values.forEach { events += HetLiveEvent.RemoveLoc(it.loc) }
        activeLocs.clear()
        roomLocTiles.clear()
        pendingActions.clear()
        pendingBarrierClears.clear()
        reservedMirrorTiles.clear()
        pendingMirrorActors.clear()
        events += HetLiveEvent.CameraReset
        if (phase != HetLivePhase.UNINITIALIZED) {
            events += HetLiveEvent.RemoveNpc(sealNpcId, HetLiveAssets.SEAL_TILE)
        }
        events += removeAllOrbs()
        events += removeMirrorItems()
        phase = HetLivePhase.CLEANED
        events += HetLiveEvent.PhaseChanged(phase, roomSetIndex)
        return events
    }

    fun snapshot(): HetLiveSnapshot =
        HetLiveSnapshot(
            phase = phase,
            cycle = currentCycle,
            roomSetIndex = roomSetIndex,
            sealHitpoints = sealHitpoints,
            sealMaximumHitpoints = sealMaximumHitpoints,
            activeLocs = activeLocs.mapValues { it.value.loc },
            heldMirrorCounts = heldMirrorIds.mapValues { it.value.size },
            fallingOrbCount = orbs.size,
        )

    private fun schedule(cycle: Int, action: HetPendingAction) {
        pendingActions.getOrPut(cycle, ::mutableListOf) += action
    }

    private fun drainPendingActions(cycle: Int): List<HetLiveEvent> {
        val events = mutableListOf<HetLiveEvent>()
        val dueCycles = pendingActions.keys.takeWhile { it <= cycle }.toList()
        dueCycles.forEach { dueCycle ->
            pendingActions.remove(dueCycle).orEmpty().forEach { action ->
                events += commitPendingAction(action, dueCycle)
            }
        }
        return events
    }

    private fun commitPendingAction(action: HetPendingAction, cycle: Int): List<HetLiveEvent> =
        when (action) {
            is HetPendingAction.ClearBarrier -> commitBarrierClear(action)
            is HetPendingAction.PushMirror -> commitMirrorPush(action)
            is HetPendingAction.PlaceMirror -> commitMirrorPlacement(action)
            HetPendingAction.ShieldImpact -> shieldImpact()
            HetPendingAction.RevealSeal -> revealSeal(cycle)
            HetPendingAction.ResetCamera -> listOf(HetLiveEvent.CameraReset)
        }

    private fun commitBarrierClear(action: HetPendingAction.ClearBarrier): List<HetLiveEvent> {
        pendingBarrierClears.remove(action.tile)
        val state = activeLocs[action.tile] ?: return emptyList()
        val spec = state.spec ?: return emptyList()
        if (!spec.kind.breakable) return emptyList()
        val previous = state.loc
        val cleared =
            HetRuntimeLoc(
                HetRevision240RuntimeLayout.CLEARED_BARRIER_LOC,
                action.tile,
                previous.shape,
                previous.angle,
            )
        state.loc = cleared
        return listOf(
            HetLiveEvent.ReplaceLoc(previous.locId, cleared),
            HetLiveEvent.Sound(HetLiveAssets.BARRIER_BREAK_SOUND, action.tile),
        )
    }

    private fun commitMirrorPush(action: HetPendingAction.PushMirror): List<HetLiveEvent> {
        val destination = action.tile.step(action.direction)
        reservedMirrorTiles.remove(action.tile)
        reservedMirrorTiles.remove(destination)
        pendingMirrorActors.remove(action.actorId)
        if (phase != HetLivePhase.ACTIVE) return emptyList()
        val state =
            activeLocs[action.tile]
                ?: return rejected(action.actorId, HetLiveRejectReason.INVALID_TARGET)
        if (!requireNotNull(encounter).pushMirror(action.tile, action.direction)) {
            return rejected(action.actorId, HetLiveRejectReason.DESTINATION_BLOCKED)
        }
        val previous = state.loc
        activeLocs.remove(action.tile)
        roomLocTiles.remove(action.tile)
        val moved = previous.copy(tile = destination)
        state.loc = moved
        activeLocs[destination] = state
        roomLocTiles += destination
        return listOf(
            HetLiveEvent.ClearLoc(previous),
            HetLiveEvent.SpawnLoc(moved),
            HetLiveEvent.Sound(HetLiveAssets.MIRROR_PUSH_SOUND, destination),
        )
    }

    private fun commitMirrorPlacement(action: HetPendingAction.PlaceMirror): List<HetLiveEvent> {
        reservedMirrorTiles.remove(action.tile)
        pendingMirrorActors.remove(action.actorId)
        if (phase != HetLivePhase.ACTIVE) return emptyList()
        val held =
            heldMirrorIds[action.actorId]
                ?: return rejected(action.actorId, HetLiveRejectReason.MIRROR_NOT_HELD)
        if (action.mirrorId !in held) {
            return rejected(action.actorId, HetLiveRejectReason.MIRROR_NOT_HELD)
        }
        if (
            !requireNotNull(encounter).placeMirror(action.mirrorId, action.tile, action.direction)
        ) {
            return rejected(action.actorId, HetLiveRejectReason.DESTINATION_BLOCKED)
        }
        held.remove(action.mirrorId)
        if (held.isEmpty()) heldMirrorIds.remove(action.actorId)
        val loc =
            HetRuntimeLoc(
                HetRevision240RuntimeLayout.mirrorLocs.movableClean,
                action.tile,
                shape = 11,
                angle = action.direction.ordinal,
            )
        activeLocs[action.tile] = HetLiveLocState(roomOwned = true, spec = null, loc = loc)
        roomLocTiles += action.tile
        return listOf(
            HetLiveEvent.MirrorItemDelta(action.actorId, HetLiveAssets.MIRROR_ITEM, -1),
            HetLiveEvent.SpawnLoc(loc),
            HetLiveEvent.Sound(HetLiveAssets.MIRROR_PLACE_SOUND, action.tile),
        )
    }

    private fun shieldImpact(): List<HetLiveEvent> {
        if (phase != HetLivePhase.EXPOSING) return emptyList()
        return listOf(
            HetLiveEvent.ShieldHit,
            HetLiveEvent.Graphic(
                HetLiveAssets.SHIELD_SUCCESS_GRAPHIC,
                HetLiveAssets.SHIELD_HIT_TILE,
                height = 254,
            ),
            HetLiveEvent.Sound(HetLiveAssets.SHIELD_HIT_SOUND, HetLiveAssets.SHIELD_TILE),
            HetLiveEvent.CameraShake(axis = 2, random = 2, amplitude = 10, rate = 10),
        )
    }

    private fun installRoom(cycle: Int): List<HetLiveEvent> {
        require(roomLocTiles.isEmpty())
        sealVulnerableUntilCycle = Int.MIN_VALUE
        val roomSet = roomSets[roomSetIndex]
        val dirtyTiles =
            if (partySize == 1) {
                emptySet()
            } else {
                roomSet.locs
                    .filter { it.kind == HetRoomLocKind.FIXED_MIRROR }
                    .filter { isDirtyMirror(roomSetIndex, it.tile) }
                    .mapTo(linkedSetOf()) { it.tile }
            }
        val fixedMirrors =
            roomSet.locs
                .filter { it.kind == HetRoomLocKind.FIXED_MIRROR }
                .mapIndexed { index, spec ->
                    HetMirror(
                        id = FIXED_MIRROR_ID_BASE + index,
                        tile = spec.tile,
                        direction = spec.mirrorDirection(),
                        movable = false,
                        dirty = spec.tile in dirtyTiles,
                    )
                }
        val movableSpecs = roomSet.locs.filter { it.kind == HetRoomLocKind.MOVABLE_MIRROR }
        val movableIds = movableSpecs.indices.mapTo(linkedSetOf()) { MOVABLE_MIRROR_ID_BASE + it }
        val barriers =
            roomSet.locs.filter { it.kind.breakable }.map { HetBarrier(it.tile, BARRIER_WORK) }

        val bound =
            HetRevision240RuntimeLayout.bindModel(
                HetDynamicModelBinding(
                    beamSource = HetLiveAssets.BEAM_START_TILE,
                    beamDirection = HetDirection.WEST,
                    shield = HetLiveAssets.SHIELD_HIT_TILE,
                    barriers = barriers,
                    fixedMirrors = fixedMirrors,
                    movableMirrorIds = movableIds,
                )
            )
        val regularWalls =
            roomSet.locs
                .filter {
                    it.kind == HetRoomLocKind.REGULAR_A || it.kind == HetRoomLocKind.REGULAR_B
                }
                .mapTo(linkedSetOf()) { it.tile }
        val statueCollision = buildSet {
            addAll(footprint3x3(HetLiveAssets.CASTER_TILE))
            addAll(footprint3x3(HetLiveAssets.SEAL_TILE))
            addAll(footprint3x3(HetLiveAssets.SHIELD_TILE))
            remove(HetLiveAssets.BEAM_START_TILE)
            remove(HetLiveAssets.SHIELD_HIT_TILE)
        }
        val liveLayout = bound.copy(solidTiles = bound.solidTiles + regularWalls + statueCollision)
        encounter = HetEncounter(liveLayout, partySize, groupSealScaling)
        movableSpecs.forEachIndexed { index, spec ->
            check(
                requireNotNull(encounter)
                    .placeMirror(MOVABLE_MIRROR_ID_BASE + index, spec.tile, spec.mirrorDirection())
            ) {
                "Revision-240 Het room set $roomSetIndex has an invalid movable mirror at ${spec.tile}."
            }
        }

        val events = mutableListOf<HetLiveEvent>()
        roomSet.locs.forEach { spec ->
            val dirty = spec.tile in dirtyTiles
            val loc = spec.runtimeLoc(settled = !isWall(spec.kind), dirty = dirty)
            activeLocs[loc.tile] = HetLiveLocState(roomOwned = true, spec = spec, loc = loc)
            roomLocTiles += loc.tile
            events += HetLiveEvent.SpawnLoc(loc)
        }
        phase = HetLivePhase.RISING
        riseUntilCycle = cycle + ROOM_RISE_TICKS
        rotateAtCycle = Int.MAX_VALUE
        nextBeamCycle = Int.MAX_VALUE
        events += HetLiveEvent.Sound(HetLiveAssets.WALLS_MOVE_SOUND, HetLiveAssets.SEAL_TILE)
        events += HetLiveEvent.PhaseChanged(phase, roomSetIndex)
        return events
    }

    private fun settleRoom(): List<HetLiveEvent> {
        val events = mutableListOf<HetLiveEvent>()
        roomLocTiles.forEach { tile ->
            val state = activeLocs[tile] ?: return@forEach
            val spec = state.spec ?: return@forEach
            if (!isWall(spec.kind)) return@forEach
            val previous = state.loc
            val settled = spec.runtimeLoc(settled = true)
            state.loc = settled
            events += HetLiveEvent.ReplaceLoc(previous.locId, settled)
        }
        phase = HetLivePhase.ACTIVE
        nextBeamCycle = currentCycle + FIRST_BEAM_DELAY_TICKS
        events += HetLiveEvent.PhaseChanged(phase, roomSetIndex)
        return events
    }

    private fun fireBeam(cycle: Int, playerTiles: Map<Long, HetTile>): List<HetLiveEvent> {
        val activeEncounter = requireNotNull(encounter)
        val trace = traceLiveBeam(activeEncounter)
        val events = mutableListOf<HetLiveEvent>()
        events += HetLiveEvent.Sound(HetLiveAssets.BEAM_SOUND, HetLiveAssets.BEAM_START_TILE)
        events += HetLiveEvent.Beam(trace, renderBeam(trace, activeEncounter))
        val beamTiles = trace.tiles.toHashSet()
        playerTiles.forEach { (playerId, tile) ->
            if (tile !in beamTiles) return@forEach
            events +=
                HetLiveEvent.DamagePlayer(
                    playerId,
                    hazardDamage(),
                    HetLiveDamageSource.BEAM,
                    delay = 1,
                )
        }
        if (trace.end == HetBeamEnd.HIT_SHIELD) {
            phase = HetLivePhase.EXPOSING
            nextBeamCycle = Int.MAX_VALUE
            schedule(cycle + SHIELD_IMPACT_DELAY_TICKS, HetPendingAction.ShieldImpact)
            schedule(cycle + ROOM_CLEAR_DELAY_TICKS, HetPendingAction.RevealSeal)
            schedule(cycle + CAMERA_RESET_DELAY_TICKS, HetPendingAction.ResetCamera)
            events += HetLiveEvent.PhaseChanged(phase, roomSetIndex)
        } else {
            events += HetLiveEvent.Sound(HetLiveAssets.BEAM_HIT_SOUND, trace.tiles.lastOrNull())
        }
        return events
    }

    private fun revealSeal(cycle: Int): List<HetLiveEvent> {
        if (phase != HetLivePhase.EXPOSING) return emptyList()
        val events = mutableListOf<HetLiveEvent>()
        phase = HetLivePhase.VULNERABLE
        rotateAtCycle = cycle + HetEncounter.VULNERABILITY_TICKS
        sealVulnerableUntilCycle = rotateAtCycle
        events += clearRoomLocs()
        events += removeMirrorItems()
        HetLiveAssets.ROOF_COLLAPSE_TILES.forEach {
            events += HetLiveEvent.Graphic(HetLiveAssets.ROOF_COLLAPSE_GRAPHIC, it, delay = 15)
        }
        events +=
            HetLiveEvent.TransformNpc(
                sealNpcId,
                HetLiveAssets.VULNERABLE_SEAL_NPC,
                HetLiveAssets.SEAL_TILE,
            )
        sealNpcId = HetLiveAssets.VULNERABLE_SEAL_NPC
        events += HetLiveEvent.PhaseChanged(phase, roomSetIndex)
        return events
    }

    private fun pickUpMirror(
        request: HetLiveObjectRequest,
        state: HetLiveLocState,
    ): List<HetLiveEvent> {
        if (state.loc.locId != HetRevision240RuntimeLayout.mirrorLocs.movableClean) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        if (!request.inventoryHasSpace) {
            return rejected(request.actorId, HetLiveRejectReason.INVENTORY_FULL)
        }
        val activeEncounter = requireNotNull(encounter)
        val mirror =
            activeEncounter.mirrorAt(request.tile)
                ?: return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        if (!activeEncounter.pickUpMirror(request.tile)) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        heldMirrorIds.getOrPut(request.actorId, ::mutableListOf) += mirror.id
        activeLocs.remove(request.tile)
        roomLocTiles.remove(request.tile)
        return listOf(
            HetLiveEvent.PlayerAnimation(request.actorId, HetLiveAssets.PICK_OR_PLACE_ANIMATION),
            HetLiveEvent.ClearLoc(state.loc),
            HetLiveEvent.MirrorItemDelta(request.actorId, HetLiveAssets.MIRROR_ITEM, 1),
            HetLiveEvent.Sound(HetLiveAssets.MIRROR_PICK_SOUND, request.tile),
        )
    }

    private fun rotateMirror(
        request: HetLiveObjectRequest,
        state: HetLiveLocState,
        clockwise: Boolean,
    ): List<HetLiveEvent> {
        if (state.loc.locId != HetRevision240RuntimeLayout.mirrorLocs.movableClean) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        if (!requireNotNull(encounter).rotateMirror(request.tile, clockwise)) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        val previous = state.loc
        val angle = Math.floorMod(previous.angle + if (clockwise) 1 else -1, 4)
        val rotated = previous.copy(angle = angle)
        state.loc = rotated
        return listOf(
            HetLiveEvent.ReplaceLoc(previous.locId, rotated),
            HetLiveEvent.Sound(HetLiveAssets.MIRROR_ROTATE_SOUND, request.tile),
        )
    }

    private fun pushMirror(
        request: HetLiveObjectRequest,
        state: HetLiveLocState,
    ): List<HetLiveEvent> {
        if (state.loc.locId != HetRevision240RuntimeLayout.mirrorLocs.movableClean) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        val direction =
            request.pushDirection
                ?: return rejected(request.actorId, HetLiveRejectReason.DESTINATION_BLOCKED)
        val destination = request.tile.step(direction)
        val activeEncounter = requireNotNull(encounter)
        if (
            destination in reservedMirrorTiles ||
                request.actorId in pendingMirrorActors ||
                !activeEncounter.canPushMirror(request.tile, direction)
        ) {
            return rejected(request.actorId, HetLiveRejectReason.DESTINATION_BLOCKED)
        }
        reservedMirrorTiles += request.tile
        reservedMirrorTiles += destination
        pendingMirrorActors += request.actorId
        schedule(
            currentCycle + MIRROR_ACTION_DELAY_TICKS,
            HetPendingAction.PushMirror(request.actorId, request.tile, direction),
        )
        return listOf(HetLiveEvent.PlayerAnimation(request.actorId, HetLiveAssets.PUSH_ANIMATION))
    }

    private fun cleanMirror(
        request: HetLiveObjectRequest,
        state: HetLiveLocState,
    ): List<HetLiveEvent> {
        if (state.loc.locId != HetRevision240RuntimeLayout.mirrorLocs.fixedDirty) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        if (!requireNotNull(encounter).cleanMirror(request.tile)) {
            return rejected(request.actorId, HetLiveRejectReason.INVALID_TARGET)
        }
        val previous = state.loc
        val clean = previous.copy(locId = HetRevision240RuntimeLayout.mirrorLocs.fixedClean)
        state.loc = clean
        return listOf(
            HetLiveEvent.PlayerAnimation(request.actorId, HetLiveAssets.PICK_OR_PLACE_ANIMATION),
            HetLiveEvent.ReplaceLoc(previous.locId, clean),
            HetLiveEvent.Sound(HetLiveAssets.MIRROR_PICK_SOUND, request.tile),
        )
    }

    private fun spawnOrbPair(cycle: Int): List<HetLiveEvent> {
        val events = mutableListOf<HetLiveEvent>()
        repeat(2) {
            val path = orbPaths[rng.nextInt(orbPaths.size)]
            val id = nextOrbId++
            val orb = HetLiveOrbState(id, path.tile, path.direction, cycle + ORB_ACTIVATION_TICKS)
            orbs[id] = orb
            events +=
                HetLiveEvent.FallingOrbTelegraph(id, path.tile, path.direction, orb.activationCycle)
            events += HetLiveEvent.Graphic(HetLiveAssets.ORB_TELEGRAPH_GRAPHIC, path.tile)
        }
        return events
    }

    private fun tickOrbs(cycle: Int, playerTiles: Map<Long, HetTile>): List<HetLiveEvent> {
        val events = mutableListOf<HetLiveEvent>()
        val remove = mutableListOf<Int>()
        orbs.values.forEach { orb ->
            if (!orb.active) {
                if (cycle >= orb.activationCycle) {
                    orb.active = true
                    events +=
                        HetLiveEvent.FallingOrbActivated(
                            orb.id,
                            HetLiveAssets.FALLING_ORB_NPC,
                            orb.tile,
                        )
                    events += HetLiveEvent.Graphic(HetLiveAssets.ORB_ACTIVE_GRAPHIC, orb.tile)
                    events += HetLiveEvent.Sound(HetLiveAssets.ORB_SPAWN_SOUND, orb.tile)
                }
                return@forEach
            }
            val hitPlayers = playerTiles.filterValues { it == orb.tile }.keys
            if (hitPlayers.isNotEmpty()) {
                hitPlayers.forEach {
                    events +=
                        HetLiveEvent.DamagePlayer(
                            it,
                            hazardDamage(),
                            HetLiveDamageSource.FALLING_ORB,
                        )
                }
                events += explodeOrb(orb)
                remove += orb.id
                return@forEach
            }
            val destination = orb.tile.step(orb.direction)
            if (destination !in HetRevision240RuntimeLayout.terrainFloorTiles) {
                events += explodeOrb(orb)
                remove += orb.id
                return@forEach
            }
            val previous = orb.tile
            orb.tile = destination
            events += HetLiveEvent.FallingOrbMoved(orb.id, previous, destination)
        }
        remove.forEach(orbs::remove)
        return events
    }

    private fun explodeOrb(orb: HetLiveOrbState): List<HetLiveEvent> =
        listOf(
            HetLiveEvent.FallingOrbExploded(orb.id, orb.tile),
            HetLiveEvent.Graphic(HetLiveAssets.ORB_ACTIVE_GRAPHIC, orb.tile),
            HetLiveEvent.Sound(HetLiveAssets.ORB_IMPACT_SOUND, orb.tile),
        )

    private fun renderBeam(
        trace: HetBeamTrace,
        activeEncounter: HetEncounter,
    ): List<HetBeamGraphic> {
        var previous = activeEncounter.layout.source
        return trace.tiles.mapIndexed { index, tile ->
            val direction = directionBetween(previous, tile)
            val mirror = activeEncounter.mirrorAt(tile)
            val spotanim =
                when {
                    index == trace.tiles.lastIndex -> HetLiveAssets.BEAM_END_GRAPHIC
                    mirror != null &&
                        !mirror.dirty &&
                        mirror.direction.reflect(direction) != null ->
                        HetLiveAssets.reflectedBeamGraphic(mirror.direction)
                    else -> HetLiveAssets.beamGraphic(direction)
                }
            previous = tile
            val delay =
                if (index == trace.tiles.lastIndex && index > 0) {
                    index
                } else index + 1
            HetBeamGraphic(tile, spotanim, delay)
        }
    }

    /**
     * Het's shield is a 3x3 loc. The pure encounter has a single-tile target, so the live adapter
     * traces against the complete revision-240 loc footprint while reading all mutable mirror and
     * barrier state from [HetEncounter].
     */
    private fun traceLiveBeam(activeEncounter: HetEncounter, stepLimit: Int = 512): HetBeamTrace {
        var tile = activeEncounter.layout.source
        var direction = activeEncounter.layout.sourceDirection
        val traversed = mutableListOf<HetTile>()
        val visited = mutableSetOf<Pair<HetTile, HetDirection>>()
        val shieldFootprint = footprint3x3(HetLiveAssets.SHIELD_TILE)
        repeat(stepLimit) {
            if (!visited.add(tile to direction)) {
                return HetBeamTrace(traversed, HetBeamEnd.LOOP)
            }
            val next = tile.step(direction)
            if (!activeEncounter.layout.bounds.contains(next)) {
                return HetBeamTrace(traversed, HetBeamEnd.LEFT_ROOM)
            }
            tile = next
            traversed += tile
            if (tile in shieldFootprint) {
                return HetBeamTrace(traversed, HetBeamEnd.HIT_SHIELD)
            }
            if (
                tile in pendingBarrierClears ||
                    tile in activeEncounter.layout.solidTiles ||
                    activeEncounter.remainingBarrierWork(tile) > 0
            ) {
                return HetBeamTrace(traversed, HetBeamEnd.BLOCKED)
            }
            val mirror = activeEncounter.mirrorAt(tile) ?: return@repeat
            if (mirror.dirty) {
                return HetBeamTrace(traversed, HetBeamEnd.BLOCKED)
            }
            direction =
                mirror.direction.reflect(direction)
                    ?: return HetBeamTrace(traversed, HetBeamEnd.BLOCKED)
        }
        return HetBeamTrace(traversed, HetBeamEnd.STEP_LIMIT)
    }

    private fun completeChallenge(): List<HetLiveEvent> {
        if (phase == HetLivePhase.COMPLETE) return emptyList()
        val events = mutableListOf<HetLiveEvent>()
        events += clearRoomLocs()
        events += removeAllOrbs()
        events += removeMirrorItems()
        phase = HetLivePhase.COMPLETE
        nextBeamCycle = Int.MAX_VALUE
        nextOrbCycle = Int.MAX_VALUE
        rotateAtCycle = Int.MAX_VALUE
        sealVulnerableUntilCycle = Int.MIN_VALUE
        events +=
            HetLiveEvent.Sound(HetLiveAssets.CHALLENGE_COMPLETE_SOUND, HetLiveAssets.SEAL_TILE)
        events += HetLiveEvent.ChallengeComplete
        events += HetLiveEvent.PhaseChanged(phase, roomSetIndex)
        return events
    }

    private fun clearRoomLocs(): List<HetLiveEvent> {
        val events = mutableListOf<HetLiveEvent>()
        roomLocTiles.toList().forEach { tile ->
            val state = activeLocs.remove(tile) ?: return@forEach
            events += HetLiveEvent.ClearLoc(state.loc)
        }
        roomLocTiles.clear()
        return events
    }

    private fun removeAllOrbs(): List<HetLiveEvent> {
        val events = orbs.values.map { HetLiveEvent.FallingOrbExploded(it.id, it.tile) }
        orbs.clear()
        return events
    }

    private fun removeMirrorItems(): List<HetLiveEvent> {
        val players = partyMemberIds + heldMirrorIds.keys
        heldMirrorIds.clear()
        return players.map { HetLiveEvent.RemoveAllMirrorItems(it, HetLiveAssets.MIRROR_ITEM) }
    }

    private fun spawnCoreLoc(locId: Int, tile: HetTile, angle: Int): HetLiveEvent.SpawnLoc {
        val loc = HetRuntimeLoc(locId, tile, shape = 10, angle = angle)
        activeLocs[tile] = HetLiveLocState(roomOwned = false, spec = null, loc = loc)
        return HetLiveEvent.SpawnLoc(loc)
    }

    private fun interactionRejection(
        actorId: Long,
        allowVulnerable: Boolean = false,
    ): HetLiveEvent.Rejected? =
        when {
            phase == HetLivePhase.UNINITIALIZED ->
                HetLiveEvent.Rejected(actorId, HetLiveRejectReason.NOT_INITIALIZED)
            phase == HetLivePhase.CLEANED ->
                HetLiveEvent.Rejected(actorId, HetLiveRejectReason.CLEANED_UP)
            phase == HetLivePhase.COMPLETE ->
                HetLiveEvent.Rejected(actorId, HetLiveRejectReason.ALREADY_COMPLETE)
            phase != HetLivePhase.ACTIVE &&
                !(allowVulnerable && phase == HetLivePhase.VULNERABLE) ->
                HetLiveEvent.Rejected(actorId, HetLiveRejectReason.NOT_ACTIVE)
            else -> null
        }

    private fun rejected(actorId: Long, reason: HetLiveRejectReason) =
        listOf<HetLiveEvent>(HetLiveEvent.Rejected(actorId, reason))

    private fun isDirtyMirror(roomIndex: Int, tile: HetTile): Boolean {
        var mixed = seed xor (roomIndex.toLong() shl 32)
        mixed = mixed xor (tile.x.toLong() shl 16) xor tile.z.toLong()
        mixed = (mixed xor (mixed ushr 33)) * -49064778989728563L
        mixed = (mixed xor (mixed ushr 33)) * -4265267296055464877L
        return (mixed xor (mixed ushr 33)) and 1L == 0L
    }

    private fun hazardDamage(): Int = floor(5.0 * hazardDamageMultiplier).toInt() + rng.nextInt(4)

    private fun validateRevision240Families() {
        check(HetRevision240RuntimeLayout.mirrorLocs == HetMirrorCacheLocs(45455, 45456, 45457))
        check(
            HetRevision240RuntimeLayout.barrierLocs ==
                listOf(
                    HetBarrierCachePair(45458, 45459, playerBreakable = false),
                    HetBarrierCachePair(45460, 45461, playerBreakable = false),
                    HetBarrierCachePair(45462, 45463, playerBreakable = true),
                    HetBarrierCachePair(45464, 45465, playerBreakable = true),
                )
        )
        check(HetRevision240RuntimeLayout.CLEARED_BARRIER_LOC == 45466)
    }

    private fun footprint3x3(southWest: HetTile): Set<HetTile> = buildSet {
        for (x in southWest.x..southWest.x + 2) {
            for (z in southWest.z..southWest.z + 2) {
                add(HetTile(x, z))
            }
        }
    }

    private fun directionBetween(from: HetTile, to: HetTile): HetDirection =
        HetDirection.entries.single { from.step(it) == to }

    private fun isWall(kind: HetRoomLocKind): Boolean =
        kind == HetRoomLocKind.REGULAR_A ||
            kind == HetRoomLocKind.REGULAR_B ||
            kind == HetRoomLocKind.BREAKABLE_A ||
            kind == HetRoomLocKind.BREAKABLE_B
}

private fun HetDirection.opposite(): HetDirection =
    when (this) {
        HetDirection.NORTH -> HetDirection.SOUTH
        HetDirection.EAST -> HetDirection.WEST
        HetDirection.SOUTH -> HetDirection.NORTH
        HetDirection.WEST -> HetDirection.EAST
    }
