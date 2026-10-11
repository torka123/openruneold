package org.rsmod.content.bosses.cerberus

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal object CerberusRules {
    const val HITPOINTS = 600
    const val ATTACK_RATE_TICKS = 6
    const val BASIC_MAX_HIT = 23
    const val ATTACK_RANGE = 14
    const val STAND_TRANSITION_TICKS = 4
    const val RESET_TRANSITION_TICKS = 4
    const val RESET_WALK_TIMEOUT_TICKS = 24
    const val RESPAWN_TICKS = 15
    const val FIRE_WALL_DAMAGE = 5
    const val BASIC_ANIMATION_LOCK_TICKS = 2
    const val TRIPLE_ANIMATION_LOCK_TICKS = 6
    const val SPECIAL_ANIMATION_LOCK_TICKS = 3
    const val ATTACK_ANIMATION_PRIORITY = 100

    const val TRIPLE_INTERVAL = 10
    const val TRIPLE_COMPONENT_GAP_TICKS = 2
    const val TRIPLE_RECOVERY_TICKS = 10
    const val SOUL_RECOVERY_TICKS = 10
    const val LAVA_RECOVERY_TICKS = 8
    val TRIPLE_SEQUENCE =
        listOf(CerberusAttackStyle.MAGIC, CerberusAttackStyle.RANGED, CerberusAttackStyle.MELEE)

    const val SOUL_HITPOINT_THRESHOLD = 400
    const val SOUL_INTERVAL = 7
    const val SOUL_SKIP_DENOMINATOR = 10
    const val SOUL_DAMAGE = 30
    const val SOUL_PRAYER_DRAIN = 30
    const val SOUL_COMPONENT_GAP_TICKS = 2
    const val SOUL_APPROACH_DELAY_TICKS = 1
    const val SOUL_FIRST_ATTACK_DELAY_TICKS = 2
    const val SOUL_ROUTE_TIMEOUT_TICKS = 14
    const val SOUL_RETREAT_DELAY_TICKS = 2
    const val SOUL_WALK_DISTANCE = 9
    const val SOUL_SPAWN_LOCAL_Z = 49
    const val SOUL_ATTACK_LOCAL_Z = SOUL_SPAWN_LOCAL_Z - SOUL_WALK_DISTANCE

    const val LAVA_HITPOINT_THRESHOLD = 200
    const val LAVA_INTERVAL = 5
    const val LAVA_SKIP_DENOMINATOR = 10
    const val LAVA_INITIAL_DAMAGE = 10
    const val LAVA_MAX_HIT = 15
    const val LAVA_DAMAGE_INTERVAL_TICKS = 2
    const val LAVA_ACTIVE_TICKS = 14

    fun isTripleTurn(attackNumber: Int): Boolean =
        attackNumber >= 1 && (attackNumber - 1) % TRIPLE_INTERVAL == 0

    fun isSoulTurn(attackNumber: Int, hitpoints: Int): Boolean =
        attackNumber >= 1 &&
            hitpoints < SOUL_HITPOINT_THRESHOLD &&
            attackNumber % SOUL_INTERVAL == 0

    fun isLavaTurn(attackNumber: Int, hitpoints: Int): Boolean =
        attackNumber >= 1 &&
            hitpoints < LAVA_HITPOINT_THRESHOLD &&
            attackNumber % LAVA_INTERVAL == 0

    fun specialCandidate(attackNumber: Int, hitpoints: Int): CerberusAttack? =
        when {
            isTripleTurn(attackNumber) -> CerberusAttack.TRIPLE
            isSoulTurn(attackNumber, hitpoints) -> CerberusAttack.SOULS
            isLavaTurn(attackNumber, hitpoints) -> CerberusAttack.LAVA
            else -> null
        }

    fun chooseAttack(attackNumber: Int, hitpoints: Int, skipSpecial: Boolean): CerberusAttack =
        when (val candidate = specialCandidate(attackNumber, hitpoints)) {
            CerberusAttack.TRIPLE -> candidate
            CerberusAttack.SOULS,
            CerberusAttack.LAVA -> if (skipSpecial) CerberusAttack.BASIC else candidate
            else -> CerberusAttack.BASIC
        }

    fun recoveryTicks(attack: CerberusAttack): Int =
        when (attack) {
            CerberusAttack.TRIPLE -> TRIPLE_RECOVERY_TICKS
            CerberusAttack.SOULS -> SOUL_RECOVERY_TICKS
            CerberusAttack.LAVA -> LAVA_RECOVERY_TICKS
            CerberusAttack.BASIC -> ATTACK_RATE_TICKS
        }

    fun projectileImpactCycle(launchCycle: Int, travelTicks: Int): Int =
        launchCycle + travelTicks.coerceAtLeast(1)

    fun shouldSkipSpecial(
        candidate: CerberusAttack?,
        roll: Int,
        forcedSkip: Boolean? = null,
    ): Boolean {
        if (candidate != CerberusAttack.SOULS && candidate != CerberusAttack.LAVA) return false
        forcedSkip?.let {
            return it
        }
        val denominator =
            if (candidate == CerberusAttack.SOULS) SOUL_SKIP_DENOMINATOR else LAVA_SKIP_DENOMINATOR
        return Math.floorMod(roll, denominator) == 0
    }

    /** Skip RNG is consumed only by a due, unforced Souls/lava decision. */
    fun selectAttack(
        attackNumber: Int,
        hitpoints: Int,
        forcedAttack: CerberusAttack? = null,
        forcedSkip: Boolean? = null,
        nextSkipRoll: (Int) -> Int,
    ): CerberusAttack {
        forcedAttack?.let { return it }
        val candidate = specialCandidate(attackNumber, hitpoints)
        if (candidate != CerberusAttack.SOULS && candidate != CerberusAttack.LAVA) {
            return candidate ?: CerberusAttack.BASIC
        }
        val denominator =
            if (candidate == CerberusAttack.SOULS) SOUL_SKIP_DENOMINATOR else LAVA_SKIP_DENOMINATOR
        val skip = forcedSkip ?: (nextSkipRoll(denominator) == 0)
        return if (skip) CerberusAttack.BASIC else candidate
    }

    /** Choose without replacement from the complete prevalidated random-floor catalog. */
    fun <T> lavaTiles(playerTile: T, legalRandomTiles: Collection<T>, nextIndex: (Int) -> Int): List<T> {
        val candidates = legalRandomTiles.distinct().filter { it != playerTile }.toMutableList()
        require(candidates.size >= 2) { "Cerberus needs two distinct legal random lava tiles." }
        return listOf(
            playerTile,
            candidates.removeAt(nextIndex(candidates.size)),
            candidates.removeAt(nextIndex(candidates.size)),
        )
    }

    fun ordinaryStyles(inMeleeRange: Boolean): List<CerberusAttackStyle> =
        if (inMeleeRange) {
            listOf(CerberusAttackStyle.MAGIC, CerberusAttackStyle.RANGED, CerberusAttackStyle.MELEE)
        } else {
            listOf(CerberusAttackStyle.MAGIC, CerberusAttackStyle.RANGED)
        }

    fun soulPrayerDrain(hasSpectral: Boolean, hasWard: Boolean): Int =
        when {
            hasSpectral && hasWard -> 10
            hasSpectral -> 15
            hasWard -> 20
            else -> SOUL_PRAYER_DRAIN
        }

    fun soulUnprotectedDamage(hasElysian: Boolean): Int = if (hasElysian) 22 else SOUL_DAMAGE

    fun soulOrder(firstPick: Int, secondPick: Int): List<CerberusAttackStyle> {
        require(firstPick in 0..2)
        require(secondPick in 0..1)
        val remaining =
            mutableListOf(
                CerberusAttackStyle.RANGED,
                CerberusAttackStyle.MAGIC,
                CerberusAttackStyle.MELEE,
            )
        return listOf(
            remaining.removeAt(firstPick),
            remaining.removeAt(secondPick),
            remaining.single(),
        )
    }

    fun lavaMaximumDamage(
        hasDragonfireShield: Boolean,
        protectFromMagic: Boolean,
        hasRegularAntifire: Boolean,
        hasSuperAntifire: Boolean,
    ): Int =
        when {
            hasSuperAntifire -> 11
            hasDragonfireShield -> 12
            hasRegularAntifire && protectFromMagic -> 12
            else -> LAVA_MAX_HIT
        }

    fun distanceToFootprint(npcX: Int, npcZ: Int, npcSize: Int, targetX: Int, targetZ: Int): Int {
        val maxX = npcX + npcSize - 1
        val maxZ = npcZ + npcSize - 1
        val dx =
            when {
                targetX < npcX -> npcX - targetX
                targetX > maxX -> targetX - maxX
                else -> 0
            }
        val dz =
            when {
                targetZ < npcZ -> npcZ - targetZ
                targetZ > maxZ -> targetZ - maxZ
                else -> 0
            }
        return maxOf(dx, dz)
    }

    fun crossedFireWall(previousX: Int, previousZ: Int, currentX: Int, currentZ: Int): Boolean {
        val crossedNorth = previousZ <= CERBERUS_FIRE_WALL_Z && currentZ > CERBERUS_FIRE_WALL_Z
        val crossedSouth = previousZ > CERBERUS_FIRE_WALL_Z && currentZ <= CERBERUS_FIRE_WALL_Z
        val crossedAtWall = crossedNorth || crossedSouth
        val crossedThroughOpening =
            previousX in CERBERUS_FIRE_WALL_MIN_X..CERBERUS_FIRE_WALL_MAX_X ||
                currentX in CERBERUS_FIRE_WALL_MIN_X..CERBERUS_FIRE_WALL_MAX_X
        return crossedAtWall && crossedThroughOpening
    }

    fun isLavaTileLegal(
        localX: Int,
        localZ: Int,
        bossLocalX: Int = CERBERUS_BOSS_LOCAL_X,
        bossLocalZ: Int = CERBERUS_BOSS_LOCAL_Z,
        bossSize: Int = 5,
    ): Boolean {
        if (localX !in CERBERUS_CHAMBER_MIN_X..CERBERUS_CHAMBER_MAX_X) return false
        if (localZ !in CERBERUS_CHAMBER_MIN_Z..CERBERUS_CHAMBER_MAX_Z) return false
        val onBoss =
            localX in bossLocalX until (bossLocalX + bossSize) &&
                localZ in bossLocalZ until (bossLocalZ + bossSize)
        return !onBoss
    }

    fun canPlayDefendAnimation(
        phase: CerberusPhase,
        currentCycle: Int,
        attackAnimationUntilCycle: Int,
        hitpoints: Int,
    ): Boolean =
        phase == CerberusPhase.ACTIVE && hitpoints > 0 && currentCycle >= attackAnimationUntilCycle

    fun bossProjectileSource(npcX: Int, npcZ: Int, npcSize: Int): Pair<Int, Int> {
        val offset = (npcSize - 1) / 2
        return (npcX + offset) to (npcZ + offset)
    }

    fun bossProjectileSource(
        npcX: Int,
        npcZ: Int,
        npcSize: Int,
        targetX: Int,
        targetZ: Int,
        head: CerberusHead,
    ): Pair<Int, Int> {
        val middle = bossProjectileSource(npcX, npcZ, npcSize)
        val radius = npcSize shr 1
        if (radius == 0) return middle
        val headOffset =
            when (head) {
                CerberusHead.LEFT -> Math.PI / 4.0
                CerberusHead.CENTRE -> 0.0
                CerberusHead.RIGHT -> -Math.PI / 4.0
            }
        val angle = atan2((targetZ - middle.second).toDouble(), (targetX - middle.first).toDouble())
        return (middle.first + radius * cos(angle + headOffset)).roundToInt() to
            (middle.second + radius * sin(angle + headOffset)).roundToInt()
    }

    fun isBoneItem(internalName: String): Boolean =
        internalName.removePrefix("obj.").endsWith("bones")

    fun completedTasks(
        kills: Int,
        soulWaveSpawned: Boolean,
        negatedSoulAttacks: Int,
        tookLavaDamage: Boolean,
        tookMeleeDamage: Boolean,
    ): Set<CerberusTask> = buildSet {
        if (!tookLavaDamage) add(CerberusTask.UNREQUIRED_ANTIFIRE)
        if (kills >= 75) add(CerberusTask.VETERAN)
        if (negatedSoulAttacks >= 6) add(CerberusTask.GHOST_BUSTER)
        if (!tookMeleeDamage) add(CerberusTask.ANTI_BITE_MECHANICS)
        if (kills >= 150) add(CerberusTask.MASTER)
        if (!soulWaveSpawned) add(CerberusTask.AROOO_NO_MORE)
    }

    fun inChamber(localX: Int, localZ: Int): Boolean =
        localX in CERBERUS_CHAMBER_MIN_X..CERBERUS_CHAMBER_MAX_X &&
            localZ in CERBERUS_CHAMBER_MIN_Z..CERBERUS_CHAMBER_MAX_Z

    fun shouldDiscardRun(expectedSession: Long, actualSession: Long?): Boolean =
        actualSession != expectedSession

    fun chebyshevDistance(firstX: Int, firstZ: Int, secondX: Int, secondZ: Int): Int =
        maxOf(abs(firstX - secondX), abs(firstZ - secondZ))
}
