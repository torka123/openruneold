package dtx.impl.weighted

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightedTableDuplicateWeightTest {
    @Test
    fun `equal-weight entries retain their independent share of the pool`() {
        val table =
            weightedTable<Unit, String> {
                name("duplicate-weight regression")
                3 weight "raw shark"
                3 weight "shark lure"
                2 weight "manta ray"
            }

        assertEquals(8.0, table.maxRoll)
        assertEquals(3, table.tableEntries.size)
        assertEquals(listOf(3.0, 3.0, 2.0), table.tableEntries.map { it.weight })
    }
}
