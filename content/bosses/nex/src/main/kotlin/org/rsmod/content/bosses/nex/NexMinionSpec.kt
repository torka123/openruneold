package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack

/**
 * Architecture-only migration of the characterized draft. Presentation and maxima below are NOT
 * OSRS-certified; see M11/M17 and the minion capture discrepancies in NEX_OSRS_AUDIT.md. Ordinary
 * selection, cadence, accuracy, prayer, projectiles and hits have one native DSL owner.
 */
internal fun nexMinionSpec(reaver: Boolean): BossSpec {
    val maximum = if (reaver) 18 else 20
    val attack =
        Effect.NpcAttack(
            attack = CombatAttack.NpcMagic(maximum),
            maxHit = maximum,
            animation = RSCM.getReverseMapping(RSCMType.SEQ, 811),
            projectile =
                if (reaver) null
                else
                    NpcProjectile(
                        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, 1999)),
                        requireNotNull(
                            ServerCacheManager.getProjectile("projanim.magic_spell".asRSCM())
                        ),
                    ),
            protectionPrayer = ProtectionPrayer.Reduce(50),
            parameters = NEX_MINION_TARGET_PARAMETERS,
            // Preserve native fired-magic lifetime through death, not deletion or room teardown.
            survivesSourceDeath = true,
        )
    return BossSpec(
        npcTypes =
            (if (reaver) listOf(11294) else (11283..11286).toList()).map {
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it))
            },
        stats = BossStats(attackRate = 5, aggressionRadius = 10),
        abilities = mapOf("magic" to attack),
        phases =
            linkedMapOf(
                "active" to
                    PhaseSpec(
                        "active",
                        lockMovement = !reaver,
                        selector = Selector.Rotation(listOf("magic")),
                    )
            ),
        triggers = emptyList(),
    )
}

internal const val NEX_MINION_TARGET_PARAMETERS = "nex.minion-target"
