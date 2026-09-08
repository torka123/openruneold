package org.rsmod.content.other.pkkits

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.game.entity.Player

public class KitDeathOpenHook @Inject constructor(private val service: KitService) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        if (service.openAfterDeathEnabled(player)) {
            // Runs one cycle after PlayerDeath finishes teleporting, restoring stats, and resetting UI.
            player.queue("queue.pk_kit_open_after_death", 1)
        }
    }
}
