package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** The row already shown by the cast; subsequent weak-lane selection cannot change its release. */
internal data class BabaRollingRow(val set: Int, val weakLane: Int)

/** Native release-before-cast dispatch following the existing boulder movement pass. */
internal class BabaRollingQueues(
    private val encounter: BossEncounter,
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val castOwner = queues.createOwner()
    private val releaseOwner = queues.createOwner()
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}

    /** Read-only observations; native owned entries determine whether work is due. */
    var castAt: Int = -1
        private set

    var releaseAt: Int = -1
        private set

    var pendingRow: BabaRollingRow? = null
        private set

    val isClosed: Boolean
        get() = castOwner.isClosed

    init {
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    fun armCast(deadline: Int, eligible: () -> Boolean, cast: () -> Unit) {
        check(!isClosed) { "Ba-Ba's rolling queues have closed." }
        clearCast()
        castAt = deadline
        val revision = castOwner.revision
        castOwner.add(ready = { clock.cycle >= deadline && eligible() }) {
            try {
                if (live()) cast()
            } finally {
                if (castOwner.revision == revision) castAt = -1
            }
        }
    }

    fun armRelease(deadline: Int, row: BabaRollingRow, release: (BabaRollingRow) -> Unit) {
        check(!isClosed) { "Ba-Ba's rolling queues have closed." }
        clearRelease()
        releaseAt = deadline
        pendingRow = row
        val revision = releaseOwner.revision
        releaseOwner.add(ready = { clock.cycle >= deadline }) {
            try {
                // Keep the cast observation available throughout the existing spawn/trace body.
                if (live()) release(row)
            } finally {
                if (releaseOwner.revision == revision) {
                    releaseAt = -1
                    pendingRow = null
                }
            }
        }
    }

    fun processRelease() = process.processOwned(releaseOwner)

    fun processCast() = process.processOwned(castOwner)

    fun clearCast() {
        castOwner.clear()
        castAt = -1
    }

    fun clearRelease() {
        releaseOwner.clear()
        releaseAt = -1
        pendingRow = null
    }

    fun clear() {
        clearCast()
        clearRelease()
    }

    fun close() {
        castOwner.close()
        releaseOwner.close()
        castAt = -1
        releaseAt = -1
        pendingRow = null
        unregisterEncounter()
        unregisterSession()
    }

    private fun live(): Boolean = !encounter.isEnded && stillAttached()
}
