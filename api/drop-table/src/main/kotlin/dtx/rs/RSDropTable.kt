package dtx.rs

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.core.flattenToList
import dtx.table.TableHooks

public class RSDropTable<T, R>(
    public override val tableIdentifier: String,
    public val npcs: List<String> = emptyList(),
    public val locs: List<String> = emptyList(),
    public val areas: List<String> = emptyList(),
    private val guaranteed: RSTable<T, R> = RSGuaranteedTable.Empty(),
    private val preRoll: RSTable<T, R> = RSPreRollTable.Empty(),
    separateRolls: RSTable<T, R> = RSPreRollTable.Empty(),
    private val mainTable: RSTable<T, R> = RSWeightedTable.Empty(),
    private val mainRolls: Int = 1,
    private val exclusivePreRollWhen: (T, RollResult<R>) -> Boolean = { _, _ -> false },
    private val mainRollsFor: (T, RollResult<R>) -> Int = { _, _ -> mainRolls.coerceAtLeast(1) },
    private val tertiaries: RSTable<T, R> = RSPreRollTable.Empty(),
    private val hooks: TableHooks<T, R> = TableHooks.Default(),
) : RSTable<T, R>, TableHooks<T, R> by hooks {

    private val separateRolls: RSTable<T, R> = mergeInlineSeparateRolls(separateRolls, mainTable)

    override val tableEntries: Collection<Rollable<T, R>> =
        listOf(guaranteed, preRoll, separateRolls, mainTable, tertiaries)

    override fun selectResult(target: T, otherArgs: ArgMap): RollResult<R> {
        val results = mutableListOf<RollResult<R>>()
        results.add(guaranteed.roll(target, otherArgs))
        val preResult = preRoll.roll(target, otherArgs)
        results.add(preResult)
        if (exclusivePreRollWhen(target, preResult)) {
            return listOf(preResult).flattenToList()
        }
        val separateResult = separateRolls.roll(target, otherArgs)
        results.add(separateResult)
        repeat(mainRollsFor(target, separateResult).coerceAtLeast(0)) {
            results.add(mainTable.roll(target, otherArgs))
        }
        results.add(tertiaries.roll(target, otherArgs))

        return results.flattenToList()
    }
}
