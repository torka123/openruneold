package org.rsmod.server.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.rsmod.server.services.Service

class GameBootstrapShutdownTest {
    @Test
    fun `failed authority acquisition neither leaks a startup waiter nor releases unowned resources`() =
        runBlocking {
            val resources = Resources("acquire")
            val bootstrap = GameBootstrap(emptySet(), resources, retainFailedShutdown = false)
            assertFailsWith<IllegalStateException> { bootstrap.startupUntilReady() }
            bootstrap.shutdown()
            assertEquals(listOf("acquire"), resources.calls)
        }

    @Test
    fun `main and hook close resources once only after committed service shutdown`() = runBlocking {
        val resources = Resources()
        val saveEntered = CountDownLatch(1)
        val allowCommit = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val saver =
            object : Service {
                override val shutdownStage = Service.ShutdownStage.Persistence

                override suspend fun startup() {}

                override suspend fun shutdown() {
                    saveEntered.countDown()
                    check(allowCommit.await(5, TimeUnit.SECONDS))
                    resources.calls += "commit"
                }
            }
        val bootstrap = GameBootstrap(setOf(saver), resources, retainFailedShutdown = false)
        val hook = bootstrap.startupUntilReady()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val main = executor.submit { bootstrap.awaitShutdown(hook) }
            val hookCall =
                executor.submit {
                    secondEntered.countDown()
                    bootstrap.shutdown()
                }
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
            assertTrue(saveEntered.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { hookCall.get(100, TimeUnit.MILLISECONDS) }
            assertEquals(listOf("acquire", "postgres start", "central start"), resources.calls)
            allowCommit.countDown()
            main.get(5, TimeUnit.SECONDS)
            hookCall.get(5, TimeUnit.SECONDS)
            bootstrap.shutdown()
            assertEquals(
                listOf(
                    "acquire",
                    "postgres start",
                    "central start",
                    "commit",
                    "central stop",
                    "postgres stop",
                    "release",
                ),
                resources.calls,
            )
        } finally {
            allowCommit.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `failed save never closes postgres or releases process authority`() = runBlocking {
        val resources = Resources()
        val saver =
            object : Service {
                override val shutdownStage = Service.ShutdownStage.Persistence

                override suspend fun startup() {}

                override suspend fun shutdown() {
                    error("uncommitted account")
                }
            }
        val bootstrap = GameBootstrap(setOf(saver), resources, retainFailedShutdown = false)
        val hook = bootstrap.startupUntilReady()
        assertFailsWith<IllegalStateException> { bootstrap.shutdown() }
        assertFailsWith<IllegalStateException> { bootstrap.awaitShutdown(hook) }
        assertEquals(listOf("acquire", "postgres start", "central start"), resources.calls)
    }

    @Test
    fun `partial resource startup cleans once without invoking unstarted services`() = runBlocking {
        for (failAt in listOf("postgres start", "central start")) {
            val resources = Resources(failAt)
            val unstarted =
                object : Service {
                    override suspend fun startup() {
                        error("must not start")
                    }

                    override suspend fun shutdown() {
                        error("must not stop")
                    }
                }
            val bootstrap = GameBootstrap(setOf(unstarted), resources, retainFailedShutdown = false)
            assertFailsWith<IllegalStateException> { bootstrap.startupUntilReady() }
            bootstrap.shutdown()
            assertEquals(
                listOf("central stop", "postgres stop", "release"),
                resources.calls.takeLast(3),
            )
            assertEquals(1, resources.calls.count { it == "postgres stop" })
        }
    }

    @Test
    fun `failed service startup uses completed native cleanup before outer resources`() =
        runBlocking {
            val resources = Resources()
            val broken =
                object : Service {
                    override suspend fun startup() {
                        error("startup failed")
                    }

                    override suspend fun shutdown() {
                        resources.calls += "partial service stop"
                    }
                }
            val bootstrap = GameBootstrap(setOf(broken), resources, retainFailedShutdown = false)
            assertFailsWith<IllegalStateException> { bootstrap.startupUntilReady() }
            bootstrap.shutdown()
            assertEquals(
                listOf("partial service stop", "central stop", "postgres stop", "release"),
                resources.calls.takeLast(4),
            )
        }

    private class Resources(val failAt: String? = null) : GameBootstrap.Resources {
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())

        private fun call(name: String) {
            calls += name
            check(name != failAt) { "failed $name" }
        }

        override fun acquire() = call("acquire")

        override fun startPostgres() = call("postgres start")

        override fun startCentral() = call("central start")

        override fun stopCentral() = call("central stop")

        override fun stopPostgres() = call("postgres stop")

        override fun release() = call("release")
    }
}
