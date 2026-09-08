package org.rsmod.content.bosses.doom

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText

/**
 * Revision-240 interface 920 (gameval: interface.dom_scoreboard). Child IDs were enumerated from
 * the active cache probe; no component IDs are guessed.
 */
internal object DoomScoreboardUi {
    private const val INTERFACE = "interface.dom_scoreboard"
    private const val INTERFACE_ID = 920
    private const val GENERAL = 3
    private const val LEVELS = 5

    fun show(access: ProtectedAccess, snapshot: DoomScoreboardSnapshot, view: View) {
        access.ifOpenMainModal(INTERFACE)
        val personalOnly = view == View.PERSONAL
        val levels = view == View.DELVES
        access.player.ifSetHide(INTERFACE_ID, GENERAL, levels)
        access.player.ifSetHide(INTERFACE_ID, LEVELS, !levels)

        setGeneral(access, snapshot, personalOnly)
        setLevels(access, snapshot)
    }

    private fun setGeneral(
        access: ProtectedAccess,
        snapshot: DoomScoreboardSnapshot,
        personalOnly: Boolean,
    ) {
        val personal = snapshot.personal
        val global = snapshot.global
        access.player.ifSetText(INTERFACE_ID, 72, displayDelve(personal.deepestDelve))
        access.player.ifSetText(INTERFACE_ID, 74, personal.deaths.toString())
        access.player.ifSetText(INTERFACE_ID, 76, DoomStatsTime.format(personal.bestFullRunTicks))
        access.player.ifSetText(INTERFACE_ID, 84, personal.deepDelves.toString())

        val globalVisible = !personalOnly
        listOf(77, 78, 79, 80, 81, 82, 85, 86).forEach {
            access.player.ifSetHide(INTERFACE_ID, it, !globalVisible)
        }
        if (globalVisible) {
            access.player.ifSetText(INTERFACE_ID, 78, displayDelve(global.deepestDelve))
            access.player.ifSetText(INTERFACE_ID, 80, global.deaths.toString())
            access.player.ifSetText(INTERFACE_ID, 82, DoomStatsTime.format(global.bestFullRunTicks))
            access.player.ifSetText(INTERFACE_ID, 86, global.deepDelves.toString())
        }
    }

    private fun setLevels(access: ProtectedAccess, snapshot: DoomScoreboardSnapshot) {
        val personal = snapshot.personal.delveStats
        val global = snapshot.global.bands
        access.player.ifSetText(INTERFACE_ID, 44, personal.sumOf { it.completions }.toString())
        for (index in 0 until DoomScoreboardCodec.BAND_COUNT) {
            val label = if (index == 8) "8+" else (index + 1).toString()
            val p = personal[index]
            val g = global[index]
            val pValue = 46 + index * 3
            val pTime = pValue + 1
            val gValue = 14 + index * 3
            val gTime = gValue + 1
            access.player.ifSetText(INTERFACE_ID, pValue, p.completions.toString())
            access.player.ifSetText(INTERFACE_ID, pTime, DoomStatsTime.format(p.bestTicks))
            access.player.ifSetText(INTERFACE_ID, gValue, g.completions.toString())
            access.player.ifSetText(INTERFACE_ID, gTime, DoomStatsTime.format(g.bestTicks))
            access.player.ifSetText(INTERFACE_ID, 45 + index * 3, "D$label")
            access.player.ifSetText(INTERFACE_ID, 13 + index * 3, "D$label")
        }
    }

    private fun displayDelve(delve: Int): String =
        when {
            delve <= 0 -> "None"
            delve <= 8 -> "D$delve"
            else -> "D8+ ($delve)"
        }

    internal enum class View {
        PERSONAL,
        GENERAL,
        DELVES,
    }
}
