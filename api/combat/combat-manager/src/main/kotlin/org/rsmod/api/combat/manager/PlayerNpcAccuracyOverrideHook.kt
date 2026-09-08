package org.rsmod.api.combat.manager

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public enum class PlayerNpcAccuracyStyle {
    Melee,
    Ranged,
    Magic,
}

/** Content hook for encounters which explicitly guarantee a player's accuracy against an NPC. */
public fun interface PlayerNpcAccuracyOverrideHook {
    public fun forceAccurate(source: Player, target: Npc, style: PlayerNpcAccuracyStyle): Boolean
}
