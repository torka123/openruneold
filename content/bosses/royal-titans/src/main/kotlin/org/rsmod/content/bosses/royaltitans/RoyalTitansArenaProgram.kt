package org.rsmod.content.bosses.royaltitans

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.BossPhaseClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

/** Adapts native timed phase entries to the encounter's existing LateCycle presentation stage. */
internal class RoyalTitansArenaProgram
@Inject
constructor(private val deps: BossDeps, private val phaseClock: BossPhaseClock) {
    fun prepare(npc: Npc) {
        deps.encounterRegistry.of(npc)
    }

    fun ready(npc: Npc, action: RoyalTitansArenaAction) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        if (
            deps.encounterRegistry.isCurrent(npc, encounter) &&
                encounter.currentPhaseName != "inactive"
        ) {
            encounter.presentation().ready = action
        }
    }

    fun consume(npc: Npc, target: Player, openingTick: Int): RoyalTitansArenaAction? {
        val encounter = deps.encounterRegistry.of(npc)
        val presentation = encounter.presentation()
        if (encounter.currentPhaseName == "platform") {
            phaseClock.bind(npc, target)
            presentation.target = target.uid
            encounter.transitionTo("arena-opening", openingTick)
        } else retarget(npc, target)
        return presentation.ready.also { presentation.ready = null }
    }

    /** Keyed departure must replace the lifetime before the next pre-input deadline can expire. */
    fun retarget(npc: Npc, target: Player) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        if (encounter.isEnded || encounter.currentPhaseName in setOf("platform", "inactive")) return
        val presentation = encounter.presentation()
        if (presentation.target != target.uid) {
            phaseClock.bind(npc, target)
            presentation.target = target.uid
        }
    }

    fun retire(npc: Npc) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        if (encounter.isEnded) return
        encounter.presentation().ready = null
        encounter.presentation().target = null
        encounter.transitionTo("inactive", deps.mapClock.cycle)
    }

    private fun BossEncounter.presentation(): Presentation =
        state("royal-titans.arena-presentation") { Presentation() }

    private class Presentation(
        var target: PlayerUid? = null,
        var ready: RoyalTitansArenaAction? = null,
    )
}
