package org.rsmod.content.minigames.toa

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal object ToaRules {
    fun mode(raidLevel: Int): ToaMode =
        when (raidLevel.coerceIn(0, 600)) {
            in 0..149 -> ToaMode.ENTRY
            in 150..299 -> ToaMode.NORMAL
            else -> ToaMode.EXPERT
        }

    fun partyHitpointsFactor(partySize: Int): Double {
        require(partySize in 1..TOA_MAX_PARTY_SIZE)
        return when (partySize) {
            1 -> 1.0
            2 -> 1.9
            3 -> 2.8
            4 -> 3.4
            5 -> 4.0
            6 -> 4.6
            7 -> 5.2
            8 -> 5.8
            else -> error("Unsupported ToA party size: $partySize")
        }
    }

    fun raidStatFactor(raidLevel: Int): Double = 1.0 + (raidLevel.coerceIn(0, 600) / 5) * 0.02

    fun damageFactor(raidLevel: Int): Double = min(2.5, raidStatFactor(raidLevel))

    fun pathFactor(pathLevel: Int): Double =
        when {
            pathLevel <= 0 -> 1.0
            else -> 1.08 + (pathLevel - 1) * 0.05
        }

    fun hitpoints(base: Int, partySize: Int, raidLevel: Int, pathLevel: Int): Int =
        floor(
                base *
                    partyHitpointsFactor(partySize) *
                    raidStatFactor(raidLevel) *
                    pathFactor(pathLevel)
            )
            .toInt()
            .coerceAtLeast(1)

    fun maxHit(base: Int, raidLevel: Int, pathLevel: Int): Int =
        floor(base * damageFactor(raidLevel) * pathFactor(pathLevel)).toInt().coerceAtLeast(1)

    /**
     * Scales the visible NPC levels used by OpenRune's attack and defence rolls. Unlike damage,
     * combat levels do not use the 150% damage cap.
     */
    fun combatStat(base: Int, raidLevel: Int, pathLevel: Int): Int {
        if (base <= 0) return base
        return floor(base * raidStatFactor(raidLevel) * pathFactor(pathLevel))
            .toInt()
            .coerceAtLeast(1)
    }

    fun attemptLimit(invocations: Set<ToaInvocation>): Int? =
        when {
            ToaInvocation.HARDCORE_RUN in invocations -> 1
            ToaInvocation.SOFTCORE_RUN in invocations -> 3
            ToaInvocation.PERSISTENCE in invocations -> 5
            ToaInvocation.TRY_AGAIN in invocations -> 10
            else -> null
        }

    fun timeLimitTicks(invocations: Set<ToaInvocation>): Int? =
        when {
            ToaInvocation.SPRINT_FOR_IT in invocations -> 25 * 100
            ToaInvocation.RUN_FOR_IT in invocations -> 30 * 100
            ToaInvocation.JOG_FOR_IT in invocations -> 35 * 100
            ToaInvocation.WALK_FOR_IT in invocations -> 40 * 100
            else -> null
        }

    fun timePenaltyLevels(invocations: Set<ToaInvocation>): Int =
        when {
            ToaInvocation.SPRINT_FOR_IT in invocations -> 50
            ToaInvocation.RUN_FOR_IT in invocations -> 40
            ToaInvocation.JOG_FOR_IT in invocations -> 30
            ToaInvocation.WALK_FOR_IT in invocations -> 20
            else -> 0
        }

    fun timerHasExpired(
        startedAt: Int?,
        cycle: Int,
        limitTicks: Int?,
        alreadyExpired: Boolean,
    ): Boolean =
        !alreadyExpired &&
            startedAt != null &&
            limitTicks != null &&
            cycle - startedAt >= limitTicks

    fun supplyMultiplier(invocations: Set<ToaInvocation>): Double =
        when {
            ToaInvocation.NO_HELP_NEEDED in invocations -> 0.10
            ToaInvocation.NEED_LESS_HELP in invocations -> 0.33
            ToaInvocation.NEED_SOME_HELP in invocations -> 0.66
            else -> 1.0
        }

    fun prayerReductionPercent(basePercent: Int, invocations: Set<ToaInvocation>): Int {
        require(basePercent in 0..100)
        return if (ToaInvocation.QUIET_PRAYERS in invocations) {
            (basePercent - QUIET_PRAYERS_PERCENTAGE_POINTS).coerceAtLeast(0)
        } else {
            basePercent
        }
    }

    fun deadlyPrayerDrain(
        landedDamage: Int,
        matchingProtectionPrayer: Boolean,
        invocations: Set<ToaInvocation>,
        selfInflicted: Boolean = false,
    ): Int =
        if (
            landedDamage > 0 &&
                matchingProtectionPrayer &&
                !selfInflicted &&
                ToaInvocation.DEADLY_PRAYERS in invocations
        ) {
            landedDamage / DEADLY_PRAYERS_DAMAGE_DIVISOR
        } else {
            0
        }

    fun deathPoints(points: Int): Int {
        val current = points.coerceAtLeast(0)
        val deduction = max(current / 5, 1_000)
        return (current - deduction).coerceAtLeast(0)
    }

    /** The raid grants 5,000 starting points, but OSRS subtracts them when rolling loot. */
    fun rewardPoints(points: Int): Int = (points - 5_000).coerceAtLeast(0)

    /** Points which may take part in normal/common and unique reward rolls. */
    fun lootEligiblePoints(points: Int): Int =
        rewardPoints(points).takeIf { it >= MIN_LOOT_POINTS } ?: 0

    fun scaledRewardLevel(raidLevel: Int): Double {
        val level = raidLevel.coerceIn(0, 600)
        return when {
            level <= 310 -> level.toDouble()
            level <= 430 -> 310.0 + (level - 310) / 3.0
            else -> 350.0 + (level - 430) / 6.0
        }
    }

    fun uniqueChance(points: Int, raidLevel: Int): Double {
        val denominator = 10_500.0 - 20.0 * scaledRewardLevel(raidLevel)
        return (points.coerceAtLeast(0) / denominator / 100.0).coerceIn(0.0, 0.55)
    }

    fun ownerTicket(participants: List<ToaParticipant>, ticket: Int): ToaParticipant? {
        val weighted =
            participants.map { it to lootEligiblePoints(it.points) }.filter { it.second > 0 }
        val total = weighted.sumOf { it.second }
        if (ticket !in 0 until total) return null
        var cursor = 0
        return weighted
            .first {
                cursor += it.second
                ticket < cursor
            }
            .first
    }

    fun initialPathLevels(invocations: Set<ToaInvocation>): MutableMap<ToaPath, Int> {
        val base =
            when {
                ToaInvocation.PATHMASTER in invocations -> 3
                ToaInvocation.PATHFINDER in invocations -> 2
                ToaInvocation.PATHSEEKER in invocations -> 1
                else -> 0
            }
        return ToaPath.entries.associateWith { base }.toMutableMap()
    }

    fun levelRemainingPaths(
        levels: MutableMap<ToaPath, Int>,
        completed: Set<ToaPath>,
        random: Random,
    ): List<ToaPath> {
        val raiseCount =
            when (completed.size) {
                1 -> 2
                2,
                3 -> 1
                else -> 0
            }
        val remaining =
            levels.keys
                .filterNot { it in completed }
                .filter { levels.getValue(it) < MAX_PATH_LEVEL }
                .shuffled(random)
        val raised = remaining.take(raiseCount)
        raised.forEach { levels[it] = (levels.getValue(it) + 1).coerceAtMost(MAX_PATH_LEVEL) }
        return raised
    }

    private const val QUIET_PRAYERS_PERCENTAGE_POINTS = 10
    private const val DEADLY_PRAYERS_DAMAGE_DIVISOR = 5
    private const val MAX_PATH_LEVEL = 6
    const val MIN_LOOT_POINTS = 1_500
}

internal object ToaInvocationEffects {
    /**
     * Boss invocations do not globally change auto-attack speed. Stay Vigilant changes Akkha's
     * autos-per-special threshold; Boulderdash changes only boulder release cadence; Arterial Spray
     * changes blood-magic range/healing; and Aerial Assault changes bomb area/damage.
     */
    fun attackRate(base: Int, @Suppress("UNUSED_PARAMETER") raid: ToaRaidSession): Int =
        base.coerceAtLeast(2)

    fun mechanicInterval(base: Int, @Suppress("UNUSED_PARAMETER") raid: ToaRaidSession): Int =
        base.coerceAtLeast(3)
}
