package org.rsmod.content.bosses.grotesqueguardians

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcHitParameters
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitValidity

/** Pure launch snapshots and damage distributions; no attack selection or hit scheduling. */
internal class GrotesqueGuardiansAttackParameters
@Inject
constructor(private val instances: InstanceManager) {
    fun snapshot(npc: Npc, target: Player, style: GuardianAttackStyle): NpcAttackOverride {
        val state = instances.guardiansState(target)
        val validity = HitValidity {
            state != null &&
                instances.guardiansState(target) === state &&
                state.phase !in setOf(GrotesquePhase.DYING, GrotesquePhase.RESPAWNING)
        }
        val protected =
            target.vars[
                    if (style == GuardianAttackStyle.MELEE) "varbit.prayer_protectfrommelee"
                    else "varbit.prayer_protectfrommissiles"] == 1
        if (
            npc.visType.id == GrotesqueAssets.DUSK_PHASE_FOUR && style == GuardianAttackStyle.RANGED
        ) {
            return NpcAttackOverride(
                hitValidity = validity,
                hitModifier = NpcBypassProtectionPrayerPlayerHitModifier,
                hits =
                    listOf(
                        NpcHitParameters(
                            damageRoll = { _, accurate, _ ->
                                GrotesqueGuardiansRules.phaseFourRangedFirstHit(accurate, protected)
                            }
                        ),
                        NpcHitParameters(
                            impactOffset = 1,
                            damageRoll = { maximum, accurate, random ->
                                val first = if (accurate) random.of(maximum + 1) else 0
                                GrotesqueGuardiansRules.phaseFourRangedSecondHit(
                                    accurate,
                                    first,
                                    random.of(first + 1),
                                )
                            },
                        ),
                    ),
            )
        }
        return NpcAttackOverride(
            hitValidity = validity,
            hitModifier = NpcCalculatedProtectionPrayerPlayerHitModifier(protected, 80),
            onAttack = { if (style == GuardianAttackStyle.MELEE) npc.facePlayer(target) },
        )
    }
}
