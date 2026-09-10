package dtx.rs

import dtx.core.ArgMap
import dtx.core.RollRandom
import dtx.core.RollResult
import dtx.core.with
import kotlin.test.Test
import kotlin.test.assertEquals

class RollRandomContextTest {
    @Test
    fun `nested standard and tertiary tables consume the same supplied context`() {
        val calls = mutableListOf<String>()
        val random =
            object : RollRandom {
                override fun nextInt(bound: Int): Int {
                    calls += "weight:$bound"
                    return 1
                }

                override fun nextDouble(bound: Double): Double {
                    calls += "chance:$bound"
                    return 0.0
                }
            }
        val table =
            RSDropTable(
                tableIdentifier = "native-random-context",
                mainTable =
                    rsWeightedTable<Unit, String> {
                        1 weight "first"
                        1 weight "second"
                    },
                mainRolls = 2,
                tertiaries = rsTertiaryTable<Unit, String> { (1 outOf 3000) rolls "pet" },
            )
        val result = table.roll(Unit, ArgMap(RollRandom.Context with random)) as RollResult.ListOf
        // Preserve the native flattening order (list results before single results).
        assertEquals(listOf("pet", "second", "second"), result.results)
        assertEquals(listOf("weight:2", "weight:2", "chance:100.0"), calls)
    }
}
