package org.rsmod.server.services

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ServiceShutdownTest {
    @Test
    fun `cancelled startup retires invoked services and publishes completion`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var stopped = 0
        val service =
            object : Service {
                override suspend fun startup() {
                    entered.complete(Unit)
                    awaitCancellation()
                }

                override suspend fun shutdown() {
                    stopped++
                }
            }
        val manager = ServiceManager.create(setOf(service))
        val startup = launch { manager.awaitStartup() }
        entered.await()
        startup.cancelAndJoin()
        assertIs<ServiceManager.ShutdownResult.Clean>(manager.awaitShutdown())
        assertEquals(1, stopped)
    }

    @Test
    fun `shutdown orders producers persistence and resources regardless of registration`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val resource = service(Service.ShutdownStage.Resource) { calls += "database" }
            val saver = service(Service.ShutdownStage.Persistence) { calls += "commit" }
            val producer = service(Service.ShutdownStage.Producer) { calls += "logout" }
            val manager = ServiceManager.create(linkedSetOf(resource, saver, producer))
            manager.awaitStartup()
            manager.shutdown()
            assertIs<ServiceManager.ShutdownResult.Clean>(manager.awaitShutdown())
            assertEquals(listOf("logout", "commit", "database"), calls)
        }

    @Test
    fun `producer and saver failures retain resources and both errors`() = runBlocking {
        val calls = mutableListOf<String>()
        val first = IllegalStateException("producer failed")
        val second = IllegalStateException("save failed")
        val manager =
            ServiceManager.create(
                linkedSetOf(
                    service(Service.ShutdownStage.Resource) { calls += "database" },
                    service(Service.ShutdownStage.Persistence) {
                        calls += "save attempted"
                        throw second
                    },
                    service(Service.ShutdownStage.Producer) { throw first },
                )
            )
        manager.awaitStartup()
        manager.shutdown()
        val result = assertIs<ServiceManager.ShutdownResult.Report>(manager.awaitShutdown())
        assertEquals(listOf(first, second), result.errors.toList())
        assertEquals(listOf("save attempted"), calls)
    }

    @Test
    fun `failed startup cleans each invoked service once in shutdown order`() = runBlocking {
        val calls = mutableListOf<String>()
        val broken =
            object : Service {
                override suspend fun startup() {
                    error("startup failed")
                }

                override suspend fun shutdown() {
                    calls += "partial producer"
                }
            }
        val manager =
            ServiceManager.create(
                linkedSetOf(service(Service.ShutdownStage.Resource) { calls += "database" }, broken)
            )
        assertIs<ServiceManager.StartResult.Error.Clean>(manager.awaitStartup())
        assertIs<ServiceManager.ShutdownResult.Clean>(manager.awaitShutdown())
        assertEquals(listOf("partial producer", "database"), calls)
    }

    private fun service(stage: Service.ShutdownStage, stop: () -> Unit) =
        object : Service {
            override val shutdownStage = stage

            override suspend fun startup() {}

            override suspend fun shutdown() = stop()
        }

    @Test fun `concurrent callers wait for the same completed cleanup`() = concurrentShutdown(null)

    @Test
    fun `concurrent callers receive the same terminal failure`() =
        concurrentShutdown(IllegalStateException("save failed"))

    private fun concurrentShutdown(failure: Throwable?) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val calls = AtomicInteger()
        val service =
            object : Service {
                override suspend fun startup() {}

                override suspend fun shutdown() {
                    calls.incrementAndGet()
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    failure?.let { throw it }
                }
            }
        val manager = ServiceManager.create(setOf(service))
        runBlocking { assertIs<ServiceManager.StartResult.Healthy>(manager.awaitStartup()) }
        manager.shutdown()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<ServiceManager.ShutdownResult> { manager.awaitShutdown() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second =
                executor.submit<ServiceManager.ShutdownResult> {
                    secondEntered.countDown()
                    manager.awaitShutdown()
                }
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { second.get(100, TimeUnit.MILLISECONDS) }
            release.countDown()
            val result = first.get(5, TimeUnit.SECONDS)
            assertSame(result, second.get(5, TimeUnit.SECONDS))
            assertSame(result, manager.awaitShutdown())
            assertEquals(1, calls.get())
            if (failure == null) assertIs<ServiceManager.ShutdownResult.Clean>(result)
            else
                assertTrue(
                    assertIs<ServiceManager.ShutdownResult.Report>(result).errors.contains(failure)
                )
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
