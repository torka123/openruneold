package org.rsmod.content.drops.tables.monsters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dtx.rs.RSWeightedTable
import dtx.rs.RSTable
import org.rsmod.api.droptable.RegisterDropTable

class CerberusDropTableTest {
    @Test
    fun `reflection registers the actual drop table field`() {
        val holder = Class.forName("org.rsmod.content.drops.tables.monsters.CerberusDropTableKt")
        val fields = holder.declaredFields.filter { it.isAnnotationPresent(RegisterDropTable::class.java) }
        assertEquals(listOf("cerberusDropTable"), fields.map { it.name })
        assertTrue(fields.single().get(null) === cerberusDropTable)
    }

    @Test
    fun `unique is one weighted main outcome and each unique has equal weight`() {
        val main = cerberusDropTable.tableEntries.elementAt(3) as RSWeightedTable<*, *>
        assertEquals(130.0, main.maxRoll)
        assertTrue(main.inlineSeparateRolls.isEmpty(), "Unique must replace the ordinary outcome")
        val nested = main.tableEntries.mapNotNull { entry ->
            (entry.rollable as? RSWeightedTable<*, *>)?.let { entry.weight to it }
        }
        val unique = nested.single { (_, table) -> table.tableEntries.size == 4 }
        assertEquals(1.0, unique.first)
        assertEquals(4.0, unique.second.maxRoll)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), unique.second.tableEntries.map { it.weight })
        val guaranteed = cerberusDropTable.tableEntries.first() as RSTable<*, *>
        assertTrue(guaranteed.tableEntries.isEmpty(), "NpcDeath already supplies infernal ashes")
    }

    @Test
    fun `Cerberus table is registered for all three boss forms`() {
        assertEquals("Cerberus Drops", cerberusDropTable.tableIdentifier)
        assertEquals(
            listOf("npc.cerberus_attacking", "npc.cerberus_resetting", "npc.cerberus_sitting"),
            cerberusDropTable.npcs,
        )
    }

    @Test
    fun `ordinary and unique rates use current OSRS totals`() {
        assertEquals(130, CERBERUS_MAIN_TABLE_TOTAL)
        assertEquals(130, CERBERUS_UNIQUE_OUTER_DENOMINATOR)
        assertEquals(4, CERBERUS_UNIQUE_OPTIONS)
        assertEquals(520, CERBERUS_UNIQUE_OUTER_DENOMINATOR * CERBERUS_UNIQUE_OPTIONS)
    }

    @Test
    fun `tertiary rates are explicit and never guaranteed`() {
        assertEquals(15, CERBERUS_ENSOULED_HEAD_DENOMINATOR)
        assertEquals(100, CERBERUS_ELITE_CLUE_DENOMINATOR)
        assertEquals(2000, CERBERUS_JAR_DENOMINATOR)
        assertEquals(3000, CERBERUS_PET_DENOMINATOR)
    }
}
