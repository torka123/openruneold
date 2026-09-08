package org.rsmod.content.systems.dailyadventure

import org.rsmod.api.dailyadventure.DailyAdventureCatalogue
import org.rsmod.api.dailyadventure.DailyAdventureDifficulty
import org.rsmod.api.dailyadventure.DailyAdventurePath
import org.rsmod.api.dailyadventure.DailyAdventureState

/** Server-owned children appended to the stock revision-240 Adventure Paths interface (642). */
public object NativeDailyAdventureComponents {
    /**
     * The current RuneLite client rejects the patched interface archive and loads stock children
     * 0..35. Keep every write to children 36..72 disabled until that client overlay is distributed.
     * The server cache cannot be used as the capability signal because it already has the patch.
     */
    internal const val ClientExtensionsEnabled: Boolean = false

    public const val Interface: Int = 642
    public const val StockTasks: Int = 7
    public const val StockNavigation: Int = 22
    public const val StockPathReward: Int = 24
    public const val StockTasksDone: Int = 25
    public const val StockTaskFocus: Int = 26
    public const val StockTaskScrollbar: Int = 27
    public const val StockTaskHint: Int = 29
    public const val StockRewardList: Int = 32
    public const val StockRewardScrollbar: Int = 33
    public const val StockPaths: Int = 35

    public const val StockNavigationIconSub: Int = 9
    public const val StockPathRewardTextSub: Int = 9
    public const val StockPathRewardIconSub: Int = 10
    public const val StockTasksDoneTextSub: Int = 0
    public const val StockTaskFocusIconSub: Int = 9
    public const val StockCombatPathLabelSub: Int = 9
    public const val StockSkillingPathLabelSub: Int = 48

    public const val RowBackgroundFirst: Int = 36
    public const val RowGraphicFirst: Int = 46
    public const val RowTextFirst: Int = 56
    public const val Progress: Int = 66
    public const val Reward: Int = 67
    public const val Rerolls: Int = 68
    public const val TaskShop: Int = 69
    public const val StreakButton: Int = 70
    public const val StreakLabel: Int = 71
    public const val Reset: Int = 72
    public const val DailyRows: Int = 10
    public const val NativeRows: Int = 10

    internal val RequiredClientChildren: Set<Int> = (RowBackgroundFirst..Reset).toSet()

    internal fun hasRequiredClientChildren(children: Collection<Int>): Boolean =
        children.containsAll(RequiredClientChildren)

    public fun rowBackground(slot: Int): Int {
        require(slot in 0 until DailyRows)
        return RowBackgroundFirst + slot
    }

    public fun rowText(slot: Int): Int {
        require(slot in 0 until DailyRows)
        return RowTextFirst + slot
    }

    public fun packed(child: Int): Int = (Interface shl 16) or child

    internal fun completedTasks(state: DailyAdventureState, path: DailyAdventurePath?): Int =
        path
            ?.let { state.assignments[it].orEmpty() }
            .orEmpty()
            .asSequence()
            .filter { it.slot in 0 until DailyRows && it.complete }
            .count()
            .coerceIn(0, DailyRows)
}

internal data class NativeDailyTaskRow(
    val slot: Int,
    val taskId: String,
    val name: String,
    val description: String,
    val difficulty: DailyAdventureDifficulty,
    val progress: Long,
    val goal: Long,
    val reward: Long,
    val complete: Boolean,
)

internal data class NativeDailyTaskSnapshot(
    val path: DailyAdventurePath?,
    val rows: List<NativeDailyTaskRow>,
    val selected: NativeDailyTaskRow?,
    val rerollsRemaining: Int,
    val rerollCap: Int,
) {
    val canRerollSelected: Boolean
        get() = selected != null && !selected.complete && rerollsRemaining > 0
}

internal object NativeDailyAdventureViewProjection {
    fun project(
        state: DailyAdventureState,
        path: DailyAdventurePath?,
        selectedSlot: Int?,
    ): NativeDailyTaskSnapshot {
        val activePath = path?.takeIf { it in state.activePaths }
        val rows =
            activePath?.let { state.assignments[it] }
                .orEmpty()
                .asSequence()
                .filter { it.slot in 0 until NativeDailyAdventureComponents.DailyRows }
                .sortedBy { it.slot }
                .mapNotNull { assignment ->
                    val definition = DailyAdventureCatalogue.byId[assignment.taskId]
                        ?: return@mapNotNull null
                    NativeDailyTaskRow(
                        slot = assignment.slot,
                        taskId = assignment.taskId,
                        name = definition.name,
                        description = definition.description,
                        difficulty = assignment.difficulty,
                        progress = assignment.progress.coerceAtMost(assignment.goal),
                        goal = assignment.goal,
                        reward = assignment.reward,
                        complete = assignment.complete,
                    )
                }
                .toList()
        return NativeDailyTaskSnapshot(
            path = activePath,
            rows = rows,
            selected = rows.firstOrNull { it.slot == selectedSlot },
            rerollsRemaining = state.rerollsRemaining(),
            rerollCap = state.rerollCap(),
        )
    }
}
