package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.game.MapClock

/** Projects the native phase and accepted-launch budget into the raid's spatial handlers. */
internal class BabaCombatPhases(private val encounter: BossEncounter, private val clock: MapClock) {
    val state: BabaState
        get() =
            checkNotNull(
                    BABA_NATIVE_PHASES.entries.singleOrNull {
                        it.value == encounter.currentPhaseName
                    }
                ) {
                    "Unrecognized native Ba-Ba phase: ${encounter.currentPhaseName}"
                }
                .key

    val acceptedAutos: Int
        get() = encounter.phaseAbilityCount

    fun transitionTo(state: BabaState) {
        check(!encounter.isEnded) { "Ba-Ba's native encounter has ended." }
        val phase = checkNotNull(BABA_NATIVE_PHASES[state]) { "$state is a session outcome." }
        // Native phase entry resets the accepted-auto budget while preserving the attack clock.
        encounter.transitionTo(phase, clock.cycle)
    }
}

/** Session outcomes deliberately have no second, mutable native phase representation. */
internal val BABA_NATIVE_PHASES: Map<BabaState, String> =
    linkedMapOf(
        BabaState.INTRO to "intro",
        BabaState.NORMAL_COMBAT to "normal",
        BabaState.ROCKFALL to "rockfall",
        BabaState.ROCK_THROW to "rock-throw",
        BabaState.SHOCKWAVE to "shockwave",
        BabaState.BABOON_DISCHARGE to "baboon-discharge",
        BabaState.ROLLING_STONES_PREP to "rolling-prep",
        BabaState.ROLLING_STONES to "rolling",
        BabaState.ROLLING_STONES_RETURN to "rolling-return",
    )
