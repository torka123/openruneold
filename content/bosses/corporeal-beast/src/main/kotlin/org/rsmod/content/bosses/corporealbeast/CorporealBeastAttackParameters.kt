package org.rsmod.content.bosses.corporealbeast

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.npc.heal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Native impact-time prayer and drain/heal callback; never selects, launches or schedules attacks.
 */
internal class CorporealBeastAttackParameters @Inject constructor(private val random: GameRandom) {
    fun resolve(npc: Npc, target: Player, drain: Boolean): NpcAttackOverride =
        NpcAttackOverride(
            applyModifierOnImpact = true,
            onImpact =
                if (drain)
                    { _, impact ->
                        if (impact.damage > 0) {
                            val stat = if (random.of(2) == 0) "stat.magic" else "stat.prayer"
                            target.statSub(stat, constant = random.of(1..2), percent = 0)
                            npc.heal(impact.damage / 2, showHitsplat = false)
                        }
                    }
                else null,
        )
}
