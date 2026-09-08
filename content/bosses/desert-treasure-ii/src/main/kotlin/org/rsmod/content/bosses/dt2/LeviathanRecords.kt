package org.rsmod.content.bosses.dt2

import jakarta.inject.Singleton
import java.util.Base64
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

internal enum class LeviathanCombatTask(val storageId: String, val displayName: String) {
    ADEPT("adept", "Leviathan Adept"),
    SPEED_TRIALIST("speed_trialist", "Leviathan Speed-Trialist"),
    SERPENTINE_SOLO("serpentine_solo", "Serpentine Solo"),
    MASTER("master", "Leviathan Master"),
    SPEED_CHASER("speed_chaser", "Leviathan Speed-Chaser"),
    PERFECT_LEVIATHAN("perfect_leviathan", "Perfect Leviathan"),
    SPEED_RUNNER("speed_runner", "Leviathan Speed-Runner"),
    SLEEPER("sleeper", "Leviathan Sleeper"),
    UNCONVENTIONAL("unconventional", "Unconventional");

    companion object {
        private val byStorageId = entries.associateBy(LeviathanCombatTask::storageId)

        fun fromStorageId(id: String): LeviathanCombatTask? = byStorageId[id]
    }
}

internal data class LeviathanRecordState(
    val deaths: Int = 0,
    val normalPersonalBestTicks: Int = 0,
    val awakenedPersonalBestTicks: Int = 0,
    val lastKillTicks: Int = 0,
    val awakenedCompleted: Boolean = false,
    val perfectKillsThisVisit: Int = 0,
    val completedTasks: Set<LeviathanCombatTask> = emptySet(),
    val recentKillEventIds: List<String> = emptyList(),
    val recentDeathEventIds: List<String> = emptyList(),
)

internal data class LeviathanKillEvidence(
    val eventId: String,
    val rewardEligible: Boolean,
    val elapsedTicks: Int,
    /**
     * Snapshot of the cache-backed NPC killcount varp after this kill; this service never writes
     * it.
     */
    val killCountAfterKill: Int,
    val awakened: Boolean,
    val stunCount: Int,
    val perfectKill: Boolean,
    val onSlayerTask: Boolean,
    val onlyMithrilAmmunition: Boolean,
    val neverAbove25Hitpoints: Boolean,
)

internal data class LeviathanRecordUpdate(
    val state: LeviathanRecordState,
    val changed: Boolean,
    val newPersonalBest: Boolean = false,
    val newlyCompletedTasks: Set<LeviathanCombatTask> = emptySet(),
)

internal object LeviathanRecordCodec {
    private const val VERSION = "v1"
    private const val FIELD_COUNT = 10

    fun encode(state: LeviathanRecordState): String =
        listOf(
                VERSION,
                state.deaths.coerceAtLeast(0),
                state.normalPersonalBestTicks.coerceAtLeast(0),
                state.awakenedPersonalBestTicks.coerceAtLeast(0),
                state.lastKillTicks.coerceAtLeast(0),
                if (state.awakenedCompleted) 1 else 0,
                state.perfectKillsThisVisit.coerceAtLeast(0),
                state.completedTasks.map(LeviathanCombatTask::storageId).sorted().joinToString(","),
                encodeEventIds(state.recentKillEventIds),
                encodeEventIds(state.recentDeathEventIds),
            )
            .joinToString("|")

    fun decode(encoded: String): LeviathanRecordState {
        val fields = encoded.split('|', limit = FIELD_COUNT)
        require(fields.size == FIELD_COUNT && fields[0] == VERSION) {
            "Malformed or unsupported Leviathan record state."
        }
        val completed =
            fields[7].split(',').filter(String::isNotBlank).map { id ->
                requireNotNull(LeviathanCombatTask.fromStorageId(id)) {
                    "Unknown Leviathan combat-task id: $id"
                }
            }
        return LeviathanRecordState(
            deaths = fields[1].nonNegativeInt("death count"),
            normalPersonalBestTicks = fields[2].nonNegativeInt("normal personal best"),
            awakenedPersonalBestTicks = fields[3].nonNegativeInt("awakened personal best"),
            lastKillTicks = fields[4].nonNegativeInt("last kill time"),
            awakenedCompleted = fields[5].booleanFlag("awakened completion"),
            perfectKillsThisVisit = fields[6].nonNegativeInt("perfect visit count"),
            completedTasks = completed.toSet(),
            recentKillEventIds = decodeEventIds(fields[8]),
            recentDeathEventIds = decodeEventIds(fields[9]),
        )
    }

    private fun encodeEventIds(ids: List<String>): String =
        LeviathanRecords.sanitizeEventIds(ids).joinToString(",") { id ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(id.toByteArray(Charsets.UTF_8))
        }

    private fun decodeEventIds(encoded: String): List<String> =
        LeviathanRecords.sanitizeEventIds(
            encoded.split(',').filter(String::isNotBlank).map { value ->
                runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }
                    .getOrElse {
                        throw IllegalArgumentException("Malformed Leviathan record event id.", it)
                    }
            }
        )

    private fun String.nonNegativeInt(label: String): Int =
        toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid Leviathan $label: $this")

    private fun String.booleanFlag(label: String): Boolean =
        when (this) {
            "0" -> false
            "1" -> true
            else -> throw IllegalArgumentException("Invalid Leviathan $label: $this")
        }
}

internal object LeviathanRecords {
    const val SPEED_TRIALIST_SECONDS = 110
    const val SPEED_CHASER_SECONDS = 85
    const val SPEED_RUNNER_SECONDS = 70
    const val PERFECT_KILLS_PER_VISIT = 5
    const val MASTER_KILLCOUNT = 50
    const val MAX_RECENT_EVENT_IDS = 256

    fun recordKill(
        current: LeviathanRecordState,
        evidence: LeviathanKillEvidence,
    ): LeviathanRecordUpdate {
        if (!evidence.rewardEligible) return LeviathanRecordUpdate(current, changed = false)
        require(evidence.eventId.isNotBlank()) { "Leviathan kill event id must not be blank." }
        require(evidence.elapsedTicks > 0) { "Leviathan kill time must be positive." }
        require(evidence.killCountAfterKill >= 1) { "Leviathan post-kill count must be positive." }
        require(evidence.stunCount >= 0) { "Leviathan stun count must not be negative." }
        if (evidence.eventId in current.recentKillEventIds) {
            return LeviathanRecordUpdate(current, changed = false)
        }

        val previousBest =
            if (evidence.awakened) current.awakenedPersonalBestTicks
            else current.normalPersonalBestTicks
        val newBest = previousBest <= 0 || evidence.elapsedTicks < previousBest
        val perfectVisit =
            if (evidence.perfectKill) saturatedIncrement(current.perfectKillsThisVisit)
            else current.perfectKillsThisVisit.coerceAtLeast(0)
        val completed = current.completedTasks.toMutableSet()
        completed += LeviathanCombatTask.ADEPT
        if (evidence.killCountAfterKill >= MASTER_KILLCOUNT) completed += LeviathanCombatTask.MASTER
        if (evidence.stunCount <= 1) completed += LeviathanCombatTask.SERPENTINE_SOLO
        if (!evidence.onSlayerTask) {
            if (underSeconds(evidence.elapsedTicks, SPEED_TRIALIST_SECONDS)) {
                completed += LeviathanCombatTask.SPEED_TRIALIST
            }
            if (underSeconds(evidence.elapsedTicks, SPEED_CHASER_SECONDS)) {
                completed += LeviathanCombatTask.SPEED_CHASER
            }
            if (underSeconds(evidence.elapsedTicks, SPEED_RUNNER_SECONDS)) {
                completed += LeviathanCombatTask.SPEED_RUNNER
            }
        }
        if (perfectVisit >= PERFECT_KILLS_PER_VISIT) {
            completed += LeviathanCombatTask.PERFECT_LEVIATHAN
        }
        if (evidence.awakened) completed += LeviathanCombatTask.SLEEPER
        if (evidence.onlyMithrilAmmunition && evidence.neverAbove25Hitpoints) {
            completed += LeviathanCombatTask.UNCONVENTIONAL
        }

        val next =
            current.copy(
                normalPersonalBestTicks =
                    if (!evidence.awakened && newBest) evidence.elapsedTicks
                    else current.normalPersonalBestTicks,
                awakenedPersonalBestTicks =
                    if (evidence.awakened && newBest) evidence.elapsedTicks
                    else current.awakenedPersonalBestTicks,
                lastKillTicks = evidence.elapsedTicks,
                awakenedCompleted = current.awakenedCompleted || evidence.awakened,
                perfectKillsThisVisit = perfectVisit,
                completedTasks = completed,
                recentKillEventIds = remember(current.recentKillEventIds, evidence.eventId),
            )
        return LeviathanRecordUpdate(
            state = next,
            changed = true,
            newPersonalBest = newBest,
            newlyCompletedTasks = completed - current.completedTasks,
        )
    }

    fun recordDeath(
        current: LeviathanRecordState,
        eventId: String,
        rewardEligible: Boolean,
    ): LeviathanRecordUpdate {
        if (!rewardEligible) return LeviathanRecordUpdate(current, changed = false)
        require(eventId.isNotBlank()) { "Leviathan death event id must not be blank." }
        if (eventId in current.recentDeathEventIds) {
            return LeviathanRecordUpdate(current, changed = false)
        }
        return LeviathanRecordUpdate(
            state =
                current.copy(
                    deaths = saturatedIncrement(current.deaths),
                    recentDeathEventIds = remember(current.recentDeathEventIds, eventId),
                ),
            changed = true,
        )
    }

    fun endVisit(current: LeviathanRecordState): LeviathanRecordUpdate =
        if (current.perfectKillsThisVisit == 0) {
            LeviathanRecordUpdate(current, changed = false)
        } else {
            LeviathanRecordUpdate(current.copy(perfectKillsThisVisit = 0), changed = true)
        }

    fun underSeconds(ticks: Int, seconds: Int): Boolean =
        ticks > 0 && seconds > 0 && ticks.toLong() * 3L < seconds.toLong() * 5L

    internal fun sanitizeEventIds(ids: List<String>): List<String> =
        ids.filter(String::isNotBlank).distinct().takeLast(MAX_RECENT_EVENT_IDS)

    private fun remember(current: List<String>, eventId: String): List<String> =
        sanitizeEventIds(current + eventId)

    private fun saturatedIncrement(value: Int): Int =
        (value.coerceAtLeast(0).toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal object LeviathanRecordAttributes {
    val STATE: AttributeKey<String> = AttributeKey(persistenceKey = "dt2_leviathan_records_v1")
}

@Singleton
internal class LeviathanRecordService {
    private val lock = Any()
    private val globalRecords = LeviathanGlobalRecordService()

    fun snapshot(player: Player): LeviathanRecordState = synchronized(lock) { load(player) }

    fun globalSnapshot(player: Player): LeviathanGlobalRecordState {
        globalRecords.refreshDisplayName(player.accountId, player.displayName)
        return globalRecords.snapshot()
    }

    fun recordKill(player: Player, evidence: LeviathanKillEvidence): LeviathanRecordUpdate =
        synchronized(lock) {
            val update = LeviathanRecords.recordKill(load(player), evidence)
            if (update.changed) {
                // Write the global side first. If its durable write fails, the personal
                // idempotency marker remains retryable instead of silently losing the event.
                globalRecords.recordKill(player.accountId, player.displayName, evidence)
                save(player, update.state)
            }
            update
        }

    fun recordDeath(
        player: Player,
        eventId: String,
        rewardEligible: Boolean,
    ): LeviathanRecordUpdate =
        synchronized(lock) {
            val update = LeviathanRecords.recordDeath(load(player), eventId, rewardEligible)
            if (update.changed) {
                globalRecords.recordDeath(
                    player.accountId,
                    player.displayName,
                    eventId,
                    rewardEligible,
                )
                save(player, update.state)
            }
            update
        }

    fun endVisit(player: Player): LeviathanRecordUpdate =
        synchronized(lock) {
            val update = LeviathanRecords.endVisit(load(player))
            if (update.changed) save(player, update.state)
            update
        }

    private fun load(player: Player): LeviathanRecordState =
        player.attr[LeviathanRecordAttributes.STATE]?.let(LeviathanRecordCodec::decode)
            ?: LeviathanRecordState()

    private fun save(player: Player, state: LeviathanRecordState) {
        player.attr[LeviathanRecordAttributes.STATE] = LeviathanRecordCodec.encode(state)
    }
}
