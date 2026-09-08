package org.rsmod.content.other.pkkits

import dev.openrune.PkLoadoutInterfaceIds
import jakarta.inject.Inject
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class KitInterfaceScript @Inject constructor(private val ui: KitInterface) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfClose(Interface) { ui.close(player) }
        repeat(PkLoadoutInterfaceIds.MaxPremadeRows) { index ->
            onIfModalButton(component("premade_row_$index")) { ui.selectPremade(this, index) }
        }
        repeat(PkLoadoutInterfaceIds.MaxCustomRows) { slot ->
            onIfModalButton(component("custom_row_$slot")) { ui.selectCustom(this, slot) }
        }
        onIfModalButton(component("death_toggle")) { ui.toggleOpenAfterDeath(this) }
        onIfModalButton(component("load_preset_button")) { ui.loadSelected(this) }
        onIfModalButton(component("save_setup_button")) { ui.saveSelected(this) }
        onIfModalButton(component("rename_button")) { ui.renameSelected(this) }
        onIfModalButton(component("delete_button")) { ui.deleteSelected(this) }
        onIfModalButton(component("set_default_button")) { ui.setSelectedDefault(this) }
        onPlayerQueue(DeathQueue) { ui.openAfterDeath(this) }
        onPlayerQueue(ReopenQueue) { ui.reopenPending(this) }
    }

    private companion object {
        const val Interface = "interface.pk_loadouts"
        const val DeathQueue = "queue.pk_kit_open_after_death"
        const val ReopenQueue = "queue.pk_kit_ui_reopen"
        fun component(name: String): String = "component.pk_loadouts:$name"
    }
}
