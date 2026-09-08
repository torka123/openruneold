package org.rsmod.api.combat.manager

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Content hook for mechanics which allow one immediate melee attack against a specific NPC. */
public fun interface PlayerNpcMeleeAttackDelayOverrideHook {
    /**
     * Returns `true` when this melee attempt may ignore the player's current attack delay.
     *
     * This hook is evaluated only from the melee attack pipeline. Implementations may consume a
     * one-shot encounter allowance when called.
     */
    public fun bypass(source: Player, target: Npc): Boolean
}
