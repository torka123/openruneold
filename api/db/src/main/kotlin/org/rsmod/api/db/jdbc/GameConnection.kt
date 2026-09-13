package org.rsmod.api.db.jdbc

import jakarta.inject.Inject
import jakarta.inject.Provider
import java.sql.Connection
import java.sql.DriverManager
import org.rsmod.api.db.DatabaseConfig

public class GameConnection
@Inject
constructor(private val configProvider: Provider<DatabaseConfig>) {
    public fun connect(): Connection = connect(configProvider.get())

    internal fun connect(config: DatabaseConfig): Connection {
        val connection = DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
        try {
            connection.autoCommit = false
            return connection
        } catch (cause: Throwable) {
            try {
                connection.close()
            } catch (closeCause: Throwable) {
                if (closeCause !== cause) cause.addSuppressed(closeCause)
            }
            throw cause
        }
    }
}
