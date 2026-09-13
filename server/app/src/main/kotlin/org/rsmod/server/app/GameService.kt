package org.rsmod.server.app

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureNanoTime
import kotlinx.coroutines.delay
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.game.process.PluginScriptBootGate
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.game.GameProcess
import org.rsmod.game.GameUpdate
import org.rsmod.game.entity.PlayerList
import org.rsmod.server.services.concurrent.ScheduledListenerService

class GameService
@Inject
constructor(
    private val process: GameProcess,
    private val scriptBootGate: PluginScriptBootGate,
    private val gameUpdate: GameUpdate,
    private val players: PlayerList,
    private val accounts: AccountRegistry,
    private val saver: AccountSavingService,
) : ScheduledListenerService {
    private val logger = InlineLogger()

    private var excessCycleNanos = 0L
    private val shutdownSignaled = AtomicBoolean(false)
    private val drainRequest = AtomicReference<CompletableFuture<Unit>?>()
    private var drainComplete = false

    /** Request native logout/save on the existing game thread, before services or JVM shutdown. */
    fun prepareShutdown(): CompletableFuture<Unit> {
        while (true) {
            val existing = drainRequest.get()
            if (existing != null && !existing.isCompletedExceptionally) return existing
            val requested = CompletableFuture<Unit>()
            if (drainRequest.compareAndSet(existing, requested)) return requested
        }
    }

    override suspend fun run() {
        // After `signalShutdown`, `run` may be invoked one final time - return early to avoid
        // redundant processing.
        if (shutdownSignaled.get()) {
            return
        }
        if (drainComplete) {
            // Keep the game executor alive for native shutdown signaling, but quiesce producers.
            delay(GAME_TICK_INTERVAL)
            return
        }
        val drain = drainRequest.get()?.takeUnless { it.isDone }
        if (drain != null && gameUpdate.state == null) gameUpdate.startCountdown(1)
        val elapsedNanos = measureNanoTime { process.cycle() } + excessCycleNanos
        if (drain != null) checkShutdownDrain(drain)
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
        val overdue = elapsedMillis > GAME_TICK_INTERVAL
        val sleepTime =
            if (overdue) {
                val elapsedCycleCount = elapsedMillis / GAME_TICK_INTERVAL
                val upcomingCycleDelay = (elapsedCycleCount + 1) * GAME_TICK_INTERVAL
                upcomingCycleDelay - elapsedMillis
            } else {
                GAME_TICK_INTERVAL - elapsedMillis
            }
        if (overdue) {
            logger.error {
                "Cycle took too long (elapsed=${elapsedMillis}ms, sleep=${sleepTime}ms)"
            }
        } else {
            logger.trace { "Cycle took ${elapsedMillis}ms, sleep=${sleepTime}ms" }
        }
        excessCycleNanos = elapsedNanos - TimeUnit.MILLISECONDS.toNanos(elapsedMillis)
        delay(sleepTime)
    }

    private fun checkShutdownDrain(request: CompletableFuture<Unit>) {
        val saves = saver.drainStatus()
        val logouts = accounts.logoutStatus()
        if (saves.failed > 0 || saves.callbackFailures > 0 || logouts.failed > 0) {
            request.completeExceptionally(
                IllegalStateException(
                    "Native logout/save failed; JVM remains running: saves=$saves, logouts=$logouts"
                )
            )
        } else if (
            gameUpdate.isUpdating() && players.none() && saves.isDrained && logouts.isDrained
        ) {
            drainComplete = true
            logger.info {
                "OPENRUNE NATIVE SAVE DRAIN COMPLETE: committed=${saves.committed}; players=0; pending=0"
            }
            request.complete(Unit)
        }
    }

    override fun createExecutor(): ExecutorService {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, GAME_THREAD_NAME).apply { isDaemon = true }
        }
        return Executors.newSingleThreadExecutor(threadFactory)
    }

    override suspend fun setup() {
        scriptBootGate.awaitReady()
        process.startup()
    }

    override suspend fun signalStartup() {}

    override suspend fun signalShutdown() {
        shutdownSignaled.set(true)
        process.preShutdown()
    }

    override suspend fun startup() {}

    override suspend fun shutdown() {
        logger.info { "Attempting to shut down game service." }
        try {
            process.shutdown()
            logger.info { "Game service successfully shut down." }
        } catch (t: Throwable) {
            logger.error(t) { "Game service failed to shut down." }
            throw t
        }
    }

    private companion object {
        private const val GAME_TICK_INTERVAL = 600L
        private const val GAME_THREAD_NAME = "game"
    }
}
