package org.rsmod.content.interfaces.spellbook.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MagicTabFilterScriptTest {
    @Test
    fun `stock filter rows map explicitly and reject unknown children`() {
        val expected =
            mapOf(
                0 to MagicFilterOption.Combat,
                1 to MagicFilterOption.Teleport,
                2 to MagicFilterOption.Utility,
                3 to MagicFilterOption.LackLevel,
                4 to MagicFilterOption.LackRunes,
                5 to MagicFilterOption.Locked,
                6 to MagicFilterOption.Resizing,
            )
        assertEquals(0..6, MAGIC_FILTER_ROW_RANGE)
        expected.forEach { (comsub, option) -> assertEquals(option, magicFilterOption(comsub)) }
        assertNull(magicFilterOption(-1))
        assertNull(magicFilterOption(7))
    }
}
