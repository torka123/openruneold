package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitValidity

/** Launch snapshots/impact policies. Does not select, launch or schedule an ordinary attack. */
internal class MuspahAttackParameters
@Inject
constructor(
    private val encounters: MuspahEncounterManager,
    private val corruption: CombatCorruption,
) {
    fun resolve(npc: Npc, target: Player, style: MuspahAttack): NpcAttackOverride {
        val run = requireNotNull(encounters.combatRun(npc, target))
        val validity = HitValidity { encounters.validHit(npc, target) }
        if (style == MuspahAttack.MELEE) {
            val moved = npc.coords != npc.previousCoords
            val protected = target.vars["varbit.prayer_protectfrommelee"] == 1
            val stationary = if (moved) 0 else run.stationaryMeleeAttacks + 1
            val maximum =
                if (MuspahRules.meleeStepBackBlocks(moved, protected)) 0
                else MuspahRules.meleeMaximum(stationary)
            return NpcAttackOverride(
                maxHit = maximum,
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                hitValidity = validity,
                onAttack = { run.stationaryMeleeAttacks = stationary },
            )
        }
        val magic = style == MuspahAttack.CHARGED_MAGIC
        val protected =
            target.vars[
                    if (magic) "varbit.prayer_protectfrommagic"
                    else "varbit.prayer_protectfrommissiles"] == 1
        return NpcAttackOverride(
            projectile =
                NpcProjectile(
                    if (magic) MuspahAssets.MAGIC_TRAVEL else MuspahAssets.RANGED_TRAVEL,
                    requireNotNull(
                        ServerCacheManager.getProjectile(
                            MuspahAssets.PROJECTILE_CONTROLLER.asRSCM(RSCMType.PROJANIM)
                        )
                    ),
                ),
            hitModifier =
                NpcCalculatedProtectionPrayerPlayerHitModifier(protected, if (magic) 100 else 90),
            hitValidity = validity,
            onAttack = { encounters.onRangedAttack(run) },
            onImpact = { _, impact ->
                target.spotanim(
                    if (magic) MuspahAssets.MAGIC_IMPACT else MuspahAssets.RANGED_IMPACT
                )
                if (magic && !protected && impact.damage > 0) corruption.apply(target)
                if (run.state == MuspahState.PRAYER_SHIELD && impact.damage > 0)
                    npc.heal(MuspahRules.soulSplitHeal(impact.damage))
            },
        )
    }
}
