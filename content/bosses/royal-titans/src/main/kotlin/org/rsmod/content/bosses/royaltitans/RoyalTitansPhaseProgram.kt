package org.rsmod.content.bosses.royaltitans

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.BossPhaseClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

/** Adapts native timed phase entries to the encounter's existing LateCycle presentation stage. */
internal class RoyalTitansPhaseProgram
@Inject
constructor(private val deps: BossDeps, private val phaseClock: BossPhaseClock) {
    fun prepare(npc: Npc, target: Player, onRemoved: () -> Unit) {
        val encounter = deps.encounterRegistry.of(npc)
        encounter.onCleanup(onRemoved)
        phaseClock.bind(npc, target)
        encounter.presentation().target = target.uid
        encounter.transitionTo(RoyalTitanMovementPhase.SPAWN_WAIT.key, deps.mapClock.cycle - 1)
    }

    fun movement(npc: Npc): RoyalTitanMovement =
        deps.encounterRegistry.of(npc).let { encounter ->
            encounter.state("royal-titans.movement") {
                RoyalTitanMovement(deps.mapClock, encounter)
            }
        }

    fun teleporting(npc: Npc) {
        val encounter = deps.encounterRegistry.of(npc)
        encounter.presentation().ready = null
        encounter.transitionTo("teleporting", deps.mapClock.cycle - 1)
    }

    fun ready(npc: Npc, action: RoyalTitansAction) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        if (
            deps.encounterRegistry.isCurrent(npc, encounter) &&
                encounter.currentPhaseName != "inactive"
        ) {
            encounter.presentation().ready = action
        }
    }

    fun consume(npc: Npc, target: Player, openingTick: Int): RoyalTitansAction? {
        val encounter = deps.encounterRegistry.of(npc)
        val presentation = encounter.presentation()
        if (
            encounter.currentPhaseName.startsWith("platform-") ||
                encounter.currentPhaseName.startsWith("teleport")
        ) {
            phaseClock.bind(npc, target)
            presentation.target = target.uid
            encounter.transitionTo("arena-opening", openingTick)
        } else retarget(npc, target)
        return presentation.ready.also { presentation.ready = null }
    }

    fun consumeTransition(npc: Npc, target: Player): RoyalTitansAction? {
        retarget(npc, target)
        val presentation = deps.encounterRegistry.of(npc).presentation()
        return presentation.ready.also { presentation.ready = null }
    }

    /** Keyed departure must replace the lifetime before the next pre-input deadline can expire. */
    fun retarget(npc: Npc, target: Player) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        if (encounter.isEnded || encounter.currentPhaseName == "inactive") return
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

    private class Presentation(var target: PlayerUid? = null, var ready: RoyalTitansAction? = null)
}
