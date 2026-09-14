package org.rsmod.content.bosses.doom

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Native phase graph and queues own timing; LateCycle consumes packet readiness after hazards. */
internal class DoomCombatPhases(
    private val encounter: BossEncounter,
    private val clock: MapClock,
    private val queues: WorldQueueList,
    private val delve: Int,
    private val stillAttached: () -> Boolean,
) {
    private val readiness = encounter.state("doom.shield-readiness") { ShieldReadiness() }
    private val shockwave = encounter.state("doom.shockwave-readiness") { ShockwaveReadiness() }

    var state: DoomState
        get() =
            when {
                encounter.currentPhaseName == doomShockwaveWarningPhase(delve, DoomState.ACTIVE) ->
                    DoomState.ACTIVE
                encounter.currentPhaseName ==
                    doomShockwaveWarningPhase(delve, DoomState.MELEE_CHARGE) ->
                    DoomState.MELEE_CHARGE
                shockwavePacket != null -> DoomState.SHOCKWAVE
                else ->
                    DOOM_COMBAT_STATES.first { doomPhase(delve, it) == encounter.currentPhaseName }
            }
        set(value) {
            require(value in DOOM_COMBAT_STATES)
            // A charge accepted before the warning can still start during its fifteen ticks.
            // Returning from that charge must retain the original queued warning deadline.
            val retainWarning =
                shockwavePreparing && value in listOf(DoomState.ACTIVE, DoomState.MELEE_CHARGE)
            invalidateReadiness(cancelShockwave = !retainWarning)
            encounter.transitionTo(
                if (retainWarning) doomShockwaveWarningPhase(delve, value)
                else doomPhase(delve, value),
                clock.cycle,
            )
        }

    fun invalidateReadiness(cancelShockwave: Boolean = true) {
        readiness.revision++
        readiness.epoch = null
        if (cancelShockwave) {
            shockwave.cancelEdge?.invoke()
            shockwave.cancelEdge = null
            shockwave.nextPacket = null
        }
    }

    fun retireReadiness() {
        invalidateReadiness()
        shockwave.cancelDue?.invoke()
        shockwave.cancelDue = null
        shockwave.due = false
    }

    var shockwavePreparing: Boolean
        get() =
            encounter.currentPhaseName in
                listOf(
                    doomShockwaveWarningPhase(delve, DoomState.ACTIVE),
                    doomShockwaveWarningPhase(delve, DoomState.MELEE_CHARGE),
                )
        set(value) {
            if (value == shockwavePreparing) return
            val current = state
            check(current == DoomState.ACTIVE || current == DoomState.MELEE_CHARGE)
            invalidateReadiness()
            encounter.transitionTo(
                if (value) doomShockwaveWarningPhase(delve, current) else doomPhase(delve, current),
                clock.cycle,
            )
            if (value) {
                shockwave.due = false
                shockwave.cancelDue?.invoke()
                shockwave.cancelDue = null
                armShockwavePacket()
            }
        }

    val shockwaveDue: Boolean
        get() = shockwave.due

    /** This readiness crosses combat phases; car emergence replaces, rather than adds, a timer. */
    fun armShockwaveAt(tick: Int) {
        shockwave.cancelDue?.invoke()
        shockwave.cancelDue = null
        shockwave.due = false
        var unregister: () -> Unit = {}
        val cancellation =
            queues.addCancellable(0, ready = { clock.cycle >= tick - 1 }) {
                unregister()
                shockwave.cancelDue = null
                if (!encounter.isEnded && stillAttached()) shockwave.due = true
            }
        unregister = encounter.onCleanup(cancellation::cancel)
        shockwave.cancelDue = {
            cancellation.cancel()
            unregister()
        }
    }

    /** Only the declared shockwave edge is armed; the queue never sends a packet or runs AI. */
    fun armShockwavePacket() {
        val phase = checkNotNull(encounter.currentPhase)
        val next = phase.nextPhase ?: return
        check(shockwavePreparing || shockwavePacket != null)
        val deadline = encounter.phaseEnteredTick + checkNotNull(phase.exitAfter) - 1
        val warning = shockwavePreparing
        shockwave.cancelEdge?.invoke()
        shockwave.nextPacket = null
        var unregister: () -> Unit = {}
        val cancellation =
            queues.addCancellable(0, ready = { clock.cycle >= deadline }) {
                unregister()
                shockwave.cancelEdge = null
                val samePhase =
                    if (warning) shockwavePreparing else encounter.currentPhaseName == phase.name
                if (!encounter.isEnded && samePhase && stillAttached()) shockwave.nextPacket = next
            }
        unregister = encounter.onCleanup(cancellation::cancel)
        shockwave.cancelEdge = {
            cancellation.cancel()
            unregister()
        }
    }

    fun advanceShockwavePacket(): DoomShockwavePacket? {
        val next = shockwave.nextPacket ?: return null
        invalidateReadiness()
        encounter.transitionTo(next, clock.cycle)
        return checkNotNull(shockwavePacket)
    }

    val shockwavePacket: DoomShockwavePacket?
        get() =
            DoomShockwavePacket.entries.firstOrNull { packet ->
                if (packet == DoomShockwavePacket.SLAM)
                    (1..DoomRules.shockwaveSlams(delve)).any {
                        encounter.currentPhaseName == doomShockwavePhase(delve, packet, it)
                    }
                else encounter.currentPhaseName == doomShockwavePhase(delve, packet)
            }

    fun armShield(epoch: Int, shieldStillAttached: () -> Boolean) {
        check(state == DoomState.SHIELD_TRANSITION)
        val revision = readiness.revision
        // GameCycle advances the clock between native world queues and LateCycle. Mark before
        // input, then activate at the original LateCycle position after native hits and hazards.
        val deadline = DoomCombatTimeline.shieldActiveCycle(encounter.phaseEnteredTick) - 1
        encounter.scheduleAt(queues, clock, deadline) {
            if (
                readiness.revision == revision &&
                    state == DoomState.SHIELD_TRANSITION &&
                    shieldStillAttached()
            ) {
                readiness.epoch = epoch
            }
        }
    }

    fun consumeShieldReady(epoch: Int): Boolean {
        if (readiness.epoch != epoch) return false
        readiness.epoch = null
        return true
    }

    private class ShieldReadiness(var revision: Long = 0, var epoch: Int? = null)

    private class ShockwaveReadiness(
        var due: Boolean = false,
        var cancelDue: (() -> Unit)? = null,
        var nextPacket: String? = null,
        var cancelEdge: (() -> Unit)? = null,
    )
}

internal val DOOM_COMBAT_STATES =
    listOf(
        DoomState.ACTIVE,
        DoomState.MELEE_CHARGE,
        DoomState.SHIELD_TRANSITION,
        DoomState.SHIELDED,
        DoomState.SHOCKWAVE,
        DoomState.BURROWING,
    )

/** These survive native NPC deletion or precede its visibility; they belong to the session. */
internal enum class DoomSessionOutcome(val state: DoomState) {
    STARTING(DoomState.STARTING),
    DYING(DoomState.DYING),
    DEFEATED(DoomState.DEFEATED),
    INVALIDATED(DoomState.INVALIDATED),
}
