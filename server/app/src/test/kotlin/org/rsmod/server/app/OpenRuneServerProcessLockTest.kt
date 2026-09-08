package org.rsmod.server.app

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenRuneServerProcessLockTest {
    @Test
    fun `second server cannot acquire the same project lock`() {
        val lockFile = createTempDirectory("openrune-lock-test").resolve("server.lock")
        val first = OpenRuneServerProcessLock(lockFile)
        val second = OpenRuneServerProcessLock(lockFile)
        try {
            first.acquire()
            val error = assertFailsWith<IllegalStateException> { second.acquire() }
            assertTrue(error.message.orEmpty().contains("already running"))
            first.close()
            assertTrue(Files.readString(lockFile).contains("pid="))
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun `lock is reusable after the owning server closes`() {
        val lockFile = createTempDirectory("openrune-lock-release-test").resolve("server.lock")
        val first = OpenRuneServerProcessLock(lockFile)
        val replacement = OpenRuneServerProcessLock(lockFile)
        try {
            first.acquire()
            first.close()
            replacement.acquire()
            replacement.close()
            assertTrue(Files.readString(lockFile).contains("pid="))
        } finally {
            replacement.close()
            first.close()
        }
    }
}
