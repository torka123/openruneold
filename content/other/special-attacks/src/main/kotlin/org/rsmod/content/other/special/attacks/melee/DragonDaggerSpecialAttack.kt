package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Puncture: two independent hits, each using 115% accuracy and maximum damage.
 *
 * The attacker's selected melee style is preserved, but the target always defends
 * using Slash defence.
 */
class DragonDaggerSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val puncture = DragonDagger(manager)
        registerMelee("obj.dragon_dagger", puncture)
        registerMelee("obj.dragon_dagger_p", puncture)
        registerMelee("obj.dragon_dagger_p+", puncture)
        registerMelee("obj.dragon_dagger_p++", puncture)
        registerMelee("obj.br_dragon_dagger", puncture)
        registerMelee("obj.bh_dragon_dagger_corrupted", puncture)
        registerMelee("obj.bh_dragon_dagger_p_corrupted", puncture)
        registerMelee("obj.bh_dragon_dagger_p+_corrupted", puncture)
        registerMelee("obj.bh_dragon_dagger_p++_corrupted", puncture)
    }

    private class DragonDagger(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            puncture(target, attack, secondHitDelay = 2)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            puncture(target, attack, secondHitDelay = 1)
            return true
        }

        private fun ProtectedAccess.puncture(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            secondHitDelay: Int,
        ) {
            anim("seq.puncture")
            spotanim(
                spot = "spotanim.sp_attack_puncture_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )

            val first =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.15,
                    maxHitMultiplier = 1.15,
                    blockType = MeleeAttackType.Slash,
                )
            val second =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.15,
                    maxHitMultiplier = 1.15,
                    blockType = MeleeAttackType.Slash,
                )
            manager.giveCombatXp(this, target, attack, first + second)
            manager.queueMeleeHit(this, target, first)
            manager.queueMeleeHit(this, target, second, delay = secondHitDelay)
            manager.continueCombat(this, target)
        }
    }
}
