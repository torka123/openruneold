package org.rsmod.content.bosses.alchemicalhydra

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitValidity

/** Snapshot parameters only; the DSL and NvPCombat launch, roll and schedule every normal hit. */
internal class AlchemicalHydraAttackParameters
@Inject
constructor(private val instances: InstanceManager) {
    fun snapshot(npc: Npc, target: Player, style: HydraAttackStyle): NpcAttackOverride {
        val run = requireNotNull(instances.hydraState(target))
        val weakened = run.weakened || run.phase == HydraPhase.FINAL
        val protectedAtLaunch =
            target.vars[
                    if (style == HydraAttackStyle.MAGIC) "varbit.prayer_protectfrommagic"
                    else "varbit.prayer_protectfrommissiles"] == 1
        return NpcAttackOverride(
            maxHit =
                AlchemicalHydraRules.empoweredDamage(
                    AlchemicalHydraRules.basicMaxHit(run.phase, weakened),
                    run.wrongVentStacks,
                ),
            hitCount = AlchemicalHydraRules.basicHitCount(run.phase, weakened),
            hitModifier =
                NpcCalculatedProtectionPrayerPlayerHitModifier(
                    protectedAtCalculation = protectedAtLaunch,
                    reductionPercent = 100,
                ),
            onAttack = {
                npc.facePlayer(target)
                run.lastBasicStyle = style
            },
            hitValidity =
                HitValidity {
                    instances.hydraState(target) === run &&
                        instances.sessionForPlayer(target) === run.session &&
                        target.isSlotAssigned &&
                        target.hitpoints > 0 &&
                        npc.isSlotAssigned
                },
        )
    }
}
