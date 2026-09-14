package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.MapClock

/** Style is a prepared input; only a new special resets the native accepted-auto budget. */
internal class AkkhaCombatPhases(val encounter: BossEncounter, private val clock: MapClock) {
    init {
        encounter.initializeDecisionSeed(0)
    }

    val actionCount: Int
        get() = encounter.phaseAbilityCount

    fun prepareStyle(style: ToaCombatStyle) {
        encounter.prepareDecisionInputs(
            clock.cycle,
            mapOf(TOA_AKKHA_STYLE_INPUT to toaAkkhaCombatPhase(style)),
        )
    }

    fun resetForSpecial() {
        check(!encounter.isEnded)
        encounter.transitionTo(TOA_AKKHA_COMBAT_PHASE, clock.cycle)
    }
}

internal const val AKKHA_COMBAT_PHASES = "toa.akkha-combat-phases"
