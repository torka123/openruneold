package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList

/** Native deadlines at the retained rock, rubble and shockwave dispatch boundaries. */
internal class BabaHazardQueues(
    private val encounter: BossEncounter,
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val rockOwner = queues.createOwner()
    private val rubbleOwner = queues.createOwner()
    private val shockwaveOwner = queues.createOwner()
    private val rocks = mutableListOf<Pending<BabaPendingRock>>()
    private val rubble = mutableListOf<Pending<BabaPendingRubble>>()
    private val shockwaves = mutableListOf<Pending<BabaPendingShockwave>>()
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}

    /** Metadata projections support target rebinding and rubble placement reservations. */
    val pendingRocks: List<BabaPendingRock>
        get() = rocks.map { it.payload }

    val pendingRubble: List<BabaPendingRubble>
        get() = rubble.map { it.payload }

    val pendingShockwaves: List<BabaPendingShockwave>
        get() = shockwaves.map { it.payload }

    val isClosed: Boolean
        get() = rockOwner.isClosed

    init {
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    fun queueRock(payload: BabaPendingRock, resolve: (BabaPendingRock) -> Unit) {
        val pending = Pending(payload)
        pending.cancellation =
            rockOwner.add(ready = { clock.cycle >= payload.landsAt }) {
                rocks.remove(pending)
                // Rebinding changes this tracked observation, not the captured original payload.
                if (live()) resolve(pending.payload)
            }
        rocks += pending
    }

    fun queueRubble(payload: BabaPendingRubble, resolve: (BabaPendingRubble) -> Unit) {
        val pending = Pending(payload)
        pending.cancellation =
            rubbleOwner.add(ready = { clock.cycle >= payload.landsAt }) {
                rubble.remove(pending)
                if (live()) resolve(pending.payload)
            }
        rubble += pending
    }

    fun queueShockwave(payload: BabaPendingShockwave, resolve: (BabaPendingShockwave) -> Unit) {
        val pending = Pending(payload)
        pending.cancellation =
            shockwaveOwner.add(ready = { clock.cycle >= payload.landsAt }) {
                shockwaves.remove(pending)
                if (live()) resolve(pending.payload)
            }
        shockwaves += pending
    }

    fun processRocks() = process.processOwned(rockOwner)

    fun processRubble() = process.processOwned(rubbleOwner)

    fun processShockwaves() = process.processOwned(shockwaveOwner)

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        rocks.forEach { pending ->
            if (pending.payload.target == oldUid) {
                pending.payload = pending.payload.copy(target = newUid)
            }
        }
    }

    fun retirePlayer(uid: PlayerUid) {
        rocks.removeAll { pending ->
            if (pending.payload.target != uid) false
            else {
                pending.cancellation.cancel()
                true
            }
        }
    }

    fun clearRocks() {
        rockOwner.clear()
        rocks.clear()
    }

    /** Rolling entry cancels these telegraphs without retiring the phase's other native owners. */
    fun clear() {
        clearRocks()
        rubbleOwner.clear()
        shockwaveOwner.clear()
        rubble.clear()
        shockwaves.clear()
    }

    fun close() {
        rockOwner.close()
        rubbleOwner.close()
        shockwaveOwner.close()
        rocks.clear()
        rubble.clear()
        shockwaves.clear()
        unregisterEncounter()
        unregisterSession()
    }

    private fun live(): Boolean = !encounter.isEnded && stillAttached()

    /** A native cancellation handle and its spatial payload, never a second due-work list. */
    private class Pending<T>(var payload: T) {
        lateinit var cancellation: WorldQueueList.Cancellation
    }
}
