package org.rsmod.server.services

import java.util.Queue
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.rsmod.server.services.concurrent.ScheduledListenerService
import org.rsmod.server.services.concurrent.ScheduledService
import org.rsmod.server.services.util.safeShutdown

/**
 * Manages the lifecycle of a collection of [Service] and [ScheduledService] instances.
 *
 * [ServiceManager] is responsible for orchestrating the startup, scheduling, and graceful shutdown
 * of all registered services. It provides error handling, service failure propagation, and resource
 * cleanup guarantees during shutdown.
 *
 * Services are started in parallel when [awaitStartup] is invoked. If any service fails during
 * startup, all previously initialized services will be shut down in parallel, and the error will be
 * returned to the caller.
 *
 * Scheduled services implement [ScheduledService] and are scheduled on dedicated [ExecutorService]
 * instances returned by each implementation's [ScheduledService.createExecutor]. These executors
 * are owned and shut down by the manager.
 *
 * To trigger shutdown, call [shutdown], which will unblock [awaitShutdown] and begin shutting down
 * all services. Errors during scheduled service execution are automatically caught and will also
 * initiate the shutdown of all services.
 *
 * @see [Service]
 * @see [ScheduledService]
 */
public class ServiceManager
private constructor(
    private val services: List<Service>,
    private val listeners: List<ListenerService>,
    private val scheduled: List<ScheduledService>,
    private val scheduledListeners: List<ScheduledListenerService>,
) {
    private val activeExecutors = mutableListOf<ExecutorService>()
    private val activeScopes = mutableMapOf<ScheduledService, CoroutineScope>()
    private val scheduledErrors = ConcurrentLinkedQueue<Throwable>()

    private val shutdownLatch = CountDownLatch(1)
    private val shutdownRequest = AtomicBoolean(false)
    private val shutdownInProgress = AtomicBoolean(false)
    private val shutdownCompleted = CountDownLatch(1)
    @Volatile private var shutdownResult: ShutdownResult? = null

    /**
     * Starts all registered [Service] and [ScheduledService] instances.
     *
     * This method blocks the current thread until startup has either:
     * - Completed successfully, in which case it returns [StartResult.Healthy], or
     * - Failed during service startup, scheduled service initialization, startup signaling, or
     *   service scheduling, in which case it returns an appropriate [StartResult.Error].
     *
     * Services are started concurrently using coroutines. If any service throws during its
     * [Service.startup] call, all previously started services will be shut down to ensure proper
     * cleanup. Any failure during shutdown will be captured and reported as part of
     * [StartResult.Error.Lingering].
     *
     * If all services start successfully, [ScheduledService] instances will be scheduled on their
     * dedicated [ExecutorService] instances as returned by [ScheduledService.createExecutor]. These
     * executors are managed and shut down automatically by the manager.
     *
     * @return [StartResult] indicating whether startup completed successfully or failed.
     */
    public suspend fun awaitStartup(): StartResult {
        val started = ConcurrentLinkedQueue<Service>()

        suspend fun safeShutdownResult(t: Throwable): StartResult.Error {
            // A cancelled startup must still clean partially initialized services once, in order.
            shutdownInProgress.set(true)
            withContext(NonCancellable) {
                try {
                    cleanupThreads(30_000L)
                    if (activeExecutors.all(ExecutorService::isTerminated)) {
                        shutdownServices(30_000L, started.toList(), started)
                    } else {
                        scheduledErrors +=
                            IllegalStateException("Startup cleanup left active service executors")
                    }
                } catch (failure: Throwable) {
                    scheduledErrors += failure
                } finally {
                    publishShutdownResult()
                    shutdown()
                }
            }
            val errors = scheduledErrors.toList()
            return if (errors.isEmpty()) StartResult.Error.Clean(t)
            else StartResult.Error.Lingering(t, errors.first(), started.toList())
        }

        // Scope construction/awaiting can itself be cancelled before returning a Deferred list.
        try {
            val startupJobs = supervisorScope { services.map { startService(it, started) } }
            startupJobs.awaitAll()
            val listenerSignals = supervisorScope { listeners.map { signalStartup(it) } }
            listenerSignals.awaitAll()
            for (service in scheduled) {
                val executor = service.createExecutor()
                activeExecutors += executor
                cacheCoroutineScope(service, executor)
            }
            val scheduledSignals = supervisorScope { scheduledListeners.map { signalStartup(it) } }
            scheduledSignals.awaitAll()
            for (service in scheduled) {
                scheduleService(service)
            }
            return StartResult.Healthy
        } catch (t: Throwable) {
            return safeShutdownResult(t)
        }
    }

    /**
     * Blocks the current thread until a shutdown signal is received via [shutdown].
     *
     * Once triggered, this method will:
     * - Cancel all active [ScheduledService] coroutine jobs.
     * - Shut down all [ScheduledService] executors.
     * - Send the shutdown signal to [ScheduledListenerService] and [ListenerService].
     * - Call [Service.shutdown] concurrently within each ordered shutdown stage.
     *
     * If any service throws an exception during its shutdown calls, the error will be captured and
     * returned as part of the resulting [ShutdownResult.Report].
     *
     * Concurrent and subsequent callers wait for the same completed terminal result. Entering
     * shutdown is not evidence that resources are safe to close.
     *
     * @param signalTimeoutSecs the maximum time to wait for active listener services to acknowledge
     *   a shutdown signal. This phase allows services to perform immediate preparations (e.g., a
     *   game service fast-forwarding player processing before disconnecting players, allowing a
     *   player-saving service to persist their data).
     * @param cleanupTimeoutSecs the maximum time to wait for active [ScheduledService] coroutine
     *   jobs to be canceled and their executors to be shut down.
     * @param shutdownTimeoutSecs the maximum time to wait for all registered services to complete
     *   their [Service.shutdown] logic.
     * @return [ShutdownResult] representing the outcome of the shutdown process.
     */
    public fun awaitShutdown(
        signalTimeoutSecs: Int = 30,
        cleanupTimeoutSecs: Int = 30,
        shutdownTimeoutSecs: Int = 30,
    ): ShutdownResult {
        shutdownLatch.await()

        val canShutdown = shutdownInProgress.compareAndSet(false, true)
        if (!canShutdown) {
            shutdownCompleted.await()
            return checkNotNull(shutdownResult)
        }
        try {
            signalShutdown(signalTimeoutSecs * 1000L)
            cleanupThreads(cleanupTimeoutSecs * 1000L)
            // Never drain a writer's mutable batch concurrently with a lingering writer thread.
            if (activeExecutors.all(ExecutorService::isTerminated)) {
                runBlocking { shutdownServices(shutdownTimeoutSecs * 1000L) }
            } else {
                scheduledErrors +=
                    IllegalStateException(
                        "Service executors have not terminated; resources retained"
                    )
            }
        } catch (t: Throwable) {
            scheduledErrors += t
        } finally {
            publishShutdownResult()
        }
        return checkNotNull(shutdownResult)
    }

    private fun publishShutdownResult() {
        val errors = scheduledErrors.toList()
        shutdownResult =
            if (errors.isEmpty()) ShutdownResult.Clean else ShutdownResult.Report(errors)
        shutdownCompleted.countDown()
    }

    /**
     * Initiates the shutdown process for all services.
     *
     * This method signals the manager to begin shutting down. It unblocks any thread waiting on
     * [awaitShutdown] and triggers the cleanup process, which includes:
     * - Canceling active coroutine jobs.
     * - Shutting down executor services.
     * - Shutting down all registered services.
     *
     * If shutdown has already been requested, subsequent calls to this method will no-op.
     *
     * This method is safe to call multiple times, but shutdown will only be performed once.
     */
    public fun shutdown() {
        val requestShutdown = shutdownRequest.compareAndSet(false, true)
        if (requestShutdown) {
            shutdownLatch.countDown()
        }
    }

    private fun CoroutineScope.startService(service: Service, started: Queue<Service>) = async {
        started.add(service)
        service.startup()
    }

    private fun CoroutineScope.signalStartup(service: ListenerService) = async {
        service.signalStartup()
    }

    private fun cacheCoroutineScope(service: ScheduledService, executor: ExecutorService) {
        val coroutineScope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        activeScopes[service] = coroutineScope
    }

    private fun signalStartup(service: ScheduledListenerService): Deferred<Unit> {
        val coroutineScope = activeScopes.getValue(service)
        return coroutineScope.async { service.signalStartup() }
    }

    private fun scheduleService(service: ScheduledService) {
        val coroutineScope = activeScopes.getValue(service)
        coroutineScope.launch {
            try {
                service.setup()
                while (isActive && !shutdownRequest.get()) {
                    service.run()
                }
            } catch (_: CleanupException) {
                // Noop - This is a controlled shutdown signal.
            } catch (t: Throwable) {
                scheduledErrors += t
                shutdown()
            }
        }
    }

    private fun signalShutdown(timeoutMillis: Long) = runBlocking {
        try {
            withTimeout(timeoutMillis) {
                supervisorScope {
                    try {
                        signalListenerShutdown()
                    } catch (t: Throwable) {
                        scheduledErrors += t
                    }
                    try {
                        signalScheduledShutdown()
                    } catch (t: Throwable) {
                        scheduledErrors += t
                    }
                }
            }
        } catch (t: TimeoutCancellationException) {
            scheduledErrors += t
        }
    }

    private suspend fun CoroutineScope.signalListenerShutdown() {
        val signalJobs = listeners.map { service -> async { service.signalShutdown() } }
        signalJobs.awaitAll()
    }

    private suspend fun signalScheduledShutdown() {
        val signalJobs =
            scheduledListeners.map { service ->
                val serviceScope = activeScopes.getValue(service)
                serviceScope.async { service.signalShutdown() }
            }
        signalJobs.awaitAll()
    }

    private fun cleanupThreads(timeoutMillis: Long) = runBlocking {
        val activeScopes = activeScopes.values
        val activeJobs = activeScopes.mapNotNull { it.coroutineContext[Job] }
        activeJobs.forEach(::safeCancel)
        val executorCount = activeExecutors.size.coerceAtLeast(1)
        val perExecutorSecs = (timeoutMillis / 1000L / executorCount).coerceAtLeast(1L)
        activeExecutors.forEach { safeShutdown(it, perExecutorSecs) }
        try {
            withTimeout(timeoutMillis) { activeJobs.joinAll() }
        } catch (t: TimeoutCancellationException) {
            scheduledErrors += t
        }
    }

    private suspend fun shutdownServices(
        timeoutMillis: Long,
        selected: List<Service> = services,
        pending: Queue<Service>? = null,
    ) {
        try {
            withTimeout(timeoutMillis) {
                for (stage in Service.ShutdownStage.entries) {
                    if (stage == Service.ShutdownStage.Resource && scheduledErrors.isNotEmpty())
                        break
                    supervisorScope {
                        selected
                            .filter { it.shutdownStage == stage }
                            .map { service ->
                                async {
                                    try {
                                        service.shutdown()
                                        pending?.remove(service)
                                    } catch (t: Throwable) {
                                        scheduledErrors += t
                                    }
                                }
                            }
                            .awaitAll()
                    }
                }
            }
        } catch (t: TimeoutCancellationException) {
            scheduledErrors += t
        }
    }

    private fun safeCancel(job: Job) {
        job.cancel(CleanupException())
    }

    private fun safeShutdown(executor: ExecutorService, timeoutSecs: Long = 15L) {
        executor.safeShutdown(timeoutSecs)
    }

    public sealed class StartResult {
        public data object Healthy : StartResult()

        public sealed class Error : StartResult() {
            public abstract val throwable: Throwable

            public data class Clean(override val throwable: Throwable) : Error()

            public data class Lingering(
                override val throwable: Throwable,
                val shutdownEx: Throwable,
                val pending: Collection<Service>,
            ) : Error()
        }
    }

    public sealed class ShutdownResult {
        public data object Clean : ShutdownResult()

        public data class Report(val errors: Collection<Throwable>) : ShutdownResult()
    }

    private class CleanupException : CancellationException("Coroutine cancelled by ServiceManager")

    public companion object {
        /**
         * Creates a new [ServiceManager] with the provided set of [Service] instances.
         *
         * @param services the complete set of services to manage. May include both regular and
         *   scheduled services.
         * @return a configured [ServiceManager] instance ready for startup and shutdown
         *   orchestration.
         */
        public fun create(services: Set<Service>): ServiceManager {
            val listeners = services.filterIsInstance<ListenerService>()
            val scheduled = services.filterIsInstance<ScheduledService>()
            val scheduledListeners = services.filterIsInstance<ScheduledListenerService>()
            return ServiceManager(services.toList(), listeners, scheduled, scheduledListeners)
        }
    }
}
