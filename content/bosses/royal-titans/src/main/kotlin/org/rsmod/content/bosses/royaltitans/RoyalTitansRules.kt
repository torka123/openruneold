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
    const val WALL_TELEGRAPH_TICKS = 4
    const val WALL_LIFETIME_TICKS = 39
    const val SUMMON_DELAY_TICKS = 2
    // Legacy fallback, disproved by2283 ages16..40; no replacement trigger is established.
    const val ELEMENTAL_LIFETIME_TICKS = 18
    const val ELEMENTAL_SPLASH_HIT_DELAY_TICKS = 1
    const val BRANDA_TRANSITION_TICKS = 3
    const val ELDRIC_TRANSITION_CAST_TICK = 4
    const val ELDRIC_TRANSITION_TICKS = 7
    const val ARENA_OPENING_TICKS = 8
    const val FIRST_ARENA_CYCLE_TICKS = 36
    const val ARENA_CYCLE_TICKS = 29
    const val AREA_CHARGE_TICKS = 6
    const val AREA_PULSE_GAP = 2
    const val AREA_GRACE_TICKS = 4
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

    fun isSafeTile(localX: Int, localZ: Int, safeX: Int, safeZ: Int): Boolean =
        localX == safeX && localZ == safeZ

    fun elementalKillAttackDelay(remainingDelay: Int, kills: Int = 1): Int =
        (remainingDelay - kills.coerceAtLeast(0)).coerceAtLeast(0)

    fun finalStompDamage(rolledDamage: Int, protectFromMelee: Boolean): Int =
        if (protectFromMelee) rolledDamage.coerceAtMost(10) else rolledDamage

    // Uniform selection across eligible tiles is an isolated rule; the exact OSRS distribution
    // is not established by a finite capture. Production callers must supply GameRandom.
    fun safeTile(survivor: RoyalElement?, nextInt: (Int) -> Int): Pair<Int, Int> {
        val rows =
            when (survivor) {
                RoyalElement.FIRE -> 28..28
                RoyalElement.ICE -> 34..34
                null -> 28..34
            }
        val candidates = (29..33).flatMap { x -> rows.map { z -> x to z } }
        return candidates[nextInt(candidates.size)]
    }

    fun removedWallIndices(selected: Int): IntRange =
        (selected - 1).coerceAtLeast(0)..(selected + 1).coerceAtMost(4)

    fun attackableWall(element: RoyalElement, spellInternal: String): Boolean {
        return org.rsmod.content.skills.magic.spell.attacks.RoyalTitansWallSpells.accepts(
            if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_HITBOX
            else RoyalTitansAssets.ICE_WALL_HITBOX,
            spellInternal,
        )
    }

    fun opposingSpell(element: RoyalElement, spellInternal: String): Boolean =
        when (element) {
            RoyalElement.FIRE -> spellInternal.contains("water", ignoreCase = true)
            RoyalElement.ICE -> spellInternal.contains("fire", ignoreCase = true)
        }
}
