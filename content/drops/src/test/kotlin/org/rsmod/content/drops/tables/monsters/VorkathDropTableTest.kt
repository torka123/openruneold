package org.rsmod.content.drops.tables.monsters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VorkathDropTableTest {
    @Test
    fun `default reward multiplier is exactly one`() {
        assertEquals(1.0, VORKATH_DROP_RATE_MULTIPLIER)
    }

    @Test
    fun `shark lure quantity choices are even from 70 through 110`() {
        assertEquals(70, vorkathSharkLureCounts.first())
        assertEquals(110, vorkathSharkLureCounts.last())
        assertEquals(21, vorkathSharkLureCounts.size)
        assertTrue(vorkathSharkLureCounts.all { it in 70..110 && it % 2 == 0 })
    }

    @Test
    fun `post quest Vorkath is the only registered form`() {
        assertEquals(listOf("npc.vorkath"), vorkathDropTable.npcs)
    }
}
