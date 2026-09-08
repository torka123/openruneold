package org.rsmod.content.minigames.toa

import java.util.UUID
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

internal enum class ToaEvidenceConfidence {
    HIGH_CONFIDENCE,
    TRACE_REQUIRED,
}

internal object CrondisAssets {
    const val PALM = 11700
    const val PALM_STAGE_2 = 11701
    const val PALM_STAGE_3 = 11702
    const val PALM_STAGE_4 = 11703
    const val PALM_COMPLETE = 11704
    const val CROCODILE = 11705
    const val WATER_CONTAINER = 27295
    const val CONTINUE_LOC = 45397
    const val WATERFALL_WET = 45398
    const val WATERFALL_DRY = 45399
    const val STATUE = 45415
    const val SPEAR = 45416
    const val SPEAR_IDLE = 9562
    const val STATUE_WARNING = 9563
    const val SPEAR_EXTEND = 9565
    const val CLOUD_TRAIL = 2129

    val npcIds = (PALM..CROCODILE).toSet()
    val locIds = setOf(CONTINUE_LOC, WATERFALL_WET, WATERFALL_DRY, STATUE, SPEAR)
    val sequenceIds = setOf(SPEAR_IDLE, STATUE_WARNING, SPEAR_EXTEND)
    val spotanimIds = setOf(2129, 2130, 2131)
}

internal object CrondisTuning {
    const val WATER_CONTAINER_CAPACITY = 100
    const val BASE_WATER_REQUIREMENT = 175
    const val ADDITIONAL_PLAYER_WATER_REQUIREMENT = 125
    const val WATERFALL_COOLDOWN_TICKS = 8
    const val SPIKE_CYCLE_TICKS = 10
    const val SPIKE_HIT_COOLDOWN_TICKS = 3
    const val CLOUD_BATCH_INTERVAL_TICKS = 5
    const val CLOUD_START_DELAY_TICKS = 2
    const val CLOUD_TILE_COUNT = 10
    const val CLOUD_TILES_PER_TICK = 3
    const val CLOUD_DANGEROUS_TICKS = 2
    const val CLOUD_HIT_COOLDOWN_TICKS = 2
    const val SPIKE_MIN_DAMAGE = 6
    const val CLOUD_MIN_DAMAGE = 5
    const val OBSTACLE_DAMAGE_ROLLS = 9
    const val CROCODILE_PALM_MIN_HIT = 2
    const val CROCODILE_PALM_DAMAGE_ROLLS = 4
    const val CROCODILE_ATTACK_INTERVAL = 7
    const val CROCODILE_SPAWN_INTERVAL_TICKS = 50
    const val CROCODILE_AGGRO_RANGE = 3
    const val CROCODILE_MAX_ALIVE = 8
    const val CROCODILE_OBSTACLE_WINDOW_TICKS = 50
    const val CROCODILE_BASE_MAX_HIT = 18
    const val CROCODILE_PATH_LEVEL_BONUS = 3
    const val CROCODILE_MAX_HIT_CAP = 36
    const val CROCODILE_MELEE_PRAYER_REDUCTION = 67
    const val CROCODILE_PRAYER_DRAIN = 12
    const val PERFECT_TICKS = 100

    /*
     * Current public evidence proves the rare 16% spill exists, but not its probability. Keep the
     * already-live 1/6 compatibility value isolated here until a live OSRS trace can replace it.
     */
    const val TRACE_REQUIRED_RARE_SPILL_DENOMINATOR = 6

    /**
     * The Palm of Resourcefulness starts at 175 water for a solo raid and gains 125 water for each
     * additional original party member. This intentionally uses the entered party size, not the
     * current connected count, so disconnecting cannot lower an in-progress room requirement.
     */
    fun requiredWater(originalPartySize: Int): Int =
        BASE_WATER_REQUIREMENT +
            ADDITIONAL_PLAYER_WATER_REQUIREMENT * (originalPartySize.coerceAtLeast(1) - 1)

    fun crocodilesPerWave(partySize: Int): Int =
        ((partySize.coerceIn(1, 8) + 1) / 2).coerceAtMost(4)

    fun crocodileDamage(recentObstacleHits: Int): Int =
        (CROCODILE_BASE_MAX_HIT + recentObstacleHits.coerceAtLeast(0) * CROCODILE_PATH_LEVEL_BONUS)
            .coerceAtMost(CROCODILE_MAX_HIT_CAP)
}

internal enum class CrondisState {
    IDLE,
    ACTIVE,
    COMPLETE,
    RESET,
}

internal enum class CrondisCorridor {
    NORTH_WEST,
    NORTH_EAST,
    SOUTH_WEST,
    SOUTH_EAST,
}

internal enum class CrondisObstacleKind {
    SPIKE,
    CLOUD,
}

internal enum class CrondisSpikePhase {
    WARNING,
    PREPARING,
    EXTENDING,
    DANGEROUS,
    RETRACTING,
    COOLDOWN,
}

internal enum class CrondisCloudPhase {
    INACTIVE,
    WARNING,
    DANGEROUS,
    EXPIRED,
}

internal enum class CrondisSpillOutcome(val remainingPercent: Int) {
    NORMAL(50),
    RARE(84),
}

internal data class CrondisSpikeEmitter(
    val index: Int,
    val delay: Int,
    val statueLocal: CoordGrid,
    val spearLocal: CoordGrid,
    val dangerousLocals: List<CoordGrid>,
)

internal data class CrondisPathDefinition(
    val corridor: CrondisCorridor,
    val spikeBaseX: Int,
    val spikeBaseZ: Int,
    val cloudBaseX: Int,
    val cloudBaseZ: Int,
    val cloudDirectionZ: Int,
    val spikeDelays: List<Int>,
) {
    init {
        require(spikeDelays.size == 10)
        require(cloudDirectionZ == -1 || cloudDirectionZ == 1)
    }

    val emitters: List<CrondisSpikeEmitter> =
        spikeDelays.mapIndexed { index, delay ->
            val westEmitter = index % 2 == 0
            val statueX = if (westEmitter) spikeBaseX else spikeBaseX + 7
            val spearX = if (westEmitter) spikeBaseX + 2 else spikeBaseX + 4
            val z = spikeBaseZ + index
            CrondisSpikeEmitter(
                index = index,
                delay = delay,
                statueLocal = CoordGrid(statueX, z, 0),
                spearLocal = CoordGrid(spearX, z, 0),
                dangerousLocals = (spearX..spearX + 2).map { CoordGrid(it, z, 0) },
            )
        }

    fun cloudLocal(lane: Int, tileIndex: Int): CoordGrid {
        require(lane in 0..4)
        require(tileIndex in 0 until CrondisTuning.CLOUD_TILE_COUNT)
        return CoordGrid(cloudBaseX + lane, cloudBaseZ + cloudDirectionZ * (tileIndex + 1), 0)
    }
}

internal object CrondisLayout {
    val paths =
        listOf(
            CrondisPathDefinition(
                CrondisCorridor.NORTH_WEST,
                spikeBaseX = 21,
                spikeBaseZ = 45,
                cloudBaseX = 23,
                cloudBaseZ = 55,
                cloudDirectionZ = -1,
                spikeDelays = listOf(0, 2, 4, 6, 8, 0, 2, 4, 6, 8),
            ),
            CrondisPathDefinition(
                CrondisCorridor.NORTH_EAST,
                spikeBaseX = 35,
                spikeBaseZ = 45,
                cloudBaseX = 37,
                cloudBaseZ = 55,
                cloudDirectionZ = -1,
                spikeDelays = listOf(0, 3, 6, 9, 2, 0, 3, 6, 9, 2),
            ),
            CrondisPathDefinition(
                CrondisCorridor.SOUTH_WEST,
                spikeBaseX = 21,
                spikeBaseZ = 10,
                cloudBaseX = 23,
                cloudBaseZ = 9,
                cloudDirectionZ = 1,
                spikeDelays = listOf(2, 1, 1, 0, 0, 2, 2, 1, 1, 0),
            ),
            CrondisPathDefinition(
                CrondisCorridor.SOUTH_EAST,
                spikeBaseX = 35,
                spikeBaseZ = 10,
                cloudBaseX = 37,
                cloudBaseZ = 9,
                cloudDirectionZ = 1,
                spikeDelays = listOf(0, 1, 2, 3, 4, 4, 3, 2, 1, 0),
            ),
        )
    private val pathsByCorridor = paths.associateBy(CrondisPathDefinition::corridor)

    val waterfallLocals =
        listOf(CoordGrid(22, 2, 0), CoordGrid(36, 2, 0), CoordGrid(22, 58, 0), CoordGrid(36, 58, 0))
    val continueLocal = CoordGrid(12, 31, 0)
    val palmLocal = CoordGrid(32, 32, 0)
    // Revision-240 room locals for the four periodic crocodile corner spawns.
    val crocodileSpawnLocals =
        listOf(
            CoordGrid(21, 37, 0),
            CoordGrid(42, 37, 0),
            CoordGrid(21, 26, 0),
            CoordGrid(42, 26, 0),
        )
    // Donor spawn positions (3938,5287) and (3934,5273), translated into region 15698 locals.
    val containerLocals = listOf(CoordGrid(34, 39, 0), CoordGrid(30, 25, 0))

    fun path(corridor: CrondisCorridor): CrondisPathDefinition =
        requireNotNull(pathsByCorridor[corridor])
}

internal data class CrondisCloudTrail(
    val serial: Int,
    val corridor: CrondisCorridor,
    val lane: Int,
    val startCycle: Int,
) {
    fun localTile(tileIndex: Int): CoordGrid =
        CrondisLayout.path(corridor).cloudLocal(lane, tileIndex)
}

internal object CrondisObstacleCycle {
    fun spikePhase(startCycle: Int, cycle: Int, emitter: CrondisSpikeEmitter): CrondisSpikePhase {
        if (cycle < startCycle) return CrondisSpikePhase.COOLDOWN
        return when (
            Math.floorMod(cycle - startCycle - emitter.delay, CrondisTuning.SPIKE_CYCLE_TICKS)
        ) {
            0 -> CrondisSpikePhase.WARNING
            1,
            2 -> CrondisSpikePhase.PREPARING
            3 -> CrondisSpikePhase.EXTENDING
            4,
            5,
            6 -> CrondisSpikePhase.DANGEROUS
            7 -> CrondisSpikePhase.RETRACTING
            else -> CrondisSpikePhase.COOLDOWN
        }
    }

    fun cloudPhase(trail: CrondisCloudTrail, cycle: Int, tileIndex: Int): CrondisCloudPhase {
        if (tileIndex !in 0 until CrondisTuning.CLOUD_TILE_COUNT) return CrondisCloudPhase.EXPIRED
        val warningAt = trail.startCycle + tileIndex / CrondisTuning.CLOUD_TILES_PER_TICK
        val dangerousAt = warningAt + 1
        return when {
            cycle < warningAt -> CrondisCloudPhase.INACTIVE
            cycle == warningAt -> CrondisCloudPhase.WARNING
            cycle < dangerousAt + CrondisTuning.CLOUD_DANGEROUS_TICKS -> CrondisCloudPhase.DANGEROUS
            else -> CrondisCloudPhase.EXPIRED
        }
    }

    fun cloudExpired(trail: CrondisCloudTrail, cycle: Int): Boolean =
        cloudPhase(trail, cycle, CrondisTuning.CLOUD_TILE_COUNT - 1) == CrondisCloudPhase.EXPIRED

    /** OSRS evaluates the tile occupied on the game tick; a skipped run tile is never occupied. */
    fun occupiesDangerousTile(occupied: CoordGrid, dangerous: Collection<CoordGrid>): Boolean =
        occupied in dangerous
}

internal data class CrondisWaterContainer(var units: Int = 0) {
    fun fill(): Int {
        val added = CrondisTuning.WATER_CONTAINER_CAPACITY - units
        units = CrondisTuning.WATER_CONTAINER_CAPACITY
        return added
    }

    fun empty(): Int = units.also { units = 0 }

    fun loseToTrap(outcome: CrondisSpillOutcome): Int {
        if (units <= 0) return 0
        val retained = (units * outcome.remainingPercent / 100).coerceAtLeast(1)
        return (units - retained).also { units = retained }
    }
}

internal class CrondisEncounter(
    val encounterId: UUID,
    private val seed: Long = encounterId.mostSignificantBits xor encounterId.leastSignificantBits,
) {
    var state = CrondisState.IDLE
    var generation = 0
    var originalPartySize = 1
    var water = 0
    var nextCloudBatchAt = -1
    var nextCrocodileSpawnAt = -1
    var crocodileSpawnCursor = 0
    var cloudSerial = 0
    var containersSpawned = false
    var perfectEligible = true
    var startedAt = -1
    var completedAt = -1
    var rngState = normalizedSeed(seed)
    var palm: Npc? = null
    val crocodiles = linkedSetOf<Npc>()
    val containers = linkedMapOf<PlayerUid, CrondisWaterContainer>()
    val waterfallReadyAt = linkedMapOf<CoordGrid, Int>()
    val crocodileNextAttackAt = linkedMapOf<Int, Int>()
    val crocodileProvokedPlayers = linkedSetOf<PlayerUid>()
    val playerObstacleHitCycles = linkedMapOf<PlayerUid, MutableList<Int>>()
    val playerSpikeReadyAt = linkedMapOf<PlayerUid, Int>()
    val playerCloudReadyAt = linkedMapOf<PlayerUid, Int>()
    val cloudTrails = mutableListOf<CrondisCloudTrail>()

    val requiredWater: Int
        get() = CrondisTuning.requiredWater(originalPartySize)

    val stage: Int
        get() =
            when {
                water >= requiredWater -> 4
                water * 4 >= requiredWater * 3 -> 3
                water * 2 >= requiredWater -> 2
                water * 4 >= requiredWater -> 1
                else -> 0
            }

    val perfectAchieved: Boolean
        get() =
            state == CrondisState.COMPLETE &&
                perfectEligible &&
                completedAt >= startedAt &&
                completedAt - startedAt <= CrondisTuning.PERFECT_TICKS

    fun reset() {
        generation++
        state = CrondisState.RESET
        originalPartySize = 1
        water = 0
        nextCloudBatchAt = -1
        nextCrocodileSpawnAt = -1
        crocodileSpawnCursor = 0
        cloudSerial = 0
        containersSpawned = false
        perfectEligible = true
        startedAt = -1
        completedAt = -1
        rngState = normalizedSeed(seed xor generation.toLong())
        palm = null
        crocodiles.clear()
        containers.clear()
        waterfallReadyAt.clear()
        crocodileNextAttackAt.clear()
        crocodileProvokedPlayers.clear()
        playerObstacleHitCycles.clear()
        playerSpikeReadyAt.clear()
        playerCloudReadyAt.clear()
        cloudTrails.clear()
    }

    fun start(teamSize: Int, palm: Npc, cycle: Int) {
        reset()
        originalPartySize = teamSize.coerceAtLeast(1)
        this.palm = palm
        state = CrondisState.ACTIVE
        startedAt = cycle
        nextCloudBatchAt = cycle
        nextCrocodileSpawnAt = cycle + CrondisTuning.CROCODILE_SPAWN_INTERVAL_TICKS
    }

    fun crocodileSpawnDue(cycle: Int): Boolean =
        state == CrondisState.ACTIVE && nextCrocodileSpawnAt >= 0 && cycle >= nextCrocodileSpawnAt

    fun scheduleNextCrocodileWave(cycle: Int) {
        nextCrocodileSpawnAt = cycle + CrondisTuning.CROCODILE_SPAWN_INTERVAL_TICKS
    }

    fun recordObstacleHit(uid: PlayerUid, cycle: Int) {
        val hits = playerObstacleHitCycles.getOrPut(uid) { mutableListOf() }
        hits.removeAll { cycle - it >= CrondisTuning.CROCODILE_OBSTACLE_WINDOW_TICKS }
        hits += cycle
    }

    fun recentObstacleHits(uid: PlayerUid, cycle: Int): Int {
        val hits = playerObstacleHitCycles[uid] ?: return 0
        hits.removeAll { cycle - it >= CrondisTuning.CROCODILE_OBSTACLE_WINDOW_TICKS }
        if (hits.isEmpty()) playerObstacleHitCycles.remove(uid)
        return hits.size
    }

    fun crocodileDamage(uid: PlayerUid, cycle: Int): Int =
        CrondisTuning.crocodileDamage(recentObstacleHits(uid, cycle))

    fun addWater(units: Int): Int {
        if (state != CrondisState.ACTIVE || units <= 0) return 0
        val accepted = units.coerceAtMost((requiredWater - water).coerceAtLeast(0))
        water += accepted
        if (water >= requiredWater) state = CrondisState.COMPLETE
        return accepted
    }

    /** Emptying a jug onto the palm consumes all carried water, even when only part is needed. */
    fun depositWater(container: CrondisWaterContainer): Int = addWater(container.empty())

    fun drainWater(units: Int): Int {
        if (state != CrondisState.ACTIVE || units <= 0) return 0
        val drained = units.coerceAtMost(water)
        water -= drained
        return drained
    }

    fun spawnCloudBatch(cycle: Int): List<CrondisCloudTrail> {
        if (state != CrondisState.ACTIVE) return emptyList()
        return CrondisLayout.paths
            .flatMap { path ->
                selectCloudLanes().map { lane ->
                    CrondisCloudTrail(
                        serial = cloudSerial++,
                        corridor = path.corridor,
                        lane = lane,
                        startCycle = cycle + CrondisTuning.CLOUD_START_DELAY_TICKS,
                    )
                }
            }
            .also(cloudTrails::addAll)
    }

    fun nextSpillOutcome(): CrondisSpillOutcome =
        if (nextInt(CrondisTuning.TRACE_REQUIRED_RARE_SPILL_DENOMINATOR) == 0) {
            CrondisSpillOutcome.RARE
        } else {
            CrondisSpillOutcome.NORMAL
        }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        containers.rebindKey(oldUid, newUid)
        playerObstacleHitCycles.rebindKey(oldUid, newUid)
        playerSpikeReadyAt.rebindKey(oldUid, newUid)
        playerCloudReadyAt.rebindKey(oldUid, newUid)
        if (crocodileProvokedPlayers.remove(oldUid)) {
            crocodileProvokedPlayers += newUid
        }
    }

    fun retirePlayer(uid: PlayerUid) {
        containers.remove(uid)
        playerObstacleHitCycles.remove(uid)
        playerSpikeReadyAt.remove(uid)
        playerCloudReadyAt.remove(uid)
        crocodileProvokedPlayers.remove(uid)
    }

    fun container(uid: PlayerUid): CrondisWaterContainer =
        containers.getOrPut(uid, ::CrondisWaterContainer)

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        rngState = rngState xor (rngState shl 13)
        rngState = rngState xor (rngState ushr 7)
        rngState = rngState xor (rngState shl 17)
        return ((rngState and Long.MAX_VALUE) % bound).toInt()
    }

    private fun selectCloudLanes(): Set<Int> {
        val available = (0..4).toMutableList()
        return buildSet {
            repeat(2 + nextInt(2)) { add(available.removeAt(nextInt(available.size))) }
        }
    }

    private fun normalizedSeed(value: Long): Long =
        if (value == 0L) -7046029254386353131L else value
}
