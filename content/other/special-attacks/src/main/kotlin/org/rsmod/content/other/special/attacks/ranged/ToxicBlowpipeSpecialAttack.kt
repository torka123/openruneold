package org.rsmod.content.other.special.attacks.ranged

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
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.type.getInvObj

/**
 * Toxic Siphon fires one stored dart with doubled accuracy and 150% maximum damage, then restores
 * half of the prospective hit. The heal uses the raw roll so overkill still heals correctly.
 */
class ToxicBlowpipeSpecialAttack
@Inject
constructor(private val random: GameRandom, private val poisons: NpcPoisonEffectService) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val toxicSiphon = ToxicSiphon(manager, random, poisons)
        registerRanged("obj.toxic_blowpipe_loaded", toxicSiphon)
        registerRanged("obj.toxic_blowpipe_loaded_ornament", toxicSiphon)
    }

    private class ToxicSiphon(
        private val manager: SpecialAttackManager,
        private val random: GameRandom,
        private val poisons: NpcPoisonEffectService,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean = toxicSiphon(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = toxicSiphon(target, attack)

        private fun ProtectedAccess.toxicSiphon(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val weaponType = getInvObj(attack.weapon)
            val equipped = player.righthand
            val dart = BlowpipeAmmo.loadedDart(equipped)
            if (dart == null || !BlowpipeAmmo.canUseLoadedDart(equipped)) {
                manager.stopCombat(this)
                mes("Your toxic blowpipe has no usable darts loaded.")
                return false
            }
            if (!BlowpipeAmmo.hasScales(equipped, 1)) {
                manager.stopCombat(this)
                mes("Your toxic blowpipe has run out of scales.")
                return false
            }

            val travelSpotanim = dart.type.paramOrNull(params.proj_travel)
            if (travelSpotanim == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }

            val consumeDart = !RangedAmmunition.conserveAmmo(player, random)
            val consumeScale = !random.randomBoolean(SCALE_CONSERVE_ROLL)

            anim(TOXIC_SIPHON_SEQUENCE)
            weaponType.paramOrNull(params.attack_sound_stance1)?.let { soundSynth(it) }
            spotanim(TOXIC_SIPHON_SPOTANIM, height = 96, slot = constants.spotanim_slot_combat)

            val projectile =
                manager.spawnProjectile(
                    source = this,
                    target = target,
                    spotanim = travelSpotanim,
                    projanim = weaponType.projectileType(),
                )
            val damage =
                manager.rollRangedDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = TOXIC_SIPHON_ACCURACY_MULTIPLIER,
                    maxHitMultiplier = TOXIC_SIPHON_MAX_HIT_MULTIPLIER,
                )
            val heal = ToxicBlowpipeSpecialDamage.heal(damage)
            val guaranteedNpcVenom = target is Npc && EquipmentChecks.isSerpentineHelm(player.hat)
            val venom =
                damage > 0 && (guaranteedNpcVenom || random.randomBoolean(VENOM_ROLL_DENOMINATOR))

            val consumption =
                BlowpipeAmmo.consume(
                    player = player,
                    darts = if (consumeDart) 1 else 0,
                    scales = if (consumeScale) 1 else 0,
                )
            if (consumption == null) {
                manager.stopCombat(this)
                return false
            }

            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                source = this,
                target = target,
                ammo = dart.type,
                damage = damage,
                clientDelay = projectile.clientCycles,
                hitDelay = projectile.serverCycles,
                impactHandler =
                    if (heal > 0 || venom) {
                        HitImpactHandler {
                            if (heal > 0 && player.hitpoints > 0) {
                                player.statHeal("stat.hitpoints", constant = heal, percent = 0)
                            }
                            if (venom && it.damage > 0) {
                                when (val impactTarget = it.target) {
                                    is Npc -> poisons.applyVenom(player, impactTarget)
                                    is Player -> PlayerVenom.tryVenom(impactTarget)
                                }
                            }
                        }
                    } else {
                        null
                    },
            )

            if (!consumption.canFire) {
                if (consumption.dartsLeft == 0) {
                    mes("Your toxic blowpipe has run out of darts.")
                } else {
                    mes("Your toxic blowpipe has run out of scales.")
                }
                manager.stopCombat(this)
            } else {
                manager.continueCombat(this, target)
            }
            return true
        }

        private fun ItemServerType.projectileType(): String =
            paramOrNull(params.proj_type)?.let { RSCM.getReverseMapping(RSCMType.PROJANIM, it.id) }
                ?: THROWN_PROJANIM
    }

    private companion object {
        const val SCALE_CONSERVE_ROLL: Int = 3
        const val VENOM_ROLL_DENOMINATOR: Int = 4
        const val TOXIC_SIPHON_ACCURACY_MULTIPLIER: Double = 2.0
        const val TOXIC_SIPHON_MAX_HIT_MULTIPLIER: Double = 1.5
        const val TOXIC_SIPHON_SEQUENCE: String = "seq.toxic_blowpipe_special_updated"
        const val TOXIC_SIPHON_SPOTANIM: String = "spotanim.toxic_blowpipe_specialattack"
        const val THROWN_PROJANIM: String = "projanim.thrown"
    }
}

internal object ToxicBlowpipeSpecialDamage {
    fun heal(prospectiveDamage: Int): Int = prospectiveDamage.coerceAtLeast(0) / 2
}
