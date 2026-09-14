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
    private val charge = encounter.state("doom.charge-readiness") { ChargeReadiness() }
    private val car = encounter.state("doom.car-readiness") { CarReadiness() }

    init {
        encounter.onCleanup(::retireCarZoom)
    }

    var state: DoomState
        get() =
            when {
                encounter.currentPhaseName == doomShockwaveWarningPhase(delve, DoomState.ACTIVE) ->
                    DoomState.ACTIVE
                encounter.currentPhaseName ==
                    doomShockwaveWarningPhase(delve, DoomState.MELEE_CHARGE) ->
                    DoomState.MELEE_CHARGE
                shockwavePacket != null -> DoomState.SHOCKWAVE
                carPacket != null -> DoomState.BURROWING
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
            retireCarZoom()
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
        cancelMeleeCharge()
        stopBeamCharge()
        retireCarZoom()
    }

    val carPacket: DoomCarPacket?
        get() =
            DoomCarPacket.entries.firstOrNull { packet ->
                (1..DoomRules.carZooms(delve)).any { zoom ->
                    encounter.currentPhaseName == doomCarPhase(delve, packet, zoom)
                }
            }

    val carZoom: Int
        get() =
            (1..DoomRules.carZooms(delve)).first { zoom ->
                DoomCarPacket.entries.any { packet ->
                    encounter.currentPhaseName == doomCarPhase(delve, packet, zoom)
                }
            }

    val carSchedule: DoomCarZoomSchedule?
        get() = car.schedule

    val inCarHazardGrace: Boolean
        get() =
            carPacket == DoomCarPacket.TRANSFORM_WAIT &&
                clock.cycle < encounter.phaseEnteredTick + DoomRules.BURROW_HAZARD_GRACE_TICKS

    /** The graph owns phase identity; nonfinal exact-move segments do not re-enter DASHING. */
    fun advanceCarPhase() {
        val next = checkNotNull(encounter.currentPhase?.nextPhase)
        cancelCarPacket()
        encounter.transitionTo(next, clock.cycle)
    }

    fun finishCarZoom(): Boolean {
        val recovery =
            encounter.spec.phases.getValue(doomCarPhase(delve, DoomCarPacket.RECOVERY, carZoom))
        retireCarZoom()
        encounter.transitionTo(checkNotNull(recovery.nextPhase), clock.cycle)
        return carPacket != null
    }

    fun armCarPacket() {
        val duration = checkNotNull(encounter.currentPhase?.exitAfter)
        armCarPacketAt(encounter.phaseEnteredTick + duration)
    }

    fun armCarPacketAt(tick: Int) {
        cancelCarPacket()
        val phase = encounter.currentPhaseName
        car.cancelEdge =
            carDeadline(tick) {
                car.cancelEdge = null
                if (encounter.currentPhaseName == phase) car.packetReady = true
            }
    }

    fun consumeCarPacketReady(): Boolean {
        if (!car.packetReady) return false
        car.packetReady = false
        return true
    }

    fun cancelCarPacket() {
        car.cancelEdge?.invoke()
        car.cancelEdge = null
        car.packetReady = false
    }

    /** Immutable capture-backed packet schedule; due shots can span several native car phases. */
    fun prepareCarZoom(schedule: DoomCarZoomSchedule) {
        retireCarZoom()
        car.schedule = schedule
        schedule.projectileCycles.forEach { tick ->
            var cancel: () -> Unit = {}
            cancel =
                carDeadline(tick) {
                    car.cancelProjectiles.remove(cancel)
                    car.projectilesReady++
                }
            car.cancelProjectiles += cancel
        }
    }

    fun consumeCarProjectiles(): Int = car.projectilesReady.also { car.projectilesReady = 0 }

    fun armCarTrampleAt(tick: Int) {
        car.cancelTrample?.invoke()
        car.trampleReady = false
        car.cancelTrample =
            carDeadline(tick) {
                car.cancelTrample = null
                car.trampleReady = true
            }
    }

    fun consumeCarTrampleReady(): Boolean {
        if (!car.trampleReady) return false
        car.trampleReady = false
        return true
    }

    fun retireCarZoom() {
        car.revision++
        cancelCarPacket()
        car.cancelProjectiles.toList().forEach { it() }
        car.cancelProjectiles.clear()
        car.cancelTrample?.invoke()
        car.cancelTrample = null
        car.schedule = null
        car.projectilesReady = 0
        car.trampleReady = false
    }

    /** Native pre-input readiness only. The retained LateCycle handlers deliver all car effects. */
    private fun carDeadline(tick: Int, ready: () -> Unit): () -> Unit {
        val revision = car.revision
        var unregister: () -> Unit = {}
        val cancellation =
            queues.addCancellable(0, ready = { clock.cycle >= tick - 1 }) {
                unregister()
                if (!encounter.isEnded && stillAttached() && revision == car.revision) ready()
            }
        unregister = encounter.onCleanup(cancellation::cancel)
        return {
            cancellation.cancel()
            unregister()
        }
    }

    val pendingMeleeChargeCycle: Int
        get() = charge.startAt

    val beamFireCycle: Int
        get() = charge.fireAt

    /** Last LateCycle observation, retained during beam travel just like the original status. */
    val chargeProgress: Int
        get() = charge.progress

    fun armMeleeChargeAt(tick: Int) {
        cancelMeleeCharge()
        charge.startAt = tick
        var unregister: () -> Unit = {}
        val cancellation =
            queues.addCancellable(0, ready = { clock.cycle >= tick - 1 }) {
                unregister()
                charge.cancelStart = null
                if (!encounter.isEnded && stillAttached()) charge.startReady = true
            }
        unregister = encounter.onCleanup(cancellation::cancel)
        charge.cancelStart = {
            cancellation.cancel()
            unregister()
        }
    }

    fun consumeMeleeChargeReady(): Boolean {
        if (!charge.startReady) return false
        cancelMeleeCharge()
        return true
    }

    fun cancelMeleeCharge() {
        charge.cancelStart?.invoke()
        charge.cancelStart = null
        charge.startAt = Int.MAX_VALUE
        charge.startReady = false
    }

    /** Beam charging is independent of car movement and the fifteen-tick warning phase. */
    fun resetBeamCharge() {
        stopBeamCharge()
        charge.startedAt = clock.cycle
        val tick = DoomCombatTimeline.beamFireCycle(clock.cycle, state)
        charge.fireAt = tick
        var unregister: () -> Unit = {}
        val cancellation =
            queues.addCancellable(0, ready = { clock.cycle >= tick - 1 }) {
                unregister()
                charge.cancelFire = null
                if (!encounter.isEnded && stillAttached()) charge.fireReady = true
            }
        unregister = encounter.onCleanup(cancellation::cancel)
        charge.cancelFire = {
            cancellation.cancel()
            unregister()
        }
    }

    fun observeChargeProgress() {
        charge.progress = (clock.cycle - charge.startedAt).coerceAtLeast(0)
    }

    fun consumeBeamReady(): Boolean {
        if (!charge.fireReady) return false
        charge.fireReady = false
        return true
    }

    fun stopBeamCharge(clearProgress: Boolean = true) {
        charge.cancelFire?.invoke()
        charge.cancelFire = null
        charge.fireAt = Int.MAX_VALUE
        charge.fireReady = false
        if (clearProgress) charge.progress = 0
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

    private class ChargeReadiness(
        var startAt: Int = Int.MAX_VALUE,
        var startReady: Boolean = false,
        var cancelStart: (() -> Unit)? = null,
        var startedAt: Int = 0,
        var fireAt: Int = Int.MAX_VALUE,
        var fireReady: Boolean = false,
        var cancelFire: (() -> Unit)? = null,
        var progress: Int = 0,
    )

    private class CarReadiness(
        var revision: Long = 0,
        var schedule: DoomCarZoomSchedule? = null,
        var packetReady: Boolean = false,
        var cancelEdge: (() -> Unit)? = null,
        var projectilesReady: Int = 0,
        val cancelProjectiles: MutableList<() -> Unit> = mutableListOf(),
        var trampleReady: Boolean = false,
        var cancelTrample: (() -> Unit)? = null,
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
