package org.rsmod.content.other.special.weapons.melee

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Fang of the Hound normal hits have a five-percent chance to add Flames of Cerberus after a
 * successful melee accuracy roll. The flame spell deliberately makes no second accuracy roll.
 */
class FangOfTheHoundWeapons
@Inject
constructor() : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register("obj.fang_of_the_hound", FangOfTheHound(manager))
    }

    private class FangOfTheHound(private val manager: WeaponAttackManager) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean = attackWithFlames(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean = attackWithFlames(target, attack)

        private fun ProtectedAccess.attackWithFlames(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.human_karambit_attack")

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
                    manager.rollMeleeMaxHit(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        multiplier = 1.0,
                    )
                } else {
                    0
                }

            manager.queueMeleeHit(this, target, damage)
            if (accurate && random.randomBoolean(FLAMES_PROC_DENOMINATOR)) {
                flamesOfCerberus(target)
            }
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.flamesOfCerberus(target: PathingEntity) {
            val damage =
                manager.rollSpellMaxHit(
                    source = this,
                    target = target,
                    spell = FLAMES_OF_CERBERUS,
                    spellbook = Spellbook.Standard,
                    baseMaxHit = FLAMES_BASE_MAX_HIT,
                    attackRate = FANG_ATTACK_RATE,
                )

            target.spotanim(
                spot = "spotanim.vfx_flames_of_cerberus",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            manager.queueMagicHit(
                source = this,
                target = target,
                damage = damage,
                clientDelay = 0,
                spell = FLAMES_OF_CERBERUS,
            )
        }
    }

    private companion object {
        const val FLAMES_PROC_DENOMINATOR: Int = 20
        const val FLAMES_BASE_MAX_HIT: Int = 10
        const val FANG_ATTACK_RATE: Int = 3

        // Cache item: obj.fang_of_the_hound_fire (id 33377).
        val FLAMES_OF_CERBERUS: ItemServerType = ItemServerType(33377)
    }
}
