package org.rsmod.content.skills.magic.spell.attacks.arceuus

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.commons.magic.DarkLureState
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpact
import org.rsmod.game.hit.HitImpactHandler

/** Revision-240 Arceuus target spells using OSRS spell-specific graphics and effects. */
internal class ArceuusCombatSpells
@Inject
constructor(private val random: GameRandom, private val clock: MapClock) : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        for (definition in ArceuusCombatRules.definitions) {
            register(definition.obj, ArceuusAttack(manager, definition))
        }
    }

    private inner class ArceuusAttack(
        private val manager: SpellAttackManager,
        private val definition: ArceuusCombatDefinition,
    ) : SpellAttack {
        override fun canTargetNonCombatNpc(target: Npc): Boolean =
            definition.effect == ArceuusCombatEffect.DarkLure && DarkLureState.isImpling(target)

        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) =
            cast(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) =
            cast(target, attack)

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            if (!validateTarget(target)) return
            val result = manager.attemptCast(this, attack)
            if (result.isFailure()) return
            if (definition.effect == ArceuusCombatEffect.DarkLure) {
                DarkLureState.recordCast(player, clock.cycle)
            }

            player.anim(seq(definition.castAnimation), priority = 6)
            player.spotanim(spot(definition.castGraphic))
            soundSynth(synth(definition.castSound))

            val timing =
                if (definition.projectile != null) {
                    val projectile =
                        manager.spawnProjectile(
                            this,
                            target,
                            spot(definition.projectile),
                            MAGIC_PROJECTILE,
                        )
                    ImpactTiming(projectile.durations.serverDelay, projectile.durations.clientDelay)
                } else {
                    ImpactTiming(definition.directDelay, definition.directDelay * CLIENT_CYCLES)
                }

            val marked = ArceuusSpellState.isMarked(player, clock.cycle)
            val splash = manager.rollSplash(this, target, attack, result)
            if (splash) {
                manager.playSplashFx(this, target, timing.client, synth(definition.castSound), 8)
                manager.queueSplashHit(
                    this,
                    target,
                    attack.spell.obj,
                    timing.client,
                    timing.server,
                )
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val damage = manager.rollMaxHit(this, target, attack, result, definition.maxHit)
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = timing.client,
                castSound = null,
                soundRadius = 8,
                hitSpot = spot(definition.impactGraphic),
                hitSpotHeight = definition.impactHeight,
                hitSound = synth(definition.hitSound),
            )
            val source = this
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(
                this,
                target,
                attack.spell.obj,
                damage,
                timing.client,
                timing.server,
                HitImpactHandler { impact -> source.applyEffect(target, marked, impact) },
            )
            manager.continueCombatIfAutocast(this, target)
        }

        private fun ProtectedAccess.validateTarget(target: PathingEntity): Boolean {
            if (definition.effect == ArceuusCombatEffect.Demonbane) {
                if (target !is Npc || target.visType.param(params.demon) == 0) {
                    mes("This spell only affects demons.")
                    manager.stopCombat(this)
                    return false
                }
            }
            if (definition.effect == ArceuusCombatEffect.DarkLure && target !is Npc) {
                mes("This spell can only be cast on monsters.")
                manager.stopCombat(this)
                return false
            }
            if (definition.effect == ArceuusCombatEffect.DarkLure && target is Npc) {
                val rejection = DarkLureState.rejection(player, target, clock.cycle)
                if (rejection != null) {
                    mes(rejection)
                    manager.stopCombat(this)
                    return false
                }
            }
            return true
        }

        private fun ProtectedAccess.applyEffect(
            target: PathingEntity,
            markedBeforeImpact: Boolean,
            impact: HitImpact,
        ) {
            when (definition.effect) {
                ArceuusCombatEffect.Grasp -> {
                    val denominator =
                        if (markedBeforeImpact) (definition.bindDenominator / 2).coerceAtLeast(1)
                        else definition.bindDenominator
                    if (impact.damage <= 0 || !random.randomBoolean(denominator)) {
                        return
                    }
                    val bindTicks =
                        when {
                            ArceuusSpellState.hasWard(target, clock.cycle) -> 1
                            else -> definition.bindTicks
                        }
                    if (CombatEffects.bind(target, bindTicks)) {
                        target.spotanim(spot(definition.bindGraphic))
                    }
                }
                ArceuusCombatEffect.DarkLure -> {
                    DarkLureState.apply(player, target as Npc, clock.cycle)
                }
                ArceuusCombatEffect.Demonbane -> Unit
            }
        }
    }

    private data class ImpactTiming(val server: Int, val client: Int)

    private companion object {
        const val CLIENT_CYCLES = 30
        const val MAGIC_PROJECTILE = "projanim.magic_spell"
    }
}

internal enum class ArceuusCombatEffect { Grasp, DarkLure, Demonbane }

internal data class ArceuusCombatDefinition(
    val obj: String,
    val maxHit: Int,
    val castAnimation: Int,
    val castGraphic: Int,
    val projectile: Int?,
    val impactGraphic: Int,
    val impactHeight: Int,
    val castSound: Int,
    val hitSound: Int,
    val effect: ArceuusCombatEffect,
    val bindDenominator: Int = 1,
    val bindTicks: Int = 0,
    val bindGraphic: Int = -1,
    val directDelay: Int = 1,
)

internal object ArceuusCombatRules {
    val definitions =
        listOf(
            ArceuusCombatDefinition("obj.dmm_ref_hat", 12, 8972, 1856, null, 1858, 0, 5042, 5029, ArceuusCombatEffect.Grasp, 10, 2, 1857),
            ArceuusCombatDefinition("obj.dmm_ref_torso", 17, 8972, 1859, null, 1861, 0, 5026, 5050, ArceuusCombatEffect.Grasp, 4, 3, 1860),
            ArceuusCombatDefinition("obj.dmm_ref_legs", 24, 8972, 1862, null, 1864, 0, 5030, 5055, ArceuusCombatEffect.Grasp, 2, 4, 1863),
            ArceuusCombatDefinition("obj.placeholder_snakepet_orange", 0, 8974, 1882, 1883, 1884, 124, 5059, 5031, ArceuusCombatEffect.DarkLure),
            ArceuusCombatDefinition("obj.br_mithril_scimitar", 16, 8977, 1865, null, 1866, 0, 5038, 5036, ArceuusCombatEffect.Demonbane, directDelay = 2),
            ArceuusCombatDefinition("obj.br_willow_bow", 23, 8977, 1867, null, 1868, 0, 5027, 5060, ArceuusCombatEffect.Demonbane, directDelay = 2),
            ArceuusCombatDefinition("obj.br_adamant_scimitar", 30, 8977, 1869, null, 1870, 0, 5053, 5035, ArceuusCombatEffect.Demonbane, directDelay = 2),
        )

    fun maxHit(obj: String): Int? = definitions.firstOrNull { it.obj == obj }?.maxHit
}

private fun seq(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)
private fun spot(id: Int): String = RSCM.getReverseMapping(RSCMType.SPOTANIM, id)
private fun synth(id: Int): String = RSCM.getReverseMapping(RSCMType.SYNTH, id)
