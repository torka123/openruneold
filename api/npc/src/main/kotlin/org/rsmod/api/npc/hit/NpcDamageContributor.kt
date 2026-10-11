package org.rsmod.api.npc.hit

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitAttribution

public fun interface NpcDamageContributor {
    public fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int)

    /** Rich notifications preserve the existing SAM for consumers that only need totals. */
    public fun onPlayerDamageNpc(
        npc: Npc,
        source: Player?,
        damage: Int,
        attribution: HitAttribution,
    ) {
        if (source != null) onPlayerDamageNpc(npc, source, damage)
    }
}
