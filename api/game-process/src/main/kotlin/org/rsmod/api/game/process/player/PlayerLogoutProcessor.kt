package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent

/*
 * This class is not strictly required, as it is not expected to grow and could have been inlined
 * into `PlayerLogoutProcess`. However, the split promotes a clear logical boundary between the
 * pending logout step and the actual logout logic.
 */
public class PlayerLogoutProcessor
@Inject
constructor(
    private val eventBus: EventBus,
    private val accountRegistry: AccountRegistry,
    private val exceptionHandler: GameExceptionHandler,
) {
    public fun process(player: Player) {
        player.completeLogout()
    }

    // Finalizes the logout process. The player remains in the player list until their account
    // save callback completes, which is guaranteed to occur.
    private fun Player.completeLogout() {
        val event = SessionStateEvent.Logout(this)
        val hooks = eventBus.unbound[event::class.java].orEmpty()
        for (hook in hooks) {
            try {
                hook(event)
            } catch (e: Exception) {
                exceptionHandler.handle(e) {
                    "Error in logout cleanup hook for player: ${this@completeLogout}."
                }
            } catch (e: NotImplementedError) {
                exceptionHandler.handle(e) {
                    "Error in logout cleanup hook for player: ${this@completeLogout}."
                }
            }
        }
        // A broken content cleanup hook must never prevent persistence or trap the player online.
        accountRegistry.queueLogout(this)
    }
}
