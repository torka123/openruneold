package org.rsmod.api.combat.scripts

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Supplies an alternative AP distance for melee targets whose map boundary intentionally prevents
 * the normal wall-aware rectangle touch check.
 *
 * Returning `null` preserves the standard melee interaction path exactly.
 */
public fun interface NpcMeleeApRangeHook {
    public fun range(player: Player, npc: Npc): Int?
}
