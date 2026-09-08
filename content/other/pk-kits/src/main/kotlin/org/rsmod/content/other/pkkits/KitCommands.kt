package org.rsmod.content.other.pkkits

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class KitCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val dialogue: KitDialogue,
    private val kitInterface: KitInterface,
    private val service: KitService,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("kits") {
            desc = "Open the PK kit manager"
            invalidArgs = "Use as ::kits"
            cheat {
                if (args.isNotEmpty()) return@cheat player.mes("Use as ::kits")
                protectedAccess.launch(player, BusyMessage) { kitInterface.open(this) }
            }
        }
        onCommand("loadoutui") {
            desc = "Open the PK loadout interface"
            invalidArgs = "Use as ::loadoutui"
            cheat {
                if (args.isNotEmpty()) return@cheat player.mes("Use as ::loadoutui")
                protectedAccess.launch(player, BusyMessage) { kitInterface.open(this) }
            }
        }
        onCommand("loadkit") {
            desc = "Load a premade, custom, or default PK kit"
            invalidArgs = "Use as ::loadkit [kit id, name, or custom slot]"
            cheat { loadKit(this) }
        }
        onCommand("savekit") {
            desc = "Save the current setup to a custom PK kit slot"
            invalidArgs = "Use as ::savekit <slot> [name]"
            cheat { saveKit(this) }
        }
        onCommand("deletekit") {
            desc = "Delete a custom PK kit slot"
            invalidArgs = "Use as ::deletekit <slot>"
            cheat { deleteKit(this) }
        }
        onCommand("reloadkits") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Reload and validate all premade PK kits"
            invalidArgs = "Use as ::reloadkits"
            cheat { reloadKits(this) }
        }
        onCommand("validatekits") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show PK kit registry validation status"
            invalidArgs = "Use as ::validatekits"
            cheat { validateKits(this) }
        }
        onCommand("kitinfo") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show policy and contents for a PK kit"
            invalidArgs = "Use as ::kitinfo <kit id or custom slot>"
            cheat { kitInfo(this) }
        }
        onCommand("forcekit") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Force-load a validated PK kit for an online player"
            invalidArgs = "Use as ::forcekit <player name> <kit id>"
            cheat { forceKit(this) }
        }
        onCommand("kitslottier") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Set a player's custom-kit slot tier (0-4)"
            invalidArgs = "Use as ::kitslottier <0-4> [player name]"
            cheat { kitSlotTier(this) }
        }
    }

    private fun loadKit(cheat: Cheat) =
        with(cheat) {
            val query = args.joinToString(" ").ifBlank { null }
            val definition = service.resolve(player, query)
            if (definition == null) {
                player.mes(
                    if (query == null) "You do not have a valid default kit."
                    else "No valid kit matched '$query'."
                )
                return@with
            }
            protectedAccess.launch(player, BusyMessage) {
                sendLoadResult(player, service.load(this, definition))
            }
        }

    private fun saveKit(cheat: Cheat) =
        with(cheat) {
            val slot = args.firstOrNull()?.toIntOrNull()?.minus(1)
            if (slot == null) {
                player.mes("Use as ::savekit <slot> [name]")
                return@with
            }
            val name = args.drop(1).joinToString(" ").ifBlank { null }
            val result = service.saveCurrent(player, slot, name)
            sendMutation(player, result, "Saved your current setup to custom slot ${slot + 1}.")
        }

    private fun deleteKit(cheat: Cheat) =
        with(cheat) {
            val slot = args.singleOrNull()?.toIntOrNull()?.minus(1)
            if (slot == null) {
                player.mes(
                    "Use as ::deletekit <slot>. The dialogue manager provides a confirmation screen."
                )
                return@with
            }
            protectedAccess.launch(player, BusyMessage) { dialogue.confirmDelete(this, slot) }
        }

    private fun reloadKits(cheat: Cheat) =
        with(cheat) {
            if (args.isNotEmpty()) return@with player.mes("Use as ::reloadkits")
            val report = service.reload()
            player.mes("PK kits reloaded: ${report.validCount}/${report.loaded} valid.")
            report.invalid.forEach { (id, errors) -> player.mes("$id: ${errors.joinToString()}") }
        }

    private fun validateKits(cheat: Cheat) =
        with(cheat) {
            if (args.isNotEmpty()) return@with player.mes("Use as ::validatekits")
            val report = service.registryReport()
            player.mes(
                "PK kit registry: ${report.loaded} loaded, ${report.validCount} valid, ${report.invalid.size} invalid."
            )
            report.invalid.forEach { (id, errors) -> player.mes("$id: ${errors.joinToString()}") }
        }

    private fun kitInfo(cheat: Cheat) =
        with(cheat) {
            val query = args.joinToString(" ")
            val kit = service.resolve(player, query)
            if (kit == null) {
                player.mes("No valid kit matched '$query'.")
                return@with
            }
            val preview = service.preview(kit)
            player.mes(
                "${kit.id}: ${kit.name} [${kit.category.displayName}] policy=${kit.loadPolicy} risk=${kit.riskValue} gp"
            )
            player.mes(
                "Stats: ${kit.stats}; spellbook=${kit.settings.spellbook}; quick-prayer mask=${kit.settings.quickPrayers}"
            )
            player.mes(
                "Inventory=${kit.inventory.size}; equipment=${kit.equipment.size}; free=${preview.freeItems.size}; owned=${preview.ownedItems.size}; forbidden=${preview.forbiddenItems.size}"
            )
        }

    private fun forceKit(cheat: Cheat) =
        with(cheat) {
            val administrator = player
            if (args.size < 2) {
                player.mes("Use as ::forcekit <player name> <kit id>")
                return@with
            }
            val id = args.last()
            val target = findTarget(args.dropLast(1).joinToString(" "), player)
            if (target == null) {
                player.mes("That player is not online.")
                return@with
            }
            val kit = service.resolve(target, id)
            if (kit == null) {
                player.mes("No valid kit matched '$id' for ${target.displayName}.")
                return@with
            }
            protectedAccess.launch(target, BusyMessage) {
                val result = service.load(this, kit, bypassRestrictions = true)
                sendLoadResult(target, result)
                administrator.mes(
                    "Force-kit result for ${target.displayName}: ${result::class.simpleName}."
                )
            }
        }

    private fun kitSlotTier(cheat: Cheat) =
        with(cheat) {
            val tier = args.firstOrNull()?.toIntOrNull()
            if (tier == null) {
                player.mes("Use as ::kitslottier <0-4> [player name]")
                return@with
            }
            val target = findTarget(args.drop(1).joinToString(" "), player)
            if (target == null) {
                player.mes("That player is not online.")
                return@with
            }
            val result = service.setSlotTier(target, tier)
            val capacity =
                if (tier in KitSlotManager.validTiers) KitSlotManager.capacity(tier) else 0
            sendMutation(
                player,
                result,
                "${target.displayName} now has kit tier $tier ($capacity slots). Locked slots were preserved.",
            )
            if (target !== player && result is KitMutationResult.Success) {
                target.mes("Your custom PK kit capacity is now $capacity slots.")
            }
        }

    private fun findTarget(name: String, fallback: Player): Player? {
        if (name.isBlank()) return fallback
        return players.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }

    private fun sendMutation(player: Player, result: KitMutationResult, success: String) {
        player.mes(
            if (result is KitMutationResult.Success) success
            else (result as KitMutationResult.Failure).reason
        )
    }

    private fun sendLoadResult(player: Player, result: KitLoadResult) {
        when (result) {
            is KitLoadResult.Success ->
                player.mes("Loaded ${result.kit.name}; your previous setup was banked.")
            is KitLoadResult.Blocked -> result.reasons.forEach(player::mes)
            is KitLoadResult.Failed -> {
                player.mes("The kit was not loaded; no items were changed.")
                result.reasons.forEach(player::mes)
            }
        }
    }

    private companion object {
        const val BusyMessage = "Finish your current action before using the PK kit manager."
    }
}
