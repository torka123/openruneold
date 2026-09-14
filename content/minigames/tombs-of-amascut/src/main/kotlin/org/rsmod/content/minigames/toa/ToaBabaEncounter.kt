package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

internal enum class BabaEvidenceConfidence {
    EXACT,
    HIGH_CONFIDENCE,
    TRACE_REQUIRED,
}

internal data class BabaTunedValue<T>(
    val value: T,
    val units: String,
    val evidence: String,
    val confidence: BabaEvidenceConfidence,
)

/** Native revision-240 Ba-Ba assets. Raw IDs must not appear in encounter logic. */
internal object BabaAssets {
    const val BABA = 11778
    const val BABA_COFFIN = 11779
    const val BABA_DIGGING = 11780
    const val BABOON = 11781
    const val BOULDER = 11782
    const val WEAK_BOULDER = 11783
    const val RUBBLE_LARGE = 11784
    const val RUBBLE_MEDIUM = 11785
    const val RUBBLE_SMALL = 11786
    const val RUBBLE_TINY = 11787
    const val SARCOPHAGUS = 11788

    const val MANDRILL_WALK = 9737
    const val MANDRILL_WALK_BACKWARDS = 9738
    const val MANDRILL_WALK_SMALL = 9739
    const val MANDRILL_WALK_SMALL_BACKWARDS = 9740
    const val MANDRILL_IDLE = 9741
    const val MANDRILL_ATTACK_MELEE = 9742
    const val BABA_ATTACK_MELEE = 9743
    const val MANDRILL_ATTACK_RANGED_1 = 9744
    const val MANDRILL_ATTACK_RANGED_2 = 9745
    const val MANDRILL_ATTACK_MAGIC_1 = 9746
    const val MANDRILL_ATTACK_MAGIC_2 = 9747
    const val MANDRILL_SPECIAL_JUMP = 9748
    const val MANDRILL_SPECIAL_RANGED = 9749
    const val MANDRILL_SPECIAL_IDLE = 9750
    const val MANDRILL_SPECIAL_END = 9751
    const val MANDRILL_SPAWN_1 = 9752
    const val MANDRILL_SPAWN_2 = 9753
    const val MANDRILL_DESPAWN_1 = 9754
    const val MANDRILL_DESPAWN_2 = 9755
    const val MANDRILL_EXPLODE = 9756
    const val MANDRILL_EXPLODE_SPOT_SEQUENCE = 9757
    const val BANANA_FALL = 9758
    const val BOULDER_ROLL = 9518
    const val ROCK_FALL_FASTER = 9805
    const val ROCK_FALL_FASTEST = 9806

    const val WALK = MANDRILL_WALK
    const val IDLE = MANDRILL_IDLE
    const val MELEE_ATTACK = BABA_ATTACK_MELEE
    const val BABOON_RANGED = MANDRILL_ATTACK_RANGED_2
    const val BABOON_DEFEND = MANDRILL_IDLE
    const val BABOON_DEATH = MANDRILL_DESPAWN_2
    const val ROCKFALL_CAST = BABA_ATTACK_MELEE
    const val ROCK_THROW_CAST = MANDRILL_ATTACK_RANGED_1
    const val SHOCKWAVE = BABA_ATTACK_MELEE
    const val ROLLING_STONES_START = MANDRILL_SPECIAL_JUMP
    const val ROLLING_STONES_CAST = MANDRILL_SPECIAL_RANGED
    const val ROLLING_STONES_IDLE = MANDRILL_SPECIAL_IDLE
    const val ROLLING_STONES_END = MANDRILL_SPECIAL_END
    const val RUBBLE_FALL = ROCK_FALL_FASTER
    const val BABOON_RANGED_TRAVEL = 2242
    const val BABOON_RANGED_TRAVEL_ALT = 2243
    const val BABA_RANGED_TRAVEL = 2244
    const val BOULDER_LAUNCH = 2245
    const val SARCOPHAGUS_ENERGY = 2246
    const val SARCOPHAGUS_SKULL = 2246
    const val BABOON_MAGIC = 2247
    const val BABOON_MAGIC_ALT = 2248
    const val EXPLOSION = 2249
    const val ROCKFALL = 2250
    const val ROCKFALL_FASTER = 2251
    const val ROCKFALL_FASTEST = 2252
    const val SHOCKWAVE_WARNING = 2257

    const val CLOSED_SARCOPHAGUS_LOC = 45752
    const val OPEN_SARCOPHAGUS_LOC = 45753
    const val COMPLETION_TELEPORT_LOC = 45754
    const val BANANA_PILE_LOC = 45755
    const val THRONE_LOC = 45756

    val npcIds = (BABA..SARCOPHAGUS).toSet()
    val sequenceIds =
        (MANDRILL_WALK..BANANA_FALL).toSet() +
            setOf(BOULDER_ROLL, ROCK_FALL_FASTER, ROCK_FALL_FASTEST)
    val spotanimIds = (BABOON_RANGED_TRAVEL..ROCKFALL_FASTEST).toSet() + SHOCKWAVE_WARNING
    val locIds =
        setOf(
            CLOSED_SARCOPHAGUS_LOC,
            OPEN_SARCOPHAGUS_LOC,
            COMPLETION_TELEPORT_LOC,
            BANANA_PILE_LOC,
            THRONE_LOC,
        )
}

internal object BabaTuning {
    private fun <T> exact(v: T, u: String, e: String) =
        BabaTunedValue(v, u, e, BabaEvidenceConfidence.EXACT)

    private fun <T> high(v: T, u: String, e: String) =
        BabaTunedValue(v, u, e, BabaEvidenceConfidence.HIGH_CONFIDENCE)

    private fun <T> trace(v: T, u: String, e: String) =
        BabaTunedValue(v, u, e, BabaEvidenceConfidence.TRACE_REQUIRED)

    val BASE_HITPOINTS = exact(380, "hitpoints", "revision-240 npc definition 11778")
    val BASE_COMBAT_LEVEL = exact(359, "combat level", "revision-240 npc definition 11778")
    val ATTACK_INTERVAL = high(6, "game ticks", "current OSRS room cadence")
    val BASE_MELEE_MAX_HIT = exact(24, "damage", "current OSRS Ba-Ba definition")
    val MELEE_ATTACK_RANGE = exact(1, "tiles", "revision-240 Ba-Ba NPC definition")
    val ROOM_CHASE_RANGE =
        exact(64, "tiles from spawn", "instance-scoped Ba-Ba room target retention")
    val MELEE_PRAYER_REDUCTION = exact(100, "percent", "Jagex Summer Sweep-Up 2025")
    val QUIET_PRAYERS_REDUCTION = high(75, "percent", "invocation protection reduction")
    val SPECIAL_AFTER_AUTOS = exact(2, "emitted attacks", "current OSRS Ba-Ba special cycle")
    val INTRO_TICKS = trace(3, "game ticks", "requires current spawn trace")
    val ROCKFALL_WARNING_TICKS =
        high(6, "game ticks", "RsProx ticks 3233-3239; current path-level zero baseline")
    val ROCK_THROW_WARNING_TICKS = high(6, "game ticks", "current strategy documentation")
    val RUBBLE_COOLDOWN_TICKS = exact(50, "game ticks", "30 seconds between rubble sequences")
    val ROCKFALL_MAX_HIT = trace(28, "damage", "requires current damage trace")
    val ROCK_THROW_MAX_HIT = trace(30, "damage", "requires current damage trace")
    val SHOCKWAVE_MAX_HIT = trace(35, "damage", "requires current damage trace")
    val FAST_STOMP_MAX_HIT = trace(18, "damage", "requires current damage trace")
    val UNDER_TARGET_STOMP_TICKS = trace(4, "game ticks", "requires current red-X trace")
    val SHOCKWAVE_WARNING_TICKS = trace(3, "game ticks", "requires current shockwave trace")
    val FAST_STOMP_WARNING_TICKS = trace(1, "game tick", "requires current under-target trace")
    val BABOON_MAX_HIT = exact(20, "damage", "revision-240 npc combat parameters")
    val BABOON_SARCOPHAGUS_DAMAGE = exact(20, "damage", "current OSRS mechanic")
    val BABOON_SARCOPHAGUS_DELAY =
        trace(8, "game ticks", "modern runner-selection cadence needs a current trace")
    val RUBBLE_COVER_DAMAGE = exact(10, "damage", "current OSRS cover transfer")
    val SARCOPHAGUS_COVER_DAMAGE = exact(30, "damage", "current OSRS cover transfer")
    val BABOON_COVER_DAMAGE = exact(12, "damage", "current OSRS cover transfer")
    val SARCOPHAGUS_HITPOINTS = exact(60, "hitpoints", "revision-240 npc definition 11788")
    val SARCOPHAGUS_ENERGY_DAMAGE = trace(12, "damage", "requires current base-energy damage trace")
    val GOTTA_HAVE_FAITH_DAMAGE = trace(20, "damage", "requires current invocation damage trace")
    val SARCOPHAGUS_ENERGY_INTERVAL =
        trace(6, "game ticks", "requires current base-energy cadence trace")
    val GOTTA_HAVE_FAITH_INTERVAL =
        trace(4, "game ticks", "requires current invocation cadence trace")
    val BABOON_STUN_TICKS = exact(5, "game ticks", "Jungle Japes invocation description")
    val BANANA_DAMAGE = trace(5, "damage", "reduced Jungle Japes damage needs live trace")
    val BANANA_LIFETIME = trace(50, "game ticks", "requires current room trace")
    val BOULDER_HITPOINTS = exact(25, "hitpoints", "revision-240 npc definitions 11782/11783")
    val BOULDER_RELEASE_INTERVAL =
        exact(8, "game ticks", "RsProx row casts 3322/3330/3338/3346/3354/3362/3370")
    val BOULDERDASH_RELEASE_INTERVAL =
        high(4, "game ticks", "Boulderdash doubles the baseline release speed")
    val BOULDER_COLLISION_MAX_HIT = high(35, "damage", "current OSRS rolling-stone behavior")
    val BOULDER_PUSH_TILES = exact(4, "tiles", "current OSRS rolling-stone behavior")
    val BOULDER_STUN_TICKS = exact(5, "game ticks", "current OSRS rolling-stone behavior")
    val BOULDER_MAX_SETS = high(10, "five-stone waves", "current OSRS phase completion cap")
    val ROLLING_THRESHOLDS = exact(listOf(2 to 3, 1 to 3), "max-HP fractions", "current OSRS")
    val traceRequired =
        listOf(
            "SPECIAL_AFTER_AUTOS" to SPECIAL_AFTER_AUTOS,
            "INTRO_TICKS" to INTRO_TICKS,
            "ROCKFALL_WARNING_TICKS" to ROCKFALL_WARNING_TICKS,
            "ROCKFALL_MAX_HIT" to ROCKFALL_MAX_HIT,
            "ROCK_THROW_MAX_HIT" to ROCK_THROW_MAX_HIT,
            "SHOCKWAVE_MAX_HIT" to SHOCKWAVE_MAX_HIT,
            "FAST_STOMP_MAX_HIT" to FAST_STOMP_MAX_HIT,
            "UNDER_TARGET_STOMP_TICKS" to UNDER_TARGET_STOMP_TICKS,
            "SHOCKWAVE_WARNING_TICKS" to SHOCKWAVE_WARNING_TICKS,
            "FAST_STOMP_WARNING_TICKS" to FAST_STOMP_WARNING_TICKS,
            "SARCOPHAGUS_ENERGY_DAMAGE" to SARCOPHAGUS_ENERGY_DAMAGE,
            "GOTTA_HAVE_FAITH_DAMAGE" to GOTTA_HAVE_FAITH_DAMAGE,
            "SARCOPHAGUS_ENERGY_INTERVAL" to SARCOPHAGUS_ENERGY_INTERVAL,
            "GOTTA_HAVE_FAITH_INTERVAL" to GOTTA_HAVE_FAITH_INTERVAL,
            "BANANA_DAMAGE" to BANANA_DAMAGE,
            "BANANA_LIFETIME" to BANANA_LIFETIME,
        )
}

internal data class BabaInvocationProfile(
    val lethalGap: Boolean,
    val sarcophagusEnergyDamage: Int,
    val sarcophagusEnergyInterval: Int,
    val bananaPilesPerBaboon: Int,
    val enhancedShockwave: Boolean,
    val boulderReleaseInterval: Int,
)

internal object BabaInvocationRules {
    fun resolve(invocations: Set<ToaInvocation>): BabaInvocationProfile {
        val faith = ToaInvocation.GOTTA_HAVE_FAITH in invocations
        return BabaInvocationProfile(
            lethalGap = ToaInvocation.MIND_THE_GAP in invocations,
            sarcophagusEnergyDamage =
                if (faith) BabaTuning.GOTTA_HAVE_FAITH_DAMAGE.value
                else BabaTuning.SARCOPHAGUS_ENERGY_DAMAGE.value,
            sarcophagusEnergyInterval =
                if (faith) BabaTuning.GOTTA_HAVE_FAITH_INTERVAL.value
                else BabaTuning.SARCOPHAGUS_ENERGY_INTERVAL.value,
            bananaPilesPerBaboon = if (ToaInvocation.JUNGLE_JAPES in invocations) 2 else 0,
            enhancedShockwave = ToaInvocation.SHAKING_THINGS_UP in invocations,
            boulderReleaseInterval =
                if (ToaInvocation.BOULDERDASH in invocations) {
                    BabaTuning.BOULDERDASH_RELEASE_INTERVAL.value
                } else {
                    BabaTuning.BOULDER_RELEASE_INTERVAL.value
                },
        )
    }
}

internal enum class BabaState {
    WAITING,
    INTRO,
    NORMAL_COMBAT,
    ROCKFALL,
    ROCK_THROW,
    SHOCKWAVE,
    BABOON_DISCHARGE,
    ROLLING_STONES_PREP,
    ROLLING_STONES,
    ROLLING_STONES_RETURN,
    DEFEATED,
    RESETTING,
}

internal enum class BabaSpecial {
    ROCKFALL,
    ROCK_THROW,
    SHOCKWAVE,
    BABOON_DISCHARGE,
}

internal enum class BabaSarcophagusState {
    CLOSED,
    DAMAGED,
    OPENING,
    OPEN,
    RESETTING,
}

internal enum class BabaCoverKind {
    RUBBLE,
    SARCOPHAGUS,
    BABOON,
}

internal data class BabaPendingRock(
    val target: PlayerUid,
    val tile: CoordGrid,
    val landsAt: Int,
    val damage: Int,
    val graphic: Int,
    val thrown: Boolean,
)

internal data class BabaRubble(val actorKey: Int, val tile: CoordGrid)

internal data class BabaPendingRubble(val tile: CoordGrid, val landsAt: Int, val variant: Int)

internal data class BabaPendingShockwave(
    val tiles: Set<CoordGrid>,
    val lethalTiles: Set<CoordGrid>,
    val landsAt: Int,
    val damage: Int,
)

internal data class BabaRollingBoulder(
    val actorKey: Int,
    val lane: Int,
    val weak: Boolean,
    var tile: CoordGrid,
    val hitPlayers: MutableSet<PlayerUid> = linkedSetOf(),
) {
    var validRangedHits: Int = 0
        private set

    fun damageForValidRangedHit(playerMaximumHit: Int, currentHitpoints: Int): Int {
        validRangedHits++
        return playerMaximumHit.coerceAtLeast(1).coerceAtMost(currentHitpoints)
    }
}

internal data class BabaBananaPile(val tile: CoordGrid, val armedAt: Int, val expiresAt: Int)

internal data class BabaSarcophagusRuntime(
    val tile: CoordGrid,
    var hitpoints: Int = BabaTuning.SARCOPHAGUS_HITPOINTS.value,
    var state: BabaSarcophagusState = BabaSarcophagusState.CLOSED,
    var nextEnergyAt: Int = -1,
)

internal object BabaArenaLayout {
    const val SOURCE_REGION = 15188
    const val SOURCE_MIN_X = 3796
    const val SOURCE_MIN_Z = 5399
    const val SOURCE_MAX_X = 3823
    const val SOURCE_MAX_Z = 5418
    const val MIN_LOCAL_X = 20
    const val MIN_LOCAL_Z = 23
    const val MAX_LOCAL_X = 47
    const val MAX_LOCAL_Z = 42
    const val BOSS_LOCAL_X = 39
    const val BOSS_LOCAL_Z = 30
    const val THRONE_LOCAL_X = 39
    const val THRONE_LOCAL_Z = 30
    const val BOULDER_START_LOCAL_X = 37
    const val BOULDER_END_LOCAL_X = 22
    val laneLocalZ = listOf(25, 28, 31, 34, 37)

    fun containsLocal(x: Int, z: Int) =
        x in MIN_LOCAL_X..MAX_LOCAL_X && z in MIN_LOCAL_Z..MAX_LOCAL_Z

    fun laneForLocalZ(z: Int): Int = laneLocalZ.indices.minByOrNull { abs(laneLocalZ[it] - z) } ?: 0

    fun rollingTile(lane: Int, localX: Int, level: Int = 0) =
        CoordGrid(localX, laneLocalZ[lane.coerceIn(laneLocalZ.indices)], level)
}

internal object BabaBaboonDischargePolicy {
    const val ATTACK_RANGE = 8
    const val CHASE_RANGE = 64

    fun runnerDue(
        now: Int,
        nextRunnerAt: Int,
        currentRunner: Int?,
        hasLiveBaboon: Boolean,
        hasClosedSarcophagus: Boolean,
    ): Boolean =
        currentRunner == null &&
            hasLiveBaboon &&
            hasClosedSarcophagus &&
            nextRunnerAt >= 0 &&
            now >= nextRunnerAt
}

internal object BabaScaling {
    fun partyHitpointsFactor(partySize: Int): Double = ToaRules.partyHitpointsFactor(partySize)

    fun hitpoints(partySize: Int, raidLevel: Int, pathLevel: Int): Int =
        ToaRules.hitpoints(BabaTuning.BASE_HITPOINTS.value, partySize, raidLevel, pathLevel)

    fun baboonCount(partySize: Int): Int = ((partySize.coerceIn(1, 8) + 1) / 2).coerceIn(1, 4)

    fun soloBoulderHitpoints(pathLevel: Int): Int =
        when {
            pathLevel >= 4 -> 31
            pathLevel >= 2 -> 27
            else -> 25
        }

    private val baselineBoulderHitpoints = listOf(25, 36, 47, 55, 62, 70, 77, 85)

    fun boulderHitpoints(partySize: Int, pathLevel: Int): Int {
        val baseline = baselineBoulderHitpoints[partySize.coerceIn(1, 8) - 1]
        val solo = soloBoulderHitpoints(pathLevel)
        return if (solo == 25) baseline else kotlin.math.floor(baseline * (solo / 25.0)).toInt()
    }

    fun rubbleHitpoints(partySize: Int): Int = 10 * partySize.coerceIn(1, 8)
}

internal object BabaThresholds {
    fun crossed(previousHp: Int, currentHp: Int, maximumHp: Int): List<Int> {
        if (maximumHp <= 0 || currentHp >= previousHp) return emptyList()
        return BabaTuning.ROLLING_THRESHOLDS.value.mapIndexedNotNull { index, (num, den) ->
            val wasAbove = previousHp.toLong() * den > maximumHp.toLong() * num
            val atOrBelow = currentHp.toLong() * den <= maximumHp.toLong() * num
            (index + 1).takeIf { wasAbove && atOrBelow }
        }
    }
}

internal object BabaRollingPath {
    /**
     * Returns the next server-authoritative rolling-stone tile, or `null` once the boulder has
     * reached the bottom of the arena. The live actor coordinate must be supplied here; a cached
     * runtime tile can fall behind forced movement and produce an illegal self-step.
     */
    fun next(current: CoordGrid, endX: Int): CoordGrid? =
        if (current.x <= endX) null else CoordGrid(current.x - 1, current.z, current.level)
}

internal object BabaRollingKnockback {
    /**
     * Limits a rolling-stone shove to the visible boulder floor. The wider Ba-Ba arena includes
     * scenery/void tiles below this edge, so using the generic arena minimum can throw a player
     * off-map from the two centre lanes.
     */
    fun safeDistance(currentX: Int, floorEndX: Int, requestedTiles: Int): Int {
        if (requestedTiles <= 0 || currentX <= floorEndX) return 0
        return requestedTiles.coerceAtMost(currentX - floorEndX)
    }
}

internal object BabaRollingCollision {
    fun touches(
        playerTile: CoordGrid,
        previousPlayerTile: CoordGrid,
        boulderTile: CoordGrid,
        boulderSize: Int,
    ): Boolean {
        val size = boulderSize.coerceAtLeast(1)
        fun inside(tile: CoordGrid): Boolean =
            tile.level == boulderTile.level &&
                tile.x in boulderTile.x until (boulderTile.x + size) &&
                tile.z in boulderTile.z until (boulderTile.z + size)
        return inside(playerTile) || inside(previousPlayerTile)
    }
}

internal object BabaSpecialSchedule {
    private val selectable =
        listOf(BabaSpecial.ROCKFALL, BabaSpecial.SHOCKWAVE, BabaSpecial.BABOON_DISCHARGE)

    fun select(last: BabaSpecial?, allowRockfall: Boolean, nextInt: (Int) -> Int): BabaSpecial {
        val available = if (allowRockfall) selectable else selectable - BabaSpecial.ROCKFALL
        val choices = available.filter { it != last }.ifEmpty { available }
        return choices[nextInt(choices.size).coerceIn(choices.indices)]
    }
}

internal object BabaRollingExit {
    fun inExitZone(player: CoordGrid, boulderStart: CoordGrid): Boolean =
        player.level == boulderStart.level && player.x > boulderStart.x

    fun canFinish(player: CoordGrid, boulderStart: CoordGrid, hitType: HitType): Boolean =
        hitType == HitType.Melee && inExitZone(player, boulderStart)
}

internal enum class BabaDirection(val dx: Int, val dz: Int) {
    NORTH(0, 1),
    NORTH_EAST(1, 1),
    EAST(1, 0),
    SOUTH_EAST(1, -1),
    SOUTH(0, -1),
    SOUTH_WEST(-1, -1),
    WEST(-1, 0),
    NORTH_WEST(-1, 1);

    companion object {
        fun toward(origin: CoordGrid, target: CoordGrid): BabaDirection {
            val dx = (target.x - origin.x).coerceIn(-1, 1)
            val dz = (target.z - origin.z).coerceIn(-1, 1)
            return entries.firstOrNull { it.dx == dx && it.dz == dz } ?: EAST
        }
    }
}

internal object BabaShockwaveMask {
    /** The cache effect is centred in front of Ba-Ba, then expanded by Shaking Things Up. */
    fun centre(bossBase: CoordGrid, target: CoordGrid): Pair<BabaDirection, CoordGrid> {
        val bossCentre = CoordGrid(bossBase.x + 2, bossBase.z + 2, bossBase.level)
        val direction = BabaDirection.toward(bossCentre, target)
        return direction to
            CoordGrid(
                bossCentre.x + direction.dx * 3,
                bossCentre.z + direction.dz * 3,
                bossCentre.level,
            )
    }

    fun offsets(enhanced: Boolean): Set<Pair<Int, Int>> {
        val radius = if (enhanced) 2 else 1
        return buildSet {
            for (x in -radius..radius) for (z in -radius..radius) add(x to z)
            add(radius + 1 to 0)
            add(-(radius + 1) to 0)
            add(0 to radius + 1)
            add(0 to -(radius + 1))
        }
    }

    fun tiles(
        bossBase: CoordGrid,
        target: CoordGrid,
        enhanced: Boolean,
    ): Pair<BabaDirection, Set<CoordGrid>> {
        val (direction, centre) = centre(bossBase, target)
        return direction to
            offsets(enhanced).mapTo(linkedSetOf()) { (x, z) ->
                CoordGrid(centre.x + x, centre.z + z, centre.level)
            }
    }

    fun mindGapTiles(bossBase: CoordGrid, target: CoordGrid): Set<CoordGrid> {
        val bossCentre = CoordGrid(bossBase.x + 2, bossBase.z + 2, bossBase.level)
        val direction = BabaDirection.toward(bossCentre, target)
        return (2..5).mapTo(linkedSetOf()) { distance ->
            CoordGrid(
                bossCentre.x + direction.dx * distance,
                bossCentre.z + direction.dz * distance,
                bossCentre.level,
            )
        }
    }
}

internal object BabaCoverResolver {
    fun select(
        impact: CoordGrid,
        rubble: Collection<CoordGrid>,
        sarcophagi: Collection<CoordGrid>,
        baboons: Collection<CoordGrid>,
    ): Pair<BabaCoverKind, CoordGrid>? =
        sequenceOf(
                BabaCoverKind.RUBBLE to rubble,
                BabaCoverKind.SARCOPHAGUS to sarcophagi,
                BabaCoverKind.BABOON to baboons,
            )
            .flatMap { (kind, tiles) -> tiles.asSequence().map { kind to it } }
            .filter { (_, tile) -> tile.chebyshevDistance(impact) <= 1 }
            .minWithOrNull(
                compareBy<Pair<BabaCoverKind, CoordGrid>> { it.first.ordinal }
                    .thenBy { it.second.chebyshevDistance(impact) }
            )
}

internal object BabaRockImpactResolver {
    fun playerRemainsOnImpactTile(playerTile: CoordGrid, impactTile: CoordGrid): Boolean =
        playerTile.level == impactTile.level && playerTile.chebyshevDistance(impactTile) == 0
}

internal data class BabaTraceEvent(
    val tick: Int,
    val event: String,
    val fields: Map<String, Any?>,
) {
    override fun toString() = buildString {
        append("[BabaTrace] tick=").append(tick).append(" event=").append(event)
        fields.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
    }
}

internal class BabaDebugTrace {
    var enabled = false
    private val events = ArrayDeque<BabaTraceEvent>()

    fun record(tick: Int, event: String, vararg fields: Pair<String, Any?>): BabaTraceEvent? {
        if (!enabled) return null
        return BabaTraceEvent(tick, event, linkedMapOf(*fields)).also {
            events += it
            while (events.size > 400) events.removeFirst()
        }
    }

    fun snapshot(limit: Int = 40) = events.takeLast(limit)

    fun clear() = events.clear()
}

/** One seedable state machine per raid; generation invalidates stale delayed work. */
internal class BabaEncounter(val encounterId: UUID, private val seed: Long) {
    var generation = 0
        private set

    var state = BabaState.WAITING
    var target: PlayerUid? = null
    var previousTarget: PlayerUid? = null
    var attackCounter = 0
    var nextRubbleAt = -1
    var activeRollingThreshold: Int? = null
    val pendingRollingThresholds = ArrayDeque<Int>()
    var completedRollingPhases = 0
    var rollingSet = 0
    var nextBoulderAt = -1
    var pendingBoulderSpawnAt = -1
    var pendingBoulderSet = 0
    var pendingWeakLane = -1
    var weakLane = -1
    var previousWeakLane = -1
    var underTargetTicks = 0
    var bouldersReleasedThisSet = 0
    var nextBaboonSarcophagusAt = -1
    var baboonSarcophagusRunner: Int? = null
    val pendingRocks = mutableListOf<BabaPendingRock>()
    val pendingRubble = mutableListOf<BabaPendingRubble>()
    val pendingShockwaves = mutableListOf<BabaPendingShockwave>()
    val rubble = linkedMapOf<Int, BabaRubble>()
    val rollingBoulders = linkedMapOf<Int, BabaRollingBoulder>()
    val destroyedBoulders = mutableSetOf<Int>()
    val bananas = linkedMapOf<CoordGrid, BabaBananaPile>()
    val sarcophagi = linkedMapOf<CoordGrid, BabaSarcophagusRuntime>()
    val trace = BabaDebugTrace()
    private var random = Random(seed)

    fun reset() {
        generation++
        state = BabaState.RESETTING
        target = null
        previousTarget = null
        attackCounter = 0
        nextRubbleAt = -1
        activeRollingThreshold = null
        pendingRollingThresholds.clear()
        completedRollingPhases = 0
        rollingSet = 0
        nextBoulderAt = -1
        pendingBoulderSpawnAt = -1
        pendingBoulderSet = 0
        pendingWeakLane = -1
        weakLane = -1
        previousWeakLane = -1
        underTargetTicks = 0
        bouldersReleasedThisSet = 0
        nextBaboonSarcophagusAt = -1
        baboonSarcophagusRunner = null
        pendingRocks.clear()
        pendingRubble.clear()
        pendingShockwaves.clear()
        rubble.clear()
        rollingBoulders.clear()
        destroyedBoulders.clear()
        bananas.clear()
        sarcophagi.clear()
        trace.clear()
        random = Random(seed xor generation.toLong())
        state = BabaState.WAITING
    }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        if (target == oldUid) target = newUid
        if (previousTarget == oldUid) previousTarget = newUid
        pendingRocks.replaceAll { rock ->
            if (rock.target == oldUid) rock.copy(target = newUid) else rock
        }
        rollingBoulders.values.forEach { it.hitPlayers.rebindValue(oldUid, newUid) }
    }

    fun retirePlayer(uid: PlayerUid) {
        if (target == uid) target = null
        if (previousTarget == uid) previousTarget = null
        pendingRocks.removeAll { it.target == uid }
        rollingBoulders.values.forEach { it.hitPlayers.remove(uid) }
    }

    fun token() = generation

    fun isCurrent(token: Int) =
        token == generation && state !in setOf(BabaState.DEFEATED, BabaState.RESETTING)

    fun startIntro() {
        check(state == BabaState.WAITING)
        state = BabaState.INTRO
    }

    fun startNormalCombat() {
        check(
            state in
                setOf(
                    BabaState.INTRO,
                    BabaState.ROCKFALL,
                    BabaState.ROCK_THROW,
                    BabaState.SHOCKWAVE,
                    BabaState.BABOON_DISCHARGE,
                    BabaState.ROLLING_STONES_RETURN,
                )
        )
        state = BabaState.NORMAL_COMBAT
        attackCounter = 0
    }

    fun recordAuto(): Boolean {
        if (state != BabaState.NORMAL_COMBAT) return false
        attackCounter++
        return attackCounter >= BabaTuning.SPECIAL_AFTER_AUTOS.value
    }

    fun rubbleDue(now: Int) = nextRubbleAt < 0 || now >= nextRubbleAt

    fun markRubbleSequenceStarted(now: Int) {
        nextRubbleAt = now + BabaTuning.RUBBLE_COOLDOWN_TICKS.value
    }

    fun queueCrossedThresholds(previousHp: Int, currentHp: Int, maximumHp: Int): List<Int> {
        val known = pendingRollingThresholds.toSet() + listOfNotNull(activeRollingThreshold)
        val crossed =
            BabaThresholds.crossed(previousHp, currentHp, maximumHp).filter {
                it > completedRollingPhases && it !in known
            }
        pendingRollingThresholds.addAll(crossed)
        return crossed
    }

    fun hasUnresolvedRollingThresholds() =
        activeRollingThreshold != null || pendingRollingThresholds.isNotEmpty()

    fun beginNextRollingPhase(): Int? {
        val threshold = pendingRollingThresholds.removeFirstOrNull() ?: return null
        activeRollingThreshold = threshold
        state = BabaState.ROLLING_STONES_PREP
        rollingSet = 0
        pendingBoulderSpawnAt = -1
        pendingBoulderSet = 0
        pendingWeakLane = -1
        weakLane = -1
        return threshold
    }

    fun beginRollingSet(boulderdash: Boolean, now: Int) {
        check(state in setOf(BabaState.ROLLING_STONES_PREP, BabaState.ROLLING_STONES))
        state = BabaState.ROLLING_STONES
        rollingSet++
        val candidates =
            if (previousWeakLane < 0) (1..3).toList()
            else
                BabaArenaLayout.laneLocalZ.indices.filter {
                    kotlin.math.abs(it - previousWeakLane) <= 2
                }
        weakLane = candidates[random.nextInt(candidates.size)]
        previousWeakLane = weakLane
        bouldersReleasedThisSet = 0
        nextBoulderAt =
            now +
                if (boulderdash) BabaTuning.BOULDERDASH_RELEASE_INTERVAL.value
                else BabaTuning.BOULDER_RELEASE_INTERVAL.value
    }

    fun isRollingCastDue(now: Int): Boolean =
        state == BabaState.ROLLING_STONES &&
            pendingBoulderSpawnAt < 0 &&
            nextBoulderAt >= 0 &&
            now >= nextBoulderAt

    /**
     * Advances to the next rolling row after the current row has been cast.
     *
     * The terminal row must explicitly disable [nextBoulderAt]. Leaving the previous due tick in
     * place makes the final row recast every time its two-tick launch delay completes, preventing
     * the rolling phase from ever draining.
     */
    fun advanceRollingScheduleAfterCast(boulderdash: Boolean, now: Int, maximumSets: Int): Boolean {
        require(maximumSets > 0) { "`maximumSets` must be greater than zero." }
        check(state == BabaState.ROLLING_STONES)
        if (rollingSet >= maximumSets) {
            nextBoulderAt = -1
            return false
        }
        beginRollingSet(boulderdash, now)
        return true
    }

    fun finishRollingPhase() {
        completedRollingPhases = maxOf(completedRollingPhases, activeRollingThreshold ?: 0)
        activeRollingThreshold = null
        state = BabaState.ROLLING_STONES_RETURN
        pendingBoulderSpawnAt = -1
        pendingBoulderSet = 0
        pendingWeakLane = -1
        rollingBoulders.clear()
    }

    fun nextInt(bound: Int) = random.nextInt(bound)
}

internal fun babaWeakBoulderCanReceive(type: HitType) = type == HitType.Ranged
