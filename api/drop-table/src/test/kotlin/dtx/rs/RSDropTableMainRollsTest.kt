package dtx.rs

import dtx.core.ArgMap
import dtx.core.RollResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RSDropTableMainRollsTest {
    @Test
    fun twoMainRollsDoNotRepeatTertiaries() {
        val main = rsGuaranteedTable<Unit, String> { add("main") }
        val tertiary = rsGuaranteedTable<Unit, String> { add("tertiary") }
        val table =
            RSDropTable(
                tableIdentifier = "two-main-roll-test",
                mainTable = main,
                mainRolls = 2,
                tertiaries = tertiary,
            )

        val result = table.selectResult(Unit, ArgMap.Empty)

        assertEquals(
            listOf("main", "main", "tertiary"),
            (result as RollResult.ListOf<String>).results,
        )
    }

    @Test
    fun exclusivePreRollSuppressesEveryOtherRewardSection() {
        val table =
            RSDropTable(
                tableIdentifier = "exclusive-pre-roll-test",
                guaranteed = rsGuaranteedTable<Unit, String> { add("guaranteed") },
                preRoll = rsGuaranteedTable<Unit, String> { add("exclusive") },
                separateRolls = rsGuaranteedTable<Unit, String> { add("separate") },
                mainTable = rsGuaranteedTable<Unit, String> { add("main") },
                mainRolls = 2,
                exclusivePreRollWhen = { _, result ->
                    when (result) {
                        is RollResult.Single -> result.result == "exclusive"
                        is RollResult.ListOf -> "exclusive" in result.results
                        RollResult.Nothing -> false
                    }
                },
                tertiaries = rsGuaranteedTable<Unit, String> { add("tertiary") },
            )

        val result = table.selectResult(Unit, ArgMap.Empty) as RollResult.ListOf<String>

        assertEquals(listOf("exclusive"), result.results)
    }

    @Test
    fun separateResultCanReduceMainRollCount() {
        val table =
            RSDropTable(
                tableIdentifier = "dynamic-main-roll-test",
                separateRolls = rsGuaranteedTable<Unit, String> { add("supplies") },
                mainTable = rsGuaranteedTable<Unit, String> { add("main") },
                mainRolls = 2,
                mainRollsFor = { _, result ->
                    val values =
                        when (result) {
                            is RollResult.Single -> listOf(result.result)
                            is RollResult.ListOf -> result.results
                            RollResult.Nothing -> emptyList()
                        }
                    if ("supplies" in values) 1 else 2
                },
            )

        val result = table.selectResult(Unit, ArgMap.Empty) as RollResult.ListOf<String>

        assertContains(result.results, "supplies")
        assertEquals(1, result.results.count { it == "main" })
    }
}
