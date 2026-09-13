package org.rsmod.api.net.central.logging

import com.github.michaelbull.logging.InlineLogger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.openrune.types.ItemServerType
import dev.or2.central.logs.CentralActivityLog
import dev.or2.central.logs.CentralActivityLogRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import org.rsmod.api.db.jdbc.EmbeddedSameInstancePostgres
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

@Singleton
public class CentralActivityLogWriter
internal constructor(
    private val config: ServerConfig,
    private val executor: ExecutorService,
    private val createResources: () -> Resources?,
    private val shutdownTimeoutMillis: Long = 15_000,
) {
    @Inject
    public constructor(
        config: ServerConfig
    ) : this(
        config,
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "central-activity-log-writer").apply { isDaemon = true }
        },
        { nativeResources(config) },
    )

    private val logger = InlineLogger()
    private val failedWrites = ConcurrentLinkedQueue<FailedWrite>()

    @Volatile private var repository: CentralActivityLogRepository? = null

    private var dataSource: AutoCloseable? = null
    private var stopResult: Result<Unit>? = null

    public fun start() {
        synchronized(this) {
            check(!executor.isShutdown) { "Stopped activity log writer cannot restart" }
            if (repository != null) {
                return
            }
            val resources = createResources() ?: return
            dataSource = resources.dataSource
            repository = resources.repository
            logger.info { "Central activity log JDBC enabled" }
        }
    }

    @Synchronized
    public fun stop() {
        stopResult?.let {
            return it.getOrThrow()
        }
        val completed = runCatching {
            executor.shutdown()
            try {
                check(executor.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    "Central activity logs have unfinished writes; datasource retained"
                }
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                throw failure
            }
            if (failedWrites.isNotEmpty()) {
                val failure =
                    IllegalStateException(
                        "Central activity logs failed; records and datasource retained"
                    )
                failedWrites.forEach { failure.addSuppressed(it.cause) }
                throw failure
            }
            dataSource?.close()
            repository = null
            dataSource = null
        }
        stopResult = completed
        completed.getOrThrow()
    }

    public fun logItemDrop(player: Player, type: ItemServerType, obj: InvObj) {
        logItem(
            player = player,
            itemId = type.internalName,
            quantity = obj.count,
            log = { worldId, now, charId, accountId, itemId, qty, x, z ->
                CentralActivityLog.DroppedItem(
                    worldId = worldId,
                    occurredAtEpochMillis = now,
                    characterId = charId,
                    accountId = accountId,
                    itemId = itemId,
                    quantity = qty,
                    tileX = x,
                    tileZ = z,
                )
            },
        )
    }

    /**
     * @param itemIdRscm RSCM object key from the destroy flow (same as
     *   [ItemServerType.internalName]).
     */
    public fun logItemDestroy(player: Player, itemIdRscm: String, obj: InvObj) {
        logItem(
            player = player,
            itemId = itemIdRscm,
            quantity = obj.count,
            log = { worldId, now, charId, accountId, itemId, qty, x, z ->
                CentralActivityLog.DestroyItem(
                    worldId = worldId,
                    occurredAtEpochMillis = now,
                    characterId = charId,
                    accountId = accountId,
                    itemId = itemId,
                    quantity = qty,
                    tileX = x,
                    tileZ = z,
                )
            },
        )
    }

    public fun logCommand(player: Player, command: String, args: List<String>) {
        val repo = repositoryForWrite() ?: return
        val worldId = config.world
        val charId = player.characterId
        val now = System.currentTimeMillis()
        val accountId = player.accountId.toLong()
        val argsCopy = args.toList()
        submit(
            repo,
            CentralActivityLog.Command(
                worldId = worldId,
                occurredAtEpochMillis = now,
                characterId = charId,
                accountId = accountId,
                command = command,
                args = argsCopy,
            ),
        )
    }

    /**
     * Persists a `login` row once the player is in-world with [Player.characterId] /
     * [Player.accountId] set. (Central worlds-link no longer writes login logs.)
     */
    public fun logPlayerLogin(player: Player) {
        val repo = repositoryForWrite() ?: return
        val worldId = config.world
        val now = System.currentTimeMillis()
        val charId = player.characterId
        val accountId = player.accountId.toLong()
        submit(
            repo,
            CentralActivityLog.Login(
                worldId = worldId,
                occurredAtEpochMillis = now,
                characterId = charId,
                accountId = accountId,
            ),
        )
    }

    /**
     * Persists a `logout` row. [centralSessionId] is optional (Central DB session row id when
     * known).
     */
    public fun logPlayerLogout(player: Player, centralSessionId: Long? = null) {
        val repo = repositoryForWrite() ?: return
        val worldId = config.world
        val now = System.currentTimeMillis()
        val charId = player.characterId
        val accountId = player.accountId.toLong()
        submit(
            repo,
            CentralActivityLog.Logout(
                worldId = worldId,
                occurredAtEpochMillis = now,
                characterId = charId,
                accountId = accountId,
                sessionId = centralSessionId,
            ),
        )
    }

    private fun logItem(
        player: Player,
        itemId: String,
        quantity: Int,
        log:
            (
                worldId: Int,
                now: Long,
                charId: Int,
                accountId: Long,
                itemId: String,
                qty: Int,
                x: Int,
                z: Int,
            ) -> CentralActivityLog,
    ) {
        val repo = repositoryForWrite() ?: return
        val worldId = config.world
        val charId = player.characterId
        val accountId = player.accountId.toLong()
        val now = System.currentTimeMillis()
        val x = player.x
        val z = player.z
        submit(repo, log(worldId, now, charId, accountId, itemId, quantity, x, z))
    }

    private fun repositoryForWrite(): CentralActivityLogRepository? {
        val current = repository
        if (executor.isShutdown) {
            throw RejectedExecutionException("Central activity log writer is stopped")
        }
        return current
    }

    private fun submit(repo: CentralActivityLogRepository, log: CentralActivityLog) {
        // The single executor decides admission and preserves original record order. A shutdown
        // cannot cancel accepted records or retry a failed record after later records committed.
        executor.execute {
            try {
                repo.insert(log)
            } catch (failure: Throwable) {
                failedWrites += FailedWrite(repo, log, failure)
                logger.warn(failure) {
                    "Central activity log ${log.type} failed; original record retained"
                }
            }
        }
    }

    internal data class Resources(
        val repository: CentralActivityLogRepository,
        val dataSource: AutoCloseable,
    )

    private data class FailedWrite(
        val repository: CentralActivityLogRepository,
        val log: CentralActivityLog,
        val cause: Throwable,
    )

    private companion object {
        fun nativeResources(config: ServerConfig): Resources? {
            val (jdbc, user, password, poolSize) = resolveCentralJdbc(config) ?: return null
            val dataSource =
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = jdbc
                        username = user
                        this.password = password
                        maximumPoolSize = min(4, poolSize)
                        poolName = "openrune-central-activity-log"
                    }
                )
            return Resources(CentralActivityLogRepository(dataSource), dataSource)
        }

        fun resolveCentralJdbc(config: ServerConfig): JdbcParams? {
            val central = config.central ?: return null
            val pg = central.postgres ?: return null
            val jdbcFromYaml = pg.jdbcUrl.trim()
            val triple =
                if (jdbcFromYaml.isNotEmpty()) {
                    Triple(jdbcFromYaml, pg.user.trim().ifBlank { "openrune" }, pg.password)
                } else if (central.sameInstance) {
                    EmbeddedSameInstancePostgres.jdbcTripleIfEmbedded() ?: return null
                } else {
                    return null
                }
            return JdbcParams(triple.first, triple.second, triple.third, pg.poolSize)
        }
    }

    private data class JdbcParams(
        val jdbcUrl: String,
        val user: String,
        val password: String,
        val poolSize: Int,
    )
}
