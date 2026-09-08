package org.rsmod.content.bosses.doom

import org.rsmod.map.CoordGrid

/** Pure geometry for the capture-confirmed 3x3 moving earth shield. */
internal object DoomVolatileEarthRules {
    const val SHIELD_SIZE: Int = 3

    /** NPC coordinates are south-west based; the selected volatile tile is the shield centre. */
    fun shieldSouthWest(selectedCentre: CoordGrid): CoordGrid =
        selectedCentre.translate(-(SHIELD_SIZE / 2), -(SHIELD_SIZE / 2))

    /** A queued second hit on the already selected node must not count as the second node. */
    fun isDistinctSelection(firstCentre: CoordGrid, selectedCentre: CoordGrid): Boolean =
        firstCentre != selectedCentre
}
