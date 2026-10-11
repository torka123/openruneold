package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.Selector

internal const val HYDRA_BASIC_HANDLER = "alchemical_hydra.basic"
internal const val HYDRA_SPECIAL_HANDLER = "alchemical_hydra.special"

internal fun alchemicalHydraSpec(encounters: HydraEncounterManager): BossSpec {
    val forms = listOf(
        "npc.hydraboss",
        "npc.hydraboss_p1_transition",
        "npc.hydraboss_p2_transition",
        "npc.hydraboss_p3_transition",
        "npc.hydraboss_2",
        "npc.hydraboss_3",
        "npc.hydraboss_4",
        "npc.hydraboss_finaldeath",
    )
    val abilities = mutableMapOf<String, Effect>()
    val phases = mutableListOf<HydraPhaseSpec>()

    for (phase in HydraPhase.entries) {
        for (style in HydraAttackStyle.entries) {
            val ability = abilityName(phase, style)
            abilities[ability] = basicAttack(phase, style, encounters)
            phases += HydraPhaseSpec(phase, style, ability)
        }
        val special = specialName(phase)
        abilities[special] = Effect.External(HYDRA_SPECIAL_HANDLER, phase)
    }

    return boss(*forms.toTypedArray()) {
        stats(attackRate = HydraRules.NORMAL_ATTACK_RATE)
        abilities.forEach { (name, effect) ->
            val specialPhase = HydraPhase.entries.firstOrNull { specialName(it) == name }
            val delay =
                specialPhase?.let {
                    when (it) {
                        HydraPhase.BLUE,
                        HydraPhase.RED -> HydraRules.LIGHTNING_TICKS
                        HydraPhase.GREEN,
                        HydraPhase.FINAL -> HydraRules.NORMAL_ATTACK_RATE
                    }
                }
            ability(name, effect, attackDelay = delay)
        }
        phases.forEach { phaseSpec ->
            val orderedStyles = if (phaseSpec.phase == HydraPhase.FINAL) {
                listOf(phaseSpec.style, phaseSpec.style.opposite())
            } else {
                List(3) { phaseSpec.style } + List(3) { phaseSpec.style.opposite() }
            }
            phase(phaseSpec.name) {
                attackRate = if (phaseSpec.phase == HydraPhase.FINAL) HydraRules.FINAL_ATTACK_RATE else HydraRules.NORMAL_ATTACK_RATE
                selector = Selector.Rotation(orderedStyles.map { abilityName(phaseSpec.phase, it) })
            }
        }
    }
}

private data class HydraPhaseSpec(
    val phase: HydraPhase,
    val style: HydraAttackStyle,
    val name: String,
)

private fun basicAttack(phase: HydraPhase, style: HydraAttackStyle, encounters: HydraEncounterManager): Effect {
    val isMagic = style == HydraAttackStyle.MAGIC
    val spot = if (isMagic) HydraAssets.MAGIC_PROJECTILE else HydraAssets.RANGED_PROJECTILE
    val hitType = if (isMagic) HitType.Magic else HitType.Ranged
    val projectile =
        Effect.Projectile(
            spotanim = RSCM.getReverseMapping(RSCMType.SPOTANIM, spot),
            config = BASIC_PROJECTILE,
            hit =
                Effect.Hit(
                    damage = DamageExpr.Custom { npc, _ -> encounters.rollBasicHit(npc, phase) },
                    type = hitType,
                ),
        )
    val secondProjectile = projectile.copy(
        hit = projectile.hit?.copy(
            damage = DamageExpr.Custom { npc, _ -> encounters.rollBasicHit(npc, phase) },
        ),
    )
    val secondHit = Effect.Whenever(
        condition = org.rsmod.api.bosses.spec.Condition.Custom { npc, _ ->
            encounters.basicHitCount(npc, phase) == 2
        },
        then = secondProjectile,
    )
    return Effect.Sequence(
        listOf(
            Effect.External(HYDRA_BASIC_HANDLER, style),
            Effect.Anim(RSCM.getReverseMapping(RSCMType.SEQ, basicAnimation(phase, style))),
            projectile,
            secondHit,
        ),
    )
}

private fun basicAnimation(phase: HydraPhase, style: HydraAttackStyle): Int =
    when (phase) {
        HydraPhase.GREEN -> if (style == HydraAttackStyle.MAGIC) HydraAssets.GREEN_MAGIC_ANIM else HydraAssets.GREEN_RANGE_ANIM
        HydraPhase.BLUE -> if (style == HydraAttackStyle.MAGIC) HydraAssets.BLUE_MAGIC_ANIM else HydraAssets.BLUE_RANGE_ANIM
        HydraPhase.RED -> if (style == HydraAttackStyle.MAGIC) HydraAssets.RED_MAGIC_ANIM else HydraAssets.RED_RANGE_ANIM
        HydraPhase.FINAL -> if (style == HydraAttackStyle.MAGIC) HydraAssets.FINAL_POISON_ANIM else HydraAssets.FINAL_RANGE_ANIM
    }

internal fun abilityName(phase: HydraPhase, style: HydraAttackStyle): String =
    "${phase.name.lowercase()}.${style.name.lowercase()}"

internal fun specialName(phase: HydraPhase): String = "${phase.name.lowercase()}.special"

private val BASIC_PROJECTILE = ProjectileConfig(
    startHeight = 48,
    endHeight = 24,
    startDelay = 30,
    travelTime = 30,
    angle = 15,
    progress = 16,
    stepMultiplier = 5,
)
