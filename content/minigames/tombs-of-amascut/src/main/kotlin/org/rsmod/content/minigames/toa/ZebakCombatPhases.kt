package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/**
 * Native phase identity and owned queue readiness. The runtime consumes one declared edge at its
 * retained LateCycle slot; neither a paused raid nor normal world processing can advance it.
 */
internal class ZebakCombatPhases(
    private val encounter: BossEncounter,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val valid: () -> Boolean,
) {
    private val edge = queues.createOwner()
    private val hazards = queues.createOwner()
    private var readyPhase: ZebakPhase? = null

    val phase: ZebakPhase
        get() = ZebakPhase.valueOf(encounter.currentPhaseName)

    val deadline: Int?
        get() = encounter.currentPhase?.exitAfter?.let { encounter.phaseEnteredTick + it }

    val isClosed: Boolean
        get() = edge.isClosed && hazards.isClosed

    fun enter(phase: ZebakPhase) {
        edge.clear()
        readyPhase = null
        encounter.transitionTo(phase.name, clock.cycle)
        val declared = checkNotNull(encounter.currentPhase)
        val next = declared.nextPhase?.let(ZebakPhase::valueOf) ?: return
        val due = encounter.phaseEnteredTick + checkNotNull(declared.exitAfter)
        val revision = edge.revision
        edge.add(ready = { clock.cycle >= due }) {
            if (valid() && edge.revision == revision && this.phase == phase) readyPhase = next
        }
    }

    fun advance(): ZebakPhase? {
        process.processOwned(edge)
        val next = readyPhase ?: return null
        enter(next)
        return next
    }

    fun prepareHazards(launch: () -> Unit, land: () -> Unit) {
        hazards.clear()
        val launchAt = ZebakTimeline.hazardLaunchAt(clock.cycle)
        val landAt = ZebakTimeline.hazardLandAt(clock.cycle)
        // Insertion order preserves launch before landing when both are overdue on resumption.
        hazards.add(ready = { clock.cycle >= launchAt }) { if (valid()) launch() }
        hazards.add(ready = { clock.cycle >= landAt }) { if (valid()) land() }
    }

    fun processHazards() = process.processOwned(hazards)

    fun cancelHazards() = hazards.clear()

    fun close() {
        edge.close()
        hazards.close()
        readyPhase = null
    }
}
