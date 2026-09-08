package org.rsmod.content.other.pkkits

import dev.openrune.PkLoadoutInterfaceIds
import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.awt.Color
import kotlin.math.floor
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.ui.setColour
import org.rsmod.game.entity.Player

/** Server-authoritative controller for interface 969. It never accepts item or stat data. */
@Singleton
public class KitInterface @Inject constructor(private val service: KitService) {
    public fun open(access: ProtectedAccess) {
        val player = access.player
        val session = KitUiSession(initialSelection(player))
        player.attr[Session] = session
        access.ifOpenMainModal(Interface, colour = 0x2B241B, transparency = 0)
        configureEvents(access)
        access.runClientScript(30, componentPacked(PkLoadoutInterfaceIds.PremadeScrollbar), componentPacked(PkLoadoutInterfaceIds.PremadeScroll))
        access.runClientScript(30, componentPacked(PkLoadoutInterfaceIds.CustomScrollbar), componentPacked(PkLoadoutInterfaceIds.CustomScroll))
        refresh(access)
    }

    public fun openAfterDeath(access: ProtectedAccess) {
        if (!service.openAfterDeathEnabled(access.player)) return
        if (!service.canOpenAfterDeath(access.player)) return
        open(access)
    }

    public fun close(player: Player) {
        player.attr.remove(Session)
    }

    public fun selectPremade(access: ProtectedAccess, index: Int) {
        if (!hasSession(access.player)) return
        val kits = service.starterKits()
        if (index !in kits.indices || index !in 0 until PkLoadoutInterfaceIds.MaxPremadeRows) {
            access.player.mes("That premade loadout is no longer available.")
            return
        }
        access.player.attr[Session] = KitUiSession(KitUiSelection.Premade(index))
        refresh(access)
    }

    public fun selectCustom(access: ProtectedAccess, slot: Int) {
        if (!hasSession(access.player)) return
        val summary = service.slotSummary(access.player)
        if (slot !in 0 until PkLoadoutInterfaceIds.MaxCustomRows) return
        if (slot >= summary.capacity) {
            access.player.mes("Custom slot ${slot + 1} is locked. Upgrade your kit-slot tier to unlock it.")
            return
        }
        access.player.attr[Session] = KitUiSession(KitUiSelection.Custom(slot))
        refresh(access)
    }

    public fun toggleOpenAfterDeath(access: ProtectedAccess) {
        if (!hasSession(access.player)) return
        val enabled = !service.openAfterDeathEnabled(access.player)
        val result = service.setOpenAfterDeath(access.player, enabled)
        val message = if (result is KitMutationResult.Success) {
            "Loadouts will ${if (enabled) "now" else "no longer"} open after respawning."
        } else {
            (result as KitMutationResult.Failure).reason
        }
        refresh(access, message)
    }

    public fun loadSelected(access: ProtectedAccess) {
        if (!hasSession(access.player)) return
        val definition = selectedDefinition(access.player)
        if (definition == null) {
            refresh(access, "Save a setup in this custom slot before loading it.")
            return
        }
        val message = when (val result = service.load(access, definition)) {
            is KitLoadResult.Success -> "Loaded ${result.kit.name}; your previous setup was banked."
            is KitLoadResult.Blocked -> result.reasons.firstOrNull() ?: "That loadout cannot be loaded right now."
            is KitLoadResult.Failed -> result.reasons.firstOrNull() ?: "The loadout was not changed."
        }
        access.player.mes(message)
        refresh(access, message)
    }

    public suspend fun saveSelected(access: ProtectedAccess) {
        if (!hasSession(access.player)) return
        val slot = selectedCustomSlot(access.player)
        if (slot == null) {
            refresh(access, "Select a custom loadout slot before saving your setup.")
            return
        }
        val existing = service.slotSummary(access.player).kits.firstOrNull { it.slot == slot }
        if (existing == null) {
            val name = access.stringDialog("Name this PK loadout")
            val result = service.saveCurrent(access.player, slot, name)
            queueCustomReopen(
                access,
                slot,
                mutationMessage(result, "Saved '$name' in custom slot ${slot + 1}."),
            )
            return
        }
        access.startDialogue {
            val confirmed = choice2("Overwrite ${existing.name}", true, "Cancel", false, title = "Save current setup?")
            val message =
                if (confirmed) {
                    mutationMessage(
                        service.saveCurrent(access.player, slot, existing.name),
                        "Updated ${existing.name}.",
                    )
                } else {
                    "Overwrite cancelled."
                }
            queueCustomReopen(access, slot, message)
        }
    }

    public suspend fun renameSelected(access: ProtectedAccess) {
        if (!hasSession(access.player)) return
        val slot = selectedCustomSlot(access.player) ?: return refresh(access, "Select a saved custom loadout first.")
        val existing = service.slotSummary(access.player).kits.firstOrNull { it.slot == slot }
            ?: return refresh(access, "There is no saved loadout in that slot.")
        val name = access.stringDialog("Rename ${existing.name}")
        val result = service.renameCustom(access.player, slot, name)
        queueCustomReopen(
            access,
            slot,
            mutationMessage(result, "Renamed the loadout to '$name'."),
        )
    }

    public suspend fun deleteSelected(access: ProtectedAccess) {
        if (!hasSession(access.player)) return
        val slot = selectedCustomSlot(access.player) ?: return refresh(access, "Select a saved custom loadout first.")
        val existing = service.slotSummary(access.player).kits.firstOrNull { it.slot == slot }
            ?: return refresh(access, "There is no saved loadout in that slot.")
        access.startDialogue {
            val confirmed = choice2("Delete ${existing.name}", true, "Keep it", false, title = "Delete custom loadout?")
            val message =
                if (confirmed) {
                    mutationMessage(
                        service.deleteCustom(access.player, slot),
                        "Deleted ${existing.name}.",
                    )
                } else {
                    "Delete cancelled."
                }
            queueCustomReopen(access, slot, message)
        }
    }

    public fun setSelectedDefault(access: ProtectedAccess) {
        if (!hasSession(access.player)) return
        val definition = selectedDefinition(access.player)
        if (definition == null) return refresh(access, "Save this custom slot before setting it as your default.")
        val result = service.setDefault(access.player, definition)
        showMutation(access, result, "${definition.name} is now your default loadout.")
        refresh(access)
    }

    private fun configureEvents(access: ProtectedAccess) {
        repeat(PkLoadoutInterfaceIds.MaxPremadeRows) { index ->
            access.ifSetEvents(component(PkLoadoutInterfaceIds.premadeRow(index)), 0..0, IfEvent.Op1)
        }
        repeat(PkLoadoutInterfaceIds.MaxCustomRows) { slot ->
            access.ifSetEvents(component(PkLoadoutInterfaceIds.customRow(slot)), 0..0, IfEvent.Op1)
        }
        listOf(
            PkLoadoutInterfaceIds.DeathToggle,
            PkLoadoutInterfaceIds.LoadButton,
            PkLoadoutInterfaceIds.SaveButton,
            PkLoadoutInterfaceIds.RenameButton,
            PkLoadoutInterfaceIds.DeleteButton,
            PkLoadoutInterfaceIds.DefaultButton,
        ).forEach { access.ifSetEvents(component(it), 0..0, IfEvent.Op1) }
    }

    private fun refresh(access: ProtectedAccess, status: String? = null) {
        val player = access.player
        val session = player.attr[Session] ?: return
        val premade = service.starterKits()
        val summary = service.slotSummary(player)
        val customBySlot = summary.kits.associateBy(CustomKit::slot)

        repeat(PkLoadoutInterfaceIds.MaxPremadeRows) { index ->
            val visible = index < premade.size
            access.player.ifSetHide(component(PkLoadoutInterfaceIds.premadeRow(index)), !visible)
            access.player.ifSetHide(component(PkLoadoutInterfaceIds.premadeText(index)), !visible)
            if (visible) {
                val kit = premade[index]
                access.player.ifSetText(component(PkLoadoutInterfaceIds.premadeText(index)), kit.name.take(18))
                setRowColour(player, PkLoadoutInterfaceIds.premadeRow(index), session.selection == KitUiSelection.Premade(index), locked = false, index)
            }
        }

        repeat(PkLoadoutInterfaceIds.MaxCustomRows) { slot ->
            val custom = customBySlot[slot]
            val locked = slot >= summary.capacity
            val label = when {
                locked -> "Locked - upgrade"
                custom == null -> "${slot + 1}. Empty Slot"
                else -> "${slot + 1}. ${custom.name}"
            }
            access.player.ifSetText(component(PkLoadoutInterfaceIds.customText(slot)), label.take(18))
            setRowColour(player, PkLoadoutInterfaceIds.customRow(slot), session.selection == KitUiSelection.Custom(slot), locked, slot)
        }

        val definition = selectedDefinition(player)
        renderDefinition(access, definition)
        val selectedCustom = selectedCustomSlot(player)
        val hasSavedCustom = selectedCustom?.let(customBySlot::get) != null
        setButtonVisible(player, PkLoadoutInterfaceIds.LoadButton, PkLoadoutInterfaceIds.LoadLabel, definition != null)
        setButtonVisible(player, PkLoadoutInterfaceIds.SaveButton, PkLoadoutInterfaceIds.SaveLabel, selectedCustom != null)
        setButtonVisible(player, PkLoadoutInterfaceIds.RenameButton, PkLoadoutInterfaceIds.RenameLabel, hasSavedCustom)
        setButtonVisible(player, PkLoadoutInterfaceIds.DeleteButton, PkLoadoutInterfaceIds.DeleteLabel, hasSavedCustom)
        setButtonVisible(player, PkLoadoutInterfaceIds.DefaultButton, PkLoadoutInterfaceIds.DefaultLabel, definition != null)
        access.player.ifSetText(component(PkLoadoutInterfaceIds.SaveLabel), if (hasSavedCustom) "Overwrite" else "Save Setup")

        val deathEnabled = service.openAfterDeathEnabled(player)
        access.player.ifSetText(component(PkLoadoutInterfaceIds.DeathLabel), "[${if (deathEnabled) "X" else " "}] Open after death")
        access.player.setColour(component(PkLoadoutInterfaceIds.DeathToggle), Color(if (deathEnabled) SelectedColour else ButtonColour))
        val defaultText = summary.defaultKit?.let { "Default: ${it.id}" } ?: "No default selected"
        val warning = summary.warnings.firstOrNull()
        access.player.ifSetText(component(PkLoadoutInterfaceIds.Status), (status ?: warning ?: defaultText).take(92))
    }

    private fun renderDefinition(access: ProtectedAccess, definition: KitDefinition?) {
        val inventory = definition?.inventory?.associateBy(KitItem::slot).orEmpty()
        repeat(28) { slot -> renderItem(access, inventory[slot], PkLoadoutInterfaceIds.inventoryModel(slot), PkLoadoutInterfaceIds.inventoryCount(slot)) }
        val equipment = definition?.equipment?.associateBy(KitItem::slot).orEmpty()
        EquipmentSlots.forEachIndexed { previewSlot, wornSlot ->
            renderItem(access, equipment[wornSlot], PkLoadoutInterfaceIds.equipmentModel(previewSlot), PkLoadoutInterfaceIds.equipmentCount(previewSlot))
        }
        if (definition == null) {
            access.player.ifSetText(component(PkLoadoutInterfaceIds.SelectedName), "Empty Custom Slot")
            StatNames.indices.forEach { access.player.ifSetText(component(PkLoadoutInterfaceIds.stat(it)), "${StatNames[it]}: -") }
            access.player.ifSetText(component(PkLoadoutInterfaceIds.CombatLevel), "Combat: -")
            access.player.ifSetText(component(PkLoadoutInterfaceIds.Spellbook), "Spellbook: -")
            access.player.ifSetText(component(PkLoadoutInterfaceIds.SpecialEnergy), "Special: -")
            access.player.ifSetText(component(PkLoadoutInterfaceIds.QuickPrayers), "Quick prayers: -")
            access.player.ifSetText(component(PkLoadoutInterfaceIds.Description), "Save your current equipment, inventory, stats and combat settings here.")
            return
        }
        val stats = definition.stats
        val values = listOf(stats.attack, stats.strength, stats.defence, stats.hitpoints, stats.ranged, stats.prayer, stats.magic)
        access.player.ifSetText(component(PkLoadoutInterfaceIds.SelectedName), definition.name.take(25))
        values.forEachIndexed { index, value -> access.player.ifSetText(component(PkLoadoutInterfaceIds.stat(index)), "${StatNames[index]}: $value") }
        access.player.ifSetText(component(PkLoadoutInterfaceIds.CombatLevel), "Combat: ${combatLevel(stats)}")
        access.player.ifSetText(component(PkLoadoutInterfaceIds.Spellbook), "Spellbook: ${spellbookName(definition)}")
        access.player.ifSetText(component(PkLoadoutInterfaceIds.SpecialEnergy), "Special: ${definition.settings.specialEnergy / 10}%")
        access.player.ifSetText(component(PkLoadoutInterfaceIds.QuickPrayers), "Quick prayers: ${Integer.bitCount(definition.settings.quickPrayers)}")
        access.player.ifSetText(component(PkLoadoutInterfaceIds.Description), definition.description.take(76))
    }

    private fun renderItem(access: ProtectedAccess, item: KitItem?, modelChild: Int, countChild: Int) {
        access.ifSetObj(component(modelChild), item?.internalName ?: "obj.blankobject", zoom = 1)
        access.player.ifSetText(component(countChild), item?.count?.takeIf { it > 1 }?.let(::formatAmount).orEmpty())
    }

    private fun initialSelection(player: Player): KitUiSelection {
        val summary = service.slotSummary(player)
        val default = summary.defaultKit
        if (default?.source == KitSource.PREMADE) {
            val index = service.starterKits().indexOfFirst { it.id == default.id }
            if (index >= 0) return KitUiSelection.Premade(index)
        }
        if (default?.source == KitSource.CUSTOM) {
            val custom = summary.kits.firstOrNull { it.id == default.id && it.slot < summary.capacity }
            if (custom != null) return KitUiSelection.Custom(custom.slot)
        }
        return if (service.starterKits().isNotEmpty()) KitUiSelection.Premade(0) else KitUiSelection.Custom(0)
    }

    private fun selectedDefinition(player: Player): KitDefinition? = when (val selection = player.attr[Session]?.selection) {
        is KitUiSelection.Premade -> service.starterKits().getOrNull(selection.index)
        is KitUiSelection.Custom -> service.resolve(player, (selection.slot + 1).toString())
        null -> null
    }

    private fun selectedCustomSlot(player: Player): Int? = (player.attr[Session]?.selection as? KitUiSelection.Custom)?.slot
    private fun hasSession(player: Player): Boolean = player.attr[Session] != null

    private fun setButtonVisible(player: Player, button: Int, label: Int, visible: Boolean) {
        player.ifSetHide(component(button), !visible)
        player.ifSetHide(component(label), !visible)
    }

    private fun setRowColour(player: Player, child: Int, selected: Boolean, locked: Boolean, index: Int) {
        val colour = when {
            selected -> SelectedColour
            locked -> LockedColour
            index % 2 == 0 -> RowColourA
            else -> RowColourB
        }
        player.setColour(component(child), Color(colour))
    }

    private fun showMutation(access: ProtectedAccess, result: KitMutationResult, success: String) {
        access.player.mes(mutationMessage(result, success))
    }

    private fun queueCustomReopen(access: ProtectedAccess, slot: Int, message: String) {
        val player = access.player
        player.attr[PendingReopen] = PendingKitUiReopen(slot, message)
        player.strongQueue(ReopenQueue, 1)
    }

    public fun reopenPending(access: ProtectedAccess) {
        val player = access.player
        val pending = player.attr[PendingReopen] ?: return
        player.attr.remove(PendingReopen)
        player.attr[Session] = KitUiSession(KitUiSelection.Custom(pending.slot))
        access.ifOpenMainModal(Interface, colour = 0x2B241B, transparency = 0)
        configureEvents(access)
        access.runClientScript(30, componentPacked(PkLoadoutInterfaceIds.PremadeScrollbar), componentPacked(PkLoadoutInterfaceIds.PremadeScroll))
        access.runClientScript(30, componentPacked(PkLoadoutInterfaceIds.CustomScrollbar), componentPacked(PkLoadoutInterfaceIds.CustomScroll))
        refresh(access, pending.message)
        player.mes(pending.message)
    }

    private fun mutationMessage(result: KitMutationResult, success: String): String =
        if (result is KitMutationResult.Success) success else (result as KitMutationResult.Failure).reason

    private fun spellbookName(definition: KitDefinition): String = when (definition.settings.spellbook.name.lowercase()) {
        "standard" -> "Normal"
        else -> definition.settings.spellbook.name
    }

    private fun combatLevel(stats: KitStats): Int {
        val base = 0.25 * (stats.defence + stats.hitpoints + floor(stats.prayer / 2.0))
        val melee = 0.325 * (stats.attack + stats.strength)
        val ranged = 0.325 * floor(stats.ranged * 1.5)
        val magic = 0.325 * floor(stats.magic * 1.5)
        return floor(base + maxOf(melee, ranged, magic)).toInt()
    }

    private fun formatAmount(value: Int): String = when {
        value >= 1_000_000 -> "${value / 1_000_000}M"
        value >= 10_000 -> "${value / 1_000}K"
        value >= 1_000 -> String.format("%.1fK", value / 1_000.0).removeSuffix(".0K") + if (value % 1_000 == 0) "K" else ""
        else -> value.toString()
    }

    private fun component(child: Int): String = "component.pk_loadouts:${componentName(child)}"
    private fun componentPacked(child: Int): Int = PkLoadoutInterfaceIds.packed(child)

    private fun componentName(child: Int): String = when {
        child == PkLoadoutInterfaceIds.PremadeScroll -> "premade_scroll"
        child == PkLoadoutInterfaceIds.PremadeScrollbar -> "premade_scrollbar"
        child in PkLoadoutInterfaceIds.PremadeRowFirst until PkLoadoutInterfaceIds.CustomTitle -> {
            val index = (child - PkLoadoutInterfaceIds.PremadeRowFirst) / 2
            if ((child - PkLoadoutInterfaceIds.PremadeRowFirst) % 2 == 0) "premade_row_$index" else "premade_text_$index"
        }
        child == PkLoadoutInterfaceIds.CustomScroll -> "custom_scroll"
        child == PkLoadoutInterfaceIds.CustomScrollbar -> "custom_scrollbar"
        child in PkLoadoutInterfaceIds.CustomRowFirst until PkLoadoutInterfaceIds.InventoryTitle -> {
            val slot = (child - PkLoadoutInterfaceIds.CustomRowFirst) / 2
            if ((child - PkLoadoutInterfaceIds.CustomRowFirst) % 2 == 0) "custom_row_$slot" else "custom_text_$slot"
        }
        child in PkLoadoutInterfaceIds.InventorySlotFirst until PkLoadoutInterfaceIds.EquipmentTitle -> slotComponentName("inventory", child, PkLoadoutInterfaceIds.InventorySlotFirst)
        child in PkLoadoutInterfaceIds.EquipmentSlotFirst until PkLoadoutInterfaceIds.StatsTitle -> slotComponentName("equipment", child, PkLoadoutInterfaceIds.EquipmentSlotFirst)
        else -> FixedNames[child] ?: error("No PK loadout component name for child $child")
    }

    private fun slotComponentName(prefix: String, child: Int, first: Int): String {
        val offset = child - first; val slot = offset / 3
        return when (offset % 3) { 0 -> "${prefix}_bg_$slot"; 1 -> "${prefix}_model_$slot"; else -> "${prefix}_count_$slot" }
    }

    private companion object {
        const val Interface = "interface.pk_loadouts"
        const val ReopenQueue = "queue.pk_kit_ui_reopen"
        const val RowColourA = 0x352C21
        const val RowColourB = 0x2B241B
        const val SelectedColour = 0x7A5B22
        const val LockedColour = 0x4A2520
        const val ButtonColour = 0x6B573A
        val Session = AttributeKey<KitUiSession>()
        val PendingReopen = AttributeKey<PendingKitUiReopen>()
        val EquipmentSlots = intArrayOf(0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13)
        val StatNames = listOf("Attack", "Strength", "Defence", "Hitpoints", "Ranged", "Prayer", "Magic")
        val FixedNames = mapOf(
            PkLoadoutInterfaceIds.SelectedName to "selected_name",
            PkLoadoutInterfaceIds.CombatLevel to "combat_level",
            PkLoadoutInterfaceIds.Spellbook to "spellbook",
            PkLoadoutInterfaceIds.SpecialEnergy to "special_energy",
            PkLoadoutInterfaceIds.QuickPrayers to "quick_prayers",
            PkLoadoutInterfaceIds.Description to "description",
            PkLoadoutInterfaceIds.Status to "status",
            PkLoadoutInterfaceIds.DeathToggle to "death_toggle",
            PkLoadoutInterfaceIds.DeathLabel to "death_label",
            PkLoadoutInterfaceIds.LoadButton to "load_preset_button",
            PkLoadoutInterfaceIds.LoadLabel to "load_preset_label",
            PkLoadoutInterfaceIds.SaveButton to "save_setup_button",
            PkLoadoutInterfaceIds.SaveLabel to "save_setup_label",
            PkLoadoutInterfaceIds.RenameButton to "rename_button",
            PkLoadoutInterfaceIds.RenameLabel to "rename_label",
            PkLoadoutInterfaceIds.DeleteButton to "delete_button",
            PkLoadoutInterfaceIds.DeleteLabel to "delete_label",
            PkLoadoutInterfaceIds.DefaultButton to "set_default_button",
            PkLoadoutInterfaceIds.DefaultLabel to "set_default_label",
        ) + StatNames.mapIndexed { index, name -> PkLoadoutInterfaceIds.stat(index) to "stat_${name.lowercase()}" }
    }
}

private data class KitUiSession(val selection: KitUiSelection)

private data class PendingKitUiReopen(val slot: Int, val message: String)

private sealed interface KitUiSelection {
    data class Premade(val index: Int) : KitUiSelection
    data class Custom(val slot: Int) : KitUiSelection
}
