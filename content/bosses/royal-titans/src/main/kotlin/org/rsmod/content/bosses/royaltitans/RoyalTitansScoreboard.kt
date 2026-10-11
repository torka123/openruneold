package org.rsmod.content.bosses.royaltitans

import jakarta.inject.Inject
import java.text.NumberFormat
import java.util.Locale
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class RoyalTitansScoreboard
@Inject
constructor(private val statistics: RoyalTitansStatistics) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfModalButton("component.royal_titans_scoreboard:close") { ifClose() }
        onPlayerLogin {
            val committed = statistics.snapshot(player.accountId)
            VarPlayerIntMapSetter.set(
                player,
                "varp.total_royal_titan_kills",
                committed.personalKills,
            )
        }
    }

    fun show(access: ProtectedAccess) {
        val snapshot = statistics.snapshot(access.player.accountId)
        access.ifOpenMainModal("interface.royal_titans_scoreboard")
        for ((component, text) in texts(snapshot)) access.player.ifSetText(component, text)
    }

    companion object {
        fun texts(snapshot: RoyalTitansStatisticsSnapshot): Map<String, String> =
            linkedMapOf(
                "component.royal_titans_scoreboard:title_text" to "Royal Titans Statistics",
                "component.royal_titans_scoreboard:pkc_content" to
                    formatCount(snapshot.personalKills),
                "component.royal_titans_scoreboard:pd_content" to
                    formatCount(snapshot.personalDeaths),
                "component.royal_titans_scoreboard:pbt_content" to
                    RoyalTitansTime.format(snapshot.personalBestTicks),
                "component.royal_titans_scoreboard:gkc_content" to
                    formatCount(snapshot.globalKills),
                "component.royal_titans_scoreboard:gd_content" to
                    formatCount(snapshot.globalDeaths),
                "component.royal_titans_scoreboard:gbt_content" to
                    RoyalTitansTime.format(snapshot.globalBestTicks),
            )

        // Capture 3402 T60: global kills "58,395,586", deaths "967,404".
        private fun formatCount(value: Int): String =
            NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value)
    }
}

internal object RoyalTitansTime {
    // All 40 visible completion messages in 2283/3398/3402 select the last-defeated Titan's
    // spawn + 1, including joins after that origin. See capture-validation.md for arithmetic.
    fun elapsed(lastTitanSpawnCycle: Int, completionCycle: Int): Int =
        (completionCycle - lastTitanSpawnCycle - 1).also { require(it > 0) }

    /** Integer hundredths avoid locale and floating-point rounding differences. */
    fun format(ticks: Int?): String {
        if (ticks == null) return "-"
        require(ticks >= 0)
        val hundredths = ticks.toLong() * 60
        return "${hundredths / 6000}:${(hundredths / 100 % 60).toString().padStart(2, '0')}.${(hundredths % 100).toString().padStart(2, '0')}"
    }

    fun durationMessage(ticks: Int, receipt: RoyalTitansCompletionReceipt): String =
        if (receipt.newPersonalBest)
            "Fight duration: <col=ff0000>${format(ticks)}</col> (new personal best)"
        else
            "Fight duration: <col=ff0000>${format(ticks)}</col>. Personal best: ${format(receipt.snapshot.personalBestTicks)}"
}
