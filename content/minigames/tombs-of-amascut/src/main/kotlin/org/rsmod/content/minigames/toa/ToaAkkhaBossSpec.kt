package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** One accepted-attack budget across style changes; raid specials still own form changes. */
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
            mapOf(
                TOA_AKKHA_COMBAT_PHASE to
                    PhaseSpec(
                        TOA_AKKHA_COMBAT_PHASE,
                        attackRate = AkkhaTuning.BASE_ATTACK_INTERVAL.value,
                        selector =
                            Selector.SeededWeighted(
                                stream = TOA_AKKHA_STYLE_STREAM,
                                salt = 0x414B4B4841L,
                                entries =
                                    ToaCombatStyle.entries.map {
                                        WeightedRef(toaAkkhaCombatPhase(it))
                                    },
                                preparedChoice =
                                    PreparedChoice(
                                        TOA_AKKHA_STYLE_INPUT,
                                        drawBeforeOverride = false,
                                    ),
                            ),
                    )
            ),
        triggers = emptyList(),
    )
}

internal fun toaAkkhaCombatPhase(style: ToaCombatStyle): String = style.name.lowercase()

internal const val TOA_AKKHA_ATTACK_PARAMETERS = "toa.akkha-ordinary"
internal const val TOA_AKKHA_COMBAT_PHASE = "combat"
internal const val TOA_AKKHA_STYLE_STREAM = "akkha.ordinary-style"
internal const val TOA_AKKHA_STYLE_INPUT = "akkha.style"
