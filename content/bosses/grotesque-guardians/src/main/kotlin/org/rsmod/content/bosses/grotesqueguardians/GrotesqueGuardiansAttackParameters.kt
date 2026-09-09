package org.rsmod.content.bosses.grotesqueguardians

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcHitParameters
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Pure launch snapshots and damage distributions; no attack selection or hit scheduling. */
internal class GrotesqueGuardiansAttackParameters @Inject constructor() {
    fun snapshot(npc: Npc, target: Player, style: GuardianAttackStyle): NpcAttackOverride {
        val protected =
            target.vars[
                    if (style == GuardianAttackStyle.MELEE) "varbit.prayer_protectfrommelee"
                    else "varbit.prayer_protectfrommissiles"] == 1
        if (
            npc.visType.id == GrotesqueAssets.DUSK_PHASE_FOUR && style == GuardianAttackStyle.RANGED
        ) {
            return NpcAttackOverride(
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
            hitModifier = NpcCalculatedProtectionPrayerPlayerHitModifier(protected, 80),
            onAttack = { if (style == GuardianAttackStyle.MELEE) npc.facePlayer(target) },
        )
    }
}
