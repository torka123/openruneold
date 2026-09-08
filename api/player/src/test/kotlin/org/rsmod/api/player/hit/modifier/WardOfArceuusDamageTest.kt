package org.rsmod.api.player.hit.modifier

import kotlin.test.Test
import kotlin.test.assertEquals

class WardOfArceuusDamageTest {
    @Test
    fun `ward reduces demon damage by ten percent rounded down`() {
        assertEquals(90, wardOfArceuusDamage(100, wardActive = true, isDemon = true))
        assertEquals(10, wardOfArceuusDamage(11, wardActive = true, isDemon = true))
        for (damage in 1..9) {
            assertEquals(damage, wardOfArceuusDamage(damage, wardActive = true, isDemon = true))
        }
    }

    @Test
    fun `ward never reduces non demon or inactive damage`() {
        assertEquals(100, wardOfArceuusDamage(100, wardActive = false, isDemon = true))
        assertEquals(100, wardOfArceuusDamage(100, wardActive = true, isDemon = false))
    }
}
