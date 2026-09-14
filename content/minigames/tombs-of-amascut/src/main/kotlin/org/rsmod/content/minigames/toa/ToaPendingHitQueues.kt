package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList

/** Native deadlines with retained target metadata at the raid's pending-hit dispatch boundary. */
internal class ToaPendingHitQueues(
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stale: (ToaPendingHit) -> Boolean,
    private val deliver: (Npc?, ToaPendingHit) -> Unit,
) {
    private val owner = queues.createOwner()
    private val entries = linkedSetOf<Entry>()
    private var unregisterSession: () -> Unit = {}
    private var processing = false
    private var sourceSnapshot: Npc? = null

    /** A snapshot of payloads, preserving distinct entries even when their values are equal. */
    val pending: List<ToaPendingHit>
        get() = entries.map { it.payload }

    init {
        unregisterSession = session.onCleanup(::close)
    }

    fun schedule(hit: ToaPendingHit) {
        val entry = Entry(hit)
        entry.cancellation =
            owner.add(ready = { stale(entry.payload) || clock.cycle >= entry.payload.landsAt }) {
                if (entries.remove(entry)) {
                    // A previous due payload may have invalidated this generation after the
                    // native readiness snapshot. Metadata retires before any graphic or delivery.
                    val payload = entry.payload
                    if (!stale(payload)) {
                        check(processing)
                        deliver(sourceSnapshot, payload)
                    }
                }
            }
        entries += entry
    }

    fun process(sourceSnapshot: Npc?) {
        check(!processing) { "Pending-hit dispatch is already active." }
        processing = true
        this.sourceSnapshot = sourceSnapshot
        try {
            process.processOwned(owner)
        } finally {
            this.sourceSnapshot = null
            processing = false
        }
    }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        entries.forEach { entry ->
            if (entry.payload.target == oldUid) {
                entry.payload = entry.payload.copy(target = newUid)
            }
        }
    }

    fun removeIf(predicate: (ToaPendingHit) -> Boolean) {
        entries.removeAll { entry ->
            if (!predicate(entry.payload)) false
            else {
                entry.cancellation.cancel()
                true
            }
        }
    }

    /** Room/encounter reset cancels current work while retaining this session's queue owner. */
    fun clear() {
        owner.clear()
        entries.clear()
    }

    fun close() {
        owner.close()
        entries.clear()
        unregisterSession()
    }

    /**
     * Identity is intentional: two value-equal payloads represent two independently queued hits.
     */
    private class Entry(var payload: ToaPendingHit) {
        lateinit var cancellation: WorldQueueList.Cancellation
    }
}
