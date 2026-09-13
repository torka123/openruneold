package org.rsmod.content.bosses.nex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NexPhaseRulesMatrixTest {
    @Test
    fun `all four phase calls use a 170 hitpoint lower floor`() {
        val expected =
            mapOf(
                NexState.SMOKE to (2720 to 2550),
                NexState.SHADOW to (2040 to 1870),
                NexState.BLOOD to (1360 to 1190),
                NexState.ICE to (680 to 510),
            )
        expected.forEach { (state, values) ->
            assertEquals(values.first, NexRules.threshold(state))
            assertEquals(values.second, NexRules.lowerFloor(state))
            assertEquals(0, NexRules.phaseDamage(values.second, 1000, state))
            assertTrue(NexRules.phaseDamage(values.first, 1000, state) <= 170)
        }
    }

    @Test
    fun `every non-final phase alternates the correct specials`() {
        val rotations =
            mapOf(
                NexState.SMOKE to (NexSpecial.CHOKE to NexSpecial.SMOKE_DASH),
                NexState.SHADOW to (NexSpecial.SHADOW_SMASH to NexSpecial.EMBRACE_DARKNESS),
                NexState.BLOOD to (NexSpecial.BLOOD_SIPHON to NexSpecial.BLOOD_SACRIFICE),
                NexState.ICE to (NexSpecial.CONTAINMENT to NexSpecial.ICE_PRISON),
            )
        rotations.forEach { (state, pair) ->
            assertEquals(pair.first, NexRules.special(state, 0))
            assertEquals(pair.second, NexRules.special(state, 1))
            assertEquals(pair.first, NexRules.special(state, 2))
        }
    }

    @Test
    fun `mvp tie inputs remain deterministically rankable`() {
        val entries =
            listOf(Triple(12, 500, 20), Triple(8, 500, 20), Triple(4, 500, 10))
                .sortedWith(
                    compareByDescending<Triple<Int, Int, Int>> { it.second }
                        .thenBy { it.third }
                        .thenBy { it.first }
                )
        assertEquals(4, entries.first().first)
    }

    @Test
    fun `unique chance scales by contribution and mvp multiplier`() {
        val fullShare = NexRules.uniqueRollThreshold(1000, 1000, false)
        val halfShare = NexRules.uniqueRollThreshold(500, 1000, false)
        assertEquals(fullShare / 2, halfShare)
        assertEquals(fullShare * 110 / 100, NexRules.uniqueRollThreshold(1000, 1000, true))
    }
}
