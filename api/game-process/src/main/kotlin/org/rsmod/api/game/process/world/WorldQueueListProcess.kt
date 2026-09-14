package org.rsmod.api.game.process.world

import jakarta.inject.Inject
import org.rsmod.game.queue.WorldQueueList

public class WorldQueueListProcess @Inject constructor(private val queues: WorldQueueList) {
    public fun process() {
        if (queues.isNotEmpty) {
            queues.process()
        }
    }

    /** Explicit native dispatch preserves the caller's established lifecycle/actor ordering. */
    public fun processOwned(owner: WorldQueueList.Owner) {
        queues.dispatchOwned(owner) { queue ->
            queue.remainingCycles--
            queue.remainingCycles <= 0 && queue.ready()
        }
    }

    private fun WorldQueueList.process() {
        // Owned nodes must also be invisible when selecting the iterator's next node. Otherwise
        // an owned tail could make a normal callback's appended work run one cycle earlier.
        val iterator = iterator(includeOwned = false)
        while (iterator.hasNext()) {
            val queue = iterator.next()

            if (queue.owner != null) continue

            queue.remainingCycles--

            if (queue.remainingCycles <= 0 && queue.ready()) {
                iterator.remove()
                queue.action()
            }
        }
        iterator.cleanUp()
    }
}
