package org.rsmod.content.minigames.toa

import java.util.Collections
import kotlin.math.abs
import org.rsmod.api.instances.InstanceManager
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

internal enum class ToaChallengeStage {
    INACTIVE,
    STARTING,
    ACTIVE,
    COMPLETE,
}

internal enum class ToaBarrierDecision {
    CONFIRM_START,
    START_CUTSCENE,
    JOIN_CUTSCENE,
    EXIT_COMPLETED_CHALLENGE,
    BLOCK_ACTIVE_EXIT,
    ENTRY_BUSY,
    DENY,
}

internal data class ToaChallengeBounds(
    val minLocalX: Int,
    val minLocalZ: Int,
    val maxLocalX: Int,
    val maxLocalZ: Int,
) {
    init {
        require(minLocalX <= maxLocalX)
        require(minLocalZ <= maxLocalZ)
        require(minLocalX in 0 until 64 && maxLocalX in 0 until 64)
        require(minLocalZ in 0 until 64 && maxLocalZ in 0 until 64)
    }

    fun containsLocal(localX: Int, localZ: Int): Boolean =
        localX in minLocalX..maxLocalX && localZ in minLocalZ..maxLocalZ
}

internal data class ToaEntryCutsceneSpec(
    val cameraLocalX: Int,
    val cameraLocalZ: Int,
    val focusLocalX: Int,
    val focusLocalZ: Int,
    val durationTicks: Int,
    val cameraHeight: Int,
    val focusHeight: Int,
)

internal data class ToaResolvedEntryCutscene(
    val room: ToaRoom,
    val camera: CoordGrid,
    val focus: CoordGrid,
    val durationTicks: Int,
    val cameraHeight: Int,
    val focusHeight: Int,
)

/**
 * Resolves the first tile beyond the exact barrier footprint instead of using a room-wide spawn.
 */
internal object ToaBarrierCrossing {
    fun candidates(player: CoordGrid, minX: Int, minZ: Int, maxX: Int, maxZ: Int): List<CoordGrid> {
        require(minX <= maxX)
        require(minZ <= maxZ)
        val centreX = (minX + maxX) / 2
        val centreZ = (minZ + maxZ) / 2
        val horizontal = abs(player.x - centreX) >= abs(player.z - centreZ)
        val distances = listOf(1, 2, 3)
        val offsets = listOf(0, -1, 1)
        return buildList {
            if (horizontal) {
                val baseZ = player.z.coerceIn(minZ, maxZ)
                for (distance in distances) {
                    val x = if (player.x <= minX) maxX + distance else minX - distance
                    for (offset in offsets) add(CoordGrid(x, baseZ + offset, player.level))
                }
            } else {
                val baseX = player.x.coerceIn(minX, maxX)
                for (distance in distances) {
                    val z = if (player.z <= minZ) maxZ + distance else minZ - distance
                    for (offset in offsets) add(CoordGrid(baseX + offset, z, player.level))
                }
            }
        }
    }
}

/**
 * Central lifecycle rules for the four challenge rooms. The bounds are the donor challenge
 * rectangles translated to revision-240 room-local coordinates; all live logic resolves those
 * locals through the OpenRune instance instead of using donor world coordinates.
 */
internal object ToaChallengeRoomRules {
    val rooms: Set<ToaRoom> = TOA_ACTIVE_PATHS.mapTo(linkedSetOf()) { it.challenge }

    private val bounds =
        mapOf(
            ToaRoom.SCABARAS to ToaChallengeBounds(13, 20, 54, 44),
            ToaRoom.APMEKEN to ToaChallengeBounds(21, 19, 43, 45),
            ToaRoom.CRONDIS to ToaChallengeBounds(19, 2, 45, 63),
            ToaRoom.HET to ToaChallengeBounds(22, 19, 42, 45),
        )

    private val cutscenes =
        mapOf(
            ToaRoom.SCABARAS to ToaEntryCutsceneSpec(7, 35, 31, 31, 6, 720, 120),
            ToaRoom.KEPHRI to ToaEntryCutsceneSpec(12, 38, 29, 30, 6, 760, 100),
            ToaRoom.APMEKEN to ToaEntryCutsceneSpec(15, 35, 32, 32, 6, 720, 120),
            ToaRoom.BABA to ToaEntryCutsceneSpec(12, 37, 38, 32, 7, 800, 100),
            ToaRoom.CRONDIS to ToaEntryCutsceneSpec(50, 37, 32, 32, 6, 720, 120),
            ToaRoom.ZEBAK to ToaEntryCutsceneSpec(54, 38, 16, 28, 7, 820, 100),
            ToaRoom.HET to ToaEntryCutsceneSpec(50, 36, 32, 32, 6, 720, 120),
            ToaRoom.WARDENS to ToaEntryCutsceneSpec(31, 60, 23, 34, 8, 900, 120),
        )

    fun bounds(room: ToaRoom): ToaChallengeBounds? = bounds[room]

    fun cutscene(room: ToaRoom): ToaEntryCutsceneSpec? = cutscenes[room]

    fun decide(
        stage: ToaChallengeStage,
        spectating: Boolean,
        inside: Boolean,
        confirmed: Boolean,
        completionExitOpen: Boolean = false,
    ): ToaBarrierDecision {
        if (spectating) return ToaBarrierDecision.DENY
        return when (stage) {
            ToaChallengeStage.INACTIVE ->
                if (confirmed) ToaBarrierDecision.START_CUTSCENE
                else ToaBarrierDecision.CONFIRM_START
            ToaChallengeStage.STARTING -> ToaBarrierDecision.ENTRY_BUSY
            ToaChallengeStage.ACTIVE ->
                when {
                    inside && completionExitOpen -> ToaBarrierDecision.EXIT_COMPLETED_CHALLENGE
                    inside -> ToaBarrierDecision.BLOCK_ACTIVE_EXIT
                    else -> ToaBarrierDecision.JOIN_CUTSCENE
                }
            ToaChallengeStage.COMPLETE -> ToaBarrierDecision.DENY
        }
    }

    fun isInside(
        raid: ToaRaidSession,
        instances: InstanceManager,
        room: ToaRoom,
        tile: CoordGrid,
    ): Boolean {
        val area = bounds(room) ?: return false
        val southWest =
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(room, area.minLocalX, area.minLocalZ),
            ) ?: return false
        val northEast =
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(room, area.maxLocalX, area.maxLocalZ),
            ) ?: return false
        return tile.level == southWest.level &&
            tile.x in southWest.x..northEast.x &&
            tile.z in southWest.z..northEast.z
    }

    fun resolveCutscene(
        raid: ToaRaidSession,
        instances: InstanceManager,
        room: ToaRoom,
    ): ToaResolvedEntryCutscene? {
        val spec = cutscene(room) ?: return null
        val camera =
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(room, spec.cameraLocalX, spec.cameraLocalZ),
            ) ?: return null
        val focus =
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(room, spec.focusLocalX, spec.focusLocalZ),
            ) ?: return null
        return ToaResolvedEntryCutscene(
            room,
            camera,
            focus,
            spec.durationTicks,
            spec.cameraHeight,
            spec.focusHeight,
        )
    }
}

/** Values that become immutable at the successful raid-entry transaction boundary. */
internal class ToaRaidStartSnapshot
private constructor(
    val partySize: Int,
    val raidLevel: Int,
    val invocations: Set<ToaInvocation>,
    val pathLevels: Map<ToaPath, Int>,
    val seed: Long,
    val participantAccountHashes: Set<Long>,
) {
    init {
        require(partySize in 1..TOA_MAX_PARTY_SIZE)
        require(participantAccountHashes.size == partySize)
        require(pathLevels.keys.containsAll(TOA_ACTIVE_PATHS))
    }

    companion object {
        fun create(
            partySize: Int,
            raidLevel: Int,
            invocations: Set<ToaInvocation>,
            pathLevels: Map<ToaPath, Int>,
            seed: Long,
            participantAccountHashes: Set<Long>,
        ): ToaRaidStartSnapshot =
            ToaRaidStartSnapshot(
                partySize = partySize,
                raidLevel = raidLevel,
                invocations = Collections.unmodifiableSet(LinkedHashSet(invocations)),
                pathLevels = Collections.unmodifiableMap(LinkedHashMap(pathLevels)),
                seed = seed,
                participantAccountHashes =
                    Collections.unmodifiableSet(LinkedHashSet(participantAccountHashes)),
            )
    }
}

/**
 * Raid roster ordered by immutable character identity. The engine UID is only a rebindable online
 * handle and never determines raid membership or reward ownership.
 */
internal class ToaRaidRoster {
    private val byAccountHash = linkedMapOf<Long, ToaParticipant>()

    val values: Collection<ToaParticipant>
        get() = byAccountHash.values

    val keys: Set<PlayerUid>
        get() = values.mapTo(linkedSetOf(), ToaParticipant::uid)

    val size: Int
        get() = byAccountHash.size

    operator fun get(uid: PlayerUid): ToaParticipant? = values.firstOrNull { it.uid == uid }

    fun byAccountHash(accountHash: Long): ToaParticipant? = byAccountHash[accountHash]

    fun add(participant: ToaParticipant) {
        require(participant.accountHash !in byAccountHash) {
            "Duplicate ToA account hash ${participant.accountHash}."
        }
        byAccountHash[participant.accountHash] = participant
    }

    /** Preserves original insertion order while changing only the transient engine handle. */
    fun rebind(accountHash: Long, newUid: PlayerUid): PlayerUid? {
        val participant = byAccountHash[accountHash] ?: return null
        require(byAccountHash.values.none { it.accountHash != accountHash && it.uid == newUid }) {
            "ToA UID $newUid is already bound to another raid participant."
        }
        val oldUid = participant.uid
        participant.uid = newUid
        return oldUid
    }

    fun eligible(): List<ToaParticipant> = values.filter(ToaParticipant::rewardEligible)
}

internal object ToaRaidPauseRules {
    fun shouldAdvance(participants: Collection<ToaParticipant>): Boolean =
        participants.any { it.rewardEligible && it.connected }
}

internal object ToaCompletionCredit {
    fun claim(participant: ToaParticipant): Boolean {
        if (!participant.rewardEligible || participant.completionRecorded) return false
        participant.completionRecorded = true
        return true
    }
}

internal fun <V> MutableMap<PlayerUid, V>.rebindKey(oldUid: PlayerUid, newUid: PlayerUid) {
    if (oldUid == newUid || oldUid !in this) return
    val replacement = linkedMapOf<PlayerUid, V>()
    forEach { (uid, value) -> replacement[if (uid == oldUid) newUid else uid] = value }
    clear()
    putAll(replacement)
}

internal fun MutableSet<PlayerUid>.rebindValue(oldUid: PlayerUid, newUid: PlayerUid) {
    if (oldUid == newUid || oldUid !in this) return
    val replacement = mapTo(linkedSetOf()) { if (it == oldUid) newUid else it }
    clear()
    addAll(replacement)
}

internal object ToaDevStartRules {
    fun validExpectedPartySize(expectedPartySize: Int): Boolean =
        expectedPartySize in 1..TOA_MAX_PARTY_SIZE

    fun matchesPartySize(expectedPartySize: Int, actualPartySize: Int): Boolean =
        expectedPartySize == actualPartySize

    fun isLevelZero(raidLevel: Int): Boolean = raidLevel == 0
}

internal class ToaDevSeedOverrides {
    private val seeds = mutableMapOf<Long, Long>()

    fun arm(accountHash: Long, seed: Long) {
        seeds[accountHash] = seed
    }

    fun peek(accountHash: Long): Long? = seeds[accountHash]

    fun consume(accountHash: Long): Long? = seeds.remove(accountHash)
}

internal sealed interface ToaDevRoomRoute {
    data class Challenge(val path: ToaPath) : ToaDevRoomRoute

    data class Boss(val path: ToaPath) : ToaDevRoomRoute

    data object Wardens : ToaDevRoomRoute

    data object Tumeken : ToaDevRoomRoute

    data object Reward : ToaDevRoomRoute
}

internal object ToaDevRoomRoutes {
    fun parse(name: String): ToaDevRoomRoute? =
        when (name.trim().lowercase()) {
            "scabaras" -> ToaDevRoomRoute.Challenge(ToaPath.SCABARAS)
            "apmeken" -> ToaDevRoomRoute.Challenge(ToaPath.APMEKEN)
            "crondis" -> ToaDevRoomRoute.Challenge(ToaPath.CRONDIS)
            "het" -> ToaDevRoomRoute.Challenge(ToaPath.HET)
            "kephri" -> ToaDevRoomRoute.Boss(ToaPath.SCABARAS)
            "baba",
            "ba-ba" -> ToaDevRoomRoute.Boss(ToaPath.APMEKEN)
            "zebak" -> ToaDevRoomRoute.Boss(ToaPath.CRONDIS)
            "akkha" -> ToaDevRoomRoute.Boss(ToaPath.HET)
            "wardens",
            "elidinis" -> ToaDevRoomRoute.Wardens
            "tumeken" -> ToaDevRoomRoute.Tumeken
            "reward" -> ToaDevRoomRoute.Reward
            else -> null
        }
}

internal data class ToaDevStatusSnapshot(
    val seed: Long,
    val lockedPartySize: Int,
    val raidLevel: Int,
    val pathLevels: Map<ToaPath, Int>,
    val room: ToaRoom,
    val completedPaths: Set<ToaPath>,
    val wardenStage: ToaWardenStage,
) {
    fun render(): String {
        val levels =
            TOA_ACTIVE_PATHS.joinToString(",") { path ->
                "${path.name.lowercase()}=${pathLevels[path] ?: 0}"
            }
        val completed =
            TOA_ACTIVE_PATHS.filter(completedPaths::contains)
                .joinToString(",") { it.name.lowercase() }
                .ifEmpty { "none" }
        return "ToA seed=$seed party=$lockedPartySize level=$raidLevel paths=[$levels] " +
            "room=${room.name.lowercase()} completed=[$completed] " +
            "wardens=${wardenStage.name.lowercase()}."
    }
}
