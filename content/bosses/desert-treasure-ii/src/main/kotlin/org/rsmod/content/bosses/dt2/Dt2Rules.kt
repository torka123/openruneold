package org.rsmod.content.bosses.dt2

import dev.openrune.types.ItemServerType
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.map.CoordGrid

internal data class LeviathanAttackContract(
    val animation: Int,
    val castGraphic: Int?,
    val projectileGraphic: Int?,
    val impactGraphic: Int?,
    val maximumHit: Int,
)

internal data class VardorvisAxeLane(
    val originX: Int,
    val originZ: Int,
    val movingStartX: Int,
    val movingStartZ: Int,
    val movingEndX: Int,
    val movingEndZ: Int,
)

internal data class LeviathanSpecialContract(
    val startAnimation: Int,
    val loopAnimation: Int?,
    val endAnimation: Int?,
    val duration: Int,
)

internal data class LeviathanCombatStats(
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val magic: Int,
    val ranged: Int,
)

internal enum class LeviathanAction {
    DORMANT,
    STANDARD_VOLLEY,
    STUNNED,
    ROAR,
    LIGHTNING_BARRAGE,
    SMOKE_BLAST,
    DYING,
    RESPAWNING,
    INVALIDATED,
}

/** Avoidable damage sources that invalidate an OSRS perfect Leviathan kill. */
internal enum class LeviathanPerfectFailure {
    STANDARD_ORB,
    FALLING_DEBRIS,
    BITE,
    LIGHTNING_CURRENT,
    SMOKE_BLAST,
    AWAKENED_TORNADO,
}

internal enum class LeviathanDebrisAxis {
    EAST_WEST,
    NORTH_SOUTH,
}

internal enum class LeviathanFacing(val x: Int, val z: Int, val angle: Int) {
    SOUTH(0, -1, 0),
    SOUTH_WEST(-1, -1, 256),
    WEST(-1, 0, 512),
    NORTH_WEST(-1, 1, 768),
    NORTH(0, 1, 1024),
    NORTH_EAST(1, 1, 1280),
    EAST(1, 0, 1536),
    SOUTH_EAST(1, -1, 1792),
}

internal data class WhispererVolleyContract(val styles: List<Dt2AttackStyle>, val attackRate: Int)

internal enum class WhispererSoulGroup(val required: Int) {
    VITA(2),
    ORATIO(3),
    SANITAS(3),
    MORS(4),
}

internal object Dt2Rules {
    fun rewardEligibleOnEntry(difficulty: Dt2Difficulty): Boolean =
        difficulty == Dt2Difficulty.NORMAL

    fun whispererCanDisturb(npcId: Int, state: Dt2State): Boolean =
        npcId == Dt2Assets.WHISPERER_SPAWN_ID && state == Dt2State.PREPARING

    fun whispererThresholds(difficulty: Dt2Difficulty): List<Int> =
        if (difficulty == Dt2Difficulty.NORMAL) listOf(720, 495, 270)
        else listOf(2_400, 2_120, 1_820, 1_600, 1_310, 1_050, 800)

    fun whispererSpecial(index: Int, seedBit: Int): WhispererSpecial {
        val a =
            if (seedBit and 1 == 0) {
                listOf(
                    WhispererSpecial.CORRUPTED_SEEDS,
                    WhispererSpecial.SCREECH,
                    WhispererSpecial.SOUL_SIPHON,
                )
            } else {
                listOf(
                    WhispererSpecial.SCREECH,
                    WhispererSpecial.SOUL_SIPHON,
                    WhispererSpecial.CORRUPTED_SEEDS,
                )
            }
        return a[index % a.size]
    }

    /** One complete three-projectile normal turn is ten ticks in the normal encounter. */
    fun whispererVolley(
        attackTurn: Int,
        specialsCompleted: Int,
        enraged: Boolean,
    ): WhispererVolleyContract {
        if (enraged) {
            val pair = (attackTurn / 2) and 1
            val style = if (pair == 0) Dt2AttackStyle.RANGED else Dt2AttackStyle.MAGIC
            return WhispererVolleyContract(listOf(style), attackRate = 2)
        }
        val primary = if (attackTurn and 1 == 0) Dt2AttackStyle.MAGIC else Dt2AttackStyle.RANGED
        val opposite =
            if (primary == Dt2AttackStyle.MAGIC) Dt2AttackStyle.RANGED else Dt2AttackStyle.MAGIC
        val styles =
            when {
                specialsCompleted >= 3 -> listOf(primary, opposite, primary)
                specialsCompleted >= 1 -> listOf(primary, primary, opposite)
                else -> List(3) { primary }
            }
        return WhispererVolleyContract(styles, attackRate = 10)
    }

    fun whispererAttackRate(specialsCompleted: Int, enraged: Boolean): Int =
        whispererVolley(0, specialsCompleted, enraged).attackRate

    fun whispererPattern(counter: Int, specialsCompleted: Int): Dt2AttackStyle =
        whispererVolley(counter / 3, specialsCompleted, enraged = false).styles[counter % 3]

    fun whispererProjectile(style: Dt2AttackStyle): Int =
        when (style) {
            Dt2AttackStyle.MAGIC -> Dt2Assets.WHISPERER_MAGIC_PROJECTILE
            Dt2AttackStyle.RANGED -> Dt2Assets.WHISPERER_RANGED_PROJECTILE
            Dt2AttackStyle.MELEE -> error("The Whisperer's melee attack has no projectile.")
        }

    fun whispererMaximumHit(style: Dt2AttackStyle, awakened: Boolean): Int =
        when (style) {
            Dt2AttackStyle.MAGIC -> if (awakened) 54 else 36
            Dt2AttackStyle.RANGED -> if (awakened) 60 else 40
            Dt2AttackStyle.MELEE -> if (awakened) 63 else 42
        }

    fun whispererSpecialDamage(damage: Int, inShadowRealm: Boolean): Int =
        protectedDamage(damage, if (inShadowRealm) 60 else 70)

    fun whispererSanityTick(
        current: Int,
        shadowRealm: Boolean,
        awakened: Boolean,
        cycle: Int,
    ): Int =
        when {
            shadowRealm -> (current - if (awakened) 4 else 3).coerceAtLeast(0)
            !awakened && cycle % 4 == 0 -> (current + 1).coerceAtMost(100)
            else -> current
        }

    fun whispererInsanityDamage(ticksAtZero: Int): Int? {
        if (ticksAtZero < 0 || ticksAtZero % 2 != 0) return null
        return (5 shl (ticksAtZero / 2).coerceAtMost(5)).coerceAtMost(160)
    }

    /** Region-local coordinates taken directly from rsprox-3299-rev234 seed spawns. */
    val whispererSeedTiles: List<Pair<Int, Int>> =
        listOf(
            27 to 33,
            27 to 35,
            31 to 37,
            29 to 35,
            29 to 33,
            28 to 33,
            26 to 33,
            31 to 39,
            37 to 33,
            37 to 35,
            33 to 37,
            35 to 35,
            32 to 39,
            32 to 37,
            35 to 33,
            36 to 33,
            38 to 33,
            33 to 39,
            27 to 31,
            29 to 31,
            31 to 29,
            31 to 27,
            37 to 31,
            33 to 29,
            35 to 31,
            32 to 27,
            32 to 29,
            33 to 27,
        )

    val whispererHealthySeedTiles: Set<Pair<Int, Int>> =
        setOf(
            27 to 33,
            27 to 35,
            31 to 37,
            29 to 35,
            37 to 33,
            37 to 35,
            33 to 37,
            35 to 35,
            27 to 31,
            29 to 31,
            31 to 29,
            37 to 31,
            33 to 29,
            35 to 31,
        )

    /** Region-local pillar positions from the Screech section of rsprox-3299-rev234. */
    val whispererScreechPillars: List<Pair<Int, Int>> =
        listOf(22 to 29, 24 to 25, 27 to 27, 30 to 28, 33 to 28, 36 to 27, 39 to 25, 41 to 29)

    /** Captured despawn order: six fall after pulse one, (30,28) after pulse two, (24,25) last. */
    fun whispererScreechStrength(local: Pair<Int, Int>): Int =
        when (local) {
            24 to 25 -> 3
            30 to 28 -> 2
            else -> 1
        }

    /** Four captured Soul Siphon clusters: 2 Vita, 3 Oratio, 3 Sanitas and 4 Mors. */
    val whispererSoulTiles: List<Pair<Pair<Int, Int>, WhispererSoulGroup>> =
        listOf(
            (26 to 39) to WhispererSoulGroup.VITA,
            (26 to 37) to WhispererSoulGroup.VITA,
            (24 to 37) to WhispererSoulGroup.ORATIO,
            (24 to 30) to WhispererSoulGroup.ORATIO,
            (26 to 28) to WhispererSoulGroup.ORATIO,
            (28 to 26) to WhispererSoulGroup.SANITAS,
            (36 to 39) to WhispererSoulGroup.SANITAS,
            (38 to 37) to WhispererSoulGroup.SANITAS,
            (40 to 37) to WhispererSoulGroup.MORS,
            (38 to 30) to WhispererSoulGroup.MORS,
            (36 to 28) to WhispererSoulGroup.MORS,
            (34 to 26) to WhispererSoulGroup.MORS,
        )

    fun vardorvisAxeCount(hitpoints: Int, awakened: Boolean): Int =
        if (awakened) {
            when {
                hitpoints < 462 -> 4
                hitpoints < 1_380 -> 3
                else -> 2
            }
        } else {
            when {
                hitpoints < 231 -> 3
                hitpoints <= 690 -> 2
                else -> 1
            }
        }

    fun vardorvisDartCount(hitpoints: Int): Int =
        when {
            hitpoints < 130 -> 3
            hitpoints <= 500 -> 2
            else -> 1
        }

    fun vardorvisAtDartEdge(playerX: Int, playerZ: Int, centerX: Int, centerZ: Int): Boolean =
        abs(playerX - centerX) >= 8 || abs(playerZ - centerZ) >= 8

    fun vardorvisEdgeDartOffset(
        playerX: Int,
        playerZ: Int,
        centerX: Int,
        centerZ: Int,
    ): Pair<Int, Int> = (centerX - playerX).coerceIn(-1, 1) to (centerZ - playerZ).coerceIn(-1, 1)

    const val VARDORVIS_NORMAL_HITPOINTS = 700
    const val VARDORVIS_HEAD_UNLOCK_HITPOINTS = 570
    const val VARDORVIS_ENRAGE_HITPOINTS = 231
    const val VARDORVIS_AXE_MAX_HIT = 35
    const val VARDORVIS_DART_MAX_HIT = 25
    const val VARDORVIS_STRANGLE_MAX_HIT = 25
    const val VARDORVIS_BLEED_DAMAGE = 3
    const val VARDORVIS_BLEED_DURATION_TICKS = 14
    const val VARDORVIS_BLEED_WALK_INTERVAL = 3
    const val VARDORVIS_HEAD_PRAYER_DRAIN = 10
    const val VARDORVIS_HEAD_PRAYER_DISABLE_TICKS = 3
    const val VARDORVIS_HEAD_BODY_RECOVERY_TICKS = 2
    const val VARDORVIS_STRANGLE_WINDOW_TICKS = 5
    const val VARDORVIS_AXE_ATTACK_INTERVAL = 3
    const val VARDORVIS_NORMAL_ATTACK_TICKS = 5
    const val VARDORVIS_AXE_MOVEMENT_TICKS = 5
    const val VARDORVIS_RANDOM_DART_COUNT = 4

    private val vardorvisAxeMovementProgress = intArrayOf(1, 3, 5, 7, 8)

    fun vardorvisNormalMaxHit(hitpoints: Int): Int {
        val lost = vardorvisMissingHitpoints(hitpoints)
        return 32 + lost * 11 / (VARDORVIS_NORMAL_HITPOINTS - 1)
    }

    fun vardorvisStrengthLevel(hitpoints: Int): Int {
        val lost = vardorvisMissingHitpoints(hitpoints)
        return 270 + lost * 90 / (VARDORVIS_NORMAL_HITPOINTS - 1)
    }

    fun vardorvisDefenceLevel(hitpoints: Int): Int {
        val lost = vardorvisMissingHitpoints(hitpoints)
        return 215 - lost * 70 / (VARDORVIS_NORMAL_HITPOINTS - 1)
    }

    private fun vardorvisMissingHitpoints(hitpoints: Int): Int =
        VARDORVIS_NORMAL_HITPOINTS - hitpoints.coerceIn(1, VARDORVIS_NORMAL_HITPOINTS)

    fun vardorvisMeleeDamage(actualRolledDamage: Int, protected: Boolean): Int =
        if (protected) protectedDamage(actualRolledDamage, 75)
        else actualRolledDamage.coerceAtLeast(0)

    fun vardorvisHeal(actualDamage: Int): Int = actualDamage.coerceAtLeast(0) / 2

    fun vardorvisEnraged(hitpoints: Int): Boolean = hitpoints <= VARDORVIS_ENRAGE_HITPOINTS

    fun vardorvisHeadUnlocked(hitpoints: Int): Boolean = hitpoints < VARDORVIS_HEAD_UNLOCK_HITPOINTS

    fun vardorvisStrangleCount(hitpoints: Int): Int {
        val progress =
            (VARDORVIS_HEAD_UNLOCK_HITPOINTS - hitpoints).coerceIn(
                0,
                VARDORVIS_HEAD_UNLOCK_HITPOINTS,
            )
        return (4 + progress * 4 / VARDORVIS_HEAD_UNLOCK_HITPOINTS).coerceIn(4, 8)
    }

    fun vardorvisBleedDue(elapsed: Int, ticksSinceLast: Int, running: Boolean): Boolean =
        elapsed in 0 until VARDORVIS_BLEED_DURATION_TICKS &&
            ticksSinceLast >= if (running) 1 else VARDORVIS_BLEED_WALK_INTERVAL

    fun vardorvisSpecialDelay(enraged: Boolean): Int = if (enraged) 7 else 10

    fun vardorvisSelectSpecial(
        roll: Int,
        previous: VardorvisSpecial?,
        headUnlocked: Boolean,
    ): VardorvisSpecial {
        if (!headUnlocked) return VardorvisSpecial.DARTING_SPIKES
        val choices =
            if (previous == VardorvisSpecial.STRANGLE) {
                listOf(VardorvisSpecial.DARTING_SPIKES, VardorvisSpecial.HEAD_GAZE)
            } else {
                listOf(
                    VardorvisSpecial.DARTING_SPIKES,
                    VardorvisSpecial.HEAD_GAZE,
                    VardorvisSpecial.STRANGLE,
                )
            }
        return choices[Math.floorMod(roll, choices.size)]
    }

    fun vardorvisAxeLanes(): List<VardorvisAxeLane> =
        listOf(
            VardorvisAxeLane(35, 25, 37, 25, 45, 25),
            VardorvisAxeLane(35, 30, 37, 28, 45, 20),
            VardorvisAxeLane(40, 30, 40, 28, 40, 20),
            VardorvisAxeLane(45, 30, 43, 28, 35, 20),
            VardorvisAxeLane(45, 25, 43, 25, 35, 25),
            VardorvisAxeLane(45, 20, 43, 22, 35, 30),
            VardorvisAxeLane(40, 20, 40, 22, 40, 30),
            VardorvisAxeLane(35, 20, 37, 22, 45, 30),
        )

    fun vardorvisAxeMovementTiles(lane: VardorvisAxeLane): List<Pair<Int, Int>> {
        val directionX = (lane.movingEndX - lane.movingStartX).sign()
        val directionZ = (lane.movingEndZ - lane.movingStartZ).sign()
        return vardorvisAxeMovementProgress.map { progress ->
            lane.movingStartX + directionX * progress to lane.movingStartZ + directionZ * progress
        }
    }

    fun vardorvisAxeSweptHit(
        playerX: Int,
        playerZ: Int,
        fromX: Int,
        fromZ: Int,
        toX: Int,
        toZ: Int,
    ): Boolean {
        val directionX = (toX - fromX).sign()
        val directionZ = (toZ - fromZ).sign()
        val perpendicularX = -directionZ
        val perpendicularZ = directionX
        val steps = max(kotlin.math.abs(toX - fromX), kotlin.math.abs(toZ - fromZ))
        for (progress in 0..steps) {
            val centreX = fromX + directionX * progress
            val centreZ = fromZ + directionZ * progress
            for (width in -1..1) {
                if (
                    playerX == centreX + perpendicularX * width &&
                        playerZ == centreZ + perpendicularZ * width
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun Int.sign(): Int =
        when {
            this < 0 -> -1
            this > 0 -> 1
            else -> 0
        }

    fun vardorvisDartPattern(
        waveIndex: Int,
        horizontal: Boolean,
        rolls: List<Int> = emptyList(),
    ): Set<Pair<Int, Int>> {
        require(waveIndex in 0..2)
        val first =
            listOf(-2 to -1, -1 to -1, 0 to 0, 1 to 1, 2 to 1).mapTo(linkedSetOf()) { (x, z) ->
                vardorvisOrientedTile(x, z, horizontal)
            }
        val second =
            listOf(-2 to 1, -1 to 1, 0 to 0, 1 to -1, 2 to -1).mapTo(linkedSetOf()) { (x, z) ->
                vardorvisOrientedTile(x, z, horizontal)
            }
        if (waveIndex == 0) return first
        if (waveIndex == 1) return second
        return vardorvisRandomDartPattern(first + second, rolls)
    }

    private fun vardorvisOrientedTile(x: Int, z: Int, horizontal: Boolean): Pair<Int, Int> =
        if (horizontal) x to z else z to x

    private fun vardorvisRandomDartPattern(
        occupied: Set<Pair<Int, Int>>,
        rolls: List<Int>,
    ): Set<Pair<Int, Int>> {
        require(rolls.size >= VARDORVIS_RANDOM_DART_COUNT)
        val candidates =
            buildList {
                    for (x in -2..2) {
                        for (z in -2..2) {
                            val tile = x to z
                            if (tile !in occupied) add(tile)
                        }
                    }
                }
                .toMutableList()
        return buildSet {
            repeat(VARDORVIS_RANDOM_DART_COUNT) { index ->
                val selected = Math.floorMod(rolls[index], candidates.size)
                add(candidates.removeAt(selected))
            }
        }
    }

    val LEVIATHAN_VOLLEY_SHOTS = listOf(6, 8, 8, 12, 10, 10, 12)
    const val LEVIATHAN_NORMAL_STUN_TICKS = 15
    const val LEVIATHAN_AWAKENED_STUN_TICKS = 8
    const val LEVIATHAN_NORMAL_ENRAGE_PERCENT = 20
    val LEVIATHAN_AWAKENED_STATS =
        LeviathanCombatStats(attack = 525, strength = 287, defence = 280, magic = 280, ranged = 260)
    const val LEVIATHAN_PATHFINDER_RADIUS = 2
    const val LEVIATHAN_AWAKENED_PATHFINDER_RADIUS = 3
    const val LEVIATHAN_ROAR_MAX_HIT = 7
    const val LEVIATHAN_DEBRIS_MAX_HIT = 25
    const val LEVIATHAN_STUN_FRONT_CAP = 10
    const val LEVIATHAN_STUN_RESET_DAMAGE_PERCENT = 25
    const val LEVIATHAN_WOUND_RANGED_MIN_PERCENT = 65
    const val LEVIATHAN_SPECIAL_DAMAGE_PERCENT = 67
    const val LEVIATHAN_OUTSIDE_AURA_DAMAGE_PERCENT = 50
    const val LEVIATHAN_OUTSIDE_AURA_PRAYER_REDUCTION_PERCENT = 75
    const val LEVIATHAN_TORNADO_ACTIVATION_PERCENT = 50
    const val LEVIATHAN_TORNADO_MIN_HIT = 40
    const val LEVIATHAN_PATHFINDER_MIN_HIT_PERCENT = 20
    const val LEVIATHAN_ROAR_TRANSIENT_DEBRIS_MIN = 12
    const val LEVIATHAN_ROAR_TRANSIENT_DEBRIS_MAX = 36
    const val LEVIATHAN_NORMAL_ROAR_BLOCKER_COUNT = 3
    const val LEVIATHAN_AWAKENED_ROAR_BLOCKER_COUNT = 5
    const val LEVIATHAN_ROAR_BLOCKER_LAND_TICKS = 7
    const val LEVIATHAN_ROAR_BLOCKER_CLIENT_DELAY = 50
    const val LEVIATHAN_TORNADO_CONTACT_COOLDOWN = 7
    const val LEVIATHAN_NORMAL_PATHFINDER_SPAWN_DELAY = 4
    const val LEVIATHAN_AWAKENED_PATHFINDER_SPAWN_DELAY = 23
    const val LEVIATHAN_ENRAGE_DEBRIS_INTERVAL_MIN = 4
    const val LEVIATHAN_ENRAGE_DEBRIS_INTERVAL_MAX = 12
    const val LEVIATHAN_ENRAGE_PERSISTENT_DEBRIS_INTERVAL = 16
    const val LEVIATHAN_ENRAGE_PERSISTENT_DEBRIS_LAND_TICKS = 7
    const val LEVIATHAN_NORMAL_SMOKE_MAX_HIT = 59
    const val LEVIATHAN_AWAKENED_SMOKE_MAX_HIT = 70
    const val LEVIATHAN_NORMAL_LIGHTNING_MAX_HIT = 20
    const val LEVIATHAN_AWAKENED_LIGHTNING_MAX_HIT = 30
    const val LEVIATHAN_AP_RANGE = 12
    const val LEVIATHAN_MAX_OP_RANGE = 20
    const val LEVIATHAN_SIZE = 7
    const val LEVIATHAN_ORB_START_HEIGHT = 550
    const val LEVIATHAN_ORB_END_HEIGHT = 100
    const val LEVIATHAN_ORB_IMPACT_HEIGHT = 100
    const val LEVIATHAN_ORB_IMPACT_SLOT = 2

    val LEVIATHAN_DEBRIS_CLIENT_DELAYS = listOf(20, 50, 80)

    fun leviathanVolleyTier(attacks: Int): Int {
        val completedShots = attacks.coerceAtLeast(0)
        var boundary = 0
        LEVIATHAN_VOLLEY_SHOTS.forEachIndexed { tier, shots ->
            boundary += shots
            if (completedShots < boundary) return tier
        }
        return LEVIATHAN_VOLLEY_SHOTS.lastIndex
    }

    fun leviathanVolleyComplete(attacks: Int): Boolean =
        attacks > 0 && LEVIATHAN_VOLLEY_SHOTS.runningFold(0, Int::plus).drop(1).contains(attacks)

    fun leviathanInitialAttackCounter(awakened: Boolean): Int =
        if (awakened) LEVIATHAN_VOLLEY_SHOTS.first() else 0

    fun leviathanPathfinderSpawnDelay(awakened: Boolean): Int =
        if (awakened) LEVIATHAN_AWAKENED_PATHFINDER_SPAWN_DELAY
        else LEVIATHAN_NORMAL_PATHFINDER_SPAWN_DELAY

    fun leviathanRoarBlockerCount(awakened: Boolean): Int =
        if (awakened) LEVIATHAN_AWAKENED_ROAR_BLOCKER_COUNT else LEVIATHAN_NORMAL_ROAR_BLOCKER_COUNT

    fun leviathanAttackRate(tier: Int, enraged: Boolean, awakened: Boolean): Int =
        when {
            enraged && awakened -> 1
            enraged -> 2
            tier <= 0 -> 3
            tier <= 2 -> 2
            else -> 1
        }

    fun leviathanStyles(tier: Int): List<Dt2AttackStyle> =
        when (tier.coerceIn(0, 6)) {
            0,
            1,
            3 -> listOf(Dt2AttackStyle.MAGIC, Dt2AttackStyle.RANGED)
            else -> listOf(Dt2AttackStyle.MAGIC, Dt2AttackStyle.RANGED, Dt2AttackStyle.MELEE)
        }

    fun leviathanStyle(tier: Int, enraged: Boolean, roll: Int): Dt2AttackStyle {
        val styles =
            if (enraged) listOf(Dt2AttackStyle.MAGIC, Dt2AttackStyle.RANGED)
            else leviathanStyles(tier)
        return styles[Math.floorMod(roll, styles.size)]
    }

    fun leviathanProjectileAnimation(tier: Int, enraged: Boolean): Int =
        if (enraged || tier >= 3) 10279 else 10278

    fun leviathanProjectileLengthAdjustment(tier: Int, enraged: Boolean, awakened: Boolean): Int =
        when {
            enraged && awakened -> 40
            enraged -> 100
            tier >= 5 -> 10
            tier >= 3 -> 40
            tier == 2 -> 70
            else -> 100
        }

    /** Fixed target-facing mouth tile used by Leviathan's source-less projectile packet. */
    fun leviathanProjectileSource(
        player: CoordGrid,
        bossSouthWest: CoordGrid,
        bossSize: Int,
    ): CoordGrid {
        require(player.level == bossSouthWest.level && bossSize > 0)
        val centre = bossSouthWest.translate(bossSize / 2, bossSize / 2)
        val deltaX = player.x - centre.x
        val deltaZ = player.z - centre.z
        val absoluteX = kotlin.math.abs(deltaX)
        val absoluteZ = kotlin.math.abs(deltaZ)
        val offsetX =
            if (absoluteX * 2 <= absoluteZ) 0 else if (deltaX < 0) -2 else if (deltaX > 0) 2 else 0
        val offsetZ =
            if (absoluteZ * 2 <= absoluteX) 0 else if (deltaZ < 0) -2 else if (deltaZ > 0) 2 else 0
        return centre.translate(offsetX, offsetZ)
    }

    fun leviathanFirstSpecial(axis: LeviathanDebrisAxis): LeviathanSpecial =
        when (axis) {
            LeviathanDebrisAxis.EAST_WEST -> LeviathanSpecial.LIGHTNING_BARRAGE
            LeviathanDebrisAxis.NORTH_SOUTH -> LeviathanSpecial.SMOKE_BLAST
        }

    fun leviathanRetaliationAction(nextSpecial: LeviathanSpecial?): LeviathanSpecial =
        nextSpecial ?: LeviathanSpecial.VOLLEY_ROAR

    fun leviathanNextSpecial(previous: LeviathanSpecial): LeviathanSpecial =
        when (previous) {
            LeviathanSpecial.LIGHTNING_BARRAGE -> LeviathanSpecial.SMOKE_BLAST
            LeviathanSpecial.SMOKE_BLAST -> LeviathanSpecial.LIGHTNING_BARRAGE
            LeviathanSpecial.VOLLEY_ROAR ->
                error("Roar does not participate in special alternation.")
        }

    fun leviathanSpecial(index: Int): LeviathanSpecial =
        if (index and 1 == 0) LeviathanSpecial.LIGHTNING_BARRAGE else LeviathanSpecial.SMOKE_BLAST

    fun leviathanEnraged(hitpointsAfterHit: Int, maximum: Int): Boolean =
        hitpointsAfterHit * 100 <= maximum * LEVIATHAN_NORMAL_ENRAGE_PERCENT

    fun leviathanStunTicks(difficulty: Dt2Difficulty): Int =
        if (difficulty == Dt2Difficulty.AWAKENED) LEVIATHAN_AWAKENED_STUN_TICKS
        else LEVIATHAN_NORMAL_STUN_TICKS

    fun leviathanStunResetsVolley(
        cumulativeDamage: Int,
        maximumHitpoints: Int,
        woundHit: Boolean,
    ): Boolean =
        woundHit ||
            (maximumHitpoints > 0 &&
                cumulativeDamage.coerceAtLeast(0) * 100 >=
                    maximumHitpoints * LEVIATHAN_STUN_RESET_DAMAGE_PERCENT)

    fun leviathanResetAttackCounter(stunCount: Int): Int =
        LEVIATHAN_VOLLEY_SHOTS.take(stunCount.coerceIn(1, 6)).sum()

    fun isLeviathanShadowSpell(spell: ItemServerType?): Boolean =
        spell?.isAnyType(
            "obj.52_shadow_rush",
            "obj.64_shadow_burst",
            "obj.76_shadow_blitz",
            "obj.88_shadow_barrage",
        ) == true

    /**
     * The Unconventional task permits the shadow spell required for stunning, but every other
     * damaging attack must use actual mithril ammunition.
     */
    fun isLeviathanMithrilAmmunition(internalName: String?): Boolean =
        internalName != null &&
            (internalName.startsWith("obj.mithril_arrow") ||
                internalName.startsWith("obj.mithril_dart") ||
                internalName.startsWith("obj.xbows_crossbow_bolts_mithril"))

    fun leviathanPerfectStandardFailure(
        finalDamage: Int,
        bite: Boolean,
        protectedAtImpact: Boolean,
    ): Boolean = finalDamage > 0 && (bite || !protectedAtImpact)

    fun leviathanCanStun(
        action: LeviathanAction,
        enraged: Boolean,
        successfulShadowHit: Boolean,
    ): Boolean = successfulShadowHit && !enraged && action == LeviathanAction.STANDARD_VOLLEY

    fun leviathanSpecialDamage(damage: Int): Int =
        damage.coerceAtLeast(0) * LEVIATHAN_SPECIAL_DAMAGE_PERCENT / 100

    fun leviathanPlayerDamage(damage: Int, enraged: Boolean, insidePathfinder: Boolean): Int =
        if (enraged && !insidePathfinder) {
            damage.coerceAtLeast(0) * LEVIATHAN_OUTSIDE_AURA_DAMAGE_PERCENT / 100
        } else {
            damage.coerceAtLeast(0)
        }

    fun leviathanPathfinderOutgoingDamage(
        damage: Int,
        maximumHit: Int,
        style: Dt2AttackStyle,
        enraged: Boolean,
        insidePathfinder: Boolean,
    ): Int {
        val nonNegative = damage.coerceAtLeast(0)
        if (!enraged) return nonNegative
        if (!insidePathfinder) return leviathanPlayerDamage(nonNegative, true, false)
        if (style == Dt2AttackStyle.MAGIC) return nonNegative * 2
        val minimum = maximumHit.coerceAtLeast(0) * LEVIATHAN_PATHFINDER_MIN_HIT_PERCENT / 100
        return max(nonNegative, minimum)
    }

    fun leviathanWoundDamage(
        damage: Int,
        maximumHit: Int,
        style: Dt2AttackStyle,
        behind: Boolean,
    ): Int {
        if (!behind) return damage.coerceIn(0, LEVIATHAN_STUN_FRONT_CAP)
        return when (style) {
            Dt2AttackStyle.RANGED ->
                max(
                    damage.coerceAtLeast(0),
                    maximumHit.coerceAtLeast(0) * LEVIATHAN_WOUND_RANGED_MIN_PERCENT / 100,
                )
            Dt2AttackStyle.MAGIC -> damage.coerceAtLeast(0) * 2
            Dt2AttackStyle.MELEE -> damage.coerceAtLeast(0)
        }
    }

    fun leviathanFacing(angle: Int): LeviathanFacing =
        LeviathanFacing.entries.minBy {
            val difference = ((angle - it.angle + 1024) and 2047) - 1024
            kotlin.math.abs(difference)
        }

    fun leviathanBehindWound(
        player: CoordGrid,
        bossSouthWest: CoordGrid,
        bossSize: Int,
        facing: LeviathanFacing,
    ): Boolean {
        if (player.level != bossSouthWest.level || bossSize <= 0) return false
        val playerCenterX2 = player.x * 2 + 1
        val playerCenterZ2 = player.z * 2 + 1
        val bossCenterX2 = bossSouthWest.x * 2 + bossSize
        val bossCenterZ2 = bossSouthWest.z * 2 + bossSize
        val fromBossX2 = playerCenterX2 - bossCenterX2
        val fromBossZ2 = playerCenterZ2 - bossCenterZ2
        return fromBossX2 * facing.x + fromBossZ2 * facing.z < 0
    }

    fun leviathanAdjacentToFootprint(
        player: CoordGrid,
        bossSouthWest: CoordGrid,
        bossSize: Int,
    ): Boolean {
        if (player.level != bossSouthWest.level || bossSize <= 0) return false
        val maximumX = bossSouthWest.x + bossSize - 1
        val maximumZ = bossSouthWest.z + bossSize - 1
        val dx =
            when {
                player.x < bossSouthWest.x -> bossSouthWest.x - player.x
                player.x > maximumX -> player.x - maximumX
                else -> 0
            }
        val dz =
            when {
                player.z < bossSouthWest.z -> bossSouthWest.z - player.z
                player.z > maximumZ -> player.z - maximumZ
                else -> 0
            }
        return max(dx, dz) == 1
    }

    fun leviathanPathfinderRadius(awakened: Boolean): Int =
        if (awakened) LEVIATHAN_AWAKENED_PATHFINDER_RADIUS else LEVIATHAN_PATHFINDER_RADIUS

    fun leviathanInsidePathfinder(
        player: CoordGrid,
        pathfinder: CoordGrid,
        awakened: Boolean,
    ): Boolean {
        if (player.level != pathfinder.level) return false
        val radius = leviathanPathfinderRadius(awakened)
        return kotlin.math.abs(player.x - pathfinder.x) <= radius &&
            kotlin.math.abs(player.z - pathfinder.z) <= radius
    }

    fun leviathanPathfinderCorners(northWest: CoordGrid, center: CoordGrid): List<CoordGrid> {
        require(northWest.level == center.level)
        val eastX = center.x + (center.x - northWest.x)
        val southZ = center.z - (northWest.z - center.z)
        return listOf(
            northWest,
            CoordGrid(eastX, northWest.z, northWest.level),
            CoordGrid(eastX, southZ, northWest.level),
            CoordGrid(northWest.x, southZ, northWest.level),
        )
    }

    fun leviathanClockwisePath(corners: List<CoordGrid>): List<CoordGrid> {
        require(corners.size == 4)
        val (northWest, northEast, southEast, southWest) = corners
        require(corners.all { it.level == northWest.level })
        val route = mutableListOf<CoordGrid>()
        for (x in northWest.x..northEast.x) route += CoordGrid(x, northWest.z, northWest.level)
        for (z in (northEast.z - 1) downTo southEast.z) {
            route += CoordGrid(northEast.x, z, northWest.level)
        }
        for (x in (southEast.x - 1) downTo southWest.x) {
            route += CoordGrid(x, southEast.z, northWest.level)
        }
        for (z in (southWest.z + 1) until northWest.z) {
            route += CoordGrid(southWest.x, z, northWest.level)
        }
        return route.distinct()
    }

    fun leviathanDebrisBlocksLine(
        bossCenter: CoordGrid,
        player: CoordGrid,
        debris: CoordGrid,
    ): Boolean {
        if (bossCenter.level != player.level || debris.level != player.level) return false
        if (debris == bossCenter || debris == player) return false
        var x = bossCenter.x
        var z = bossCenter.z
        val dx = kotlin.math.abs(player.x - x)
        val dz = kotlin.math.abs(player.z - z)
        val stepX = if (x < player.x) 1 else -1
        val stepZ = if (z < player.z) 1 else -1
        var error = dx - dz
        while (x != player.x || z != player.z) {
            val twiceError = error * 2
            if (twiceError > -dz) {
                error -= dz
                x += stepX
            }
            if (twiceError < dx) {
                error += dx
                z += stepZ
            }
            if (x == debris.x && z == debris.z && (x != player.x || z != player.z)) return true
        }
        return false
    }

    fun leviathanOrbDamage(
        rolledDamage: Int,
        protected: Boolean,
        enraged: Boolean,
        insidePathfinder: Boolean,
    ): Int {
        if (!protected) return rolledDamage.coerceAtLeast(0)
        if (!enraged || insidePathfinder) return 0
        return protectedDamage(rolledDamage, LEVIATHAN_OUTSIDE_AURA_PRAYER_REDUCTION_PERCENT)
    }

    fun leviathanInsidePathfinderOrTransition(
        enraged: Boolean,
        pathfinderPresent: Boolean,
        geometricInside: Boolean,
    ): Boolean = !enraged || !pathfinderPresent || geometricInside

    fun leviathanTornadoActive(hitpointsAfterHit: Int, maximum: Int, awakened: Boolean): Boolean =
        awakened && hitpointsAfterHit * 100 <= maximum * LEVIATHAN_TORNADO_ACTIVATION_PERCENT

    fun leviathanTornadoCanMove(currentCycle: Int, lastContactCycle: Int): Boolean =
        lastContactCycle == Int.MIN_VALUE ||
            currentCycle >= lastContactCycle + LEVIATHAN_TORNADO_CONTACT_COOLDOWN

    fun leviathanRoarTransientDebrisCount(roll: Int): Int =
        LEVIATHAN_ROAR_TRANSIENT_DEBRIS_MIN +
            Math.floorMod(
                roll,
                LEVIATHAN_ROAR_TRANSIENT_DEBRIS_MAX - LEVIATHAN_ROAR_TRANSIENT_DEBRIS_MIN + 1,
            )

    fun leviathanEnrageDebrisInterval(roll: Int): Int =
        LEVIATHAN_ENRAGE_DEBRIS_INTERVAL_MIN +
            Math.floorMod(
                roll,
                LEVIATHAN_ENRAGE_DEBRIS_INTERVAL_MAX - LEVIATHAN_ENRAGE_DEBRIS_INTERVAL_MIN + 1,
            )

    fun leviathanDebrisImpactTicks(clientDelay: Int): Int =
        ((clientDelay.coerceAtLeast(0) + 29) / 30).coerceAtLeast(1)

    fun leviathanLightningImpactTicks(playerHitpoints: Int): Int =
        if (playerHitpoints > 25) 2 else 1

    /**
     * Returns every newly crossed tile for the lightning current. Running can advance the player
     * two tiles in one game tick, so emitting only at the sampled destination leaves a visible and
     * mechanically safe gap. Large jumps are treated as teleports and do not draw across the room.
     */
    fun leviathanLightningTrail(previous: CoordGrid?, current: CoordGrid): List<CoordGrid> {
        if (previous == null || previous.level != current.level) return listOf(current)
        val dx = current.x - previous.x
        val dz = current.z - previous.z
        val distance = max(kotlin.math.abs(dx), kotlin.math.abs(dz))
        if (distance == 0 || distance > 2) return listOf(current)
        return (1..distance)
            .map { step ->
                CoordGrid(
                    x = previous.x + dx * step / distance,
                    z = previous.z + dz * step / distance,
                    level = current.level,
                )
            }
            .distinct()
    }

    fun leviathanLightningTileAllowed(
        tile: CoordGrid,
        bossSouthWest: CoordGrid,
        bossSize: Int,
        arenaNorthWest: CoordGrid,
        arenaSouthEast: CoordGrid,
    ): Boolean {
        if (
            tile.level != bossSouthWest.level ||
                tile.level != arenaNorthWest.level ||
                tile.level != arenaSouthEast.level ||
                bossSize <= 0
        ) {
            return false
        }
        if (
            tile.x !in arenaNorthWest.x..arenaSouthEast.x ||
                tile.z !in arenaSouthEast.z..arenaNorthWest.z
        ) {
            return false
        }
        val maximumX = bossSouthWest.x + bossSize - 1
        val maximumZ = bossSouthWest.z + bossSize - 1
        return tile.x < bossSouthWest.x - 1 ||
            tile.x > maximumX + 1 ||
            tile.z < bossSouthWest.z - 1 ||
            tile.z > maximumZ + 1
    }

    fun leviathanSmokeMaximumHit(awakened: Boolean): Int =
        if (awakened) LEVIATHAN_AWAKENED_SMOKE_MAX_HIT else LEVIATHAN_NORMAL_SMOKE_MAX_HIT

    fun leviathanLightningMaximumHit(awakened: Boolean): Int =
        if (awakened) LEVIATHAN_AWAKENED_LIGHTNING_MAX_HIT else LEVIATHAN_NORMAL_LIGHTNING_MAX_HIT

    fun leviathanValidStart(locId: Int, clicked: CoordGrid, expected: CoordGrid?): Boolean =
        locId == Dt2Assets.LEVIATHAN_START_HANDHOLDS && expected != null && clicked == expected

    fun leviathanCrossedCombatTrigger(player: CoordGrid, trigger: CoordGrid): Boolean =
        player.level == trigger.level &&
            player.z >= trigger.z &&
            player.x in (trigger.x - 8)..(trigger.x + 8)

    fun leviathanAttackContract(
        style: Dt2AttackStyle,
        adjacentBite: Boolean,
        attackCounter: Int,
    ): LeviathanAttackContract {
        if (adjacentBite) {
            return LeviathanAttackContract(
                animation = 10283 + (attackCounter and 1),
                castGraphic = null,
                projectileGraphic = null,
                impactGraphic = null,
                maximumHit = 50,
            )
        }
        return when (style) {
            Dt2AttackStyle.RANGED ->
                LeviathanAttackContract(
                    10278,
                    Dt2Assets.LEVIATHAN_RANGED_CAST,
                    Dt2Assets.LEVIATHAN_RANGED_PROJECTILE,
                    Dt2Assets.LEVIATHAN_RANGED_IMPACT,
                    24,
                )
            Dt2AttackStyle.MAGIC ->
                LeviathanAttackContract(
                    10278,
                    Dt2Assets.LEVIATHAN_MAGIC_CAST,
                    Dt2Assets.LEVIATHAN_MAGIC_PROJECTILE,
                    Dt2Assets.LEVIATHAN_MAGIC_IMPACT,
                    32,
                )
            Dt2AttackStyle.MELEE ->
                LeviathanAttackContract(
                    10278,
                    Dt2Assets.LEVIATHAN_MELEE_CAST,
                    Dt2Assets.LEVIATHAN_MELEE_PROJECTILE,
                    Dt2Assets.LEVIATHAN_MELEE_IMPACT,
                    50,
                )
        }
    }

    fun leviathanSpecialContract(special: LeviathanSpecial): LeviathanSpecialContract =
        when (special) {
            LeviathanSpecial.VOLLEY_ROAR -> LeviathanSpecialContract(10282, null, null, 4)
            LeviathanSpecial.LIGHTNING_BARRAGE -> LeviathanSpecialContract(10285, 10286, 10287, 18)
            LeviathanSpecial.SMOKE_BLAST -> LeviathanSpecialContract(10288, 10289, 10290, 16)
        }

    fun dukePoisonRequired(difficulty: Dt2Difficulty): Int =
        DukeRules.poisonDoses(difficulty == Dt2Difficulty.AWAKENED)

    fun protectedDamage(damage: Int, reductionPercent: Int): Int =
        max(0, damage - damage * reductionPercent / 100)

    fun formatTicks(ticks: Int): String {
        val tenths = ticks.coerceAtLeast(0) * 6
        val totalSeconds = tenths / 10
        val tenth = tenths % 10
        return if (totalSeconds < 60) "$totalSeconds.$tenth seconds"
        else "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}.$tenth"
    }
}

internal class Dt2SeededRng(seed: Long) {
    private var state = if (seed == 0L) 0x6d2b79f5L else seed

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        state = (state * 6364136223846793005L + 1442695040888963407L)
        return ((state ushr 33) % bound.toLong()).toInt()
    }
}
