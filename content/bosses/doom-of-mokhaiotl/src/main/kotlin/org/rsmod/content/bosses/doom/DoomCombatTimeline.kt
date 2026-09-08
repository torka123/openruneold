package org.rsmod.content.bosses.doom

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.map.CoordGrid

/** Pure tick calculations shared by the live encounter and deterministic regression tests. */
internal object DoomCombatTimeline {
    fun bossSpawnCycle(promptAdvancedCycle: Int) =
        promptAdvancedCycle + DoomRules.BOSS_SPAWN_DELAY_TICKS

    fun firstShockwaveCycle(activationCycle: Int) =
        activationCycle + DoomRules.FIRST_SHOCKWAVE_TICKS

    fun standardProjectileImpactCycle(launchCycle: Int, delve: Int) =
        launchCycle + DoomRules.standardProjectileTravelTicks(delve)

    fun rockExplosionCycle(launchCycle: Int, delve: Int = 1, volleyIndex: Int = 0): Int {
        val ticks =
            when {
                delve >= 8 -> if (volleyIndex == 0) 6 else 9
                delve >= 6 -> 6
                else -> DoomRules.ROCK_EXPLOSION_TICKS
            }
        return launchCycle + ticks
    }

    fun rockShadowImpactCycle(explosionCycle: Int) = explosionCycle + DoomRules.ROCK_SHADOW_TICKS

    fun shockwaveStartCycle(volatileSpawnCycle: Int) =
        volatileSpawnCycle + DoomRules.SHOCKWAVE_WARNING_TICKS

    fun postCarShockwavePreparationCycle(emergeCycle: Int) =
        emergeCycle + DoomRules.POST_CAR_SHOCKWAVE_DELAY_TICKS

    fun volatileAttackableCycle(spawnCycle: Int) = spawnCycle + 1

    fun earthShieldSpawnCycle(secondVolatileHitCycle: Int) = secondVolatileHitCycle + 1

    fun shockwaveChargeCycle(startCycle: Int) = startCycle + 2

    fun shockwaveSlamCycles(startCycle: Int, delve: Int): List<Int> =
        List(DoomRules.shockwaveSlams(delve)) { index ->
            startCycle + 4 + index * DoomRules.SHOCKWAVE_INTERVAL_TICKS
        }

    fun shockwavePulseCycle(slamCycle: Int) = slamCycle + DoomRules.SHOCKWAVE_INTERVAL_TICKS

    fun shockwavePulseCycles(startCycle: Int, delve: Int): List<Int> =
        shockwaveSlamCycles(startCycle, delve).map(::shockwavePulseCycle)

    fun beamImpactCycle(launchCycle: Int, projectileCycles: Int) =
        launchCycle + projectileCycles.coerceAtLeast(1)

    fun beamFireCycle(chargeStartCycle: Int, state: DoomState) =
        chargeStartCycle + DoomRules.chargeTicks(state)

    fun shieldActiveCycle(transitionStartCycle: Int) =
        transitionStartCycle + DoomRules.SHIELD_TRANSITION_TICKS

    fun firstShieldLarvaSpawnCycle(shieldActiveCycle: Int) =
        shieldActiveCycle + DoomRules.SHIELD_FIRST_LARVA_DELAY_TICKS

    fun nextShieldLarvaSpawnCycle(previousSpawnCycle: Int, delve: Int, intervalTicks: Int): Int {
        require(intervalTicks in DoomRules.shieldLarvaIntervalRange(delve))
        return previousSpawnCycle + intervalTicks
    }

    fun larvaMovementCycle(spawnCycle: Int) = spawnCycle + DoomRules.LARVA_MOVEMENT_DELAY_TICKS

    fun larvaAttackableCycle(spawnCycle: Int) = spawnCycle + DoomRules.LARVA_ATTACKABLE_DELAY_TICKS

    /** Compatibility alias for code which still describes the T+2 attackable state as active. */
    fun larvaActiveCycle(spawnCycle: Int) = larvaAttackableCycle(spawnCycle)

    fun carArrivalCycle(startCycle: Int, delve: Int, distance: Int) =
        startCycle + DoomRules.carTravelTicks(delve, distance)

    /** OpenRune hit queues require a positive delay, so enqueue one cycle before impact. */
    fun queueCycleForImpact(impactCycle: Int) = impactCycle - 1
}

/** Capture-backed packet schedule for one Doom car-phase zoom. */
internal object DoomCarTimeline {
    const val WARNING_TO_DASH_TICKS: Int = 3
    const val PERSISTENT_EYE_CLIENT_DELAY: Int = 60
    const val PROJECTILE_INTERVAL_TICKS: Int = 2
    const val SLAM_CLIENT_DELAY: Int = 120
    const val MIN_WAVE_TICKS_FROM_DASH: Int = 8
    const val WAVE_TICKS_AFTER_SLAM_PACKET: Int = 6
    const val NEXT_EYE_TICKS_AFTER_WAVE: Int = 4

    fun schedule(eyeSpawnCycle: Int, delve: Int, exactMoveSegments: Int): DoomCarZoomSchedule {
        val profile = DoomRules.profile(delve)
        require(profile.carEnabled) { "Doom's car phase starts at delve five: $delve" }
        require(exactMoveSegments > 0) {
            "A Doom car zoom must contain at least one exact-move segment."
        }
        val dashStartCycle = eyeSpawnCycle + WARNING_TO_DASH_TICKS
        val exactMoveCycles = List(exactMoveSegments) { dashStartCycle + it }
        val trampleImpactCycles = exactMoveCycles.map { it + 1 }
        // The slam packet follows the final exact-move packet by one server cycle.
        val slamPacketCycle =
            if (profile.carSlamEnabled) dashStartCycle + exactMoveSegments else null
        val projectileCycles =
            if (profile.carProjectilesEnabled) {
                val finalCycle = requireNotNull(slamPacketCycle)
                val firstCycle =
                    if (dashStartCycle and 1 == 1) dashStartCycle else dashStartCycle + 1
                generateSequence(firstCycle) { it + PROJECTILE_INTERVAL_TICKS }
                    .takeWhile { it <= finalCycle }
                    .toList()
            } else {
                emptyList()
            }
        val waveCycle =
            slamPacketCycle?.let {
                max(dashStartCycle + MIN_WAVE_TICKS_FROM_DASH, it + WAVE_TICKS_AFTER_SLAM_PACKET)
            }
        return DoomCarZoomSchedule(
            eyeSpawnCycle = eyeSpawnCycle,
            persistentEyeClientDelay = PERSISTENT_EYE_CLIENT_DELAY,
            dashStartCycle = dashStartCycle,
            exactMoveCycles = exactMoveCycles,
            trampleImpactCycles = trampleImpactCycles,
            projectileCycles = projectileCycles,
            slamPacketCycle = slamPacketCycle,
            slamClientDelay = if (slamPacketCycle == null) null else SLAM_CLIENT_DELAY,
            waveCycle = waveCycle,
            nextEyeCycle = waveCycle?.plus(NEXT_EYE_TICKS_AFTER_WAVE),
        )
    }
}

/** Accuracy behavior for attacks launched by Doom during its burrowed car phase. */
internal object DoomCarAttackRules {
    const val PROJECTILES_GUARANTEED_ACCURACY: Boolean = false
}

internal data class DoomCarZoomSchedule(
    val eyeSpawnCycle: Int,
    val persistentEyeClientDelay: Int,
    val dashStartCycle: Int,
    val exactMoveCycles: List<Int>,
    val trampleImpactCycles: List<Int>,
    val projectileCycles: List<Int>,
    val slamPacketCycle: Int?,
    val slamClientDelay: Int?,
    val waveCycle: Int?,
    val nextEyeCycle: Int?,
)

internal object DoomActivationRules {
    fun shouldSpawn(promptAdvanced: Boolean, bossPresent: Boolean): Boolean =
        promptAdvanced && !bossPresent

    fun spawnCycle(promptAdvancedCycle: Int): Int =
        DoomCombatTimeline.bossSpawnCycle(promptAdvancedCycle)

    fun shouldWake(
        promptAdvanced: Boolean,
        playerMoved: Boolean,
        prayerActivated: Boolean,
    ): Boolean = promptAdvanced || playerMoved || prayerActivated
}

internal object DoomPersistenceRules {
    /** Acid survives a descent except when entering delve six, matching the live clear. */
    fun shouldCarryAcidInto(delve: Int): Boolean = delve != 6

    /** Delve six clears acid again when the post-shield car phase begins. */
    fun shouldClearAcidAtCarStart(delve: Int): Boolean = delve == 6
}

internal object DoomHolyWaterRules {
    private const val LANDING_RADIUS: Int = 2
    private const val CLEAR_RADIUS: Int = 1

    /**
     * Stable scatter centres around Doom; exact rev-240 projectile visuals remain capture-pending.
     */
    fun landingTiles(bossCentre: CoordGrid): List<CoordGrid> =
        listOf(
            bossCentre.translate(-LANDING_RADIUS, -LANDING_RADIUS),
            bossCentre.translate(LANDING_RADIUS, -LANDING_RADIUS),
            bossCentre.translate(-LANDING_RADIUS, LANDING_RADIUS),
            bossCentre.translate(LANDING_RADIUS, LANDING_RADIUS),
        )

    fun clearedTiles(landings: Iterable<CoordGrid>): Set<CoordGrid> = buildSet {
        landings.forEach { landing ->
            for (dx in -CLEAR_RADIUS..CLEAR_RADIUS) {
                for (dz in -CLEAR_RADIUS..CLEAR_RADIUS) {
                    add(landing.translate(dx, dz))
                }
            }
        }
    }
}

internal object DoomShieldGeometry {
    /** The visible Earth Shield is a 3x3 safe footprint, not an infinite ray behind the NPC. */
    fun protects(shieldSouthWest: CoordGrid, shieldSize: Int, player: CoordGrid): Boolean =
        shieldSize > 0 &&
            player.level == shieldSouthWest.level &&
            player.x in shieldSouthWest.x until shieldSouthWest.x + shieldSize &&
            player.z in shieldSouthWest.z until shieldSouthWest.z + shieldSize
}

internal data class DoomLarvaSpawnDirection(val deltaX: Int, val deltaZ: Int) {
    init {
        require(deltaX in -1..1 && deltaZ in -1..1 && (deltaX != 0 || deltaZ != 0))
    }
}

/** Pure placement constraints used by the live encounter collision-aware tile selector. */
internal object DoomLarvaPlacementRules {
    val shieldStreamPreference: List<DoomLarvaSpawnDirection> =
        listOf(
            DoomLarvaSpawnDirection(-1, -1), // south-west
            DoomLarvaSpawnDirection(-1, 0), // west
            DoomLarvaSpawnDirection(-1, 1), // north-west
            DoomLarvaSpawnDirection(0, 1), // north
        )

    val shieldStreamFallback: List<DoomLarvaSpawnDirection> =
        listOf(
            DoomLarvaSpawnDirection(1, 1), // north-east
            DoomLarvaSpawnDirection(1, 0), // east
            DoomLarvaSpawnDirection(1, -1), // south-east
            DoomLarvaSpawnDirection(0, -1), // south
        )

    val allShieldStreams: List<DoomLarvaSpawnDirection> =
        shieldStreamPreference + shieldStreamFallback

    /** Retains the selected stream while open, otherwise reselects in live preference order. */
    fun selectShieldStream(
        current: DoomLarvaSpawnDirection?,
        available: Set<DoomLarvaSpawnDirection>,
    ): DoomLarvaSpawnDirection? {
        if (current != null && current in available) return current
        return allShieldStreams.firstOrNull { it in available }
    }

    /**
     * Outside-shield larvae belong beyond the player on the player side of the boss. The caller
     * remains responsible for clipping and arena-bound checks.
     */
    fun isBeyondPlayerOnBossSide(
        boss: CoordGrid,
        player: CoordGrid,
        candidate: CoordGrid,
    ): Boolean {
        if (boss.level != player.level || boss.level != candidate.level) return false
        val playerX = player.x - boss.x
        val playerZ = player.z - boss.z
        val candidateX = candidate.x - boss.x
        val candidateZ = candidate.z - boss.z
        val sameSide = playerX * candidateX + playerZ * candidateZ > 0
        return sameSide && squaredDistance(boss, candidate) > squaredDistance(boss, player)
    }

    /**
     * Selects one exact-count outside-shield wave. Adjacent player-side formations win first;
     * fragmented but otherwise safe arenas still receive the full wave instead of silently
     * consuming the attack turn.
     */
    fun selectWave(
        count: Int,
        orderedCandidates: List<CoordGrid>,
        preferredCandidates: Set<CoordGrid>,
        adjacent: (CoordGrid) -> List<CoordGrid>,
    ): List<CoordGrid> {
        require(count >= 0) { "Larva wave count cannot be negative: $count" }
        if (count == 0) return emptyList()
        val open = orderedCandidates.distinct()
        if (open.size < count) return emptyList()
        val openSet = open.toSet()

        fun find(allowed: Set<CoordGrid>): List<CoordGrid>? {
            fun extend(path: List<CoordGrid>): List<CoordGrid>? {
                if (path.size == count) return path
                for (next in adjacent(path.last())) {
                    if (next !in allowed || next in path) continue
                    val result = extend(path + next)
                    if (result != null) return result
                }
                return null
            }
            for (start in open) {
                if (start !in allowed) continue
                val result = extend(listOf(start))
                if (result != null) return result
            }
            return null
        }

        val preferred = openSet.intersect(preferredCandidates)
        return find(preferred) ?: find(openSet) ?: open.take(count)
    }

    fun isAdjacentPair(first: CoordGrid, second: CoordGrid): Boolean =
        first.level == second.level && max(abs(first.x - second.x), abs(first.z - second.z)) == 1

    private fun squaredDistance(first: CoordGrid, second: CoordGrid): Int {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return dx * dx + dz * dz
    }
}

internal object DoomShieldRules {
    fun thresholdHitpoints(maxHitpoints: Int): Int =
        maxHitpoints.coerceAtLeast(0) * DoomRules.INITIAL_SHIELD_PERCENT / 100

    fun shouldArm(
        delve: Int,
        alreadyTriggered: Boolean,
        currentHitpoints: Int,
        incomingDamage: Int,
        maxHitpoints: Int = DoomRules.hitpoints(delve),
    ): Boolean =
        delve >= 3 &&
            !alreadyTriggered &&
            incomingDamage > 0 &&
            currentHitpoints - incomingDamage <= thresholdHitpoints(maxHitpoints)

    /** Arms from the final dealt hit, never from a projectile's pre-impact damage roll. */
    fun shouldArmAfterImpact(
        delve: Int,
        alreadyTriggered: Boolean,
        currentHitpoints: Int,
    ): Boolean =
        delve >= 3 &&
            !alreadyTriggered &&
            currentHitpoints > 0 &&
            currentHitpoints <= thresholdHitpoints(DoomRules.hitpoints(delve))

    fun initialStandardAttacksRemaining(): Int = DoomRules.SHIELD_STANDARD_ATTACKS

    fun normalHitpointsAfterTransitionHit(currentHitpoints: Int, damage: Int): Int =
        (currentHitpoints.coerceAtLeast(0) - damage.coerceAtLeast(0)).coerceAtLeast(0)

    fun attacksRemainingAfter(remaining: Int, attack: DoomPrimaryAttack): Int =
        if (attack == DoomPrimaryAttack.STANDARD) (remaining - 1).coerceAtLeast(0)
        else remaining.coerceAtLeast(0)
}

internal enum class DoomLarvaHealPool {
    ACTIVE_BOSS,
    STORED_NORMAL,
}

internal object DoomLarvaHealingRules {
    /**
     * Only an active blue shield stores healing separately. During its two-tick transition the real
     * boss is still hittable and healable, so healing must update that live health pool.
     */
    fun pool(state: DoomState): DoomLarvaHealPool =
        if (state == DoomState.SHIELDED) DoomLarvaHealPool.STORED_NORMAL
        else DoomLarvaHealPool.ACTIVE_BOSS
}

internal enum class DoomPhaseTransition {
    NONE,
    SHOCKWAVE,
    SHIELD,
}

internal object DoomPhasePriority {
    /** A due or already-preparing shockwave always resolves before a newly armed shield. */
    fun select(
        shockwaveDue: Boolean,
        shockwavePreparing: Boolean,
        shieldReady: Boolean,
    ): DoomPhaseTransition =
        when {
            shockwavePreparing || shockwaveDue -> DoomPhaseTransition.SHOCKWAVE
            shieldReady -> DoomPhaseTransition.SHIELD
            else -> DoomPhaseTransition.NONE
        }
}

/** Compatibility facade; all footprint math is owned by [DoomGeometry]. */
internal object DoomFootprintGeometry {
    fun tiles(anchor: CoordGrid, size: Int): Set<CoordGrid> = DoomGeometry.occupied(anchor, size)

    fun sweptTiles(startAnchor: CoordGrid, endAnchor: CoordGrid, size: Int): Set<CoordGrid> {
        require(startAnchor.level == endAnchor.level)
        return DoomGeometry.sweptFootprint(DoomTileLine.inclusive(startAnchor, endAnchor), size)
    }

    fun intersects(
        startAnchor: CoordGrid,
        endAnchor: CoordGrid,
        size: Int,
        target: CoordGrid,
    ): Boolean = target in sweptTiles(startAnchor, endAnchor, size)
}

internal object DoomSlamRockRules {
    fun blockingRock(
        origin: CoordGrid,
        player: CoordGrid,
        rocks: Collection<CoordGrid>,
    ): CoordGrid? {
        if (origin.level != player.level) return null
        val line = DoomTileLine.inclusive(origin, player).dropLast(1).toSet()
        return rocks
            .asSequence()
            .filter { it.level == origin.level && it in line }
            .minByOrNull { squaredDistance(origin, it) }
    }

    fun protects(origin: CoordGrid, player: CoordGrid, rocks: Collection<CoordGrid>): Boolean =
        blockingRock(origin, player, rocks) != null

    /** Only the rock that absorbed this line-of-fire slam is consumed. */
    fun destroyedBySlam(
        origin: CoordGrid,
        player: CoordGrid,
        rocks: Collection<CoordGrid>,
    ): Set<CoordGrid> = blockingRock(origin, player, rocks)?.let(::setOf) ?: emptySet()

    private fun squaredDistance(first: CoordGrid, second: CoordGrid): Int {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return dx * dx + dz * dz
    }
}

internal object DoomTileLine {
    fun inclusive(start: CoordGrid, end: CoordGrid): List<CoordGrid> {
        require(start.level == end.level)
        val result = mutableListOf<CoordGrid>()
        var x = start.x
        var z = start.z
        val dx = abs(end.x - start.x)
        val dz = -abs(end.z - start.z)
        val stepX = if (start.x < end.x) 1 else -1
        val stepZ = if (start.z < end.z) 1 else -1
        var error = dx + dz
        while (true) {
            result += CoordGrid(x, z, start.level)
            if (x == end.x && z == end.z) break
            val twiceError = error * 2
            if (twiceError >= dz) {
                error += dz
                x += stepX
            }
            if (twiceError <= dx) {
                error += dx
                z += stepZ
            }
        }
        return result
    }
}
