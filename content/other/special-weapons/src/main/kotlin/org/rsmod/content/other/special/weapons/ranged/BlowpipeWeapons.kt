package org.rsmod.content.other.special.weapons.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.ranged.RangedAmmunition
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.NpcPoisonEffectService
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.hat
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ranged.BlowpipeAmmo
import org.rsmod.api.player.righthand
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.type.getInvObj

/**
 * Normal attacks for cache-packed blowpipes.
 *
 * Blowpipes are categorised as thrown weapons in the cache, but their darts are stored inside the
 * equipped item. Handling them here prevents the generic thrown-weapon path from consuming the
 * blowpipe itself.
 */
class BlowpipeWeapons
@Inject
constructor(private val random: GameRandom, private val poisons: NpcPoisonEffectService) :
    WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val toxic = StoredBlowpipe(manager, random, poisons, usesScales = true)
        val rosewood = StoredBlowpipe(manager, random, poisons, usesScales = false)
        val empty = EmptyBlowpipe(manager)

        register("obj.toxic_blowpipe_loaded", toxic)
        register("obj.toxic_blowpipe_loaded_ornament", toxic)
        register("obj.rosewood_blowpipe", rosewood)
        register("obj.toxic_blowpipe", empty)
        register("obj.toxic_blowpipe_ornament", empty)
        register("obj.rosewood_blowpipe_empty", empty)
    }

    private class StoredBlowpipe(
        private val manager: WeaponAttackManager,
        private val random: GameRandom,
        private val poisons: NpcPoisonEffectService,
        private val usesScales: Boolean,
    ) : RangedWeapon {
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
            val equipped = player.righthand
            val dart = BlowpipeAmmo.loadedDart(equipped)
            if (dart == null || !BlowpipeAmmo.canUseLoadedDart(equipped)) {
                manager.stopCombat(this)
                mes("Your blowpipe has no usable darts loaded.")
                return
            }
            if (usesScales && !BlowpipeAmmo.hasScales(equipped, 1)) {
                manager.stopCombat(this)
                mes("Your toxic blowpipe has run out of scales.")
                return
            }
            if (!playRangedWeaponFx(weaponType)) {
                manager.stopCombat(this)
                mes("The blowpipe fails to fire.")
                return
            }

            val travelSpotanim = dart.type.paramOrNull(params.proj_travel)
            if (travelSpotanim == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return
            }
            val dartsToConsume =
                if (RangedAmmunition.conserveAmmo(player, random)) {
                    0
                } else {
                    1
                }
            val scalesToConsume =
                if (usesScales && !random.randomBoolean(SCALE_CONSERVE_ROLL)) {
                    1
                } else {
                    0
                }

            player.spotanim(
                dart.type.paramOrNull(params.proj_launch)?.id ?: NULL_SPOTANIM_ID,
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val projectile =
                manager.spawnProjectile(
                    source = this,
                    target = target,
                    spotanim = travelSpotanim,
                    projanim = weaponType.projectileType(),
                )
            // Roll before updating the packed item: its stored dart strength applies to this shot.
            val damage = manager.rollRangedDamage(this, target, attack)
            val guaranteedNpcVenom = target is Npc && EquipmentChecks.isSerpentineHelm(player.hat)
            val venom =
                usesScales &&
                    damage > 0 &&
                    (guaranteedNpcVenom || random.randomBoolean(VENOM_ROLL_DENOMINATOR))
            val consumption =
                BlowpipeAmmo.consume(
                    player = player,
                    darts = dartsToConsume,
                    scales = scalesToConsume,
                )
            if (consumption == null) {
                manager.stopCombat(this)
                return
            }

            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                source = this,
                target = target,
                ammo = dart.type,
                damage = damage,
                clientDelay = projectile.clientCycles,
                hitDelay = projectile.serverCycles,
                impactHandler = if (venom) venomImpactHandler(player) else null,
            )
            if (!consumption.canFire) {
                if (consumption.dartsLeft == 0) {
                    mes("Your blowpipe has run out of darts.")
                } else {
                    mes("Your toxic blowpipe has run out of scales.")
                }
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
                ?: THROWN_PROJANIM

        private fun venomImpactHandler(source: Player): HitImpactHandler =
            HitImpactHandler { impact ->
                if (impact.damage <= 0) {
                    return@HitImpactHandler
                }
                when (val target = impact.target) {
                    is Npc -> poisons.applyVenom(source, target)
                    is Player -> PlayerVenom.tryVenom(target)
                }
            }
    }

    private class EmptyBlowpipe(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            stopForEmptyBlowpipe()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            stopForEmptyBlowpipe()
            return true
        }

        private fun ProtectedAccess.stopForEmptyBlowpipe() {
            manager.stopCombat(this)
            mes("Your blowpipe has no usable darts loaded.")
        }
    }

    private companion object {
        const val SCALE_CONSERVE_ROLL: Int = 3
        const val VENOM_ROLL_DENOMINATOR: Int = 4
        const val NULL_SPOTANIM_ID: Int = 0xFFFF
        const val THROWN_PROJANIM: String = "projanim.thrown"
    }
}
