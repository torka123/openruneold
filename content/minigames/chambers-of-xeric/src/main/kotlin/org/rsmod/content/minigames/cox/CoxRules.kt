package org.rsmod.content.minigames.cox

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.rsmod.map.CoordGrid

internal object CoxScaling {
    /**
     * CoX health scales linearly from the raid's player count. Challenge Mode then applies its
     * documented 50% combat-room modifier. Keeping this named and pure prevents room scripts from
     * drifting into unrelated private-server multipliers.
     */
    fun hitpoints(base: Int, partySize: Int, difficulty: RaidDifficulty): Int {
        require(base > 0 && partySize > 0)
        val teamScaled = base * (100 + 50 * (partySize - 1)) / 100
        return if (difficulty == RaidDifficulty.CHALLENGE) teamScaled * 150 / 100 else teamScaled
    }

    fun combatStat(base: Int, partySize: Int, difficulty: RaidDifficulty): Int {
        require(base > 0 && partySize > 0)
        val teamScaled = base + (partySize - 1) * max(1, base / 10)
        return if (difficulty == RaidDifficulty.CHALLENGE) teamScaled * 150 / 100 else teamScaled
    }
}

internal object MuttadileScaling {
    const val BASE_HITPOINTS = 250
    private const val EXTRA_PLAYER_STAT_PERCENT = 8
    private const val EXTRA_PLAYER_DEFENCE = 2

    fun hitpoints(partySize: Int, difficulty: RaidDifficulty): Int {
        require(partySize > 0)
        val teamScaled = BASE_HITPOINTS * partySize
        return challengeScale(teamScaled, difficulty)
    }

    fun combatStat(base: Int, partySize: Int, difficulty: RaidDifficulty): Int {
        require(base > 0 && partySize > 0)
        val teamScaled = base * (100 + EXTRA_PLAYER_STAT_PERCENT * (partySize - 1)) / 100
        return challengeScale(teamScaled, difficulty)
    }

    fun defenceStat(base: Int, partySize: Int, difficulty: RaidDifficulty): Int {
        require(base > 0 && partySize > 0)
        val teamScaled = base + EXTRA_PLAYER_DEFENCE * (partySize - 1)
        return challengeScale(teamScaled, difficulty)
    }

    fun combatDamageCap(partySize: Int, difficulty: RaidDifficulty): Int =
        hitpoints(partySize, difficulty) * 2

    fun pointableDamage(alreadyCredited: Int, incomingDamage: Int, cap: Int): Int {
        require(alreadyCredited >= 0 && cap >= 0)
        return incomingDamage.coerceAtLeast(0).coerceAtMost((cap - alreadyCredited).coerceAtLeast(0))
    }

    private fun challengeScale(value: Int, difficulty: RaidDifficulty): Int =
        if (difficulty == RaidDifficulty.CHALLENGE) value * 150 / 100 else value
}

/** Current OSRS Dimitri/meat-tree and Muttadile eating rules. */
internal object MuttadileMeatTreeRules {
    const val NPC_ID = CoxNpcs.MUTTADILE_MEAT_TREE
    const val LIVE_LOC_ID = CoxLocs.MUTTADILE_MEAT_TREE
    const val DEAD_LOC_ID = CoxLocs.MUTTADILE_MEAT_TREE_DEAD
    const val LOCAL_X = 5
    const val LOCAL_Z = 8
    const val AXE_LOCAL_X = 7
    const val AXE_LOCAL_Z = 8
    const val BRONZE_AXE = "obj.bronze_axe"
    const val CHOP_INTERVAL_TICKS = 5
    const val WOODCUTTING_XP = 5.0
    const val EAT_SEQUENCE = 7420
    const val EAT_ANIMATION_TICKS = 3
    const val GIVE_UP_TICKS = 25
    const val HEAL_PERCENT = 40
    const val EAT_THRESHOLD_PERCENT = 50
    const val MIN_HEALTH = 100
    const val MAX_HEALTH = 500
    const val MIN_ACCURACY_PERCENT = 20
    const val MAX_ACCURACY_PERCENT = 78

    fun health(woodcuttingLevel: Int): Int =
        ((woodcuttingLevel.coerceIn(1, 99) + 1) * 5).coerceIn(MIN_HEALTH, MAX_HEALTH)

    fun accuracyPercent(woodcuttingLevel: Int): Int {
        val level = woodcuttingLevel.coerceIn(1, 99)
        return MIN_ACCURACY_PERCENT +
            (level - 1) * (MAX_ACCURACY_PERCENT - MIN_ACCURACY_PERCENT) / 98
    }

    /** Jagex formula: floor(level / 2) - 2, plus or minus five. */
    fun damageRange(woodcuttingLevel: Int): IntRange {
        val centre = woodcuttingLevel.coerceIn(1, 99) / 2 - 2
        return (centre - 5).coerceAtLeast(1)..(centre + 5).coerceAtLeast(1)
    }

    fun shouldEat(hitpoints: Int, maximum: Int): Boolean =
        maximum > 0 && hitpoints > 0 && hitpoints * 100 <= maximum * EAT_THRESHOLD_PERCENT

    fun healAmount(maximum: Int): Int = maximum.coerceAtLeast(0) * HEAL_PERCENT / 100

    fun feedingTile(muttadile: CoordGrid, size: Int, tree: CoordGrid): CoordGrid {
        require(size > 0 && muttadile.level == tree.level)
        val alignedX = muttadile.x.coerceIn(tree.x - size + 1, tree.x + 1)
        val alignedZ = muttadile.z.coerceIn(tree.z - size + 1, tree.z + 1)
        return listOf(
                CoordGrid(tree.x + 2, alignedZ, tree.level),
                CoordGrid(tree.x - size, alignedZ, tree.level),
                CoordGrid(alignedX, tree.z + 2, tree.level),
                CoordGrid(alignedX, tree.z - size, tree.level),
            )
            .minBy { it.chebyshevDistance(muttadile) }
    }
}

internal object CoxRewardBeamRules {
    const val PRIMARY_SHAPE = 3
    const val TRIGGER_SHAPE = 10
    const val ANGLE = 0
    const val TRIGGER_OFFSET_Z = 1
    const val TRIGGER_LIFETIME_TICKS = 1

    fun triggerTile(chest: CoordGrid): CoordGrid = chest.translate(0, TRIGGER_OFFSET_Z)
}

internal data class CoxDeathDeduction(val personalLost: Int, val teamLost: Int)

internal object CoxPoints {
    const val PERSONAL_DEATH_PERCENT = 40
    const val LOW_CONTRIBUTION_TEAM_PERCENT = 5
    const val FULL_RAID_POINTS_PER_DAMAGE = 8
    const val OLM_ONLY_POINTS_PER_DAMAGE = 10

    /**
     * Olm's third hand set is deliberately non-pointable because either hand can regenerate while
     * its partner lives. The remaining fixed-route damage targets 55-60k for a full solo and
     * 30-33k for Olm-only. Party HP scaling naturally increases available points at larger scales.
     */
    fun forCombatDamage(damage: Int, olmOnly: Boolean): Int {
        val multiplier =
            if (olmOnly) OLM_ONLY_POINTS_PER_DAMAGE else FULL_RAID_POINTS_PER_DAMAGE
        return (damage.coerceAtLeast(0).toLong() * multiplier)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun award(session: CoxRaidSession, participant: RaidParticipant, amount: Int) {
        if (amount <= 0 || session.state in setOf(RaidState.ABANDONED, RaidState.COMPLETE)) return
        participant.personalPoints += amount
        session.teamPoints += amount
    }

    /**
     * OSRS CoX death policy: lose 40% personal points; low contributors also cost 5% team points.
     */
    fun deductDeath(session: CoxRaidSession, participant: RaidParticipant): CoxDeathDeduction {
        val personalBefore = participant.personalPoints
        val teamBefore = session.teamPoints
        val personalLost = personalBefore * PERSONAL_DEATH_PERCENT / 100
        val lowContributor =
            personalBefore * 100 < max(1, teamBefore) * LOW_CONTRIBUTION_TEAM_PERCENT
        val teamPenalty =
            if (lowContributor) teamBefore * LOW_CONTRIBUTION_TEAM_PERCENT / 100 else 0
        participant.personalPoints = (personalBefore - personalLost).coerceAtLeast(0)
        session.teamPoints = (teamBefore - personalLost - teamPenalty).coerceAtLeast(0)
        participant.deaths++
        return CoxDeathDeduction(personalLost, personalLost + teamPenalty)
    }
}

internal object CoxRewardRules {
    const val POINTS_PER_ONE_PERCENT = 8_676
    const val MAX_UNIQUE_CHANCE = 0.657
    const val COMMON_POINT_CAP = 131_071
    const val UNIQUE_ROLL_POINT_CAP = 570_000
    const val MAX_UNIQUE_ROLLS = 3
    const val DONOR_FULL_PURPLE_CAP = 0.45
    const val DONOR_OLM_PURPLE_CAP = 0.20

    private val donorFullUniques =
        listOf(
            WeightedUnique(21_034, 10),
            WeightedUnique(21_079, 10),
            WeightedUnique(24_670, 10),
            WeightedUnique(21_000, 7),
            WeightedUnique(21_012, 7),
            WeightedUnique(21_015, 5),
            WeightedUnique(21_018, 5),
            WeightedUnique(21_021, 5),
            WeightedUnique(21_024, 5),
            WeightedUnique(13_652, 5),
            WeightedUnique(21_003, 2),
            WeightedUnique(21_043, 2),
            WeightedUnique(20_997, 1),
        )

    private val donorOlmOnlyUniques =
        donorFullUniques.map { entry ->
            if (entry.objId == 20_997) entry else entry.copy(weight = entry.weight * 2)
        }

    val uniqueTable: List<WeightedUnique>
        get() = donorFullUniques

    fun donorUniqueTable(olmOnly: Boolean): List<WeightedUnique> =
        if (olmOnly) donorOlmOnlyUniques else donorFullUniques

    /** Donor formula converts personal points to a percent, capped at 45%/20%. */
    fun donorPurpleChance(personalPoints: Int, olmOnly: Boolean): Double {
        val divisor = if (olmOnly) 380_000.0 else 180_000.0
        val cap = if (olmOnly) DONOR_OLM_PURPLE_CAP else DONOR_FULL_PURPLE_CAP
        return min(cap, personalPoints.coerceAtLeast(0) / divisor)
    }

    /** Donor intended a 131,071-point cap; use min rather than its accidental Math.max typo. */
    fun donorCommonAmount(personalPoints: Int, pointsPerItem: Int): Int {
        require(pointsPerItem > 0)
        val points = personalPoints.coerceIn(1, COMMON_POINT_CAP)
        return max(1, ceil(points.toDouble() / pointsPerItem).toInt())
    }

    fun uniqueAtTicket(table: List<WeightedUnique>, ticket: Int): WeightedUnique {
        require(ticket in 0 until table.sumOf(WeightedUnique::weight))
        var cursor = 0
        return table.first { entry ->
            cursor += entry.weight
            ticket < cursor
        }
    }

    fun uniqueAtTicket(ticket: Int): WeightedUnique = uniqueAtTicket(uniqueTable, ticket)

    fun uniqueChance(teamPoints: Int, multiplier: Double = 1.0): Double {
        require(multiplier > 0.0)
        if (teamPoints >= UNIQUE_ROLL_POINT_CAP) return MAX_UNIQUE_CHANCE
        val raw = teamPoints.coerceAtLeast(0).toDouble() / (POINTS_PER_ONE_PERCENT * 100.0)
        return min(MAX_UNIQUE_CHANCE, raw * multiplier)
    }

    fun uniqueRollChances(teamPoints: Int, multiplier: Double = 1.0): List<Double> {
        require(multiplier > 0.0)
        var remaining = teamPoints.coerceAtLeast(0)
        return buildList {
            while (remaining > 0 && size < MAX_UNIQUE_ROLLS) {
                val points = min(UNIQUE_ROLL_POINT_CAP, remaining)
                add(uniqueChance(points, multiplier))
                remaining -= points
            }
        }
    }

    fun uniqueOwnerTicket(participants: List<RaidParticipant>, ticket: Int): RaidParticipant? {
        val eligible = participants.filter { it.personalPoints > 0 }
        val total = eligible.sumOf(RaidParticipant::personalPoints)
        if (total == 0 || ticket !in 0 until total) return null
        var cursor = 0
        return eligible.first { participant ->
            cursor += participant.personalPoints
            ticket < cursor
        }
    }

    fun commonQuantity(baseMinimum: Int, baseMaximum: Int, personalPoints: Int, roll: Double): Int {
        require(baseMinimum > 0 && baseMaximum >= baseMinimum && roll in 0.0..1.0)
        val points = personalPoints.coerceIn(0, COMMON_POINT_CAP)
        val scale = 0.5 + points.toDouble() / COMMON_POINT_CAP
        val minScaled = max(1, floor(baseMinimum * scale).toInt())
        val maxScaled = max(minScaled, floor(baseMaximum * scale).toInt())
        return minScaled + floor((maxScaled - minScaled + 1) * roll.coerceAtMost(0.999999)).toInt()
    }
}

internal data class WeightedUnique(val objId: Int, val weight: Int)

internal object CoxChallengeRules {
    fun targetTicks(partySize: Int): Int {
        require(partySize > 0)
        val minutes =
            when (partySize) {
                1 -> 70
                2 -> 65
                3 -> 50
                4 -> 45
                in 5..10 -> 42
                in 11..15 -> 45
                in 16..23 -> 60
                else -> 80
            }
        return minutes * 100
    }

    fun eligibleForTimedRewards(session: CoxRaidSession): Boolean =
        session.difficulty == RaidDifficulty.CHALLENGE &&
            (session.completedAt ?: Int.MAX_VALUE) - session.startedAt <=
                targetTicks(session.participants.size.coerceAtLeast(1))
}

internal enum class GreatOlmPhase {
    DORMANT,
    PHASE_ONE,
    PHASE_TWO,
    PHASE_THREE,
    FINAL_HEAD,
    DEFEATED,
}

internal enum class GreatOlmTarget {
    LEFT_HAND,
    RIGHT_HAND,
    HEAD,
}

internal data class GreatOlmVisualDefinition(
    val riseLoc: Int,
    val idleLoc: Int,
    val dormantLoc: Int,
    val riseSequence: Int,
    val retreatSequence: Int,
)

/**
 * Great Olm's NPCs are invisible combat anchors. The visible head and hands are map objects which
 * must be changed from their dormant form, animated, and then left in their idle form.
 */
internal object GreatOlmVisuals {
    private val definitions =
        mapOf(
            GreatOlmTarget.HEAD to
                GreatOlmVisualDefinition(
                    CoxLocs.OLM_HEAD_RISE,
                    CoxLocs.OLM_HEAD_IDLE,
                    CoxLocs.OLM_HEAD_DORMANT,
                    7335,
                    7348,
                ),
            GreatOlmTarget.LEFT_HAND to
                GreatOlmVisualDefinition(
                    CoxLocs.OLM_LEFT_HAND_RISE,
                    CoxLocs.OLM_LEFT_HAND_IDLE,
                    CoxLocs.OLM_LEFT_HAND_DORMANT,
                    7354,
                    7370,
                ),
            GreatOlmTarget.RIGHT_HAND to
                GreatOlmVisualDefinition(
                    CoxLocs.OLM_RIGHT_HAND_RISE,
                    CoxLocs.OLM_RIGHT_HAND_IDLE,
                    CoxLocs.OLM_RIGHT_HAND_DORMANT,
                    7350,
                    7352,
                ),
        )

    val locIds: Set<Int> =
        definitions.values.flatMapTo(mutableSetOf()) {
            listOf(it.riseLoc, it.idleLoc, it.dormantLoc)
        }

    val sequenceIds: Set<Int> =
        definitions.values.flatMapTo(mutableSetOf()) {
            listOf(it.riseSequence, it.retreatSequence)
        } +
            setOf(
                HEAD_ATTACK_SEQUENCE,
                HEAD_ENRAGED_ATTACK_SEQUENCE,
                LEFT_HAND_CRYSTAL_SEQUENCE,
                LEFT_HAND_LIGHTNING_SEQUENCE,
                LEFT_HAND_PORTAL_SEQUENCE,
            )

    operator fun get(target: GreatOlmTarget): GreatOlmVisualDefinition =
        definitions.getValue(target)

    fun headAttackSequence(enraged: Boolean): Int =
        if (enraged) HEAD_ENRAGED_ATTACK_SEQUENCE else HEAD_ATTACK_SEQUENCE

    fun leftHandSpecialSequence(special: GreatOlmSpecial): Int? =
        when (special) {
            GreatOlmSpecial.CRYSTALS -> LEFT_HAND_CRYSTAL_SEQUENCE
            GreatOlmSpecial.LIGHTNING -> LEFT_HAND_LIGHTNING_SEQUENCE
            GreatOlmSpecial.PORTALS -> LEFT_HAND_PORTAL_SEQUENCE
            else -> null
        }

    private const val HEAD_ATTACK_SEQUENCE = 7345
    private const val HEAD_ENRAGED_ATTACK_SEQUENCE = 7371
    private const val LEFT_HAND_CRYSTAL_SEQUENCE = 7356
    private const val LEFT_HAND_LIGHTNING_SEQUENCE = 7358
    private const val LEFT_HAND_PORTAL_SEQUENCE = 7359
}

internal enum class GreatOlmBasicAttackStyle {
    MAGIC,
    RANGED,
}

internal class GreatOlmBasicAttackCycle(
    initial: GreatOlmBasicAttackStyle = GreatOlmBasicAttackStyle.MAGIC
) {
    var current: GreatOlmBasicAttackStyle = initial
        private set

    fun advance(switchStyle: Boolean) {
        if (!switchStyle) return
        current =
            if (current == GreatOlmBasicAttackStyle.MAGIC) GreatOlmBasicAttackStyle.RANGED
            else GreatOlmBasicAttackStyle.MAGIC
    }
}

internal enum class GreatOlmAttackSlot {
    BASIC,
    SPECIAL,
    EMPTY,
}

/** OSRS Olm repeats basic, basic, special, basic, basic, empty on one four-tick clock. */
internal class GreatOlmAttackCycle {
    private var index = 0

    val current: GreatOlmAttackSlot
        get() = ROTATION[index]

    fun advance() {
        index = (index + 1) % ROTATION.size
    }

    private companion object {
        val ROTATION =
            listOf(
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.SPECIAL,
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.EMPTY,
            )
    }
}

/** Donor-backed warning, eruption, and ceiling-crystal timing for Great Olm. */
internal object GreatOlmCrystalRules {
    const val BURST_WARNING_TICKS = 3
    const val BURST_ERUPTION_TICKS = 1
    const val FALLING_INTERVAL_TICKS = 2
    const val FALLING_TARGETED_INTERVAL_TICKS = 4
    const val FALLING_PROJECTILE = 1357
    const val FALLING_SHADOW = 1447
    const val FALLING_IMPACT = 1358
    const val FALLING_PROJECTILE_START_HEIGHT = 150
    const val FALLING_PROJECTILE_END_TIME = 135
    const val FALLING_SHADOW_DELAY = 30
    const val ARENA_MIN_LOCAL_X = 28
    const val ARENA_MAX_LOCAL_X = 37
    const val ARENA_MIN_LOCAL_Z = 35
    const val ARENA_MAX_LOCAL_Z = 51

    val spotanimIds = setOf(FALLING_PROJECTILE, FALLING_SHADOW, FALLING_IMPACT)

    fun burstHits(markedTile: org.rsmod.map.CoordGrid, currentTile: org.rsmod.map.CoordGrid): Boolean =
        markedTile == currentTile

    fun fallingDamageRange(distance: Int): IntRange? =
        when (distance) {
            0 -> 16..25
            1 -> 10..16
            else -> null
        }

    fun isTargetedFallingWave(waveIndex: Int): Boolean {
        val wavesPerTarget = FALLING_TARGETED_INTERVAL_TICKS / FALLING_INTERVAL_TICKS
        return waveIndex % wavesPerTarget == 0
    }

    fun fallingSource(
        target: org.rsmod.map.CoordGrid,
        eastOffset: Boolean,
    ): org.rsmod.map.CoordGrid =
        if (eastOffset) target.translate(1, 0) else target.translate(0, 1)
}
/** Donor-accurate loc synchronization and rise duration for Olm's head and hands. */
internal object GreatOlmVisualTiming {
    private const val LOC_SYNC_TICKS = 1
    private const val RISE_ANIMATION_TICKS = 5

    fun riseAnimationCycle(startCycle: Int): Int = startCycle + LOC_SYNC_TICKS

    fun riseIdleCycle(startCycle: Int): Int =
        riseAnimationCycle(startCycle) + RISE_ANIMATION_TICKS
}

internal fun olmBarrierDestination(start: org.rsmod.map.CoordGrid): org.rsmod.map.CoordGrid =
    start.translate(0, 2)

/**
 * The donor raid changes the submerged large Muttadile into its land form, then has it travel six
 * tiles west over seven game ticks before combat can begin. Keeping the movement values together
 * prevents the client-side emergence from being replaced with an instant server teleport.
 */
internal object MuttadileEmergence {
    const val OFFSET_X = -6
    const val OFFSET_Z = 0
    const val DURATION_TICKS = 7
    const val SEQUENCE = 7423

    fun destination(start: org.rsmod.map.CoordGrid): org.rsmod.map.CoordGrid =
        start.translate(OFFSET_X, OFFSET_Z)

    fun nextStep(
        current: org.rsmod.map.CoordGrid,
        destination: org.rsmod.map.CoordGrid,
    ): org.rsmod.map.CoordGrid =
        current.translate(
            (destination.x - current.x).coerceIn(-1, 1),
            (destination.z - current.z).coerceIn(-1, 1),
        )
}

internal data class VanguardCombatDefinition(
    val weakness: CoxCombatStyle,
    val attackSequence: Int,
    val defendSequence: Int,
    val deathSequence: Int,
)

internal object VanguardCombatRules {
    private val definitions =
        mapOf(
            CoxNpcs.VANGUARD_MELEE to
                VanguardCombatDefinition(
                    CoxCombatStyle.MAGIC,
                    attackSequence = 7441,
                    defendSequence = 7440,
                    deathSequence = 7442,
                ),
            CoxNpcs.VANGUARD_RANGED to
                VanguardCombatDefinition(
                    CoxCombatStyle.MELEE,
                    attackSequence = 7446,
                    defendSequence = 7445,
                    deathSequence = 7447,
                ),
            CoxNpcs.VANGUARD_MAGIC to
                VanguardCombatDefinition(
                    CoxCombatStyle.RANGED,
                    attackSequence = 7436,
                    defendSequence = 7435,
                    deathSequence = 7437,
                ),
        )

    val sequenceIds: Set<Int> =
        definitions.values.flatMapTo(mutableSetOf()) {
            listOf(it.attackSequence, it.defendSequence, it.deathSequence)
        }

    operator fun get(npcId: Int): VanguardCombatDefinition? = definitions[npcId]

    fun getValue(npcId: Int): VanguardCombatDefinition = definitions.getValue(npcId)
}

internal object SkeletalMysticAnimations {
    const val ATTACK_SEQUENCE = 5523
    const val DEFEND_SEQUENCE = 5489
    const val DEATH_SEQUENCE = 5527

    val sequenceIds: Set<Int> = setOf(ATTACK_SEQUENCE, DEFEND_SEQUENCE, DEATH_SEQUENCE)

    fun appliesTo(npcId: Int): Boolean =
        npcId == CoxNpcs.SKELETAL_MYSTIC ||
            npcId == CoxNpcs.SKELETAL_MYSTIC_ALT ||
            npcId == CoxNpcs.SKELETAL_MYSTIC_ALT_2

    fun defendSequence(npcId: Int): Int? = if (appliesTo(npcId)) DEFEND_SEQUENCE else null

    fun deathSequence(npcId: Int): Int? = if (appliesTo(npcId)) DEATH_SEQUENCE else null
}

internal object GuardianRoomSupplies {
    const val IRON_PICKAXE = "obj.iron_pickaxe"
    const val LOCAL_X = 23
    const val LOCAL_Z = 45
}

internal object DeathlyNpcCombatRules {
    const val MAX_SPAWNS_PER_STYLE = 5
    const val PROTECTION_PRAYER_REDUCTION_PERCENT = 50
    const val PUZZLE_DOOR_POINTS = 1_200

    val RANGER_SPAWNS = listOf(6 to 52, 5 to 52, 6 to 53, 5 to 53, 6 to 54)
    val MAGE_SPAWNS = listOf(12 to 53, 11 to 53, 12 to 54, 11 to 54, 12 to 55)

    init {
        require(RANGER_SPAWNS.size == MAX_SPAWNS_PER_STYLE)
        require(MAGE_SPAWNS.size == MAX_SPAWNS_PER_STYLE)
    }

    /** Solo and duo raids use two of each style; larger parties use one per player. */
    fun spawnsPerStyle(partySize: Int): Int = partySize.coerceIn(2, MAX_SPAWNS_PER_STYLE)

    /** Party-scaled unprotected max hit. Matching protection prayer reduces this by half. */
    fun maxHit(partySize: Int): Int =
        when (partySize.coerceAtLeast(1)) {
            1, 2 -> 28
            3 -> 32
            4 -> 34
            else -> 36
        }

    fun appliesTo(npcId: Int): Boolean =
        npcId == CoxNpcs.DEATHLY_RANGER || npcId == CoxNpcs.DEATHLY_MAGE
}

internal object DeathlyNpcAnimations {
    const val DEFEND_SEQUENCE = 424
    const val DEATH_SEQUENCE = 836

    fun appliesTo(npcId: Int): Boolean =
        DeathlyNpcCombatRules.appliesTo(npcId)

    fun defendSequence(npcId: Int): Int? =
        if (appliesTo(npcId)) DEFEND_SEQUENCE else null

    fun deathSequence(npcId: Int): Int? =
        if (appliesTo(npcId)) DEATH_SEQUENCE else null
}

internal enum class MuttadileAttackStyle {
    MELEE,
    RANGED,
    MAGIC,
    STOMP,
}

/**
 * The two Muttadiles do not share one generic melee profile. The submerged adult casts magic while
 * the juvenile is alive, the juvenile mixes melee and ranged, and the emerged adult mixes every
 * style while keeping ranged/magic streaks for at least three attacks.
 */
internal object MuttadileCombatRules {
    const val ATTACK_RATE = 4
    const val SMALL_MELEE_MAX_HIT = 45
    const val SMALL_RANGED_MAX_HIT = 44
    const val SUBMERGED_MAGIC_MAX_HIT = 42
    const val LARGE_MELEE_MAX_HIT = 66
    const val LARGE_RANGED_MAX_HIT = 42
    const val LARGE_MAGIC_MAX_HIT = 42
    const val LARGE_STOMP_MAX_HIT = 101
    const val MIN_DISTANCE_STYLE_STREAK = 3

    fun smallAttack(inMeleeRange: Boolean, roll: Int): MuttadileAttackStyle =
        if (inMeleeRange && Math.floorMod(roll, 3) != 0) {
            MuttadileAttackStyle.MELEE
        } else {
            MuttadileAttackStyle.RANGED
        }

    fun largeCloseAttack(roll: Int): MuttadileAttackStyle =
        if (Math.floorMod(roll, 4) == 0) {
            MuttadileAttackStyle.MELEE
        } else {
            MuttadileAttackStyle.STOMP
        }

    fun distanceAttack(roll: Int): MuttadileAttackStyle =
        if (Math.floorMod(roll, 2) == 0) {
            MuttadileAttackStyle.MAGIC
        } else {
            MuttadileAttackStyle.RANGED
        }
}

internal data class MuttadileDistanceAttackCycle(
    var style: MuttadileAttackStyle = MuttadileAttackStyle.MAGIC,
    var remaining: Int = 0,
) {
    fun next(roll: Int): MuttadileAttackStyle {
        if (remaining <= 0) {
            style = MuttadileCombatRules.distanceAttack(roll)
            remaining = MuttadileCombatRules.MIN_DISTANCE_STYLE_STREAK
        }
        remaining--
        return style
    }
}

internal fun coxEntranceAdjacentCandidates(
    entrance: org.rsmod.map.CoordGrid,
    reference: org.rsmod.map.CoordGrid,
    maxRadius: Int = 4,
): List<org.rsmod.map.CoordGrid> {
    require(maxRadius > 0)
    return (1..maxRadius).flatMap { radius ->
        (-radius..radius)
            .flatMap { dx ->
                (-radius..radius).mapNotNull { dz ->
                    val perimeter = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == radius
                    if (perimeter) entrance.translate(dx, dz) else null
                }
            }
            .sortedBy { kotlin.math.abs(it.x - reference.x) + kotlin.math.abs(it.z - reference.z) }
    }
}

internal fun canQueueOlmSpecial(
    sourceAssigned: Boolean,
    sourceHitpoints: Int,
    arenaPlayerCount: Int,
): Boolean = sourceAssigned && sourceHitpoints > 0 && arenaPlayerCount > 0

internal enum class GreatOlmSpecial {
    CRYSTALS,
    LIGHTNING,
    ACID,
    BURN,
    PORTALS,
    FALLING_CRYSTALS,
}

internal class GreatOlmState(val partySize: Int, difficulty: RaidDifficulty) {
    var phase: GreatOlmPhase = GreatOlmPhase.DORMANT
        private set

    var leftHandAlive: Boolean = true
        private set

    var rightHandAlive: Boolean = true
        private set

    var headAlive: Boolean = true
        private set

    var cycleIndex: Int = 0
        private set

    val phaseNumber: Int
        get() =
            when (phase) {
                GreatOlmPhase.PHASE_ONE -> 1
                GreatOlmPhase.PHASE_TWO -> 2
                GreatOlmPhase.PHASE_THREE -> 3
                GreatOlmPhase.FINAL_HEAD -> 4
                else -> 0
            }

    fun activate() {
        check(phase == GreatOlmPhase.DORMANT)
        phase = GreatOlmPhase.PHASE_ONE
    }

    fun isAlive(target: GreatOlmTarget): Boolean =
        when (target) {
            GreatOlmTarget.LEFT_HAND -> leftHandAlive
            GreatOlmTarget.RIGHT_HAND -> rightHandAlive
            GreatOlmTarget.HEAD -> headAlive
        }

    fun canDamage(target: GreatOlmTarget): Boolean =
        when (target) {
            GreatOlmTarget.LEFT_HAND,
            GreatOlmTarget.RIGHT_HAND -> phase in ACTIVE_HAND_PHASES && isAlive(target)
            GreatOlmTarget.HEAD -> phase == GreatOlmPhase.FINAL_HEAD && headAlive
        }

    fun defeat(target: GreatOlmTarget): GreatOlmTransition {
        check(canDamage(target)) { "Target $target is not vulnerable during $phase" }
        return when (target) {
            GreatOlmTarget.LEFT_HAND -> {
                leftHandAlive = false
                advanceHandSetIfComplete()
            }
            GreatOlmTarget.RIGHT_HAND -> {
                rightHandAlive = false
                advanceHandSetIfComplete()
            }
            GreatOlmTarget.HEAD -> {
                headAlive = false
                phase = GreatOlmPhase.DEFEATED
                GreatOlmTransition.RAID_COMPLETE
            }
        }
    }

    fun canRevive(target: GreatOlmTarget): Boolean {
        if (phase != GreatOlmPhase.PHASE_THREE || target == GreatOlmTarget.HEAD) return false
        val other =
            if (target == GreatOlmTarget.LEFT_HAND) GreatOlmTarget.RIGHT_HAND
            else GreatOlmTarget.LEFT_HAND
        return !isAlive(target) && isAlive(other)
    }

    fun revive(target: GreatOlmTarget) {
        check(canRevive(target)) { "Target $target cannot revive during $phase" }
        when (target) {
            GreatOlmTarget.LEFT_HAND -> leftHandAlive = true
            GreatOlmTarget.RIGHT_HAND -> rightHandAlive = true
            GreatOlmTarget.HEAD -> error("Great Olm's head cannot revive")
        }
    }

    private fun advanceHandSetIfComplete(): GreatOlmTransition {
        if (leftHandAlive || rightHandAlive) return GreatOlmTransition.HAND_DEFEATED
        phase =
            when (phase) {
                GreatOlmPhase.PHASE_ONE -> GreatOlmPhase.PHASE_TWO
                GreatOlmPhase.PHASE_TWO -> GreatOlmPhase.PHASE_THREE
                GreatOlmPhase.PHASE_THREE -> GreatOlmPhase.FINAL_HEAD
                else -> error("Hands cannot advance from $phase")
            }
        cycleIndex = 0
        if (phase == GreatOlmPhase.FINAL_HEAD) return GreatOlmTransition.FINAL_HEAD
        leftHandAlive = true
        rightHandAlive = true
        return GreatOlmTransition.NEXT_PHASE
    }

    fun nextSpecial(): GreatOlmSpecial {
        val phaseCycle =
            when (phase) {
                GreatOlmPhase.PHASE_ONE ->
                    listOf(GreatOlmSpecial.CRYSTALS, GreatOlmSpecial.LIGHTNING)
                GreatOlmPhase.PHASE_TWO -> listOf(GreatOlmSpecial.ACID, GreatOlmSpecial.BURN)
                GreatOlmPhase.PHASE_THREE ->
                    listOf(GreatOlmSpecial.PORTALS, GreatOlmSpecial.CRYSTALS)
                GreatOlmPhase.FINAL_HEAD -> listOf(GreatOlmSpecial.FALLING_CRYSTALS)
                else -> error("No active special cycle during $phase")
            }
        return phaseCycle[cycleIndex++ % phaseCycle.size]
    }

    private companion object {
        private val ACTIVE_HAND_PHASES =
            setOf(
                GreatOlmPhase.PHASE_ONE,
                GreatOlmPhase.PHASE_TWO,
                GreatOlmPhase.PHASE_THREE,
            )
    }
}

internal enum class GreatOlmTransition {
    HAND_DEFEATED,
    NEXT_PHASE,
    FINAL_HEAD,
    RAID_COMPLETE,
}
