package org.rsmod.content.other.special.weapons.scripts

import dev.openrune.util.Wearpos
import org.rsmod.api.mechanics.toxins.NoxiousHalberdVirulence
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Virulence is lost when the player's weapon changes, including swapping away and back again.
 */
class NoxiousHalberdVirulenceScript : PluginScript() {
    override fun ScriptContext.startup() {
        onWearposChange {
            if (wearpos == Wearpos.RightHand) {
                NoxiousHalberdVirulence.clear(player)
            }
        }
    }
}
