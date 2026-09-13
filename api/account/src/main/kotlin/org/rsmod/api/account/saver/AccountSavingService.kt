package org.rsmod.api.account.saver

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.server.services.Service
import org.rsmod.server.services.concurrent.ScheduledService

// Serialized account saves: one writer at a time avoids overlapping transactions on the game DB.
// Therefore, save operations are not parallelized and instead run on a single thread.
public class AccountSavingService
@Inject
constructor(
    private val database: GameDatabase,
    private val repository: CharacterAccountRepository,
    private val pipelines: Set<CharacterDataStage.Pipeline>,
    private val serverConfig: ServerConfig,
) : ScheduledService {
    private val logger = InlineLogger()

    private val pendingRequests = ConcurrentLinkedQueue<AccountSaveRequest>()

    private val pendingBatch = ArrayList<AccountSaveRequest>(REQUESTS_PER_BATCH)
    private var consecutiveFailureCount = AtomicInteger(0)
    private val saveGate = Semaphore(1, true)
    private val batchGate = Mutex()
    private val requestMonitor = Any()
    private val outstanding = IdentityHashMap<AccountSaveRequest, Unit>()
    private val failed = IdentityHashMap<AccountSaveRequest, Throwable>()
    private val callbackFailures = ConcurrentLinkedQueue<Throwable>()
    private val committed = AtomicLong()
    private var acceptingRequests = true

    override val shutdownStage: Service.ShutdownStage = Service.ShutdownStage.Persistence

    /** Includes prefetched, queued and in-flight saves until their callback has returned. */
    public fun drainStatus(): DrainStatus =
        synchronized(requestMonitor) {
            DrainStatus(
                outstanding.size,
                failed.size,
                callbackFailures.size,
                committed.get(),
                acceptingRequests,
            )
        }

    public data class DrainStatus(
        val pending: Int,
        val failed: Int,
        val callbackFailures: Int,
        val committed: Long,
        val acceptingRequests: Boolean,
    ) {
        public val isDrained: Boolean
            get() = pending == 0 && failed == 0 && callbackFailures == 0
    }

    /**
     * Runs a game-thread mutation without allowing an account save to observe only half of it.
     * Callers must keep [action] short and queue an account save before returning when the mutation
     * changes persistent player state.
     */
    public fun <T> withExclusiveSave(action: () -> T): T {
        saveGate.acquireUninterruptibly()
        return try {
            action()
        } finally {
            saveGate.release()
        }
    }

    /**
     * Commits one in-memory player mutation and the complete resulting account snapshot in the same
     * serialized save boundary. A successful return means the mutation is durable; a process
     * restart can therefore observe only the pre-mutation or post-mutation state.
     *
     * [rollback] must restore every in-memory value changed by [mutation]. It runs before the save
     * gate is released if either the mutation or database transaction fails.
     */
    public fun commitPlayerMutationBlocking(
        player: org.rsmod.game.entity.Player,
        mutation: () -> Boolean,
        rollback: () -> Unit,
    ): Boolean = withExclusiveSave {
        try {
            if (!mutation()) return@withExclusiveSave false
            database.withTransactionBlocking { connection ->
                repository.save(
                    connection,
                    player,
                    player.accountId,
                    player.characterId,
                    serverConfig.world,
                )
                for (pipeline in pipelines) {
                    pipeline.save(connection, player, player.characterId)
                }
            }
            true
        } catch (cause: Throwable) {
            try {
                rollback()
            } catch (rollbackFailure: Throwable) {
                cause.addSuppressed(rollbackFailure)
            }
            throw cause
        }
    }

    /**
     * Executes a small persistence mutation on the same serialized gate as full account saves.
     * [afterCommit] runs while the gate is still held, so an online player's in-memory mirror can
     * be updated before any later autosave is allowed to replace the committed rows.
     */
    public fun <T> withTransactionBlockingAndThen(
        block: (DatabaseConnection) -> T,
        afterCommit: (T) -> Unit,
    ): T = withExclusiveSave {
        val result = database.withTransactionBlocking(block)
        afterCommit(result)
        result
    }

    public fun queue(request: AccountSaveRequest) {
        synchronized(requestMonitor) {
            check(acceptingRequests) { "Account saver is closed to new requests" }
            if (outstanding.containsKey(request) || failed.containsKey(request)) return
            outstanding[request] = Unit
            pendingRequests.add(request)
        }
    }

    override suspend fun run() {
        val failureBackoffEnabled = consecutiveFailureCount.get() >= MAX_CONSECUTIVE_FAILURES
        val batchPollCount = if (failureBackoffEnabled) 1 else REQUESTS_PER_BATCH

        batchGate.withLock {
            batchPendingRequests(batchPollCount)
            handlePendingBatch()
        }

        if (!failureBackoffEnabled) {
            val backlog = pendingRequests.isNotEmpty()
            delay(if (backlog) DELAY_WHEN_BACKLOG_MS else DELAY_PER_BATCH)
            return
        }

        val backoffDelay = ERROR_BACKOFF_MS
        logger.warn { "Too many consecutive failures, backing off for: ${backoffDelay}ms" }
        delay(backoffDelay)
    }

    private fun batchPendingRequests(count: Int) {
        for (i in 0 until count) {
            val request = pendingRequests.poll() ?: break
            pendingBatch += request
        }
    }

    private suspend fun handlePendingBatch() {
        while (pendingBatch.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val request = pendingBatch.first()
            handleRequestWithTimeout(request, REQUEST_TIMEOUT_MS)
            // Remove only the completed/requeued prefix. Cancellation retains the unread suffix.
            pendingBatch.removeAt(0)
        }
    }

    private suspend fun handleRequestWithTimeout(request: AccountSaveRequest, timeoutMillis: Long) {
        var didCommit = false
        var failure: Exception? = null
        try {
            val result =
                withTimeoutOrNull(timeoutMillis) {
                    saveSegments(request)
                    didCommit = true
                }
            if (result == null && !didCommit)
                failure = IllegalStateException("Account save timed out")
        } catch (cancelled: CancellationException) {
            // Ordinary service cancellation is neither a failed attempt nor a terminal response.
            if (!didCommit) throw cancelled
        } catch (cause: Exception) {
            failure = cause
        }
        if (didCommit) {
            committed.incrementAndGet()
            synchronized(requestMonitor) {
                // A later full snapshot for the same retained player supersedes failed snapshots.
                failed.keys.removeIf { it.player === request.player }
            }
            invokeSaveCallback(request)
            synchronized(requestMonitor) { outstanding.remove(request) }
            resetConsecutiveFailures()
        } else {
            val cause = checkNotNull(failure)
            incrementSaveAttempts(request, cause)
            incrementConsecutiveFailures()
            logger.warn(cause) { "Could not save player account: ${request.player}" }
        }
    }

    private suspend fun saveSegments(request: AccountSaveRequest) {
        saveGate.acquire()
        try {
            database.withTransaction { connection ->
                repository.save(
                    connection,
                    request.player,
                    request.accountId,
                    request.characterId,
                    serverConfig.world,
                )
                for (pipeline in pipelines) {
                    pipeline.save(connection, request.player, request.characterId)
                }
            }
        } finally {
            saveGate.release()
        }
    }

    private fun invokeSaveCallback(request: AccountSaveRequest) {
        safeInvokeCallback(request, AccountSaveResponse.Success(request.player))
    }

    override fun createExecutor(): ExecutorService {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, SERVICE_THREAD_NAME).apply { isDaemon = true }
        }
        return Executors.newSingleThreadExecutor(threadFactory)
    }

    override suspend fun setup() {
        logger.debug {
            "Account saving service accepting $REQUESTS_PER_BATCH requests / $DELAY_PER_BATCH ms."
        }
    }

    override suspend fun startup() {}

    override suspend fun shutdown() {
        logger.info { "Attempting to shutdown account saving service." }
        synchronized(requestMonitor) { acceptingRequests = false }
        val before = committed.get()
        try {
            withTimeout(SHUTDOWN_TIMEOUT_MS) {
                batchGate.withLock {
                    // A cancelled run can leave a partially processed batch outside the queue.
                    handlePendingBatch()
                    while (pendingRequests.isNotEmpty()) {
                        batchPendingRequests(REQUESTS_PER_BATCH)
                        handlePendingBatch()
                    }
                }
            }
            val status = drainStatus()
            check(status.isDrained) { "Account saves are not durably drained: $status" }
            logger.info {
                "Committed ${committed.get() - before} pending account saves before shutdown."
            }
            logger.info { "Account saving service successfully shut down." }
        } catch (t: Throwable) {
            logger.error(t) {
                "Account saving service failed to shut down; pending state retained: ${drainStatus()}"
            }
            throw t
        }
    }

    private fun incrementSaveAttempts(request: AccountSaveRequest, cause: Throwable) {
        if (request.attempts++ < REQUEST_RETRIES_BEFORE_FAILURE) {
            // Existing requests may retry during drain, after admission of new work is closed.
            pendingRequests.add(request)
            return
        }
        synchronized(requestMonitor) {
            failed[request] = cause
            outstanding.remove(request)
        }
        safeInvokeCallback(request, AccountSaveResponse.ExcessiveRetries(request.player))
        logger.error { "Reached max retry attempts for player: ${request.player}" }
    }

    // Ideally, any errors would be handled within the callback itself. However, since this is
    // executed on the service thread, we add a safeguard to prevent one faulty callback from
    // affecting other pending requests.
    private fun safeInvokeCallback(request: AccountSaveRequest, response: AccountSaveResponse) {
        try {
            request.callback(response)
        } catch (e: Throwable) {
            callbackFailures.add(e)
            val message = "Error handling save request callback for: $request (response=$response)"
            logger.error(e) { message }
        }
    }

    private fun incrementConsecutiveFailures() {
        consecutiveFailureCount.updateAndGet { curr -> min(curr + 1, MAX_CONSECUTIVE_FAILURES) }
    }

    private fun resetConsecutiveFailures() {
        val previous = consecutiveFailureCount.getAndSet(0)
        if (previous == 0) {
            return
        }
        logger.info { "Account saving service recovered after $previous failure(s)." }
    }

    private companion object {
        private const val SERVICE_THREAD_NAME = "account-writer"

        /**
         * The time (in ms) to wait between each batch cycle when not under failure backoff and the
         * queue is empty. Avoids a hot loop when nothing is queued.
         */
        private const val DELAY_PER_BATCH = 100L

        /** When saves are already waiting, poll again quickly so work reaches the DB sooner. */
        private const val DELAY_WHEN_BACKLOG_MS = 1L

        /** The maximum number of requests processed per service cycle ([run]). */
        private const val REQUESTS_PER_BATCH = 25

        /**
         * The number of times a request will retry before returning a failure response to the
         * callback. The caller is responsible for any backup or recovery strategy.
         *
         * This is compared against [AccountSaveRequest.attempts].
         */
        private const val REQUEST_RETRIES_BEFORE_FAILURE = 3

        /**
         * The timeout (in ms) before a request is considered failed and its attempt count
         * ([AccountSaveRequest.attempts]) is incremented.
         *
         * If the request still has remaining retries, it will be re-queued for another attempt.
         */
        private const val REQUEST_TIMEOUT_MS = 5000L

        /**
         * The delay duration (in ms) when backing off due to consecutive failures. Gives external
         * systems time to recover (e.g. Database under load).
         */
        private const val ERROR_BACKOFF_MS = 10_000L

        /**
         * The maximum number of consecutive batch failures before the service enters a backoff
         * period.
         *
         * During backoff, the service delays each cycle using [ERROR_BACKOFF_MS]. This failure
         * state persists until at least one request is successfully processed.
         */
        private const val MAX_CONSECUTIVE_FAILURES = 10

        /**
         * A safeguard timeout to prevent the application from waiting indefinitely during service
         * shutdown.
         *
         * While all pending requests are expected to eventually complete, or invoke their callback
         * with a "failed after X attempts" response, this timeout ensures the shutdown process does
         * not hang in case something goes wrong.
         */
        private const val SHUTDOWN_TIMEOUT_MS = 30_000L
    }
}
