package org.rsmod.content.bosses.yama

import kotlin.math.atan2
import kotlin.math.roundToInt

internal enum class YamaContract(
    val itemId: Int?,
    val displayName: String,
    val duoAllowed: Boolean,
    val returnedOnVictory: Boolean,
) {
    NONE(null, "Normal challenge", true, false),
    GLYPHIC(30810, "Contract of Glyphic Attenuation", false, true),
    SENSORY(30813, "Contract of Sensory Clouding", false, true),
    BLOODIED(30816, "Contract of Bloodied Blows", false, true),
    DIVINE(30819, "Contract of Divine Severance", false, true),
    FORFEIT(30822, "Contract of Forfeit Breath", false, true),
    OATHPLATE(30825, "Contract of Oathplate Acquisition", false, false),
    SHARD(30828, "Contract of Shard Acquisition", true, false),
    CATALYST(30831, "Contract of Catalyst Acquisition", true, false),
    WORM(30834, "Contract of Worm Acquisition", true, false),
    HARMONY(30837, "Contract of Harmony Acquisition", false, true),
    FAMILIAR(30840, "Contract of Familiar Acquisition", true, true),
    CUSTOM(null, "Custom pineapple-pizza contract", true, false);

    val isContractFight: Boolean
        get() = this != NONE

    val enhancedAcquisition: Boolean
        get() = this in setOf(CATALYST, WORM, SHARD, OATHPLATE, HARMONY, FAMILIAR, CUSTOM)

    val acquisitionShadowCrash: Boolean
        get() = this in setOf(CATALYST, WORM, SHARD, OATHPLATE, HARMONY, CUSTOM)

    val persistentShadowPools: Boolean
        get() = this in setOf(OATHPLATE, HARMONY)

    companion object {
        val itemContracts = entries.filter { it.itemId != null }

        fun forItem(id: Int): YamaContract? = itemContracts.firstOrNull { it.itemId == id }
    }
}

internal data class YamaContractProfile(
    val hitpoints: Int,
    val standardMaxHit: Int,
    val attackRatePhaseOneTwo: Int,
    val attackRatePhaseThree: Int,
    val regularsBeforeSpecial: Int,
    val glyphProtectionTicks: Int,
    val glyphRespawnTicks: Int,
    val flareCount: Int,
    val judgeAttackRate: Int,
    val judgeTargets: Int,
    val shadowCrashSets: Int,
    val restorationPercent: Int,
    val prayerPierce: Boolean,
    val continuousFlares: Boolean,
    val healingBlocked: Boolean,
)

internal object YamaRules {
    val JUDGE_STONE_ROWS = listOf(31, 34, 37, 40, 43, 46)
    val VOID_FLARE_MULTI_POSITIONS = listOf(23 to 33, 26 to 25, 35 to 25, 38 to 33)
    val VOID_FLARE_PHASE_THREE_ORDER = listOf(26 to 25, 23 to 33, 35 to 25, 38 to 33)
    const val JUDGE_WEST_LANDING_X = 8
    const val JUDGE_EAST_LANDING_X = 50
    const val JUDGE_LANDING_Z = 48
    val FIRE_STREAK_LANES = listOf(21, 22, 23, 25, 26, 27, 29, 31, 35)
    val JUDGE_WEST_X_RANGE = 5..9
    val JUDGE_EAST_X_RANGE = 49..53
    const val JUDGE_STONE_INTERACTION_RANGE = 4
    const val ACTIVE_FIGHT_RANGE = 18
    const val BASE_HITPOINTS = 2500
    const val BLOODIED_HITPOINTS = 3000
    const val GLYPHIC_HITPOINTS = 2875
    const val GRAVE_FEE_CAP = 500_000
    const val YAMA_DISCOUNT_KILLS = 10
    const val MIN_REWARD_DAMAGE = 375
    const val MIN_REWARD_CONTRIBUTION_PERCENT = 15.0
    const val JUDGE_HITPOINTS = 400
    const val JUDGE_SURGE_MAX = 30
    const val JUDGE_FIREBALL_MAX = 20
    const val JUDGE_FIREBALL_SELF_MIN = 13
    const val JUDGE_FIREBALL_SELF_MAX = 18
    const val JUDGE_PORTAL_FALL_HEIGHT = 638
    const val JUDGE_PORTAL_SPOTANIM_SLOT = 5
    const val VOID_FLARE_HITPOINTS = 140
    const val FAMILIAR_VOID_FLARE_HITPOINTS = 81
    const val NORMAL_STANDARD_MAX = 46
    const val CONTRACT_STANDARD_MAX = 53
    const val MELEE_PROTECTED_MAX = 6
    const val PHASE_THREE_MELEE_PROTECTED_MAX = 22
    const val PHASE_THREE_PRAYER_PIERCE_MAX = 3
    const val SHADOW_STOMP_MAX = 80
    const val SHADOW_STOMP_PRAYER_DRAIN = 19
    const val SHADOW_STOMP_ROOT_TICKS = 2
    const val FIRE_STREAK_MAX = 20
    const val SHADOW_WAVE_MAX = 20
    const val SHADOW_CRASH_MAX = 20
    const val VOID_FLARE_FULL_MAX = 44
    const val METEOR_BURN_VALUE = 8
    const val VOID_FLARE_BASE_LIFETIME = 25
    const val VOID_FLARE_ATTACKABLE_DELAY = 3
    const val VOID_FLARE_PROGRESS_CLIENT_CYCLES = 600
    const val FIRE_STREAK_NORMAL_COUNT = 8
    const val FIRE_STREAK_CONTRACT_COUNT = 9
    const val PHASE_THREE_SHADOW_CRASH_SETS = 3
    const val GLYPH_NORMAL_PROTECTION = 10
    const val GLYPH_GLYPHIC_PROTECTION = 2
    const val GLYPH_SIZE = 3
    const val GLYPH_CENTRE_OFFSET = GLYPH_SIZE / 2
    const val GLYPH_DEACTIVATE_TICKS = 1
    const val GLYPH_NORMAL_RESPAWN = 30
    const val GLYPH_CONTRACT_RESPAWN = 20
    const val NORMAL_REGULARS = 3
    const val CONTRACT_REGULARS = 2
    const val NORMAL_ATTACK_RATE = 8
    const val CONTRACT_ATTACK_RATE = 7
    const val PHASE_THREE_ATTACK_RATE = 7
    const val JUDGE_NORMAL_ATTACK_RATE = 5
    const val JUDGE_CONTRACT_ATTACK_RATE = 3

    fun profile(contract: YamaContract): YamaContractProfile {
        val contracted = contract.isContractFight
        return YamaContractProfile(
            hitpoints =
                when (contract) {
                    YamaContract.BLOODIED -> BLOODIED_HITPOINTS
                    YamaContract.GLYPHIC -> GLYPHIC_HITPOINTS
                    else -> BASE_HITPOINTS
                },
            standardMaxHit = if (contracted) CONTRACT_STANDARD_MAX else NORMAL_STANDARD_MAX,
            attackRatePhaseOneTwo = if (contracted) CONTRACT_ATTACK_RATE else NORMAL_ATTACK_RATE,
            attackRatePhaseThree = PHASE_THREE_ATTACK_RATE,
            regularsBeforeSpecial = if (contracted) CONTRACT_REGULARS else NORMAL_REGULARS,
            glyphProtectionTicks =
                if (contract == YamaContract.GLYPHIC) GLYPH_GLYPHIC_PROTECTION
                else GLYPH_NORMAL_PROTECTION,
            glyphRespawnTicks = if (contracted) GLYPH_CONTRACT_RESPAWN else GLYPH_NORMAL_RESPAWN,
            flareCount =
                when {
                    contract == YamaContract.FAMILIAR -> 1
                    contract.isContractFight -> 4
                    else -> 2
                },
            judgeAttackRate =
                if (contracted) JUDGE_CONTRACT_ATTACK_RATE else JUDGE_NORMAL_ATTACK_RATE,
            judgeTargets = if (contracted) 2 else 1,
            shadowCrashSets = if (contracted) 5 else 3,
            restorationPercent = if (contract.enhancedAcquisition) 50 else 100,
            prayerPierce = contract in setOf(YamaContract.FAMILIAR, YamaContract.SENSORY),
            continuousFlares = contract in setOf(YamaContract.FAMILIAR, YamaContract.DIVINE),
            healingBlocked = contract in setOf(YamaContract.FAMILIAR),
        )
    }

    fun firstThreshold(hitpoints: Int, contract: YamaContract = YamaContract.NONE): Int =
        if (contract.isContractFight) (hitpoints * 3) / 4 else (hitpoints * 2) / 3

    fun secondThreshold(hitpoints: Int, contract: YamaContract = YamaContract.NONE): Int =
        if (contract.isContractFight) hitpoints / 2 else hitpoints / 3

    fun phaseFor(hitpoints: Int, maximum: Int, contract: YamaContract = YamaContract.NONE): Int =
        when {
            hitpoints > firstThreshold(maximum, contract) -> 1
            hitpoints > secondThreshold(maximum, contract) -> 2
            else -> 3
        }

    fun attackRate(phase: Int, contract: YamaContract): Int =
        if (phase == 3) profile(contract).attackRatePhaseThree
        else profile(contract).attackRatePhaseOneTwo

    fun protectedStandardMaximum(
        phase: Int,
        style: YamaAttackStyle,
        protected: Boolean,
        contract: YamaContract,
    ): Int {
        if (!protected) return profile(contract).standardMaxHit
        if (contract == YamaContract.SENSORY) return profile(contract).standardMaxHit
        if (contract == YamaContract.DIVINE) return 0
        return when {
            phase == 3 && style == YamaAttackStyle.MELEE -> PHASE_THREE_MELEE_PROTECTED_MAX
            phase == 3 -> PHASE_THREE_PRAYER_PIERCE_MAX
            style == YamaAttackStyle.MELEE -> MELEE_PROTECTED_MAX
            else -> 0
        }
    }

    fun isGlyphCentre(anchorX: Int, anchorZ: Int, playerX: Int, playerZ: Int): Boolean =
        playerX == anchorX + GLYPH_CENTRE_OFFSET && playerZ == anchorZ + GLYPH_CENTRE_OFFSET

    fun glyphCost(distance: Int, contract: YamaContract, type: YamaGlyph = YamaGlyph.SHADOW): Int {
        val base =
            when {
                distance >= 4 -> 2
                distance == 3 -> 6
                distance == 2 -> 9
                else -> 12
            }
        if (contract != YamaContract.GLYPHIC) return base
        return if (type == YamaGlyph.FIRE && distance >= 4) 16 else base * 3
    }

    fun fireStreakCount(contract: YamaContract): Int =
        if (contract.isContractFight) FIRE_STREAK_CONTRACT_COUNT else FIRE_STREAK_NORMAL_COUNT

    fun glyphRecipientHashes(
        participantHashes: Set<Long>,
        onlineHashes: Collection<Long>,
    ): Set<Long> = onlineHashes.filterTo(linkedSetOf()) { it in participantHashes }

    fun flareSpawnPositions(count: Int, singleCursor: Int): List<Pair<Int, Int>> =
        if (count == 1) {
            listOf(
                VOID_FLARE_PHASE_THREE_ORDER[
                    Math.floorMod(singleCursor, VOID_FLARE_PHASE_THREE_ORDER.size)]
            )
        } else {
            VOID_FLARE_MULTI_POSITIONS.take(count.coerceIn(0, VOID_FLARE_MULTI_POSITIONS.size))
        }

    fun flareCount(phase: Int, contract: YamaContract): Int =
        if (phase == 3) {
            1
        } else {
            when (contract) {
                YamaContract.FAMILIAR -> 1
                YamaContract.DIVINE -> 3
                YamaContract.NONE -> 2
                else -> 4
            }
        }

    fun flareHitpoints(phase: Int, contract: YamaContract, index: Int): Int =
        when {
            contract == YamaContract.FAMILIAR -> FAMILIAR_VOID_FLARE_HITPOINTS
            contract == YamaContract.DIVINE && index == 2 -> 71
            contract.acquisitionShadowCrash && phase < 3 && index >= 2 -> 71
            contract == YamaContract.BLOODIED && index >= 2 -> 71
            phase == 3 && contract == YamaContract.NONE -> 71
            else -> VOID_FLARE_HITPOINTS
        }

    fun judgeCavernDamageTurn(turn: Int): Boolean = turn == 5 || turn == 13

    fun flareExplosionDamage(remaining: Int, maximum: Int): Int =
        ((remaining.coerceIn(0, maximum).toDouble() / maximum) * VOID_FLARE_FULL_MAX).roundToInt()

    fun flareHealing(damage: Int): Int = damage.coerceAtLeast(0)

    fun flareShouldHeal(unabsorbedPlayers: Int): Boolean = unabsorbedPlayers > 0

    fun flareForcesMaximum(style: YamaAttackStyle, scorchingBow: Boolean): Boolean =
        style == YamaAttackStyle.MAGIC || scorchingBow

    fun flareModifiedDamage(
        rolledDamage: Int,
        currentMaxHit: Int,
        style: YamaAttackStyle,
        scorchingBow: Boolean,
        demonbane: Boolean,
    ): Int {
        val base =
            if (flareForcesMaximum(style, scorchingBow)) currentMaxHit.coerceAtLeast(1)
            else rolledDamage.coerceAtLeast(0)
        return if (demonbane) base * 2 else base
    }

    fun nextGlyphAttack(current: YamaGlyph): YamaGlyph =
        if (current == YamaGlyph.FIRE) YamaGlyph.SHADOW else YamaGlyph.FIRE

    fun glyphProtects(protection: YamaGlyph, attack: YamaGlyph): Boolean = protection == attack

    fun nextAlternatingStandardStyle(current: YamaAttackStyle): YamaAttackStyle =
        if (current == YamaAttackStyle.RANGED) YamaAttackStyle.MAGIC else YamaAttackStyle.RANGED

    fun phaseOneStyleSeed(fireGlyphMajority: Boolean): YamaAttackStyle =
        if (fireGlyphMajority) YamaAttackStyle.MAGIC else YamaAttackStyle.RANGED

    fun phaseThreeStyleSeed(fireGlyphMajority: Boolean): YamaAttackStyle =
        if (fireGlyphMajority) YamaAttackStyle.RANGED else YamaAttackStyle.MAGIC

    fun meteorDamage(movedTiles: Int, distanceFromCentre: Int, currentHitpoints: Int): Int =
        when {
            movedTiles < 2 || distanceFromCentre < 2 -> currentHitpoints.coerceAtLeast(1)
            distanceFromCentre == 2 -> 80
            distanceFromCentre == 3 -> 40
            else -> 0
        }

    fun bloodiedMinimumHit(playerHitpointsPercent: Int, maximum: Int): Int =
        (((100 - playerHitpointsPercent.coerceIn(0, 100)) / 100.0) * maximum).roundToInt()

    fun contributionPercent(damage: Int, maximumHitpoints: Int): Double =
        if (maximumHitpoints <= 0) 0.0 else damage.coerceAtLeast(0) * 100.0 / maximumHitpoints

    fun rewardEligible(damage: Int, maximumHitpoints: Int): Boolean =
        damage >= MIN_REWARD_DAMAGE &&
            contributionPercent(damage, maximumHitpoints) >= MIN_REWARD_CONTRIBUTION_PERCENT

    fun petEligible(damage: Int, maximumHitpoints: Int): Boolean =
        contributionPercent(damage, maximumHitpoints) > 15.0

    fun specialFor(phase: Int, index: Int): YamaSpecial {
        val rotation =
            when (phase) {
                1 ->
                    listOf(
                        YamaSpecial.GLYPH_ATTACK,
                        YamaSpecial.SHADOW_WAVES,
                        YamaSpecial.FIRE_STREAKS,
                        YamaSpecial.VOID_FLARES,
                    )
                2 ->
                    listOf(
                        YamaSpecial.METEOR_STRIKE,
                        YamaSpecial.SHADOW_STOMP,
                        YamaSpecial.VOID_FLARES,
                    )
                else -> listOf(YamaSpecial.SHADOW_WAVES, YamaSpecial.VOID_FLARES)
            }
        return rotation[Math.floorMod(index, rotation.size)]
    }

    fun isValidJudgeStone(slot: Int, completedRows: Int, localX: Int, localZ: Int): Boolean {
        if (completedRows !in JUDGE_STONE_ROWS.indices) return false
        val xRange = if (slot == 0) JUDGE_WEST_X_RANGE else JUDGE_EAST_X_RANGE
        return localX in xRange && localZ == JUDGE_STONE_ROWS[completedRows]
    }

    fun judgeCrossingComplete(completedRows: Int): Boolean = completedRows >= JUDGE_STONE_ROWS.size

    fun judgeLandingX(slot: Int): Int =
        if (slot == 0) JUDGE_WEST_LANDING_X else JUDGE_EAST_LANDING_X

    fun judgeSurgeCount(randomBit: Int): Int = 1 + Math.floorMod(randomBit, 2)

    fun judgeSuccessfulDamage(rolledDamage: Int, currentMaxHit: Int): Int =
        maxOf(rolledDamage, currentMaxHit.coerceAtLeast(1))

    fun judgeFireballHitsJudge(
        judgeX: Int,
        judgeZ: Int,
        judgeSize: Int,
        targetX: Int,
        targetZ: Int,
    ): Boolean {
        val size = judgeSize.coerceAtLeast(1)
        return targetX in judgeX until judgeX + size && targetZ in judgeZ until judgeZ + size
    }

    fun exactMoveAngle(startX: Int, startZ: Int, endX: Int, endZ: Int): Int {
        val dx = endX - startX
        val dz = endZ - startZ
        if (dx == 0 && dz == 0) return 0
        return (1024 + (atan2(dx.toDouble(), dz.toDouble()) * 325.94932345220167).roundToInt()) and
            2047
    }

    fun glyphAbsorbMessage(type: YamaGlyph): String =
        when (type) {
            YamaGlyph.FIRE -> "<col=ff8e32>You absorb a Glyph of Fire."
            YamaGlyph.SHADOW -> "<col=a53fff>You absorb a Glyph of Shadow."
        }

    fun glyphBlockedMessage(type: YamaGlyph): String =
        when (type) {
            YamaGlyph.FIRE -> "<col=ff8e32>The Glyph of Fire absorbs the damage from the attack."
            YamaGlyph.SHADOW ->
                "<col=a53fff>The Glyph of Shadow absorbs the damage from the attack."
        }

    fun glyphFadeMessage(type: YamaGlyph): String =
        "<col=ff3045>The Glyph of ${if (type == YamaGlyph.FIRE) "Fire" else "Shadow"} fades away."

    fun forfeitRunDrain(cycle: Int, startCycle: Int): Boolean =
        (cycle - startCycle) > 0 && (cycle - startCycle) % 4 == 0

    fun customOfferingCount(): Int = 100

    fun graveItemFee(singleValue: Long): Int =
        when {
            singleValue < 100_000L -> 0
            singleValue < 1_000_000L -> 1_000
            singleValue < 10_000_000L -> 10_000
            else -> 100_000
        }

    fun discountedGraveFee(baseFee: Long, killCount: Int): Int {
        var fee = baseFee.coerceIn(0L, GRAVE_FEE_CAP.toLong())
        if (killCount < YAMA_DISCOUNT_KILLS) fee /= 4
        return fee.toInt()
    }

    fun restorationAfterRestriction(previous: Int, restored: Int, percent: Int): Int {
        if (restored <= previous) return restored
        val gained = restored - previous
        return previous + (gained * percent.coerceIn(0, 100) / 100)
    }
}

internal class YamaRotation(private val contract: YamaContract) {
    var regularsSinceSpecial: Int = 0
        private set

    var specialIndex: Int = 0
        private set

    private var firstSpecialAfterOne: Boolean = true

    fun next(phase: Int): YamaSpecial? {
        if (firstSpecialAfterOne) {
            if (regularsSinceSpecial == 0) {
                regularsSinceSpecial = 1
                return null
            }
            firstSpecialAfterOne = false
            regularsSinceSpecial = 0
            return YamaRules.specialFor(phase, specialIndex++)
        }
        if (regularsSinceSpecial < YamaRules.profile(contract).regularsBeforeSpecial) {
            regularsSinceSpecial++
            return null
        }
        regularsSinceSpecial = 0
        return YamaRules.specialFor(phase, specialIndex++)
    }

    fun resetForPhase(firstSpecialAfterOne: Boolean = true) {
        regularsSinceSpecial = 0
        specialIndex = 0
        this.firstSpecialAfterOne = firstSpecialAfterOne
    }
}
