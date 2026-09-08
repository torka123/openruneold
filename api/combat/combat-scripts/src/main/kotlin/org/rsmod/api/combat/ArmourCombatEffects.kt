package org.rsmod.api.combat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.mechanics.toxins.BurnEffectService
import org.rsmod.api.player.front
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.modifier.BypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.legs
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpactHandler

/** Resolves passive effects attached to a player's complete armour set. */
@Singleton
internal class ArmourCombatEffects
@Inject
constructor(private val random: GameRandom, private val burnEffects: BurnEffectService) {
    /**
     * Rolls one ordinary melee attack while preserving the normal accuracy/max-hit formula.
     *
     * Verac's Defiler roll must happen before accuracy because it ignores armour and Defence as
     * well as protection prayer. Other Barrows effects are armed only after a successful accuracy
     * roll. The returned impact callback uses final damage, so Guthan cannot heal damage that was
     * absorbed or lost to overkill.
     */
    fun resolveMelee(
        source: Player,
        target: PathingEntity,
        attack: CombatAttack.Melee,
        manager: PlayerAttackManager,
    ): MeleeResolution {
        val veracSet =
            EquipmentChecks.isVeracSet(source.hat, source.torso, source.legs, source.righthand)
        val defiler = veracSet && random.randomBoolean(BARROWS_PROC_DENOMINATOR)
        val accurate =
            defiler ||
                manager.rollMeleeAccuracy(
                    source = source,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = attack.type,
                    multiplier = 1.0,
                )
        val damage =
            if (accurate) {
                manager.rollMeleeMaxHit(
                    source = source,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            } else {
                0
            }

        val impactHandler =
            when {
                defiler -> HitImpactHandler { target.spotanim("spotanim.barrows_verac_desolation") }

                accurate && isGuthanProc(source) ->
                    HitImpactHandler { impact ->
                        healGuthan(source, impact.damage)
                        source.spotanim("spotanim.barrows_guthan_effect")
                    }

                accurate && isToragProc(source) && target is Player ->
                    HitImpactHandler {
                        target.runEnergy = (target.runEnergy * TORAG_RUN_PERCENT_REMAINING) / 100
                        UpdateRun.energy(target, target.runEnergy)
                        target.spotanim("spotanim.barrows_torag_effect")
                    }

                else -> null
            }

        return MeleeResolution(
            damage = damage,
            impactHandler = impactHandler,
            playerModifier =
                if (defiler) {
                    BypassProtectionPrayerPlayerHitModifier
                } else {
                    StandardPlayerHitModifier
                },
        )
    }

    /** Resolves the standard Eclipse and Karil effects after one successful ranged roll. */
    fun resolveRanged(
        source: Player,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        manager: PlayerAttackManager,
    ): RangedResolution {
        val accurate =
            manager.rollRangedAccuracy(
                source = source,
                target = target,
                attackType = attack.type,
                attackStyle = attack.style,
                blockType = attack.type,
                multiplier = 1.0,
            )
        val damage =
            if (accurate) {
                manager.rollRangedMaxHit(
                    source = source,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                    boltSpecDamage = 0,
                )
            } else {
                0
            }

        val eclipseBurn =
            accurate &&
                EquipmentChecks.isEclipseMoonSet(
                    source.hat,
                    source.torso,
                    source.legs,
                    source.righthand,
                ) &&
                random.randomBoolean(ECLIPSE_PROC_DENOMINATOR)

        val karilSet =
            EquipmentChecks.isKarilSet(source.hat, source.torso, source.legs, source.righthand)
        val taintedShot =
            accurate &&
                target is Player &&
                karilSet &&
                random.randomBoolean(BARROWS_PROC_DENOMINATOR)
        val damnedDouble =
            accurate &&
                target is Player &&
                karilSet &&
                EquipmentChecks.isAmuletOfTheDamned(source.front) &&
                random.randomBoolean(BARROWS_PROC_DENOMINATOR)

        val impactHandler =
            when {
                eclipseBurn -> HitImpactHandler { burnEffects.apply(source, target) }
                taintedShot && target is Player ->
                    HitImpactHandler {
                        target.statSub("stat.agility", constant = 0, percent = 20)
                        target.spotanim("spotanim.barrows_karil_tainted_shot")
                    }
                else -> null
            }

        return RangedResolution(
            damage = damage,
            impactHandler = impactHandler,
            extraHit = damnedDouble,
            extraDamage = damage / 2,
        )
    }

    private fun isGuthanProc(source: Player): Boolean =
        EquipmentChecks.isGuthanSet(source.hat, source.torso, source.legs, source.righthand) &&
            random.randomBoolean(BARROWS_PROC_DENOMINATOR)

    private fun isToragProc(source: Player): Boolean =
        EquipmentChecks.isToragSet(source.hat, source.torso, source.legs, source.righthand) &&
            random.randomBoolean(BARROWS_PROC_DENOMINATOR)

    private fun healGuthan(source: Player, finalDamage: Int) {
        if (finalDamage <= 0) {
            return
        }
        val base = source.statBase("stat.hitpoints")
        val cap =
            if (EquipmentChecks.isAmuletOfTheDamned(source.front)) {
                base + GUTHAN_DAMNED_OVERHEAL
            } else {
                base
            }
        val healing = min(finalDamage, cap - source.stat("stat.hitpoints"))
        if (healing > 0) {
            source.statAdd("stat.hitpoints", constant = healing, percent = 0)
        }
    }

    data class MeleeResolution(
        val damage: Int,
        val impactHandler: HitImpactHandler?,
        val playerModifier: PlayerHitModifier,
    )

    data class RangedResolution(
        val damage: Int,
        val impactHandler: HitImpactHandler?,
        val extraHit: Boolean,
        val extraDamage: Int,
    )

    companion object {
        const val BARROWS_PROC_DENOMINATOR: Int = 4
        const val ECLIPSE_PROC_DENOMINATOR: Int = 5
        const val TORAG_RUN_PERCENT_REMAINING: Int = 80
        const val GUTHAN_DAMNED_OVERHEAL: Int = 10
    }
}
