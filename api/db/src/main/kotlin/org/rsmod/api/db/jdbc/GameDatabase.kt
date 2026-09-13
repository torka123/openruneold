package org.rsmod.api.db.jdbc

import dev.or2.central.db.FlywayMigrator
import jakarta.inject.Inject
import jakarta.inject.Provider
import java.sql.Connection
import java.sql.SQLException
import org.rsmod.api.db.Database
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.util.DatabaseCommitUncertainException
import org.rsmod.api.db.util.DatabaseRollbackException

public class GameDatabase
@Inject
constructor(private val configProvider: Provider<DatabaseConfig>) : Database {
    private lateinit var connection: Connection
    private val transactionMonitor = Any()

    public fun connect(connector: GameConnection) {
        check(!::connection.isInitialized) { "Connection already initialized." }
        val config = configProvider.get()
        val connection = connector.connect()
        try {
            FlywayMigrator.migrate(config.jdbcUrl, config.user, config.password)
            connection.commit()
        } catch (t: Throwable) {
            try {
                connection.rollback()
            } catch (_: Throwable) {
                // ignore
            }
            throw t
        }
        this.connection = connection
    }

    public fun close() {
        synchronized(transactionMonitor) {
            assertValidConnection()
            this.connection.close()
        }
    }

    override suspend fun <T> withTransaction(block: (DatabaseConnection) -> T): T =
        withSerializedConnection(block)

    /** Blocking variant for the game thread (avoids [kotlinx.coroutines.runBlocking] overhead). */
    public fun <T> withTransactionBlocking(block: (DatabaseConnection) -> T): T =
        withSerializedConnection(block)

    /**
     * Executes one prepared SQL transaction without retrying its callback or commit.
     *
     * A failed commit acknowledgement has an unknown outcome, even if a subsequent rollback
     * succeeds. Receipt-aware callers must reconcile [DatabaseCommitUncertainException] and retain
     * unresolved state before permitting another account snapshot. This does not provide that
     * reconciliation or suspend gameplay. Keep gameplay mutation and randomness outside [block].
     */
    public fun <T> withTransactionBlockingOnce(block: (DatabaseConnection) -> T): T =
        synchronized(transactionMonitor) {
            assertValidConnection()
            val result =
                try {
                    block(DatabaseConnection(connection))
                } catch (cause: Throwable) {
                    try {
                        connection.rollback()
                    } catch (rollbackCause: Throwable) {
                        throw DatabaseRollbackException(cause, rollbackCause)
                    }
                    throw cause
                }
            try {
                connection.commit()
            } catch (cause: Throwable) {
                val rollbackCause =
                    try {
                        connection.rollback()
                        null
                    } catch (rollbackFailure: Throwable) {
                        rollbackFailure
                    }
                throw DatabaseCommitUncertainException(cause, rollbackCause)
            }
            result
        }

    /**
     * The database owns one JDBC [connection], so every transaction must use the same monitor. This
     * also serializes the blocking game-thread entry point with gateway worker requests.
     */
    private fun <T> withSerializedConnection(block: (DatabaseConnection) -> T): T =
        synchronized(transactionMonitor) {
            withConnection { connection ->
                val wrapped = DatabaseConnection(connection)
                try {
                    val result = block(wrapped)
                    connection.commit()
                    result
                } catch (t: Throwable) {
                    try {
                        connection.rollback()
                    } catch (rollbackEx: Throwable) {
                        throw DatabaseRollbackException(t, rollbackEx)
                    }
                    throw t
                }
            }
        }

    private fun <T> withConnection(block: (Connection) -> T): T {
        assertValidConnection()
        repeat(MAX_ATTEMPTS - 1) {
            try {
                return block(connection)
            } catch (_: SQLException) {
                Thread.sleep(BACKOFF_MILLIS)
            }
        }
        return block(connection)
    }

    private fun assertValidConnection() {
        check(::connection.isInitialized) { "Connection was not initialized." }
        check(!connection.isClosed) { "Connection is closed." }
    }

    private companion object {
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MILLIS = 10L
    }
}
