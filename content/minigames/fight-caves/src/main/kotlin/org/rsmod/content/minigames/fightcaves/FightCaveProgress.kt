package org.rsmod.content.minigames.fightcaves

internal enum class FightCaveRunState {
    COUNTDOWN,
    ACTIVE_WAVE,
    BETWEEN_WAVES,
    JAD_PHASE,
    COMPLETED,
    ABORTED,
}

/**
 * Small, deterministic state machine kept separate so stale callbacks and duplicate rewards test
 * cleanly.
 */
internal class FightCaveProgress(val generation: Long, initialWaveIndex: Int = 0) {
    var waveIndex: Int = initialWaveIndex
        private set

    var state: FightCaveRunState = FightCaveRunState.COUNTDOWN
        private set

    fun beginWave(): Boolean {
        if (state != FightCaveRunState.COUNTDOWN && state != FightCaveRunState.BETWEEN_WAVES) {
            return false
        }
        state =
            if (waveIndex == FIGHT_CAVE_WAVES.lastIndex) {
                FightCaveRunState.JAD_PHASE
            } else {
                FightCaveRunState.ACTIVE_WAVE
            }
        return true
    }

    fun finishWave(): Boolean {
        if (state != FightCaveRunState.ACTIVE_WAVE || waveIndex >= FIGHT_CAVE_WAVES.lastIndex) {
            return false
        }
        state = FightCaveRunState.BETWEEN_WAVES
        return true
    }

    fun advanceWave(): Boolean {
        if (state != FightCaveRunState.BETWEEN_WAVES || waveIndex >= FIGHT_CAVE_WAVES.lastIndex) {
            return false
        }
        waveIndex++
        return beginWave()
    }

    fun forceWave(index: Int): Boolean {
        if (index !in FIGHT_CAVE_WAVES.indices || isTerminal) {
            return false
        }
        waveIndex = index
        state =
            if (index == FIGHT_CAVE_WAVES.lastIndex) {
                FightCaveRunState.JAD_PHASE
            } else {
                FightCaveRunState.ACTIVE_WAVE
            }
        return true
    }

    fun complete(): Boolean {
        if (state != FightCaveRunState.JAD_PHASE) {
            return false
        }
        state = FightCaveRunState.COMPLETED
        return true
    }

    fun abort(): Boolean {
        if (isTerminal) {
            return false
        }
        state = FightCaveRunState.ABORTED
        return true
    }

    val isTerminal: Boolean
        get() = state == FightCaveRunState.COMPLETED || state == FightCaveRunState.ABORTED
}
