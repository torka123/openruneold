package org.rsmod.content.bosses.wilderness

import org.rsmod.api.game.process.npc.NpcPlayerChaseValidateHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Keeps the normal Callisto and Artio encounters inside their caves while preserving event spawns.
 */
public class BearCaveChaseValidateHook : NpcPlayerChaseValidateHook {
    override fun canChase(npc: Npc, player: Player): Boolean =
        bearCaveChaseAllowed(npc.type.internalName, npc.coords, player.coords)
}

internal fun bearCaveChaseAllowed(
    npcInternalName: String,
    npcCoords: CoordGrid,
    playerCoords: CoordGrid,
): Boolean {
    val room =
        when (npcInternalName.removePrefix("npc.")) {
            "callisto" -> CALLISTO_ROOM
            "callisto_singles" -> ARTIO_ROOM
            else -> return true
        }
    if (!room.contains(npcCoords)) {
        return true
    }
    return room.contains(playerCoords)
}
