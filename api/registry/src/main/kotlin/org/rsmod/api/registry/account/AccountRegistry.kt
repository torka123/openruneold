package org.rsmod.api.registry.account

import jakarta.inject.Inject
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.loader.request.AccountLoadResponse
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

public class AccountRegistry
@Inject
constructor(
    private val eventBus: EventBus,
    private val playerRegistry: PlayerRegistry,
    private val accountManager: AccountManager,
) {
    private val pendingLogins = ConcurrentLinkedQueue<QueuedLogin>()
    private val pendingLogouts = ConcurrentLinkedQueue<QueuedLogout>()
    private val logoutStates = IdentityHashMap<Player, LogoutState>()

    /** Failed logout saves remain attached and block controlled deployment. */
    public fun logoutStatus(): LogoutStatus =
        synchronized(logoutStates) {
            LogoutStatus(logoutStates.size, logoutStates.values.count { it == LogoutState.Failed })
        }

    public data class LogoutStatus(val pending: Int, val failed: Int) {
        public val isDrained: Boolean
            get() = pending == 0
    }

    public fun queueLogin(
        player: Player,
        response: AccountLoadResponse.Ok,
        callback: (Player, AccountLoadResponse.Ok) -> Unit,
    ) {
        val queued = QueuedLogin(player, response, callback)
        pendingLogins.add(queued)
    }

    public fun queueLogout(player: Player) {
        synchronized(logoutStates) {
            if (logoutStates.containsKey(player)) return
            logoutStates[player] = LogoutState.AwaitingSave
        }
        try {
            accountManager.save(player, ::queueSaveResponse)
        } catch (t: Throwable) {
            synchronized(logoutStates) { logoutStates[player] = LogoutState.Failed }
            throw t
        }
    }

    /** Game-thread recovery after the underlying save failure has been repaired. */
    public fun retryFailedLogouts() {
        val retry =
            synchronized(logoutStates) {
                logoutStates.filterValues { it == LogoutState.Failed }.keys.toList()
            }
        retry.forEach { player ->
            synchronized(logoutStates) { logoutStates.remove(player) }
            queueLogout(player)
        }
    }

    public fun handleLogins(count: Int = DEFAULT_LOGINS_PER_CYCLE) {
        var left = count
        while (left-- > 0) {
            val next = pendingLogins.poll() ?: break
            val (player, response, callback) = next
            callback(player, response)
        }
    }

    public fun handleLogouts(count: Int = DEFAULT_LOGOUTS_PER_CYCLE) {
        var left = count
        while (left-- > 0) {
            val next = pendingLogouts.poll() ?: break
            val (player, callback) = next
            callback(player)
        }
    }

    private fun queueSaveResponse(response: AccountSaveResponse) {
        synchronized(logoutStates) {
            if (logoutStates[response.player] != LogoutState.AwaitingSave) return
            when (response) {
                is AccountSaveResponse.Success -> {
                    logoutStates[response.player] = LogoutState.Saved
                    pendingLogouts.add(QueuedLogout(response.player, ::handleLogout))
                }
                is AccountSaveResponse.ExcessiveRetries,
                is AccountSaveResponse.InternalShutdownError -> {
                    // Do not delete the only recoverable in-memory snapshot on a failed save.
                    logoutStates[response.player] = LogoutState.Failed
                }
            }
        }
    }

    private fun handleLogout(player: Player) {
        val result = playerRegistry.del(player)
        check(result == org.rsmod.api.registry.player.PlayerRegistryResult.Delete.Success) {
            "Saved logout could not detach player: $result"
        }
        synchronized(logoutStates) { logoutStates.remove(player) }
    }

    private enum class LogoutState {
        AwaitingSave,
        Saved,
        Failed,
    }

    private data class QueuedLogin(
        val player: Player,
        val response: AccountLoadResponse.Ok,
        val callback: (Player, AccountLoadResponse.Ok) -> Unit,
    )

    private data class QueuedLogout(val player: Player, val callback: (Player) -> Unit)

    private companion object {
        private const val DEFAULT_LOGINS_PER_CYCLE = 100
        private const val DEFAULT_LOGOUTS_PER_CYCLE = 200
    }
}
