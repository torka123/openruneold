package org.rsmod.api.bosses.validation

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

data class BossAssetValidation(
    val missing: List<String>,
    val phases: List<String>,
    val assets: List<String> = emptyList(),
) {
    val valid: Boolean
        get() = missing.isEmpty()
}

/** Development-time validation for definitions referenced directly by a loaded [BossSpec]. */
object BossAssetValidator {
    fun validate(spec: BossSpec, extensions: BossExtensionRegistry? = null): BossAssetValidation {
        val assets = linkedSetOf<String>()
        val missing = linkedSetOf<String>()

        fun resolve(key: String, type: RSCMType, cacheCheck: (Int) -> Boolean = { true }) {
            val id =
                runCatching { key.asRSCM(type) }
                    .getOrElse {
                        missing += "$key (no ${type.prefix} RSCM mapping)"
                        return
                    }
            assets += "$key=$id"
            if (!cacheCheck(id)) missing += "$key ($id absent from active server cache)"
        }

        fun npc(key: String) = resolve(key, RSCMType.NPC) { ServerCacheManager.getNpc(it) != null }
        fun seq(key: String) = resolve(key, RSCMType.SEQ) { ServerCacheManager.getAnim(it) != null }
        fun spot(key: String) = resolve(key, RSCMType.SPOTANIM)
        fun proj(key: String) =
            resolve(key, RSCMType.PROJANIM) { ServerCacheManager.getProjectile(it) != null }
        fun synth(key: String) = resolve(key, RSCMType.SYNTH)

        fun effect(value: Effect) {
            when (value) {
                is Effect.Anim -> seq(value.seq)
                is Effect.Sound -> synth(value.synth)
                is Effect.Spotanim -> spot(value.spot)
                is Effect.MapSpotanim -> spot(value.spot)
                is Effect.Hit -> value.spotanim?.let(::spot)
                is Effect.Projectile -> {
                    spot(value.spotanim)
                    value.travel?.let(::proj)
                    value.launch?.let(::spot)
                    value.impact?.let(::spot)
                    value.hit?.let(::effect)
                }
                is Effect.TileAoE -> value.telegraph?.spotanim?.let(::spot)
                is Effect.Debris -> {
                    spot(value.telegraph)
                    value.impact?.let(::spot)
                }
                is Effect.Summon -> npc(value.npc)
                is Effect.Transmog -> npc(value.to)
                is Effect.External ->
                    if (extensions != null && !extensions.contains(value.handler)) {
                        missing += "external.${value.handler} (handler is not registered)"
                    }
                is Effect.Sequence -> value.effects.forEach(::effect)
                is Effect.Parallel -> value.effects.forEach(::effect)
                is Effect.Choose -> value.branches.values.forEach(::effect)
                is Effect.Repeat -> effect(value.effect)
                is Effect.Whenever -> {
                    effect(value.then)
                    effect(value.otherwise)
                }
                is Effect.OnEach -> effect(value.effect)
                is Effect.Broadcast,
                is Effect.Delay,
                is Effect.Freeze,
                is Effect.NoOp,
                is Effect.NpcAttackAnim,
                is Effect.Poison,
                is Effect.Run,
                is Effect.Say,
                is Effect.StatDrain,
                is Effect.TransitionTo -> Unit
            }
        }

        spec.npcTypes.forEach(::npc)
        spec.phases.values.forEach { phase ->
            phase.idleAnim?.let(::seq)
            phase.transmog?.let(::npc)
        }
        spec.abilities.values.forEach(::effect)
        spec.triggers.forEach { effect(it.effect) }
        return BossAssetValidation(missing.toList(), spec.phases.keys.toList(), assets.toList())
    }
}
