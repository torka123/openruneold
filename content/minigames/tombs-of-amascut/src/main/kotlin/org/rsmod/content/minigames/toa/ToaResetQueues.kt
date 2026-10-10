package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Native wipe deadlines retain raid insertion order while the manager owns wipe/pause policy. */
internal class ToaResetQueues(
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val canDispatch: (InstanceId) -> Boolean,
    private val reset: (InstanceId) -> Unit,
) {
    private val owner = queues.createOwner()
    private val entries = linkedMapOf<InstanceId, Entry>()

    val pending: Map<InstanceId, Int>
        get() = entries.mapValues { it.value.deadline }

    fun schedule(session: InstanceSession, deadline: Int) {
        check(!owner.isClosed) { "Raid reset queue is closed." }
        check(!session.isEnded) { "Cannot schedule a reset for an ended instance: ${session.id}" }
        val existing = entries[session.id]
        if (existing?.session === session) {
            existing.deadline = deadline
            return
        }
        if (existing != null) cancel(existing)
        val entry = Entry(session, deadline)
        entry.unregisterSession = session.onCleanup { cancel(entry) }
        entry.cancellation =
            owner.add(ready = { clock.cycle >= entry.deadline && canDispatch(session.id) }) {
                if (entries[session.id] === entry) {
                    entries.remove(session.id)
                    entry.unregisterSession()
                    reset(session.id)
                }
            }
        entries[session.id] = entry
    }

    fun cancel(id: InstanceId) {
        entries[id]?.let(::cancel)
    }

    fun process() = process.processOwned(owner)

    fun clear() {
        owner.clear()
        val cancelled = entries.values.toList()
        entries.clear()
        cancelled.forEach { it.unregisterSession() }
    }

    fun close() {
        owner.close()
        val cancelled = entries.values.toList()
        entries.clear()
        cancelled.forEach { it.unregisterSession() }
    }

    private fun cancel(entry: Entry) {
        // A previous session's cleanup must not cancel a replacement sharing its logical id.
        if (entries[entry.session.id] !== entry) return
        entries.remove(entry.session.id)
        entry.cancellation.cancel()
        entry.unregisterSession()
    }

    private class Entry(val session: InstanceSession, var deadline: Int) {
        lateinit var cancellation: WorldQueueList.Cancellation
        var unregisterSession: () -> Unit = {}
    }
}
