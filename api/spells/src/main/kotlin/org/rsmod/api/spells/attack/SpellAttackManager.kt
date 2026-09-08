package org.rsmod.api.spells.attack

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.combat.formulas.attributes.AhrimPassiveState
import org.rsmod.api.combat.formulas.attributes.FrostweaverState
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.consumedRune
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.config.constants
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.proj.ProjAnim

public class SpellAttackManager
@Inject
constructor(
    private val manager: PlayerAttackManager,
    private val runes: MagicRuneManager,
    private val random: GameRandom,
) {
    private val ProtectedAccess.autocastEnabled by boolVarBit("varbit.autocast_set")

    /**
     * Checks and **consumes** any requirements for [CombatAttack.Spell.spell], delegating to
     * [MagicRuneManager.attemptCast].
     *
     * **Notes:**
     * - If the spell cannot be cast, this function automatically calls [stopCombat] to avoid any
     *   lingering combat interactions.
     * - If the spell can be cast, this function automatically calls [giveCastXp], awarding the
     *   player the [MagicSpell.castXp] cast experience from [attack].
     * - This does **not** include the experience for any damage that should be dealt to the target.
     *   That is handled separately by calling [giveCombatXp].
     * - This function returns a [MagicRuneManager.CastResult], providing useful context. For
     *   example, if the result is an instance of [MagicRuneManager.CastResult.Success.Consumed], it
     *   can be used to check whether a Sunfire rune was used.
     *
     * #### Example Usage:
     * ```
     * val castResult = attemptCast(this, attack)
     * if (castResult.isFailure()) {
     *  // Combat is cleared inside `attemptCast`, so we can just return.
     *  return
     * }
     * // `consumedRune` casts `castResult` to `CastResult.Success.Consumed` (via Kotlin contracts)
     * // if applicable, allowing `usedSunfire` to be accessed safely.
     * val usedSunfireRune = castResult.consumedRune() && castResult.usedSunfire
     * ```
     *
     * @see [MagicRuneManager.attemptCast]
     * @see [stopCombat]
     */
    public fun attemptCast(
        source: ProtectedAccess,
        attack: CombatAttack.Spell,
    ): MagicRuneManager.CastResult {
        val castResult = runes.attemptCast(source.player, attack.spell)

        if (castResult.isFailure()) {
            AhrimPassiveState.clear(source.player)
            stopCombat(source)
            return castResult
        }

        giveCastXp(source, attack)
        val player = source.player
        val fullAhrim =
            EquipmentChecks.isAhrimSet(player.hat, player.torso, player.legs, player.righthand)
        AhrimPassiveState.begin(
            player,
            fullAhrim && random.randomBoolean(AhrimPassiveState.PROC_DENOMINATOR),
        )
        return castResult
    }

    /** Returns whether combat is currently continuing from the player's selected autocast. */
    public fun isAutocasting(source: ProtectedAccess): Boolean = source.autocastEnabled

    /** A non-consuming, message-free check for transparent spell substitutions. */
    public fun canCastSilently(source: ProtectedAccess, spell: MagicSpell): Boolean =
        runes.canCastSpellSilently(source.player, spell)

    /**
     * Grants Magic experience to the player based on [MagicSpell.castXp] from the given
     * [CombatAttack.Spell.spell].
     */
    public fun giveCastXp(access: ProtectedAccess, attack: CombatAttack.Spell) {
        access.statAdvance("stat.magic", attack.spell.castXp)
    }

    /**
     * Delegates to [PlayerAttackManager.continueCombat] if the [source] player is autocasting.
     * Otherwise, it breaks off any further combat interactions with the [target].
     *
     * _Note: The continued combat interaction will use the player's autocast spell, not necessarily
     * the spell cast during this interaction._
     *
     * @see [PlayerAttackManager.continueCombat]
     */
    public fun continueCombatIfAutocast(source: ProtectedAccess, target: PathingEntity) {
        if (!source.autocastEnabled) {
            manager.stopCombat(source.player)
            return
        }
        manager.continueCombat(source.player, target)
    }

    /** @see [PlayerAttackManager.stopCombat] */
    public fun stopCombat(access: ProtectedAccess) {
        manager.stopCombat(access.player)
    }

    /** @see [PlayerAttackManager.giveCombatXp] */
    public fun giveCombatXp(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        damage: Int,
    ) {
        manager.giveCombatXp(source.player, target, attack, damage)
    }

    /**
     * Determines whether the magic spell cast by [source] will splash on [target].
     *
     * This is a helper function that inverts the result of [rollSpellAccuracy], returning `true` if
     * the spell misses (i.e., "splashes") and `false` if it hits successfully.
     *
     * @see [rollSpellAccuracy]
     */
    public fun rollSplash(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        castResult: MagicRuneManager.CastResult,
    ): Boolean = !rollSpellAccuracy(source, target, attack, castResult)

    /** @see [PlayerAttackManager.rollSpellAccuracy] */
    public fun rollSpellAccuracy(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        castResult: MagicRuneManager.CastResult,
    ): Boolean =
        manager.rollSpellAccuracy(
            source = source.player,
            target = target,
            spell = attack.spell.obj,
            spellbook = attack.spell.spellbook,
            sunfireRune = castResult.consumedRune() && castResult.usedSunfire,
        )

    /** @see [PlayerAttackManager.rollSpellMaxHit] */
    public fun rollMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        castResult: MagicRuneManager.CastResult,
        baseMaxHit: Int,
        attackRate: Int = constants.combat_spell_attackrate,
    ): Int =
        manager.rollSpellMaxHit(
            source = source.player,
            target = target,
            spell = attack.spell.obj,
            spellbook = attack.spell.spellbook,
            baseMaxHit = baseMaxHit,
            attackRate = attackRate,
            sunfireRune = castResult.consumedRune() && castResult.usedSunfire,
        )

    /** @see [PlayerAttackManager.calculateSpellMaxHit] */
    public fun calculateMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        cast: MagicRuneManager.CastResult,
        baseMaxHit: Int,
        attackRate: Int = constants.combat_spell_attackrate,
    ): IntRange =
        manager.calculateSpellMaxHit(
            source = source.player,
            target = target,
            spell = attack.spell.obj,
            spellbook = attack.spell.spellbook,
            baseMaxHit = baseMaxHit,
            attackRate = attackRate,
            sunfireRune = cast.consumedRune() && cast.usedSunfire,
        )

    /**
     * Queues a **non-splash** hit on [target]. Unlike splash hits, this will queue auto-retaliation
     * with a delay of [hitDelay] cycles.
     *
     * _To queue a splash hit, use [queueSplashHit]._
     *
     * @see [PlayerAttackManager.queueMagicHit]
     */
    public fun queueMagicHit(
        source: ProtectedAccess,
        target: PathingEntity,
        spell: ItemServerType,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int = 1 + (clientDelay / 30),
        effectHandler: HitImpactHandler? = null,
    ): Hit {
        val fullBlueMoon =
            EquipmentChecks.isBlueMoonSet(
                source.player.hat,
                source.player.torso,
                source.player.legs,
                source.player.righthand,
            )
        if (fullBlueMoon) {
            val denominator =
                when {
                    MagicSpellChecks.isGraspSpell(spell) -> FrostweaverState.GRASP_DENOMINATOR
                    MagicSpellChecks.isBindSpell(spell) || MagicSpellChecks.isIceSpell(spell) ->
                        FrostweaverState.STANDARD_OR_ICE_DENOMINATOR
                    else -> null
                }
            if (denominator != null && random.randomBoolean(denominator)) {
                FrostweaverState.trigger(source.player)
            }
        }

        val ahrimTriggered = AhrimPassiveState.consume(source.player)
        val iceBindTicks = MagicSpellChecks.iceBindTicks(spell)
        val builtInImpactHandler =
            if (ahrimTriggered || iceBindTicks != null) {
                HitImpactHandler { impact ->
                    val impacted = impact.target
                    if (ahrimTriggered) {
                        when (impacted) {
                            is Npc ->
                                impacted.strengthLvl =
                                    (impacted.strengthLvl - AhrimPassiveState.STRENGTH_DRAIN)
                                        .coerceAtLeast(0)
                            is Player ->
                                impacted.statSub(
                                    "stat.strength",
                                    constant = AhrimPassiveState.STRENGTH_DRAIN,
                                    percent = 0,
                                )
                        }
                        impacted.spotanim("spotanim.barrows_ahirm_blighted_aura")
                    }
                    if (iceBindTicks != null && impact.damage >= 1) {
                        when (impacted) {
                            is Player -> CombatEffects.freeze(impacted, iceBindTicks)
                            is Npc -> CombatEffects.bind(impacted, iceBindTicks)
                        }
                    }
                }
            } else {
                null
            }
        val impactHandler =
            when {
                builtInImpactHandler == null -> effectHandler
                effectHandler == null -> builtInImpactHandler
                else ->
                    HitImpactHandler { impact ->
                        builtInImpactHandler.onImpact(impact)
                        effectHandler.onImpact(impact)
                    }
            }

        return manager.queueMagicHit(
            source = source.player,
            target = target,
            spell = spell,
            damage = damage,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
            retaliationDelay = hitDelay,
            impactHandler = impactHandler,
        )
    }

    /**
     * Queues Twinflame's echo without awarding XP or rolling independent armour-set effects. The
     * echo's damage has already been derived from the first successful hit.
     */
    public fun queueMagicEchoHit(
        source: ProtectedAccess,
        target: PathingEntity,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int,
    ): Hit =
        manager.queueMagicHit(
            source = source.player,
            target = target,
            // The echo is a derived damage-only follow-up, not another spell cast. Leaving the
            // spell unset prevents target mechanics from rerolling accuracy, elemental weakness,
            // resource effects, or area splashes a second time.
            spell = null,
            damage = damage,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
            retaliationDelay = hitDelay,
            notifyAttackLaunch = false,
        )

    /** @see [PlayerAttackManager.queueSplashHit] */
    public fun queueSplashHit(
        source: ProtectedAccess,
        target: PathingEntity,
        spell: ItemServerType,
        clientDelay: Int,
        hitDelay: Int = 1 + (clientDelay / 30),
    ): Hit {
        AhrimPassiveState.clear(source.player)
        return manager.queueSplashHit(
            source = source.player,
            target = target,
            spell = spell,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
        )
    }

    /** @see [PlayerAttackManager.playMagicHitFx] */
    public fun playHitFx(
        source: ProtectedAccess,
        target: PathingEntity,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
        hitSpot: String?,
        hitSpotHeight: Int,
        hitSound: String?,
    ): Unit =
        manager.playMagicHitFx(
            source = source.player,
            target = target,
            clientDelay = clientDelay,
            castSound = castSound,
            soundRadius = soundRadius,
            hitSpot = hitSpot,
            hitSpotHeight = hitSpotHeight,
            hitSound = hitSound,
        )

    /** @see [PlayerAttackManager.playMagicSplashFx] */
    public fun playSplashFx(
        source: ProtectedAccess,
        target: PathingEntity,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
    ): Unit =
        manager.playMagicSplashFx(
            source = source.player,
            target = target,
            clientDelay = clientDelay,
            castSound = castSound,
            soundRadius = soundRadius,
        )

    /** @see [PlayerAttackManager.spawnProjectile] */
    public fun spawnProjectile(
        source: ProtectedAccess,
        target: PathingEntity,
        spotanim: String,
        projanim: String,
    ): ProjAnim = manager.spawnProjectile(source.player, target, spotanim, projanim)

    /** @see [PlayerAttackManager.spawnProjectileWithImpactOffset] */
    public fun spawnProjectileWithImpactOffset(
        source: ProtectedAccess,
        target: PathingEntity,
        spotanim: String,
        projanim: String,
        impactOffset: Int,
    ): ProjAnim =
        manager.spawnProjectileWithImpactOffset(source.player, target, spotanim, projanim, impactOffset)
}
