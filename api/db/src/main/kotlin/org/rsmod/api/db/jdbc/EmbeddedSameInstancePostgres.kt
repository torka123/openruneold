package org.rsmod.api.db.jdbc

import dev.or2.central.db.embedded.EmbeddedPostgresSupport
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.net.BindException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.rsmod.api.server.config.CentralPostgresYaml
import org.rsmod.api.server.config.ServerConfig

public object EmbeddedSameInstancePostgres {
    @Volatile private var embedded: EmbeddedPostgres? = null

    @Volatile private var reused: EmbeddedPostgresSupport.Session? = null

    @Volatile private var activeDataDir: Path? = null
    @Volatile private var ownedProcess: ProcessHandle? = null

    /**
     * Starts embedded PostgreSQL when same-instance Central is enabled and no JDBC URL is set.
     * Reuses an existing postmaster for the same PGDATA when the port is still bound.
     */
    public fun ensureStarted(config: ServerConfig) {
        val central = config.central ?: return
        if (!central.sameInstance) {
            return
        }
        val pg = central.postgres ?: return
        if (pg.jdbcUrl.trim().isNotEmpty()) {
            return
        }
        synchronized(this) {
            if (embedded != null || reused != null) {
                return
            }
            val dataDir = embeddedPgdataDirectory(pg)
            Files.createDirectories(dataDir)
            activeDataDir = dataDir
            when (val plan = EmbeddedPostgresSupport.planStart(dataDir)) {
                is EmbeddedPostgresSupport.StartPlan.Reuse -> {
                    reused = plan.session
                }
                EmbeddedPostgresSupport.StartPlan.StartNew -> {
                    val instance = startNewEmbedded(dataDir)
                    embedded = instance
                    val pid =
                        Files.readAllLines(dataDir.resolve("postmaster.pid"))
                            .first()
                            .trim()
                            .toLong()
                    ownedProcess =
                        ProcessHandle.of(pid).orElseThrow {
                            IllegalStateException("Owned PostgreSQL exited during startup")
                        }
                }
            }
        }
    }

    /** JDBC URL / user / password when embedded instance is running; otherwise `null`. */
    public fun jdbcTripleIfEmbedded(): Triple<String, String, String>? {
        embedded?.let { instance ->
            return Triple(instance.getJdbcUrl("postgres", "postgres"), "postgres", "")
        }
        reused?.let { session ->
            return Triple(session.jdbcUrl, session.user, session.password)
        }
        return null
    }

    /**
     * Called only after native services have committed their saves and closed their connections.
     */
    public fun stop() {
        synchronized(this) {
            // Keep ownership until close actually succeeds. Never stop a reused postmaster.
            embedded?.let { owned ->
                val process =
                    checkNotNull(ownedProcess) { "Cannot verify owned PostgreSQL process" }
                owned.close()
                // Zonky logs some pg_ctl failures and returns normally. Verify the owned process,
                // not just close() returning, before releasing canonical process authority.
                check(!process.isAlive) { "Owned PostgreSQL did not exit; ownership retained" }
            }
            embedded = null
            ownedProcess = null
            reused = null
            activeDataDir = null
        }
    }

    private fun startNewEmbedded(dataDir: Path): EmbeddedPostgres {
        try {
            return EmbeddedPostgres.builder()
                .setRegisterShutdownHook(false)
                .setDataDirectory(dataDir.toFile())
                .setCleanDataDirectory(false)
                .setPGStartupWait(Duration.ofSeconds(30))
                .start()
        } catch (t: Throwable) {
            if (!isBindException(t)) {
                throw t
            }
            // A bind race is not authority to terminate another process using this data directory.
            return EmbeddedPostgres.builder()
                .setRegisterShutdownHook(false)
                .setDataDirectory(dataDir.toFile())
                .setCleanDataDirectory(false)
                .setPGStartupWait(Duration.ofSeconds(30))
                .start()
        }
    }

    private fun isBindException(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            if (current is BindException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun embeddedPgdataDirectory(pg: CentralPostgresYaml): Path {
        val raw = pg.embeddedPgdataDir.trim()
        val pathStr = if (raw.isEmpty()) ".data/postgres" else raw
        return Path.of(pathStr).toAbsolutePath().normalize()
    }
}
