package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoomAttackFlowTest {
    @Test
    fun `cooldown gate prevents rejected attacks from consuming the rotation`() {
        assertFalse(DoomAttackFlow.canStartTurn(actionDelay = 101, mapClock = 100))
        assertTrue(DoomAttackFlow.canStartTurn(actionDelay = 100, mapClock = 100))
        assertTrue(DoomAttackFlow.canStartTurn(actionDelay = 99, mapClock = 100))
    }

    @Test
    fun `same seed and immutable state always produce the same turn`() {
        val state = DoomAttackFlow.initial(8, seed = 99L)
        val first = DoomAttackFlow.next(delve = 8, state = state, adjacent = false)
        val second = DoomAttackFlow.next(delve = 8, state = state, adjacent = false)

        assertEquals(first, second)
    }

    @Test
    fun `seeded free attacks reproduce while retaining captured random choices`() {
        fun turns(delve: Int, seed: Long, count: Int = 128): List<DoomAttackTurn> {
            var state = DoomAttackFlow.initial(delve, seed)
            return List(count) {
                DoomAttackFlow.next(delve, state, adjacent = false).also { state = it.nextState }
            }
        }

        assertEquals(turns(1, 11L), turns(1, 11L))
        val delveOne = turns(1, 11L)
        assertTrue(delveOne.any { it.primary == DoomPrimaryAttack.STANDARD })
        assertTrue(delveOne.any { it.primary == DoomPrimaryAttack.ROCKS })
        assertFalse(
            delveOne
                .filter { it.primary == DoomPrimaryAttack.STANDARD }
                .any { it.standardStyle == DoomAttackStyle.MELEE }
        )
        val delveTwoStyles =
            turns(2, 23L)
                .filter { it.primary == DoomPrimaryAttack.STANDARD }
                .mapTo(linkedSetOf()) { it.standardStyle }
        assertEquals(
            setOf(DoomAttackStyle.MAGIC, DoomAttackStyle.RANGED, DoomAttackStyle.MELEE),
            delveTwoStyles,
        )
    }

    @Test
    fun `post-rock charge is a separate seeded decision and standards never arm it`() {
        var chargedRockSeen = false
        var unchargedRockSeen = false
        for (seed in 1L..128L) {
            var state = DoomAttackFlow.initial(4, seed)
            repeat(32) {
                val turn = DoomAttackFlow.next(4, state, adjacent = false)
                state = turn.nextState
                if (turn.primary == DoomPrimaryAttack.ROCKS) {
                    chargedRockSeen = chargedRockSeen || turn.startMeleeCharge
                    unchargedRockSeen = unchargedRockSeen || !turn.startMeleeCharge
                } else {
                    assertFalse(turn.startMeleeCharge)
                }
            }
        }
        assertTrue(chargedRockSeen)
        assertTrue(unchargedRockSeen)
    }

    @Test
    fun `adjacent tongue turns do not consume the seeded primary attack rotation`() {
        val seed = 77L
        var directState = DoomAttackFlow.initial(5, seed)
        var tongueState = DoomAttackFlow.initial(5, seed)
        val directPrimaries = mutableListOf<DoomPrimaryAttack>()
        val primariesAfterTongues = mutableListOf<DoomPrimaryAttack>()

        repeat(32) {
            val direct = DoomAttackFlow.next(5, directState, adjacent = false)
            directState = direct.nextState
            directPrimaries += direct.primary

            val primaryStreamBeforeTongue = tongueState.primaryRandomState
            val tongue = DoomAttackFlow.next(5, tongueState, adjacent = true)
            assertEquals(DoomPrimaryAttack.TONGUE, tongue.primary)
            assertFalse(tongue.consumesRotation)
            assertFalse(tongue.advancesPhaseCounters)
            assertEquals(primaryStreamBeforeTongue, tongue.nextState.primaryRandomState)
            assertEquals(tongueState, tongue.nextState)

            val resumed = DoomAttackFlow.next(5, tongue.nextState, adjacent = false)
            tongueState = resumed.nextState
            primariesAfterTongues += resumed.primary
        }

        assertEquals(directPrimaries, primariesAfterTongues)
    }

    @Test
    fun `forced rock does not consume or trigger other mechanics`() {
        val state = DoomAttackFlow.initial(4, seed = 42L).copy(scheduledTurn = 14)
        val forced =
            DoomAttackFlow.next(
                delve = 4,
                state = state,
                adjacent = true,
                forceRock = true,
                suppressMeleeCharge = true,
            )

        assertEquals(DoomPrimaryAttack.ROCKS, forced.primary)
        assertFalse(forced.spawnLarvae)
        assertFalse(forced.startMeleeCharge)
        assertFalse(forced.consumesRotation)
        assertTrue(forced.advancesPhaseCounters)
        assertEquals(state, forced.nextState)
    }

    @Test
    fun `delves deeper than eight retain the capped deep-delve attack flow`() {
        val state = DoomAttackFlow.initial(9, seed = 19L)
        val turn = DoomAttackFlow.next(9, state, adjacent = false)

        assertTrue(turn.consumesRotation)
        assertTrue(turn.advancesPhaseCounters)
    }

    @Test
    fun `forced and tongue substitutions leave all three random streams unchanged`() {
        val state = DoomAttackFlow.initial(8, seed = 120L)
        assertEquals(state, DoomAttackFlow.next(8, state, adjacent = true).nextState)
        assertEquals(
            state,
            DoomAttackFlow.next(8, state, adjacent = false, forceRock = true).nextState,
        )
    }
}
