package org.rsmod.api.player.hit.modifier

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/** Shared state for Vesta's spear's eight-cycle Spear Wall melee immunity. */
public object VestaSpearMeleeImmunity {
    public const val DURATION_CYCLES: Int = 8

    /** Refreshes the full immunity duration whenever Spear Wall is activated. */
    public fun activate(player: Player) {
        player.vestaSpearMeleeImmuneUntil = player.currentMapClock + DURATION_CYCLES
    }

    /** Returns whether an incoming hit of [type] is fully blocked by the active effect. */
    public fun blocks(player: Player, type: HitType): Boolean =
        type == HitType.Melee && player.currentMapClock < player.vestaSpearMeleeImmuneUntil
}
