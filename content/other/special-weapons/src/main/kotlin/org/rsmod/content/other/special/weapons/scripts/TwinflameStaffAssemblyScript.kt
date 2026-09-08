package org.rsmod.content.other.special.weapons.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TwinflameStaffAssemblyScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(FIRE_CROWN, ICE_CROWN) { assembleTwinflameStaff() }
        onOpHeldU(FIRE_CROWN, BATTLESTAFF) { assembleTwinflameStaff() }
        onOpHeldU(ICE_CROWN, BATTLESTAFF) { assembleTwinflameStaff() }
    }

    private fun ProtectedAccess.assembleTwinflameStaff() {
        val missing =
            when {
                invTotal(inv, FIRE_CROWN) == 0 -> "the fire crown"
                invTotal(inv, ICE_CROWN) == 0 -> "the ice crown"
                invTotal(inv, BATTLESTAFF) == 0 -> "a battlestaff"
                else -> null
            }
        if (missing != null) {
            mes("You also need $missing to create the Twinflame staff.")
            return
        }

        // All three counts were verified above and protected interactions are processed serially.
        invDel(inv, FIRE_CROWN, 1, ICE_CROWN, 1)
        invDel(inv, BATTLESTAFF, 1)
        invAdd(inv, TWINFLAME_STAFF, 1)
        mes("You combine the two crowns with the battlestaff to create a Twinflame staff.")
    }

    private companion object {
        const val FIRE_CROWN: String = "obj.twinflame_piece_2"
        const val ICE_CROWN: String = "obj.twinflame_piece_1"
        const val BATTLESTAFF: String = "obj.battlestaff"
        const val TWINFLAME_STAFF: String = "obj.twinflame_staff"
    }
}
