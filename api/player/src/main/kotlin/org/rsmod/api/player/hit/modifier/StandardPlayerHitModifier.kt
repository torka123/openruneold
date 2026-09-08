package org.rsmod.api.player.hit.modifier

import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hit.PlayerAbsorption
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

public object StandardPlayerHitModifier : PlayerHitModifier {
    override fun HitBuilder.modify(target: Player): Unit =
        applyStandardPlayerHitModifier(target, applyProtectionPrayer = true)
}

internal fun HitBuilder.applyStandardPlayerHitModifier(
    target: Player,
    applyProtectionPrayer: Boolean,
    applyWard: Boolean = true,
) {
    if (target.adminGodMode) {
        damage = 0
        return
    }

    PowerOfDeathMeleeProtection.invalidateIfUnequipped(player = target, incomingDamage = damage)
    if (VestaSpearMeleeImmunity.blocks(target, type)) {
        damage = 0
        return
    }

    if (applyProtectionPrayer) {
        val protectionPrayer =
            when (type) {
                HitType.Typeless -> false
                HitType.Melee -> target.vars["varbit.prayer_protectfrommelee"] == 1
                HitType.Ranged -> target.vars["varbit.prayer_protectfrommissiles"] == 1
                HitType.Magic -> target.vars["varbit.prayer_protectfrommagic"] == 1
            }

        if (protectionPrayer) {
            val reduction = if (isFromPlayer) 40 else 100
            damage = (damage * (100 - reduction)) / 100
        }
    }

    damage =
        PowerOfDeathMeleeProtection.reduceMeleeDamage(
            player = target,
            incomingDamage = damage,
            type = type,
        )

    if (isFromNpc) {
        damage = PlayerAbsorption.absorb(player = target, incomingDamage = damage)

        // The display preference is independent of Ward's timed combat effect. The packed source
        // UID lets us classify the attacker even after the originating NPC has despawned.
        if (
            applyWard && WardOfArceuusState.isActive(target, target.currentMapClock) &&
                (sourceNpcType()?.param(params.demon) ?: 0) != 0
        ) {
            damage = wardOfArceuusDamage(damage, wardActive = true, isDemon = true)
        }
    }
}

internal fun wardOfArceuusDamage(
    incomingDamage: Int,
    wardActive: Boolean,
    isDemon: Boolean,
): Int = if (wardActive && isDemon) incomingDamage - incomingDamage / 10 else incomingDamage
