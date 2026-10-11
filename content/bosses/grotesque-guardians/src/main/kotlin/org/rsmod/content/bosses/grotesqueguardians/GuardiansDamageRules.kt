package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity

/** Native calculation and resolution policies; never advance a phase before accepted damage. */
internal class GuardiansDamageRules
@Inject
constructor(private val instances: InstanceManager, private val deps: BossDeps) {
    fun modify(player: Player, npc: Npc, hit: HitBuilder) {
        val run = instances.guardiansState(player)
        if (run == null || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        val role =
            if (npc === run.dawn) GuardianRole.DAWN
            else if (npc === run.dusk) GuardianRole.DUSK else null
        if (role == null || !GrotesqueGuardiansRules.canDamage(role, run.phase)) {
            hit.damage = 0
            return
        }
        if (role == GuardianRole.DUSK && hit.type != HitType.Melee) {
            hit.damage = 0
            player.mes("Dusk's stone armour resists that attack.")
            return
        }
        if (role == GuardianRole.DAWN && hit.type == HitType.Melee && !isHalberd(player)) {
            hit.damage = 0
            player.mes("Dawn is flying too high for that weapon.")
            return
        }
        hit.damage = resolve(npc, hit.damage)
        val previous = hit.validity
        val encounter = if (npc === run.dawn) run.dawnEncounter else run.duskEncounter
        hit.validity = HitValidity {
            (previous?.isValid() ?: true) &&
                instances.guardiansState(player) === run &&
                deps.encounterRegistry.isCurrent(npc, encounter)
        }
    }

    fun resolve(npc: Npc, damage: Int): Int {
        val run = instances.guardiansState(npc) ?: return 0
        val role =
            when (npc) {
                run.dawn -> GuardianRole.DAWN
                run.dusk -> GuardianRole.DUSK
                else -> return 0
            }
        if (!GrotesqueGuardiansRules.canDamage(role, run.phase)) return 0
        return when (run.phase) {
            GrotesquePhase.DAWN_ONE,
            GrotesquePhase.DUSK_TWO ->
                GrotesqueGuardiansRules.capDamageAtThreshold(npc.hitpoints, damage)
            GrotesquePhase.DAWN_THREE ->
                GrotesqueGuardiansRules.capDawnFinalDamage(npc.hitpoints, damage)
            else -> damage
        }
    }

    private fun isHalberd(player: Player): Boolean {
        val weapon = player.righthand
        val internal =
            weapon?.let { ServerCacheManager.getItem(it.id)?.internalName } ?: return false
        return internal.contains("halberd") || internal.contains("golembane")
    }
}
