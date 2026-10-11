package org.rsmod.content.bosses.vorkath

import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Ordinary selection/cadence belongs to BossEncounter; arena mechanics remain named extensions. */
internal fun vorkathCombatSpec(): BossSpec =
    boss(VorkathAssets.ACTIVE, VorkathAssets.SLEEPING) {
        stats(attackRate = VORKATH_ATTACK_RATE, aggressionRadius = 32)
        for (attack in VorkathStandardAttack.entries) {
            val effect =
                if (attack == VorkathStandardAttack.FIREBALL) {
                    Effect.External("vorkath.fireball")
                } else {
                    Effect.NpcAttack(
                        attack =
                            when (attack) {
                                VorkathStandardAttack.MELEE ->
                                    CombatAttack.NpcMelee(MeleeAttackType.Crush)
                                VorkathStandardAttack.RANGED ->
                                    CombatAttack.NpcRanged(RangedAttackType.Standard)
                                else -> CombatAttack.NpcMagic(30)
                            },
                        parameters = "vorkath.normal",
                        parameterData = attack,
                    )
                }
            ability(attack.name, effect)
        }
        ability("special", Effect.External("vorkath.special"))
        phase("sleeping", lockMovement = true) { selector = Selector.Rotation(emptyList()) }
        phase("active", lockMovement = true) {
            selector =
                Selector.WeightedRandom(
                    VorkathRules.standardWeights(true).map { (attack, weight) ->
                        WeightedRef(
                            attack.name,
                            weight,
                            requires =
                                if (attack == VorkathStandardAttack.MELEE)
                                    Condition.WithinMeleeRange and
                                        Condition.Not(Condition.PlayerEnterRange(0))
                                else Condition.Always,
                        )
                    }
                )
            forceEveryAttacks(VORKATH_STANDARD_ATTACKS, VORKATH_STANDARD_ATTACKS, "special")
        }
        // External arena handlers release the action lock and return to active after their real
        // finish.
        phase("acid", lockMovement = true) { selector = Selector.Rotation(emptyList()) }
        phase("spawn", lockMovement = true) { selector = Selector.Rotation(emptyList()) }
        for (state in listOf("entering", "awakening", "dying", "lootable", "resetting", "ended")) {
            phase(state, lockMovement = true) { selector = Selector.Rotation(emptyList()) }
        }
    }
