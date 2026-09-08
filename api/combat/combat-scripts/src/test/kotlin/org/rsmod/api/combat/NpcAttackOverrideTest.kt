package org.rsmod.api.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.api.random.GameRandom

class NpcAttackOverrideTest {
    @Test
    fun `defaults retain accurate roll and inaccurate zero`() {
        val random = FixedRoll(23)
        assertEquals(23, NpcAttackOverride().rollDamage(80, true, random))
        assertEquals(0..80, random.lastRange)
        assertEquals(0, NpcAttackOverride().rollDamage(80, false, random))
        assertEquals(1, random.calls)
    }

    @Test
    fun `resisted damage uses its separate base roll before reduction`() {
        val random = FixedRoll(40)
        val attack = NpcAttackOverride(inaccurateMaxHit = 50, damageTransform = { it - 10 })
        assertEquals(30, attack.rollDamage(80, false, random))
        assertEquals(0..50, random.lastRange)
    }

    @Test
    fun `flat protection subtracts from the result rather than reducing roll range`() {
        val random = FixedRoll(30)
        val attack = NpcAttackOverride(damageTransform = { it - 20 - 5 })
        assertEquals(5, attack.rollDamage(80, true, random))
        assertEquals(0..80, random.lastRange)
    }

    @Test
    fun `flat reduction floors low damage at zero`() {
        val attack = NpcAttackOverride(damageTransform = { it - 20 })
        assertEquals(0, attack.rollDamage(80, true, FixedRoll(10)))
    }

    @Test
    fun `a missed ordinary attack does not transform into a successful hit`() {
        var transforms = 0
        val attack = NpcAttackOverride(damageTransform = { transforms++; it + 1 })
        val random = FixedRoll(0)
        assertEquals(0, attack.rollDamage(80, false, random))
        assertEquals(0, transforms)
        assertEquals(0, random.calls)
    }

    private class FixedRoll(private val value: Int) : GameRandom {
        var calls = 0
        var lastRange: IntRange? = null

        override fun of(maxExclusive: Int): Int = error("Unexpected exclusive roll")

        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            val range = minInclusive..maxInclusive
            check(value in range)
            calls++
            lastRange = range
            return value
        }

        override fun randomDouble(): Double = error("Unexpected double roll")
    }
}
