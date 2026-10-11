package org.rsmod.api.bosses.runtime

import jakarta.inject.Inject
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitContext

/**
 * Opt-in clock for declared timed phases whose actor need not be in attack range. Uses the existing
 * encounter, cancellable world queue and native NPC access/interpreter. It never selects or
 * launches ordinary attacks and never changes reach or movement.
 */
class BossPhaseClock
@Inject
constructor(private val deps: BossDeps, private val accessLauncher: StandardNpcAccessLauncher) {
    fun bind(npc: Npc, target: Player) {
        val encounter = deps.encounterRegistry.of(npc)
        val lifetime = HitContext.capture(npc, target)
        fun live(): Boolean =
            deps.encounterRegistry.isCurrent(npc, encounter) &&
                npc.isVisible &&
                lifetime.isValid() &&
                target.isSlotAssigned &&
                target.hitpoints > 0 &&
                !target.pendingLogout &&
                !target.loggingOut
        encounter.bindPhaseClock {
            val phase = encounter.currentPhase
            val duration = phase?.exitAfter
            val next = phase?.nextPhase?.let(encounter.spec.phases::get)
            if (duration != null && next != null && encounter.phaseEnteredTick >= 0 && live()) {
                require(duration > 0) { "Timed phase clocks require a positive exitAfter." }
                val deadline = encounter.phaseEnteredTick + duration
                var unregister: () -> Unit = {}
                val cancellation =
                    deps.worldQueues.addCancellable(
                        0,
                        ready = { !live() || (deps.mapClock.cycle >= deadline && !npc.isBusy) },
                    ) {
                        unregister()
                        if (live()) {
                            encounter.transitionTo(next.name, deps.mapClock.cycle)
                            next.entry?.let(encounter.spec.abilities::get)?.let { entry ->
                                accessLauncher.launch(npc) {
                                    EffectInterpreter(npc, target, encounter.spec, encounter, deps)
                                        .run(this, entry)
                                }
                            }
                        }
                    }
                unregister = encounter.onCleanup(cancellation::cancel)
                encounter.ownPhaseDeadline {
                    cancellation.cancel()
                    unregister()
                }
            }
        }
    }
}
