package org.rsmod.api.net.central

import com.google.inject.AbstractModule
import com.google.inject.Guice
import jakarta.inject.Inject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.rsmod.api.net.social.SocialRuntimeService
import org.rsmod.api.server.config.OpenRuneCentralGameConfig
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.GameUpdate

class OpenRuneCentralWorldLinkShutdownTest {
    @Test
    fun `Guice shares the authoritative client across separate native consumers`() {
        val injector =
            Guice.createInjector(
                object : AbstractModule() {
                    override fun configure() {
                        bind(ServerConfig::class.java).toInstance(config())
                        bind(GameUpdate::class.java).toInstance(GameUpdate())
                    }
                }
            )
        val first = injector.getInstance(Consumer::class.java)
        val second = injector.getInstance(Consumer::class.java)
        assertSame(first.central, second.central)
        assertSame(first.central, injector.getInstance(OpenRuneCentralWorldLink::class.java))
        first.central.shutdown()
    }

    @Test
    fun `shutdown waits for active and queued accepted auth work without cancellation`() {
        val central = client()
        val executor = field<ExecutorService>(central, "authExecutor")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val events = CopyOnWriteArrayList<String>()
        executor.execute {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            events.add("active")
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        executor.execute { events.add("queued") }
        val stopper = Thread {
            try {
                central.shutdown(5_000)
                events.add("stopped")
            } catch (caught: Throwable) {
                failure.set(caught)
            } finally {
                stopped.countDown()
            }
        }
        try {
            stopper.start()
            awaitAdmissionClosed(executor)
            assertEquals(1L, stopped.count)
            assertFalse(executor.isTerminated)
            assertFailsWith<RejectedExecutionException> {
                central.beginAuthenticate("late-login", charArrayOf('x'), 1)
            }
            release.countDown()
            assertTrue(stopped.await(5, TimeUnit.SECONDS))
            assertEquals(null, failure.get())
            assertEquals(listOf("active", "queued", "stopped"), events.toList())
            assertTrue(executor.isTerminated)
            central.shutdown()
        } finally {
            release.countDown()
            stopper.join(10_000)
            runCatching { central.shutdown() }
        }
    }

    @Test
    fun `timeout retains accepted auth work and every later stop sees the same failure`() {
        val central = client()
        val executor = field<ExecutorService>(central, "authExecutor")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(2)
        executor.execute {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            completed.countDown()
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        executor.execute { completed.countDown() }
        try {
            val failure = assertFailsWith<IllegalStateException> { central.shutdown(1) }
            assertFalse(executor.isTerminated)
            assertEquals(2L, completed.count)
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertSame(failure, assertFailsWith<IllegalStateException> { central.shutdown() })
        } finally {
            release.countDown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `interrupted shutdown caller retains its interrupt and accepted auth work`() {
        val central = client()
        val executor = field<ExecutorService>(central, "authExecutor")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        executor.execute {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            completed.countDown()
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            Thread.currentThread().interrupt()
            val failure = assertFailsWith<IllegalStateException> { central.shutdown(1_000) }
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(failure.suppressed.any { it is InterruptedException })
            assertFalse(executor.isTerminated)
            assertEquals(1L, completed.count)
        } finally {
            Thread.interrupted()
            release.countDown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals(0L, completed.count)
    }

    @Test
    fun `partially connected watcher is joined and retained when native handshake has not finished`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 5_000
            val central = client(listener.localPort)
            var socket: Socket? = null
            try {
                central.startInboundWatch()
                socket = listener.accept()
                val watcher = assertNotNull(field<Thread?>(central, "inboundWatchThread"))
                central.startInboundWatch()
                assertSame(watcher, field<Thread?>(central, "inboundWatchThread"))
                // The real watcher is waiting for native HELLO_ACK on this socket.
                val failure = assertFailsWith<IllegalStateException> { central.shutdown(1) }
                assertSame(watcher, field<Thread?>(central, "inboundWatchThread"))
                assertTrue(watcher.isAlive)
                assertFalse(watcher.isInterrupted)
                socket.close()
                watcher.join(5_000)
                assertFalse(watcher.isAlive)
                assertSame(failure, assertFailsWith<IllegalStateException> { central.shutdown() })
                assertSame(watcher, field<Thread?>(central, "inboundWatchThread"))
            } finally {
                socket?.close()
                runCatching { central.stopInboundWatch(5_000) }
                runCatching { central.shutdown() }
            }
        }
    }

    @Test
    fun `stop before watcher startup closes admission and prevents a late partial start`() {
        val central = client()
        central.shutdown()
        central.shutdown()
        assertFailsWith<IllegalStateException> { central.startInboundWatch() }
        assertEquals(null, field<Thread?>(central, "inboundWatchThread"))
        assertTrue(field<ExecutorService>(central, "authExecutor").isTerminated)
    }

    private fun awaitAdmissionClosed(executor: ExecutorService) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!executor.isShutdown && System.nanoTime() < deadline) Thread.yield()
        assertTrue(executor.isShutdown)
    }

    private fun client(port: Int = 1): OpenRuneCentralWorldLink =
        OpenRuneCentralWorldLink(config(port), GameUpdate(), SocialRuntimeService())

    private fun config(port: Int = 1): ServerConfig =
        ServerConfig(
            name = "shutdown-test",
            gamePort = 0,
            revision = 240,
            environment = "test",
            world = 1,
            central =
                OpenRuneCentralGameConfig(host = "127.0.0.1", worldKey = "test", linkPort = port),
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T

    private class Consumer @Inject constructor(val central: OpenRuneCentralWorldLink)
}
