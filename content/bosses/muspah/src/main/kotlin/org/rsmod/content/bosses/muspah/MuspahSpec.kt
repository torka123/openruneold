package org.rsmod.content.bosses.muspah

import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Ordinary combat only; spatial phase migration is the next strangler step. */
internal fun muspahCombatSpec(): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    val phases = linkedMapOf("dormant" to PhaseSpec("dormant", lockMovement = true))
    for (form in listOf(MuspahForm.MELEE, MuspahForm.RANGED, MuspahForm.SHIELD, MuspahForm.FINAL)) {
        val name = form.name
        val reinforced = form == MuspahForm.SHIELD || form == MuspahForm.FINAL
        val rate =
            when (form) {
                MuspahForm.MELEE -> MUSPAH_MELEE_ATTACK_RATE
                MuspahForm.RANGED -> MUSPAH_RANGED_ATTACK_RATE
                MuspahForm.SHIELD -> MUSPAH_SHIELD_ATTACK_RATE
                else -> MUSPAH_FINAL_ATTACK_RATE
            }
        val selector =
            if (form == MuspahForm.MELEE) {
                abilities["melee"] =
                    Effect.NpcAttack(
                        attack = CombatAttack.NpcMelee(MeleeAttackType.Crush),
                        animation = MuspahAssets.ATTACK_MELEE,
                        parameters = "muspah.attack",
                        parameterData = MuspahAttack.MELEE,
                    )
                Selector.Rotation(listOf("melee"))
            } else {
                for (magic in listOf(true, false)) {
                    val style = if (magic) MuspahAttack.CHARGED_MAGIC else MuspahAttack.RANGED
                    val maximum =
                        if (magic) {
                            if (reinforced) 79 else 72
                        } else {
                            if (reinforced) 72 else 61
                        }
                    abilities["$name-$style"] =
                        Effect.NpcAttack(
                            attack =
                                if (magic) CombatAttack.NpcMagic(maximum)
                                else CombatAttack.NpcRanged(RangedAttackType.Standard),
                            maxHit = maximum,
                            animation =
                                if (magic) MuspahAssets.ATTACK_MAGIC
                                else MuspahAssets.ATTACK_RANGED,
                            impactDelay = if (magic) 3 else null,
                            parameters = "muspah.attack",
                            parameterData = style,
                        )
                }
                Selector.WeightedRandom(
                    WeightedRef("$name-${MuspahAttack.CHARGED_MAGIC}", 1),
                    WeightedRef("$name-${MuspahAttack.RANGED}", if (reinforced) 2 else 4),
                    noRepeatBias = 0.0,
                )
            }
        phases[name] =
            PhaseSpec(
                name,
                lockMovement = form != MuspahForm.MELEE,
                attackRate = rate,
                selector = selector,
            )
    }
    return BossSpec(
        MuspahAssets.npcForms,
        BossStats(attackRate = MUSPAH_RANGED_ATTACK_RATE, aggressionRadius = 10),
        abilities,
        phases,
        emptyList(),
    )
}
