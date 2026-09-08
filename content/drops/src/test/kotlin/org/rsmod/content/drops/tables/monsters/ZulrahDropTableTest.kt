package org.rsmod.content.drops.tables.monsters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZulrahDropTableTest {
    @Test
    fun `current common table totals 249`() {
        assertEquals(249.0, zulrahCommonTable.maxRoll)
        assertTrue(zulrahCommonTable.tableEntries.isNotEmpty())
    }

    @Test
    fun `shark lure replacement table uses current three three two weights`() {
        assertEquals(8.0, zulrahSharkTable.maxRoll)
        assertEquals(3, zulrahSharkTable.tableEntries.size)
    }

    @Test
    fun `per roll unique table represents four independent one in 1024 outcomes`() {
        assertEquals(1024.0, zulrahPerRollTable.maxRoll)
        assertTrue(
            zulrahDropTable.npcs.containsAll(
                listOf(
                    "npc.snakeboss_boss_ranged",
                    "npc.snakeboss_boss_melee",
                    "npc.snakeboss_boss_magic",
                )
            )
        )
    }
}
