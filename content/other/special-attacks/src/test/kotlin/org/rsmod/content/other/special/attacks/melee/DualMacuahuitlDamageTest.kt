package org.rsmod.content.other.special.attacks.melee

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class DualMacuahuitlDamageTest {
    @Test
    fun `raises total minimum and maximum then splits the two hits`() {
        val hits =
            DualMacuahuitlDamage.roll(
                normalMax = 20,
                rollAccuracy = { true },
                rollDamage = { range -> range.last },
            )

        assertArrayEquals(intArrayOf(12, 13), hits)
    }

    @Test
    fun `rolls the two special hits independently`() {
        var calls = 0
        val hits =
            DualMacuahuitlDamage.roll(
                normalMax = 20,
                rollAccuracy = {
                    calls++
                    calls == 1
                },
                rollDamage = { range -> range.first },
            )

        assertArrayEquals(intArrayOf(2, 0), hits)
    }
}
