package org.rsmod.content.bosses.muspah

import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Native attack selection and phase deadlines; named handlers retain only specialized effects. */
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
        if (form == MuspahForm.FINAL) {
            for (style in listOf(MuspahAttack.CHARGED_MAGIC, MuspahAttack.RANGED)) {
                val ability = "$name-$style"
                abilities[ability] =
                    Effect.Sequence(
                        listOf(
                            requireNotNull(abilities[ability]),
                            Effect.Whenever(
                                Condition.AbilityCountModulo(MUSPAH_FINAL_SPIKE_ATTACKS, 0),
                                Effect.External("muspah.final-spikes"),
                            ),
                        )
                    )
            }
        }
        phases[name] =
            PhaseSpec(
                name,
                lockMovement = form != MuspahForm.MELEE,
                attackRate = rate,
                selector = selector,
                entry =
                    if (form == MuspahForm.SHIELD) "shield-arrival"
                    else if (form != MuspahForm.FINAL) "ordinary-arrival" else null,
            )
    }
    abilities["ordinary-arrival"] = Effect.External("muspah.ordinary-arrival")
    abilities["shield-arrival"] = Effect.External("muspah.shield-arrival")
    abilities["shockwave"] = Effect.External("muspah.shockwave")
    for (form in listOf(MuspahForm.RANGED, MuspahForm.MELEE)) {
        val name = form.name
        phases["spawn-$name"] =
            PhaseSpec("spawn-$name", exitAfter = 4, nextPhase = name, lockMovement = true)
        phases["swap-$name"] =
            PhaseSpec(
                "swap-$name",
                exitAfter = 4,
                nextPhase = name,
                lockMovement = form == MuspahForm.MELEE,
            )
        phases["homing-$name"] =
            requireNotNull(phases[name])
                .copy(
                    name = "homing-$name",
                    entry = null,
                    exitAfter = MUSPAH_HOMING_DURATION,
                    nextPhase = name,
                )
    }
    phases["lightning"] =
        PhaseSpec("lightning", exitAfter = 38, nextPhase = "RANGED", lockMovement = true)
    for (locked in listOf(true, false)) {
        val suffix = if (locked) "locked" else "mobile"
        phases["shockwave-$suffix"] =
            PhaseSpec(
                "shockwave-$suffix",
                exitAfter = 4,
                nextPhase = "shield-arrival-$suffix",
                lockMovement = locked,
            )
        phases["shield-arrival-$suffix"] =
            PhaseSpec(
                "shield-arrival-$suffix",
                entry = "shockwave",
                exitAfter = 1,
                nextPhase = "SHIELD",
                lockMovement = locked,
            )
    }
    for (name in listOf("dying", "respawning", "invalidated")) phases[name] =
        PhaseSpec(name, lockMovement = true)
    return BossSpec(
        MuspahAssets.npcForms,
        BossStats(attackRate = MUSPAH_RANGED_ATTACK_RATE, aggressionRadius = 10),
        abilities,
        phases,
        emptyList(),
    )
}
