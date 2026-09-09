package org.rsmod.api.combat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.npc.attackingPlayer
import org.rsmod.api.combat.npc.lastAttack
import org.rsmod.api.combat.player.aggressiveNpc
import org.rsmod.api.combat.player.lastCombat
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitImpact
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

public data class NpcProjectile(
    public val travelSpotanim: String,
    public val type: ProjAnimType,
    public val source: CoordGrid? = null,
)

public data class NpcAttackOverride(
    public val attackAnimation: String? = null,
    public val maxHit: Int? = null,
    public val inaccurateMaxHit: Int? = null,
    public val forceAccurate: Boolean = false,
    public val projectile: NpcProjectile? = null,
    public val impactDelay: Int? = null,
    public val attackRate: Int? = null,
    public val applyModifierOnImpact: Boolean = false,
    public val hitModifier: PlayerHitModifier = StandardPlayerHitModifier,
    public val onAttack: (StandardNpcAccess.(Player) -> Unit)? = null,
    public val onSuccessfulAttack: (StandardNpcAccess.(Player, Int) -> Unit)? = null,
    public val onImpact: ((Npc, HitImpact) -> Unit)? = null,
    /** Exact geometry takes precedence over [projectile]; [impactDelay] still controls the hit. */
    public val projectileFactory: ((Npc, Player) -> ProjAnim)? = null,
    /** Checked before launch and again before the queued hit can produce impact effects. */
    public val hitValidity: HitValidity? = null,
    /** Applied to the rolled damage, before prayer and equipment modifiers. */
    public val damageTransform: ((Int) -> Int)? = null,
    /** Optional native lifecycle scope; queued hits retire when their encounter ends. */
    public val hitContext: HitContext? = null,
    /** Simultaneous independently rolled hits under one native attack gate; null means one. */
    public val hitCount: Int? = null,
) {
    init {
        require(hitCount == null || hitCount > 0) { "Hit count must be positive." }
    }
}

internal fun NpcAttackOverride.rollDamage(
    accurateMaxHit: Int,
    successfulHit: Boolean,
    random: GameRandom,
): Int {
    val maximum = if (successfulHit) accurateMaxHit else inaccurateMaxHit ?: return 0
    val rolled = random.of(0..maximum.coerceAtLeast(0))
    return damageTransform?.invoke(rolled)?.coerceAtLeast(0) ?: rolled
}

public class NvPCombat
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
) {
    public fun attack(
        access: StandardNpcAccess,
        target: Player,
        attack: CombatAttack.NpcAttack,
    ): Unit = attack(access, target, attack, NpcAttackOverride())

    public fun attack(
        access: StandardNpcAccess,
        target: Player,
        attack: CombatAttack.NpcAttack,
        override: NpcAttackOverride,
    ) {
        when (attack) {
            is CombatAttack.NpcMelee -> access.attackMelee(target, attack, override)
            is CombatAttack.NpcRanged -> access.attackRanged(target, attack, override)
            is CombatAttack.NpcMagic -> access.attackMagic(target, attack, override)
        }
    }

    private fun StandardNpcAccess.attackMelee(
        target: Player,
        attack: CombatAttack.NpcMelee,
        override: NpcAttackOverride,
    ) {
        if (!beginAttack(target, override)) return
        playAttackFx(target, override)
        override.onAttack?.invoke(this, target)
        repeat(override.hitCount ?: 1) { index ->
            val successfulHit =
                override.forceAccurate ||
                    accuracy.rollMeleeAccuracy(npc, target, attack.type, random)
            val maxHit = override.maxHit ?: maxHits.getMeleeMaxHit(npc, target, attack.type)
            val damage = override.rollDamage(maxHit, successfulHit, random)
            val projectileDelay =
                if (override.projectile != null || override.projectileFactory != null)
                    spawnProjectile(target, override)
                else MELEE_HIT_DELAY
            val hitDelay = override.impactDelay ?: projectileDelay
            finishAttack(
                target,
                hitDelay,
                HitType.Melee,
                damage,
                successfulHit,
                override,
                index == 0,
            )
        }
    }

    private fun StandardNpcAccess.attackRanged(
        target: Player,
        attack: CombatAttack.NpcRanged,
        override: NpcAttackOverride,
    ) {
        if (!beginAttack(target, override)) return
        playAttackFx(target, override)
        override.onAttack?.invoke(this, target)
        repeat(override.hitCount ?: 1) { index ->
            val projectileDelay = spawnProjectile(target, override)
            val hitDelay = override.impactDelay ?: projectileDelay
            val successfulHit =
                override.forceAccurate || accuracy.rollRangedAccuracy(npc, target, random)
            val maxHit = override.maxHit ?: maxHits.getRangedMaxHit(npc, target)
            val damage = override.rollDamage(maxHit, successfulHit, random)
            finishAttack(
                target,
                hitDelay,
                HitType.Ranged,
                damage,
                successfulHit,
                override,
                index == 0,
            )
        }
    }

    private fun StandardNpcAccess.attackMagic(
        target: Player,
        attack: CombatAttack.NpcMagic,
        override: NpcAttackOverride,
    ) {
        if (!beginAttack(target, override)) return
        playAttackFx(target, override)
        override.onAttack?.invoke(this, target)
        repeat(override.hitCount ?: 1) { index ->
            val projectileDelay = spawnProjectile(target, override)
            val hitDelay = override.impactDelay ?: projectileDelay
            val maxHit =
                override.maxHit
                    ?: if (attack.maxHit > 0) attack.maxHit else maxHits.getMagicMaxHit(npc, target)
            val successfulHit =
                override.forceAccurate || accuracy.rollMagicAccuracy(npc, target, random)
            val damage = override.rollDamage(maxHit, successfulHit, random)
            finishAttack(
                target,
                hitDelay,
                HitType.Magic,
                damage,
                successfulHit,
                override,
                index == 0,
            )
        }
    }

    /** Shared attack gating and attack-rate arming. */
    private fun StandardNpcAccess.beginAttack(
        target: Player,
        override: NpcAttackOverride,
    ): Boolean {
        if (override.hitValidity?.isValid() == false) return false
        if (!canAttack(target)) {
            resetMode()
            return false
        }
        if (actionDelay > mapClock) return false
        if (!npc.isInCombat()) {
            resetMode()
            return false
        }
        actionDelay = mapClock + (override.attackRate ?: npc.attackRate())
        return true
    }

    /** Uses only an animation that exists in this server revision. */
    private fun StandardNpcAccess.playAttackFx(target: Player, override: NpcAttackOverride) {
        if (override.attackAnimation != null) {
            anim(override.attackAnimation)
        } else {
            val configured = npc.visType.paramOrNull(params.attack_anim)?.id
            val animation =
                configured?.takeIf { ServerCacheManager.getAnim(it) != null }
                    ?: npc.visType.standAnim.takeIf { ServerCacheManager.getAnim(it) != null }
            animation?.let { npc.anim(it) }
        }
        npc.visType.paramOrNull(params.attack_sound)?.let(target::soundSynth)
    }

    private fun StandardNpcAccess.spawnProjectile(
        target: Player,
        override: NpcAttackOverride,
    ): Int {
        override.projectileFactory?.let { factory ->
            val proj = factory(npc, target)
            worldRepo.projAnim(proj)
            return proj.serverCycles
        }
        override.projectile?.let { configured ->
            val proj =
                configured.source?.let { source ->
                    ProjAnim.fromCoordToPlayer(
                        source,
                        target,
                        RSCM.getRSCM(configured.travelSpotanim),
                        configured.type,
                    )
                }
                    ?: ProjAnim.fromNpcToPlayer(
                        npc,
                        target,
                        RSCM.getRSCM(configured.travelSpotanim),
                        configured.type,
                    )
            worldRepo.projAnim(proj)
            return proj.serverCycles
        }
        val travelSpot =
            npc.visType.paramOrNull(params.proj_travel) ?: return DEFAULT_PROJECTILE_HIT_DELAY
        val projType = npc.visType.paramOrNull(params.proj_type) ?: DEFAULT_PROJECTILE_TYPE
        val proj = ProjAnim.fromNpcToPlayer(npc, target, travelSpot.id, projType)
        worldRepo.projAnim(proj)
        return proj.serverCycles
    }

    private fun StandardNpcAccess.finishAttack(
        target: Player,
        delay: Int,
        type: HitType,
        damage: Int,
        successfulHit: Boolean,
        override: NpcAttackOverride,
        firstHit: Boolean,
    ) {
        if (firstHit) {
            setAttackVars(target)
            target.queueCombatRetaliate(npc, context = override.hitContext)
        }
        val impactHandler =
            override.onImpact?.let { effect -> HitImpactHandler { impact -> effect(npc, impact) } }
        if (override.applyModifierOnImpact) {
            target.queueImpactHit(
                source = npc,
                delay = delay,
                type = type,
                damage = damage,
                modifier = override.hitModifier,
                impactHandler = impactHandler,
                validity = override.hitValidity,
                context = override.hitContext,
            )
        } else {
            target.queueHit(
                source = npc,
                delay = delay,
                type = type,
                damage = damage,
                modifier = override.hitModifier,
                impactHandler = impactHandler,
                validity = override.hitValidity,
                context = override.hitContext,
            )
        }
        if (firstHit) target.combatPlayDefendAnim()
        if (successfulHit) override.onSuccessfulAttack?.invoke(this, target, delay)
    }

    private fun canAttack(target: Player): Boolean = target.isValidTarget()

    private fun StandardNpcAccess.setAttackVars(target: Player) {
        npc.lastAttack = mapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = mapClock
        target.aggressiveNpc = npc.uid
    }

    private companion object {
        private const val MELEE_HIT_DELAY = 1
        private const val DEFAULT_PROJECTILE_HIT_DELAY = 2

        private val DEFAULT_PROJECTILE_TYPE =
            ProjAnimType(
                startHeight = 43,
                endHeight = 31,
                delay = 51,
                angle = 10,
                lengthAdjustment = 56,
                progress = 15,
                stepMultiplier = 5,
            )
    }
}
