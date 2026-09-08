package org.rsmod.content.other.special.weapons.ranged

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

/** Normal attacks and explicit empty-state handling for Webweaver bow. */
class WebweaverBowWeapons : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register("obj.wild_cave_webweaver_charged", ChargedWebweaverBow(manager))
        register("obj.wild_cave_webweaver_uncharged", UnchargedWebweaverBow(manager))
    }

    private class ChargedWebweaverBow(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        private fun ProtectedAccess.shoot(target: PathingEntity, attack: CombatAttack.Ranged) {
            val weaponType = getInvObj(attack.weapon)
            if (webweaverCharges() < 1) {
                manager.stopCombat(this)
                mes("Your Webweaver bow has run out of charges.")
                return
            }
            if (!playRangedWeaponFx(weaponType)) {
                manager.stopCombat(this)
                mes("The bow fails to fire.")
                return
            }

            spotanim(WEBWEAVER_LAUNCH_SPOTANIM, height = 96, slot = constants.spotanim_slot_combat)
            // Roll while the bow remains charged so the final ether shot has the right bonuses.
            val damage = manager.rollRangedDamage(this, target, attack)
            val chargesLeft = detractWebweaverCharge()
            if (chargesLeft == null) {
                manager.stopCombat(this)
                return
            }

            val projectile =
                manager.spawnProjectile(
                    source = this,
                    target = target,
                    spotanim = WEBWEAVER_TRAVEL_SPOTANIM,
                    projanim = weaponType.projectileType(),
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                source = this,
                target = target,
                ammo = null,
                damage = damage,
                clientDelay = projectile.clientCycles,
                hitDelay = projectile.serverCycles,
            )
            if (chargesLeft == 0) {
                mes("Your Webweaver bow has run out of charges.")
                manager.stopCombat(this)
            } else {
                manager.continueCombat(this, target)
            }
        }

        private fun ProtectedAccess.playRangedWeaponFx(weapon: ItemServerType): Boolean {
            val attackAnim = weapon.paramOrNull(params.attack_anim_stance1) ?: return false
            anim(RSCM.getReverseMapping(RSCMType.SEQ, attackAnim.id))
            weapon.paramOrNull(params.attack_sound_stance1)?.let { soundSynth(it) }
            return true
        }

        private fun ItemServerType.projectileType(): String =
            paramOrNull(params.proj_type)?.let { RSCM.getReverseMapping(RSCMType.PROJANIM, it.id) }
                ?: ARROW_PROJANIM

        private fun ProtectedAccess.webweaverCharges(): Int {
            val weapon = player.righthand ?: return 0
            val varobj =
                ServerCacheManager.getVarObj(ETHER_VAROBJ.asRSCM(RSCMType.VAROBJ)) ?: return 0
            return weapon.vars.getBits(varobj.bits)
        }

        private fun ProtectedAccess.detractWebweaverCharge(): Int? {
            val weapon = player.righthand ?: return null
            val varobj =
                ServerCacheManager.getVarObj(ETHER_VAROBJ.asRSCM(RSCMType.VAROBJ)) ?: return null
            val current = weapon.vars.getBits(varobj.bits)
            if (current < 1) {
                return null
            }
            val chargesLeft = current - 1
            val packed = weapon.vars.withBits(varobj.bits, chargesLeft)
            player.righthand = weapon.copy(vars = packed)
            return chargesLeft
        }
    }

    private class UnchargedWebweaverBow(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            stopForEmptyBow()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            stopForEmptyBow()
            return true
        }

        private fun ProtectedAccess.stopForEmptyBow() {
            manager.stopCombat(this)
            mes("Your Webweaver bow has run out of charges.")
        }
    }

    private companion object {
        const val ETHER_VAROBJ: String = "varobj.charges_16383"
        const val WEBWEAVER_LAUNCH_SPOTANIM: String = "spotanim.wild_cave_bow_arrow_launch02"
        const val WEBWEAVER_TRAVEL_SPOTANIM: String = "spotanim.wild_cave_bow_arrow_travel02"
        const val ARROW_PROJANIM: String = "projanim.arrow"
    }
}
