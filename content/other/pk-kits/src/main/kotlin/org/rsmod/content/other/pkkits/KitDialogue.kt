package org.rsmod.content.other.pkkits

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess

@Singleton
public class KitDialogue @Inject constructor(private val service: KitService) {
    public suspend fun open(access: ProtectedAccess) {
        access.startDialogue { mainMenu() }
    }

    public suspend fun confirmDelete(access: ProtectedAccess, slot: Int) {
        access.startDialogue { confirmDeleteSlot(slot) }
    }

    private suspend fun Dialogue.confirmDeleteSlot(slot: Int) {
        val summary = service.slotSummary(player)
        if (slot !in 0 until summary.capacity) {
            mesbox(
                "Custom slot ${slot + 1} is locked; you currently have ${summary.capacity} slots."
            )
            return
        }
        val custom = summary.kits.firstOrNull { it.slot == slot }
        if (custom == null) {
            mesbox("There is no custom kit in slot ${slot + 1}.")
            return
        }
        val confirmed =
            choice2("Delete ${custom.name}", true, "Keep it", false, title = "Delete custom kit?")
        if (confirmed) showMutation(service.deleteCustom(player, slot), "Deleted ${custom.name}.")
    }

    private suspend fun Dialogue.mainMenu() {
        when (
            choice5(
                "Premade PK kits",
                MainChoice.Premade,
                "Custom PK kits",
                MainChoice.Custom,
                "Load my default",
                MainChoice.Default,
                "How kits work",
                MainChoice.Help,
                "Exit",
                MainChoice.Exit,
                title = "PK Kit Manager",
            )
        ) {
            MainChoice.Premade -> premadePage(0)
            MainChoice.Custom -> customPage(0)
            MainChoice.Default -> loadDefault()
            MainChoice.Help -> help()
            MainChoice.Exit -> Unit
        }
    }

    private suspend fun Dialogue.premadePage(page: Int) {
        val kits = service.starterKits()
        if (kits.isEmpty()) {
            mesbox("No validated premade kits are currently available.")
            return mainMenu()
        }
        val pages = kits.chunked(PAGE_SIZE)
        val selectedPage = page.coerceIn(pages.indices)
        val entries = pages[selectedPage]
        val labels = entries.map { "${it.name} (${it.category.displayName})" }.toMutableList()
        while (labels.size < PAGE_SIZE) labels += "-"
        val result =
            choice5(
                labels[0],
                0,
                labels[1],
                1,
                labels[2],
                2,
                labels[3],
                3,
                if (selectedPage == pages.lastIndex) "Back" else "Next page",
                4,
                title = "Premade kits ${selectedPage + 1}/${pages.size}",
            )
        if (result == 4) {
            if (selectedPage == pages.lastIndex) mainMenu() else premadePage(selectedPage + 1)
            return
        }
        entries.getOrNull(result)?.let { kitDetails(it) } ?: premadePage(selectedPage)
    }

    private suspend fun Dialogue.kitDetails(kit: KitDefinition) {
        val preview = service.preview(kit)
        val readiness = service.readiness(access, kit)
        mesbox(
            "<col=ff981f>${kit.name}</col><br>${kit.description}<br>" +
                "Category: ${kit.category.displayName}<br>Policy: ${kit.loadPolicy}<br>" +
                "Estimated risk: ${formatCoins(kit.riskValue)}<br>" +
                "Free items: ${preview.freeItems.size}; owned items: ${preview.ownedItems.size}."
        )
        mesbox(
            "Stats: ${kit.stats.attack}/${kit.stats.strength}/${kit.stats.defence}/${kit.stats.hitpoints}/" +
                "${kit.stats.ranged}/${kit.stats.magic}/${kit.stats.prayer}<br>" +
                "Spellbook: ${kit.settings.spellbook}; prayer book: ${kit.settings.prayerBook}<br>" +
                if (readiness.isEmpty()) "<col=00aa00>Ready to load.</col>"
                else "<col=ff0000>${readiness.joinToString("<br>")}</col>"
        )
        when (
            choice4(
                "Load kit",
                DetailChoice.Load,
                "Set as default",
                DetailChoice.Default,
                "Preview item requirements",
                DetailChoice.Items,
                "Back",
                DetailChoice.Back,
                title = kit.name,
            )
        ) {
            DetailChoice.Load -> {
                val confirmed =
                    choice2(
                        "Load this kit",
                        true,
                        "Cancel",
                        false,
                        title = "Bank current setup and load?",
                    )
                if (confirmed) showLoadResult(access, service.load(access, kit))
                mainMenu()
            }
            DetailChoice.Default -> {
                showMutation(
                    service.setDefault(player, kit),
                    "${kit.name} is now your default kit.",
                )
                mainMenu()
            }
            DetailChoice.Items -> {
                showItems(preview)
                kitDetails(kit)
            }
            DetailChoice.Back -> mainMenu()
        }
    }

    private suspend fun Dialogue.showItems(preview: KitPreview) {
        val lines = buildList {
            add("<col=00aa00>Freely supplied</col>")
            addAll(preview.freeItems.map { "${it.count} x ${displayName(it)}" })
            add("<col=ff981f>Ownership required</col>")
            addAll(preview.ownedItems.map { "${it.count} x ${displayName(it)}" })
            if (preview.forbiddenItems.isNotEmpty()) {
                add("<col=ff0000>Forbidden</col>")
                addAll(preview.forbiddenItems.map { displayName(it) })
            }
        }
        for (page in lines.chunked(8)) mesbox(page.joinToString("<br>"))
    }

    private suspend fun Dialogue.customPage(page: Int) {
        val summary = service.slotSummary(player)
        val slots = (0 until summary.capacity).toList()
        val pages = slots.chunked(PAGE_SIZE)
        val selectedPage = page.coerceIn(pages.indices)
        val entries = pages[selectedPage]
        val labels =
            entries
                .map { slot ->
                    val kit = summary.kits.firstOrNull { it.slot == slot }
                    "Slot ${slot + 1}: ${kit?.name ?: "Empty"}"
                }
                .toMutableList()
        while (labels.size < PAGE_SIZE) labels += "-"
        val result =
            choice5(
                labels[0],
                0,
                labels[1],
                1,
                labels[2],
                2,
                labels[3],
                3,
                if (selectedPage == pages.lastIndex) "Back" else "Next page",
                4,
                title = "Custom kits (${summary.capacity} slots)",
            )
        if (result == 4) {
            if (selectedPage == pages.lastIndex) mainMenu() else customPage(selectedPage + 1)
            return
        }
        val slot = entries.getOrNull(result) ?: return customPage(selectedPage)
        customSlot(slot, summary.kits.firstOrNull { it.slot == slot })
    }

    private suspend fun Dialogue.customSlot(slot: Int, custom: CustomKit?) {
        if (custom == null) {
            val save =
                choice2(
                    "Save current setup",
                    true,
                    "Back",
                    false,
                    title = "Custom slot ${slot + 1}",
                )
            if (save) {
                val name = access.stringDialog("Name this PK kit")
                showMutation(
                    service.saveCurrent(player, slot, name),
                    "Saved '$name' in slot ${slot + 1}.",
                )
            }
            return customPage(slot / PAGE_SIZE)
        }
        val definition = service.resolve(player, custom.id)
        if (definition == null) {
            mesbox("This custom kit is invalid or currently locked.")
            return customPage(slot / PAGE_SIZE)
        }
        val preview = service.preview(definition)
        val readiness = service.readiness(access, definition)
        mesbox(
            "<col=ff981f>${custom.name}</col><br>Estimated risk: ${formatCoins(definition.riskValue)}<br>" +
                "Free items: ${preview.freeItems.size}; owned items: ${preview.ownedItems.size}.<br>" +
                if (readiness.isEmpty()) "<col=00aa00>Ready to load.</col>"
                else "<col=ff0000>${readiness.first()}</col>"
        )
        when (
            choice5(
                "Load",
                CustomChoice.Load,
                "Overwrite with current setup",
                CustomChoice.Overwrite,
                "Rename",
                CustomChoice.Rename,
                "Delete",
                CustomChoice.Delete,
                "Back",
                CustomChoice.Back,
                title = custom.name,
            )
        ) {
            CustomChoice.Load -> {
                showLoadResult(access, service.load(access, definition))
            }
            CustomChoice.Overwrite -> {
                val confirmed =
                    choice2("Overwrite", true, "Cancel", false, title = "Replace ${custom.name}?")
                if (confirmed)
                    showMutation(
                        service.saveCurrent(player, slot, custom.name),
                        "Updated ${custom.name}.",
                    )
            }
            CustomChoice.Rename -> {
                val name = access.stringDialog("Rename ${custom.name}")
                showMutation(
                    service.renameCustom(player, slot, name),
                    "Renamed the kit to '$name'.",
                )
            }
            CustomChoice.Delete -> {
                val confirmed =
                    choice2(
                        "Delete permanently",
                        true,
                        "Cancel",
                        false,
                        title = "Delete ${custom.name}?",
                    )
                if (confirmed)
                    showMutation(service.deleteCustom(player, slot), "Deleted ${custom.name}.")
            }
            CustomChoice.Back -> Unit
        }
        customPage(slot / PAGE_SIZE)
    }

    private suspend fun Dialogue.loadDefault() {
        val kit = service.resolve(player, null)
        if (kit == null) {
            mesbox(
                "You do not have a valid default kit. Select a premade kit and choose Set as default."
            )
        } else {
            showLoadResult(access, service.load(access, kit))
        }
        mainMenu()
    }

    private suspend fun Dialogue.help() {
        mesbox(
            "Loading a kit first validates every item and bank slot. Your current setup is banked, " +
                "owned items are withdrawn, and approved basic supplies may be supplied freely. " +
                "If any step fails, nothing is changed."
        )
        mesbox(
            "Custom kits preserve exact inventory/equipment slots and item variant data. " +
                "You start with 5 slots; higher configured slot tiers unlock 7, 10, 15, or 20. " +
                "Downgrades lock extra slots without deleting them."
        )
        mainMenu()
    }

    private suspend fun Dialogue.showMutation(result: KitMutationResult, success: String) {
        mesbox(
            if (result is KitMutationResult.Success) success
            else (result as KitMutationResult.Failure).reason
        )
    }

    private suspend fun Dialogue.showLoadResult(access: ProtectedAccess, result: KitLoadResult) {
        val text =
            when (result) {
                is KitLoadResult.Success ->
                    "Loaded <col=00aa00>${result.kit.name}</col>. Your previous setup was banked."
                is KitLoadResult.Blocked -> result.reasons.joinToString("<br>")
                is KitLoadResult.Failed ->
                    "The kit was not loaded:<br>${result.reasons.joinToString("<br>")}"
            }
        access.mesbox(text)
    }

    private fun displayName(item: KitItem): String =
        item.internalName.removePrefix("obj.").replace('_', ' ')

    private fun formatCoins(value: Long): String = "%,d gp".format(value)

    private enum class MainChoice {
        Premade,
        Custom,
        Default,
        Help,
        Exit,
    }

    private enum class DetailChoice {
        Load,
        Default,
        Items,
        Back,
    }

    private enum class CustomChoice {
        Load,
        Overwrite,
        Rename,
        Delete,
        Back,
    }

    private companion object {
        const val PAGE_SIZE = 4
    }
}
