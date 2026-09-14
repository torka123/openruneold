package org.rsmod.content.bosses.doom

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Three native owners dispatched at Doom's established LateCycle effect boundaries. */
internal class DoomAttackQueues(
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val rocks = queues.createOwner()
    private val larvae = queues.createOwner()
    private val hits = queues.createOwner()
    private val rockIndexes = mutableListOf<Int>()
    private val styleHits = mutableListOf<DoomEncounterManager.PendingStyleHit>()

    val rockVolleysRemaining: Int
        get() = rockIndexes.size

    val pendingHits: List<DoomEncounterManager.PendingStyleHit>
        get() = styleHits

    var nextShieldLarvaCycle: Int = Int.MAX_VALUE
        private set

    fun beginRockVolleys(
        count: Int,
        tied: Boolean,
        eligible: () -> Boolean,
        launch: (Int) -> Unit,
    ) {
        cancelRockVolleys()
        rockIndexes += (0 until count)
        val revision = rocks.revision
        fun launchIndex(index: Int) {
            launch(index)
            if (rocks.revision == revision) rockIndexes.remove(index)
        }
        if (count == 0) return
        // The accepted native attack sends its first volley immediately. Remaining entries run
        // only at the retained LateCycle boundary, including same-cycle delve8+ deadlines.
        launchIndex(0)
        if (rocks.revision != revision || rocks.isClosed) return
        if (tied) {
            val tick = clock.cycle
            for (index in 1 until count) {
                rocks.add(ready = { clock.cycle >= tick && eligible() }) {
                    if (stillAttached()) launchIndex(index)
                }
            }
        } else {
            fun queueNext(index: Int) {
                if (index >= count) return
                val tick = clock.cycle + 1
                rocks.add(ready = { clock.cycle >= tick && eligible() }) {
                    if (stillAttached()) {
                        launchIndex(index)
                        if (rocks.revision == revision && !rocks.isClosed) queueNext(index + 1)
                    }
                }
            }
            queueNext(1)
        }
    }

    fun processRockVolleys() = process.processOwned(rocks)

    fun cancelRockVolleys() {
        rocks.clear()
        rockIndexes.clear()
    }

    fun armShieldLarvae(tick: Int, eligible: () -> Boolean, spawn: () -> Int) {
        cancelShieldLarvae()
        val revision = larvae.revision
        fun queueSpawn(next: Int) {
            nextShieldLarvaCycle = next
            larvae.add(ready = { clock.cycle >= next && eligible() }) {
                if (stillAttached()) {
                    val following = spawn()
                    if (larvae.revision == revision && !larvae.isClosed) queueSpawn(following)
                }
            }
        }
        queueSpawn(tick)
    }

    fun processShieldLarvae() = process.processOwned(larvae)

    fun cancelShieldLarvae() {
        larvae.clear()
        nextShieldLarvaCycle = Int.MAX_VALUE
    }

    fun scheduleHit(pending: DoomEncounterManager.PendingStyleHit, deliver: () -> Unit) {
        styleHits += pending
        val revision = hits.revision
        hits.add(
            ready = { clock.cycle >= DoomCombatTimeline.queueCycleForImpact(pending.impactCycle) }
        ) {
            if (stillAttached()) deliver()
            if (hits.revision == revision) {
                val index = styleHits.indexOfFirst { it === pending }
                if (index >= 0) styleHits.removeAt(index)
            }
        }
    }

    fun processHits() = process.processOwned(hits)

    fun cancelHits() {
        hits.clear()
        styleHits.clear()
    }

    fun clear() {
        cancelRockVolleys()
        cancelShieldLarvae()
        cancelHits()
    }

    fun close() {
        clear()
        rocks.close()
        larvae.close()
        hits.close()
    }
}
