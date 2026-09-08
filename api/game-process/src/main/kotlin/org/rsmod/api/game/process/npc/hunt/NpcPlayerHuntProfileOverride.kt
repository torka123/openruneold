package org.rsmod.api.game.process.npc.hunt

import org.rsmod.game.entity.Npc

/** A location-aware override for NPCs whose cache ID is used in multiple areas. */
public fun interface NpcPlayerHuntProfileOverride {
    /** Returns `null` when this provider does not own [npc]. */
    public fun resolve(npc: Npc): NpcPlayerHuntProfile?
}

/** A bounded player-hunt mode applied before an idle NPC searches for a target. */
public data class NpcPlayerHuntProfile(
    public val modeId: Int,
    public val range: Int,
    public val source: String,
)
