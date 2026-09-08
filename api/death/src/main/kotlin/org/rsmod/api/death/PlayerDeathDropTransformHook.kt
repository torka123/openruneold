package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/**
 * Converts a selected death drop into its ground-drop components. Returning `null` leaves the item
 * unchanged. The transformation runs after keep/loss selection, so every produced component
 * inherits the original item's loss status.
 */
public fun interface PlayerDeathDropTransformHook {
    public fun transform(player: Player, item: InvObj): List<InvObj>?
}
