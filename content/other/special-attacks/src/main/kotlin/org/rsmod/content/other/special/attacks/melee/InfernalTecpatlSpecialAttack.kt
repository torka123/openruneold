package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The Infernal tecpatl's four-hit special uses the same ordered accuracy and damage-split model
 * as dragon claws, with 25% increased accuracy and maximum hit.
 */
class InfernalTecpatlSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee("obj.infernal_tecpatl", FourfoldStrike(manager))
    }

    private class FourfoldStrike(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            fourfoldStrike(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            fourfoldStrike(target, attack)
            return true
        }

        private fun ProtectedAccess.fourfoldStrike(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim("seq.human_infernal_tecpatl_spec")

            val hits =
                DragonClawDamage.roll(
                    maxHit =
                        manager.calculateMeleeMaxHit(
                            source = this,
                            target = target,
                            attackType = attack.type,
                            attackStyle = attack.style,
                            multiplier = 1.25,
                        ),
                    rollAccuracy = {
                        manager.rollMeleeAccuracy(
                            source = this,
                            target = target,
                            attackType = attack.type,
                            attackStyle = attack.style,
                            blockType = attack.type,
                            multiplier = 1.25,
                        )
                    },
                    rollInclusive = { range -> random.of(range) },
                    rollExclusive = { maximum -> random.of(maximum) },
                )

            manager.giveCombatXp(this, target, attack, hits.sum())
            hits.forEach { hit -> manager.queueMeleeHit(this, target, hit) }
            manager.continueCombat(this, target)
        }
    }
}
