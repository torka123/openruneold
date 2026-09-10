package org.rsmod.content.bosses.cerberus

import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Preserves the characterized draft rotation; normal attacks execute through NvPCombat. */
internal fun cerberusCombatSpec(): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    fun sequence(vararg effects: Effect) = Effect.Sequence(effects.toList())
    fun shot(style: CerberusAttackStyle, head: CerberusHead) =
        Effect.NpcAttack(
            attack =
                when (style) {
                    CerberusAttackStyle.MAGIC -> CombatAttack.NpcMagic(CerberusRules.BASIC_MAX_HIT)
                    CerberusAttackStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                    CerberusAttackStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Stab)
                },
            maxHit = CerberusRules.BASIC_MAX_HIT,
            parameters = "cerberus.head-attack",
            parameterData = CerberusHeadAttack(style, head),
        )
    fun outer(choice: (CerberusHead) -> Effect) =
        Effect.Choose(
            Selector.WeightedRandom(WeightedRef("left"), WeightedRef("right")),
            linkedMapOf("left" to choice(CerberusHead.LEFT), "right" to choice(CerberusHead.RIGHT)),
        )
    val adjacent =
        Condition.And(Condition.WithinMeleeRange, Condition.Not(Condition.PlayerEnterRange(0)))
    for (style in CerberusAttackStyle.entries) {
        abilities[style.name.lowercase()] =
            sequence(
                Effect.SetAttackRate(CerberusRules.ATTACK_RATE_TICKS),
                Effect.External("cerberus.attack-presentation", style),
                if (style == CerberusAttackStyle.MELEE) shot(style, CerberusHead.CENTRE)
                else outer { shot(style, it) },
            )
    }
    abilities["basic"] =
        Effect.Choose(
            Selector.WeightedRandom(
                WeightedRef("magic"),
                WeightedRef("ranged"),
                WeightedRef("melee", requires = adjacent),
            ),
            linkedMapOf(
                "magic" to Effect.Run("magic"),
                "ranged" to Effect.Run("ranged"),
                "melee" to Effect.Run("melee"),
            ),
        )
    abilities["triple"] =
        sequence(
            Effect.External("cerberus.attack-presentation", CerberusAttack.TRIPLE),
            // Native coroutine owns the volley; each component opens exactly two ticks apart.
            Effect.SetAttackRate(CerberusRules.TRIPLE_COMPONENT_GAP_TICKS),
            outer { first ->
                sequence(
                    shot(CerberusAttackStyle.MAGIC, first),
                    Effect.Delay(CerberusRules.TRIPLE_COMPONENT_GAP_TICKS),
                    shot(
                        CerberusAttackStyle.RANGED,
                        if (first == CerberusHead.LEFT) CerberusHead.RIGHT else CerberusHead.LEFT,
                    ),
                    Effect.Delay(CerberusRules.TRIPLE_COMPONENT_GAP_TICKS),
                    shot(CerberusAttackStyle.MELEE, CerberusHead.CENTRE),
                )
            },
            Effect.SetAttackRate(CerberusRules.TRIPLE_RECOVERY_TICKS),
        )
    for (special in listOf("souls", "lava")) {
        abilities[special] =
            sequence(
                Effect.SetAttackRate(
                    if (special == "souls") CerberusRules.SOUL_RECOVERY_TICKS
                    else CerberusRules.LAVA_RECOVERY_TICKS
                ),
                Effect.External("cerberus.$special"),
            )
        // A skipped Souls opportunity does not fall through to lava on a coincident turn.
        abilities["$special-candidate"] =
            Effect.Choose(
                Selector.WeightedRandom(WeightedRef("skip", 1), WeightedRef("special", 9)),
                linkedMapOf("skip" to Effect.Run("basic"), "special" to Effect.Run(special)),
            )
    }
    return BossSpec(
        listOf("npc.cerberus_sitting", "npc.cerberus_attacking", "npc.cerberus_resetting"),
        BossStats(CerberusRules.ATTACK_RATE_TICKS, CerberusRules.ATTACK_RANGE),
        abilities,
        linkedMapOf(
            "sitting" to PhaseSpec("sitting", lockMovement = true),
            "standing" to
                PhaseSpec(
                    "standing",
                    lockMovement = true,
                    exitAfter = CerberusRules.STAND_TRANSITION_TICKS,
                    nextPhase = "standing-ready",
                ),
            // The native clock owns the deadline; LateCycle preserves the original presentation
            // stage.
            "standing-ready" to PhaseSpec("standing-ready", lockMovement = true),
            "resetting" to
                PhaseSpec(
                    "resetting",
                    exitAfter = CerberusRules.RESET_TRANSITION_TICKS,
                    nextPhase = "reset-travel",
                ),
            "reset-travel" to
                PhaseSpec(
                    "reset-travel",
                    exitAfter =
                        CerberusRules.RESET_WALK_TIMEOUT_TICKS -
                            CerberusRules.RESET_TRANSITION_TICKS,
                    nextPhase = "reset-timeout",
                ),
            "reset-timeout" to PhaseSpec("reset-timeout"),
            "reset-sit" to
                PhaseSpec(
                    "reset-sit",
                    lockMovement = true,
                    exitAfter = CerberusRules.RESET_TRANSITION_TICKS,
                    nextPhase = "reset-ready",
                ),
            "reset-ready" to PhaseSpec("reset-ready", lockMovement = true),
            "dying" to PhaseSpec("dying", lockMovement = true),
            "respawning" to PhaseSpec("respawning", lockMovement = true),
            "invalidated" to PhaseSpec("invalidated", lockMovement = true),
            "complete" to PhaseSpec("complete", lockMovement = true),
            "active" to
                PhaseSpec(
                    "active",
                    lockMovement = false,
                    selector =
                        Selector.Conditional(
                            listOf(
                                Condition.AbilityCountModulo(CerberusRules.TRIPLE_INTERVAL, 0) to
                                    "triple",
                                Condition.And(
                                    Condition.HpBelow(400.0 / 600),
                                    Condition.AbilityCountModulo(CerberusRules.SOUL_INTERVAL, 6),
                                ) to "souls-candidate",
                                Condition.And(
                                    Condition.HpBelow(200.0 / 600),
                                    Condition.AbilityCountModulo(CerberusRules.LAVA_INTERVAL, 4),
                                ) to "lava-candidate",
                            ),
                            "basic",
                        ),
                ),
        ),
        emptyList(),
    )
}

internal data class CerberusHeadAttack(val style: CerberusAttackStyle, val head: CerberusHead)
