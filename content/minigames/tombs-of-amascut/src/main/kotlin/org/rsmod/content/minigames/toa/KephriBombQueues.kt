package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList

/** Native lock -> hit preparation -> landing batches at Kephri's retained LateCycle boundary. */
internal class KephriBombQueues(
    private val encounter: BossEncounter,
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val lockOwner = queues.createOwner()
    private val preparationOwner = queues.createOwner()
    private val landingOwner = queues.createOwner()
    private val bomberOwner = queues.createOwner()
    private val targetEntries = linkedSetOf<TargetEntry>()
    private val bombEntries = linkedSetOf<BombEntry>()
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}
    private var processing = false
    private var lockBatch: MutableList<KephriBombTargetRuntime>? = null
    private var preparation: ((KephriBombRuntime) -> Unit)? = null
    private var landingBatch: MutableList<KephriBombRuntime>? = null

    /** Read-only payload observations; the native entries own timing and cancellation. */
    val targets: List<KephriBombTargetRuntime>
        get() = targetEntries.map { it.payload }

    val bombs: List<KephriBombRuntime>
        get() = bombEntries.map { it.payload }

    var bomberAt: Int = -1
        private set

    val isClosed: Boolean
        get() = lockOwner.isClosed

    init {
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    fun scheduleTarget(payload: KephriBombTargetRuntime) {
        check(!isClosed) { "Kephri's bomb queues have closed." }
        val entry = TargetEntry(payload)
        entry.cancellation =
            lockOwner.add(ready = { clock.cycle >= entry.payload.locksAt }) {
                if (targetEntries.remove(entry) && live()) {
                    check(processing)
                    checkNotNull(lockBatch).add(entry.payload)
                }
            }
        targetEntries += entry
    }

    fun scheduleBomb(payload: KephriBombRuntime) {
        check(!isClosed) { "Kephri's bomb queues have closed." }
        val entry = BombEntry(payload)
        if (!payload.hitQueued) {
            entry.preparation =
                preparationOwner.add(ready = { clock.cycle >= payload.landsAt - 1 }) {
                    if (entry in bombEntries && !payload.hitQueued && live()) {
                        check(processing)
                        payload.hitQueued = true
                        checkNotNull(preparation).invoke(payload)
                    }
                }
        }
        landingOwner.add(ready = { clock.cycle >= payload.landsAt }) {
            if (bombEntries.remove(entry)) {
                entry.preparation?.cancel()
                if (live()) {
                    check(processing)
                    checkNotNull(landingBatch).add(payload)
                }
            }
        }
        bombEntries += entry
    }

    fun processBombs(
        lock: (List<KephriBombTargetRuntime>) -> Unit,
        prepare: (KephriBombRuntime) -> Unit,
        land: (List<KephriBombRuntime>) -> Unit,
    ) {
        check(!processing) { "Kephri bomb dispatch is already active." }
        processing = true
        val locked = mutableListOf<KephriBombTargetRuntime>()
        val landed = mutableListOf<KephriBombRuntime>()
        lockBatch = locked
        preparation = prepare
        landingBatch = landed
        try {
            process.processOwned(lockOwner)
            if (locked.isNotEmpty() && live()) lock(locked)
            process.processOwned(preparationOwner)
            process.processOwned(landingOwner)
            if (landed.isNotEmpty() && live()) land(landed)
        } finally {
            lockBatch = null
            preparation = null
            landingBatch = null
            processing = false
        }
    }

    fun armBomber(deadline: Int, action: () -> Unit) {
        check(!isClosed) { "Kephri's bomb queues have closed." }
        clearBomber()
        bomberAt = deadline
        bomberOwner.add(ready = { clock.cycle >= deadline }) {
            bomberAt = -1
            if (live()) action()
        }
    }

    fun processBomber() = process.processOwned(bomberOwner)

    fun clearBomber() {
        bomberOwner.clear()
        bomberAt = -1
    }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        targetEntries.forEach { entry ->
            if (entry.payload.target == oldUid) entry.payload = entry.payload.copy(target = newUid)
        }
    }

    /** Locked bombs retain their tile; only an unlocked player's target token is retired. */
    fun retirePlayer(uid: PlayerUid) {
        targetEntries.removeAll { entry ->
            if (entry.payload.target != uid) false
            else {
                entry.cancellation.cancel()
                true
            }
        }
    }

    fun clearBombs() {
        lockOwner.clear()
        preparationOwner.clear()
        landingOwner.clear()
        targetEntries.clear()
        bombEntries.clear()
    }

    fun clear() {
        clearBombs()
        clearBomber()
    }

    fun close() {
        lockOwner.close()
        preparationOwner.close()
        landingOwner.close()
        bomberOwner.close()
        targetEntries.clear()
        bombEntries.clear()
        bomberAt = -1
        unregisterEncounter()
        unregisterSession()
    }

    private fun live(): Boolean = !encounter.isEnded && stillAttached()

    private class TargetEntry(var payload: KephriBombTargetRuntime) {
        lateinit var cancellation: WorldQueueList.Cancellation
    }

    /** Identity survives hitQueued mutation and preserves distinct value-equal bomb payloads. */
    private class BombEntry(val payload: KephriBombRuntime) {
        var preparation: WorldQueueList.Cancellation? = null
    }
}
