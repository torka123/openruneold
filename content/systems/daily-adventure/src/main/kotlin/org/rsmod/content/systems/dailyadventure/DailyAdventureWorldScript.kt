package org.rsmod.content.systems.dailyadventure

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.dailyadventure.DailyAdventurePath
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class DailyAdventureWorldScript
@Inject
constructor(
    private val service: DailyAdventureService,
    private val ui: DailyAdventureUi,
    private val shop: DailyAdventureShop,
) : PluginScript() {
    override fun ScriptContext.startup() {
        shop.startup()
        onPlayerLogin {
            service.onLogin(player)
            ui.syncNativeState(player)
        }
        onPlayerLogout { service.onLogout(player) }
        onEvent<GameLifecycle.LateCycle> { service.tick() }

        onCommand("tasks") {
            desc = "Open Daily Adventure Paths"
            invalidArgs = "Use ::tasks"
            cheat { if (args.isEmpty()) ui.open(player) }
        }

        onIfModalButton(component("shop_button")) { ui.openShop(player) }
        onIfModalButton(component("combat_path_card")) {
            ui.chooseOrView(this, DailyAdventurePath.Combat)
        }
        onIfModalButton(component("combat_path_action")) {
            ui.chooseOrView(this, DailyAdventurePath.Combat)
        }
        onIfModalButton(component("skilling_path_card")) {
            ui.chooseOrView(this, DailyAdventurePath.Skilling)
        }
        onIfModalButton(component("skilling_path_action")) {
            ui.chooseOrView(this, DailyAdventurePath.Skilling)
        }
        repeat(TASK_COUNT) { slot ->
            onIfModalButton(component("task_reroll_$slot")) { ui.reroll(this, slot) }
        }
        onIfModalButton(component("claim_button")) { ui.claim(player) }

        staff("dailydebug", "Show account-wide Daily Adventure state") {
            if (args.isEmpty()) player.mes(service.status(player))
        }
        staff("dailycomplete", "Complete a task: ::dailycomplete combat|skilling 1-10") {
            val path = args.getOrNull(0)?.toDailyPath()
            val slot = args.getOrNull(1)?.toIntOrNull()?.minus(1)
            if (path == null || slot !in 0 until TASK_COUNT) {
                player.mes("Use ::dailycomplete combat|skilling 1-10.")
                return@staff
            }
            if (service.forceComplete(player, path, slot!!)) {
                player.mes("Completed ${path.name} task ${slot + 1} for testing.")
            } else {
                player.mes("That path/task is not active.")
            }
        }
        staff("dailypoints", "Add Task Points: ::dailypoints amount") {
            val amount = args.singleOrNull()?.toLongOrNull()
            if (amount == null || !service.addPoints(player, amount)) {
                player.mes("Use ::dailypoints with a positive amount that will not overflow.")
            } else {
                player.mes("Added $amount Task Points; balance=${service.points(player)}.")
            }
        }
    }

    private fun ScriptContext.staff(
        command: String,
        description: String,
        action: Cheat.() -> Unit,
    ) {
        onCommand(command) {
            requiredRights = Rights.ADMINISTRATOR
            desc = description
            cheat(action)
        }
    }

    private fun String.toDailyPath(): DailyAdventurePath? =
        when (lowercase()) {
            "combat", "c" -> DailyAdventurePath.Combat
            "skilling", "skill", "s" -> DailyAdventurePath.Skilling
            else -> null
        }

    private companion object {
        const val TASK_COUNT = 10
        fun component(name: String): String = "component.daily_adventure:$name"
    }
}
