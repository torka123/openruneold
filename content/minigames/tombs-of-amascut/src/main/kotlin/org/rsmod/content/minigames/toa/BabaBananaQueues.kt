package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/** Native expiry or armed-occupancy readiness at the existing banana collision boundary. */
internal class BabaBananaQueues(
    private val encounter: BossEncounter,
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val cleanupPile: (BabaBananaPile) -> Unit = {},
    private val stillAttached: () -> Boolean,
) {
    private val owner = queues.createOwner()
    private val pending = linkedMapOf<CoordGrid, Pending>()
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}

    /** Spatial observations in placement order; execution remains owned by the native queue. */
    val piles: Map<CoordGrid, BabaBananaPile>
        get() = pending.mapValues { it.value.pile }

    val isClosed: Boolean
        get() = owner.isClosed

    init {
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    fun schedule(
        pile: BabaBananaPile,
        canTrigger: (BabaBananaPile) -> Boolean,
        resolve: (pile: BabaBananaPile, expired: Boolean) -> Unit,
    ) {
        check(!isClosed) { "Ba-Ba's banana queues have closed." }
        // Native cancellation and remove/reinsert retain the current replacement ordering.
        remove(pile.tile)
        val entry = Pending(pile)
        entry.cancellation =
            owner.add(
                ready = {
                    clock.cycle >= pile.expiresAt ||
                        (clock.cycle >= pile.armedAt && canTrigger(pile))
                }
            ) {
                if (pending[pile.tile] !== entry) return@add
                pending.remove(pile.tile)
                if (live()) resolve(pile, clock.cycle >= pile.expiresAt)
            }
        pending[pile.tile] = entry
    }

    fun remove(tile: CoordGrid): BabaBananaPile? {
        val entry = pending.remove(tile) ?: return null
        entry.cancellation.cancel()
        return entry.pile
    }

    fun process() = process.processOwned(owner)

    fun clear() {
        owner.clear()
        retirePiles()
    }

    fun close() {
        owner.close()
        try {
            retirePiles()
        } finally {
            unregisterEncounter()
            unregisterSession()
        }
    }

    /** Tile cleanup must remain available after native NPC deletion retires this encounter. */
    private fun retirePiles() {
        val retired = pending.values.map { it.pile }
        pending.clear()
        var failure: Throwable? = null
        retired.forEach { pile ->
            try {
                cleanupPile(pile)
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun live(): Boolean = !encounter.isEnded && stillAttached()

    private class Pending(val pile: BabaBananaPile) {
        lateinit var cancellation: WorldQueueList.Cancellation
    }
}
