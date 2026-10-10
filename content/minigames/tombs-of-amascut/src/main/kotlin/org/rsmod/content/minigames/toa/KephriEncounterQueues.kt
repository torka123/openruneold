package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Session-owned cadence owners at Kephri's existing LateCycle dispatch boundaries. */
internal class KephriEncounterQueues(
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val recovery = queues.createOwner()
    private val medic = queues.createOwner()
    private val death = queues.createOwner()
    private var unregisterSession: () -> Unit = {}

    var recoveryAt: Int = -1
        private set

    var medicAt: Int = -1
        private set

    var deathAt: Int = -1
        private set

    val isClosed: Boolean
        get() = recovery.isClosed

    init {
        unregisterSession = session.onCleanup(::close)
    }

    fun armRecovery(deadline: Int, ready: () -> Boolean, action: () -> Unit) =
        arm(recovery, deadline, ready, action) { recoveryAt = it }

    fun processRecovery() = process.processOwned(recovery)

    fun clearRecovery() = clear(recovery) { recoveryAt = it }

    fun armMedic(deadline: Int, ready: () -> Boolean, action: () -> Unit) =
        arm(medic, deadline, ready, action) { medicAt = it }

    fun processMedic() = process.processOwned(medic)

    fun clearMedic() = clear(medic) { medicAt = it }

    fun armDeath(deadline: Int, ready: () -> Boolean, action: () -> Unit) =
        arm(death, deadline, ready, action) { deathAt = it }

    fun processDeath() = process.processOwned(death)

    fun clearDeath() = clear(death) { deathAt = it }

    fun clear() {
        clearRecovery()
        clearMedic()
        clearDeath()
    }

    fun close() {
        recovery.close()
        medic.close()
        death.close()
        recoveryAt = -1
        medicAt = -1
        deathAt = -1
        unregisterSession()
    }

    private fun arm(
        owner: WorldQueueList.Owner,
        deadline: Int,
        ready: () -> Boolean,
        action: () -> Unit,
        setDeadline: (Int) -> Unit,
    ) {
        check(!owner.isClosed) { "Kephri encounter queues have closed." }
        owner.clear()
        setDeadline(deadline)
        val revision = owner.revision
        owner.add(ready = { clock.cycle >= deadline && ready() }) {
            if (owner.revision != revision) return@add
            setDeadline(-1)
            try {
                if (stillAttached()) action()
            } catch (failure: Throwable) {
                // Preserve the due timer if a payload fails before it clears or rearms itself.
                if (!owner.isClosed && owner.revision == revision) {
                    arm(owner, deadline, ready, action, setDeadline)
                }
                throw failure
            }
        }
    }

    private fun clear(owner: WorldQueueList.Owner, setDeadline: (Int) -> Unit) {
        owner.clear()
        setDeadline(-1)
    }
}
