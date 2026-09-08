package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Seeking Lunge deals exactly 70% of the wielder's normal maximum melee hit on a successful hit.
 *
 * When that fixed damage would defeat the target, its attack roll is fixed at 70% instead of being
 * randomized. The target still rolls its normal defence.
 */
class SunspearSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee("obj.sunspear", SeekingLunge(manager))
    }

    private class SeekingLunge(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            seekingLunge(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            seekingLunge(target, attack)
            return true
        }

        private fun ProtectedAccess.seekingLunge(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim("seq.human_weapons_sunspear_spec")
            spotanim("spotanim.vfx_sunspear_special")
            val maxHit =
                manager.calculateMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            val fixedDamage = (maxHit * DAMAGE_MULTIPLIER).toInt()
            val accurate =
                if (target.hitpoints() <= fixedDamage) {
                    manager.rollFixedMeleeAttackRoll(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        blockType = attack.type,
                        attackMultiplier = FIXED_ACCURACY_MULTIPLIER,
                    )
                } else {
                    manager.rollMeleeAccuracy(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        blockType = attack.type,
                        multiplier = 1.0,
                    )
                }
            val damage = if (accurate) fixedDamage else 0
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }

        private companion object {
            const val DAMAGE_MULTIPLIER: Double = 0.70
            const val FIXED_ACCURACY_MULTIPLIER: Double = 0.70
        }
    }
}

private fun PathingEntity.hitpoints(): Int =
    when (this) {
        is Npc -> hitpoints
        is Player -> hitpoints
    }
