package org.rsmod.content.bosses.dt2

import jakarta.inject.Singleton
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

internal data class LeviathanGlobalBest(
    val accountId: Int = 0,
    val displayName: String = "",
    val ticks: Int = 0,
)

internal data class LeviathanGlobalRecordState(
    val normalKills: Long = 0,
    val awakenedKills: Long = 0,
    val deaths: Long = 0,
    val normalBest: LeviathanGlobalBest = LeviathanGlobalBest(),
    val awakenedBest: LeviathanGlobalBest = LeviathanGlobalBest(),
    val recentKillEventIds: List<String> = emptyList(),
    val recentDeathEventIds: List<String> = emptyList(),
) {
    val totalKills: Long
        get() = saturatedAdd(normalKills, awakenedKills)

    private fun saturatedAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first.coerceAtLeast(0) < second.coerceAtLeast(0)) Long.MAX_VALUE
        else first.coerceAtLeast(0) + second.coerceAtLeast(0)
}

internal data class LeviathanGlobalRecordUpdate(
    val state: LeviathanGlobalRecordState,
    val changed: Boolean,
)

internal object LeviathanGlobalRecords {
    const val MAX_RECENT_EVENT_IDS = 4_096

    fun recordKill(
        current: LeviathanGlobalRecordState,
        accountId: Int,
        displayName: String,
        evidence: LeviathanKillEvidence,
    ): LeviathanGlobalRecordUpdate {
        if (!evidence.rewardEligible) {
            return LeviathanGlobalRecordUpdate(current, changed = false)
        }
        require(accountId > 0) { "Leviathan global record account id must be positive." }
        require(displayName.isNotBlank()) {
            "Leviathan global record display name must not be blank."
        }
        require(evidence.eventId.isNotBlank()) {
            "Leviathan global kill event id must not be blank."
        }
        require(evidence.elapsedTicks > 0) { "Leviathan global kill time must be positive." }
        if (evidence.eventId in current.recentKillEventIds) {
            return LeviathanGlobalRecordUpdate(current, changed = false)
        }

        val safeName = sanitizeDisplayName(displayName)
        val refreshed = refreshOwnedNames(current, accountId, safeName)
        val next =
            if (evidence.awakened) {
                refreshed.copy(
                    awakenedKills = saturatedIncrement(refreshed.awakenedKills),
                    awakenedBest =
                        betterBest(
                            refreshed.awakenedBest,
                            LeviathanGlobalBest(accountId, safeName, evidence.elapsedTicks),
                        ),
                    recentKillEventIds = remember(refreshed.recentKillEventIds, evidence.eventId),
                )
            } else {
                refreshed.copy(
                    normalKills = saturatedIncrement(refreshed.normalKills),
                    normalBest =
                        betterBest(
                            refreshed.normalBest,
                            LeviathanGlobalBest(accountId, safeName, evidence.elapsedTicks),
                        ),
                    recentKillEventIds = remember(refreshed.recentKillEventIds, evidence.eventId),
                )
            }
        return LeviathanGlobalRecordUpdate(next, changed = true)
    }

    fun recordDeath(
        current: LeviathanGlobalRecordState,
        accountId: Int,
        displayName: String,
        eventId: String,
        rewardEligible: Boolean,
    ): LeviathanGlobalRecordUpdate {
        if (!rewardEligible) {
            return LeviathanGlobalRecordUpdate(current, changed = false)
        }
        require(accountId > 0) { "Leviathan global record account id must be positive." }
        require(displayName.isNotBlank()) {
            "Leviathan global record display name must not be blank."
        }
        require(eventId.isNotBlank()) { "Leviathan global death event id must not be blank." }
        if (eventId in current.recentDeathEventIds) {
            return LeviathanGlobalRecordUpdate(current, changed = false)
        }
        val refreshed = refreshOwnedNames(current, accountId, sanitizeDisplayName(displayName))
        return LeviathanGlobalRecordUpdate(
            refreshed.copy(
                deaths = saturatedIncrement(refreshed.deaths),
                recentDeathEventIds = remember(refreshed.recentDeathEventIds, eventId),
            ),
            changed = true,
        )
    }

    fun refreshDisplayName(
        current: LeviathanGlobalRecordState,
        accountId: Int,
        displayName: String,
    ): LeviathanGlobalRecordUpdate {
        if (accountId <= 0 || displayName.isBlank()) {
            return LeviathanGlobalRecordUpdate(current, changed = false)
        }
        val refreshed = refreshOwnedNames(current, accountId, sanitizeDisplayName(displayName))
        return LeviathanGlobalRecordUpdate(refreshed, changed = refreshed != current)
    }

    private fun refreshOwnedNames(
        current: LeviathanGlobalRecordState,
        accountId: Int,
        displayName: String,
    ): LeviathanGlobalRecordState =
        current.copy(
            normalBest =
                if (current.normalBest.accountId == accountId) {
                    current.normalBest.copy(displayName = displayName)
                } else {
                    current.normalBest
                },
            awakenedBest =
                if (current.awakenedBest.accountId == accountId) {
                    current.awakenedBest.copy(displayName = displayName)
                } else {
                    current.awakenedBest
                },
        )

    /** Lowest positive tick wins; equal times use stable account id ordering. */
    private fun betterBest(
        current: LeviathanGlobalBest,
        candidate: LeviathanGlobalBest,
    ): LeviathanGlobalBest =
        when {
            current.ticks <= 0 -> candidate
            candidate.ticks < current.ticks -> candidate
            candidate.ticks > current.ticks -> current
            candidate.accountId < current.accountId -> candidate
            candidate.accountId > current.accountId -> current
            else -> candidate
        }

    private fun sanitizeDisplayName(name: String): String = name.trim().take(64)

    private fun remember(current: List<String>, eventId: String): List<String> =
        (current.asSequence().filter(String::isNotBlank).filterNot { it == eventId } + eventId)
            .takeLast(MAX_RECENT_EVENT_IDS)
            .toList()

    private fun <T> Sequence<T>.takeLast(count: Int): Sequence<T> =
        toList().takeLast(count).asSequence()

    private fun saturatedIncrement(value: Long): Long =
        if (value >= Long.MAX_VALUE) Long.MAX_VALUE else value.coerceAtLeast(0) + 1L
}

internal object LeviathanGlobalRecordCodec {
    private const val VERSION = 1

    fun encode(state: LeviathanGlobalRecordState): String = buildString {
        appendLine("version=$VERSION")
        appendLine("normalKills=${state.normalKills}")
        appendLine("awakenedKills=${state.awakenedKills}")
        appendLine("deaths=${state.deaths}")
        appendLine("normalBest=${encodeBest(state.normalBest)}")
        appendLine("awakenedBest=${encodeBest(state.awakenedBest)}")
        appendLine("killEvents=${encodeEvents(state.recentKillEventIds)}")
        appendLine("deathEvents=${encodeEvents(state.recentDeathEventIds)}")
    }

    fun decode(text: String): LeviathanGlobalRecordState {
        val values =
            text.lineSequence().filter(String::isNotBlank).associate { line ->
                val split = line.indexOf('=')
                require(split > 0) { "Malformed Leviathan global-record line: $line" }
                line.substring(0, split) to line.substring(split + 1)
            }
        require(values.required("version").toInt() == VERSION) {
            "Unsupported Leviathan global-record version."
        }
        return LeviathanGlobalRecordState(
            normalKills = values.required("normalKills").nonNegativeLong("normal kill count"),
            awakenedKills = values.required("awakenedKills").nonNegativeLong("awakened kill count"),
            deaths = values.required("deaths").nonNegativeLong("death count"),
            normalBest = decodeBest(values.required("normalBest")),
            awakenedBest = decodeBest(values.required("awakenedBest")),
            recentKillEventIds = decodeEvents(values.required("killEvents")),
            recentDeathEventIds = decodeEvents(values.required("deathEvents")),
        )
    }

    private fun encodeBest(best: LeviathanGlobalBest): String =
        "${best.accountId}:${best.ticks}:${encode(best.displayName)}"

    private fun decodeBest(value: String): LeviathanGlobalBest {
        val fields = value.split(':', limit = 3)
        require(fields.size == 3) { "Malformed Leviathan global best record." }
        val accountId = fields[0].toIntOrNull() ?: error("Invalid Leviathan best account id.")
        val ticks = fields[1].toIntOrNull() ?: error("Invalid Leviathan best time.")
        require(accountId >= 0 && ticks >= 0) { "Negative Leviathan global best field." }
        require((accountId == 0) == (ticks == 0)) { "Incomplete Leviathan global best record." }
        val displayName = decodeText(fields[2])
        require(accountId == 0 || displayName.isNotBlank()) { "Blank Leviathan best display name." }
        return LeviathanGlobalBest(accountId, displayName, ticks)
    }

    private fun encodeEvents(values: List<String>): String = values.joinToString(",") { encode(it) }

    private fun decodeEvents(value: String): List<String> {
        val events = value.split(',').filter(String::isNotBlank).map(::decodeText)
        require(events.size <= LeviathanGlobalRecords.MAX_RECENT_EVENT_IDS) {
            "Leviathan global-record event history exceeds its bound."
        }
        require(events.all(String::isNotBlank) && events.distinct().size == events.size) {
            "Malformed Leviathan global-record event history."
        }
        return events
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeText(value: String): String =
        try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed Leviathan global-record text.", error)
        }

    private fun Map<String, String>.required(key: String): String =
        get(key) ?: throw IllegalArgumentException("Missing Leviathan global-record key: $key")

    private fun String.nonNegativeLong(label: String): Long =
        toLongOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid Leviathan global $label: $this")
}

/** Atomic file boundary used by the single authoritative game-server process. */
internal class LeviathanGlobalRecordStore(private val stateFile: Path) {
    @Synchronized
    fun load(): LeviathanGlobalRecordState {
        if (!Files.exists(stateFile)) return LeviathanGlobalRecordState()
        return try {
            LeviathanGlobalRecordCodec.decode(Files.readString(stateFile))
        } catch (error: Exception) {
            throw IllegalStateException("Malformed Leviathan global records at $stateFile", error)
        }
    }

    @Synchronized
    fun save(state: LeviathanGlobalRecordState) {
        Files.createDirectories(stateFile.parent)
        val temporary = stateFile.resolveSibling("${stateFile.fileName}.tmp")
        Files.writeString(temporary, LeviathanGlobalRecordCodec.encode(state))
        try {
            Files.move(
                temporary,
                stateFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        fun live(): LeviathanGlobalRecordStore =
            LeviathanGlobalRecordStore(
                Path.of(".data", "desert-treasure-ii", "leviathan-global-records.txt")
            )
    }
}

@Singleton
internal class LeviathanGlobalRecordService
internal constructor(
    private val store: LeviathanGlobalRecordStore = LeviathanGlobalRecordStore.live()
) {
    private val lock = Any()
    private var state = store.load()

    fun snapshot(): LeviathanGlobalRecordState = synchronized(lock) { state }

    fun recordKill(
        accountId: Int,
        displayName: String,
        evidence: LeviathanKillEvidence,
    ): LeviathanGlobalRecordUpdate =
        synchronized(lock) {
            val update = LeviathanGlobalRecords.recordKill(state, accountId, displayName, evidence)
            if (update.changed) {
                store.save(update.state)
                state = update.state
            }
            update
        }

    fun recordDeath(
        accountId: Int,
        displayName: String,
        eventId: String,
        rewardEligible: Boolean,
    ): LeviathanGlobalRecordUpdate =
        synchronized(lock) {
            val update =
                LeviathanGlobalRecords.recordDeath(
                    state,
                    accountId,
                    displayName,
                    eventId,
                    rewardEligible,
                )
            if (update.changed) {
                store.save(update.state)
                state = update.state
            }
            update
        }

    fun refreshDisplayName(accountId: Int, displayName: String): LeviathanGlobalRecordUpdate =
        synchronized(lock) {
            val update = LeviathanGlobalRecords.refreshDisplayName(state, accountId, displayName)
            if (update.changed) {
                store.save(update.state)
                state = update.state
            }
            update
        }
}
