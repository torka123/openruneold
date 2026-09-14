package org.rsmod.content.bosses.dt2

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList

/** Three native dispatch boundaries preserve form expiry, volley launch and special resolution. */
internal class WhispererCombatPhases(
    val encounter: BossEncounter,
    private val clock: MapClock,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    session: InstanceSession,
    seed: Long,
    private val stillAttached: () -> Boolean,
) {
    val shotOwner = queues.createOwner()
    val completionOwner = queues.createOwner()
    val formOwner = queues.createOwner()
    private val specialChoice = WHISPERER_SPECIAL_CHOICES[(seed and 1L).toInt()]
    private var unregisterEncounter: () -> Unit = {}
    private var unregisterSession: () -> Unit = {}

    init {
        encounter.initializeDecisionSeed(seed)
        unregisterEncounter = encounter.onCleanup(::close)
        unregisterSession = session.onCleanup(::close)
    }

    val activeSpecial: WhispererSpecial?
        get() =
            WhispererSpecial.entries.firstOrNull {
                whispererSpecialPhase(it) == encounter.currentPhaseName
            }

    val enteredTick: Int
        get() = encounter.phaseEnteredTick

    val startedSpecials: Int
        get() = encounter.decisionDrawCount(WHISPERER_SPECIAL_STREAM).toInt()

    fun selectSpecial(requested: String?, target: Player): WhispererSpecial {
        val scheduled =
            checkNotNull(encounter.selectScheduledDecision(specialChoice, clock.cycle, target))
        return requested?.let { name ->
            WhispererSpecial.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        } ?: WhispererSpecial.valueOf(scheduled.choice)
    }

    fun enterSpecial(special: WhispererSpecial) {
        clearTransient()
        val movementLocked = encounter.npc.movementLocked
        encounter.transitionTo(whispererSpecialPhase(special), clock.cycle)
        encounter.npc.movementLocked = movementLocked
    }

    /** A form expiry during a special must not reset its pulse/deadline origin. */
    fun alignSpecial(special: WhispererSpecial) {
        if (activeSpecial != special) enterSpecial(special)
    }

    fun armSpecialCompletion(success: () -> Boolean, complete: (Boolean) -> Unit): Int {
        completionOwner.clear()
        val special = checkNotNull(activeSpecial)
        val entered = enteredTick
        val deadline = entered + WHISPERER_SPECIAL_DURATIONS.getValue(special)
        completionOwner.add(ready = { success() || clock.cycle >= deadline }) {
            if (live() && activeSpecial == special && enteredTick == entered) complete(success())
        }
        return deadline
    }

    fun enqueueFollowups(
        primary: Dt2AttackStyle,
        startedSpecials: Int,
        enraged: Boolean,
        launch: (WhispererFollowup) -> Unit,
    ) {
        for (shot in whispererFollowups(primary, startedSpecials, enraged)) {
            val deadline = clock.cycle + shot.afterTicks
            shotOwner.add(ready = { clock.cycle >= deadline }) { if (live()) launch(shot) }
        }
    }

    fun armMeleeExpiry(allowed: () -> Boolean, expire: () -> Unit): Int {
        formOwner.clear()
        val deadline = clock.cycle + 13
        formOwner.add(ready = { clock.cycle >= deadline && allowed() }) { if (live()) expire() }
        return deadline
    }

    fun expireMeleeForm() = process.processOwned(formOwner)

    fun launchDueShots() = process.processOwned(shotOwner)

    fun completeSpecial() = process.processOwned(completionOwner)

    /** Existing transient cleanup never cancels the independent visual melee window. */
    fun clearTransient() {
        shotOwner.clear()
        completionOwner.clear()
    }

    fun close() {
        shotOwner.close()
        completionOwner.close()
        formOwner.close()
        unregisterEncounter()
        unregisterSession()
    }

    private fun live(): Boolean = !encounter.isEnded && stillAttached()
}
