package org.rsmod.content.bosses.doom

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Phase identity is native; this adapter retains only a shield activation readiness marker. */
internal class DoomCombatPhases(
    private val encounter: BossEncounter,
    private val clock: MapClock,
    private val queues: WorldQueueList,
    private val delve: Int,
) {
    private val readiness = encounter.state("doom.shield-readiness") { ShieldReadiness() }

    var state: DoomState
        get() = DOOM_COMBAT_STATES.first { doomPhase(delve, it) == encounter.currentPhaseName }
        set(value) {
            require(value in DOOM_COMBAT_STATES)
            invalidateReadiness()
            encounter.transitionTo(doomPhase(delve, value), clock.cycle)
        }

    fun invalidateReadiness() {
        readiness.revision++
        readiness.epoch = null
    }

    fun armShield(epoch: Int, stillAttached: () -> Boolean) {
        check(state == DoomState.SHIELD_TRANSITION)
        val revision = readiness.revision
        // GameCycle advances the clock between native world queues and LateCycle. Mark before
        // input, then activate at the original LateCycle position after native hits and hazards.
        val deadline = DoomCombatTimeline.shieldActiveCycle(encounter.phaseEnteredTick) - 1
        encounter.scheduleAt(queues, clock, deadline) {
            if (
                readiness.revision == revision &&
                    state == DoomState.SHIELD_TRANSITION &&
                    stillAttached()
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
