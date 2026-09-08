package org.rsmod.api.game.process.player

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Allows a scripted NPC arena to bypass only the generic AP ray-cast check.
 *
 * Distance and combat-specific validation remain authoritative. Returning false preserves the
 * standard interaction path unchanged.
 */
public fun interface PlayerNpcApLineOfSightHook {
    public fun bypassLineOfSight(player: Player, npc: Npc): Boolean
}
