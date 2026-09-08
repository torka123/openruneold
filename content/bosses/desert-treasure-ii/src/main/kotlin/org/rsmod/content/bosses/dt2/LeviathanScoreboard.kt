package org.rsmod.content.bosses.dt2

import java.text.NumberFormat
import java.util.Locale

/** Revision-240 interface 831, as observed in the supplied Leviathan RSProx capture. */
internal object LeviathanScoreboard {
    const val INTERFACE = 831
    const val TITLE = 6
    const val PERSONAL_KILL_COUNT = 9
    const val PERSONAL_DEATHS = 11
    const val PERSONAL_BEST = 13
    const val GLOBAL_KILL_COUNT = 15
    const val GLOBAL_DEATHS = 17
    const val GLOBAL_BEST = 19

    private const val UNAVAILABLE = "-"

    fun texts(
        killCount: Int,
        records: LeviathanRecordState,
        globals: LeviathanGlobalRecordState = LeviathanGlobalRecordState(),
    ): Map<Int, String> =
        linkedMapOf(
            TITLE to title("Leviathan"),
            PERSONAL_KILL_COUNT to formatCount(killCount),
            PERSONAL_DEATHS to formatCount(records.deaths),
            PERSONAL_BEST to formatTime(records.normalPersonalBestTicks),
            GLOBAL_KILL_COUNT to formatCount(globals.totalKills),
            GLOBAL_DEATHS to formatCount(globals.deaths),
            GLOBAL_BEST to formatTime(globals.normalBest.ticks),
        )

    fun formatTime(ticks: Int): String {
        if (ticks <= 0) return UNAVAILABLE
        val totalSeconds = ticks.toLong() * 3L / 5L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    fun formatCount(count: Int): String =
        NumberFormat.getIntegerInstance(Locale.ENGLISH).format(count.coerceAtLeast(0))

    fun formatCount(count: Long): String =
        NumberFormat.getIntegerInstance(Locale.ENGLISH).format(count.coerceAtLeast(0))

    fun title(bossName: String): String = "${escapeMarkup(bossName)} Statistics"

    /** Prevents cache/client text tags from being injected by any future dynamic label source. */
    fun escapeMarkup(text: String): String =
        buildString(text.length) {
            text.forEach { char ->
                when (char) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }
}
