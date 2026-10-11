package org.rsmod.content.bosses.giantmole

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLocU
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Public Mole Hole entry. The custom encounter deliberately has no scripted cave exit. */
public class GiantMoleAccess : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLocU(GIANT_MOLE_HILL, SPADE) { enterMoleHole() }
    }

    private suspend fun ProtectedAccess.enterMoleHole() {
        arriveDelay()
        anim(DIG_ANIM)
        delay(2)
        telejump(GIANT_MOLE_ENTRY)
        mes("You dig down into the Mole Hole.")
    }
}

internal val GIANT_MOLE_ENTRY: CoordGrid = CoordGrid(1752, 5137)

private const val DIG_ANIM: String = "seq.human_dig"
