package org.rsmod.content.skills.magic.spell.attacks.arceuus

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PvPPlayerImpactHook
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.npc.hit.NpcAttackLaunchHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpact

/** Bridges Arceuus state into the authoritative player tick, NPC damage, and death paths. */
@Singleton
internal class ArceuusSpellLifecycle
@Inject
constructor(private val state: ArceuusUtilityState) :
    PlayerPostTickHook,
    NpcAttackValidateHook,
    PvPPlayerImpactHook,
    NpcAttackLaunchHook,
    NpcDamageContributor {
    override fun onPostTick(player: Player) = state.tick(player)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        state.validateReanimatedAttack(player, npc)

    override fun onPlayerImpact(attacker: Player, target: Player, impact: HitImpact) {
        state.tryApplyCorruption(attacker, target, impact.damage)
    }

    override fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int) {
        state.onNpcDamage(source, npc, damage)
    }

    override fun onAttackLaunched(player: Player, npc: Npc) {
        state.recordTarget(player, npc)
    }
}
