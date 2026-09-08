package org.rsmod.server.app

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

internal class OpenRuneServerProcessLock(
    private val lockFile: Path = Path.of(".data", "openrune-server.lock").toAbsolutePath().normalize(),
) : AutoCloseable {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun acquire() {
        check(lock == null) { "OpenRune server process lock is already held by this process." }
        Files.createDirectories(lockFile.parent)
        val opened =
            FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        val acquired =
            try {
                opened.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
        if (acquired == null) {
            opened.close()
            error(
                "Another OpenRune server is already running from this project. " +
                    "The second launch was stopped before PostgreSQL or player data was touched."
            )
        }
        channel = opened
        lock = acquired
        val details =
            "pid=${ProcessHandle.current().pid()} acquired=${Instant.now()} " +
                "workingDirectory=${Path.of("").toAbsolutePath().normalize()}\n"
        opened.truncate(0)
        opened.position(0)
        opened.write(ByteBuffer.wrap(details.toByteArray(StandardCharsets.UTF_8)))
        opened.force(true)
    }

    @Synchronized
    override fun close() {
        val heldLock = lock
        val opened = channel
        lock = null
        channel = null
        runCatching { heldLock?.release() }
        runCatching { opened?.close() }
    }
}
