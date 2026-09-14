package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Separate dispatch points preserve completion, forced-style and final-swap ordering. */
internal class AkkhaCombatQueues(
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val completion = queues.createOwner()
    private val style = queues.createOwner()
    private val swap = queues.createOwner()
    private var unregisterSession: () -> Unit = {}
    private var dispatched = false

    var completionAt: Int = -1
        private set

    var styleAt: Int = -1
        private set

    var swapAt: Int = -1
        private set

    init {
        unregisterSession = session.onCleanup(::close)
    }

    fun armCompletion(deadline: Int, complete: () -> Unit) {
        clearCompletion()
        completionAt = deadline
        completion.add(ready = { clock.cycle >= deadline }) {
            completionAt = -1
            dispatch(complete)
        }
    }

    fun armStyle(deadline: Int, ready: () -> Boolean, action: () -> Unit) {
        clearStyle()
        styleAt = deadline
        style.add(ready = { clock.cycle >= deadline && ready() }) {
            styleAt = -1
            dispatch(action)
        }
    }

    fun armSwap(deadline: Int, ready: () -> Boolean, action: () -> Unit) {
        clearSwap()
        swapAt = deadline
        swap.add(ready = { clock.cycle >= deadline && ready() }) {
            swapAt = -1
            dispatch(action)
        }
    }

    fun processCompletion(): Boolean = process(completion)

    fun processStyle(): Boolean = process(style)

    fun processSwap(): Boolean = process(swap)

    fun clearCompletion() {
        completion.clear()
        completionAt = -1
    }

    fun clearStyle() {
        style.clear()
        styleAt = -1
    }

    fun clearSwap() {
        swap.clear()
        swapAt = -1
    }

    fun close() {
        completionAt = -1
        styleAt = -1
        swapAt = -1
        completion.close()
        style.close()
        swap.close()
        unregisterSession()
    }

    private fun process(owner: WorldQueueList.Owner): Boolean {
        dispatched = false
        process.processOwned(owner)
        return dispatched
    }

    private fun dispatch(action: () -> Unit) {
        if (!stillAttached()) return
        dispatched = true
        action()
    }
}
