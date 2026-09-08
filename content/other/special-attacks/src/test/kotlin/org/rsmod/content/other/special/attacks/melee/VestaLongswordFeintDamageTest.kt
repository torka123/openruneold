package org.rsmod.content.other.special.attacks.melee

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VestaLongswordFeintDamageTest {
    @Test
    fun `uses the inclusive 20 to 120 percent normal-max range`() {
        assertEquals(20..120, VestaLongswordFeintDamage.range(normalMax = 100))
    }

    @Test
    fun `floors both percentage endpoints`() {
        assertEquals(14..87, VestaLongswordFeintDamage.range(normalMax = 73))
    }

    @Test
    fun `keeps a zero maximum valid`() {
        assertEquals(0..0, VestaLongswordFeintDamage.range(normalMax = 0))
    }
}
