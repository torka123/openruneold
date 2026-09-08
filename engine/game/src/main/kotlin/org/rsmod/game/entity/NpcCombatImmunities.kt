package org.rsmod.game.entity

/**
 * Runtime combat immunities for one spawned NPC.
 *
 * These flags deliberately live on the NPC instance rather than its cache type: encounters may use
 * the same cache NPC for modes with different rules. All flags default to `false`, so merely
 * introducing this policy does not alter existing NPC behaviour.
 */
public class NpcCombatImmunities {
    /** Rejects both poison and venom application. */
    public var toxins: Boolean = false

    /** Rejects reductions to attack, strength, defence, ranged, and magic levels. */
    public var statDrain: Boolean = false

    /** Suppresses task-only accuracy and damage bonuses without changing Slayer task progress. */
    public var slayerTaskBonuses: Boolean = false
}
