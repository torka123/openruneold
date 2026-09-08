package org.rsmod.api.game.process.npc

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Allows content to terminate an active NPC-to-player chase at an encounter boundary without
 * coupling the generic interaction processor to map coordinates or NPC ids.
 */
public fun interface NpcPlayerChaseValidateHook {
    public fun canChase(npc: Npc, player: Player): Boolean
}
