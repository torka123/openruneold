package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpact

/** Called after a queued player-versus-player hit has resolved to its final dealt damage. */
public fun interface PvPPlayerImpactHook {
    public fun onPlayerImpact(attacker: Player, target: Player, impact: HitImpact)
}
