package org.rsmod.content.bosses.doom

/**
 * Seeded deterministic per-delve combat state machine.
 *
 * Primary attacks, standard styles, and post-rock charges use independent seeded streams. A
 * positional tongue attack and a forced rock used by a phase transition are substitutions, so
 * neither advances those streams or the scheduled free-attack mechanics.
 */
internal object DoomAttackFlow {
    fun initial(delve: Int, seed: Long = 1L): DoomAttackFlowState {
        require(delve >= 1)
        return DoomAttackFlowState(
            nextLarvaTurn = LARVA_START_TURN,
            primaryRandomState = DoomAttackDecision.initialPrimaryStream(seed),
            styleRandomState = DoomAttackDecision.initialStyleStream(seed),
            chargeRandomState = DoomAttackDecision.initialChargeStream(seed),
        )
    }

    /** A rejected generic-combat turn must not advance the seeded attack state. */
    fun canStartTurn(actionDelay: Int, mapClock: Int): Boolean = actionDelay <= mapClock

    fun next(
        delve: Int,
        state: DoomAttackFlowState,
        adjacent: Boolean,
        forceRock: Boolean = false,
        suppressMeleeCharge: Boolean = false,
    ): DoomAttackTurn {
        require(delve >= 1)
        if (forceRock) {
            return DoomAttackTurn(
                nextState = state,
                spawnLarvae = false,
                primary = DoomPrimaryAttack.ROCKS,
                startMeleeCharge = false,
                standardStyle = DoomAttackStyle.RANGED,
                consumesRotation = false,
                advancesPhaseCounters = true,
            )
        }
        if (adjacent) {
            return DoomAttackTurn(
                nextState = state,
                spawnLarvae = false,
                primary = DoomPrimaryAttack.TONGUE,
                startMeleeCharge = false,
                standardStyle = DoomAttackStyle.RANGED,
                consumesRotation = false,
                advancesPhaseCounters = false,
            )
        }

        val primaryRoll =
            DoomAttackDecision.choosePrimary(state.primaryRandomState, state.lastFreePrimary)
        val primary = primaryRoll.value
        val spawnLarvae = state.scheduledTurn >= state.nextLarvaTurn
        val styleRoll =
            if (primary == DoomPrimaryAttack.STANDARD) {
                DoomAttackDecision.chooseStyle(state.styleRandomState, delve)
            } else {
                null
            }
        val style = styleRoll?.value ?: DoomAttackStyle.RANGED
        val chargeRoll =
            if (primary == DoomPrimaryAttack.ROCKS && !suppressMeleeCharge) {
                DoomAttackDecision.chooseCharge(state.chargeRandomState)
            } else {
                null
            }
        val nextState =
            state.copy(
                primaryRandomState = primaryRoll.nextState,
                styleRandomState = styleRoll?.nextState ?: state.styleRandomState,
                chargeRandomState = chargeRoll?.nextState ?: state.chargeRandomState,
                lastFreePrimary = primary,
                scheduledTurn = state.scheduledTurn + 1,
                nextLarvaTurn =
                    if (spawnLarvae) state.nextLarvaTurn + larvaCadence(delve)
                    else state.nextLarvaTurn,
            )
        val startMeleeCharge = chargeRoll?.value == true
        return DoomAttackTurn(
            nextState = nextState,
            spawnLarvae = spawnLarvae,
            primary = primary,
            startMeleeCharge = startMeleeCharge,
            standardStyle = style,
            consumesRotation = true,
            advancesPhaseCounters = true,
        )
    }

    private fun larvaCadence(delve: Int): Int =
        when {
            delve <= 2 -> 6
            delve <= 4 -> 5
            delve <= 6 -> 4
            else -> 3
        }

    private const val LARVA_START_TURN = 3
}

internal data class DoomAttackFlowState(
    val scheduledTurn: Int = 0,
    val nextLarvaTurn: Int,
    val primaryRandomState: Long,
    val styleRandomState: Long,
    val chargeRandomState: Long,
    val lastFreePrimary: DoomPrimaryAttack? = null,
)

/**
 * Encounter-scoped decision streams. Split streams prevent debris, acid, or larva rolls from
 * changing Doom's primary attack, style, or post-rock charge sequence.
 *
 * Captures prove these choices are random: standards and Rock Throws repeat, while consecutive
 * rocks are less likely rather than forbidden. Exact live weights are not exposed by packets, so
 * the explicit 1/4, repeat 1/8, and charge 1/2 rolls remain isolated calibration constants.
 */
internal object DoomAttackDecision {
    private const val STREAM_INCREMENT: Long = -7046029254386353131L
    private const val MIX_ONE: Long = -4658895280553007687L
    private const val MIX_TWO: Long = -7723592293110705685L
    private const val PRIMARY_SALT: Long = 0x13579BDFL
    private const val STYLE_SALT: Long = 0x2468ACE0L
    private const val CHARGE_SALT: Long = 0x5A5A5A5AL
    private const val INITIAL_ROCK_ROLL: Int = 4
    private const val REPEAT_ROCK_ROLL: Int = 8
    private const val CHARGE_ROLL: Int = 2

    fun initialPrimaryStream(seed: Long): Long = seed xor PRIMARY_SALT

    fun initialStyleStream(seed: Long): Long = seed xor STYLE_SALT

    fun initialChargeStream(seed: Long): Long = seed xor CHARGE_SALT

    fun choosePrimary(
        state: Long,
        previous: DoomPrimaryAttack?,
    ): DoomDecisionRoll<DoomPrimaryAttack> {
        val bound = if (previous == DoomPrimaryAttack.ROCKS) REPEAT_ROCK_ROLL else INITIAL_ROCK_ROLL
        val roll = roll(state, bound)
        return DoomDecisionRoll(
            nextState = roll.nextState,
            value = if (roll.value == 0) DoomPrimaryAttack.ROCKS else DoomPrimaryAttack.STANDARD,
        )
    }

    fun chooseStyle(state: Long, delve: Int): DoomDecisionRoll<DoomAttackStyle> {
        require(delve >= 1)
        val styles =
            if (delve == 1) {
                listOf(DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC)
            } else {
                listOf(DoomAttackStyle.MAGIC, DoomAttackStyle.RANGED, DoomAttackStyle.MELEE)
            }
        val roll = roll(state, styles.size)
        return DoomDecisionRoll(roll.nextState, styles[roll.value])
    }

    fun chooseCharge(state: Long): DoomDecisionRoll<Boolean> {
        val roll = roll(state, CHARGE_ROLL)
        return DoomDecisionRoll(roll.nextState, roll.value == 0)
    }

    private fun roll(state: Long, bound: Int): DoomDecisionRoll<Int> {
        require(bound > 0)
        val next = state + STREAM_INCREMENT
        var mixed = next
        mixed = (mixed xor (mixed ushr 30)) * MIX_ONE
        mixed = (mixed xor (mixed ushr 27)) * MIX_TWO
        mixed = mixed xor (mixed ushr 31)
        return DoomDecisionRoll(next, ((mixed ushr 1) % bound.toLong()).toInt())
    }
}

internal data class DoomDecisionRoll<T>(val nextState: Long, val value: T)

internal data class DoomAttackTurn(
    val nextState: DoomAttackFlowState,
    val spawnLarvae: Boolean,
    val primary: DoomPrimaryAttack,
    val startMeleeCharge: Boolean,
    val standardStyle: DoomAttackStyle,
    val consumesRotation: Boolean,
    val advancesPhaseCounters: Boolean,
)

internal enum class DoomPrimaryAttack {
    STANDARD,
    TONGUE,
    ROCKS,
}
