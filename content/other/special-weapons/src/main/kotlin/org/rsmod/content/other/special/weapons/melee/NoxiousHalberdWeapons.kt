package org.rsmod.content.other.special.weapons.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.mechanics.toxins.NoxiousHalberdVirulence
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Handles the normal Noxious halberd attack so Virulence can apply its inclusive minimum-to-max
 * roll after a successful accuracy roll.
 */
class NoxiousHalberdWeapons
@Inject
constructor() : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val halberd = NoxiousHalberd(manager)
        register("obj.noxious_halberd", halberd)
        register("obj.br_noxious_halberd", halberd)
    }

    private class NoxiousHalberd(private val manager: WeaponAttackManager) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean = attackWithVirulence(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean = attackWithVirulence(target, attack)

        private fun ProtectedAccess.attackWithVirulence(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)

            val accurate =
                manager.rollMeleeAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = attack.type,
                    multiplier = 1.0,
                )
            val damage =
                if (accurate) {
                    rollAccurateDamage(target, attack)
                } else {
                    0
                }

            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.rollAccurateDamage(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Int {
            val maximum =
                manager.calculateMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            val minimum = NoxiousHalberdVirulence.consume(player) ?: return random.of(1..maximum)
            return random.of(minimum..maxOf(minimum, maximum))
        }
    }
}
