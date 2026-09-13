package org.rsmod.api.db.jdbc

import dev.or2.central.db.embedded.EmbeddedPostgresSupport
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@ResourceLock("EmbeddedSameInstancePostgres")
class EmbeddedPostgresOwnershipTest {
    @Test
    fun `owned postgres closes once and reused postgres never closes`() {
        val owned = mock(EmbeddedPostgres::class.java)
        try {
            field("embedded").set(null, owned)
            field("ownedProcess").set(null, mock(ProcessHandle::class.java))
            EmbeddedSameInstancePostgres.stop()
            EmbeddedSameInstancePostgres.stop()
            verify(owned, times(1)).close()
            val reused = mock(EmbeddedPostgresSupport.Session::class.java)
            field("reused").set(null, reused)
            EmbeddedSameInstancePostgres.stop()
            verifyNoInteractions(reused)
            assertNull(field("reused").get(null))
        } finally {
            clearFixture()
        }
    }

    @Test
    fun `failed postgres close retains ownership and reports failure`() {
        val owned = mock(EmbeddedPostgres::class.java)
        doThrow(IllegalStateException("close failed")).`when`(owned).close()
        try {
            field("embedded").set(null, owned)
            field("ownedProcess").set(null, mock(ProcessHandle::class.java))
            assertFailsWith<IllegalStateException> { EmbeddedSameInstancePostgres.stop() }
            assertSame(owned, field("embedded").get(null))
        } finally {
            clearFixture()
        }
    }

    private fun field(name: String) =
        EmbeddedSameInstancePostgres::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }

    @Test
    fun `silently failed library stop retains live postgres ownership`() {
        val owned = mock(EmbeddedPostgres::class.java)
        val process =
            mock(ProcessHandle::class.java) { call ->
                if (call.method.name == "isAlive") true else null
            }
        try {
            field("embedded").set(null, owned)
            field("ownedProcess").set(null, process)
            assertFailsWith<IllegalStateException> { EmbeddedSameInstancePostgres.stop() }
            assertSame(owned, field("embedded").get(null))
        } finally {
            clearFixture()
        }
    }

    private fun clearFixture() {
        field("embedded").set(null, null)
        field("reused").set(null, null)
        field("ownedProcess").set(null, null)
    }
}
