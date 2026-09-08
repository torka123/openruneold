package org.rsmod.content.bosses.yama

internal object YamaCombatTimeline {
    const val SUMMON_BOSS_DELAY = 2
    const val ENABLE_ATTACK_DELAY = 3
    const val STANDARD_IMPACT_DELAY = 3
    const val GLYPH_ATTACK_IMPACT_DELAY = 6
    const val SHADOW_WAVE_TELEGRAPH = 4
    const val FIRE_STREAK_TELEGRAPH = 3
    const val SHADOW_STOMP_IMPACT = 5
    const val METEOR_IMPACT = 4
    const val JUDGE_PORTAL_DELAY = 6
    const val JUDGE_DEATH_RETURN_DELAY = 6
    const val JUDGE_RETURN_PLAYER_ANIM_DELAY = 7
    const val JUDGE_RETURN_TELEPORT_DELAY = 10
    const val JUDGE_SURGE_INTERVAL = 5
    const val JUDGE_SURGE_MOVE_CLIENT_CYCLES = 30
    const val STONE_JUMP_CLIENT_CYCLES = 27
    const val JUDGE_POST_CROSS_ATTACK_DELAY = 1
    const val JUDGE_FIREBALL_IMPACT_DELAY = 4
    const val PHASE_RESUME_ATTACK_DELAY = 6
    const val SHADOW_CRASH_FIRST_LOCK_DELAY = 2
    const val SHADOW_CRASH_SET_INTERVAL = 3
    const val SHADOW_CRASH_IMPACT_DELAY = 3
    const val MOVING_HAZARD_HIT_DELAY = 1
    const val VOID_FLARE_HIT_DELAY = 1
    const val PHASE_THREE_FOUR_WAVE_PAIR_GAP = 10

    fun bossSpawnCycle(challengeCycle: Int) = challengeCycle + SUMMON_BOSS_DELAY

    fun bossAttackableCycle(challengeCycle: Int) = challengeCycle + ENABLE_ATTACK_DELAY + 2

    fun standardImpactCycle(castCycle: Int) = castCycle + STANDARD_IMPACT_DELAY

    fun glyphAttackImpactCycle(castCycle: Int) = castCycle + GLYPH_ATTACK_IMPACT_DELAY

    fun judgeArrivalCycle(portalCycle: Int) = portalCycle + JUDGE_PORTAL_DELAY

    fun judgeReturnCycle(deathCycle: Int) = deathCycle + JUDGE_DEATH_RETURN_DELAY

    fun judgeReturnPlayerAnimCycle(deathCycle: Int) = deathCycle + JUDGE_RETURN_PLAYER_ANIM_DELAY

    fun judgeReturnTeleportCycle(deathCycle: Int) = deathCycle + JUDGE_RETURN_TELEPORT_DELAY

    fun judgeFireballImpactCycle(castCycle: Int) = castCycle + JUDGE_FIREBALL_IMPACT_DELAY

    fun judgeSurgeCycles(arrivalCycle: Int, count: Int): List<Int> =
        List(count.coerceAtLeast(0)) { arrivalCycle + it * JUDGE_SURGE_INTERVAL }

    fun meteorImpactCycle(castCycle: Int) = castCycle + METEOR_IMPACT

    fun shadowStompImpactCycle(castCycle: Int) = castCycle + SHADOW_STOMP_IMPACT

    fun shadowCrashLockCycles(castCycle: Int, contract: YamaContract): List<Int> =
        shadowCrashLockCycles(castCycle, YamaRules.profile(contract).shadowCrashSets)

    fun shadowCrashLockCycles(castCycle: Int, sets: Int): List<Int> =
        List(sets.coerceAtLeast(0)) {
            castCycle + SHADOW_CRASH_FIRST_LOCK_DELAY + it * SHADOW_CRASH_SET_INTERVAL
        }

    fun shadowCrashImpactCycle(lockCycle: Int) = lockCycle + SHADOW_CRASH_IMPACT_DELAY
}
