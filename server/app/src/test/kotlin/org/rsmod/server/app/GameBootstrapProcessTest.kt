package org.rsmod.server.app

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.rsmod.server.services.Service

/** Uses isolated child JVMs with fake resources; never attaches to OpenRune or opens PostgreSQL. */
class GameBootstrapProcessTest {
    @Test
    fun `failed final save retains process and snapshots after main returns`() {
        withChild("failure") { child ->
            awaitMainReturn(child)
            assertFalse(
                child.waitFor(500, TimeUnit.MILLISECONDS),
                "Failed save lost its owning JVM",
            )
            child.outputStream.bufferedWriter().apply {
                write("exit\n")
                flush()
            }
            assertTrue(child.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, child.exitValue())
        }
    }

    @Test
    fun `successful native shutdown leaves no retention thread and exits naturally`() {
        withChild("success") { child ->
            awaitMainReturn(child)
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Clean shutdown retained its JVM")
            assertEquals(0, child.exitValue())
        }
    }

    private fun awaitMainReturn(child: Process) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val lines =
                executor.submit<String> {
                    val output = StringBuilder()
                    child.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line =
                                reader.readLine()
                                    ?: error("Child exited before main returned: $output")
                            output.appendLine(line)
                            if (line == "BOOTSTRAP_FIXTURE_MAIN_RETURN")
                                return@submit output.toString()
                        }
                    }
                    error("Child output closed")
                }
            lines.get(20, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun withChild(mode: String, test: (Process) -> Unit) {
        // Gradle's worker classpath alone omits test dependencies. Reuse its actual classloader
        // URLs, with an argument file to avoid Windows' command-line length limit.
        val classpath = linkedSetOf<String>()
        classpath += System.getProperty("java.class.path").split(File.pathSeparator)
        var loader: ClassLoader? = javaClass.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) {
                classpath +=
                    loader.urLs
                        .filter { it.protocol == "file" }
                        .map { Path.of(it.toURI()).toString() }
            }
            loader = loader.parent
        }
        val fixtureDir = Files.createTempDirectory(Path.of("build"), "bootstrap-process-")
        val arguments = fixtureDir.resolve("java.args")
        val cp = classpath.joinToString(File.pathSeparator).replace('\\', '/')
        Files.writeString(
            arguments,
            "-cp\n\"$cp\"\n${GameBootstrapProcessFixture::class.java.name}\n$mode\n",
        )
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val child =
            ProcessBuilder(java, "@${arguments.toAbsolutePath()}").redirectErrorStream(true).start()
        try {
            test(child)
        } finally {
            // This process was created above and has no real resources or player state.
            if (child.isAlive) {
                child.destroy()
                if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly().waitFor()
            }
        }
    }
}

internal object GameBootstrapProcessFixture {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val failSave = args.single() == "failure"
        var resourcesClosed = 0
        val resources =
            object : GameBootstrap.Resources {
                override fun acquire() {}

                override fun startPostgres() {}

                override fun startCentral() {}

                override fun stopCentral() {
                    resourcesClosed++
                }

                override fun stopPostgres() {
                    resourcesClosed++
                }

                override fun release() {
                    resourcesClosed++
                }
            }
        val saver =
            object : Service {
                override val shutdownStage = Service.ShutdownStage.Persistence

                override suspend fun startup() {}

                override suspend fun shutdown() {
                    check(!failSave) { "fixture uncommitted snapshot" }
                }
            }
        val bootstrap = GameBootstrap(setOf(saver), resources)
        val hook = bootstrap.startupUntilReady()
        repeat(2) {
            val result = runCatching { bootstrap.shutdown() }
            check(result.isFailure == failSave)
        }
        check(runCatching { bootstrap.awaitShutdown(hook) }.isFailure == failSave)
        check(resourcesClosed == if (failSave) 0 else 3)
        val guards =
            Thread.getAllStackTraces().keys.filter {
                it.name == "openrune-failed-shutdown-retention"
            }
        check(guards.size == if (failSave) 1 else 0)
        check(guards.all { !it.isDaemon })
        // Only this isolated fixture accepts an exit request, after the parent proves retention.
        Thread(
                { if (System.`in`.bufferedReader().readLine() == "exit") System.exit(0) },
                "fixture-exit",
            )
            .apply {
                isDaemon = true
                start()
            }
        println("BOOTSTRAP_FIXTURE_MAIN_RETURN")
    }
}
