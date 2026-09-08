package org.rsmod.content.systems.voting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

internal enum class VoteBossKind {
    TZKAL_ZUK,
    SOL_HEREDIT,
}

internal fun bossFromSecureRoll(value: Boolean): VoteBossKind =
    if (value) VoteBossKind.SOL_HEREDIT else VoteBossKind.TZKAL_ZUK

internal enum class VoteBossEventState {
    QUEUED,
    WARNING,
    ACTIVE,
    DEAD,
    TIMED_OUT,
    CLEANUP,
}

internal fun shouldRequeueAfterRestart(state: VoteBossEventState): Boolean =
    state == VoteBossEventState.QUEUED ||
        state == VoteBossEventState.WARNING ||
        state == VoteBossEventState.ACTIVE

internal data class VoteBossEventRecord(
    var id: String = UUID.randomUUID().toString(),
    var thresholdSerial: Long = 0,
    var boss: VoteBossKind = VoteBossKind.TZKAL_ZUK,
    var arenaIndex: Int = 0,
    var state: VoteBossEventState = VoteBossEventState.QUEUED,
    var createdAt: Long = System.currentTimeMillis(),
    var warningEndsAt: Long = 0,
    var spawnedAt: Long = 0,
    var endedAt: Long = 0,
    var awardedAccounts: MutableSet<Int> = linkedSetOf(),
)

internal data class RaffleEntry(
    var id: String = UUID.randomUUID().toString(),
    var accountId: Int = -1,
    var username: String = "",
    var createdAt: Long = System.currentTimeMillis(),
)

internal data class RafflePrize(
    var drawId: String = UUID.randomUUID().toString(),
    var accountId: Int = -1,
    var username: String = "",
    var itemId: Int = 6199,
    var quantity: Int = 2,
    var drawnAt: Long = System.currentTimeMillis(),
)

internal data class CompletedRaffleDraw(
    var drawId: String = "",
    var winner: String = "",
    var entryCount: Int = 0,
    var drawnAt: Long = 0,
)

internal data class PendingVoteClaim(
    var accountId: Int = -1,
    var username: String = "",
    var voteIds: MutableList<String> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis(),
)

internal data class VoteGlobalState(
    var version: Int = 1,
    var pendingVoteCounter: Int = 0,
    var lifetimeClaimedVotes: Long = 0,
    var nextThresholdSerial: Long = 1,
    var claimedVoteIds: MutableSet<String> = linkedSetOf(),
    var pendingClaims: MutableMap<Int, PendingVoteClaim> = linkedMapOf(),
    var queue: MutableList<VoteBossEventRecord> = mutableListOf(),
    var active: VoteBossEventRecord? = null,
    var raffleEntries: MutableList<RaffleEntry> = mutableListOf(),
    var nextRaffleDrawAt: Long = 0,
    var unclaimedRafflePrizes: MutableMap<Int, MutableList<RafflePrize>> = linkedMapOf(),
    var completedRaffleDraws: MutableList<CompletedRaffleDraw> = mutableListOf(),
)

@Singleton
internal class VoteStateStore @Inject constructor(private val config: VoteSystemConfig) {
    private val log = InlineLogger()
    private val mapper = ObjectMapper().registerKotlinModule()
    private val lock = Any()

    fun load(): VoteGlobalState =
        synchronized(lock) {
            val path = config.statePath
            if (!Files.isRegularFile(path)) return@synchronized VoteGlobalState()
            runCatching { mapper.readValue(path.toFile(), VoteGlobalState::class.java) }
                .onFailure { log.error(it) { "[Vote] Unable to load $path" } }
                .getOrElse {
                    throw IllegalStateException("Refusing to replace unreadable vote state", it)
                }
        }

    fun save(state: VoteGlobalState) {
        synchronized(lock) {
            val path = config.statePath
            path.parent?.let(Files::createDirectories)
            val temp = path.resolveSibling("${path.fileName}.tmp")
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state)
            try {
                Files.move(
                    temp,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun audit(message: String) {
        log.info { "[Vote] $message" }
    }
}

internal fun thresholdsCreated(counter: Int, added: Int, threshold: Int): Pair<Int, Int> {
    require(counter >= 0 && added >= 0 && threshold > 0)
    val total = counter.toLong() + added.toLong()
    return (total % threshold).toInt() to (total / threshold).toInt()
}

internal fun nowIso(): String = Instant.now().toString()
