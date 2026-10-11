package org.rsmod.content.bosses.corporealbeast

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType

/** Declarative attack distribution; room hazards and core movement are named external mechanics. */
internal fun corporealBeastSpec(): BossSpec {
    fun seq(id: Int) = RSCM.getReverseMapping(RSCMType.SEQ, id)
    fun spot(id: Int) = RSCM.getReverseMapping(RSCMType.SPOTANIM, id)
    // Preserve the baseline random.of(Int.MAX_VALUE) % 8 distribution, including its tiny bias.
    val denominator = CorporealBeastRules.SHORT_ATTACK_ROLL_DENOMINATOR
    val shortWeight = (Int.MAX_VALUE.toLong() + denominator - 1) / denominator
    val rate =
        Effect.Choose(
            Selector.WeightedRandom(
                WeightedRef("short", shortWeight.toInt()),
                WeightedRef("normal", (Int.MAX_VALUE - shortWeight).toInt()),
            ),
            mapOf(
                "short" to Effect.SetAttackRate(CorporealBeastRules.SHORT_ATTACK_RATE_TICKS),
                "normal" to Effect.SetAttackRate(CorporealBeastRules.NORMAL_ATTACK_RATE_TICKS),
            ),
        )
    fun normal(
        attack: CombatAttack.NpcAttack,
        maximum: Int,
        animation: Int,
        projectile: Int? = null,
        drain: Boolean = false,
    ) =
        Effect.NpcAttack(
            attack,
            maxHit = maximum,
            animation = seq(animation),
            projectile =
                projectile?.let {
                    NpcProjectile(spot(it), CorporealBeastAssets.TRACKING_PROJECTILE)
                },
            protectionPrayer =
                ProtectionPrayer.Reduce(if (attack is CombatAttack.NpcMelee) 100 else 33),
            parameters = "corp.normal",
            parameterData = drain,
        )
    val melee =
        Effect.Choose(
            Selector.WeightedRandom(WeightedRef("left"), WeightedRef("right")),
            mapOf(
                "left" to
                    normal(
                        CombatAttack.NpcMelee(MeleeAttackType.Crush),
                        CorporealBeastRules.MELEE_MAX_HIT,
                        CorporealBeastAssets.MELEE_LEFT_ANIMATION,
                    ),
                "right" to
                    normal(
                        CombatAttack.NpcMelee(MeleeAttackType.Crush),
                        CorporealBeastRules.MELEE_MAX_HIT,
                        CorporealBeastAssets.MELEE_RIGHT_ANIMATION,
                    ),
            ),
        )
    val abilities =
        mapOf(
                "melee" to melee,
                "magic" to
                    normal(
                        CombatAttack.NpcMagic(CorporealBeastRules.MAGIC_MAX_HIT),
                        CorporealBeastRules.MAGIC_MAX_HIT,
                        CorporealBeastAssets.MAGIC_ANIMATION,
                        CorporealBeastAssets.MAGIC_PROJECTILE_SPOTANIM,
                    ),
                "drain" to
                    normal(
                        CombatAttack.NpcMagic(CorporealBeastRules.MAGIC_MAX_HIT),
                        CorporealBeastRules.MAGIC_MAX_HIT,
                        CorporealBeastAssets.DRAIN_ANIMATION,
                        CorporealBeastAssets.DRAIN_PROJECTILE_SPOTANIM,
                        drain = true,
                    ),
                "area" to Effect.External("corp.area"),
            )
            .mapValues { (_, attack) -> Effect.Sequence(listOf(rate, attack)) }
    return BossSpec(
        listOf("npc.corp_beast"),
        BossStats(attackRate = 4),
        abilities,
        mapOf(
            "main" to
                PhaseSpec(
                    "main",
                    selector =
                        Selector.WeightedRandom(
                            WeightedRef("melee", 2, requires = Condition.WithinMeleeRange),
                            WeightedRef("magic"),
                            WeightedRef("drain"),
                            WeightedRef("area"),
                        ),
                )
        ),
        emptyList(),
    )
}
