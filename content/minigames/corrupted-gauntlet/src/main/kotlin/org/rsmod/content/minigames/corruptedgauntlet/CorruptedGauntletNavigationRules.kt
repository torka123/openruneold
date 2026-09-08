package org.rsmod.content.minigames.corruptedgauntlet

import org.rsmod.game.loc.LocAngle

internal object CorruptedGauntletNavigationRules {
    fun destination(
        current: CorruptedGauntletGridPoint,
        nodeId: Int,
        angle: LocAngle,
    ): CorruptedGauntletGridPoint? {
        val direction =
            if (nodeId == CorruptedGauntletConfig.Loc.UNLIT_NODE_RIGHT) angle.turn(2) else angle
        val (xOffset, zOffset) =
            when (direction) {
                LocAngle.West -> -1 to 0
                LocAngle.North -> 0 to 1
                LocAngle.East -> 1 to 0
                LocAngle.South -> 0 to -1
            }
        val x = current.x + xOffset
        val z = current.z + zOffset
        if (x !in 0 until CorruptedGauntletConfig.GRID_SIZE) return null
        if (z !in 0 until CorruptedGauntletConfig.GRID_SIZE) return null
        return CorruptedGauntletGridPoint(x, z)
    }
}
