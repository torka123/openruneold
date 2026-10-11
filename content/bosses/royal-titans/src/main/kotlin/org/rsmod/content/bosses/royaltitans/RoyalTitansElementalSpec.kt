package org.rsmod.content.bosses.royaltitans

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType

/** Pack placement/pathing is encounter-specific; ordinary melee belongs to the native boss DSL. */
internal fun royalTitansElementalSpec(element: RoyalElement): BossSpec {
    val fire = element == RoyalElement.FIRE
    val npcId = if (fire) RoyalTitansAssets.FIRE_ELEMENTAL else RoyalTitansAssets.ICE_ELEMENTAL
    val npcType = requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, npcId))
    val sequence = requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, if (fire) 11947 else 11950))
    return BossSpec(
        npcTypes = listOf(npcType),
        stats = BossStats(attackRate = 4, aggressionRadius = 1),
        abilities =
            mapOf(
                "melee" to
                    Effect.NpcAttack(
                        attack =
                            CombatAttack.NpcMelee(
                                if (fire) MeleeAttackType.Slash else MeleeAttackType.Stab
                            ),
                        maxHit = 4,
                        animation = sequence,
                        impactDelay = 1,
                        // Retain the characterized fired hit while the custom death animation
                        // finishes.
                        // Native deletion, instance and participant scope retirement still
                        // invalidate it.
                        survivesSourceDeath = true,
                    )
            ),
        phases =
            mapOf("active" to PhaseSpec("active", selector = Selector.Rotation(listOf("melee")))),
        triggers = emptyList(),
    )
}
