package org.rsmod.content.minigames.toa

import kotlin.math.abs
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

internal object KephriEncounter {
    const val ACTIVE = 11719
    const val RECOVERING = 11720
    const val FINAL = 11721
    const val DEAD = 11722
    const val SHIELD_HITPOINTS = 150
    const val FINAL_HITPOINTS = 80
    const val RECOVERY_PHASES = 2
    const val FINAL_PHASE = 3
    const val BOMB_ANIMATION = 9577
    const val SPECIAL_ANIMATION = 9578
    const val STUN_ANIMATION = 9579
    const val RECOVER_ANIMATION = 9581
    const val DEATH_ANIMATION = 9582
    const val SHIELD_GRAPHIC = 2141
    const val RECOVERY_SHIELD_GRAPHIC = 2142
    const val SHIELD_BREAK_GRAPHIC = 2143
    const val REFORM_GRAPHIC = 2144
    const val DEATH_COMPLETION_TICKS = 3
    const val ROOM_MIDI = 730
    const val COMBAT_MIDI = 737
    const val FIRST_ATTACK_DELAY = 4
}

internal enum class KephriAttackKind {
    BOMB,
    DUNG_STRIKE,
    MASS_INCUBATION,
}

internal data class KephriAttackDecision(
    val kind: KephriAttackKind,
    val attacksUntilSpecial: Int,
    val nextSpecialDung: Boolean,
    val nextDelay: Int,
)

/** Packet-derived level-0 attack order. Values count ordinary attacks before the next special. */
internal object KephriAttackCycle {
    const val INITIAL_BOMBS = 3
    const val BETWEEN_SHIELDED_SPECIALS = 5
    const val FINAL_FIRST_DUNG_BOMBS = 5
    const val FINAL_REPEAT_DUNG_BOMBS = 2
    const val NORMAL_DELAY = 6
    const val DUNG_DELAY = 13

    fun next(
        phase: Int,
        attacksUntilSpecial: Int,
        nextSpecialDung: Boolean,
        agileCount: Int,
    ): KephriAttackDecision {
        if (attacksUntilSpecial > 0) {
            return KephriAttackDecision(
                kind = KephriAttackKind.BOMB,
                attacksUntilSpecial = attacksUntilSpecial - 1,
                nextSpecialDung = nextSpecialDung,
                nextDelay = NORMAL_DELAY,
            )
        }
        if (phase >= KephriEncounter.FINAL_PHASE) {
            return KephriAttackDecision(
                kind = KephriAttackKind.DUNG_STRIKE,
                attacksUntilSpecial = FINAL_REPEAT_DUNG_BOMBS,
                nextSpecialDung = true,
                nextDelay = DUNG_DELAY,
            )
        }
        val kind =
            if (nextSpecialDung || agileCount >= KephriEggs.MASS_INCUBATION_AGILE_LIMIT) {
                KephriAttackKind.DUNG_STRIKE
            } else {
                KephriAttackKind.MASS_INCUBATION
            }
        return KephriAttackDecision(
            kind = kind,
            attacksUntilSpecial = BETWEEN_SHIELDED_SPECIALS,
            nextSpecialDung = !nextSpecialDung,
            nextDelay = if (kind == KephriAttackKind.DUNG_STRIKE) DUNG_DELAY else NORMAL_DELAY,
        )
    }
}

/** Immutable encounter-facing projection of the locked raid-start invocation snapshot. */
internal data class KephriInvocationProfile(
    val massIncubationBrownEggs: Int,
    val addMissingOverlord: Boolean,
    val dungStrikeTargets: Int,
    val medicSwarmsOutsideShield: Boolean,
    val medicSpawnInterval: Int,
    val medicMoveInterval: Int,
    val aerialAssault: Boolean,
    val bombRadius: Int,
    val bombDamage: Int,
)

internal object KephriInvocationRules {
    const val MEDIC_SPAWN_INTERVAL = 12
    const val MEDIC_MOVE_INTERVAL = 3
    const val AERIAL_ASSAULT_DAMAGE = 32

    fun resolve(
        invocations: Set<ToaInvocation>,
        partySize: Int,
        nextInt: (Int) -> Int = { 0 },
    ): KephriInvocationProfile {
        val players = partySize.coerceIn(1, 8)
        val lively = ToaInvocation.LIVELY_LARVAE in invocations
        val aerial = ToaInvocation.AERIAL_ASSAULT in invocations
        val brownEggs =
            if (lively) {
                // Current OSRS: 3-5 brown eggs per player. Seeded raid RNG supplies the roll.
                players * (3 + nextInt(3).coerceIn(0, 2))
            } else {
                // Protected level-0 baseline. Do not silently retune the verified raid here.
                KephriEggs.darkEggCount(players, livelyLarvae = false, roll = 0)
            }
        return KephriInvocationProfile(
            massIncubationBrownEggs = brownEggs,
            addMissingOverlord = ToaInvocation.MORE_OVERLORDS in invocations,
            // Blowing Mud targets two players and has no extra effect in solo raids.
            dungStrikeTargets =
                if (ToaInvocation.BLOWING_MUD in invocations && players > 1) 2 else 1,
            medicSwarmsOutsideShield = ToaInvocation.MEDIC in invocations,
            medicSpawnInterval = MEDIC_SPAWN_INTERVAL,
            medicMoveInterval = MEDIC_MOVE_INTERVAL,
            aerialAssault = aerial,
            bombRadius = KephriBombs.radius(aerial),
            bombDamage = if (aerial) AERIAL_ASSAULT_DAMAGE else KephriBombs.BASE_DAMAGE,
        )
    }
}

/** Native revision-240 assets and deterministic geometry for Kephri's Dung Strike. */
internal object KephriDungStrike {
    const val DUNG_LOC = 45504
    const val DUNG_GRAPHIC = 2145
    const val PLAYER_FLIES_GRAPHIC = 2146
    const val PLAYER_THROW_ANIMATION = 9799
    const val PLAYER_DUNG_MOVE_ANIMATION = 1114
    const val PLAYER_STUN_GRAPHIC = 245
    const val TELEGRAPH_TICKS = 5
    const val TRAIL_GRAPHIC_DELAY = 2
    const val FIRST_LOC_DELAY = 4
    const val MAX_RADIUS = 8
    const val STUN_TICKS = 4
    const val MOVE_START_CLIENT_CYCLES = 5

    data class Step(val dx: Int, val dz: Int)

    fun targetIndices(partySize: Int, cursor: Int, requested: Int): List<Int> {
        require(partySize > 0)
        if (requested <= 0) return emptyList()
        val count = requested.coerceAtMost(partySize)
        val start = cursor.mod(partySize)
        return List(count) { (start + it) % partySize }
    }

    fun direction(center: CoordGrid, target: CoordGrid): Step {
        val dx = target.x - center.x
        val dz = target.z - center.z
        val sx = dx.coerceIn(-1, 1)
        val sz = dz.coerceIn(-1, 1)
        if (sx == 0 && sz == 0) return Step(0, 1)
        return when {
            abs(abs(dx) - abs(dz)) <= 1 -> Step(sx, sz)
            abs(dx) > abs(dz) -> Step(sx, 0)
            else -> Step(0, sz)
        }
    }

    fun remainingPushTiles(center: CoordGrid, target: CoordGrid): Int =
        (MAX_RADIUS - maxOf(abs(target.x - center.x), abs(target.z - center.z))).coerceAtLeast(0)

    fun collisionSafeDestination(
        start: CoordGrid,
        step: Step,
        maximumTiles: Int,
        canTravel: (CoordGrid, Step) -> Boolean,
    ): CoordGrid {
        var destination = start
        repeat(maximumTiles.coerceAtLeast(0)) {
            if (!canTravel(destination, step)) return destination
            destination =
                CoordGrid(destination.x + step.dx, destination.z + step.dz, destination.level)
        }
        return destination
    }

    fun movementClientCycles(distance: Int): Int = 29 + maxOf(1, distance.coerceAtLeast(0) / 3) * 30

    /**
     * The trail begins just outside Kephri's 5x5 body and includes the shove ray through the
     * destination. This intentionally is not a fixed radius-2..8 ray: blocked shoves are shorter.
     */
    fun path(center: CoordGrid, target: CoordGrid, destination: CoordGrid): List<CoordGrid> {
        var current =
            CoordGrid(
                center.x + (target.x - center.x).coerceIn(-2, 2),
                center.z + (target.z - center.z).coerceIn(-2, 2),
                center.level,
            )
        val result = mutableListOf<CoordGrid>()
        while (current != destination && result.size < MAX_RADIUS * 2) {
            val step = direction(current, destination)
            current = CoordGrid(current.x + step.dx, current.z + step.dz, current.level)
            result += current
        }
        return result
    }
}

internal data class KephriDungStrikeRuntime(val target: PlayerUid, val resolvesAt: Int)

internal data class KephriDungPlacementRuntime(
    val tile: CoordGrid,
    val graphicAt: Int,
    val locAt: Int,
    val clientDelay: Int,
    var graphicSent: Boolean = false,
)

internal object KephriBomberScarabs {
    const val INITIAL_DELAY = 8
    const val INTERVAL = 12

    private val sourceOffsets = listOf(-9 to 9, 9 to 9, 9 to -10, -9 to -10)

    fun sourceTiles(center: CoordGrid): List<CoordGrid> =
        sourceOffsets.map { (dx, dz) -> CoordGrid(center.x + dx, center.z + dz, center.level) }

    fun nearestSource(center: CoordGrid, target: CoordGrid): CoordGrid =
        sourceTiles(center).minBy { it.chebyshevDistance(target) }
}

internal object KephriSwarms {
    const val NPC = 11723
    const val SPAWN_ANIMATION = 9605
    const val FIRST_SPAWN_DELAY = 5
    const val SPAWN_DELAY = 3
    const val MOVE_INTERVAL = 1
    const val ARRIVAL_SETTLE_TICKS = 3

    data class Wave(val total: Int, val healCap: Int)

    fun wave(phase: Int): Wave =
        when (phase) {
            1 -> Wave(total = 28, healCap = 18)
            2 -> Wave(total = 24, healCap = 14)
            else -> Wave(total = 0, healCap = 0)
        }

    fun spawnInterval(spawned: Int): Int =
        when {
            spawned < 4 -> 4
            spawned < 8 -> 3
            spawned < 12 -> 2
            else -> 1
        }

    fun nextStep(current: CoordGrid, destination: CoordGrid): CoordGrid =
        CoordGrid(
            current.x + (destination.x - current.x).coerceIn(-1, 1),
            current.z + (destination.z - current.z).coerceIn(-1, 1),
            current.level,
        )

    fun healAmount(maxHitpoints: Int, partySize: Int): Int =
        (maxHitpoints / (10 + (partySize.coerceAtLeast(1) - 1) * 2)).coerceAtLeast(1)

    fun shieldCap(maxHitpoints: Int): Int = maxHitpoints * 115 / 100
}

internal object KephriBombs {
    const val OVERHEAD_PROJECTILE = 1481
    const val PROJECTILE = 2266
    const val SHADOW = 1447
    const val IMPACT = 2156
    const val BASE_DAMAGE = 24
    const val TARGET_LOCK_DELAY = 3
    const val IMPACT_AFTER_LOCK = 4

    fun radius(aerialAssault: Boolean): Int = if (aerialAssault) 1 else 0

    fun tiles(center: CoordGrid, radius: Int): List<CoordGrid> =
        (-radius..radius).flatMap { dx ->
            (-radius..radius).map { dz -> CoordGrid(center.x + dx, center.z + dz, center.level) }
        }
}

internal object KephriArenaLayout {
    data class SwarmLane(val spawnX: Int, val spawnZ: Int, val approachX: Int, val approachZ: Int)

    val swarmLanes =
        listOf(
            SwarmLane(-6, -2, -1, 0),
            SwarmLane(-6, 4, -1, 4),
            SwarmLane(-2, 10, 0, 5),
            SwarmLane(4, 10, 4, 5),
            SwarmLane(10, 4, 5, 4),
            SwarmLane(10, -2, 5, 0),
            SwarmLane(4, -6, 4, -1),
            SwarmLane(-2, -6, 0, -1),
        )
}

internal object KephriOverlords {
    fun ids(phase: Int, moreOverlords: Boolean): List<Int> {
        val normal =
            when (phase) {
                1 -> listOf(KephriScarabRanger.NPC)
                2 -> listOf(KephriScarabMelee.NPC, KephriScarabMager.NPC)
                else -> emptyList()
            }
        if (!moreOverlords || normal.isEmpty()) return normal
        val missing =
            when (phase) {
                1 -> KephriScarabMelee.NPC
                else -> KephriScarabRanger.NPC
            }
        return normal + missing
    }
}

internal fun KephriOverlords.offset(id: Int): Pair<Int, Int> =
    when (id) {
        KephriScarabRanger.NPC -> 7 to 7
        KephriScarabMelee.NPC -> 7 to 1
        KephriScarabMager.NPC -> 7 to -5
        else -> 0 to 0
    }

internal data class KephriBombTargetRuntime(
    val target: PlayerUid,
    val locksAt: Int,
    val damage: Int,
    val radius: Int,
)

internal data class KephriBombRuntime(
    val tile: CoordGrid,
    val landsAt: Int,
    val damage: Int,
    val radius: Int,
    var hitQueued: Boolean = false,
)

internal data class KephriSwarmRuntime(
    val actorKey: Int,
    val healEligible: Boolean,
    val approachTile: CoordGrid,
    val startsMovingAt: Int,
    val medic: Boolean = false,
    val moveInterval: Int = KephriSwarms.MOVE_INTERVAL,
    var nextMoveAt: Int = startsMovingAt,
    var settlesAt: Int = -1,
)

internal object KephriEggs {
    const val EXPLOSIVE = 11728
    const val AGILE = 11729
    const val HATCHED_AGILE = 11727
    const val SPAWN_ANIMATION = 8630
    const val HATCH_ANIMATION = 2166
    const val TOTAL = 12
    const val SPAWN_DELAY = 4
    const val LIFETIME = 15
    const val LANDING_DAMAGE = 2
    const val EXPLOSION_DAMAGE = 8
    const val MASS_INCUBATION_AGILE_LIMIT = 6

    val offsets =
        listOf(
            2 to -2,
            0 to -4,
            -4 to 0,
            2 to 6,
            0 to 8,
            -4 to 4,
            -2 to 2,
            4 to -4,
            8 to 0,
            4 to 8,
            6 to 2,
            8 to 4,
        )

    fun darkEggCount(partySize: Int, livelyLarvae: Boolean, roll: Int): Int {
        val players = partySize.coerceIn(1, 8)
        return if (livelyLarvae) players * (3 + roll.coerceIn(0, 2)) else players * 2
    }
}

internal data class KephriEggBatchRuntime(
    val spawnsAt: Int,
    val tiles: List<CoordGrid>,
    val darkCount: Int,
)

internal data class KephriEggRuntime(
    val actorKey: Int,
    val tile: CoordGrid,
    val dark: Boolean,
    val resolvesAt: Int,
    var hitQueued: Boolean = false,
)

internal data class KephriSoldierRuntime(val actorKey: Int, var nextHealAt: Int)

internal data class KephriSpittingRuntime(val actorKey: Int, var nextAttackAt: Int)

internal data class KephriArcaneRuntime(
    val actorKey: Int,
    var locationIndex: Int = 0,
    var chargeStep: Int = 0,
    var nextChargeAt: Int,
    var target: PlayerUid? = null,
    var impactsAt: Int = -1,
)

internal object KephriScarabMelee {
    const val ATTACK_RANGE = 1
    const val NPC = 11724
    const val ANIMATION = 9587
    const val MAX_HIT = 17
    const val ATTACK_INTERVAL = 7
    const val HEAL_INTERVAL = 6
    const val HEAL_PROJECTILE = 2150

    fun healAmount(currentHitpoints: Int, maximumHitpoints: Int): Int =
        (MAX_HIT * currentHitpoints.coerceAtLeast(0) / maximumHitpoints.coerceAtLeast(1) / 6)
            .coerceAtLeast(1)
}

internal object KephriScarabRanger {
    const val ATTACK_RANGE = 8
    const val NPC = 11725
    const val ANIMATION = 9588
    const val PROJECTILE = 2152
    const val MAX_HIT = 10
    const val ATTACK_INTERVAL = 3
    const val POISON_DAMAGE = 6
}

internal object KephriScarabMager {
    const val ATTACK_RANGE = 10
    const val NPC = 11726
    const val SPAWN_ANIMATION = 9596
    const val DETONATE_ANIMATION = 9597
    const val CHARGE_GRAPHIC = 2149
    const val CHARGED_GRAPHIC = 2151
    const val CHARGE_INTERVAL = 3
    const val CHARGE_STEPS = 8
    const val IMPACT_DELAY = 3
    const val PRAYER_DAMAGE = 40
}

internal object KephriScarabAgile {
    const val NPC = 11727
    const val SPAWN_ANIMATION = 9596
    const val ATTACK_ANIMATION = 9594
    const val PROJECTILE = 2152
    const val ATTACK_RANGE = 6
    const val MAX_HIT = 5
    const val ATTACK_INTERVAL = 4
}
