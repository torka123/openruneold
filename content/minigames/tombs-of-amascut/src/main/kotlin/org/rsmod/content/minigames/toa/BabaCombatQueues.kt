package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Independent native owners preserve forced-Rockfall priority over phase completion. */
internal class BabaCombatQueues(
    private val encounter: BossEncounter,
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val completion = queues.createOwner()
    private val forced = queues.createOwner()
    private val rubbleCooldown = queues.createOwner()
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}
    private var dispatched = false
    private var rubbleReady = true

    /** Presentation/test projection only; native queue readiness owns execution. */
    var completionAt: Int = -1
        private set

    var rubbleAt: Int = -1
        private set

    init {
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    fun armCompletion(deadline: Int, complete: () -> Unit) {
        completion.clear()
        completionAt = deadline
        completion.add(ready = { clock.cycle >= deadline }) {
            completionAt = -1
            if (live()) {
                dispatched = true
                complete()
            }
        }
    }

    fun armForced(deadline: Int, eligible: () -> Boolean, launch: () -> Unit) {
        forced.clear()
        forced.add(ready = { clock.cycle >= deadline && eligible() }) {
            if (live()) {
                dispatched = true
                launch()
            }
        }
    }

    fun processForced(): Boolean = dispatch(forced)

    fun processCompletion(): Boolean = dispatch(completion)

    /** Every actual Rockfall, including the forced bypass, replaces this same native cooldown. */
    fun armRubbleCooldown(deadline: Int) {
        check(!rubbleCooldown.isClosed) { "Ba-Ba's rubble cooldown has closed." }
        rubbleCooldown.clear()
        rubbleReady = false
        rubbleAt = deadline
        rubbleCooldown.add(ready = { clock.cycle >= deadline }) {
            rubbleAt = -1
            if (live()) rubbleReady = true
        }
    }

    /** Only ordinary special selection consumes readiness; phase/completion dispatch does not. */
    fun rubbleAvailable(): Boolean {
        process.processOwned(rubbleCooldown)
        return rubbleReady && !rubbleCooldown.isClosed && live()
    }

    fun clearRubbleCooldown() {
        rubbleCooldown.clear()
        rubbleAt = -1
        rubbleReady = !rubbleCooldown.isClosed
    }

    private fun dispatch(owner: WorldQueueList.Owner): Boolean {
        dispatched = false
        process.processOwned(owner)
        return dispatched
    }

    fun close() {
        completionAt = -1
        rubbleAt = -1
        rubbleReady = false
        completion.close()
        forced.close()
        rubbleCooldown.close()
        unregisterEncounter()
        unregisterSession()
    }

    private fun live(): Boolean = !encounter.isEnded && stillAttached()
}

internal const val BABA_COMBAT_QUEUES = "toa.baba-completion-queues"
