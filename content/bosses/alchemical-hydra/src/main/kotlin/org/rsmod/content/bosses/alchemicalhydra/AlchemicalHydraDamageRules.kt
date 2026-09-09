package org.rsmod.content.bosses.alchemicalhydra

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitValidity

/**
 * Native hit policy: calculation, impact-time floors and accepted-damage observation are separate.
 */
internal class AlchemicalHydraDamageRules
@Inject
constructor(
    private val instances: InstanceManager,
    private val deps: BossDeps,
    private val transforms: AlchemicalHydraTransforms,
) {
    fun modify(player: Player, npc: Npc, hit: HitBuilder) {
        val run = instances.hydraState(player)
        if (
            run == null ||
                run.boss !== npc ||
                !npc.isSpawnOwnedBy(player) ||
                run.state != HydraState.ACTIVE
        ) {
            hit.damage = 0
            return
        }
        hit.damage =
            AlchemicalHydraRules.incomingDamage(
                hit.damage,
                run.weakened || run.phase == HydraPhase.FINAL,
            )
        hit.damage = AlchemicalHydraRules.capAtThreshold(run.phase, npc.hitpoints, hit.damage)
        val encounter = run.encounter
        val previous = hit.validity
        hit.validity = HitValidity {
            (previous?.isValid() ?: true) &&
                instances.hydraState(player) === run &&
                deps.encounterRegistry.isCurrent(npc, encounter)
        }
    }

    fun resolve(npc: Npc, damage: Int): Int {
        val run = instances.hydraState(npc) ?: return 0
        if (run.state != HydraState.ACTIVE) return 0
        // Earlier projectiles were calculated against earlier HP. Enforce the floor at impact too.
        return AlchemicalHydraRules.capAtThreshold(run.phase, npc.hitpoints, damage)
    }

    fun impact(npc: Npc, acceptedDamage: Int) {
        val run = instances.hydraState(npc) ?: return
        if (
            acceptedDamage > 0 &&
                run.state == HydraState.ACTIVE &&
                AlchemicalHydraRules.reachesThreshold(run.phase, npc.hitpoints)
        ) {
            transforms.begin(run)
        }
    }
}
