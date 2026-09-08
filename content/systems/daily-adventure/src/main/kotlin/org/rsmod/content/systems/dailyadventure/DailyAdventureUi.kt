package org.rsmod.content.systems.dailyadventure

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.dailyadventure.DailyAdventureCatalogue
import org.rsmod.api.dailyadventure.DailyAdventureClaimStatus
import org.rsmod.api.dailyadventure.DailyAdventureDifficulty
import org.rsmod.api.dailyadventure.DailyAdventurePath
import org.rsmod.api.dailyadventure.DailyAdventureRewards
import org.rsmod.api.dailyadventure.DailyAdventureRerollStatus
import org.rsmod.api.dailyadventure.DailyAdventureSelectionStatus
import org.rsmod.api.dailyadventure.DailyAdventureState
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

private val DAILY_ADVENTURE_VIEW_PATH = AttributeKey<DailyAdventurePath>()
private val DAILY_ADVENTURE_VIEW_SLOT = AttributeKey<Int>()

@Singleton
public class DailyAdventureUi
@Inject
constructor(
    private val service: DailyAdventureService,
    private val shop: DailyAdventureShop,
    private val eventBus: EventBus,
) {
    /** Opens the unmodified revision-240 Adventure Paths modal as the Daily front end. */
    public fun open(player: Player, status: String? = null) {
        service.deliverPendingMemberBoxes(player)
        val state = service.state(player)
        val selected = normalizeViewPath(player, state)
        val selectedSlot = normalizeViewSlot(player, state, selected)
        player.syncDailyAdventureNative(state, selected)
        player.ifOpenMainModal(NATIVE_INTERFACE, eventBus)
        configureNativeEvents(player)
        renderNativeDailyContent(player, state, selected, selectedSlot)
        if (status != null) player.mes(status)
    }

    /** Keeps the side journal and modal varps on the authoritative Daily Adventure state. */
    public fun syncNativeState(player: Player) {
        val state = service.state(player)
        val selected = normalizeViewPath(player, state)
        player.syncDailyAdventureNative(state, selected)
    }

    /** Handles the existing path-card focus control without choosing a free path. */
    public fun focusNativePath(player: Player, row: Int) {
        val path = DailyAdventureNativeProjection.pathForRow(row)
        if (path == null) {
            player.mes("That legacy path is not part of Daily Adventure Paths.")
            return
        }
        val state = service.state(player)
        if (path !in state.activePaths) {
            player.mes("Open that path to choose it for today first.")
            return
        }
        player.attr[DAILY_ADVENTURE_VIEW_PATH] = path
        player.attr.remove(DAILY_ADVENTURE_VIEW_SLOT)
        player.syncDailyAdventureNative(state, path)
        renderNativeDailyContent(player, state, path, normalizeViewSlot(player, state, path))
        player.mes("${path.displayName} is now the path shown by your journal.")
    }

    /** Uses the existing path detail control for Daily Combat and Daily Skilling only. */
    public suspend fun chooseOrViewNative(access: ProtectedAccess, row: Int): Boolean {
        val path = DailyAdventureNativeProjection.pathForRow(row)
        if (path == null) {
            access.player.mes("That legacy path is not part of Daily Adventure Paths.")
            return false
        }
        chooseOrView(access, path)
        return true
    }

    /** Maps a native task row to the selected Daily path and refreshes the real details panel. */
    public fun viewNativeTask(player: Player, comsub: Int) {
        val row = DailyAdventureNativeProjection.taskRow(comsub) ?: return
        if (row !in 0 until DailyAdventureNativeProjection.DAILY_TASK_ROWS) {
            player.mes("That extra legacy Combat row is not used by today's Daily path.")
            return
        }
        selectNativeTask(player, row)
    }

    public fun selectNativeTask(player: Player, slot: Int) {
        if (slot !in 0 until NativeDailyAdventureComponents.DailyRows) return
        val state = service.state(player)
        val path = normalizeViewPath(player, state)
        val assignment =
            path?.let { state.assignments[it].orEmpty().firstOrNull { task -> task.slot == slot } }
        if (assignment == null) {
            player.mes("That task is no longer available.")
            renderNativeDailyContent(player, state, path, normalizeViewSlot(player, state, path))
            return
        }
        player.attr[DAILY_ADVENTURE_VIEW_SLOT] = slot
        renderNativeDailyContent(player, state, path, slot)
    }

    /** Reuses the existing hint button to show the authoritative Daily task description in chat. */
    public fun showNativeTaskHint(player: Player) {
        val slot = player.attr[DAILY_ADVENTURE_VIEW_SLOT]
        if (slot == null) {
            player.mes("Select one of today's mapped task rows first.")
            return
        }
        describeNativeTask(player, slot)
    }

    /** Keeps the stock Show path reward button and reports the authoritative Daily path bonus. */
    public fun showNativePathReward(player: Player) {
        val state = service.state(player)
        val path = normalizeViewPath(player, state)
        val rewardStreak =
            if (state.streakAdvancedDay == state.day) state.streak.coerceAtLeast(1)
            else (state.streak + 1).coerceAtLeast(1)
        val bonus = DailyAdventureRewards.streakBonus(rewardStreak)
        player.mes(
            "Daily streak: ${state.streak}; best: ${state.bestStreak}. " +
                "The current path reward is +$bonus Task Points."
        )
        val message =
            when {
                path == null || path !in state.activePaths ->
                    "Choose a Daily path to work toward today's path reward."
                path in state.claimedPaths -> "You already claimed today's ${path.displayName} bonus."
                state.assignments[path].orEmpty().count { it.complete } >= REQUIRED_FOR_CLAIM ->
                    "Your ${path.displayName} path reward is ready to claim."
                else ->
                    "Complete any $REQUIRED_FOR_CLAIM ${path.displayName} tasks to unlock the path reward " +
                        "(${state.assignments[path].orEmpty().count { it.complete }}/$REQUIRED_FOR_CLAIM)."
            }
        player.mes(message)
    }

    public fun refreshNativeChrome(player: Player) {
        val state = service.state(player)
        val path = normalizeViewPath(player, state)
        renderNativeDailyContent(player, state, path, normalizeViewSlot(player, state, path))
    }

    public suspend fun chooseOrView(access: ProtectedAccess, path: DailyAdventurePath) {
        val player = access.player
        val state = service.state(player)
        if (path in state.activePaths) {
            player.attr[DAILY_ADVENTURE_VIEW_PATH] = path
            player.attr.remove(DAILY_ADVENTURE_VIEW_SLOT)
            open(player)
            return
        }
        if (state.selectedFreePath != null) {
            open(player, "You already chose ${state.selectedFreePath!!.displayName} for today.")
            return
        }
        val confirmed =
            access.choice2(
                "Choose ${path.displayName}",
                true,
                "Cancel",
                false,
                title = "Free accounts choose one path for this UTC day.",
            )
        if (!confirmed) {
            open(player)
            return
        }
        val result = service.selectPath(player, path)
        if (result == DailyAdventureSelectionStatus.Selected) {
            player.attr[DAILY_ADVENTURE_VIEW_PATH] = path
            player.attr.remove(DAILY_ADVENTURE_VIEW_SLOT)
        }
        open(player, selectionMessage(result, path))
    }

    public suspend fun reroll(access: ProtectedAccess, slot: Int) {
        val player = access.player
        val path = player.attr[DAILY_ADVENTURE_VIEW_PATH]
        if (path == null) {
            open(player, "Choose a path first.")
            return
        }
        val assignment = service.state(player).assignments[path]?.firstOrNull { it.slot == slot }
        if (assignment == null) {
            open(player, "That task is no longer available.")
            return
        }
        if (assignment.progress > 0L && !assignment.complete) {
            val confirmed =
                access.choice2(
                    "Reroll and reset progress",
                    true,
                    "Keep this task",
                    false,
                    title =
                        "This removes ${assignment.progress}/${assignment.goal} progress and uses one shared reroll.",
                )
            if (!confirmed) {
                open(player)
                return
            }
        }
        val result =
            service.reroll(
                player,
                path,
                slot,
                service.nextEventId(player, "reroll-${path.name.lowercase()}-$slot"),
            )
        open(player, rerollMessage(result.status))
    }

    /** Rerolls only the currently selected incomplete native task and refreshes in place. */
    public suspend fun rerollSelected(access: ProtectedAccess) {
        val player = access.player
        var state = service.state(player)
        val path = normalizeViewPath(player, state)
        val slot = player.attr[DAILY_ADVENTURE_VIEW_SLOT]
        if (path == null || slot == null) {
            player.mes("Select an incomplete Daily task before using a reroll.")
            renderNativeDailyContent(player, state, path, normalizeViewSlot(player, state, path))
            return
        }
        val assignment = state.assignments[path].orEmpty().firstOrNull { it.slot == slot }
        if (assignment == null) {
            player.mes("That task is no longer available.")
            renderNativeDailyContent(player, state, path, normalizeViewSlot(player, state, path))
            return
        }
        if (assignment.complete) {
            player.mes("Completed tasks cannot be rerolled.")
            renderNativeDailyContent(player, state, path, slot)
            return
        }
        if (state.rerollsRemaining() <= 0) {
            player.mes("You have no shared rerolls remaining today.")
            renderNativeDailyContent(player, state, path, slot)
            return
        }
        if (assignment.progress > 0L) {
            val confirmed =
                access.choice2(
                    "Reroll and reset progress",
                    true,
                    "Keep this task",
                    false,
                    title =
                        "This removes ${assignment.progress}/${assignment.goal} progress and uses one shared reroll.",
                )
            if (!confirmed) {
                renderNativeDailyContent(player, state, path, slot)
                return
            }
        }
        val result =
            service.reroll(
                player,
                path,
                slot,
                service.nextEventId(player, "reroll-${path.name.lowercase()}-$slot"),
            )
        state = service.state(player)
        player.mes(rerollMessage(result.status))
        renderNativeDailyContent(player, state, path, slot)
    }

    public fun claim(player: Player) {
        val path = player.attr[DAILY_ADVENTURE_VIEW_PATH]
        if (path == null) {
            open(player, "Choose a path first.")
            return
        }
        val result = service.claim(player, path)
        val message =
            when (result.status) {
                DailyAdventureClaimStatus.Claimed ->
                    "Claimed +${result.bonusPoints} Task Points. Your streak is ${result.streak}."
                DailyAdventureClaimStatus.AlreadyClaimed -> "You already claimed this path today."
                DailyAdventureClaimStatus.NotEnoughTasks -> "Complete any 8 tasks on this path first."
                DailyAdventureClaimStatus.InvalidPath -> "That path is not active today."
            }
        open(player, message)
    }

    public fun openShop(player: Player) {
        shop.open(player)
    }

    private fun configureNativeEvents(player: Player) {
        repeat(DailyAdventureNativeProjection.DAILY_TASK_ROWS) { row ->
            val sub =
                DailyAdventureNativeProjection.TASK_FIRST +
                    (row * DailyAdventureNativeProjection.TASK_STRIDE)
            player.ifSetEvents("component.adventurepath:tasks", sub..sub, IfEvent.Op1)
        }
        repeat(DailyAdventureNativeProjection.PATH_ROWS) { row ->
            val base = row * DailyAdventureNativeProjection.PATH_STRIDE
            player.ifSetEvents("component.adventurepath:paths", base..base, IfEvent.Op1)
            val select = base + DailyAdventureNativeProjection.PATH_SELECT_OFFSET
            player.ifSetEvents("component.adventurepath:paths", select..select, IfEvent.Op1)
            val show = base + DailyAdventureNativeProjection.PATH_SHOW_OFFSET
            player.ifSetEvents("component.adventurepath:paths", show..show, IfEvent.Op4)
        }
        player.ifSetEvents("component.adventurepath:task_focus", 9..10, IfEvent.Op1)
        player.ifSetEvents("component.adventurepath:back", 9..9, IfEvent.Op1)
        player.ifSetEvents("component.adventurepath:extra_back_button", 9..9, IfEvent.Op1)
        player.ifSetEvents("component.adventurepath:task_hint", 1..2, IfEvent.Op1)
        player.ifSetEvents("component.adventurepath:path_reward", 9..9, IfEvent.Op1)
    }

    private fun normalizeViewPath(player: Player, state: DailyAdventureState): DailyAdventurePath? {
        val selected = player.attr[DAILY_ADVENTURE_VIEW_PATH]
        if (selected in state.activePaths) return selected
        val defaultPath = state.selectedFreePath ?: state.activePaths.firstOrNull()
        if (defaultPath == null) {
            player.attr.remove(DAILY_ADVENTURE_VIEW_PATH)
            player.attr.remove(DAILY_ADVENTURE_VIEW_SLOT)
        } else {
            player.attr[DAILY_ADVENTURE_VIEW_PATH] = defaultPath
        }
        return defaultPath
    }

    private fun normalizeViewSlot(
        player: Player,
        state: DailyAdventureState,
        path: DailyAdventurePath?,
    ): Int? {
        val assignments = path?.let { state.assignments[it].orEmpty() }.orEmpty()
        val selected = player.attr[DAILY_ADVENTURE_VIEW_SLOT]
        if (assignments.any { it.slot == selected }) return selected
        val defaultSlot =
            assignments.firstOrNull { !it.complete }?.slot ?: assignments.firstOrNull()?.slot
        if (defaultSlot == null) {
            player.attr.remove(DAILY_ADVENTURE_VIEW_SLOT)
        } else {
            player.attr[DAILY_ADVENTURE_VIEW_SLOT] = defaultSlot
        }
        return defaultSlot
    }

    private fun describeNativeTask(player: Player, slot: Int) {
        val state = service.state(player)
        val path = normalizeViewPath(player, state)
        if (path == null || path !in state.activePaths) {
            player.mes("Choose a Daily path before viewing its tasks.")
            return
        }
        val assignment = state.assignments[path].orEmpty().firstOrNull { it.slot == slot }
        if (assignment == null) {
            player.mes("That task row is not used by today's ${path.displayName}.")
            return
        }
        val definition = DailyAdventureCatalogue.byId[assignment.taskId]
        if (definition == null) {
            player.mes("That Daily task definition is unavailable.")
            return
        }
        val status = if (assignment.complete) "complete" else "${assignment.progress}/${assignment.goal}"
        player.mes("[${assignment.difficulty.shortName}] ${definition.name}: $status; +${assignment.reward} Task Points.")
        player.mes(definition.description)
    }

    private fun renderNativeDailyContent(
        player: Player,
        state: DailyAdventureState,
        path: DailyAdventurePath?,
        selectedSlot: Int?,
    ) {
        val completed = NativeDailyAdventureComponents.completedTasks(state, path)
        val paths = NativeDailyAdventureComponents.packed(NativeDailyAdventureComponents.StockPaths)
        val tasksDone = NativeDailyAdventureComponents.packed(NativeDailyAdventureComponents.StockTasksDone)
        val navigation = NativeDailyAdventureComponents.packed(NativeDailyAdventureComponents.StockNavigation)
        val pathReward = NativeDailyAdventureComponents.packed(NativeDailyAdventureComponents.StockPathReward)
        val taskFocus = NativeDailyAdventureComponents.packed(NativeDailyAdventureComponents.StockTaskFocus)

        player.runClientScript(
            DYNAMIC_TEXT_SWAPPER,
            paths,
            NativeDailyAdventureComponents.StockCombatPathLabelSub,
            "Combat Path",
        )
        player.runClientScript(
            DYNAMIC_TEXT_SWAPPER,
            paths,
            NativeDailyAdventureComponents.StockSkillingPathLabelSub,
            "Skilling Path",
        )
        player.runClientScript(
            DYNAMIC_TEXT_SWAPPER,
            tasksDone,
            NativeDailyAdventureComponents.StockTasksDoneTextSub,
            "Completed $completed/10 tasks",
        )
        player.runClientScript(
            DYNAMIC_GRAPHIC_SWAPPER,
            navigation,
            NativeDailyAdventureComponents.StockNavigationIconSub,
            BONDS_SPRITE,
            NO_CALLBACK,
        )
        player.runClientScript(
            CREATE_GRAPHIC,
            pathReward,
            NativeDailyAdventureComponents.StockPathRewardIconSub,
            MONEY_BAG_SPRITE,
            1,
            7,
            0,
            0,
            14,
            14,
            0,
            0,
            0,
        )
        player.runClientScript(
            DYNAMIC_GRAPHIC_SWAPPER,
            taskFocus,
            NativeDailyAdventureComponents.StockTaskFocusIconSub,
            REROLL_SPRITE,
            NO_CALLBACK,
        )
    }

    private fun selectionMessage(
        status: DailyAdventureSelectionStatus,
        path: DailyAdventurePath,
    ): String =
        when (status) {
            DailyAdventureSelectionStatus.Selected ->
                "${path.displayName} is active until the next UTC reset."
            DailyAdventureSelectionStatus.AlreadySelected ->
                "A free path has already been selected today."
            DailyAdventureSelectionStatus.MembersAlreadyActive ->
                "Members already have both paths active."
            DailyAdventureSelectionStatus.InvalidDay ->
                "The daily reset is updating; reopen your tasks."
        }

    private fun rerollMessage(status: DailyAdventureRerollStatus): String =
        when (status) {
            DailyAdventureRerollStatus.Rerolled -> "Task rerolled; its progress was reset."
            DailyAdventureRerollStatus.InvalidPath -> "That path is not active today."
            DailyAdventureRerollStatus.InvalidSlot -> "That task is no longer available."
            DailyAdventureRerollStatus.NoRerollsRemaining ->
                "You have no shared rerolls remaining today."
            DailyAdventureRerollStatus.ProgressAlreadyStarted ->
                "Confirm before resetting partial progress."
            DailyAdventureRerollStatus.CompletedTask -> "Completed tasks cannot be rerolled."
            DailyAdventureRerollStatus.NoEligibleReplacement ->
                "No same-difficulty replacement is available."
        }

    private companion object {
        const val NATIVE_INTERFACE = "interface.adventurepath"
        const val REQUIRED_FOR_CLAIM = 8
        const val CREATE_GRAPHIC = 98
        const val DYNAMIC_GRAPHIC_SWAPPER = 229
        const val DYNAMIC_TEXT_SWAPPER = 599
        const val NO_CALLBACK = -1
        const val MONEY_BAG_SPRITE = 1090
        const val BONDS_SPRITE = 5259
        const val REROLL_SPRITE = 7039

        val DailyAdventurePath.displayName: String
            get() = if (this == DailyAdventurePath.Combat) "Combat Path" else "Skilling Path"

        val DailyAdventureDifficulty.shortName: String
            get() =
                when (this) {
                    DailyAdventureDifficulty.Easy -> "EASY"
                    DailyAdventureDifficulty.Medium -> "MED"
                    DailyAdventureDifficulty.Hard -> "HARD"
                }

        val DailyAdventureDifficulty.colour: String
            get() =
                when (this) {
                    DailyAdventureDifficulty.Easy -> "66ff66"
                    DailyAdventureDifficulty.Medium -> "ffcc33"
                    DailyAdventureDifficulty.Hard -> "ff6666"
                }
    }
}
