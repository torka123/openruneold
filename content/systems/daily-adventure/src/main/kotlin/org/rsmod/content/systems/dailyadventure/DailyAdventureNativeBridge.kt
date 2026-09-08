package org.rsmod.content.systems.dailyadventure

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.VarBitType
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.baseVar
import org.rsmod.api.dailyadventure.DailyAdventurePath
import org.rsmod.api.dailyadventure.DailyAdventureState
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.game.entity.Player

/**
 * Projects Daily Adventure state onto the fixed revision-240 Adventure Paths interface.
 *
 * The native task list is covered by ten compact server-owned rows that reuse the stock Attack,
 * Strength, Defence, and Ranged sprites as placeholders. Every visible name and value is supplied
 * from authoritative Daily Adventure state.
 *
 * - native path 0 is Daily Combat;
 * - native path 1 (the old Gatherer path) is Daily Skilling;
 * - all ten Daily assignments are rendered by appended server-owned children;
 * - the five unused stock rows are covered and never armed as Daily actions.
 */
internal object DailyAdventureNativeProjection {
    data class Snapshot(
        val completedTasksMask: Int,
        val completedPathsMask: Int,
        val combatTasksDone: Int,
        val skillingTasksDone: Int,
        val combatFocusedTask: Int,
        val skillingFocusedTask: Int,
        val unrepresentedSkillingTasks: Int,
    )

    fun pathForRow(row: Int): DailyAdventurePath? =
        when (row) {
            0 -> DailyAdventurePath.Combat
            1 -> DailyAdventurePath.Skilling
            else -> null
        }

    fun pathRow(comsub: Int, offset: Int): Int? {
        if (comsub < offset || (comsub - offset) % PATH_STRIDE != 0) return null
        return ((comsub - offset) / PATH_STRIDE).takeIf { it in 0 until PATH_ROWS }
    }

    fun taskRow(comsub: Int): Int? {
        if (comsub < TASK_FIRST || (comsub - TASK_FIRST) % TASK_STRIDE != 0) return null
        return ((comsub - TASK_FIRST) / TASK_STRIDE).takeIf { it in 0 until TASK_ROWS }
    }

    fun project(state: DailyAdventureState): Snapshot {
        val combat = state.assignments[DailyAdventurePath.Combat].orEmpty()
        val skilling = state.assignments[DailyAdventurePath.Skilling].orEmpty()
        var completedTasks = 0

        combat
            .asSequence()
            .filter { it.slot in 0 until DAILY_TASK_ROWS && it.complete }
            .forEach { completedTasks = completedTasks or (1 shl (COMBAT_TASK_ID_FIRST + it.slot)) }
        skilling
            .asSequence()
            .filter { it.slot in 0 until NATIVE_SKILLING_TASK_ROWS && it.complete }
            .forEach { completedTasks = completedTasks or (1 shl (SKILLING_TASK_ID_FIRST + it.slot)) }

        val completedPaths =
            (if (DailyAdventurePath.Combat in state.claimedPaths) 1 shl COMBAT_PATH_ROW else 0) or
                (if (DailyAdventurePath.Skilling in state.claimedPaths) 1 shl SKILLING_PATH_ROW else 0)

        val visibleCombat = combat.filter { it.slot in 0 until DAILY_TASK_ROWS }
        val visibleSkilling = skilling.filter { it.slot in 0 until NATIVE_SKILLING_TASK_ROWS }
        return Snapshot(
            completedTasksMask = completedTasks,
            completedPathsMask = completedPaths,
            combatTasksDone = visibleCombat.count { it.complete },
            skillingTasksDone = visibleSkilling.count { it.complete },
            combatFocusedTask =
                visibleCombat.filterNot { it.complete }.minOfOrNull { it.slot + COMBAT_TASK_ID_FIRST }
                    ?: 0,
            skillingFocusedTask =
                visibleSkilling.filterNot { it.complete }.minOfOrNull { it.slot + 1 } ?: 0,
            unrepresentedSkillingTasks = skilling.count { it.slot >= NATIVE_SKILLING_TASK_ROWS },
        )
    }

    const val TASK_FIRST: Int = 11
    const val TASK_STRIDE: Int = 12
    const val TASK_ROWS: Int = 15
    const val DAILY_TASK_ROWS: Int = 10
    const val NATIVE_SKILLING_TASK_ROWS: Int = 3
    const val PATH_STRIDE: Int = 39
    const val PATH_ROWS: Int = 5
    const val PATH_ROW_OFFSET: Int = 0
    const val PATH_SELECT_OFFSET: Int = 10
    const val PATH_SHOW_OFFSET: Int = 38

    private const val COMBAT_PATH_ROW = 0
    private const val SKILLING_PATH_ROW = 1
    private const val COMBAT_TASK_ID_FIRST = 1
    private const val SKILLING_TASK_ID_FIRST = 16
}

internal fun Player.syncDailyAdventureNative(
    state: DailyAdventureState,
    focusedPath: DailyAdventurePath?,
) {
    val projection = DailyAdventureNativeProjection.project(state)
    setDailyAdventureNativeVarbit(ADVENTURE_PATH_PARTICIPATION_VARBIT, ADVENTURE_PATH_ACTIVE)
    setDailyAdventureNativeVarp(ADVENTURE_PATH_COMPLETED_TASKS_VARP, projection.completedTasksMask)
    setDailyAdventureNativeVarp(ADVENTURE_PATH_COMPLETED_PATHS_VARP, projection.completedPathsMask)
    setDailyAdventureNativeVarbit(ADVENTURE_PATH_COMBAT_TASKS_DONE_VARBIT, projection.combatTasksDone)
    setDailyAdventureNativeVarbit(ADVENTURE_PATH_SKILLING_TASKS_DONE_VARBIT, projection.skillingTasksDone)
    setDailyAdventureNativeVarbit(ADVENTURE_PATH_COMBAT_FOCUSED_TASK_VARBIT, projection.combatFocusedTask)
    setDailyAdventureNativeVarbit(ADVENTURE_PATH_SKILLING_FOCUSED_TASK_VARBIT, projection.skillingFocusedTask)
    setDailyAdventureNativeVarbit(ADVENTURE_PATH_MAIN_TAB_VARBIT, ADVENTURE_PATH_LIST_TAB)
    setDailyAdventureNativeVarbit(
        ADVENTURE_PATH_FOCUSED_PATH_VARBIT,
        if (focusedPath == DailyAdventurePath.Skilling) 1 else 0,
    )
    ADVENTURE_PATH_VARPS.forEach(::resyncVar)
}

private const val ADVENTURE_PATH_ACTIVE = 1
private const val ADVENTURE_PATH_LIST_TAB = 0
private const val ADVENTURE_PATH_PARTICIPATION_VARBIT = 9340
private const val ADVENTURE_PATH_COMPLETED_TASKS_VARP = 2364
private const val ADVENTURE_PATH_COMPLETED_PATHS_VARP = 2361
private const val ADVENTURE_PATH_COMBAT_TASKS_DONE_VARBIT = 9341
private const val ADVENTURE_PATH_SKILLING_TASKS_DONE_VARBIT = 10163
private const val ADVENTURE_PATH_COMBAT_FOCUSED_TASK_VARBIT = 9325
private const val ADVENTURE_PATH_SKILLING_FOCUSED_TASK_VARBIT = 10158
private const val ADVENTURE_PATH_FOCUSED_PATH_VARBIT = 9324
private const val ADVENTURE_PATH_MAIN_TAB_VARBIT = 9328

private val ADVENTURE_PATH_VARPS: List<VarpServerType> by lazy {
    listOf(
            adventurePathVarpType(ADVENTURE_PATH_COMPLETED_TASKS_VARP),
            adventurePathVarpType(ADVENTURE_PATH_COMPLETED_PATHS_VARP),
            adventurePathVarbitType(ADVENTURE_PATH_PARTICIPATION_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_COMBAT_TASKS_DONE_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_SKILLING_TASKS_DONE_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_COMBAT_FOCUSED_TASK_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_SKILLING_FOCUSED_TASK_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_FOCUSED_PATH_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_MAIN_TAB_VARBIT).baseVar,
        )
        .distinctBy(VarpServerType::id)
}

private fun Player.setDailyAdventureNativeVarp(id: Int, value: Int) {
    VarPlayerIntMapSetter.set(this, adventurePathVarpType(id), value)
}

private fun Player.setDailyAdventureNativeVarbit(id: Int, value: Int) {
    VarPlayerIntMapSetter.set(this, adventurePathVarbitType(id), value)
}

private fun adventurePathVarpType(id: Int): VarpServerType =
    requireNotNull(ServerCacheManager.getVarp(id)) { "Missing Adventure Paths varp $id" }

private fun adventurePathVarbitType(id: Int): VarBitType =
    requireNotNull(ServerCacheManager.getVarbit(id)) { "Missing Adventure Paths varbit $id" }
