package org.rsmod.content.interfaces.journal.tab

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.VarBitType
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.baseVar
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal object AdventurePathProgress {
    data class Snapshot(
        val completedTasks: Int,
        val completedPaths: Int,
        val combatTasksDone: Int,
        val gathererTasksDone: Int,
        val combatFocusedTask: Int,
        val gathererFocusedTask: Int,
    )

    private data class Task(val id: Int, val path: Int, val stat: String, val level: Int)

    private val tasks =
        listOf(
            Task(1, COMBAT_PATH, "attack", 5),
            Task(2, COMBAT_PATH, "attack", 10),
            Task(3, COMBAT_PATH, "attack", 20),
            Task(4, COMBAT_PATH, "strength", 5),
            Task(5, COMBAT_PATH, "strength", 10),
            Task(6, COMBAT_PATH, "strength", 20),
            Task(7, COMBAT_PATH, "defence", 5),
            Task(8, COMBAT_PATH, "defence", 10),
            Task(9, COMBAT_PATH, "defence", 20),
            Task(10, COMBAT_PATH, "ranged", 5),
            Task(11, COMBAT_PATH, "ranged", 10),
            Task(12, COMBAT_PATH, "ranged", 20),
            Task(13, COMBAT_PATH, "magic", 5),
            Task(14, COMBAT_PATH, "magic", 10),
            Task(15, COMBAT_PATH, "magic", 20),
            Task(16, GATHERER_PATH, "fishing", 5),
            Task(17, GATHERER_PATH, "woodcutting", 5),
            Task(18, GATHERER_PATH, "mining", 5),
        )

    fun calculate(level: (String) -> Int): Snapshot {
        val completed = tasks.filter { level(it.stat) >= it.level }
        val completedMask = completed.fold(0) { mask, task -> mask or (1 shl task.id) }
        val combat = tasks.filter { it.path == COMBAT_PATH }
        val gatherer = tasks.filter { it.path == GATHERER_PATH }
        val combatDone = completed.count { it.path == COMBAT_PATH }
        val gathererDone = completed.count { it.path == GATHERER_PATH }
        val pathsMask =
            (if (combatDone == combat.size) 1 shl COMBAT_PATH else 0) or
                (if (gathererDone == gatherer.size) 1 shl GATHERER_PATH else 0)
        return Snapshot(
            completedTasks = completedMask,
            completedPaths = pathsMask,
            combatTasksDone = combatDone,
            gathererTasksDone = gathererDone,
            combatFocusedTask =
                combat.firstOrNull { completedMask and (1 shl it.id) == 0 }?.id ?: 0,
            // Varbit 10158 is a three-bit, path-local task selector (0..7), not a global
            // Adventure Paths task id. The gatherer task ids are 16..18, so writing the
            // global id overflows the revision-240 varbit during login.
            gathererFocusedTask =
                gatherer.indexOfFirst { completedMask and (1 shl it.id) == 0 }.let { if (it < 0) 0 else it + 1 },
        )
    }

    private const val COMBAT_PATH = 0
    private const val GATHERER_PATH = 1
}

internal fun Player.refreshAdventurePaths() {
    // OpenRune has no Tutorial Island eligibility choice. Keep the real revision-240 visibility
    // state active so the journal entry and Adventurer Jon morph are available to every player.
    setAdventurePathVarbit(ADVENTURE_PATH_PARTICIPATION_VARBIT, ADVENTURE_PATH_ACTIVE)

    val progress = AdventurePathProgress.calculate { stat -> statBase("stat.$stat") }
    setAdventurePathVarp(ADVENTURE_PATH_COMPLETED_TASKS_VARP, progress.completedTasks)
    setAdventurePathVarp(ADVENTURE_PATH_COMPLETED_PATHS_VARP, progress.completedPaths)
    setAdventurePathVarbit(ADVENTURE_PATH_COMBAT_TASKS_DONE_VARBIT, progress.combatTasksDone)
    setAdventurePathVarbit(ADVENTURE_PATH_GATHERER_TASKS_DONE_VARBIT, progress.gathererTasksDone)
    setAdventurePathVarbit(ADVENTURE_PATH_COMBAT_FOCUSED_TASK_VARBIT, progress.combatFocusedTask)
    setAdventurePathVarbit(ADVENTURE_PATH_GATHERER_FOCUSED_TASK_VARBIT, progress.gathererFocusedTask)
    if (adventurePathVarbit(ADVENTURE_PATH_FOCUSED_PATH_VARBIT) !in 0..4) {
        setAdventurePathVarbit(ADVENTURE_PATH_FOCUSED_PATH_VARBIT, 0)
    }

    ADVENTURE_PATH_VARPS.forEach(::resyncVar)
}

private const val ADVENTURE_PATH_ACTIVE = 1

private const val ADVENTURE_PATH_PARTICIPATION_VARBIT = 9340
private const val ADVENTURE_PATH_COMPLETED_TASKS_VARP = 2364
private const val ADVENTURE_PATH_COMPLETED_PATHS_VARP = 2361
private const val ADVENTURE_PATH_COMBAT_TASKS_DONE_VARBIT = 9341
private const val ADVENTURE_PATH_GATHERER_TASKS_DONE_VARBIT = 10163
private const val ADVENTURE_PATH_COMBAT_FOCUSED_TASK_VARBIT = 9325
private const val ADVENTURE_PATH_GATHERER_FOCUSED_TASK_VARBIT = 10158
private const val ADVENTURE_PATH_FOCUSED_PATH_VARBIT = 9324

private val ADVENTURE_PATH_VARPS: List<VarpServerType> by lazy {
    listOf(
            adventurePathVarpType(ADVENTURE_PATH_COMPLETED_TASKS_VARP),
            adventurePathVarpType(ADVENTURE_PATH_COMPLETED_PATHS_VARP),
            adventurePathVarbitType(ADVENTURE_PATH_PARTICIPATION_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_COMBAT_TASKS_DONE_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_GATHERER_TASKS_DONE_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_COMBAT_FOCUSED_TASK_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_GATHERER_FOCUSED_TASK_VARBIT).baseVar,
            adventurePathVarbitType(ADVENTURE_PATH_FOCUSED_PATH_VARBIT).baseVar,
        )
        .distinctBy(VarpServerType::id)
}

private fun Player.setAdventurePathVarp(id: Int, value: Int) {
    VarPlayerIntMapSetter.set(this, adventurePathVarpType(id), value)
}

private fun Player.setAdventurePathVarbit(id: Int, value: Int) {
    VarPlayerIntMapSetter.set(this, adventurePathVarbitType(id), value)
}

private fun Player.adventurePathVarbit(id: Int): Int = vars[adventurePathVarbitType(id)]

private fun adventurePathVarpType(id: Int): VarpServerType =
    requireNotNull(ServerCacheManager.getVarp(id)) { "Missing Adventure Paths varp $id" }

private fun adventurePathVarbitType(id: Int): VarBitType =
    requireNotNull(ServerCacheManager.getVarbit(id)) { "Missing Adventure Paths varbit $id" }
