package org.rsmod.content.drops.tables.monsters

import kotlin.test.Test
import kotlin.test.assertEquals

class YamaDropTableTest {
    @Test
    fun `only the instanced yama npc uses the yama reward table`() {
        assertEquals(listOf("npc.yama"), yamaDropTable.npcs)
        assertEquals("Yama Drops", yamaDropTable.tableIdentifier)
    }

    @Test
    fun `unique and tertiary rates are explicit and not guaranteed`() {
        assertEquals(120, YAMA_UNIQUE_DENOMINATOR)
        assertEquals(12, YAMA_DOSSIER_DENOMINATOR)
        assertEquals(30, YAMA_LOCKBOX_DENOMINATOR)
        assertEquals(15, YAMA_SHARDS_DENOMINATOR)
        assertEquals(2500, YAMA_PET_DENOMINATOR)
        assertEquals(30, YAMA_ELITE_CLUE_DENOMINATOR)
        assertEquals(5.0, yamaUniqueRoll.maxRoll)
        assertEquals(111.0, yamaStandardRoll.maxRoll)
    }
}
