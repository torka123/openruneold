package org.rsmod.content.bosses.nex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NexRulesTest {
    @Test
    fun `critical revision 240 coordinates remain pinned`() {
        assertEquals(2900, NEX_BANK_TELEPORT.x)
        assertEquals(5203, NEX_BANK_TELEPORT.z)
        assertEquals(2910, NEX_ARENA_ENTRY.x)
        assertEquals(2924, NEX_SPAWN.x)
        assertEquals(5202, NEX_SPAWN.z)
    }

    @Test
    fun `soft cap reduces only damage above fifty`() {
        assertEquals(50, NexRules.softCap(50))
        assertEquals(60, NexRules.softCap(80))
        assertEquals(0, NexRules.softCap(-1))
    }

    @Test
    fun `smoke phase honours threshold and 170 hp lower floor`() {
        assertEquals(66, NexRules.phaseDamage(2820, 100, NexState.SMOKE))
        assertEquals(6, NexRules.phaseDamage(2720, 100, NexState.SMOKE))
        assertEquals(0, NexRules.phaseDamage(2550, 100, NexState.SMOKE))
        assertTrue(NexRules.phaseDamage(2730, 1000, NexState.SMOKE) <= 180)
    }

    @Test
    fun `called bodyguards advance the fixed phase order`() {
        assertEquals(NexState.FUMUS, NexRules.calledBodyguard(NexState.SMOKE))
        assertEquals(NexState.SHADOW, NexRules.nextPhase(NexState.FUMUS))
        assertEquals(NexState.ZAROS, NexRules.nextPhase(NexState.GLACIES))
    }

    @Test
    fun `special cycle alternates every five attacks`() {
        assertEquals(NexSpecial.CHOKE, NexRules.special(NexState.SMOKE, 5, 0))
        assertEquals(NexSpecial.SMOKE_DASH, NexRules.special(NexState.SMOKE, 5, 1))
        assertEquals(null, NexRules.special(NexState.SMOKE, 4, 0))
    }

    @Test
    fun `eligibility is five percent capped at one hundred`() {
        assertEquals(50, NexRules.eligibleDamage(1000))
        assertEquals(100, NexRules.eligibleDamage(10000))
        assertTrue(NexRules.isEligible(100, 10000))
        assertFalse(NexRules.isEligible(99, 10000))
    }

    @Test
    fun `reaver damage heals nex for seventy five percent`() {
        assertEquals(75, NexRules.reaverHeal(100))
    }
}
