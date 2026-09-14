package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Native owners dispatched at Zebak's existing split and visual-expiry LateCycle boundaries. */
internal class ZebakAttackQueues(
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val valid: (generation: Int) -> Boolean,
    private val removeVisual: (ZebakSplitVisualRuntime) -> Unit,
) {
    private val splits = queues.createOwner()
    private val expiry = queues.createOwner()
    private val splitPayloads = mutableListOf<ZebakSplitProjectileRuntime>()
    private val visualPayloads = mutableListOf<ZebakSplitVisualRuntime>()

    // Read-only payload observations; the native owners alone decide when each entry executes.
    val pendingSplits: List<ZebakSplitProjectileRuntime>
        get() = splitPayloads

    val pendingVisuals: List<ZebakSplitVisualRuntime>
        get() = visualPayloads

    val isClosed: Boolean
        get() = splits.isClosed && expiry.isClosed

    fun scheduleSplit(
        split: ZebakSplitProjectileRuntime,
        launch: (ZebakSplitProjectileRuntime) -> Unit,
    ) {
        splitPayloads += split
        splits.add(ready = { clock.cycle >= split.splitAt }) {
            splitPayloads.remove(split)
            if (valid(split.generation)) launch(split)
        }
    }

    fun scheduleVisual(visual: ZebakSplitVisualRuntime) {
        visualPayloads += visual
        expiry.add(ready = { !valid(visual.generation) || clock.cycle >= visual.removesAt }) {
            visualPayloads.remove(visual)
            removeVisual(visual)
        }
    }

    fun processSplits() = process.processOwned(splits)

    fun processVisuals() = process.processOwned(expiry)

    fun close() {
        splits.close()
        expiry.close()
        splitPayloads.clear()
        val visuals = visualPayloads.toList()
        visualPayloads.clear()
        visuals.forEach(removeVisual)
    }
}
