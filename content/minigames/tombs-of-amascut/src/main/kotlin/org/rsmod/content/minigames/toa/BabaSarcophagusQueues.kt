package org.rsmod.content.minigames.toa

import java.util.IdentityHashMap
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Native runner dispatch and independently dispatched energy owners in the room's actor order. */
internal class BabaSarcophagusQueues(
    private val encounter: BossEncounter,
    session: InstanceSession,
    private val queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private class Energy(val owner: WorldQueueList.Owner, var deadline: Int = -1)

    private val runner = queues.createOwner()
    // Sarcophagi are mutable data classes: hitpoints/state must never change owner lookup identity.
    private val energy = IdentityHashMap<BabaSarcophagusRuntime, Energy>()
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}

    /** Presentation only; native owned entries decide whether work is due. */
    var runnerAt: Int = -1
        private set

    val isClosed: Boolean
        get() = runner.isClosed

    init {
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    fun armRunner(deadline: Int, eligible: () -> Boolean, callback: () -> Unit) {
        check(!isClosed) { "Ba-Ba's sarcophagus queues have closed." }
        clearRunner()
        runnerAt = deadline
        runner.add(ready = { clock.cycle >= deadline && eligible() }) {
            // The old runner assignment clears its deadline before directing/tracing the actor.
            runnerAt = -1
            if (live()) callback()
        }
    }

    fun processRunner() = process.processOwned(runner)

    fun clearRunner() {
        runner.clear()
        runnerAt = -1
    }

    fun armEnergy(
        sarc: BabaSarcophagusRuntime,
        deadline: Int,
        eligible: () -> Boolean,
        callback: () -> Unit,
    ) {
        check(!isClosed) { "Ba-Ba's sarcophagus queues have closed." }
        val entry = energy.getOrPut(sarc) { Energy(queues.createOwner()) }
        entry.owner.clear()
        entry.deadline = deadline
        val revision = entry.owner.revision
        entry.owner.add(ready = { clock.cycle >= deadline && eligible() }) {
            try {
                // Payload/RNG observes the old deadline; successful launch explicitly rearms it.
                if (live()) callback()
            } catch (failure: Throwable) {
                if (energy[sarc] === entry && entry.owner.revision == revision && !isClosed) {
                    // Before migration a failed payload left nextEnergyAt due for the next pass.
                    armEnergy(sarc, deadline, eligible, callback)
                }
                throw failure
            } finally {
                if (energy[sarc] === entry && entry.owner.revision == revision) {
                    entry.deadline = -1
                }
            }
        }
    }

    fun processEnergy(sarc: BabaSarcophagusRuntime) {
        energy[sarc]?.let { process.processOwned(it.owner) }
    }

    fun energyAt(sarc: BabaSarcophagusRuntime): Int = energy[sarc]?.deadline ?: -1

    fun clearEnergy(sarc: BabaSarcophagusRuntime) {
        energy.remove(sarc)?.owner?.close()
    }

    fun clear() {
        clearRunner()
        energy.values.forEach { it.owner.close() }
        energy.clear()
    }

    fun close() {
        clear()
        runner.close()
        unregisterEncounter()
        unregisterSession()
    }

    private fun live(): Boolean = !encounter.isEnded && stillAttached()
}
