package org.rsmod.content.bosses.chaosfanatic

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.HitType.Magic
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.ScriptContext

public class ChaosFanatic
@Inject
constructor(
    deps: BossDeps,
    private val unequipOp: WornUnequipOp,
) : BossPluginScript(deps) {
    override fun ScriptContext.startup() {
        registerQuote()
        registerDisarm()
        registerExplosion()
        BossCombat.register(this, spec, deps, ownerSlug = CHAOS_FANATIC_SLUG)
    }

    private fun registerQuote() {
        deps.extensionRegistry.register(QUOTE_EXTENSION) { access, _, _, _ ->
            access.say(deps.random.pick(CHAOS_FANATIC_QUOTES))
        }
    }

    private fun registerDisarm() {
        deps.extensionRegistry.register(DISARM_EXTENSION) { access, npc, target, _ ->
            access.say(deps.random.pick(CHAOS_FANATIC_QUOTES))
            access.anim(CAST_ANIM)
            access.spotanim(DISARM_CAST)

            val encounter = deps.encounterRegistry.of(npc)
            repeat(DISARM_PROJECTILE_COUNT) {
                deps.worldRepo.projAnimSourced(npc, target, DISARM_TRAVEL_TYPE, FANATIC_PROJECTILE)
            }
            deps.worldQueues.add(DISARM_DELAY_TICKS) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                if (!target.isSlotAssigned || target.hitpoints <= 0) return@add
                if (target.coords.level != npc.coords.level || target.coords.chebyshevDistance(npc.coords) > 32) return@add
                target.spotanim(DISARM_IMPACT)
                if (target.inv.isFull()) return@add
                val occupied = target.worn.mapSlots { _, obj -> obj != null }.toList()
                val slot = deps.random.pickOrNull(occupied) ?: return@add
                unequipOp.unequip(target, slot, target.worn, target.inv)
            }
        }
    }

    private fun registerExplosion() {
        deps.extensionRegistry.register(EXPLOSION_EXTENSION) { access, npc, target, _ ->
            val targetTile = target.coords
            val targetUid = target.uid
            val encounter = deps.encounterRegistry.of(npc)
            access.say(deps.random.pick(CHAOS_FANATIC_QUOTES))
            access.anim(CAST_ANIM)
            access.spotanim(EXPLOSION_CAST)

            repeat(EXPLOSION_PROJECTILE_COUNT) {
                deps.worldRepo.projAnimSourced(npc, targetTile, EXPLOSION_TRAVEL_TYPE, FANATIC_PROJECTILE)
            }
            deps.worldQueues.add(EXPLOSION_DELAY_TICKS) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                val player = targetUid.resolve(deps.playerList) ?: return@add
                if (player.coords.level != npc.coords.level || player.coords.chebyshevDistance(npc.coords) > 32) return@add
                deps.worldRepo.spotanimMap(EXPLOSION_IMPACT_TYPE, targetTile)
                if (player.hitpoints <= 0 || player.coords != targetTile) return@add
                player.queueHit(
                    source = npc,
                    delay = 1,
                    type = HitType.Magic,
                    damage = deps.random.of(CHAOS_FANATIC_MAX_HIT + 1),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
        }
    }

    override val spec: BossSpec = CHAOS_FANATIC_SPEC
}

internal val CHAOS_FANATIC_SPEC: BossSpec =
    boss(CHAOS_FANATIC_NPC) {
        stats(attackRate = 2, aggressionRadius = 8)

        val normal =
            ability("magic") {
                include(external(QUOTE_EXTENSION))
                anim(CAST_ANIM)
                spotanim(EXPLOSION_CAST)
                projectile {
                    spotanim = EXPLOSION_TRAVEL
                    config = FANATIC_PROJECTILE_CONFIG
                    hit {
                        damage(0..CHAOS_FANATIC_MAX_HIT).roll()
                        type(Magic)
                        spotanim(EXPLOSION_IMPACT)
                        protectionPrayer = ProtectionPrayer.Standard
                    }
                }
            }
        val disarm = ability("disarm") { include(external(DISARM_EXTENSION)) }
        val explosion = ability("explosion") { include(external(EXPLOSION_EXTENSION)) }

        phase("combat") {
            weightedSelectorRandom(noRepeatBias = 0.25) {
                +random(normal, weight = 6)
                +random(disarm, weight = 1, cooldown = 8)
                +random(explosion, weight = 1, cooldown = 8)
            }
        }
    }

internal val CHAOS_FANATIC_QUOTES: List<String> =
    listOf(
        "BURN!",
        "WEUGH!",
        "Devilish Oxen Roll!!",
        "All your wilderness are belong to them!",
        "AhehHeheuhHhahueHuUEehEahAH!",
        "I shall call him squidgy and he shall be my squidgy!!",
    )

internal const val CHAOS_FANATIC_SLUG: String = "chaos-fanatic"
internal const val CHAOS_FANATIC_NPC: String = "npc.chaos_fanatic"
internal const val CHAOS_FANATIC_MAX_HIT: Int = 31
internal const val EXPLOSION_PROJECTILE_COUNT: Int = 6
internal const val DISARM_PROJECTILE_COUNT: Int = 2
internal const val EXPLOSION_DELAY_TICKS: Int = 4
internal const val DISARM_DELAY_TICKS: Int = 2

private const val QUOTE_EXTENSION: String = "chaos-fanatic.quote"
private const val DISARM_EXTENSION: String = "chaos-fanatic.disarm"
private const val EXPLOSION_EXTENSION: String = "chaos-fanatic.explosion"
private const val CAST_ANIM: String = "seq.human_casting"
private const val DISARM_CAST: String = "spotanim.chaoselemental_spotanim_madness_casting"
private const val DISARM_TRAVEL: String = "spotanim.chaoselemental_spotanim_madness_travel"
private const val DISARM_IMPACT: String = "spotanim.chaoselemental_spotanim_madness_impact"
private const val EXPLOSION_CAST: String = "spotanim.chaoselemental_spotanim_discord_casting"
private const val EXPLOSION_TRAVEL: String = "spotanim.chaoselemental_spotanim_discord_travel"
private const val EXPLOSION_IMPACT: String = "spotanim.chaoselemental_spotanim_discord_impact"

private val DISARM_TRAVEL_TYPE by lazy {
    SpotanimType(DISARM_TRAVEL.asRSCM(RSCMType.SPOTANIM))
}
private val EXPLOSION_TRAVEL_TYPE by lazy {
    SpotanimType(EXPLOSION_TRAVEL.asRSCM(RSCMType.SPOTANIM))
}
private val EXPLOSION_IMPACT_TYPE by lazy {
    SpotanimType(EXPLOSION_IMPACT.asRSCM(RSCMType.SPOTANIM))
}
private val FANATIC_PROJECTILE_CONFIG = org.rsmod.api.bosses.spec.ProjectileConfig()
private val FANATIC_PROJECTILE =
    ProjAnimType(
        startHeight = FANATIC_PROJECTILE_CONFIG.startHeight,
        endHeight = FANATIC_PROJECTILE_CONFIG.endHeight,
        delay = FANATIC_PROJECTILE_CONFIG.startDelay,
        angle = FANATIC_PROJECTILE_CONFIG.angle,
        lengthAdjustment = FANATIC_PROJECTILE_CONFIG.travelTime,
        progress = FANATIC_PROJECTILE_CONFIG.progress,
        stepMultiplier = FANATIC_PROJECTILE_CONFIG.stepMultiplier,
    )
