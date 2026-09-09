package org.rsmod.api.bosses.spec

import dev.openrune.types.NpcMode
import org.rsmod.api.combat.NpcHitParameters
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType

public data class StatDrainEntry(
    val stat: String,
    val amount: Int,
    val chance: Int = 1,
    val outOf: Int = 1,
)

public sealed interface ImpactEffect {
    public val chance: Int
    public val outOf: Int
    public val protection: ImpactProtection

    public data class Poison(
        val damage: Int,
        override val chance: Int = 1,
        override val outOf: Int = 1,
        override val protection: ImpactProtection = ImpactProtection.None,
    ) : ImpactEffect

    public data class Freeze(
        val ticks: Int,
        override val chance: Int = 1,
        override val outOf: Int = 1,
        override val protection: ImpactProtection = ImpactProtection.None,
    ) : ImpactEffect

    public data class StatDrain(
        val stats: List<String>,
        val amount: Int,
        override val chance: Int = 1,
        override val outOf: Int = 1,
        override val protection: ImpactProtection = ImpactProtection.None,
    ) : ImpactEffect
}

public sealed interface ImpactProtection {
    public data object None : ImpactProtection

    public data object KingBlackDragon : ImpactProtection
}

sealed interface Effect {

    /**
     * Ordinary attacks delegate to NvPCombat, including its native presentation and retaliation.
     */
    data class NpcAttack(
        val attack: CombatAttack.NpcAttack,
        val maxHit: Int? = null,
        val animation: String? = null,
        val projectile: NpcProjectile? = null,
        val impactDelay: Int? = null,
        val impact: String? = null,
        val protectionPrayer: ProtectionPrayer = ProtectionPrayer.Standard,
        val accuracy: AccuracyMode = AccuracyMode.Standard,
        /** Optional named parameter resolver; attack execution and cadence remain native/DSL. */
        val parameters: String? = null,
        val parameterData: Any? = null,
        val hitCount: Int = 1,
        val hits: List<NpcHitParameters>? = null,
    ) : Effect {
        init {
            require(hitCount > 0) { "Hit count must be positive." }
            require(hits == null || hits.isNotEmpty()) { "Explicit hit list must not be empty." }
        }
    }

    data class Anim(val seq: String, val delay: Int = 0) : Effect

    data object NpcAttackAnim : Effect

    data class Say(val text: String) : Effect

    data class Sound(val synth: String, val radius: Int = 10) : Effect

    data class Spotanim(val spot: String, val height: Int = 0, val delay: Int = 0) : Effect

    data class MapSpotanim(
        val spot: String,
        val at: TargetExpr,
        val height: Int = 0,
        val delay: Int = 0,
    ) : Effect

    data class Broadcast(val text: String, val radius: Int = 15) : Effect

    data class Delay(val ticks: Int) : Effect

    /** Declaratively sets the upstream per-encounter cadence override; null restores phase rate. */
    data class SetAttackRate(val ticks: Int?) : Effect {
        init {
            require(ticks == null || ticks > 0) { "Attack rate must be positive." }
        }
    }

    data object NoOp : Effect

    data class Hit(
        val target: TargetExpr = TargetExpr.CurrentTarget,
        val damage: DamageExpr,
        val type: HitType,
        val delay: Int = 0,
        val spotanim: String? = null,
        val spotanimHeight: Int = 0,
        val accuracy: AccuracyMode = AccuracyMode.Standard,
        val meleeType: MeleeAttackType? = null,
        val protectionPrayer: ProtectionPrayer = ProtectionPrayer.Standard,
        val impactEffects: List<ImpactEffect> = emptyList(),
    ) : Effect

    data class Projectile(
        val spotanim: String,
        val travel: String? = null,
        val config: ProjectileConfig? = null,
        val target: TargetExpr = TargetExpr.CurrentTarget,
        val launch: String? = null,
        val impact: String? = null,
        val hit: Hit? = null,
    ) : Effect

    data class TileAoE(
        val center: TargetExpr,
        val radius: Int,
        val telegraph: TelegraphSpec? = null,
        val damage: DamageExpr,
        val type: HitType,
    ) : Effect

    /**
     * A telegraphed, dodgeable area attack (e.g. Scurrius' falling rocks): a [telegraph] spotanim
     * is shown on a set of tiles, then after [windup] ticks any player standing on one of those
     * tiles takes [damage]. Tiles are every player within [targetRadius] of the boss (so each is
     * targeted) plus random scatter within [scatterRadius] of the boss, up to a total drawn from
     * [count].
     */
    data class Debris(
        val telegraph: String,
        val damage: DamageExpr,
        val type: HitType = HitType.Typeless,
        val impact: String? = null,
        val windup: Int = 3,
        val targetRadius: Int = 15,
        val scatterRadius: Int = 5,
        val count: IntRange = 1..1,
        val center: TargetExpr = TargetExpr.Self,
    ) : Effect

    data class Summon(
        val npc: String,
        val count: Int = 1,
        val radius: Int = 3,
        val centeredOn: TargetExpr = TargetExpr.Self,
        val mode: NpcMode? = null,
    ) : Effect

    data class Transmog(val to: String, val durationTicks: Int) : Effect

    data class Poison(val damage: Int, val chance: Int = 1, val outOf: Int = 1) : Effect

    data class Freeze(val ticks: Int, val chance: Int = 1, val outOf: Int = 1) : Effect

    data class StatDrain(val entries: List<StatDrainEntry>) : Effect {
        init {
            require(entries.isNotEmpty()) { "StatDrain requires at least one entry." }
        }
    }

    data class Run(val ability: String) : Effect

    data class TransitionTo(val phase: String) : Effect

    data class External(val handler: String, val params: Any? = null) : Effect

    data class Sequence(val effects: List<Effect>) : Effect

    data class Parallel(val effects: List<Effect>) : Effect

    data class Choose(val selector: Selector, val branches: Map<String, Effect>) : Effect

    data class Repeat(val times: Int, val effect: Effect, val gap: Int = 0) : Effect

    data class Whenever(val condition: Condition, val then: Effect, val otherwise: Effect = NoOp) :
        Effect

    data class OnEach(val targets: TargetExpr, val effect: Effect) : Effect
}

enum class HitType {
    Melee,
    Ranged,
    Magic,
    Dragonfire,
    DragonfireMetal,
    WyvernIce,
    KbdDragonfire,
    KbdSpecialDragonfire,
    Typeless,
}

enum class AccuracyMode {
    Standard,
    Always,
}

sealed interface ProtectionPrayer {
    data object Standard : ProtectionPrayer

    data object Bypass : ProtectionPrayer

    data class Reduce(val percent: Int) : ProtectionPrayer {
        init {
            require(percent in 0..100) { "Protection-prayer reduction must be between 0 and 100." }
        }
    }
}
