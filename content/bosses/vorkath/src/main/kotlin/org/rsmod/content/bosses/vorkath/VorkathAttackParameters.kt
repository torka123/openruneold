package org.rsmod.content.bosses.vorkath

import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Launch-time snapshots only; the boss interpreter and NvPCombat launch and queue the attack. */
internal object VorkathAttackParameters {
    fun resolve(npc: Npc, target: Player, attack: VorkathStandardAttack): NpcAttackOverride {
        require(attack != VorkathStandardAttack.FIREBALL)
        val projectile = VorkathProjectiles.standard(attack)?.build(npc, target.coords, target)
        val dragonfire =
            attack in
                setOf(
                    VorkathStandardAttack.DRAGONFIRE,
                    VorkathStandardAttack.VENOM_DRAGONFIRE,
                    VorkathStandardAttack.PRAYER_DRAGONFIRE,
                )
        val protection = VorkathDragonfire.snapshot(target)
        val maximum =
            when (attack) {
                VorkathStandardAttack.MELEE,
                VorkathStandardAttack.RANGED -> 32
                VorkathStandardAttack.MAGIC -> 30
                else -> protection.maximum
            }
        return NpcAttackOverride(
            attackAnimation =
                if (attack == VorkathStandardAttack.MELEE) VorkathAssets.MELEE_ANIM
                else VorkathAssets.RANGED_ANIM,
            maxHit = maximum,
            inaccurateMaxHit = if (dragonfire) protection.resistedMaximum else null,
            projectileFactory = projectile?.let { exact -> { _, _ -> exact } },
            impactDelay = (projectile?.let { it.endTime / 30 } ?: 0) + 1,
            damageTransform =
                if (dragonfire) { rolled -> protection.damage(rolled, attack) } else null,
            hitModifier =
                if (dragonfire) NpcBypassProtectionPrayerPlayerHitModifier
                else StandardPlayerHitModifier,
            onAttack = {
                VorkathProjectiles.impact(attack)?.let {
                    target.spotanim(it, delay = projectile?.endTime ?: 0, height = 124)
                }
            },
            onImpact = { _, _ ->
                if (attack == VorkathStandardAttack.VENOM_DRAGONFIRE) PlayerVenom.tryVenom(target)
                if (attack == VorkathStandardAttack.PRAYER_DRAGONFIRE) target.disablePrayers()
            },
        )
    }
}
