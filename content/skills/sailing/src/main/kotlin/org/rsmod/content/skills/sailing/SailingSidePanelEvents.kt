package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailingSidePanelEvents @Inject constructor(private val clientState: SailingClientState) :
    PluginScript() {
    override fun ScriptContext.startup() {
        if (!SailingReleaseGate.playerGameplayEnabled) {
            return
        }
        onIfOpen(SIDE_PANEL) { clientState.enableInteractions(player) }
        onIfOverlayButton(SWITCH_BUTTON) { clientState.showCombatOptions(player) }
    }

    private companion object {
        const val SIDE_PANEL = "interface.sailing_sidepanel"
        const val SWITCH_BUTTON = "component.sailing_sidepanel:switch_button"
    }
}
