package org.rsmod.content.bosses.nex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NexRewardRulesTest {
    @Test
    fun `mvp receives ten percent larger unique threshold`() {
        val normal = NexRules.uniqueRollThreshold(500, 1000, false)
        val mvp = NexRules.uniqueRollThreshold(500, 1000, true)
        assertEquals(normal * 110 / 100, mvp)
    }

    @Test
    fun `common quantities scale by contribution and never vanish for eligible roll`() {
        assertEquals(50, NexRules.commonQuantity(100, 500, 1000, false))
        assertEquals(55, NexRules.commonQuantity(100, 500, 1000, true))
        assertTrue(NexRules.commonQuantity(1, 1, 10_000, false) >= 1)
    }
}
