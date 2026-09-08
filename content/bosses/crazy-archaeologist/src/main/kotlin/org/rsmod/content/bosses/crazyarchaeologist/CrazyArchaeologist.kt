package org.rsmod.content.bosses.crazyarchaeologist

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.dsl.whenever
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType.Melee
import org.rsmod.api.bosses.spec.HitType.Ranged
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

public class CrazyArchaeologist
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {
    override fun ScriptContext.startup() {
        registerQuote()
        registerRainOfKnowledge()
        BossCombat.register(
            this,
            spec,
            deps,
            ownerSlug = CRAZY_ARCHAEOLOGIST_SLUG,
            onLethal = { it.say("Ow!") },
        )
    }

    private fun registerQuote() {
        deps.extensionRegistry.register(QUOTE_EXTENSION) { access, _, _, _ ->
            access.say(deps.random.pick(CRAZY_ARCHAEOLOGIST_QUOTES))
        }
    }

    private fun registerRainOfKnowledge() {
        deps.extensionRegistry.register(RAIN_EXTENSION) { access, npc, target, _ ->
            val targetUid = target.uid
            val center = target.coords
            val tiles = rainTiles(center)
            access.say(RAIN_QUOTE)
            access.anim(BOOK_THROW_ANIM)

            val projectile = RAIN_BOOK.asRSCM(RSCMType.SPOTANIM)
            tiles.forEach { tile ->
                deps.bossProjectile(
                    spotanim = projectile,
                    src = npc.coords,
                    target = tile,
                    startHeight = 43,
                    endHeight = 0,
                    delay = 0,
                    travel = 90,
                    curve = 20,
                )
            }

            deps.worldQueues.add(RAIN_DELAY_TICKS) {
                tiles.forEach { deps.worldRepo.spotanimMap(RAIN_IMPACT_TYPE, it) }
                val player = targetUid.resolve(deps.playerList) ?: return@add
                if (player.hitpoints <= 0 || tiles.none { player.coords.chebyshevDistance(it) <= 1 }) {
                    return@add
                }
                player.queueHit(
                    source = npc,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = deps.random.of(CRAZY_SPECIAL_MAX_HIT + 1),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
        }
    }

    override val spec: BossSpec = CRAZY_ARCHAEOLOGIST_SPEC
}

internal val CRAZY_ARCHAEOLOGIST_SPEC: BossSpec =
    boss(CRAZY_ARCHAEOLOGIST_NPC) {
        stats(attackRate = 3, aggressionRadius = 8)

        val melee =
            ability("melee") {
                include(external(QUOTE_EXTENSION))
                anim(MELEE_ANIM)
                hit {
                    damage(0..CRAZY_STANDARD_MAX_HIT).roll()
                    type(Melee)
                    meleeType = MeleeAttackType.Crush
                    protectionPrayer = ProtectionPrayer.Standard
                }
            }
        val ranged =
            ability("ranged") {
                include(external(QUOTE_EXTENSION))
                anim(BOOK_THROW_ANIM)
                projectile {
                    spotanim = NORMAL_BOOK
                    hit {
                        damage(0..CRAZY_STANDARD_MAX_HIT).roll()
                        type(Ranged)
                        protectionPrayer = ProtectionPrayer.Standard
                    }
                }
            }
        val normal =
            ability("normal") {
                include(
                    whenever(
                        Condition.WithinMeleeRange,
                        Effect.Run(melee.name),
                        Effect.Run(ranged.name),
                    )
                )
            }
        val rain = ability("rain_of_knowledge") { include(external(RAIN_EXTENSION)) }

        phase("combat") {
            weightedSelectorRandom(noRepeatBias = 0.25) {
                +random(normal, weight = 4)
                +random(rain, weight = 1, cooldown = 12)
            }
        }
    }

internal val CRAZY_ARCHAEOLOGIST_QUOTES: List<String> =
    listOf(
        "I'm Bellock - respect me!",
        "Get off my site!",
        "No-one messes with Bellock's dig!",
        "These ruins are mine!",
        "Taste my knowledge!",
        "You belong in a museum!",
    )

internal fun rainTiles(center: CoordGrid): List<CoordGrid> =
    listOf(center, center.translate(-1, -1), center.translate(1, 1))

internal const val CRAZY_ARCHAEOLOGIST_SLUG: String = "crazy-archaeologist"
internal const val CRAZY_ARCHAEOLOGIST_NPC: String = "npc.crazy_archaeologist"
internal const val CRAZY_STANDARD_MAX_HIT: Int = 14
internal const val CRAZY_SPECIAL_MAX_HIT: Int = 24
internal const val RAIN_DELAY_TICKS: Int = 4
internal const val RAIN_QUOTE: String = "Rain of knowledge!"

private const val QUOTE_EXTENSION: String = "crazy-archaeologist.quote"
private const val RAIN_EXTENSION: String = "crazy-archaeologist.rain"
private const val BOOK_THROW_ANIM: String = "seq.book_chuck"
private const val MELEE_ANIM: String = "seq.human_unarmedpunch"
private const val NORMAL_BOOK: String = "spotanim.crazy_archaeologist_book"
private const val RAIN_BOOK: String = "spotanim.crazy_archaeologist_book_special"
private const val RAIN_IMPACT: String = "spotanim.firewave_impact"
private val RAIN_IMPACT_TYPE by lazy {
    SpotanimType(RAIN_IMPACT.asRSCM(RSCMType.SPOTANIM))
}
