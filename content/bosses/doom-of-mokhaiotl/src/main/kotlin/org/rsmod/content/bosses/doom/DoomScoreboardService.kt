package org.rsmod.content.bosses.doom

import jakarta.inject.Singleton
import java.util.Base64
import org.rsmod.game.entity.Player

internal data class DoomDelveStat(val completions: Int = 0, val bestTicks: Int = 0)

internal data class DoomPersonalStats(
    val deepestDelve: Int = 0,
    val deaths: Int = 0,
    val bestFullRunTicks: Int = 0,
    val deepDelves: Int = 0,
    val delveStats: List<DoomDelveStat> = List(DoomScoreboardCodec.BAND_COUNT) { DoomDelveStat() },
    val completionIds: Set<String> = emptySet(),
    val deathIds: Set<String> = emptySet(),
)

internal object DoomScoreboardCodec {
    const val BAND_COUNT = 9
    private const val VERSION = "v1"

    fun encode(state: DoomPersonalStats): String {
        require(state.delveStats.size == BAND_COUNT)
        val bands = state.delveStats.joinToString(",") { "${it.completions}:${it.bestTicks}" }
        return listOf(
                VERSION,
                state.deepestDelve,
                state.deaths,
                state.bestFullRunTicks,
                state.deepDelves,
                bands,
                state.completionIds.sorted().joinToString(","),
                state.deathIds.sorted().joinToString(","),
            )
            .joinToString("|")
    }

    fun decode(encoded: String): DoomPersonalStats {
        val parts = encoded.split('|', limit = 8)
        require(parts.size == 8 && parts[0] == VERSION) {
            "Malformed or unsupported Doom personal scoreboard state."
        }
        val bands =
            parts[5].split(',').map { entry ->
                val values = entry.split(':')
                require(values.size == 2) { "Malformed Doom delve stat: $entry" }
                DoomDelveStat(values[0].toIntChecked("completion"), values[1].toIntChecked("time"))
            }
        require(bands.size == BAND_COUNT) { "Doom scoreboard must contain $BAND_COUNT bands." }
        return DoomPersonalStats(
            deepestDelve = parts[1].toIntChecked("deepest delve"),
            deaths = parts[2].toIntChecked("deaths"),
            bestFullRunTicks = parts[3].toIntChecked("full-run time"),
            deepDelves = parts[4].toIntChecked("deep delves"),
            delveStats = bands,
            completionIds = parts[6].split(',').filter(String::isNotBlank).toSet(),
            deathIds = parts[7].split(',').filter(String::isNotBlank).toSet(),
        )
    }

    private fun String.toIntChecked(label: String): Int =
        toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid Doom $label value: $this")
}

internal data class DoomGlobalBand(
    val completions: Long = 0,
    val bestTicks: Int = 0,
    val bestPlayer: String = "",
)

internal data class DoomScoreboardSnapshot(
    val personal: DoomPersonalStats,
    val global: DoomGlobalStats,
)

internal data class DoomGlobalStats(
    val deepestDelve: Int = 0,
    val deepestPlayer: String = "",
    val deaths: Long = 0,
    val bestFullRunTicks: Int = 0,
    val bestFullRunPlayer: String = "",
    val deepDelves: Long = 0,
    val bands: List<DoomGlobalBand> = List(DoomScoreboardCodec.BAND_COUNT) { DoomGlobalBand() },
    val completionIds: Set<String> = emptySet(),
    val deathIds: Set<String> = emptySet(),
)

internal object DoomGlobalStatsCodec {
    private const val VERSION = 1

    fun encode(state: DoomGlobalStats): String = buildString {
        appendLine("version=$VERSION")
        appendLine("deepestDelve=${state.deepestDelve}")
        appendLine("deepestPlayer=${encodeName(state.deepestPlayer)}")
        appendLine("deaths=${state.deaths}")
        appendLine("bestFullRunTicks=${state.bestFullRunTicks}")
        appendLine("bestFullRunPlayer=${encodeName(state.bestFullRunPlayer)}")
        appendLine("deepDelves=${state.deepDelves}")
        state.bands.forEachIndexed { index, band ->
            appendLine(
                "band.$index=${band.completions}:${band.bestTicks}:${encodeName(band.bestPlayer)}"
            )
        }
        appendLine("completionIds=${state.completionIds.sorted().joinToString(",")}")
        appendLine("deathIds=${state.deathIds.sorted().joinToString(",")}")
    }

    fun decode(text: String): DoomGlobalStats {
        val values =
            text.lineSequence().filter(String::isNotBlank).associate { line ->
                val split = line.indexOf('=')
                require(split > 0) { "Malformed Doom global scoreboard line: $line" }
                line.substring(0, split) to line.substring(split + 1)
            }
        require(values.required("version").toInt() == VERSION) {
            "Unsupported Doom global scoreboard version."
        }
        val bands =
            List(DoomScoreboardCodec.BAND_COUNT) { index ->
                val parts = values.required("band.$index").split(':', limit = 3)
                require(parts.size == 3) { "Malformed Doom global band $index." }
                DoomGlobalBand(parts[0].toLong(), parts[1].toInt(), decodeName(parts[2]))
            }
        return DoomGlobalStats(
            deepestDelve = values.required("deepestDelve").toInt(),
            deepestPlayer = decodeName(values.required("deepestPlayer")),
            deaths = values.required("deaths").toLong(),
            bestFullRunTicks = values.required("bestFullRunTicks").toInt(),
            bestFullRunPlayer = decodeName(values.required("bestFullRunPlayer")),
            deepDelves = values.required("deepDelves").toLong(),
            bands = bands,
            completionIds =
                values.required("completionIds").split(',').filter(String::isNotBlank).toSet(),
            deathIds = values.required("deathIds").split(',').filter(String::isNotBlank).toSet(),
        )
    }

    private fun Map<String, String>.required(key: String): String =
        get(key) ?: throw IllegalArgumentException("Missing Doom global scoreboard key: $key")

    private fun encodeName(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeName(value: String): String =
        if (value.isBlank()) "" else String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}

internal object DoomStatsTime {
    fun format(ticks: Int, precise: Boolean = false): String {
        if (ticks <= 0) return "--:--"
        val milliseconds = ticks.toLong() * 600L
        if (precise) {
            val tenths = milliseconds / 100L
            val minutes = tenths / 600L
            val seconds = (tenths % 600L) / 10L
            return "%d:%02d.%d".format(minutes, seconds, tenths % 10L)
        }
        val seconds = (milliseconds + 500L) / 1000L
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }
}

@Singleton
internal class DoomScoreboardService {
    private val lock = Any()
    private val store = DoomGlobalStatsStore.live()
    private var global: DoomGlobalStats = store.load()

    fun recordCompletion(
        player: Player,
        resultId: String,
        delve: Int,
        floorTicks: Int,
        chainTicks: Int,
    ) {
        val personal = personal(player)
        if (resultId !in personal.completionIds) {
            val band = bandIndex(delve)
            val bands = personal.delveStats.toMutableList()
            val previous = bands[band]
            bands[band] =
                previous.copy(
                    completions = previous.completions + 1,
                    bestTicks = betterTime(previous.bestTicks, floorTicks),
                )
            player.attr[DOOM_PERSONAL_STATS] =
                DoomScoreboardCodec.encode(
                    personal.copy(
                        deepestDelve = maxOf(personal.deepestDelve, delve),
                        bestFullRunTicks =
                            if (delve == 8) betterTime(personal.bestFullRunTicks, chainTicks)
                            else personal.bestFullRunTicks,
                        deepDelves = personal.deepDelves + if (delve > 8) 1 else 0,
                        delveStats = bands,
                        completionIds = personal.completionIds + resultId,
                    )
                )
        }

        synchronized(lock) {
            if (resultId in global.completionIds) return
            val band = bandIndex(delve)
            val bands = global.bands.toMutableList()
            val previous = bands[band]
            val best = betterTime(previous.bestTicks, floorTicks)
            bands[band] =
                previous.copy(
                    completions = previous.completions + 1,
                    bestTicks = best,
                    bestPlayer =
                        if (best != previous.bestTicks) player.displayName else previous.bestPlayer,
                )
            val fullBest =
                if (delve == 8) betterTime(global.bestFullRunTicks, chainTicks)
                else global.bestFullRunTicks
            global =
                global.copy(
                    deepestDelve = maxOf(global.deepestDelve, delve),
                    deepestPlayer =
                        if (delve > global.deepestDelve) player.displayName
                        else global.deepestPlayer,
                    bestFullRunTicks = fullBest,
                    bestFullRunPlayer =
                        if (delve == 8 && fullBest != global.bestFullRunTicks) player.displayName
                        else global.bestFullRunPlayer,
                    deepDelves = global.deepDelves + if (delve > 8) 1 else 0,
                    bands = bands,
                    completionIds = global.completionIds + resultId,
                )
            store.save(global)
        }
    }

    fun recordDeath(player: Player, deathId: String) {
        val personal = personal(player)
        if (deathId !in personal.deathIds) {
            player.attr[DOOM_PERSONAL_STATS] =
                DoomScoreboardCodec.encode(
                    personal.copy(
                        deaths = personal.deaths + 1,
                        deathIds = personal.deathIds + deathId,
                    )
                )
        }
        synchronized(lock) {
            if (deathId in global.deathIds) return
            global = global.copy(deaths = global.deaths + 1, deathIds = global.deathIds + deathId)
            store.save(global)
        }
    }

    fun read(player: Player): String {
        val personal = personal(player)
        return "Doom: deepest ${displayDelve(personal.deepestDelve)}, " +
            "${personal.delveStats.sumOf(DoomDelveStat::completions)} completions, " +
            "${personal.deaths} deaths."
    }

    fun general(player: Player): List<String> {
        val personal = personal(player)
        val currentGlobal = synchronized(lock) { global }
        return listOf(
            "Personal — deepest ${displayDelve(personal.deepestDelve)}; deaths ${personal.deaths}; " +
                "D1-8 ${DoomStatsTime.format(personal.bestFullRunTicks)}; deep delves ${personal.deepDelves}.",
            "Global — deepest ${displayDelve(currentGlobal.deepestDelve)}" +
                named(currentGlobal.deepestPlayer) +
                "; deaths ${currentGlobal.deaths}; D1-8 " +
                DoomStatsTime.format(currentGlobal.bestFullRunTicks) +
                named(currentGlobal.bestFullRunPlayer) +
                "; deep delves ${currentGlobal.deepDelves}.",
        )
    }

    fun delve(player: Player): List<String> {
        val personal = personal(player)
        val currentGlobal = synchronized(lock) { global }
        return List(DoomScoreboardCodec.BAND_COUNT) { index ->
            val label = if (index == 8) "8+" else "${index + 1}"
            val own = personal.delveStats[index]
            val all = currentGlobal.bands[index]
            "D$label — you ${own.completions} / ${DoomStatsTime.format(own.bestTicks)}; " +
                "global ${all.completions} / ${DoomStatsTime.format(all.bestTicks)}" +
                named(all.bestPlayer) +
                "."
        }
    }

    internal fun snapshot(player: Player): DoomScoreboardSnapshot =
        synchronized(lock) { DoomScoreboardSnapshot(personal(player), global) }

    internal fun personal(player: Player): DoomPersonalStats {
        val encoded = player.attr[DOOM_PERSONAL_STATS]
        if (encoded != null) return DoomScoreboardCodec.decode(encoded)
        val migrated =
            DoomPersonalStats(
                deepestDelve = player.attr[DOOM_BEST_DELVE] ?: 0,
                delveStats =
                    List(DoomScoreboardCodec.BAND_COUNT) { index ->
                        if (index == 0) DoomDelveStat(player.attr[DOOM_KILL_COUNT] ?: 0)
                        else DoomDelveStat()
                    },
            )
        player.attr[DOOM_PERSONAL_STATS] = DoomScoreboardCodec.encode(migrated)
        return migrated
    }

    private fun bandIndex(delve: Int): Int = (delve.coerceAtLeast(1) - 1).coerceAtMost(8)

    private fun betterTime(previous: Int, candidate: Int): Int =
        if (candidate <= 0) previous
        else if (previous <= 0) candidate else minOf(previous, candidate)

    private fun displayDelve(delve: Int): String =
        when {
            delve <= 0 -> "none"
            delve <= 8 -> "D$delve"
            else -> "D8+ ($delve)"
        }

    private fun named(player: String): String = if (player.isBlank()) "" else " by $player"
}
