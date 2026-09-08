package org.rsmod.content.bosses.obor

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.HitType.Melee
import org.rsmod.api.bosses.spec.HitType.Ranged
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.route.StepFactory
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.Direction
import org.rsmod.game.map.translate
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Current combat loop for Obor's post-Summer-Sweep-Up encounter. */
public class Obor
@Inject
constructor(
    deps: BossDeps,
    private val collision: CollisionFlagMap,
    private val stepFactory: StepFactory,
) : BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, ownerSlug = OBOR_SLUG)
        deps.extensionRegistry.register(KNOCKBACK_EXTENSION) { _, npc, target, _ ->
            val direction = Direction.between(npc.bounds(), target.bounds())
            val destination = target.coords.translate(direction)
            val validated =
                stepFactory.validated(
                    source = target.coords,
                    dest = destination,
                    size = target.size,
                    extraFlag = CollisionFlag.BLOCK_PLAYERS,
                )
            if (validated == destination) {
                PathingEntityCommon.telejump(target, collision, destination)
            }
        }
    }

    override val spec: BossSpec = OBOR_SPEC
}

internal val OBOR_SPEC: BossSpec =
    boss(OBOR_NPC) {
        stats(attackRate = 6, aggressionRadius = 8)

        val melee =
            ability("melee") {
                npcAttackAnim()
                hit {
                    damage(0..22).roll()
                    type(Melee)
                    meleeType = MeleeAttackType.Crush
                }
                include(external(KNOCKBACK_EXTENSION))
            }

        val ranged =
            ability("ranged") {
                npcAttackAnim()
                hit {
                    damage(0..26).roll()
                    type(Ranged)
                    delay = 1
                    protectionPrayer = ProtectionPrayer.Reduce(50)
                }
            }

        phase("combat") {
            selector =
                Selector.Conditional(
                    branches = listOf(Condition.WithinMeleeRange to melee.name),
                    fallback = ranged.name,
                )
        }
    }

internal const val OBOR_SLUG: String = "obor"
internal const val OBOR_NPC: String = "npc.hillgiant_boss"
internal const val OBOR_KEY: String = "obj.hillgiant_boss_key"
internal const val OBOR_CHEST_CLOSED: String = "loc.giantkey_chest_closed"
internal const val OBOR_CHEST_OPEN: String = "loc.giantkey_chest_open"
internal const val OBOR_KILLCOUNT_SKIP_TYPE: String = OBOR_NPC
private const val KNOCKBACK_EXTENSION: String = "obor.knockback"
