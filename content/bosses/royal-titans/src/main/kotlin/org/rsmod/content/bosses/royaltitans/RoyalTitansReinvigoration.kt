package org.rsmod.content.bosses.royaltitans

import jakarta.inject.Inject
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.anim
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.spot
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** One-shot corpse choreography owned by the native session, including the NPC-free reset gap. */
internal class RoyalTitansReinvigoration
@Inject
constructor(
    private val clock: MapClock,
    private val queues: WorldQueueList,
    private val arena: RoyalTitansArena,
    private val landings: RoyalTitansLandings,
) {
    fun start(run: Run): Boolean {
        if (run.reinvigoration != null) return false
        val state = State()
        val generation = run.generation
        run.reinvigoration = state
        state.unregister = run.session.onCleanup { cancel(run) }
        fun live() =
            !run.retired &&
                !run.session.isEnded &&
                run.generation == generation &&
                run.reinvigoration === state
        for (action in Action.entries) {
            // World queues run before MapClock increments. Apply the marker in LateCycle on
            // the original presentation frame, after player input and existing arena effects.
            val deadline = clock.cycle + action.delay - 1
            lateinit var cancellation: WorldQueueList.Cancellation
            cancellation =
                queues.addCancellable(0, ready = { !live() || clock.cycle >= deadline }) {
                    state.cancellations.remove(cancellation)
                    if (live()) state.ready = action
                }
            state.cancellations += cancellation
        }
        return true
    }

    fun tick(run: Run, respawn: () -> Unit) {
        val state = run.reinvigoration ?: return
        when (state.ready.also { state.ready = null }) {
            Action.COLLAPSE ->
                run.corpses.forEach { (corpse, element) ->
                    corpse.hideAllOps()
                    corpse.anim(
                        anim(
                            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_DEATH
                            else RoyalTitansAssets.ELDRIC_DEATH
                        )
                    )
                    if (element == RoyalElement.FIRE)
                        corpse.spotanim(spot(RoyalTitansAssets.BRANDA_DEATH_GFX), slot = 2)
                    landings.replaceArenaFloors(run, element)
                }
            Action.REMOVE -> {
                run.corpses.keys.toList().forEach { arena.removeNpc(run, it) }
                landings.clearArenaFloors(run)
            }
            Action.RESPAWN -> {
                cancel(run)
                respawn()
            }
            null -> Unit
        }
    }

    fun cancel(run: Run) {
        val state = run.reinvigoration ?: return
        run.reinvigoration = null
        state.cancellations.forEach { it.cancel() }
        state.cancellations.clear()
        state.ready = null
        state.unregister()
        state.unregister = {}
    }

    internal class State {
        val cancellations = mutableListOf<WorldQueueList.Cancellation>()
        var ready: Action? = null
        var unregister: () -> Unit = {}
    }

    internal enum class Action(val delay: Int) {
        COLLAPSE(21),
        REMOVE(26),
        RESPAWN(29),
    }
}
