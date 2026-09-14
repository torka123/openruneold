package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/** Carries accepted history across replacement bodies; native selectors own each body's cycle. */
internal class WardenPhaseTwoCombatPhases(
    val encounter: BossEncounter,
    private val clock: MapClock,
    val acceptedBeforeBody: Int,
    private val attackInterval: Int,
) {
    private var dispatchTarget: Player? = null

    init {
        require(acceptedBeforeBody >= 0)
        require(attackInterval > 0)
        encounter.initializeDecisionSeed(0)
    }

    val actionCount: Int
        get() = acceptedBeforeBody + encounter.phaseAbilityCount

    fun start(delay: Int) {
        require(delay > 0)
        check(!encounter.isEnded)
        check(encounter.phaseAbilityCount == 0)
        encounter.transitionTo(
            wardenPhaseTwoCombatPhase(acceptedBeforeBody.mod(WARDEN_PHASE_TWO_OFFSETS)),
            clock.cycle,
        )
        encounter.attackRateOverride = attackInterval
        encounter.npc.actionDelay = clock.cycle + delay
        encounter.lastAbilityTick = encounter.npc.actionDelay - attackInterval
    }

    /** Retained geometry compares base tiles, not the multi-tile body's native reach bounds. */
    fun prepareOpportunity(target: Player) {
        encounter.prepareDecisionInputs(
            clock.cycle,
            mapOf(
                WARDEN_PHASE_TWO_DISTANCE_INPUT to
                    if (encounter.npc.coords.chebyshevDistance(target.coords) <= 2)
                        WARDEN_PHASE_TWO_NEAR
                    else WARDEN_PHASE_TWO_FAR
            ),
        )
    }

    fun ownsDispatch(target: Player): Boolean = !encounter.isEnded && dispatchTarget === target

    fun dispatch(target: Player, action: () -> Unit) {
        check(!encounter.isEnded)
        check(dispatchTarget == null)
        dispatchTarget = target
        try {
            action()
        } finally {
            dispatchTarget = null
        }
    }
}
