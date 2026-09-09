package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Existing attack order/timing expressed on the native encounter, not a second selector. */
internal fun alchemicalHydraBossSpec(): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    val phases = linkedMapOf<String, PhaseSpec>()
    for (phase in HydraPhase.entries) {
        for (style in HydraAttackStyle.entries) {
            val name = hydraPhaseName(phase, style)
            val magic = style == HydraAttackStyle.MAGIC
            abilities[name] =
                Effect.Sequence(
                    listOf(
                        Effect.SetAttackRate(AlchemicalHydraRules.attackRate(phase)),
                        Effect.NpcAttack(
                            attack =
                                if (magic) CombatAttack.NpcMagic(0)
                                else CombatAttack.NpcRanged(RangedAttackType.Standard),
                            animation =
                                RSCM.getReverseMapping(
                                    RSCMType.SEQ,
                                    hydraBasicAnimation(phase, style),
                                ),
                            projectile =
                                NpcProjectile(
                                    RSCM.getReverseMapping(
                                        RSCMType.SPOTANIM,
                                        if (magic) AlchemicalHydraAssets.MAGIC_PROJECTILE
                                        else AlchemicalHydraAssets.RANGED_PROJECTILE,
                                    ),
                                    AlchemicalHydraAssets.BASIC_PROJECTILE,
                                ),
                            parameters = "hydra.normal",
                            parameterData = style,
                        ),
                    )
                )
            val groupSize = if (phase == HydraPhase.FINAL) 1 else 3
            val special = "${phase.name.lowercase()}.special"
            phases[name] =
                PhaseSpec(
                    name = name,
                    attackRate = AlchemicalHydraRules.attackRate(phase),
                    selector =
                        Selector.Rotation(
                            List(groupSize) { name } +
                                List(groupSize) { hydraPhaseName(phase, style.opposite()) }
                        ),
                    forceAbilities = listOf(ForcedAbility(0, special, 9, 9, firstAttackCount = 3)),
                )
        }
        val duration =
            when (AlchemicalHydraRules.special(phase)) {
                HydraSpecial.POISON -> AlchemicalHydraRules.NORMAL_ATTACK_RATE
                HydraSpecial.LIGHTNING -> AlchemicalHydraRules.LIGHTNING_LIFETIME
                HydraSpecial.FLAME -> AlchemicalHydraRules.FLAME_LIFETIME
            }
        abilities["${phase.name.lowercase()}.special"] =
            Effect.Sequence(
                listOf(
                    Effect.SetAttackRate(duration),
                    Effect.External("hydra.${AlchemicalHydraRules.special(phase).name.lowercase()}"),
                )
            )
    }
    return BossSpec(
        npcTypes = AlchemicalHydraAssets.allForms.map { RSCM.getReverseMapping(RSCMType.NPC, it) },
        stats = BossStats(attackRate = AlchemicalHydraRules.NORMAL_ATTACK_RATE),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

internal fun hydraPhaseName(phase: HydraPhase, style: HydraAttackStyle) =
    "${phase.name.lowercase()}.${style.name.lowercase()}"

internal fun hydraBasicAnimation(phase: HydraPhase, style: HydraAttackStyle): Int =
    when (phase) {
        HydraPhase.GREEN ->
            if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.GREEN_MAGIC_ANIM
            else AlchemicalHydraAssets.GREEN_RANGE_ANIM
        HydraPhase.BLUE ->
            if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.BLUE_MAGIC_ANIM
            else AlchemicalHydraAssets.BLUE_RANGE_ANIM
        HydraPhase.RED ->
            if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.RED_MAGIC_ANIM
            else AlchemicalHydraAssets.RED_RANGE_ANIM
        HydraPhase.FINAL ->
            if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.FINAL_MAGIC_OR_POISON_ANIM
            else AlchemicalHydraAssets.FINAL_RANGE_ANIM
    }
