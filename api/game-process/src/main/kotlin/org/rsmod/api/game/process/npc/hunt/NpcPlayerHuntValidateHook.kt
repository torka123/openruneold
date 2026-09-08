package org.rsmod.api.game.process.npc.hunt

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Allows content to make an otherwise aggressive NPC tolerant of a specific player without coupling
 * the generic hunt processor to an item or NPC id.
 */
public fun interface NpcPlayerHuntValidateHook {
    public fun canHunt(npc: Npc, player: Player): Boolean
}
