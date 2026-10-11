package org.rsmod.api.npc.hit

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Observes a committed player attack, including misses, before its delayed hit lands. */
public fun interface NpcAttackLaunchHook {
    public fun onAttackLaunched(player: Player, npc: Npc)
}
