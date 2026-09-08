package org.rsmod.content.bosses.royaltitans

internal enum class RoyalTitansStage {
    INTRO,
    RANGED,
    MELEE,
    TRANSITION,
    ARENA,
    FINAL_BRANDA,
    FINAL_ELDRIC,
    COMPLETE,
}

internal enum class RoyalElement {
    FIRE,
    ICE,
}

internal object RoyalTitansRules {
    const val ARENA_THRESHOLD_HP = 210
    const val LATE_JOIN_MINIMUM_HP = 450
    const val INTRO_TICKS = 4
    const val RANGED_PHASE_TICKS = 33
    const val MELEE_PHASE_TICKS = 21
    const val BRANDA_WALL_CAST_TICK = 3
    const val ELDRIC_WALL_CAST_TICK = 5
    const val ADVANCE_TICK = 30
    const val WALL_TELEGRAPH_TICKS = 4
    const val WALL_LIFETIME_TICKS = 50
    const val SUMMON_DELAY_TICKS = 2
    const val ELEMENTAL_LIFETIME_TICKS = 18
    const val ELEMENTAL_SPLASH_HIT_DELAY_TICKS = 1
    const val BRANDA_TRANSITION_TICKS = 3
    const val ELDRIC_TRANSITION_CAST_TICK = 4
    const val ELDRIC_TRANSITION_TICKS = 7
    const val ARENA_OPENING_TICKS = 7
    const val FINAL_OPENING_TICKS = 13
    const val FIRST_ARENA_CYCLE_TICKS = 36
    const val ARENA_CYCLE_TICKS = 29
    const val AREA_CHARGE_TICKS = 6
    const val AREA_PULSE_GAP = 2
    const val AREA_GRACE_TICKS = 4
    const val CORPSE_RESET_TICKS = 30
    const val RESET_WARNING_TICK = 12
    const val ELEMENTAL_HITPOINTS = 40
    const val WALL_MAX_HIT = 12
    const val STOMP_MAX_HIT = 26
    const val AREA_MAX_HIT = 30

    fun shouldEnterArena(brandaHp: Int, eldricHp: Int): Boolean =
        brandaHp <= ARENA_THRESHOLD_HP || eldricHp <= ARENA_THRESHOLD_HP

    fun canJoin(brandaHp: Int, eldricHp: Int, stage: RoyalTitansStage): Boolean =
        stage in setOf(RoyalTitansStage.INTRO, RoyalTitansStage.RANGED, RoyalTitansStage.MELEE) &&
            brandaHp >= LATE_JOIN_MINIMUM_HP &&
            eldricHp >= LATE_JOIN_MINIMUM_HP

    fun hasRangedAccuracyBoost(
        stage: RoyalTitansStage,
        areaAttackActive: Boolean,
        graceTicks: Int,
    ): Boolean = stage == RoyalTitansStage.RANGED || areaAttackActive || graceTicks > 0

    fun isSafeTile(localX: Int, localZ: Int, safeX: Int, safeZ: Int): Boolean =
        localX == safeX && localZ == safeZ

    fun capTransitionDamage(currentHp: Int, requestedDamage: Int, transitioned: Boolean): Int {
        val damage = requestedDamage.coerceAtLeast(0)
        return if (transitioned) damage
        else minOf(damage, (currentHp - ARENA_THRESHOLD_HP).coerceAtLeast(0))
    }

    fun elementalMagicHitChance(magicAccuracyBonus: Int, voidMage: Boolean): Double {
        var chance = ((magicAccuracyBonus + 30) / 100.0).coerceIn(0.30, 1.0)
        if (voidMage) chance = (chance * 1.45).coerceAtMost(1.0)
        return chance
    }

    fun elementalGuaranteedMax(magicAccuracyBonus: Int, voidMage: Boolean): Boolean =
        magicAccuracyBonus >= 70 || (voidMage && magicAccuracyBonus >= 39)

    fun elementalKillAttackDelay(sameTickKills: Int): Int =
        (5 - sameTickKills.coerceAtLeast(0)).coerceAtLeast(0)

    fun finalStompDamage(rolledDamage: Int, protectFromMelee: Boolean): Int =
        if (protectFromMelee) rolledDamage.coerceAtMost(10) else rolledDamage

    fun safeTile(survivor: RoyalElement?, deterministicIndex: Int): Pair<Int, Int> {
        val x = 29 + Math.floorMod(deterministicIndex, 5)
        val z =
            when (survivor) {
                RoyalElement.FIRE -> 28
                RoyalElement.ICE -> 34
                null -> 28 + Math.floorMod(deterministicIndex / 5, 7)
            }
        return x to z
    }

    fun attackableWall(element: RoyalElement, spellInternal: String): Boolean {
        val value = spellInternal.lowercase()
        val blastOrHigher =
            value.contains("blast") || value.contains("wave") || value.contains("surge")
        return when (element) {
            RoyalElement.FIRE ->
                (value.contains("water") && blastOrHigher) || value.contains("ice_")
            RoyalElement.ICE -> value.contains("fire") && blastOrHigher
        }
    }

    fun elementalSpellBaseMaxHit(spellInternal: String): Int? {
        val value = spellInternal.lowercase()
        val elementOffset =
            when {
                value.contains("wind") -> 0
                value.contains("water") -> 1
                value.contains("earth") -> 2
                value.contains("fire") -> 3
                else -> return null
            }
        return when {
            value.contains("strike") -> 2 + elementOffset * 2
            value.contains("bolt") -> 9 + elementOffset
            value.contains("blast") -> 13 + elementOffset
            value.contains("wave") -> 17 + elementOffset
            value.contains("surge") -> 21 + elementOffset
            else -> null
        }
    }

    fun opposingSpell(element: RoyalElement, spellInternal: String): Boolean =
        when (element) {
            RoyalElement.FIRE -> spellInternal.contains("water", ignoreCase = true)
            RoyalElement.ICE -> spellInternal.contains("fire", ignoreCase = true)
        }
}
