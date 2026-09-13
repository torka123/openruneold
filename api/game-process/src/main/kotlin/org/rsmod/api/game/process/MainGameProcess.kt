package org.rsmod.api.game.process

import jakarta.inject.Inject
import org.rsmod.api.db.gateway.GameDbSynchronizer
import org.rsmod.events.EventBus
import org.rsmod.game.GameProcess
import org.rsmod.game.entity.PlayerList

public class MainGameProcess
@Inject
constructor(
    private val gameCycle: GameCycle,
    private val eventBus: EventBus,
    private val playerList: PlayerList,
    private val dbSync: GameDbSynchronizer,
) : GameProcess {
    override fun startup() {
        eventBus.publish(GameLifecycle.Startup)
    }

    override fun cycle() {
        gameCycle.tick()
    }

    override fun preShutdown() {
        setShutdownFlags()
        fastForwardServices()
        // Gateway responses must still run on the game thread when native preflight has already
        // removed every player. Drain them directly instead of advancing an otherwise idle world.
        dbSync.invokeCallbacks(Int.MAX_VALUE)
        fastForwardCycles()
        checkRemainingPlayers()
    }

    override fun shutdown() {
        eventBus.publish(GameLifecycle.Shutdown)
    }

    private fun setShutdownFlags() {
        for (player in playerList) {
            player.pendingShutdown = true
        }
    }

    // Delegates to services to perform fast-forward shutdown. This function may block while
    // services complete their shutdown logic.
    private fun fastForwardServices() {
        dbSync.blockingFastForwardShutdown()
    }

    private fun fastForwardCycles() {
        repeat(SHUTDOWN_MAX_SIMULATIONS) {
            if (playerList.none()) return
            cycle()
        }
    }

    private fun checkRemainingPlayers() {
        val remaining = playerList.toList()
        check(remaining.isEmpty()) {
            // Log up to 250 player names to avoid creating excessively large log entries.
            val names = remaining.take(250).joinToString { "'${it.displayName}'" }
            "${remaining.size} players were unable to logout properly: $names"
        }
    }

    private companion object {
        /**
         * The maximum number of [GameCycle.tick] functions that will be called in a tight,
         * non-delayed loop before the server is shutdown.
         *
         * Stop as soon as all players detach. Reaching this limit with players still present is a
         * failed shutdown, including players whose native save/removal is still in progress.
         */
        private const val SHUTDOWN_MAX_SIMULATIONS = 1024
    }
}
