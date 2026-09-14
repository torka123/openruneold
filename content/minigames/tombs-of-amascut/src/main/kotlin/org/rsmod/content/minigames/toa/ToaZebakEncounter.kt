package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

/** Native revision-240 assets and raid-owned deterministic encounter rules for Zebak. */
internal object ZebakAssets {
    const val BODY = 11730
    const val TAIL = 11731
    const val ENRAGED_BODY = 11732
    const val DEAD_BODY = 11733
    const val DEAD_TAIL = 11734
    const val JUG = 11735
    const val ROLLING_JUG = 11736
    const val ROCK = 11737
    const val WAVE = 11738
    const val BLOODY_WAVE = 11739
    const val LAND_CROCODILE = 11740
    const val WATER_CROCODILE = 11741
    const val BLOOD_CLOUD = 11742
    const val SMALL_BLOOD_CLOUD = 11743
    const val SPLIT_HELPER = 11744

    const val BODY_IDLE = 9618
    const val TAIL_IDLE = 9619
    const val BODY_MELEE = 9620
    const val TAIL_MELEE = 9621
    const val BODY_ENRAGED_MELEE = 9622
    const val TAIL_ENRAGED_MELEE = 9623
    const val BODY_RANGE = 9624
    const val TAIL_RANGE = 9625
    const val BODY_ENRAGED_RANGE = 9626
    const val TAIL_ENRAGED_RANGE = 9627
    const val BODY_ROAR = 9628
    const val TAIL_ROAR = 9629
    const val BODY_SPECIAL = 9630
    const val TAIL_SPECIAL = 9631
    const val BODY_ACID = 9632
    const val TAIL_ACID = 9633
    const val BODY_DEATH = 9634
    const val TAIL_DEATH = 9635
    const val BODY_DEAD = 9636
    const val TAIL_DEAD = 9637
    const val JUG_PUSH_ANIMATION = 832

    const val POISON_PROJECTILE = 1555
    const val BOULDER_PROJECTILE = 2172
    const val JUG_PROJECTILE = 2173
    const val MAGIC_PROJECTILE = 2176
    const val ENRAGED_MAGIC_PROJECTILE = 2177
    const val RANGE_PROJECTILE = 2178
    const val ENRAGED_RANGE_PROJECTILE = 2179
    const val MELEE_SPLIT = 2180
    const val MAGIC_SPLIT = 2181
    const val RANGE_SPLIT = 2187
    const val ROAR_DUST = 2184
    const val RANGE_SPLIT_VISUAL = 2185
    const val MAGIC_SPLIT_VISUAL = 2186
    const val MAGIC_IMPACT = 131
    const val RANGE_IMPACT = 1103
    const val JUG_BREAK = 2192
    const val JUG_SPLASH = 2193
    const val ACID_PROJECTILE = 2194
    const val ROCK_FALL = 2195
    const val WATER_CLEANSE = 95
    const val CLIMB_SPLASH = 163

    const val BODY_BLOCKER_LOC = 3192
    const val COLLAPSED_ROCK_LOC = 29733
    const val LIVE_ROCK_BLOCKER_LOC = 43876
    const val ROCK_STEPS = 45509
    val ACID_LOCS = (45570..45576).toList()
    val locIds =
        ACID_LOCS.toSet() +
            setOf(BODY_BLOCKER_LOC, COLLAPSED_ROCK_LOC, LIVE_ROCK_BLOCKER_LOC, ROCK_STEPS)
    val npcIds =
        setOf(
            BODY,
            TAIL,
            ENRAGED_BODY,
            DEAD_BODY,
            DEAD_TAIL,
            JUG,
            ROLLING_JUG,
            ROCK,
            WAVE,
            BLOODY_WAVE,
            LAND_CROCODILE,
            WATER_CROCODILE,
            BLOOD_CLOUD,
            SMALL_BLOOD_CLOUD,
        )
    val validationNpcIds = npcIds + SPLIT_HELPER
    val sequenceIds = (BODY_IDLE..TAIL_DEAD).toSet()
    val spotanimIds =
        (MAGIC_PROJECTILE..ROCK_FALL).toSet() +
            setOf(
                WATER_CLEANSE,
                CLIMB_SPLASH,
                MAGIC_IMPACT,
                RANGE_IMPACT,
                POISON_PROJECTILE,
                BOULDER_PROJECTILE,
                JUG_PROJECTILE,
            )
}

internal object ZebakSounds {
    const val MAGIC_LAUNCH = 5823
    const val RANGE_LAUNCH = 5819
    const val MAGIC_SPLIT = 5878
    const val RANGE_SPLIT = 5896
    const val IMPACT = 5884
}

internal object ZebakTuning {
    const val BASE_HITPOINTS = 580
    const val BASE_DEFENCE = 70
    const val EMERGE_TICKS = 3
    const val DEATH_TRANSFORM_TICKS = 4
    const val MINIMUM_DRAINED_DEFENCE = 50
    const val BASE_ATTACK_INTERVAL = 7
    const val ENRAGED_ATTACK_INTERVAL = 4
    const val MELEE_MAX_HIT = 38
    const val MAGIC_MAX_HIT = 16
    const val RANGE_MAX_HIT = 16
    const val MELEE_PRAYER_REDUCTION = 75
    const val RANGE_MAGIC_PRAYER_REDUCTION = 100
    const val PRE_SPECIAL_AUTOS = 4
    const val HAZARD_LAUNCH_DELAY = 1
    const val HAZARD_LAND_DELAY = 6
    const val HAZARD_PROJECTILE_START_HEIGHT = 62
    const val HAZARD_PROJECTILE_END_HEIGHT = 0
    const val HAZARD_PROJECTILE_START_CYCLES = 30
    const val HAZARD_PROJECTILE_END_CYCLES = 150
    const val HAZARD_PROJECTILE_ANGLE = 35
    const val HAZARD_PROJECTILE_PROGRESS = 100
    const val BOULDER_PROJECTILE_END_CYCLES = 150
    const val WAVE_CALL_DELAY = 4
    const val WAVE_FIRST_ROW_DELAY = 13
    const val WAVE_FIRST_AUTO_DELAY = 14
    const val ROAR_START_DELAY = 33
    const val ROAR_FIRST_AUTO_DELAY = 10
    const val ROAR_FIRST_PULSE_DELAY = 4
    const val ROAR_PULSES = 3
    const val ROAR_PULSE_INTERVAL = 2
    const val ROAR_ROCK_CLEANUP_DELAY = 4
    const val ROAR_PUSH_TILES = 2
    const val ROAR_DAMAGE_MIN = 20
    const val ROAR_DAMAGE_ROLLS = 11
    const val SPECIAL_RECOVERY_TICKS = 3
    const val JUG_HITPOINTS = 5
    const val ROCK_HITPOINTS = 150
    const val ROCK_ROAR_DAMAGE = 50
    const val ACID_RADIUS = 1
    const val UPSET_ACID_RADIUS = 2
    const val WATER_RADIUS = 2
    const val UPSET_WATER_RADIUS = 1
    const val WAVE_BASE_GAP = 3
    const val WAVE_COUNT = 3
    const val WAVE_ROW_INTERVAL = 7
    const val WAVE_PUSH_TILES = 4
    const val WAVE_MIN_DAMAGE = 8
    const val WAVE_DAMAGE_ROLLS = 11
    const val INITIAL_PROJECTILE_TICKS = 4
    const val SPLIT_PROJECTILE_TICKS = 4
    const val SPLIT_VISUAL_TICKS = 3
    const val JUG_ROLL_STEPS = 8
    const val JUG_MIN_COUNT = 6
    const val JUG_COUNT_ROLLS = 3
    const val ACID_TICK_INTERVAL = 4
    const val ACID_BASE_DAMAGE = 4
    const val ACID_RAMP_DAMAGE = 2
    const val ACID_DAMAGE_CAP = 20
    const val MELEE_BLEED_CHANCE = 5
    const val MELEE_BLEED_MOVES = 5
    const val MELEE_BLEED_DAMAGE = 4
    const val BLOOD_INTERVAL = 20
    const val BLOOD_CLOUD_HITPOINTS = 40
    const val BLOOD_CLOUD_DECAY = 2
    const val BLOOD_CLOUD_CONTACT_INTERVAL = 4
    const val BLOOD_HEAL_NUMERATOR = 2
    const val BLOOD_HEAL_DENOMINATOR = 3
    // Public documentation specifies increased healing but not the exact current multiplier.
    // Keep this isolated for a live trace instead of scattering an inferred value in runtime code.
    const val ARTERIAL_HEAL_NUMERATOR = 1
    const val ARTERIAL_HEAL_DENOMINATOR = 1
    const val ACID_POISON_INITIAL_DAMAGE = 2
    const val WATER_CROCODILE_ATTACK_INTERVAL = 5
    const val WATER_CROCODILE_MAX_HIT = 18

    val thresholds = intArrayOf(85, 70, 55, 40)

    fun attackInterval(pathLevel: Int, enraged: Boolean): Int {
        val base = if (enraged) ENRAGED_ATTACK_INTERVAL else BASE_ATTACK_INTERVAL
        return (base - pathLevel.coerceAtLeast(0) / 2).coerceAtLeast(2)
    }

    fun waveGap(pathLevel: Int): Int =
        (WAVE_BASE_GAP - pathLevel.coerceAtLeast(0) / 2).coerceAtLeast(1)

    /** Allows exactly three ordinary attacks before a queued threshold special resolves. */
    fun preparationTicks(pathLevel: Int, enraged: Boolean): Int =
        (PRE_SPECIAL_AUTOS + 1) * attackInterval(pathLevel, enraged)

    fun jugCount(roll: Int): Int = JUG_MIN_COUNT + roll.coerceIn(0, JUG_COUNT_ROLLS - 1)

    fun rockCount(teamSize: Int): Int = if (teamSize <= 1) 2 else 3
}

/** Server-tick timelines shared by runtime code and deterministic regression tests. */
internal object ZebakTimeline {
    fun hazardLaunchAt(preparationTick: Int): Int =
        preparationTick + ZebakTuning.HAZARD_LAUNCH_DELAY

    fun hazardLandAt(preparationTick: Int): Int = preparationTick + ZebakTuning.HAZARD_LAND_DELAY

    fun firstAutoAt(entryTick: Int): Int =
        entryTick + ZebakTuning.EMERGE_TICKS + ZebakTuning.BASE_ATTACK_INTERVAL

    fun deathTransformAt(deathTick: Int): Int = deathTick + ZebakTuning.DEATH_TRANSFORM_TICKS

    fun splitAt(launchTick: Int): Int = launchTick + ZebakTuning.INITIAL_PROJECTILE_TICKS

    fun splitVisualRemoveAt(splitTick: Int): Int = splitTick + ZebakTuning.SPLIT_VISUAL_TICKS

    fun projectileImpactAt(launchTick: Int): Int =
        splitAt(launchTick) + ZebakTuning.SPLIT_PROJECTILE_TICKS

    fun waveReleaseTicks(startTick: Int): List<Int> =
        List(ZebakTuning.WAVE_COUNT) { index -> startTick + index * ZebakTuning.WAVE_ROW_INTERVAL }

    fun roarPulseTicks(roarTick: Int): List<Int> =
        List(ZebakTuning.ROAR_PULSES) { index ->
            roarTick + ZebakTuning.ROAR_FIRST_PULSE_DELAY + index * ZebakTuning.ROAR_PULSE_INTERVAL
        }

    fun jugBreakAt(collisionTick: Int): Int = collisionTick + 1

    fun jugAcidClearAt(collisionTick: Int): Int = jugBreakAt(collisionTick) + 1
}

internal object ZebakJugMotion {
    fun next(tile: CoordGrid, directionX: Int, directionZ: Int): CoordGrid =
        CoordGrid(tile.x + directionX.sign, tile.z + directionZ.sign, tile.level)

    fun unobstructedPath(start: CoordGrid, directionX: Int, directionZ: Int): List<CoordGrid> {
        require(directionX != 0 || directionZ != 0)
        return buildList {
            var tile = start
            repeat(ZebakTuning.JUG_ROLL_STEPS) {
                tile = next(tile, directionX, directionZ)
                add(tile)
            }
        }
    }
}

internal data class ZebakInvocationProfile(
    val bloodMagic: Boolean,
    val barrageRadius: Int,
    val bloodCloudCount: Int,
    val bloodCloudNpc: Int,
    val acidRadius: Int,
    val jugWaterRadius: Int,
    val arterialHealing: Boolean,
)

internal object ZebakInvocationRules {
    fun resolve(invocations: Set<ToaInvocation>): ZebakInvocationProfile {
        val bloodMagic = ToaInvocation.NOT_JUST_A_HEAD in invocations
        // Dependencies are enforced here too. A malformed UI state can never enable a child effect.
        val arterial = bloodMagic && ToaInvocation.ARTERIAL_SPRAY in invocations
        val thinners = bloodMagic && ToaInvocation.BLOOD_THINNERS in invocations
        val upset = ToaInvocation.UPSET_STOMACH in invocations
        return ZebakInvocationProfile(
            bloodMagic = bloodMagic,
            barrageRadius = if (arterial) 2 else 1,
            bloodCloudCount = if (thinners) 3 else 1,
            bloodCloudNpc =
                if (thinners) ZebakAssets.SMALL_BLOOD_CLOUD else ZebakAssets.BLOOD_CLOUD,
            acidRadius = if (upset) ZebakTuning.UPSET_ACID_RADIUS else ZebakTuning.ACID_RADIUS,
            jugWaterRadius =
                if (upset) ZebakTuning.UPSET_WATER_RADIUS else ZebakTuning.WATER_RADIUS,
            arterialHealing = arterial,
        )
    }

    fun healAmount(damageDealt: Int, arterialHealing: Boolean): Int {
        val damage = damageDealt.coerceAtLeast(0)
        return if (arterialHealing) {
            damage * ZebakTuning.ARTERIAL_HEAL_NUMERATOR / ZebakTuning.ARTERIAL_HEAL_DENOMINATOR
        } else {
            damage * ZebakTuning.BLOOD_HEAL_NUMERATOR / ZebakTuning.BLOOD_HEAL_DENOMINATOR
        }
    }
}

internal enum class ZebakState {
    IDLE,
    EMERGING,
    NORMAL,
    PREP_ROAR,
    ROAR,
    PREP_WAVES,
    WAVES,
    ENRAGED,
    DYING,
    COMPLETE,
    RESET,
}

internal enum class ZebakSpecial {
    WAVES,
    ROAR,
}

internal enum class ZebakAttackStyle {
    MELEE,
    RANGED,
    MAGIC,
}

/**
 * The Crondis challenge and Zebak boss share one live runtime, but their lifecycle gates differ.
 * The puzzle is usable only while its challenge is active. Entering Zebak happens after that
 * challenge has been marked complete, so applying the puzzle gate to the boss would leave Zebak
 * with no valid targets and prevent every ordinary attack.
 */
internal object CrondisZebakParticipationRules {
    fun permitsRoom(room: ToaRoom, challengeStage: ToaChallengeStage?): Boolean =
        when (room) {
            ToaRoom.CRONDIS -> challengeStage == ToaChallengeStage.ACTIVE
            ToaRoom.ZEBAK -> true
            else -> false
        }

    fun permitsPosition(
        room: ToaRoom,
        insideChallengeBounds: Boolean,
        insideZebakArena: Boolean,
    ): Boolean =
        when (room) {
            ToaRoom.CRONDIS -> insideChallengeBounds
            ToaRoom.ZEBAK -> insideZebakArena
            else -> false
        }

    fun permitsStateRetention(
        room: ToaRoom,
        challengeStage: ToaChallengeStage?,
        participantRoom: ToaRoom,
        spectating: Boolean,
    ): Boolean = permitsRoom(room, challengeStage) && participantRoom == room && !spectating

    fun permitsTracking(
        room: ToaRoom,
        challengeStage: ToaChallengeStage?,
        participantRoom: ToaRoom,
        connected: Boolean,
        spectating: Boolean,
    ): Boolean =
        permitsStateRetention(room, challengeStage, participantRoom, spectating) && connected

    fun permitsZebakCombatTarget(insideZebakArena: Boolean, swimming: Boolean): Boolean =
        insideZebakArena || swimming
}

internal data class ZebakRockStepRoute(
    val stepLocalX: Int,
    val stepLocalZ: Int,
    val landingLocalX: Int,
    val landingLocalZ: Int,
)

/** Cache-backed side steps and their safe interior landings in source region 15700. */
internal object ZebakRockSteps {
    const val STEP_LOCAL_X = 29
    const val LANDING_LOCAL_X = 30
    const val SOUTH_STEP_LOCAL_Z = 21
    const val NORTH_STEP_LOCAL_Z = 43
    const val SOUTH_LANDING_LOCAL_Z = 23
    const val NORTH_LANDING_LOCAL_Z = 41

    val routes =
        listOf(
            ZebakRockStepRoute(
                STEP_LOCAL_X,
                SOUTH_STEP_LOCAL_Z,
                LANDING_LOCAL_X,
                SOUTH_LANDING_LOCAL_Z,
            ),
            ZebakRockStepRoute(
                STEP_LOCAL_X,
                NORTH_STEP_LOCAL_Z,
                LANDING_LOCAL_X,
                NORTH_LANDING_LOCAL_Z,
            ),
        )

    fun routeFor(stepLocalX: Int, stepLocalZ: Int): ZebakRockStepRoute? =
        routes.firstOrNull { it.stepLocalX == stepLocalX && it.stepLocalZ == stepLocalZ }
}

internal enum class ZebakWaveDirection(val stepZ: Int) {
    SOUTH(-1),
    NORTH(1),
}

internal data class ZebakJugRuntime(
    var npc: Npc,
    var tile: CoordGrid,
    var rolling: Boolean = false,
    var directionX: Int = 0,
    var directionZ: Int = 0,
    var remainingSteps: Int = 0,
    var breaksAt: Int = -1,
)

internal data class ZebakWaterSplashRuntime(val tiles: Set<CoordGrid>, val clearsAt: Int)

internal data class ZebakRockRuntime(
    val npc: Npc,
    var hitpoints: Int = ZebakTuning.ROCK_HITPOINTS,
    var destroysAt: Int = -1,
)

internal data class ZebakWaveRuntime(
    val npc: Npc,
    var tile: CoordGrid,
    val direction: ZebakWaveDirection,
    val gapStartX: Int,
    val gapWidth: Int,
)

internal data class ZebakBloodCloudRuntime(
    val npc: Npc,
    val target: PlayerUid,
    val expiresAt: Int,
    var hitpoints: Int = ZebakTuning.BLOOD_CLOUD_HITPOINTS,
    var nextContactAt: Int = 0,
)

internal data class ZebakSplitProjectileRuntime(
    val tile: CoordGrid,
    val style: ZebakAttackStyle,
    val splitAt: Int,
    val generation: Int,
)

internal data class ZebakSplitVisualRuntime(val npc: Npc, val removesAt: Int, val generation: Int)

internal data class ZebakWaterCrocodileRuntime(
    val npc: Npc,
    val target: PlayerUid,
    var nextAttackAt: Int,
)

internal data class ZebakPlayerRuntime(
    var previousTile: CoordGrid? = null,
    var acidStack: Int = 0,
    var nextAcidAt: Int = 0,
    var bleedMoves: Int = 0,
    var swimming: Boolean = false,
)

internal class ZebakEncounter(val encounterId: UUID, private var rngState: Long) {
    var state = ZebakState.IDLE
    var generation = 0
    var body: Npc? = null
    var tail: Npc? = null
    var firstSpecial = if (nextInt(2) == 0) ZebakSpecial.WAVES else ZebakSpecial.ROAR
    var emergesAt = -1
    var specialStartsAt = -1
    var specialEndsAt = -1
    var hazardLaunchAt = -1
    var hazardLandAt = -1
    var hazardsLaunched = false
    var hazardsLanded = false
    var preparationIncludesRocks = false
    var pendingJugCount = 0
    var pendingRockCount = 0
    var roarPulses = 0
    var lastRoarAt = -1
    var nextThresholdIndex = 0
    var enrageTriggered = false
    var completedBy: PlayerUid? = null
    var completesAt = -1
    var primaryTarget: PlayerUid? = null
    var waveStage = 0
    var nextWaveAt = -1
    var waveOccurrence = 0
    var waveDirection = ZebakWaveDirection.NORTH
    var nextBloodAt = -1
    var deadBody: Npc? = null
    var deadTail: Npc? = null
    val pendingThresholds = ArrayDeque<Int>()
    val jugs = linkedMapOf<Int, ZebakJugRuntime>()
    val rocks = linkedMapOf<Int, ZebakRockRuntime>()
    val waves = linkedMapOf<Int, ZebakWaveRuntime>()
    val bloodClouds = linkedMapOf<Int, ZebakBloodCloudRuntime>()
    val splitProjectiles = mutableListOf<ZebakSplitProjectileRuntime>()
    val splitVisuals = mutableListOf<ZebakSplitVisualRuntime>()
    val waterSplashes = mutableListOf<ZebakWaterSplashRuntime>()
    val waterCrocodiles = linkedMapOf<PlayerUid, ZebakWaterCrocodileRuntime>()
    val acidTiles = linkedSetOf<CoordGrid>()
    val waterTiles = linkedSetOf<CoordGrid>()
    val waveHitAt = linkedMapOf<PlayerUid, Int>()
    val playerState = linkedMapOf<PlayerUid, ZebakPlayerRuntime>()

    fun reset() {
        generation++
        state = ZebakState.RESET
        body = null
        tail = null
        emergesAt = -1
        specialStartsAt = -1
        specialEndsAt = -1
        hazardLaunchAt = -1
        hazardLandAt = -1
        hazardsLaunched = false
        hazardsLanded = false
        preparationIncludesRocks = false
        pendingJugCount = 0
        pendingRockCount = 0
        roarPulses = 0
        lastRoarAt = -1
        nextThresholdIndex = 0
        enrageTriggered = false
        completedBy = null
        completesAt = -1
        primaryTarget = null
        waveStage = 0
        nextWaveAt = -1
        waveOccurrence = 0
        waveDirection = ZebakWaveDirection.NORTH
        nextBloodAt = -1
        deadBody = null
        deadTail = null
        pendingThresholds.clear()
        jugs.clear()
        rocks.clear()
        waves.clear()
        bloodClouds.clear()
        splitProjectiles.clear()
        splitVisuals.clear()
        waterSplashes.clear()
        waterCrocodiles.clear()
        acidTiles.clear()
        waterTiles.clear()
        waveHitAt.clear()
        playerState.clear()
    }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        if (completedBy == oldUid) completedBy = newUid
        if (primaryTarget == oldUid) primaryTarget = newUid
        waterCrocodiles.rebindKey(oldUid, newUid)
        waveHitAt.rebindKey(oldUid, newUid)
        playerState.rebindKey(oldUid, newUid)
        waterCrocodiles.replaceAll { _, runtime ->
            if (runtime.target == oldUid) runtime.copy(target = newUid) else runtime
        }
        bloodClouds.replaceAll { _, runtime ->
            if (runtime.target == oldUid) runtime.copy(target = newUid) else runtime
        }
    }

    fun retirePlayer(uid: PlayerUid) {
        if (completedBy == uid) completedBy = null
        if (primaryTarget == uid) primaryTarget = null
        waterCrocodiles.remove(uid)
        waveHitAt.remove(uid)
        playerState.remove(uid)
        bloodClouds.entries.removeIf { it.value.target == uid }
    }

    fun start(body: Npc, tail: Npc, cycle: Int) {
        reset()
        this.body = body
        this.tail = tail
        state = ZebakState.EMERGING
        emergesAt = cycle + ZebakTuning.EMERGE_TICKS
        nextBloodAt = emergesAt + ZebakTuning.BLOOD_INTERVAL
    }

    fun queueCrossedThresholds(previousHp: Int, currentHp: Int, maximum: Int): List<Int> {
        if (state in setOf(ZebakState.DYING, ZebakState.COMPLETE, ZebakState.RESET)) {
            return emptyList()
        }
        val crossed = mutableListOf<Int>()
        while (nextThresholdIndex < ZebakTuning.thresholds.size) {
            val threshold = ZebakTuning.thresholds[nextThresholdIndex]
            val thresholdHp = maximum * threshold / 100
            if (previousHp > thresholdHp && currentHp <= thresholdHp) {
                pendingThresholds.addLast(threshold)
                crossed += threshold
                nextThresholdIndex++
            } else if (previousHp <= thresholdHp) {
                nextThresholdIndex++
            } else {
                break
            }
        }
        return crossed
    }

    fun nextSpecial(): ZebakSpecial {
        val completed = nextThresholdIndex - pendingThresholds.size
        return if (completed % 2 == 0) firstSpecial else opposite(firstSpecial)
    }

    fun discardThresholdsForEnrage() {
        pendingThresholds.clear()
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        rngState = rngState xor (rngState shl 13)
        rngState = rngState xor (rngState ushr 7)
        rngState = rngState xor (rngState shl 17)
        return ((rngState and Long.MAX_VALUE) % bound).toInt()
    }

    private fun opposite(special: ZebakSpecial) =
        if (special == ZebakSpecial.WAVES) ZebakSpecial.ROAR else ZebakSpecial.WAVES
}

/** Cache-backed local geometry for source region 15700. */
internal object ZebakRoomLayout {
    const val PROJECTILE_ORIGIN_LOCAL_X = 21
    const val PROJECTILE_ORIGIN_LOCAL_Z = 32
    const val PROJECTILE_SPLIT_LOCAL_X = 29
    const val PROJECTILE_SPLIT_LOCAL_Z = 32
    const val PROJECTILE_SPLIT_HELPER_LOCAL_X = 26
    const val PROJECTILE_SPLIT_HELPER_LOCAL_Z = 29
    const val PROJECTILE_SPLIT_HELPER_SIZE = 7
    const val BODY_LOCAL_X = 14
    const val BODY_LOCAL_Z = 28
    const val TAIL_LOCAL_X = 5
    const val TAIL_LOCAL_Z = 27
    const val ENTRY_LOCAL_X = 37
    const val ENTRY_LOCAL_Z = 32
    const val ARENA_CENTER_LOCAL_X = 30
    const val ARENA_CENTER_LOCAL_Z = 32
    const val ARENA_MIN_LOCAL_X = 20
    const val ARENA_MAX_LOCAL_X = 41
    const val ARENA_MIN_LOCAL_Z = 21
    const val ARENA_MAX_LOCAL_Z = 42
    const val WAVE_MIN_LOCAL_X = 19
    const val WAVE_MAX_LOCAL_X = 39
    const val WAVE_SOUTH_LOCAL_Z = 21
    const val WAVE_NORTH_LOCAL_Z = 43
    const val WAVE_SOUTH_END_LOCAL_Z = 19
    const val WAVE_NORTH_END_LOCAL_Z = 45
    const val ACID_FLOOR_MIN_LOCAL_X = 25
    const val ACID_FLOOR_MAX_LOCAL_X = 37
    const val ACID_FLOOR_MIN_LOCAL_Z = 25
    const val ACID_FLOOR_MAX_LOCAL_Z = 38

    fun waveStartLocalZ(direction: ZebakWaveDirection): Int =
        when (direction) {
            ZebakWaveDirection.NORTH -> WAVE_SOUTH_LOCAL_Z
            ZebakWaveDirection.SOUTH -> WAVE_NORTH_LOCAL_Z
        }

    fun waveHasPassed(direction: ZebakWaveDirection, localZ: Int): Boolean =
        when (direction) {
            ZebakWaveDirection.NORTH -> localZ > WAVE_NORTH_END_LOCAL_Z
            ZebakWaveDirection.SOUTH -> localZ < WAVE_SOUTH_END_LOCAL_Z
        }

    fun contains(localX: Int, localZ: Int): Boolean =
        localX in ARENA_MIN_LOCAL_X..ARENA_MAX_LOCAL_X &&
            localZ in ARENA_MIN_LOCAL_Z..ARENA_MAX_LOCAL_Z

    fun acidFootprintFits(centerLocalX: Int, centerLocalZ: Int, radius: Int): Boolean =
        centerLocalX - radius >= ACID_FLOOR_MIN_LOCAL_X &&
            centerLocalX + radius <= ACID_FLOOR_MAX_LOCAL_X &&
            centerLocalZ - radius >= ACID_FLOOR_MIN_LOCAL_Z &&
            centerLocalZ + radius <= ACID_FLOOR_MAX_LOCAL_Z
}

/**
 * Acid centers run along the room's north/south axis. Every normal 3x3 or Upset Stomach 5x5
 * footprint is inset from the irregular shoreline so no acid loc can render in the surrounding
 * water. Keep the entries column-major: selecting a contiguous subset still produces long
 * north/south lanes instead of a wide east/west wall.
 */
internal object ZebakAcidLayout {
    private val NORMAL_CENTER_LOCAL_TILES =
        listOf(
            26 to 26,
            26 to 30,
            26 to 34,
            26 to 37,
            31 to 26,
            31 to 30,
            31 to 34,
            31 to 37,
            36 to 26,
            36 to 30,
            36 to 34,
            36 to 37,
        )

    private val UPSET_CENTER_LOCAL_TILES =
        listOf(
            27 to 27,
            27 to 31,
            27 to 36,
            31 to 27,
            31 to 31,
            31 to 36,
            35 to 27,
            35 to 31,
            35 to 36,
        )

    fun centerLocalTiles(upsetStomach: Boolean): List<Pair<Int, Int>> =
        if (upsetStomach) UPSET_CENTER_LOCAL_TILES else NORMAL_CENTER_LOCAL_TILES
}

internal object ZebakAnimationPairs {
    private val pairs =
        mapOf(
            ZebakAssets.BODY_IDLE to ZebakAssets.TAIL_IDLE,
            ZebakAssets.BODY_MELEE to ZebakAssets.TAIL_MELEE,
            ZebakAssets.BODY_ENRAGED_MELEE to ZebakAssets.TAIL_ENRAGED_MELEE,
            ZebakAssets.BODY_RANGE to ZebakAssets.TAIL_RANGE,
            ZebakAssets.BODY_ENRAGED_RANGE to ZebakAssets.TAIL_ENRAGED_RANGE,
            ZebakAssets.BODY_ROAR to ZebakAssets.TAIL_ROAR,
            ZebakAssets.BODY_SPECIAL to ZebakAssets.TAIL_SPECIAL,
            ZebakAssets.BODY_ACID to ZebakAssets.TAIL_ACID,
            ZebakAssets.BODY_DEATH to ZebakAssets.TAIL_DEATH,
            ZebakAssets.BODY_DEAD to ZebakAssets.TAIL_DEAD,
        )

    fun tailFor(bodySequence: Int): Int =
        requireNotNull(pairs[bodySequence]) {
            "No Zebak tail sequence for body sequence $bodySequence"
        }

    fun all(): Map<Int, Int> = pairs
}

internal object ZebakWaveCollision {
    fun crossed(
        previousWaveTile: CoordGrid,
        nextWaveTile: CoordGrid,
        previousPlayerTile: CoordGrid?,
        currentPlayerTile: CoordGrid,
    ): Boolean =
        currentPlayerTile == nextWaveTile ||
            (previousPlayerTile == nextWaveTile && currentPlayerTile == previousWaveTile)

    fun pushedTile(player: CoordGrid, direction: ZebakWaveDirection): CoordGrid =
        CoordGrid(player.x, player.z + direction.stepZ * ZebakTuning.WAVE_PUSH_TILES, player.level)
}

internal object ZebakGeometry {
    fun footprint(center: CoordGrid, radius: Int): Set<CoordGrid> = buildSet {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                add(CoordGrid(center.x + dx, center.z + dz, center.level))
            }
        }
    }

    fun applyJugWater(
        center: CoordGrid,
        upsetStomach: Boolean,
        acid: MutableSet<CoordGrid>,
        water: MutableSet<CoordGrid>,
    ): Set<CoordGrid> {
        val radius = if (upsetStomach) ZebakTuning.UPSET_WATER_RADIUS else ZebakTuning.WATER_RADIUS
        val footprint = footprint(center, radius)
        acid.removeAll(footprint)
        water.addAll(footprint)
        return footprint
    }

    fun applyAcid(
        center: CoordGrid,
        upsetStomach: Boolean,
        acid: MutableSet<CoordGrid>,
        water: MutableSet<CoordGrid>,
    ): Set<CoordGrid> {
        val radius = if (upsetStomach) ZebakTuning.UPSET_ACID_RADIUS else ZebakTuning.ACID_RADIUS
        val footprint = footprint(center, radius)
        water.removeAll(footprint)
        acid.addAll(footprint)
        return footprint
    }

    /** A player is safe only when the rock is between them and Zebak, no more than 3 tiles deep. */
    fun isCovered(zebak: CoordGrid, rock: CoordGrid, player: CoordGrid): Boolean {
        if (zebak.level != rock.level || rock.level != player.level) return false
        val dx = rock.x - zebak.x
        val dz = rock.z - zebak.z
        val horizontal = abs(dx) >= abs(dz)
        return if (horizontal) {
            val sameLine = player.z == rock.z
            val behind = if (dx > 0) player.x > rock.x else player.x < rock.x
            sameLine && behind && abs(player.x - rock.x) <= 3
        } else {
            val sameLine = player.x == rock.x
            val behind = if (dz > 0) player.z > rock.z else player.z < rock.z
            sameLine && behind && abs(player.z - rock.z) <= 3
        }
    }
}
