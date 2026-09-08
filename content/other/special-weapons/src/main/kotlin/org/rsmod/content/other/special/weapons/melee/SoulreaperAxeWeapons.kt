package org.rsmod.content.other.special.weapons.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.mechanics.toxins.SoulreaperStackDecay
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/**
 * Normal Soulreaper attacks calculate their damage from the existing stack count, then create the
 * next stack afterwards. This ordering is important: the attack that creates a stack must not
 * receive the stack's strength bonus.
 */
class SoulreaperAxeWeapons @Inject constructor() : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val soulreaper = SoulreaperAxe(manager)
        register("obj.soulreaper", soulreaper)
        register("obj.soulreaper_axe_orn", soulreaper)
    }

    private class SoulreaperAxe(private val manager: WeaponAttackManager) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean = attackSoulreaper(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean = attackSoulreaper(target, attack)

        private fun ProtectedAccess.attackSoulreaper(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0,
                )
            manager.queueMeleeHit(this, target, damage)
            createSoulStackAfterAttack()
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.createSoulStackAfterAttack() {
            val currentStacks = vars[SOUL_STACKS].coerceIn(0, MAX_SOUL_STACKS)
            val updatedStacks =
                if (currentStacks < MAX_SOUL_STACKS && stat("stat.hitpoints") > SELF_DAMAGE) {
                    takeInstantHit(type = HitType.Typeless, damage = SELF_DAMAGE)
                    currentStacks + 1
                } else {
                    currentStacks
                }

            if (updatedStacks > 0) {
                VarPlayerIntMapSetter.set(player, SOUL_STACKS, updatedStacks)
                SoulreaperStackDecay.reset(player)
            }
        }
    }

    private companion object {
        const val SOUL_STACKS = "varp.soulreaper_stacks"
        const val SELF_DAMAGE = 8
        const val MAX_SOUL_STACKS = 5
    }
}
