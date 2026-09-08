package org.rsmod.api.db.jdbc

import jakarta.inject.Provider
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.db.DatabaseConfig

class GameDatabaseConcurrencyTest {
    @Test
    fun `suspending and blocking transactions cannot overlap on the shared connection`() {
        val database = GameDatabase(Provider<DatabaseConfig> { error("Unused by this test") })
        database.installConnection(noOpConnection())

        val firstEntered = CountDownLatch(1)
        val secondAttempting = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first =
                executor.submit {
                    runBlocking {
                        database.withTransaction {
                            firstEntered.countDown()
                            assertTrue(secondAttempting.await(5, TimeUnit.SECONDS))
                            assertFalse(
                                secondEntered.await(250, TimeUnit.MILLISECONDS),
                                "A blocking transaction entered while the suspending transaction still owned the shared connection.",
                            )
                        }
                    }
                }
            val second =
                executor.submit {
                    assertTrue(firstEntered.await(5, TimeUnit.SECONDS))
                    secondAttempting.countDown()
                    database.withTransactionBlocking { secondEntered.countDown() }
                }

            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun GameDatabase.installConnection(connection: Connection) {
        val field = GameDatabase::class.java.getDeclaredField("connection")
        field.isAccessible = true
        field.set(this, connection)
    }

    private fun noOpConnection(): Connection =
        Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "isClosed" -> false
                "commit",
                "rollback",
                "close" -> Unit
                "toString" -> "NoOpConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> primitiveDefault(method.returnType)
            }
        } as Connection

    private fun primitiveDefault(type: Class<*>): Any? =
        when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
}
