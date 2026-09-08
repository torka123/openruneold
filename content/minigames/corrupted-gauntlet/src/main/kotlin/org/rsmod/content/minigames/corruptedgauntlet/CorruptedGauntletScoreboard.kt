package org.rsmod.content.minigames.corruptedgauntlet

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetText

internal data class CorruptedGauntletScoreboardSnapshot(
    val completions: Int,
    val deaths: Int,
    val bestCompletionTicks: Int?,
)

/** Revision-240 interface 639, populated from durable Corrupted Gauntlet account records. */
internal object CorruptedGauntletScoreboard {
    private const val INTERFACE = CorruptedGauntletConfig.Interface.SCOREBOARD
    private const val CLOSE = 1
    private const val REGULAR_TITLE = 5
    private const val REGULAR_PERSONAL_COMPLETIONS = 7
    private const val REGULAR_GLOBAL_COMPLETIONS = 9
    private const val REGULAR_PERSONAL_DEATHS = 11
    private const val REGULAR_GLOBAL_DEATHS = 13
    private const val REGULAR_PERSONAL_TIME = 15
    private const val REGULAR_GLOBAL_TIME = 17
    private const val CORRUPTED_TITLE = 18
    private const val CORRUPTED_PERSONAL_COMPLETIONS = 20
    private const val CORRUPTED_GLOBAL_COMPLETIONS = 22
    private const val CORRUPTED_PERSONAL_DEATHS = 24
    private const val CORRUPTED_GLOBAL_DEATHS = 26
    private const val CORRUPTED_PERSONAL_TIME = 28
    private const val CORRUPTED_GLOBAL_TIME = 30

    fun show(access: ProtectedAccess, snapshot: CorruptedGauntletScoreboardSnapshot) {
        access.ifOpenMainModal(interfaceName())
        with(access.player) {
            ifSetText(INTERFACE, REGULAR_TITLE, "The Gauntlet")
            ifSetText(INTERFACE, REGULAR_PERSONAL_COMPLETIONS, "Not available")
            ifSetText(INTERFACE, REGULAR_GLOBAL_COMPLETIONS, "Not available")
            ifSetText(INTERFACE, REGULAR_PERSONAL_DEATHS, "Not available")
            ifSetText(INTERFACE, REGULAR_GLOBAL_DEATHS, "Not available")
            ifSetText(INTERFACE, REGULAR_PERSONAL_TIME, "Not available")
            ifSetText(INTERFACE, REGULAR_GLOBAL_TIME, "Not available")
            ifSetText(INTERFACE, CORRUPTED_TITLE, "The Corrupted Gauntlet")
            ifSetText(INTERFACE, CORRUPTED_PERSONAL_COMPLETIONS, snapshot.completions.toString())
            ifSetText(INTERFACE, CORRUPTED_GLOBAL_COMPLETIONS, "Not available")
            ifSetText(INTERFACE, CORRUPTED_PERSONAL_DEATHS, snapshot.deaths.toString())
            ifSetText(INTERFACE, CORRUPTED_GLOBAL_DEATHS, "Not available")
            ifSetText(INTERFACE, CORRUPTED_PERSONAL_TIME, formatTicks(snapshot.bestCompletionTicks))
            ifSetText(INTERFACE, CORRUPTED_GLOBAL_TIME, "Not available")
        }
    }

    fun closeComponentName(): String =
        RSCM.getReverseMapping(RSCMType.COMPONENT, (INTERFACE shl 16) or CLOSE)

    internal fun formatTicks(ticks: Int?): String {
        if (ticks == null || ticks < 0) return "-"
        val seconds = ticks * CorruptedGauntletConfig.GAME_TICK_MILLIS / 1_000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun interfaceName(): String = RSCM.getReverseMapping(RSCMType.INTERFACE, INTERFACE)
}
