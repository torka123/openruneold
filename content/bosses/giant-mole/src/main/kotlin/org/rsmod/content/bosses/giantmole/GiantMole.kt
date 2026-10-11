package org.rsmod.content.bosses.giantmole

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.HitType.Melee
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The server's custom Giant Mole encounter.
 *
 * Unlike Old School RuneScape, these moles never burrow or relocate during combat. The normal
 * melee attack, death/respawn lifecycle, and registered Giant Mole drop table remain intact.
 */
public class GiantMole
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {
    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, ownerSlug = GIANT_MOLE_SLUG)
    }

    override val spec: BossSpec = GIANT_MOLE_SPEC
}

internal val GIANT_MOLE_SPEC: BossSpec =
    boss(GIANT_MOLE_NPC) {
        stats(attackRate = 4, aggressionRadius = 0)

        val melee =
            ability("melee") {
                npcAttackAnim()
                hit {
                    damage(0..21).roll()
                    type(Melee)
                    meleeType = MeleeAttackType.Crush
                }
            }

        phase("combat") {
            weightedSelectorRandom(noRepeatBias = 0.0) {
                +random(melee, weight = 1)
            }
        }
    }

internal const val GIANT_MOLE_SLUG: String = "giant-mole"
internal const val GIANT_MOLE_NPC: String = "npc.mole_giant"
internal const val GIANT_MOLE_HILL: String = "loc.mole_hill"
internal const val SPADE: String = "obj.spade"
