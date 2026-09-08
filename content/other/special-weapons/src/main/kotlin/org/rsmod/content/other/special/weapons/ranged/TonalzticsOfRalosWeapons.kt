package org.rsmod.content.other.special.weapons.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

/**
 * Tonalztics are a non-stackable thrown weapon with self-contained ammunition. Registering them
 * here prevents the generic thrown-weapon branch from consuming the equipped item on every shot.
 * The uncharged item makes one independent 75%-max hit; the charged item makes two.
 */
class TonalzticsOfRalosWeapons @Inject constructor() : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register(
            "obj.tonalztics_of_ralos_uncharged",
            Tonalztics(manager, hitCount = UNCHARGED_HIT_COUNT),
        )
        register(
            "obj.tonalztics_of_ralos_charged",
            Tonalztics(manager, hitCount = CHARGED_HIT_COUNT),
        )
    }

    private class Tonalztics(
        private val manager: WeaponAttackManager,
        private val hitCount: Int,
    ) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            fire(target, attack)
            return true
        }

        private fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ) {
            val weaponType = getInvObj(attack.weapon)
            if (!playRangedWeaponFx(weaponType)) {
                manager.stopCombat(this)
                mes("The Tonalztics fail to fire.")
                return
            }
            // The item has no cache projectile-launch param. Sending the null launch spotanim
            // matches ordinary ranged behavior while the travel projectile supplies hit timing.
            spotanim(null, height = 96, slot = constants.spotanim_slot_combat)

            val hits =
                List(hitCount) {
                    val projectile =
                        manager.spawnProjectile(
                            source = this,
                            target = target,
                            spotanim = TONALZTICS_TRAVEL_SPOTANIM,
                            projanim = THROWN_PROJANIM,
                        )
                    NormalHit(
                        damage =
                            manager.rollRangedDamage(
                                source = this,
                                target = target,
                                attack = attack,
                                maxHitMultiplier = MAX_HIT_MULTIPLIER,
                            ),
                        clientDelay = projectile.clientCycles,
                        hitDelay = projectile.serverCycles,
                    )
                }

            manager.giveCombatXp(this, target, attack, hits.sumOf { it.damage })
            hits.forEachIndexed { index, hit ->
                if (index == 0) {
                    manager.queueRangedHit(
                        source = this,
                        target = target,
                        ammo = null,
                        damage = hit.damage,
                        clientDelay = hit.clientDelay,
                        hitDelay = hit.hitDelay,
                    )
                } else {
                    manager.queueRangedDamage(
                        source = this,
                        target = target,
                        ammo = null,
                        damage = hit.damage,
                        hitDelay = hit.hitDelay,
                    )
                }
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.playRangedWeaponFx(weapon: ItemServerType): Boolean {
            val attackAnim = weapon.paramOrNull(params.attack_anim_stance1) ?: return false
            anim(RSCM.getReverseMapping(RSCMType.SEQ, attackAnim.id))
            weapon.paramOrNull(params.attack_sound_stance1)?.let { soundSynth(it) }
            return true
        }

        private data class NormalHit(
            val damage: Int,
            val clientDelay: Int,
            val hitDelay: Int,
        )
    }

    private companion object {
        const val UNCHARGED_HIT_COUNT: Int = 1
        const val CHARGED_HIT_COUNT: Int = 2
        const val MAX_HIT_MULTIPLIER: Double = 0.75
        const val TONALZTICS_TRAVEL_SPOTANIM: String = "spotanim.rune_javelin_travel"
        const val THROWN_PROJANIM: String = "projanim.thrown"
    }
}
