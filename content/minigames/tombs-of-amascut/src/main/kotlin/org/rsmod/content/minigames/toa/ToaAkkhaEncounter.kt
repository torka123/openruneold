package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

internal enum class AkkhaEvidenceConfidence {
    EXACT,
    HIGH_CONFIDENCE,
    TRACE_REQUIRED,
}

internal data class AkkhaTunedValue<T>(
    val value: T,
    val units: String,
    val evidence: String,
    val confidence: AkkhaEvidenceConfidence,
)

/** Revision-240 cache assets. Raw IDs must not appear in encounter logic. */
internal object AkkhaAssets {
    const val SPAWN = 11789
    const val MELEE = 11790
    const val RANGE = 11791
    const val MAGE = 11792
    const val ENRAGE_SPAWN = 11793
    const val ENRAGE_INITIAL = 11794
    const val ENRAGE = 11795
    const val ENRAGE_DUMMY = 11796
    const val SHADOW = 11797
    const val SHADOW_ENRAGE = 11798
    const val SHADOW_ENRAGE_DUMMY = 11799
    const val TRAIL_ORB_LIGHTNING = 11800
    const val TRAIL_ORB_DARKNESS = 11801
    const val TRAIL_ORB_BURN = 11802
    const val TRAIL_ORB_FREEZE = 11803
    const val ENRAGE_ORB = 11804
    const val HEADBAR = 11805
    const val SHADOW_TIMER_HEADBAR = 36
    const val SHADOW_TIMER_SEGMENTS = 100
    const val IDLE_SPEAR = 9760
    const val IDLE_SWORD = 9761
    const val WALK_SPEAR = 9762
    const val WALK_SWORD = 9763
    const val WALK_ALT_SPEAR = 9764
    const val WALK_ALT_SWORD = 9765
    const val WALK_BACK_SPEAR = 9766
    const val WALK_BACK_SWORD = 9767
    const val WALK_BACK_ALT_SPEAR = 9768
    const val WALK_BACK_ALT_SWORD = 9769
    const val ATTACK_MELEE_SPEAR = 9770
    const val ATTACK_MELEE_SWORD = 9771
    const val ATTACK_RANGE_SPEAR = 9772
    const val ATTACK_RANGE_SWORD = 9773
    const val ATTACK_MAGIC_SPEAR = 9774
    const val ATTACK_MAGIC_SWORD = 9775
    const val DETONATE_SPEAR = 9776
    const val MEMORY_BLAST_SPEAR = 9777
    const val TRAILING_ORBS_SPEAR = 9778
    const val FINAL_STAND_SPEAR = 9779
    const val DETONATE_SWORD = 9780
    const val MEMORY_BLAST_SWORD = 9781
    const val TRAILING_ORBS_SWORD = 9782
    const val FINAL_STAND_SWORD = 9783
    const val TELEPORT_DISAPPEAR_SPEAR = 9784
    const val TELEPORT_APPEAR_SPEAR = 9785
    const val TELEPORT_DISAPPEAR_SWORD = 9786
    const val TELEPORT_APPEAR_SWORD = 9787
    const val STOMP_SPEAR = 9788
    const val STOMP_SWORD = 9789
    const val SPAWN_SPEAR = 9790
    const val SPAWN_SWORD = 9791
    const val DEATH_SPEAR = 9792
    const val DEATH_SWORD = 9793
    const val ORB_DUST_IDLE = 9794
    const val MAGIC_TRAVEL = 2253
    const val MAGIC_IMPACT = 2254
    const val RANGE_TRAVEL = 2255
    const val LIGHTNING = 2256
    const val DARKNESS = 2257
    const val BURN = 2258
    const val FREEZE = 2259
    const val ENRAGE_ORB_IMPACT = 2260
    val npcIds = (SPAWN..HEADBAR).toSet()
    val sequenceIds = (IDLE_SPEAR..ORB_DUST_IDLE).toSet()
    val spotanimIds = (MAGIC_TRAVEL..ENRAGE_ORB_IMPACT).toSet()
    val elementalIndicatorNpcIds =
        setOf(TRAIL_ORB_LIGHTNING, TRAIL_ORB_DARKNESS, TRAIL_ORB_BURN, TRAIL_ORB_FREEZE)
}

internal fun akkhaShadowTimerFill(elapsed: Int, maximum: Int): Int {
    val safeMaximum = maximum.coerceAtLeast(1)
    return (elapsed.coerceIn(0, safeMaximum) * AkkhaAssets.SHADOW_TIMER_SEGMENTS / safeMaximum)
        .coerceIn(0, AkkhaAssets.SHADOW_TIMER_SEGMENTS)
}

internal data class AkkhaShadowTint(
    val startTime: Int,
    val endTime: Int,
    val hue: Int,
    val saturation: Int,
    val lightness: Int,
    val weight: Int,
)

/** Exact revision-240 shadow tint masks captured from the live OSRS encounter. */
internal object AkkhaShadowVisuals {
    val ATTACKABLE = AkkhaShadowTint(0, 0, 255, 255, 255, 0)
    val IMMUNE = AkkhaShadowTint(0, 600, 0, 0, 106, 40)

    fun tint(encounter: AkkhaEncounter, quadrant: AkkhaQuadrant): AkkhaShadowTint =
        if (encounter.canDisruptShadow(quadrant)) ATTACKABLE else IMMUNE
}

internal fun akkhaMemoryRepositoryHideDuration(hideAt: Int, reappearAt: Int): Int =
    (reappearAt - hideAt - 1).coerceAtLeast(1)

internal fun akkhaHitCanUseNpcSource(isSlotAssigned: Boolean): Boolean = isSlotAssigned

internal enum class AkkhaState {
    WAITING,
    INTRO,
    NORMAL_COMBAT,
    SPECIAL_ATTACK,
    SHADOW_TRANSITION,
    FINAL_STAND_INTRO,
    FINAL_STAND,
    DEFEATED,
    RESETTING,
}

internal enum class AkkhaStyle(
    val npcId: Int,
    val hitType: HitType,
    val animation: Int,
    val projectile: Int?,
    val impact: Int?,
) {
    MELEE(AkkhaAssets.MELEE, HitType.Melee, AkkhaAssets.ATTACK_MELEE_SPEAR, null, null),
    RANGED(
        AkkhaAssets.RANGE,
        HitType.Ranged,
        AkkhaAssets.ATTACK_RANGE_SPEAR,
        AkkhaAssets.RANGE_TRAVEL,
        null,
    ),
    MAGIC(
        AkkhaAssets.MAGE,
        HitType.Magic,
        AkkhaAssets.ATTACK_MAGIC_SPEAR,
        AkkhaAssets.MAGIC_TRAVEL,
        AkkhaAssets.MAGIC_IMPACT,
    );

    fun next() = entries[(ordinal + 1) % entries.size]
}

internal fun akkhaAttackRange(style: AkkhaStyle): Int = if (style == AkkhaStyle.MELEE) 1 else 32

internal enum class AkkhaQuadrant(
    val displayName: String,
    val trailNpc: Int,
    val graphic: Int,
    val symbolObjectId: Int,
    val symbolAnchor: Pair<Int, Int>,
) {
    BURN("burn", AkkhaAssets.TRAIL_ORB_BURN, AkkhaAssets.BURN, 45868, 31 to 33),
    DARKNESS("darkness", AkkhaAssets.TRAIL_ORB_DARKNESS, AkkhaAssets.DARKNESS, 45871, 34 to 33),
    LIGHTNING("lightning", AkkhaAssets.TRAIL_ORB_LIGHTNING, AkkhaAssets.LIGHTNING, 45869, 34 to 30),
    FREEZE("freeze", AkkhaAssets.TRAIL_ORB_FREEZE, AkkhaAssets.FREEZE, 45870, 31 to 30),
}

internal enum class AkkhaSpecial(val spearAnimation: Int, val swordAnimation: Int) {
    MEMORY_BLAST(AkkhaAssets.MEMORY_BLAST_SPEAR, AkkhaAssets.MEMORY_BLAST_SWORD),
    TRAILING_ORBS(AkkhaAssets.TRAILING_ORBS_SPEAR, AkkhaAssets.TRAILING_ORBS_SWORD),
    DETONATE(AkkhaAssets.DETONATE_SPEAR, AkkhaAssets.DETONATE_SWORD),
}

internal enum class AkkhaOrbDirection(val dx: Int, val dz: Int) {
    NORTH(0, 1),
    NORTH_EAST(1, 1),
    EAST(1, 0),
    SOUTH_EAST(1, -1),
    SOUTH(0, -1),
    SOUTH_WEST(-1, -1),
    WEST(-1, 0),
    NORTH_WEST(-1, 1),
}

/** All uncertain values are isolated and evidence-labelled for ::akkhatrace tuning. */
internal object AkkhaTuning {
    private fun <T> exact(v: T, u: String, e: String) =
        AkkhaTunedValue(v, u, e, AkkhaEvidenceConfidence.EXACT)

    private fun <T> high(v: T, u: String, e: String) =
        AkkhaTunedValue(v, u, e, AkkhaEvidenceConfidence.HIGH_CONFIDENCE)

    private fun <T> trace(v: T, u: String, e: String) =
        AkkhaTunedValue(v, u, e, AkkhaEvidenceConfidence.TRACE_REQUIRED)

    val BASE_HITPOINTS = exact(400, "hitpoints", "current OSRS baseline")
    val BASE_ATTACK_INTERVAL = exact(6, "game ticks", "current OSRS attack speed")
    val ROOM_CHASE_RANGE =
        exact(16, "tiles from spawn", "covers every walkable tile in Akkha's 19x19 arena")
    val BASE_MAX_HIT = high(28, "damage", "current OSRS encounter scaling baseline")
    val KEEP_BACK_MAX_HIT = trace(16, "damage", "needs current invocation trace")
    val SHADOW_BASE_HITPOINTS =
        high(70, "hitpoints", "current OSRS Wiki baseline before raid and path scaling")
    val SHADOW_CHARGE_MAX_HIT = trace(24, "damage", "needs current tick trace")
    val MEMORY_BLAST_MAX_HIT = trace(32, "damage", "needs current tick trace")
    val DETONATE_MAX_HIT = trace(30, "damage", "needs current tick trace")
    val TRAILING_ORB_MAX_HIT = trace(28, "damage", "needs current tick trace")
    val BURN_DAMAGE = trace(5, "damage per hit", "needs current tick trace")
    val NORMAL_PRAYER_REDUCTION = trace(85, "percent", "public strategy gives an 80-90% range")
    val MELEE_PRAYER_REDUCTION = trace(75, "percent", "public strategy approximation")
    val FINAL_ORB_PRAYER_REDUCTION = trace(25, "percent", "needs final-stand trace")
    val BASE_AUTOS_PER_SPECIAL = exact(7, "emitted autos", "Jagex Summer Sweep-Up 2025")
    val STAY_VIGILANT_AUTOS_PER_SPECIAL = exact(6, "emitted autos", "Jagex Summer Sweep-Up 2025")
    val SHADOW_THRESHOLDS = exact(listOf(80, 60, 40, 20), "percent HP", "current OSRS")
    val FINAL_STAND_HEAL = exact(20, "percent max HP", "current OSRS")
    val SOLO_FINAL_HITS = high(3, "successful hits", "current OSRS strategy documentation")
    val TEAM_FINAL_HIT_OFFSET =
        exact(2, "successful hits added to party size", "three solo, plus one per extra player")
    val FINAL_ORB_DAMAGE_CAP = exact(25, "damage", "current OSRS")
    val INTRO_TARGETABLE_DELAY =
        high(0, "game ticks", "Akkha immediately pursues with melee when the encounter begins")
    val MEMORY_ITERATIONS_BY_PATH_BRACKET =
        trace(listOf(4, 5, 6), "iterations at path 0-1, 2-3, and 4+", "needs current path trace")
    val MEMORY_BASE_STEP_WINDOW = exact(4, "game ticks", "revision-240 packet capture")
    val MEMORY_FEELING_SPECIAL_STEP_WINDOW = exact(2, "game ticks", "invocation description")
    val MEMORY_INITIAL_REVEAL = exact(4, "game ticks", "first floor symbol packet")
    val MEMORY_REVEAL_INTERVAL = exact(2, "game ticks", "subsequent floor symbol packets")
    val MEMORY_DISAPPEAR_ANIM_DELAY = exact(3, "game ticks", "revision-240 packet capture")
    val MEMORY_HIDE_DELAY = exact(4, "game ticks", "revision-240 packet capture")
    val MEMORY_FIRST_RESOLUTION_DELAY = exact(12, "game ticks", "revision-240 packet capture")
    val MEMORY_REAPPEAR_DELAY = exact(2, "game ticks after final resolution", "packet capture")
    val DETONATE_TELEGRAPH = trace(5, "game ticks", "needs current tick trace")
    val DETONATE_RADIUS = exact(1, "tile", "3x3 plus, or diagonal X with Feeling Special")
    val TRAIL_INITIAL_GRACE = exact(2, "game ticks", "revision-240 packet capture")
    val TRAIL_LIFETIME = exact(20, "active game ticks", "revision-240 packet capture")
    val TRAIL_ORB_LIFETIME = trace(5, "game ticks", "needs current tick trace")
    val SHADOW_CHARGE = exact(50, "active game ticks", "revision-240 packet capture")
    val BURN_TICKS = trace(3, "hits", "needs current tick trace")
    val BURN_CADENCE = trace(2, "game ticks", "needs current tick trace")
    val DARKNESS_DRAIN = high(3, "Combat levels", "Jagex 2025 clarification")
    val LIGHTNING_SUPPRESSION = trace(9, "game ticks", "approximately five real seconds")
    val FREEZE_ATTACK_DELAY = high(1, "game tick", "current mechanic description")
    val FREEZE_DURATION = high(25, "game ticks", "donor lifecycle and current mechanic evidence")
    val FINAL_ORB_INITIAL_GRACE = trace(3, "game ticks", "needs post-rework trace")
    val FINAL_ORB_SPAWN_INTERVAL = trace(3, "game ticks", "reduced 2025 density; needs trace")
    val SHADOW_BLAST_WAVE_TICKS =
        trace(4, "game ticks", "outside-edge-to-centre travel needs current trace")
    val FINAL_ORB_ACTIVE_CAP = trace(12, "actors", "reduced 2025 density; needs trace")
    val FINAL_STAND_STAGE_OFFSETS =
        exact(listOf(0, 2, 4, 5), "ticks from final-stand cast", "revision-240 packet capture")
    val FINAL_ORB_TRAVEL_SPEED = high(1, "tiles per tick", "current visual behavior")
    val SPECIAL_SELECTION_POLICY =
        trace("seeded pool, no immediate repeat", "policy", "not publicly specified")
    val traceRequired =
        listOf(
            "KEEP_BACK_MAX_HIT" to KEEP_BACK_MAX_HIT,
            "SHADOW_CHARGE_MAX_HIT" to SHADOW_CHARGE_MAX_HIT,
            "MEMORY_BLAST_MAX_HIT" to MEMORY_BLAST_MAX_HIT,
            "DETONATE_MAX_HIT" to DETONATE_MAX_HIT,
            "TRAILING_ORB_MAX_HIT" to TRAILING_ORB_MAX_HIT,
            "NORMAL_PRAYER_REDUCTION" to NORMAL_PRAYER_REDUCTION,
            "MELEE_PRAYER_REDUCTION" to MELEE_PRAYER_REDUCTION,
            "BURN_DAMAGE" to BURN_DAMAGE,
            "FINAL_ORB_PRAYER_REDUCTION" to FINAL_ORB_PRAYER_REDUCTION,
            "MEMORY_ITERATIONS_BY_PATH_BRACKET" to MEMORY_ITERATIONS_BY_PATH_BRACKET,
            "DETONATE_TELEGRAPH" to DETONATE_TELEGRAPH,
            "TRAIL_ORB_LIFETIME" to TRAIL_ORB_LIFETIME,
            "BURN_TICKS" to BURN_TICKS,
            "BURN_CADENCE" to BURN_CADENCE,
            "LIGHTNING_SUPPRESSION" to LIGHTNING_SUPPRESSION,
            "FINAL_ORB_INITIAL_GRACE" to FINAL_ORB_INITIAL_GRACE,
            "FINAL_ORB_SPAWN_INTERVAL" to FINAL_ORB_SPAWN_INTERVAL,
            "FINAL_ORB_ACTIVE_CAP" to FINAL_ORB_ACTIVE_CAP,
            "SHADOW_BLAST_WAVE_TICKS" to SHADOW_BLAST_WAVE_TICKS,
            "SPECIAL_SELECTION_POLICY" to SPECIAL_SELECTION_POLICY,
        )
}

internal object AkkhaArenaLayout {
    const val PLANE = 1
    const val MIN_LOCAL_X = 23
    const val MAX_LOCAL_X = 42
    const val MIN_LOCAL_Z = 22
    const val MAX_LOCAL_Z = 41
    const val CENTER_X = 32
    const val CENTER_Z = 32
    const val ARENA_RADIUS = 9
    const val SHADOW_RADIUS = 5
    const val HOURGLASS_RADIUS = 8
    val shadowAnchors =
        mapOf(
            AkkhaQuadrant.BURN to (26 to 36),
            AkkhaQuadrant.DARKNESS to (37 to 36),
            AkkhaQuadrant.LIGHTNING to (37 to 25),
            AkkhaQuadrant.FREEZE to (26 to 25),
        )
    val headbarAnchors =
        mapOf(
            AkkhaQuadrant.BURN to (23 to 40),
            AkkhaQuadrant.DARKNESS to (41 to 40),
            AkkhaQuadrant.LIGHTNING to (41 to 22),
            AkkhaQuadrant.FREEZE to (23 to 22),
        )
    val butterflyValidationEndpoints =
        setOf(36 to 32, 36 to 39, 29 to 39, 29 to 32, 29 to 31, 29 to 24, 36 to 31, 36 to 24)
    val finalStandAnchors = shadowAnchors.values.toList()
    val entrance = 41 to 32
    val completion = 50 to 32

    fun quadrant(x: Int, z: Int) =
        when {
            x <= CENTER_X && z >= CENTER_Z -> AkkhaQuadrant.BURN
            x > CENTER_X && z >= CENTER_Z -> AkkhaQuadrant.DARKNESS
            x > CENTER_X -> AkkhaQuadrant.LIGHTNING
            else -> AkkhaQuadrant.FREEZE
        }

    fun contains(x: Int, z: Int) = x in MIN_LOCAL_X..MAX_LOCAL_X && z in MIN_LOCAL_Z..MAX_LOCAL_Z

    private val southRowLengths = listOf(2, 5, 6, 7, 8, 9, 9, 9, 10, 10)
    private val northRowLengths = listOf(10, 10, 9, 9, 9, 8, 7, 6, 5, 2)

    /** Exact clipped floor footprint from the revision-240 zone packet, 75 tiles per quadrant. */
    fun floorRowLength(z: Int): Int =
        when (z) {
            in MIN_LOCAL_Z until CENTER_Z -> southRowLengths[z - MIN_LOCAL_Z]
            in CENTER_Z..MAX_LOCAL_Z -> northRowLengths[z - CENTER_Z]
            else -> 0
        }

    fun floorXRange(z: Int, east: Boolean): IntRange {
        val length = floorRowLength(z)
        if (length == 0) return IntRange.EMPTY
        return if (east) (CENTER_X + 1)..(CENTER_X + length) else (CENTER_X - length + 1)..CENTER_X
    }

    fun isFloorTile(x: Int, z: Int): Boolean {
        val east = x > CENTER_X
        return x in floorXRange(z, east)
    }

    fun floorTiles(
        quadrants: Set<AkkhaQuadrant> = AkkhaQuadrant.entries.toSet()
    ): List<Pair<Int, Int>> = buildList {
        for (z in MIN_LOCAL_Z..MAX_LOCAL_Z) {
            for (east in listOf(false, true)) {
                for (x in floorXRange(z, east)) {
                    if (quadrant(x, z) in quadrants) add(x to z)
                }
            }
        }
    }
}

internal object AkkhaAggressionPolicy {
    fun attackRange(style: AkkhaStyle, lureRequired: Boolean): Int =
        if (lureRequired) 1 else akkhaAttackRange(style)

    fun shouldRearm(
        force: Boolean,
        hasPlayerInteractionMode: Boolean,
        targetMatches: Boolean,
        interactionRangeMatches: Boolean,
    ): Boolean = force || !hasPlayerInteractionMode || !targetMatches || !interactionRangeMatches
}

internal object AkkhaScaling {
    fun hitpoints(p: Int, r: Int, l: Int) =
        ToaRules.hitpoints(AkkhaTuning.BASE_HITPOINTS.value, p, r, l)

    fun shadowHitpoints(p: Int, r: Int, l: Int) =
        ToaRules.hitpoints(AkkhaTuning.SHADOW_BASE_HITPOINTS.value, p, r, l)

    fun maxHit(base: Int, r: Int, l: Int) = ToaRules.maxHit(base, r, l)
}

internal object AkkhaInvocationRules {
    data class Profile(
        val doubleTrouble: Boolean,
        val keepBack: Boolean,
        val attacksBeforeSpecial: Int,
        val feelingSpecial: Boolean,
        val memoryStepWindow: Int,
        val diagonalDetonation: Boolean,
        val extraTrailingOrb: Boolean,
    )

    fun resolve(invocations: Set<ToaInvocation>): Profile {
        val stay = ToaInvocation.STAY_VIGILANT in invocations
        val feeling = ToaInvocation.FEELING_SPECIAL in invocations
        return Profile(
            doubleTrouble = ToaInvocation.DOUBLE_TROUBLE in invocations,
            keepBack = ToaInvocation.KEEP_BACK in invocations,
            attacksBeforeSpecial = attacksBeforeSpecial(stay),
            feelingSpecial = feeling,
            memoryStepWindow = memoryStepWindow(feeling),
            diagonalDetonation = feeling,
            extraTrailingOrb = feeling,
        )
    }

    fun attacksBeforeSpecial(stay: Boolean) =
        if (stay) AkkhaTuning.STAY_VIGILANT_AUTOS_PER_SPECIAL.value
        else AkkhaTuning.BASE_AUTOS_PER_SPECIAL.value

    fun memoryStepWindow(feeling: Boolean) =
        if (feeling) AkkhaTuning.MEMORY_FEELING_SPECIAL_STEP_WINDOW.value
        else AkkhaTuning.MEMORY_BASE_STEP_WINDOW.value
}

internal object AkkhaSpecialSchedule {
    fun eligible(party: Int) =
        if (party > 1) AkkhaSpecial.entries
        else listOf(AkkhaSpecial.MEMORY_BLAST, AkkhaSpecial.TRAILING_ORBS)

    fun select(
        party: Int,
        double: Boolean,
        previous: AkkhaSpecial?,
        nextInt: (Int) -> Int,
    ): List<AkkhaSpecial> {
        if (double && party == 1)
            return listOf(AkkhaSpecial.MEMORY_BLAST, AkkhaSpecial.TRAILING_ORBS)
        val pool = eligible(party)
        val firstPool = pool.filterNot { pool.size > 1 && it == previous }
        val first = firstPool[nextInt(firstPool.size)]
        if (!double) return listOf(first)
        val secondPool = pool.filterNot { it == first }
        return listOf(first, secondPool[nextInt(secondPool.size)])
    }
}

internal class AkkhaMemoryStep(
    val safeQuadrant: AkkhaQuadrant,
    val revealAt: Int,
    val blastStartsAt: Int,
    val resolvesAt: Int,
    var revealed: Boolean = false,
    var renderedWaveTick: Int = -1,
)

internal data class AkkhaFloorBlast(
    val quadrants: Set<AkkhaQuadrant>,
    val startedAt: Int,
    val resolvesAt: Int,
    var renderedWaveTick: Int = -1,
)

internal data class AkkhaDetonation(
    val origins: Map<PlayerUid, CoordGrid>,
    val resolvesAt: Int,
    val diagonal: Boolean,
)

internal data class AkkhaTrailOrb(
    val owner: PlayerUid,
    val element: AkkhaQuadrant,
    val tile: CoordGrid,
    val spawnedAt: Int,
    val expiresAt: Int,
)

internal data class AkkhaFinalOrb(
    val actorKey: Int,
    var tile: CoordGrid,
    val direction: AkkhaOrbDirection,
    val spawnedAt: Int,
)

internal data class AkkhaFinalStandSwap(
    val fromIndex: Int,
    val toIndex: Int,
    val outgoingBossActorKey: Int?,
    val incomingBossActorKey: Int?,
)

internal enum class AkkhaMemoryVisibilityTransition {
    HIDE,
    REAPPEAR,
}

internal data class AkkhaStatus(
    var burnExpiresAt: Int = -1,
    var nextBurnAt: Int = -1,
    var darknessExpiresAt: Int = -1,
    var nextDarknessHitAt: Int = -1,
    var lightningExpiresAt: Int = -1,
    var freezeExpiresAt: Int = -1,
    var freezeAttackDelayApplied: Boolean = false,
    var combatDrained: Boolean = false,
)

internal data class AkkhaTraceEvent(
    val tick: Int,
    val event: String,
    val fields: Map<String, Any?>,
) {
    override fun toString() = buildString {
        append("[AkkhaTrace] tick=").append(tick).append(" event=").append(event)
        fields.forEach { (k, v) -> append(' ').append(k).append('=').append(v) }
    }
}

internal class AkkhaDebugTrace {
    var enabled = false
    private val events = ArrayDeque<AkkhaTraceEvent>()

    fun record(tick: Int, event: String, vararg fields: Pair<String, Any?>): AkkhaTraceEvent? {
        if (!enabled) return null
        val e = AkkhaTraceEvent(tick, event, linkedMapOf(*fields))
        events += e
        while (events.size > 400) events.removeFirst()
        return e
    }

    fun snapshot(limit: Int = 40) = events.takeLast(limit)

    fun clear() = events.clear()
}

/** One seedable mutable state machine per ToaRaidSession; never shared between parties. */
internal class AkkhaEncounter(val encounterId: UUID, private val seed: Long) {
    var generation = 0
        private set

    var state = AkkhaState.WAITING
    var style = AkkhaStyle.MELEE
    var quadrant = AkkhaQuadrant.BURN
    var target: PlayerUid? = null
    var attackCounter = 0
    var lastEmittedAutoAt = -1
    var specialEndsAt = -1
    private var autoSinceLastSpecial = false
    private var advanceStyleWhenSpecialFinishes = false
    val activeSpecials = linkedSetOf<AkkhaSpecial>()
    private val unusedThresholds = ArrayDeque(AkkhaTuning.SHADOW_THRESHOLDS.value)
    val pendingThresholds = ArrayDeque<Int>()
    var activeShadowThreshold: Int? = null
    var shadowChargeEndsAt = -1
    var shadowBlast: AkkhaFloorBlast? = null
    var completedShadow: AkkhaQuadrant? = null
    val pendingLureQuadrant: AkkhaQuadrant?
        get() = completedShadow.takeIf { activeShadowThreshold == null }

    val shadowHeadbarActors = mutableMapOf<AkkhaQuadrant, Int>()
    val usedShadowQuadrants = linkedSetOf<AkkhaQuadrant>()
    var pendingDisruptedShadow: AkkhaQuadrant? = null
        private set

    /**
     * Compatibility view. Used quadrants still respawn, charge, and are immune at later thresholds.
     */
    val defeatedShadows: Set<AkkhaQuadrant>
        get() = usedShadowQuadrants

    val memorySteps = mutableListOf<AkkhaMemoryStep>()
    var memoryHideAt = -1
        private set

    var memoryReappearAt = -1
        private set

    var memoryHidden = false
        private set

    var detonation: AkkhaDetonation? = null
    var trailStartsAt = -1
    var trailEndsAt = -1
    val trailLastTiles = mutableMapOf<PlayerUid, CoordGrid>()
    val trailOrbs = mutableListOf<AkkhaTrailOrb>()
    var finalStandHitCounter = 0
    var finalStandLocationIndex = -1
    var finalStandIntroStage = 0
    var finalStandIntroStartedAt = -1
    var finalStandTransitionAt = -1
    var nextFinalOrbAt = -1
    private var finalOrbKey = 0
    val finalOrbs = linkedMapOf<Int, AkkhaFinalOrb>()
    var finalStandBossActorKey: Int? = null
        private set

    val finalStandDecoyActorKeys = linkedMapOf<Int, Int>()
    var pendingFinalStandSwap: AkkhaFinalStandSwap? = null
        private set

    val statuses = mutableMapOf<PlayerUid, AkkhaStatus>()
    val wrongStyleWarnedAt = mutableMapOf<PlayerUid, Int>()
    val trace = AkkhaDebugTrace()
    private var random = Random(seed)

    fun reset() {
        generation++
        state = AkkhaState.RESETTING
        style = AkkhaStyle.MELEE
        quadrant = AkkhaQuadrant.BURN
        target = null
        attackCounter = 0
        lastEmittedAutoAt = -1
        specialEndsAt = -1
        autoSinceLastSpecial = false
        advanceStyleWhenSpecialFinishes = false
        activeSpecials.clear()
        unusedThresholds.clear()
        unusedThresholds.addAll(AkkhaTuning.SHADOW_THRESHOLDS.value)
        pendingThresholds.clear()
        activeShadowThreshold = null
        shadowChargeEndsAt = -1
        shadowBlast = null
        completedShadow = null
        shadowHeadbarActors.clear()
        usedShadowQuadrants.clear()
        pendingDisruptedShadow = null
        memorySteps.clear()
        memoryHideAt = -1
        memoryReappearAt = -1
        memoryHidden = false
        detonation = null
        trailStartsAt = -1
        trailEndsAt = -1
        trailLastTiles.clear()
        trailOrbs.clear()
        finalStandHitCounter = 0
        finalStandLocationIndex = -1
        finalStandIntroStage = 0
        finalStandIntroStartedAt = -1
        finalStandTransitionAt = -1
        nextFinalOrbAt = -1
        finalOrbKey = 0
        finalOrbs.clear()
        finalStandBossActorKey = null
        finalStandDecoyActorKeys.clear()
        pendingFinalStandSwap = null
        statuses.clear()
        wrongStyleWarnedAt.clear()
        trace.clear()
        random = Random(seed xor generation.toLong())
        state = AkkhaState.WAITING
    }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        if (target == oldUid) target = newUid
        trailLastTiles.rebindKey(oldUid, newUid)
        statuses.rebindKey(oldUid, newUid)
        wrongStyleWarnedAt.rebindKey(oldUid, newUid)
        detonation =
            detonation?.let { value ->
                value.copy(
                    origins =
                        value.origins.entries.associate { (uid, tile) ->
                            (if (uid == oldUid) newUid else uid) to tile
                        }
                )
            }
        trailOrbs.replaceAll { orb -> if (orb.owner == oldUid) orb.copy(owner = newUid) else orb }
    }

    fun retirePlayer(uid: PlayerUid) {
        if (target == uid) target = null
        trailLastTiles.remove(uid)
        statuses.remove(uid)
        wrongStyleWarnedAt.remove(uid)
        val value = detonation
        if (value != null) detonation = value.copy(origins = value.origins - uid)
        trailOrbs.removeAll { it.owner == uid }
    }

    fun token() = generation

    fun isCurrent(token: Int) =
        token == generation && state !in setOf(AkkhaState.DEFEATED, AkkhaState.RESETTING)

    fun startIntro() {
        check(state == AkkhaState.WAITING)
        state = AkkhaState.INTRO
    }

    fun startNormalCombat() {
        check(
            state in
                setOf(AkkhaState.INTRO, AkkhaState.SHADOW_TRANSITION, AkkhaState.SPECIAL_ATTACK)
        )
        state = AkkhaState.NORMAL_COMBAT
    }

    fun recordEmittedAuto(tick: Int, stay: Boolean): Boolean {
        if (state !in setOf(AkkhaState.NORMAL_COMBAT, AkkhaState.SHADOW_TRANSITION)) return false
        attackCounter++
        lastEmittedAutoAt = tick
        autoSinceLastSpecial = true
        return attackCounter >= AkkhaInvocationRules.attacksBeforeSpecial(stay)
    }

    fun startSpecial(specials: Collection<AkkhaSpecial>, endsAt: Int) {
        check(state in setOf(AkkhaState.NORMAL_COMBAT, AkkhaState.SHADOW_TRANSITION))
        require(specials.isNotEmpty())
        val blocking = specials.filterNot { it == AkkhaSpecial.TRAILING_ORBS }
        activeSpecials.clear()
        activeSpecials.addAll(blocking)
        attackCounter = 0
        advanceStyleWhenSpecialFinishes = autoSinceLastSpecial
        autoSinceLastSpecial = false
        if (blocking.isEmpty()) {
            // Trailing orbs persist as a hazard overlay while Akkha resumes standard attacks.
            specialEndsAt = -1
            advanceStyleAfterSpecialIfDue()
            return
        }
        state = AkkhaState.SPECIAL_ATTACK
        specialEndsAt = endsAt
    }

    fun finishSpecial() {
        if (state != AkkhaState.SPECIAL_ATTACK) return
        activeSpecials.clear()
        specialEndsAt = -1
        clearMemoryLifecycle()
        advanceStyleAfterSpecialIfDue()
        state =
            if (activeShadowThreshold == null && pendingLureQuadrant == null)
                AkkhaState.NORMAL_COMBAT
            else AkkhaState.SHADOW_TRANSITION
    }

    private fun advanceStyleAfterSpecialIfDue() {
        if (advanceStyleWhenSpecialFinishes) style = style.next()
        advanceStyleWhenSpecialFinishes = false
    }

    fun scheduleMemoryLifecycle(startTick: Int, steps: List<AkkhaMemoryStep> = memorySteps) {
        require(steps.isNotEmpty())
        memoryHideAt = startTick + AkkhaTuning.MEMORY_HIDE_DELAY.value
        memoryReappearAt = steps.maxOf { it.resolvesAt } + AkkhaTuning.MEMORY_REAPPEAR_DELAY.value
        memoryHidden = false
    }

    fun pollMemoryVisibility(tick: Int): AkkhaMemoryVisibilityTransition? {
        if (!memoryHidden && memoryHideAt >= 0 && tick >= memoryHideAt) {
            memoryHidden = true
            memoryHideAt = -1
            return AkkhaMemoryVisibilityTransition.HIDE
        }
        if (memoryHidden && memoryReappearAt >= 0 && tick >= memoryReappearAt) {
            memoryHidden = false
            memoryReappearAt = -1
            return AkkhaMemoryVisibilityTransition.REAPPEAR
        }
        return null
    }

    fun clearMemoryLifecycle() {
        memorySteps.clear()
        memoryHideAt = -1
        memoryReappearAt = -1
        memoryHidden = false
    }

    fun queueCrossedThresholds(previous: Int, current: Int, maximum: Int): List<Int> {
        if (
            maximum <= 0 ||
                pendingThresholds.isNotEmpty() ||
                activeShadowThreshold != null ||
                state in
                    setOf(AkkhaState.FINAL_STAND_INTRO, AkkhaState.FINAL_STAND, AkkhaState.DEFEATED)
        )
            return emptyList()
        val old = previous.coerceAtLeast(0) * 100 / maximum
        val now = current.coerceAtLeast(0) * 100 / maximum
        val threshold = unusedThresholds.firstOrNull() ?: return emptyList()
        if (old <= threshold || now > threshold) return emptyList()
        unusedThresholds.removeFirst()
        pendingThresholds += threshold
        return listOf(threshold)
    }

    fun capDamageAtNextShadowThreshold(
        currentHitpoints: Int,
        proposedDamage: Int,
        maximum: Int,
    ): Int {
        if (proposedDamage <= 0 || maximum <= 0 || state != AkkhaState.NORMAL_COMBAT) return 0
        if (pendingThresholds.isNotEmpty() || activeShadowThreshold != null) return 0
        val threshold = unusedThresholds.firstOrNull() ?: return proposedDamage
        val thresholdHitpoints = (maximum * threshold + 99) / 100
        val damageUntilThreshold = (currentHitpoints - thresholdHitpoints).coerceAtLeast(0)
        return proposedDamage.coerceAtMost(damageUntilThreshold)
    }

    fun allShadowsDefeated(): Boolean = usedShadowQuadrants.containsAll(AkkhaQuadrant.entries)

    fun canDisruptShadow(q: AkkhaQuadrant): Boolean =
        activeShadowThreshold != null &&
            completedShadow == null &&
            pendingDisruptedShadow == null &&
            q !in usedShadowQuadrants

    /** Reserves the one eligible shadow death for this threshold; simultaneous deaths lose. */
    fun recordShadowDeath(q: AkkhaQuadrant): Boolean {
        if (!canDisruptShadow(q)) return false
        pendingDisruptedShadow = q
        return true
    }

    fun hasUnresolvedShadowThresholds(): Boolean =
        unusedThresholds.isNotEmpty() ||
            pendingThresholds.isNotEmpty() ||
            activeShadowThreshold != null

    fun beginNextShadow(tick: Int): Int? {
        if (activeShadowThreshold != null || pendingThresholds.isEmpty()) return null
        return pendingThresholds.removeFirst().also {
            activeShadowThreshold = it
            completedShadow = null
            pendingDisruptedShadow = null
            shadowBlast = null
            shadowChargeEndsAt = tick + AkkhaTuning.SHADOW_CHARGE.value
            state = AkkhaState.SHADOW_TRANSITION
        }
    }

    fun startShadowBlast(tick: Int, living: Set<AkkhaQuadrant>): AkkhaFloorBlast? {
        if (
            state !in
                setOf(
                    AkkhaState.NORMAL_COMBAT,
                    AkkhaState.SHADOW_TRANSITION,
                    AkkhaState.SPECIAL_ATTACK,
                ) || living.isEmpty()
        ) {
            return null
        }
        return AkkhaFloorBlast(
                quadrants = living.toSet(),
                startedAt = tick,
                resolvesAt = tick + AkkhaTuning.SHADOW_BLAST_WAVE_TICKS.value,
            )
            .also {
                shadowBlast = it
                shadowChargeEndsAt = -1
            }
    }

    fun finishShadowBlast(tick: Int) {
        if (
            state !in
                setOf(
                    AkkhaState.NORMAL_COMBAT,
                    AkkhaState.SHADOW_TRANSITION,
                    AkkhaState.SPECIAL_ATTACK,
                )
        )
            return
        shadowBlast = null
        shadowChargeEndsAt = tick + AkkhaTuning.SHADOW_CHARGE.value
    }

    fun completeShadow(q: AkkhaQuadrant, tick: Int): Boolean {
        if (
            state !in setOf(AkkhaState.SHADOW_TRANSITION, AkkhaState.SPECIAL_ATTACK) ||
                activeShadowThreshold == null ||
                completedShadow != null ||
                q in usedShadowQuadrants
        )
            return false
        val reserved = pendingDisruptedShadow
        if (reserved != null && reserved != q) return false
        if (reserved == null && !recordShadowDeath(q)) return false
        completedShadow = q
        usedShadowQuadrants += q
        pendingDisruptedShadow = null
        quadrant = q
        activeShadowThreshold = null
        shadowHeadbarActors.remove(q)
        shadowBlast = shadowBlast?.let { it.copy(quadrants = it.quadrants - q) }
        // The other three actors and their shared charge continue after Akkha becomes vulnerable.
        // Akkha remains protected until his footprint is physically lured into this safe quadrant.
        return true
    }

    fun confirmLure(q: AkkhaQuadrant): Boolean {
        if (state != AkkhaState.SHADOW_TRANSITION || pendingLureQuadrant != q) return false
        completedShadow = null
        state = AkkhaState.NORMAL_COMBAT
        return true
    }

    fun hasChargingShadows(): Boolean =
        shadowHeadbarActors.isNotEmpty() && (shadowChargeEndsAt >= 0 || shadowBlast != null)

    fun pauseShadowCharge(ticks: Int = 1) {
        if (ticks <= 0 || shadowBlast != null || !hasChargingShadows()) return
        shadowChargeEndsAt += ticks
    }

    fun canReceiveDamage(type: HitType) =
        when (state) {
            AkkhaState.NORMAL_COMBAT -> type != style.hitType
            AkkhaState.FINAL_STAND -> type == HitType.Melee
            else -> false
        }

    fun startTrailingOrbs(tick: Int) {
        trailStartsAt = tick + AkkhaTuning.TRAIL_INITIAL_GRACE.value
        trailEndsAt = trailStartsAt + AkkhaTuning.TRAIL_LIFETIME.value
        trailLastTiles.clear()
        trailOrbs.clear()
    }

    /** Each spawned orb resolves independently; do not merge overlapping contacts synthetically. */
    @Suppress("UNUSED_PARAMETER") fun claimTrailCollision(uid: PlayerUid, tick: Int): Boolean = true

    fun beginFinalStand() {
        generation++
        state = AkkhaState.FINAL_STAND_INTRO
        activeSpecials.clear()
        memorySteps.clear()
        shadowBlast = null
        shadowHeadbarActors.clear()
        finalOrbs.clear()
        detonation = null
        trailStartsAt = -1
        trailEndsAt = -1
        trailLastTiles.clear()
        trailOrbs.clear()
        pendingThresholds.clear()
        activeShadowThreshold = null
        shadowChargeEndsAt = -1
        finalStandHitCounter = 0
        finalStandLocationIndex = -1
        finalStandIntroStage = 0
        finalStandIntroStartedAt = -1
        finalStandTransitionAt = -1
        finalStandBossActorKey = null
        finalStandDecoyActorKeys.clear()
        pendingFinalStandSwap = null
    }

    fun scheduleFinalStandIntro(tick: Int) {
        check(state == AkkhaState.FINAL_STAND_INTRO)
        finalStandIntroStage = 0
        finalStandIntroStartedAt = tick
        finalStandTransitionAt = tick + AkkhaTuning.FINAL_STAND_STAGE_OFFSETS.value[1]
    }

    fun advanceFinalStandIntro(tick: Int): Int {
        if (state != AkkhaState.FINAL_STAND_INTRO || tick < finalStandTransitionAt) return -1
        finalStandIntroStage++
        val offsets = AkkhaTuning.FINAL_STAND_STAGE_OFFSETS.value
        finalStandTransitionAt =
            if (finalStandIntroStage + 1 in offsets.indices) {
                finalStandIntroStartedAt + offsets[finalStandIntroStage + 1]
            } else {
                -1
            }
        return finalStandIntroStage
    }

    fun activateFinalStand(tick: Int) {
        check(state == AkkhaState.FINAL_STAND_INTRO)
        state = AkkhaState.FINAL_STAND
        finalStandTransitionAt = -1
        if (finalStandLocationIndex !in AkkhaArenaLayout.finalStandAnchors.indices) {
            finalStandLocationIndex = 0
        }
        nextFinalOrbAt = tick + AkkhaTuning.FINAL_ORB_INITIAL_GRACE.value
    }

    fun registerFinalStandFormation(
        bossActorKey: Int,
        bossIndex: Int,
        decoyActorKeys: Map<Int, Int>,
    ) {
        check(state in setOf(AkkhaState.FINAL_STAND_INTRO, AkkhaState.FINAL_STAND))
        val indices = AkkhaArenaLayout.finalStandAnchors.indices
        require(bossIndex in indices)
        require(decoyActorKeys.keys == indices.filterNot { it == bossIndex }.toSet())
        require(decoyActorKeys.values.toSet().size == decoyActorKeys.size)
        require(bossActorKey !in decoyActorKeys.values)
        finalStandBossActorKey = bossActorKey
        finalStandLocationIndex = bossIndex
        finalStandDecoyActorKeys.clear()
        finalStandDecoyActorKeys.putAll(decoyActorKeys)
        pendingFinalStandSwap = null
    }

    fun finalStandActorKey(index: Int): Int? =
        if (index == finalStandLocationIndex) finalStandBossActorKey
        else finalStandDecoyActorKeys[index]

    fun recordFinalStandHit(party: Int): Boolean {
        if (state != AkkhaState.FINAL_STAND || pendingFinalStandSwap != null) return false
        finalStandHitCounter++
        if (finalStandHitCounter < hitsBeforeFinalStandTeleport(party)) return false
        finalStandHitCounter = 0
        val from = finalStandLocationIndex.coerceAtLeast(0)
        val destinations = AkkhaArenaLayout.finalStandAnchors.indices.filterNot { it == from }
        val to = destinations[nextInt(destinations.size)]
        pendingFinalStandSwap =
            AkkhaFinalStandSwap(
                fromIndex = from,
                toIndex = to,
                outgoingBossActorKey = finalStandBossActorKey,
                incomingBossActorKey = finalStandDecoyActorKeys[to],
            )
        finalStandLocationIndex = to
        return true
    }

    fun completeFinalStandSwap(): AkkhaFinalStandSwap? {
        val swap = pendingFinalStandSwap ?: return null
        val outgoing = swap.outgoingBossActorKey
        val incoming = swap.incomingBossActorKey
        if (outgoing != null && incoming != null) {
            finalStandDecoyActorKeys.remove(swap.toIndex)
            finalStandDecoyActorKeys[swap.fromIndex] = outgoing
            finalStandBossActorKey = incoming
        }
        pendingFinalStandSwap = null
        return swap
    }

    fun nextInt(bound: Int) = random.nextInt(bound)

    fun nextBoolean() = random.nextBoolean()

    fun nextOrbKey() = ++finalOrbKey
}

internal object AkkhaShadowLifecycle {
    fun requiresStationarySpawn(id: Int, role: ToaNpcRole): Boolean =
        (role == ToaNpcRole.ADD && id in ToaNpcs.akkhaShadows) ||
            (role == ToaNpcRole.HAZARD && id == AkkhaAssets.HEADBAR)

    /** Cache-native outward initial facing for each stationary shadow. */
    fun spawnDirection(quadrant: AkkhaQuadrant): Direction =
        when (quadrant) {
            AkkhaQuadrant.BURN -> Direction.NorthWest
            AkkhaQuadrant.DARKNESS -> Direction.NorthEast
            AkkhaQuadrant.LIGHTNING -> Direction.SouthEast
            AkkhaQuadrant.FREEZE -> Direction.SouthWest
        }

    fun spawnTile(center: CoordGrid, quadrant: AkkhaQuadrant): CoordGrid {
        val anchor = requireNotNull(AkkhaArenaLayout.shadowAnchors[quadrant])
        return CoordGrid(
            center.x + anchor.first - AkkhaArenaLayout.CENTER_X,
            center.z + anchor.second - AkkhaArenaLayout.CENTER_Z,
            center.level,
        )
    }

    fun hourglassTile(center: CoordGrid, quadrant: AkkhaQuadrant): CoordGrid {
        val anchor = requireNotNull(AkkhaArenaLayout.headbarAnchors[quadrant])
        return CoordGrid(
            center.x + anchor.first - AkkhaArenaLayout.CENTER_X,
            center.z + anchor.second - AkkhaArenaLayout.CENTER_Z,
            center.level,
        )
    }
}

internal object AkkhaShadowController {
    fun thresholdsCrossed(old: Int, now: Int, max: Int) =
        AkkhaTuning.SHADOW_THRESHOLDS.value.filter { old * 100 / max > it && now * 100 / max <= it }
}

internal object AkkhaFloorBlastController {
    fun ringsForWaveTick(waveTick: Int, totalTicks: Int): List<Int> {
        require(totalTicks > 0)
        if (waveTick !in 0 until totalTicks) return emptyList()
        val levels = AkkhaArenaLayout.ARENA_RADIUS + 1
        val from = waveTick * levels / totalTicks
        val until = (waveTick + 1) * levels / totalTicks
        return (from until until).map { AkkhaArenaLayout.ARENA_RADIUS - it }
    }

    fun quadrantTiles(center: CoordGrid, quadrants: Set<AkkhaQuadrant>): List<CoordGrid> =
        AkkhaArenaLayout.floorTiles(quadrants).map { (localX, localZ) ->
            CoordGrid(
                center.x + localX - AkkhaArenaLayout.CENTER_X,
                center.z + localZ - AkkhaArenaLayout.CENTER_Z,
                center.level,
            )
        }

    fun waveTiles(
        center: CoordGrid,
        quadrants: Set<AkkhaQuadrant>,
        waveTick: Int,
        totalTicks: Int,
    ): List<CoordGrid> {
        val rings = ringsForWaveTick(waveTick, totalTicks).toSet()
        if (rings.isEmpty()) return emptyList()
        return quadrantTiles(center, quadrants).filter { tile ->
            maxOf(abs(tile.x - center.x), abs(tile.z - center.z)) in rings
        }
    }

    fun markerTile(center: CoordGrid, quadrant: AkkhaQuadrant): CoordGrid {
        return CoordGrid(
            center.x + quadrant.symbolAnchor.first - AkkhaArenaLayout.CENTER_X,
            center.z + quadrant.symbolAnchor.second - AkkhaArenaLayout.CENTER_Z,
            center.level,
        )
    }

    /** The three unsafe quadrants contain exactly 225 clipped floor tiles. */
    fun memoryBlastTiles(
        center: CoordGrid,
        safeQuadrant: AkkhaQuadrant,
    ): Map<AkkhaQuadrant, List<CoordGrid>> =
        AkkhaQuadrant.entries
            .filterNot { it == safeQuadrant }
            .associateWith { quadrantTiles(center, setOf(it)) }

    fun memoryBlastDelay(center: CoordGrid, tile: CoordGrid, quadrant: AkkhaQuadrant): Int {
        require(quadrantAt(center, tile) == quadrant)
        val localX = AkkhaArenaLayout.CENTER_X + tile.x - center.x
        val localZ = AkkhaArenaLayout.CENTER_Z + tile.z - center.z
        val (innerX, innerZ) =
            when (quadrant) {
                AkkhaQuadrant.BURN -> 32 to 32
                AkkhaQuadrant.DARKNESS -> 33 to 32
                AkkhaQuadrant.LIGHTNING -> 33 to 31
                AkkhaQuadrant.FREEZE -> 32 to 31
            }
        return 30 + 3 * (abs(localX - innerX) + abs(localZ - innerZ))
    }

    fun quadrantAt(center: CoordGrid, tile: CoordGrid): AkkhaQuadrant? {
        if (tile.level != center.level) return null
        val localX = AkkhaArenaLayout.CENTER_X + tile.x - center.x
        val localZ = AkkhaArenaLayout.CENTER_Z + tile.z - center.z
        if (!AkkhaArenaLayout.isFloorTile(localX, localZ)) return null
        return AkkhaArenaLayout.quadrant(localX, localZ)
    }
}

internal object MemoryBlastController {
    fun iterations(path: Int): Int {
        val bracket =
            when (path.coerceAtLeast(0)) {
                0,
                1 -> 0
                2,
                3 -> 1
                else -> 2
            }
        return AkkhaTuning.MEMORY_ITERATIONS_BY_PATH_BRACKET.value[bracket]
    }

    fun sequence(start: Int, count: Int, nextDirection: () -> Boolean): List<AkkhaQuadrant> {
        require(count > 0)
        var current = Math.floorMod(start, AkkhaQuadrant.entries.size)
        return buildList {
            add(AkkhaQuadrant.entries[current])
            repeat(count - 1) {
                current =
                    Math.floorMod(
                        current + if (nextDirection()) 1 else -1,
                        AkkhaQuadrant.entries.size,
                    )
                add(AkkhaQuadrant.entries[current])
            }
        }
    }

    fun sequence(start: Int, count: Int): List<AkkhaQuadrant> {
        val seeded = Random(start.toLong())
        return sequence(start, count, seeded::nextBoolean)
    }

    fun schedule(
        startTick: Int,
        start: Int,
        count: Int,
        window: Int,
        nextDirection: () -> Boolean,
    ): List<AkkhaMemoryStep> {
        require(window > 0)
        val firstReveal = startTick + AkkhaTuning.MEMORY_INITIAL_REVEAL.value
        val extraRevealTicks =
            (count - 4).coerceAtLeast(0) * AkkhaTuning.MEMORY_REVEAL_INTERVAL.value
        val firstResolve =
            startTick + AkkhaTuning.MEMORY_FIRST_RESOLUTION_DELAY.value + extraRevealTicks
        return sequence(start, count, nextDirection).mapIndexed { index, quadrant ->
            val resolve = firstResolve + index * window
            AkkhaMemoryStep(
                quadrant,
                firstReveal + index * AkkhaTuning.MEMORY_REVEAL_INTERVAL.value,
                resolve,
                resolve,
            )
        }
    }

    fun schedule(startTick: Int, start: Int, count: Int, window: Int): List<AkkhaMemoryStep> {
        val seeded = Random(start.toLong())
        return schedule(startTick, start, count, window, seeded::nextBoolean)
    }

    fun duration(startTick: Int, steps: List<AkkhaMemoryStep>): Int =
        (steps.maxOfOrNull { it.resolvesAt } ?: startTick) +
            AkkhaTuning.MEMORY_REAPPEAR_DELAY.value - startTick + 1

    fun revealsFinishBeforeResolution(steps: List<AkkhaMemoryStep>): Boolean =
        steps.isEmpty() || steps.maxOf { it.revealAt } < steps.minOf { it.resolvesAt }
}

internal object DetonateController {
    fun isRayTile(tile: CoordGrid, origin: CoordGrid, diagonal: Boolean): Boolean {
        if (tile.level != origin.level || tile == origin) return false
        val dx = abs(tile.x - origin.x)
        val dz = abs(tile.z - origin.z)
        if (maxOf(dx, dz) > AkkhaTuning.DETONATE_RADIUS.value) return false
        return if (diagonal) dx == 1 && dz == 1 else (dx == 0) xor (dz == 0)
    }
}

internal object TrailingOrbController {
    fun traversedTiles(old: CoordGrid, now: CoordGrid): List<CoordGrid> {
        if (old.level != now.level || old == now) return emptyList()
        val n = old.chebyshevDistance(now)
        val dx = (now.x - old.x).coerceIn(-1, 1)
        val dz = (now.z - old.z).coerceIn(-1, 1)
        return (0 until n).map { CoordGrid(old.x + dx * it, old.z + dz * it, old.level) }
    }

    fun oneTileAhead(old: CoordGrid, now: CoordGrid): CoordGrid? {
        if (old.level != now.level || old == now) return null
        val dx = (now.x - old.x).coerceIn(-1, 1)
        val dz = (now.z - old.z).coerceIn(-1, 1)
        return CoordGrid(now.x + dx, now.z + dz, now.level)
    }

    /** Compatibility shim for callers migrating to the corrected one-tile Feeling Special lead. */
    fun twoTilesAhead(old: CoordGrid, now: CoordGrid) = oneTileAhead(old, now)
}

internal object AkkhaFinalOrbController {
    private val laneDirections =
        listOf(
            AkkhaOrbDirection.NORTH,
            AkkhaOrbDirection.WEST,
            AkkhaOrbDirection.SOUTH,
            AkkhaOrbDirection.EAST,
            AkkhaOrbDirection.NORTH_EAST,
            AkkhaOrbDirection.NORTH_WEST,
            AkkhaOrbDirection.SOUTH_WEST,
            AkkhaOrbDirection.SOUTH_EAST,
        )
    private val floor = AkkhaArenaLayout.floorTiles().toSet()

    fun spawn(center: CoordGrid, lane: Int, offset: Int): Pair<CoordGrid, AkkhaOrbDirection> {
        val path = path(center, lane, offset)
        return path.first() to laneDirections[lane]
    }

    fun path(center: CoordGrid, lane: Int, offset: Int): List<CoordGrid> {
        require(lane in laneDirections.indices)
        require(offset in -AkkhaArenaLayout.ARENA_RADIUS..AkkhaArenaLayout.ARENA_RADIUS)
        val direction = laneDirections[lane]
        val entries = entryTiles(direction)
        val entry = entries[Math.floorMod(offset, entries.size)]
        return buildList {
            var current = entry
            while (current in floor) {
                add(
                    CoordGrid(
                        center.x + current.first - AkkhaArenaLayout.CENTER_X,
                        center.z + current.second - AkkhaArenaLayout.CENTER_Z,
                        center.level,
                    )
                )
                current = current.first + direction.dx to current.second + direction.dz
            }
        }
    }

    fun entryTiles(direction: AkkhaOrbDirection): List<Pair<Int, Int>> =
        floor
            .filter { tile ->
                val previous = tile.first - direction.dx to tile.second - direction.dz
                val following = tile.first + direction.dx to tile.second + direction.dz
                previous !in floor && following in floor
            }
            .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })

    fun next(tile: CoordGrid, direction: AkkhaOrbDirection): CoordGrid =
        CoordGrid(tile.x + direction.dx, tile.z + direction.dz, tile.level)

    fun collides(
        orbTile: CoordGrid,
        nextOrbTile: CoordGrid,
        previousPlayerTile: CoordGrid,
        playerTile: CoordGrid,
    ): Boolean {
        val playerTiles =
            (TrailingOrbController.traversedTiles(previousPlayerTile, playerTile) + playerTile)
                .toSet()
        return orbTile in playerTiles || nextOrbTile in playerTiles
    }
}

internal object AkkhaFinalStandController {
    fun restoredHitpoints(max: Int) =
        (max.coerceAtLeast(1) * AkkhaTuning.FINAL_STAND_HEAL.value / 100).coerceAtLeast(1)

    fun hitsBeforeTeleport(party: Int) =
        if (party <= 1) AkkhaTuning.SOLO_FINAL_HITS.value
        else party + AkkhaTuning.TEAM_FINAL_HIT_OFFSET.value
}

internal fun akkhaFormId(style: Int) = AkkhaStyle.entries[Math.floorMod(style, 3)].npcId

internal fun akkhaProtectedHitType(style: Int) = AkkhaStyle.entries[Math.floorMod(style, 3)].hitType

internal fun akkhaAttacksBeforeSpecial(stayVigilant: Boolean) =
    AkkhaInvocationRules.attacksBeforeSpecial(stayVigilant)

internal fun akkhaFinalSwapHits(party: Int) = AkkhaFinalStandController.hitsBeforeTeleport(party)

internal fun hitsBeforeFinalStandTeleport(party: Int) =
    AkkhaFinalStandController.hitsBeforeTeleport(party)

internal fun akkhaFinalHitpoints(max: Int) = AkkhaFinalStandController.restoredHitpoints(max)

internal fun akkhaMemorySequence(start: Int, count: Int) =
    MemoryBlastController.sequence(start, count)

internal fun memoryIterations(path: Int) = MemoryBlastController.iterations(path)

internal fun isAkkhaDetonationTile(tile: CoordGrid, origin: CoordGrid, diagonal: Boolean) =
    DetonateController.isRayTile(tile, origin, diagonal)
