package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
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
 * Penance gains 0.5% maximum damage for every visible Prayer level the attacker is missing.
 */
class AbyssalBludgeonSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee("obj.abyssal_bludgeon", Penance(manager))
    }

    private class Penance(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            penance(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            penance(target, attack)
            return true
        }

        private fun ProtectedAccess.penance(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim("seq.abyssal_bludgeon_special_attack")
            spotanim(
                spot = "spotanim.abyssal_miasma_spotanim_bludgeon",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )

            val missingPrayer = (statBase("stat.prayer") - stat("stat.prayer")).coerceAtLeast(0)
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0 + (missingPrayer * 0.005),
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }
}
