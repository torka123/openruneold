package org.rsmod.content.systems.breach

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal enum class BreachState {
    IDLE,
    FIVE_MINUTE_WARNING,
    ONE_MINUTE_WARNING,
    OPENING,
    ACTIVE,
    COLLAPSING,
}

internal enum class BreachBossKind(val displayName: String) {
    CERBERUS("Cerberus"),
    CORRUPTED_HUNLLEF("Corrupted Hunllef"),
    WHISPERER("The Whisperer"),
    VARDORVIS("Vardorvis"),
}

internal data class BreachContribution(
    val accountId: Int,
    val playerUuid: Long,
    val username: String,
    var effectiveDamage: Long = 0,
    val firstHitTick: Int,
    var lastHitTick: Int,
)

internal data class BreachEvent(
    val id: UUID = UUID.randomUUID(),
    val scheduledAtMillis: Long,
    val boss: BreachBossKind,
    val locationIndex: Int,
    var state: BreachState = BreachState.FIVE_MINUTE_WARNING,
    var stateStartedAtMillis: Long = System.currentTimeMillis(),
    var spawnedAtMillis: Long = 0,
    var attackNumber: Int = 0,
    var nextAttackTick: Int = 0,
    var lastValidTargetTick: Int = 0,
    var rewardsDistributed: Boolean = false,
    var bossSpawned: Boolean = false,
    val paidAccounts: MutableSet<Int> = mutableSetOf(),
    val hitpointScaledAccounts: MutableSet<Int> = mutableSetOf(),
    val contributions: MutableMap<Int, BreachContribution> = linkedMapOf(),
)

internal data class RoatRewardDecision(
    val accountId: Int,
    val damage: Long,
    val pkPoints: Int,
    val mvp: Boolean,
    val mainReward: Boolean,
    val pet: Boolean,
)

/** First UTC :00 or :30 boundary strictly after [nowMillis]. */
internal fun nextHalfHourBoundary(nowMillis: Long): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC)
    val floor = now.truncatedTo(ChronoUnit.HOURS)
    val half = if (now.minute < 30) floor.plusMinutes(30) else floor.plusHours(1)
    return half.toInstant().toEpochMilli()
}

internal fun effectiveDamage(rawDamage: Int, remainingHitpoints: Int): Int =
    rawDamage.coerceAtLeast(0).coerceAtMost(remainingHitpoints.coerceAtLeast(0))

internal fun scaledBreachHitpoints(
    baseHitpoints: Int,
    hitpointsPerAttacker: Int,
    maximumHitpoints: Int,
    attackerCount: Int,
): Int {
    require(baseHitpoints > 0 && hitpointsPerAttacker > 0 && maximumHitpoints >= baseHitpoints)
    return (baseHitpoints.toLong() + hitpointsPerAttacker.toLong() * attackerCount.coerceAtLeast(0))
        .coerceAtMost(maximumHitpoints.toLong())
        .toInt()
}

internal fun targetCount(activePlayers: Int): Int =
    ((activePlayers * 4 + 9) / 10).coerceIn(2, 8).coerceAtMost(activePlayers.coerceAtLeast(0))

internal fun contributionBasisPoints(damage: Long, totalDamage: Long): Int =
    if (damage <= 0 || totalDamage <= 0) 0
    else ((damage.coerceAtMost(totalDamage) * 10_000L) / totalDamage).toInt()

internal fun pkPointsForDamage(damage: Long, pointsPerDamage: Int): Int =
    (damage.coerceAtLeast(0) * pointsPerDamage.toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

internal fun chooseMvp(contributions: Collection<BreachContribution>): BreachContribution? =
    contributions
        .asSequence()
        .filter { it.effectiveDamage > 0 }
        .sortedWith(
            compareByDescending<BreachContribution> { it.effectiveDamage }
                .thenBy { it.firstHitTick }
                .thenBy { it.accountId }
        )
        .firstOrNull()

/**
 * Roat-style reward decisions. The MVP gets a guaranteed premium roll. Every other contributor
 * independently passes their standard table when the roll in [0,totalDamage) is below their
 * effective damage. Every contributor also receives an independent pet roll.
 */
internal fun roatRewardDecisions(
    contributions: Collection<BreachContribution>,
    pointsPerDamage: Int,
    petDenominator: Int,
    contributionRoll: (Long) -> Long,
    petRoll: (Int) -> Int,
): List<RoatRewardDecision> {
    require(pointsPerDamage > 0)
    require(petDenominator > 0)
    val eligible = contributions.filter { it.effectiveDamage > 0 }
    val total = eligible.sumOf(BreachContribution::effectiveDamage)
    val mvp = chooseMvp(eligible) ?: return emptyList()
    return eligible.map { contribution ->
        val isMvp = contribution.accountId == mvp.accountId
        RoatRewardDecision(
            accountId = contribution.accountId,
            damage = contribution.effectiveDamage,
            pkPoints = pkPointsForDamage(contribution.effectiveDamage, pointsPerDamage),
            mvp = isMvp,
            mainReward = isMvp || contributionRoll(total) < contribution.effectiveDamage,
            pet = petRoll(petDenominator) == 0,
        )
    }
}
