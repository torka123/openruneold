package org.rsmod.server.app

import jakarta.inject.Inject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import org.rsmod.api.db.jdbc.EmbeddedSameInstancePostgres
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.net.central.embed.CentralEmbeddedLifecycle
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.server.services.Service
import org.rsmod.server.services.ServiceManager

class GameBootstrap
internal constructor(
    services: Set<Service>,
    private val resources: Resources,
    private val retainFailedShutdown: Boolean = true,
) {
    @Inject
    constructor(
        services: Set<Service>,
        serverConfig: ServerConfig,
        centralEmbedded: CentralEmbeddedLifecycle,
        openRuneCentral: OpenRuneCentralWorldLink,
    ) : this(
        services,
        object : Resources {
            private val processLock = OpenRuneServerProcessLock()

            override fun acquire() = processLock.acquire()

            override fun startPostgres() = EmbeddedSameInstancePostgres.ensureStarted(serverConfig)

            override fun startCentral() {
                centralEmbedded.startIfConfigured()
                openRuneCentral.startInboundWatch()
            }

            override fun stopCentral() {
                openRuneCentral.shutdown()
                centralEmbedded.stopIfRunning()
            }

            override fun stopPostgres() = EmbeddedSameInstancePostgres.stop()

            override fun release() = processLock.close()
        },
    )

    private val serviceManager = ServiceManager.create(services)
    private val gameService = services.filterIsInstance<GameService>().singleOrNull()
    private val startupFinished = CountDownLatch(1)
    private val cleanupFinished = CountDownLatch(1)
    private val cleanupOwner = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var authorityAcquired = false
    @Volatile private var servicesInvoked = false
    @Volatile private var cleanupFailure: Throwable? = null
    private var shutdownHook: Thread? = null

    suspend fun startupUntilReady(): Thread {
        var failure: Throwable? = null
        try {
            resources.acquire()
            authorityAcquired = true
            // Bootstrap is the sole PostgreSQL owner, including partial startup.
            val hook = Thread(::shutdown, "ShutdownHook")
            Runtime.getRuntime().addShutdownHook(hook)
            shutdownHook = hook
            check(!stopRequested.get()) { "Shutdown requested during startup" }
            resources.startPostgres()
            check(!stopRequested.get()) { "Shutdown requested during startup" }
            resources.startCentral()
            check(!stopRequested.get()) { "Shutdown requested during startup" }
            servicesInvoked = true
            val startupResult = serviceManager.awaitStartup()
            if (startupResult is ServiceManager.StartResult.Error) {
                throw startupResult.throwable
            }
            check(!stopRequested.get()) { "Shutdown requested during service startup" }
        } catch (t: Throwable) {
            failure = t
        } finally {
            startupFinished.countDown()
        }
        failure?.let { cause ->
            try {
                shutdown()
            } catch (cleanup: Throwable) {
                cause.addSuppressed(cleanup)
            }
            throw cause
        }
        return checkNotNull(shutdownHook)
    }

    fun awaitShutdown(shutdownHook: Thread) {
        check(shutdownHook === this.shutdownHook)
        finishShutdown()
    }

    suspend fun startup() {
        val hook = startupUntilReady()
        awaitShutdown(hook)
    }

    fun shutdown() {
        stopRequested.set(true)
        serviceManager.shutdown()
        finishShutdown()
    }

    /** Operational preflight; a successful result is required before requesting JVM termination. */
    fun prepareShutdown(): CompletableFuture<Unit> =
        checkNotNull(gameService) { "Native game service is unavailable" }.prepareShutdown()

    private fun finishShutdown() {
        startupFinished.await()
        if (cleanupOwner.compareAndSet(false, true)) {
            try {
                if (servicesInvoked) {
                    val result = serviceManager.awaitShutdown()
                    if (result is ServiceManager.ShutdownResult.Report) {
                        val failure =
                            IllegalStateException(
                                "Service shutdown failed; PostgreSQL and process lock retained"
                            )
                        result.errors.forEach(failure::addSuppressed)
                        throw failure
                    }
                }
                if (authorityAcquired) {
                    resources.stopCentral()
                    resources.stopPostgres()
                    resources.release()
                }
            } catch (t: Throwable) {
                cleanupFailure = t
                if (retainFailedShutdown) retainForRecovery()
            } finally {
                // Removing a hook cannot cancel an already initiated JVM exit. Controlled restart
                // must therefore verify native logout/save drain BEFORE requesting termination.
                shutdownHook?.let { hook ->
                    try {
                        Runtime.getRuntime().removeShutdownHook(hook)
                    } catch (_: IllegalStateException) {
                        /* JVM hooks already running. */
                    }
                }
                cleanupFinished.countDown()
            }
        } else {
            cleanupFinished.await()
        }
        cleanupFailure?.let { throw it }
    }

    private fun retainForRecovery() {
        // Returning from main must not discard the failed service's only in-memory snapshots.
        // Retain this bootstrap and its services until explicit process-level recovery. This cannot
        // cancel an external JVM exit already in progress; controlled preflight remains essential.
        Thread(
                {
                    synchronized(this) {
                        while (true) {
                            try {
                                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                                (this as java.lang.Object).wait()
                            } catch (_: InterruptedException) {
                                // Interruption does not prove that retained player data was saved.
                            }
                        }
                    }
                },
                "openrune-failed-shutdown-retention",
            )
            .apply {
                isDaemon = false
                start()
            }
    }

    /** Test seam for bootstrap-owned resources; production still uses the native owners above. */
    internal interface Resources {
        fun acquire()

        fun startPostgres()

        fun startCentral()

        fun stopCentral()

        fun stopPostgres()

        fun release()
    }
}
