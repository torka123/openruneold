package org.rsmod.content.bosses.dt2

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Primary native attacks; the retained stage-dependent followup shots remain in the adapter. */
internal fun whispererBossSpec(): BossSpec {
    val abilities =
        Dt2AttackStyle.entries.associate { style ->
            style.name to
                Effect.NpcAttack(
                    attack =
                        when (style) {
                            Dt2AttackStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                            Dt2AttackStyle.MAGIC -> CombatAttack.NpcMagic(36)
                            Dt2AttackStyle.RANGED ->
                                CombatAttack.NpcRanged(RangedAttackType.Standard)
                        },
                    animation =
                        requireNotNull(
                            RSCM.getReverseMapping(
                                RSCMType.SEQ,
                                when (style) {
                                    Dt2AttackStyle.MELEE -> 10234
                                    Dt2AttackStyle.MAGIC -> 10243
                                    Dt2AttackStyle.RANGED -> 10237
                                },
                            )
                        ),
                    impactDelay = if (style == Dt2AttackStyle.MELEE) null else 2,
                    parameters = WHISPERER_ATTACK_PARAMETERS,
                    parameterData = style,
                    survivesSourceDeath = true,
                )
        }
    val phases =
        linkedMapOf(WHISPERER_INACTIVE to PhaseSpec(WHISPERER_INACTIVE, lockMovement = true))
    phases[WHISPERER_MELEE] =
        PhaseSpec(
            WHISPERER_MELEE,
            attackRate = 2,
            selector = Selector.Rotation(listOf(Dt2AttackStyle.MELEE.name)),
        )
    for (offset in 0..1) {
        val styles = List(2) { Dt2Rules.whispererVolley(offset + it, 0, false).styles.first().name }
        val key = whispererDistancePhase(offset, false)
        phases[key] =
            PhaseSpec(
                key,
                lockMovement = true,
                attackRate = 10,
                selector = Selector.Rotation(styles),
            )
    }
    for (offset in 0..3) {
        val styles = List(4) { Dt2Rules.whispererVolley(offset + it, 0, true).styles.first().name }
        val key = whispererDistancePhase(offset, true)
        phases[key] =
            PhaseSpec(
                key,
                lockMovement = true,
                attackRate = 2,
                selector = Selector.Rotation(styles),
            )
    }
    return BossSpec(
        npcTypes =
            listOf(12203, 12204, 12205).map {
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it))
            },
        stats = BossStats(attackRate = 10, aggressionRadius = 12),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

internal fun whispererDistancePhase(counter: Int, enraged: Boolean): String =
    if (enraged) "enraged.${Math.floorMod(counter, 4)}" else "distance.${Math.floorMod(counter, 2)}"

internal const val WHISPERER_INACTIVE = "inactive"
internal const val WHISPERER_MELEE = "melee"
internal const val WHISPERER_ATTACK_PARAMETERS = "whisperer.primary-impact"
