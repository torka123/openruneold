package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcHitParameters
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Ordinary attacks, attack ordinals and forced cadence belong to the native boss interpreter. */
internal fun guardiansBossSpec(role: GuardianRole): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    if (role == GuardianRole.DAWN) {
        abilities["basic"] =
            Effect.Whenever(
                Condition.AbilityCountModulo(2, 0) and Condition.WithinMeleeRange,
                guardianMelee(
                    GrotesqueAssets.DAWN_MELEE_ANIMATION,
                    GrotesqueGuardiansRules.DAWN_MELEE_MAX_HIT,
                ),
                guardianRanged(
                    GrotesqueAssets.DAWN_RANGED_ANIMATION,
                    GrotesqueGuardiansRules.DAWN_RANGED_MAX_HIT,
                ),
            )
        abilities["stone"] = Effect.External("guardians.stone")
    } else {
        abilities["basic"] =
            guardianMelee(
                GrotesqueAssets.DUSK_MELEE_ANIMATION,
                GrotesqueGuardiansRules.DUSK_PHASE_TWO_MAX_HIT,
            )
        abilities["phase-two"] =
            Effect.Sequence(
                listOf(
                    Effect.Whenever(
                        Condition.AbilityCountModulo(2, 0),
                        guardianMelee(
                            GrotesqueAssets.DUSK_MELEE_ANIMATION,
                            GrotesqueGuardiansRules.DUSK_PHASE_TWO_MAX_HIT,
                        ),
                        guardianMelee(
                            GrotesqueAssets.DUSK_MELEE_SECOND_ANIMATION,
                            GrotesqueGuardiansRules.DUSK_PHASE_TWO_MAX_HIT,
                        ),
                    ),
                    Effect.External("guardians.debris"),
                )
            )
        abilities["explosion"] =
            Effect.Sequence(
                listOf(Effect.External("guardians.explosion"), Effect.External("guardians.debris"))
            )
        abilities["final"] =
            Effect.Whenever(
                Condition.AbilityCountModulo(2, 0),
                guardianRanged(
                    GrotesqueAssets.DUSK_RANGED_ANIMATION,
                    GrotesqueGuardiansRules.DUSK_PHASE_FOUR_RANGED_MAX_HIT,
                ),
                guardianMelee(
                    GrotesqueAssets.DUSK_PHASE_FOUR_MELEE_ANIMATION,
                    GrotesqueGuardiansRules.DUSK_PHASE_FOUR_MELEE_MAX_HIT,
                ),
            )
        abilities["prison"] = Effect.External("guardians.prison")
    }
    val phases =
        GrotesquePhase.entries.associate { phase ->
            val name = phase.name.lowercase()
            val active =
                when (role) {
                    GuardianRole.DAWN ->
                        phase == GrotesquePhase.DAWN_ONE || phase == GrotesquePhase.DAWN_THREE
                    GuardianRole.DUSK ->
                        phase in
                            listOf(
                                GrotesquePhase.DAWN_ONE,
                                GrotesquePhase.DUSK_TWO,
                                GrotesquePhase.DAWN_THREE,
                                GrotesquePhase.DUSK_FOUR,
                            )
                }
            val ability =
                when {
                    role == GuardianRole.DUSK && phase == GrotesquePhase.DUSK_TWO -> "phase-two"
                    role == GuardianRole.DUSK && phase == GrotesquePhase.DUSK_FOUR -> "final"
                    else -> "basic"
                }
            val forced =
                when {
                    role == GuardianRole.DAWN && active -> listOf(ForcedAbility(0, "stone", 3, 3))
                    role == GuardianRole.DUSK && phase == GrotesquePhase.DUSK_TWO ->
                        listOf(ForcedAbility(0, "explosion", 2, 2))
                    role == GuardianRole.DUSK && phase == GrotesquePhase.DUSK_FOUR ->
                        listOf(ForcedAbility(42, "prison"))
                    else -> emptyList()
                }
            val locked =
                phase == GrotesquePhase.LIGHTNING_TRANSITION ||
                    (role == GuardianRole.DAWN && phase == GrotesquePhase.DUSK_TWO)
            name to
                PhaseSpec(
                    name,
                    lockMovement = locked,
                    selector = Selector.Rotation(if (active) listOf(ability) else emptyList()),
                    forceAbilities = forced,
                )
        }
    return BossSpec(
        (if (role == GuardianRole.DAWN) GrotesqueAssets.DAWN_FORMS else GrotesqueAssets.DUSK_FORMS)
            .map { RSCM.getReverseMapping(RSCMType.NPC, it) },
        BossStats(attackRate = GrotesqueGuardiansRules.ATTACK_RATE_TICKS),
        abilities,
        phases,
        emptyList(),
    )
}

private fun guardianMelee(animation: Int, maxHit: Int) =
    Effect.NpcAttack(
        CombatAttack.NpcMelee(MeleeAttackType.Slash),
        maxHit = maxHit,
        animation = RSCM.getReverseMapping(RSCMType.SEQ, animation),
        impactDelay = 1,
        parameters = "guardians.normal",
        parameterData = GuardianAttackStyle.MELEE,
    )

private fun guardianRanged(animation: Int, maxHit: Int) =
    Effect.NpcAttack(
        CombatAttack.NpcRanged(RangedAttackType.Standard),
        maxHit = maxHit,
        animation = RSCM.getReverseMapping(RSCMType.SEQ, animation),
        projectile =
            NpcProjectile(
                RSCM.getReverseMapping(RSCMType.SPOTANIM, GrotesqueAssets.RANGED_PROJECTILE),
                ProjAnimType(
                    startHeight = 90,
                    endHeight = 40,
                    delay = 0,
                    angle = 32,
                    lengthAdjustment = 0,
                    progress = 0,
                    stepMultiplier = 5,
                ),
            ),
        hits = listOf(NpcHitParameters(), NpcHitParameters(impactOffset = 1)),
        parameters = "guardians.normal",
        parameterData = GuardianAttackStyle.RANGED,
    )
