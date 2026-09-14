package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/** Native phase/clock adapter; no private attack count, selector cursor or due-cycle field. */
internal class KephriCombatPhases(
    val encounter: BossEncounter,
    private val clock: MapClock,
    seed: Long,
) {
    private var dispatchTarget: Player? = null

    fun ownsDispatch(target: Player): Boolean = dispatchTarget === target

    /** Only the synchronous native LateCycle AI event may consume this spatial observation. */
    fun dispatch(target: Player, action: () -> Unit) {
        check(dispatchTarget == null)
        dispatchTarget = target
        try {
            action()
        } finally {
            dispatchTarget = null
        }
    }

    init {
        encounter.initializeDecisionSeed(seed)
    }

    val actionCount: Int
        get() = encounter.phaseAbilityCount

    fun activate() = resume(shield = 0, delay = KephriEncounter.FIRST_ATTACK_DELAY)

    /** Called after the existing immediate final-shield Dung, or completed shield recovery. */
    fun resume(shield: Int, delay: Int = KephriAttackCycle.DUNG_DELAY) {
        encounter.transitionTo(kephriCombatPhase(shield), clock.cycle)
        encounter.attackRateOverride = null
        encounter.npc.actionDelay = clock.cycle + delay
        encounter.lastAbilityTick = encounter.npc.actionDelay - encounter.expectedAttackRate()
    }

    fun suspendCombat() {
        encounter.transitionTo(KEPHRI_INACTIVE, clock.cycle)
        encounter.npc.actionDelay = Int.MAX_VALUE
    }

    /** Supply only the current spatial observation; the declared selector owns the decision. */
    fun prepareOpportunity(agileCount: Int) {
        encounter.prepareDecisionInputs(
            clock.cycle,
            mapOf(
                KEPHRI_AGILE_INPUT to
                    if (agileCount >= KephriEggs.MASS_INCUBATION_AGILE_LIMIT) KEPHRI_DUNG
                    else KEPHRI_INCUBATION
            ),
        )
    }
}
