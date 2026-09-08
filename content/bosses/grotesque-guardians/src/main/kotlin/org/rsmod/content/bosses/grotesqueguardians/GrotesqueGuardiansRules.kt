package org.rsmod.content.bosses.grotesqueguardians

import kotlin.math.abs

internal object GrotesqueGuardiansRules {
    const val HITPOINTS = 450
    const val PHASE_THRESHOLD = 247
    const val FINISH_THRESHOLD = 9
    const val ATTACK_RATE_TICKS = 6
    const val DAWN_RANGED_MAX_HIT = 9
    const val DAWN_MELEE_MAX_HIT = 15
    const val DUSK_PHASE_TWO_MAX_HIT = 15
    const val DUSK_PHASE_FOUR_MELEE_MAX_HIT = 26
    const val DUSK_PHASE_FOUR_RANGED_MAX_HIT = 15
    const val DEBRIS_MAX_HIT = 22
    const val DEBRIS_STUN_TICKS = 10
    const val LIGHTNING_MIN_HIT = 5
    const val LIGHTNING_MAX_HIT = 10
    const val LIGHTNING_WARNING_TICKS = 2
    const val SPHERE_STAGE_TICKS = 12
    const val SPHERE_HEAL = 90
    const val PRISON_ESCAPE_TICKS = 5
    const val PRISON_MAX_HIT = 65
    const val RESPAWN_TICKS = 17

    val ROCK_HAMMERS =
        setOf("obj.slayer_rock_hammer", "obj.granite_hammer", "obj.rock_thrownhammer")

    fun phaseAfterThreshold(phase: GrotesquePhase): GrotesquePhase =
        when (phase) {
            GrotesquePhase.DAWN_ONE -> GrotesquePhase.DAWN_ASCENDING
            GrotesquePhase.DUSK_TWO -> GrotesquePhase.LIGHTNING_TRANSITION
            else -> phase
        }

    fun canDamage(role: GuardianRole, phase: GrotesquePhase): Boolean =
        when (phase) {
            GrotesquePhase.DAWN_ONE,
            GrotesquePhase.DAWN_THREE -> role == GuardianRole.DAWN
            GrotesquePhase.DAWN_ASCENDING,
            GrotesquePhase.DUSK_TWO,
            GrotesquePhase.LIGHTNING_TRANSITION,
            GrotesquePhase.DUSK_FOUR -> role == GuardianRole.DUSK
            else -> false
        }

    fun capDamageAtThreshold(hitpoints: Int, damage: Int): Int =
        damage.coerceIn(0, (hitpoints - PHASE_THRESHOLD).coerceAtLeast(0))

    fun capDawnFinalDamage(hitpoints: Int, damage: Int): Int =
        damage.coerceIn(0, (hitpoints - FINISH_THRESHOLD).coerceAtLeast(0))

    fun sphereState(ageTicks: Int): SphereState =
        when {
            ageTicks < SPHERE_STAGE_TICKS -> SphereState.LOW
            ageTicks < SPHERE_STAGE_TICKS * 2 -> SphereState.MEDIUM
            ageTicks < SPHERE_STAGE_TICKS * 3 -> SphereState.HIGH
            else -> SphereState.RETURNING
        }

    fun inDebrisArea(playerX: Int, playerZ: Int, impactX: Int, impactZ: Int): Boolean =
        abs(playerX - impactX) <= 1 && abs(playerZ - impactZ) <= 1

    fun directlyUnderDebris(playerX: Int, playerZ: Int, impactX: Int, impactZ: Int): Boolean =
        playerX == impactX && playerZ == impactZ

    fun inLightningArea(playerX: Int, playerZ: Int, centreX: Int, centreZ: Int): Boolean =
        abs(playerX - centreX) <= 1 && abs(playerZ - centreZ) <= 1

    fun phaseFourRangedFirstHit(accurate: Boolean, protected: Boolean): Int {
        if (!accurate) return 0
        val fixed = (DUSK_PHASE_FOUR_RANGED_MAX_HIT * 60) / 100
        return if (protected) fixed / 2 else fixed
    }

    fun phaseFourRangedSecondHit(accurate: Boolean, firstRoll: Int, reroll: Int): Int {
        if (!accurate) return 0
        return reroll.coerceIn(0, firstRoll.coerceIn(0, DUSK_PHASE_FOUR_RANGED_MAX_HIT))
    }

    fun healAfterPrisonDamage(current: Int, damage: Int): Int =
        (current + damage.coerceAtLeast(0)).coerceAtMost(HITPOINTS)
}

internal enum class SphereState {
    LOW,
    MEDIUM,
    HIGH,
    RETURNING,
}
