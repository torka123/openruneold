package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/**
 * Core presentation outlives the discarded P2 body and keeps its three native dispatch boundaries.
 */
internal class WardenCoreQueues(
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val ejection = queues.createOwner()
    private val exposure = queues.createOwner()
    private val phaseThree = queues.createOwner()
    private var unregisterSession: () -> Unit = {}

    var projectileAt: Int = -1
        private set

    var spawnAt: Int = -1
        private set

    var returnAt: Int = -1
        private set

    var phaseThreeAt: Int = -1
        private set

    val isClosed: Boolean
        get() = ejection.isClosed

    init {
        unregisterSession = session.onCleanup(::close)
    }

    fun armCore(
        projectileAt: Int,
        spawnAt: Int,
        ready: () -> Boolean,
        launchProjectile: () -> Unit,
        spawnCore: () -> Unit,
    ) {
        check(!isClosed) { "Wardens core queues are closed." }
        clearCore()
        this.projectileAt = projectileAt
        this.spawnAt = spawnAt
        scheduleEjection(ready, launchProjectile, spawnCore)
    }

    private fun scheduleEjection(
        ready: () -> Boolean,
        launchProjectile: () -> Unit,
        spawnCore: () -> Unit,
    ) {
        fun enqueue(deadline: Int, action: () -> Unit, consumed: () -> Unit) {
            if (deadline < 0) return
            ejection.add(ready = { clock.cycle >= deadline && ready() }) {
                val revision = ejection.revision
                try {
                    if (stillAttached()) action()
                } catch (failure: Throwable) {
                    // Polling retained unfinished deadlines after a thrown payload. Recreate the
                    // pending pair in its original order; a callback's clear/rearm takes
                    // precedence.
                    if (!isClosed && ejection.revision == revision) {
                        ejection.clear()
                        scheduleEjection(ready, launchProjectile, spawnCore)
                    }
                    throw failure
                }
                if (!isClosed && ejection.revision == revision) consumed()
            }
        }
        // Arm both before dispatch: an overdue call emits the projectile and spawns the core in
        // this order within the same native snapshot. Adding spawn from projectile would defer it.
        enqueue(projectileAt, launchProjectile) { projectileAt = -1 }
        enqueue(spawnAt, spawnCore) { spawnAt = -1 }
    }

    fun processCoreEjection() = process.processOwned(ejection)

    fun armCoreReturn(deadline: Int, ready: () -> Boolean, resolve: () -> Unit) {
        check(!isClosed) { "Wardens core queues are closed." }
        exposure.clear()
        returnAt = deadline
        exposure.add(ready = { clock.cycle >= deadline && ready() }) {
            returnAt = -1
            if (stillAttached()) resolve()
        }
    }

    fun processCoreReturn() = process.processOwned(exposure)

    fun armPhaseThree(deadline: Int, complete: () -> Unit) {
        check(!isClosed) { "Wardens core queues are closed." }
        clearPhaseThree()
        phaseThreeAt = deadline
        phaseThree.add(ready = { clock.cycle >= deadline }) {
            val revision = phaseThree.revision
            try {
                if (stillAttached()) complete()
            } catch (failure: Throwable) {
                if (!isClosed && phaseThree.revision == revision) armPhaseThree(deadline, complete)
                throw failure
            }
            if (!isClosed && phaseThree.revision == revision) phaseThreeAt = -1
        }
    }

    fun processPhaseThree() = process.processOwned(phaseThree)

    fun clearCore() {
        ejection.clear()
        exposure.clear()
        projectileAt = -1
        spawnAt = -1
        returnAt = -1
    }

    fun clearPhaseThree() {
        phaseThree.clear()
        phaseThreeAt = -1
    }

    fun clear() {
        clearCore()
        clearPhaseThree()
    }

    fun close() {
        ejection.close()
        exposure.close()
        phaseThree.close()
        projectileAt = -1
        spawnAt = -1
        returnAt = -1
        phaseThreeAt = -1
        unregisterSession()
    }
}
