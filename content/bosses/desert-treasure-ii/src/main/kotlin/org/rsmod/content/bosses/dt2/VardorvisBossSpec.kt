package org.rsmod.content.bosses.dt2

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType

/**
 * Native ordinary combat; preserves Dt2Rules' five-tick cadence and launch/impact callbacks. Asset
 * evidence: docs/dt2-capture-validation.md:19620 (retained 3285 animation 10340). Environmental
 * mechanics keep their existing independent deadlines and native action locks.
 */
internal fun vardorvisBossSpec(): BossSpec =
    BossSpec(
        npcTypes = listOf(Dt2Boss.VARDORVIS.normalNpc),
        stats =
            BossStats(attackRate = Dt2Rules.VARDORVIS_NORMAL_ATTACK_TICKS, aggressionRadius = 1),
        abilities =
            mapOf(
                "melee" to
                    Effect.NpcAttack(
                        attack = CombatAttack.NpcMelee(MeleeAttackType.Slash),
                        animation =
                            requireNotNull(
                                RSCM.getReverseMapping(RSCMType.SEQ, Dt2Assets.VARDORVIS_MELEE_ANIM)
                            ),
                        protectionPrayer = ProtectionPrayer.Reduce(75),
                        parameters = VARDORVIS_ATTACK_PARAMETERS,
                        // Preserve the queued context through source death. Native melee impact
                        // processing still rejects a source with zero HP.
                        survivesSourceDeath = true,
                    )
            ),
        phases =
            mapOf("active" to PhaseSpec("active", selector = Selector.Rotation(listOf("melee")))),
        triggers = emptyList(),
    )

internal const val VARDORVIS_ATTACK_PARAMETERS = "vardorvis.normal-impact"
