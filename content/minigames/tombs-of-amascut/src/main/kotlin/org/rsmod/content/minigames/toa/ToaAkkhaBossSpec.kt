package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Retained AkkhaTuning cadence and native NvP styles; raid specials still own form changes. */
internal fun toaAkkhaBossSpec(): BossSpec {
    val abilities =
        ToaCombatStyle.entries.associate { style ->
            val attack =
                when (style) {
                    ToaCombatStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                    ToaCombatStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                    ToaCombatStyle.MAGIC -> CombatAttack.NpcMagic(0)
                }
            val animation =
                when (style) {
                    ToaCombatStyle.MELEE -> AkkhaAssets.ATTACK_MELEE_SPEAR
                    ToaCombatStyle.RANGED -> AkkhaAssets.ATTACK_RANGE_SPEAR
                    ToaCombatStyle.MAGIC -> AkkhaAssets.ATTACK_MAGIC_SPEAR
                }
            toaAkkhaCombatPhase(style) to
                Effect.NpcAttack(
                    attack = attack,
                    animation = requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, animation)),
                    impactDelay = akkhaImpactDelay(style),
                    parameters = TOA_AKKHA_ATTACK_PARAMETERS,
                    parameterData = style,
                    // Native melee rejects a dead source; already-fired range/magic does not.
                    survivesSourceDeath = style != ToaCombatStyle.MELEE,
                )
        }
    return BossSpec(
        npcTypes =
            ToaNpcs.akkhaBossForms.map { id ->
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, id))
            },
        stats = BossStats(attackRate = AkkhaTuning.BASE_ATTACK_INTERVAL.value),
        abilities = abilities,
        phases =
            ToaCombatStyle.entries.associate { style ->
                val phase = toaAkkhaCombatPhase(style)
                phase to
                    PhaseSpec(
                        phase,
                        attackRate = AkkhaTuning.BASE_ATTACK_INTERVAL.value,
                        selector = Selector.Rotation(listOf(phase)),
                    )
            },
        triggers = emptyList(),
    )
}

internal fun toaAkkhaCombatPhase(style: ToaCombatStyle): String = style.name.lowercase()

internal const val TOA_AKKHA_ATTACK_PARAMETERS = "toa.akkha-ordinary"
