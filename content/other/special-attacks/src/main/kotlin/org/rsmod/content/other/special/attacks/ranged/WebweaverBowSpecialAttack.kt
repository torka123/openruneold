package org.rsmod.content.other.special.attacks.ranged

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.NpcPoisonEffectService
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
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
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

/** Swarm consumes one ether and fires four independently rolled, poison-capable hits. */
class WebweaverBowSpecialAttack
@Inject
constructor(
    private val charges: CombatChargeManager,
    private val random: GameRandom,
    private val poisons: NpcPoisonEffectService,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerRanged("obj.wild_cave_webweaver_charged", Swarm(manager, charges, random, poisons))
    }

    private class Swarm(
        private val manager: SpecialAttackManager,
        private val charges: CombatChargeManager,
        private val random: GameRandom,
        private val poisons: NpcPoisonEffectService,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean = swarm(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = swarm(target, attack)

        private fun ProtectedAccess.swarm(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val weaponType = getInvObj(attack.weapon)
            if (charges.getWeaponCharges(player, ETHER_VAROBJ) < 1) {
                manager.stopCombat(this)
                mes("Your Webweaver bow has run out of charges.")
                return false
            }

            anim(SWARM_SEQUENCE)
            weaponType.paramOrNull(params.attack_sound_stance1)?.let { soundSynth(it) }
            spotanim(SWARM_LAUNCH_SPOTANIM, height = 96, slot = constants.spotanim_slot_combat)

            val projanim =
                weaponType.paramOrNull(params.proj_type)?.let {
                    RSCM.getReverseMapping(RSCMType.PROJANIM, it.id)
                } ?: ARROW_PROJANIM
            // The official Swarm sequence has one upgraded Webweaver travel graphic followed by
            // four hits. The final three hits all land one tick after the first.
            manager.spawnProjectile(
                source = this,
                target = target,
                spotanim = SWARM_TRAVEL_SPOTANIM,
                projanim = projanim,
            )
            val hits =
                List(SWARM_HIT_COUNT) { index ->
                    val damage = swarmDamage(target, attack)
                    val poisonRoll =
                        damage > 0 &&
                            random.randomBoolean(WebweaverBowSpecialPoison.ROLL_DENOMINATOR)
                    val poison = WebweaverBowSpecialPoison.shouldAttempt(damage, poisonRoll)
                    val hitDelay = WebweaverBowSpecialTiming.hitDelay(index)
                    SwarmHit(
                        damage = damage,
                        clientDelay = hitDelay * CLIENT_CYCLES_PER_TICK,
                        hitDelay = hitDelay,
                        poison = poison,
                    )
                }

            val chargesLeft =
                detractWebweaverCharge()
                    ?: run {
                        manager.stopCombat(this)
                        return false
                    }

            manager.giveCombatXp(this, target, attack, hits.sumOf { it.damage })
            target.spotanim(SWARM_IMPACT_SPOTANIM, delay = hits.first().clientDelay, height = 96)
            hits.forEachIndexed { index, hit ->
                val impactHandler = if (hit.poison) poisonImpactHandler(player) else null
                if (index == 0) {
                    manager.queueRangedHit(
                        source = this,
                        target = target,
                        ammo = null,
                        damage = hit.damage,
                        clientDelay = hit.clientDelay,
                        hitDelay = hit.hitDelay,
                        impactHandler = impactHandler,
                    )
                } else {
                    manager.queueRangedDamage(
                        source = this,
                        target = target,
                        ammo = null,
                        damage = hit.damage,
                        hitDelay = hit.hitDelay,
                        impactHandler = impactHandler,
                    )
                }
            }

            if (chargesLeft == 0) {
                mes("Your Webweaver bow has run out of charges.")
                manager.stopCombat(this)
            } else {
                manager.continueCombat(this, target)
            }
            return true
        }

        private fun ProtectedAccess.detractWebweaverCharge(): Int? {
            val weapon = player.righthand ?: return null
            val varobj =
                requireNotNull(ServerCacheManager.getVarObj(ETHER_VAROBJ.asRSCM(RSCMType.VAROBJ)))
            val current = weapon.vars.getBits(varobj.bits)
            if (current < 1) {
                return null
            }
            val chargesLeft = current - 1
            val packed = weapon.vars.withBits(varobj.bits, chargesLeft)
            player.righthand = weapon.copy(vars = packed)
            return chargesLeft
        }

        private fun ProtectedAccess.swarmDamage(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Int {
            val accurate =
                manager.rollRangedAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = attack.type,
                    multiplier = SWARM_ACCURACY_MULTIPLIER,
                )
            if (!accurate) {
                return 0
            }

            val normalMaxHit =
                manager.calculateRangedMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                    boltSpecDamage = 0,
                )
            val swarmMaxHit = WebweaverBowSpecialDamage.maxHit(normalMaxHit)
            if (swarmMaxHit <= 0) {
                return 0
            }
            return if (player.adminMaxHit) swarmMaxHit else random.of(1..swarmMaxHit)
        }

        private fun poisonImpactHandler(source: Player): HitImpactHandler =
            HitImpactHandler { impact ->
                if (impact.damage <= 0) {
                    return@HitImpactHandler
                }

                when (val target = impact.target) {
                    is Npc ->
                        poisons.applyAfterInterval(
                            source,
                            target,
                            WebweaverBowSpecialPoison.INITIAL_DAMAGE,
                        )
                    is Player ->
                        PlayerPoison.tryPoisonAfterInterval(
                            target,
                            initialDamage = WebweaverBowSpecialPoison.INITIAL_DAMAGE,
                        )
                }
            }

        private data class SwarmHit(
            val damage: Int,
            val clientDelay: Int,
            val hitDelay: Int,
            val poison: Boolean,
        )
    }

    private companion object {
        const val ETHER_VAROBJ: String = "varobj.charges_16383"
        const val SWARM_HIT_COUNT: Int = 4
        const val SWARM_ACCURACY_MULTIPLIER: Double = 2.0
        const val CLIENT_CYCLES_PER_TICK: Int = 30
        const val SWARM_SEQUENCE: String = "seq.human_special01_webweaver"
        const val SWARM_LAUNCH_SPOTANIM: String = "spotanim.fx_webweaver01_launch_spotanim"
        const val SWARM_IMPACT_SPOTANIM: String = "spotanim.fx_webweaver01_impact_spotanim"
        const val SWARM_TRAVEL_SPOTANIM: String = "spotanim.wild_cave_bow_arrow_travel02"
        const val ARROW_PROJANIM: String = "projanim.arrow"
    }
}

internal object WebweaverBowSpecialTiming {
    fun hitDelay(index: Int): Int {
        require(index in 0 until 4)
        return if (index == 0) 1 else 2
    }
}

internal object WebweaverBowSpecialPoison {
    /**
     * Exact live probability still needs packet/capture validation; preserve the established roll.
     */
    const val ROLL_DENOMINATOR: Int = 4
    const val INITIAL_DAMAGE: Int = 4

    fun shouldAttempt(damage: Int, rolled: Boolean): Boolean = damage > 0 && rolled
}

internal object WebweaverBowSpecialDamage {
    /** Ceiling of forty percent of the normal maximum hit. */
    fun maxHit(normalMaxHit: Int): Int = ((normalMaxHit.coerceAtLeast(0) * 2) + 4) / 5
}
