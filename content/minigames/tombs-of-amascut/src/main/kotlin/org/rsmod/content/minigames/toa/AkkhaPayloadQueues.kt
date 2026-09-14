package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList

/** Session-owned payload deadlines survive final-stand transfers between existing Akkha actors. */
internal class AkkhaPayloadQueues(
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val memoryReveal = queues.createOwner()
    private val memoryRender = queues.createOwner()
    private val memoryResolve = queues.createOwner()
    private val detonationOwner = queues.createOwner()
    private val finalOrb = queues.createOwner()
    private val memory = linkedSetOf<MemoryEntry>()
    private var memoryDispatch: MemoryDispatch? = null
    private var detonationDispatch: ((AkkhaDetonation) -> Unit)? = null
    private var unregisterSession: () -> Unit = {}

    val memorySteps: List<AkkhaMemoryStep>
        get() = memory.map { it.step }

    var detonation: AkkhaDetonation? = null
        private set

    var finalOrbAt: Int = -1
        private set

    val isClosed: Boolean
        get() = memoryReveal.isClosed

    init {
        unregisterSession = session.onCleanup(::close)
    }

    fun armMemory(steps: List<AkkhaMemoryStep>) {
        check(!isClosed) { "Akkha payload queues are closed." }
        clearMemory()
        steps.forEach { step ->
            val entry = MemoryEntry(step)
            entry.reveal =
                memoryReveal.add(ready = { !step.revealed && clock.cycle >= step.revealAt }) {
                    if (entry in memory && stillAttached()) {
                        checkNotNull(memoryDispatch).reveal(step)
                    }
                }
            entry.render =
                memoryRender.add(
                    ready = { clock.cycle >= step.blastStartsAt && step.renderedWaveTick < 0 }
                ) {
                    if (entry in memory && stillAttached()) {
                        checkNotNull(memoryDispatch).render(step)
                    }
                }
            memoryResolve.add(ready = { clock.cycle >= step.resolvesAt }) {
                if (memory.remove(entry)) {
                    // Already-rendered steps or unusual deadline order must not leave tasks behind.
                    entry.reveal.cancel()
                    entry.render.cancel()
                    if (stillAttached()) checkNotNull(memoryDispatch).resolved += step
                }
            }
            memory += entry
        }
    }

    fun processMemory(
        reveal: (AkkhaMemoryStep) -> Unit,
        render: (AkkhaMemoryStep) -> Unit,
        resolve: (List<AkkhaMemoryStep>) -> Unit,
    ) {
        check(memoryDispatch == null) { "Memory payload dispatch is already active." }
        val dispatch = MemoryDispatch(reveal, render)
        val revision = memoryReveal.revision
        memoryDispatch = dispatch
        try {
            process.processOwned(memoryReveal)
            if (memoryReveal.revision != revision) return
            process.processOwned(memoryRender)
            if (memoryReveal.revision != revision) return
            process.processOwned(memoryResolve)
            // Every due step is removed before the first damage/status callback in this batch.
            if (dispatch.resolved.isNotEmpty() && stillAttached()) {
                resolve(dispatch.resolved.toList())
            }
        } finally {
            memoryDispatch = null
        }
    }

    fun clearMemory() {
        memoryReveal.clear()
        memoryRender.clear()
        memoryResolve.clear()
        memory.clear()
    }

    fun armDetonation(payload: AkkhaDetonation) {
        check(!isClosed) { "Akkha payload queues are closed." }
        clearDetonation()
        detonation = payload
        detonationOwner.add(ready = { clock.cycle >= payload.resolvesAt }) {
            val current = detonation
            detonation = null
            if (current != null && stillAttached()) checkNotNull(detonationDispatch).invoke(current)
        }
    }

    fun processDetonation(resolve: (AkkhaDetonation) -> Unit) {
        check(detonationDispatch == null) { "Detonation payload dispatch is already active." }
        detonationDispatch = resolve
        try {
            process.processOwned(detonationOwner)
        } finally {
            detonationDispatch = null
        }
    }

    fun clearDetonation() {
        detonationOwner.clear()
        detonation = null
    }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        detonation =
            detonation?.let { payload ->
                payload.copy(
                    origins =
                        payload.origins.entries.associate { (uid, tile) ->
                            (if (uid == oldUid) newUid else uid) to tile
                        }
                )
            }
    }

    fun retirePlayer(uid: PlayerUid) {
        detonation = detonation?.let { it.copy(origins = it.origins - uid) }
    }

    fun armFinalOrb(deadline: Int, eligible: () -> Boolean, spawn: () -> Unit) {
        check(!isClosed) { "Akkha payload queues are closed." }
        clearFinalOrb()
        finalOrbAt = deadline
        finalOrb.add(ready = { clock.cycle >= deadline && eligible() }) {
            finalOrbAt = -1
            if (stillAttached()) spawn()
        }
    }

    fun processFinalOrb() = process.processOwned(finalOrb)

    fun clearFinalOrb() {
        finalOrb.clear()
        finalOrbAt = -1
    }

    fun clear() {
        clearMemory()
        clearDetonation()
        clearFinalOrb()
    }

    fun close() {
        memoryReveal.close()
        memoryRender.close()
        memoryResolve.close()
        detonationOwner.close()
        finalOrb.close()
        memory.clear()
        detonation = null
        finalOrbAt = -1
        unregisterSession()
    }

    private class MemoryEntry(val step: AkkhaMemoryStep) {
        lateinit var reveal: WorldQueueList.Cancellation
        lateinit var render: WorldQueueList.Cancellation
    }

    private class MemoryDispatch(
        val reveal: (AkkhaMemoryStep) -> Unit,
        val render: (AkkhaMemoryStep) -> Unit,
    ) {
        val resolved = mutableListOf<AkkhaMemoryStep>()
    }
}
