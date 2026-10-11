package org.rsmod.content.bosses.dagannothkings

import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Existing public-lair attacks expressed through the shared native interpreter. */
internal fun dagannothKingSpec(type: String): BossSpec {
    val attack =
        when (type) {
            DagannothKingsAssets.PRIME ->
                Effect.NpcAttack(
                    attack = CombatAttack.NpcMagic(DagannothKingsRules.PRIME_MAX_HIT),
                    maxHit = DagannothKingsRules.PRIME_MAX_HIT,
                    animation = DagannothKingsAssets.PRIME_ATTACK_ANIMATION,
                    projectile =
                        NpcProjectile(
                            DagannothKingsAssets.PRIME_PROJECTILE,
                            DagannothKingsAssets.primeProjectileType,
                        ),
                    parameters = DKS_PRIME_PARAMETERS,
                    survivesSourceDeath = true,
                )
            DagannothKingsAssets.SUPREME ->
                Effect.NpcAttack(
                    attack = CombatAttack.NpcRanged(RangedAttackType.Standard),
                    maxHit = DagannothKingsRules.SUPREME_MAX_HIT,
                    animation = DagannothKingsAssets.SUPREME_ATTACK_ANIMATION,
                    projectile =
                        NpcProjectile(
                            DagannothKingsAssets.SUPREME_PROJECTILE,
                            DagannothKingsAssets.supremeProjectileType,
                        ),
                    survivesSourceDeath = true,
                )
            DagannothKingsAssets.REX ->
                Effect.NpcAttack(
                    attack = CombatAttack.NpcMelee(MeleeAttackType.Slash),
                    maxHit = DagannothKingsRules.REX_MAX_HIT,
                    animation = DagannothKingsAssets.REX_ATTACK_ANIMATION,
                )
            else -> error("Unknown Dagannoth King: $type")
        }
    return BossSpec(
        npcTypes = listOf(type),
        stats = BossStats(attackRate = DagannothKingsRules.ATTACK_RATE_TICKS),
        abilities = mapOf("normal" to attack),
        phases =
            mapOf("combat" to PhaseSpec("combat", selector = Selector.Rotation(listOf("normal")))),
        triggers = emptyList(),
    )
}

internal const val DKS_PRIME_PARAMETERS = "dagannoth-kings.prime-splash"
