package org.rsmod.content.bosses.dt2

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList

/** Native phase identity and cancellable completion at the retained LateCycle effect boundary. */
internal class LeviathanCombatPhases(
    val encounter: BossEncounter,
    private val clock: MapClock,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    session: InstanceSession,
    private val stillAttached: () -> Boolean,
) {
    val owner = queues.createOwner()
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}

    init {
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    val action: LeviathanAction
        get() = leviathanPhaseAction(encounter.currentPhaseName)

    val enteredTick: Int
        get() = encounter.phaseEnteredTick

    fun enter(action: LeviathanAction, ordinaryRate: Int = 3) {
        owner.clear()
        val movementLocked = encounter.npc.movementLocked
        encounter.transitionTo(leviathanActionPhase(action, ordinaryRate), clock.cycle)
        if (action != LeviathanAction.STANDARD_VOLLEY) encounter.npc.movementLocked = movementLocked
    }

    fun align(action: LeviathanAction, ordinaryRate: Int) {
        val phase = leviathanActionPhase(action, ordinaryRate)
        if (encounter.currentPhaseName != phase) enter(action, ordinaryRate)
    }

    fun selectSpecial(selector: Selector, target: Player): LeviathanSpecial =
        LeviathanSpecial.valueOf(
            checkNotNull(encounter.selectAbility(selector, clock.cycle, target))
        )

    fun armCompletion(deadline: Int, action: () -> Unit) {
        owner.clear()
        val phase = encounter.currentPhaseName
        val entered = encounter.phaseEnteredTick
        owner.add(ready = { clock.cycle >= deadline }) {
            if (
                !encounter.isEnded &&
                    stillAttached() &&
                    encounter.currentPhaseName == phase &&
                    encounter.phaseEnteredTick == entered
            )
                action()
        }
    }

    fun completeDue() = process.processOwned(owner)

    fun close() {
        owner.close()
        unregisterEncounter()
        unregisterSession()
    }
}
