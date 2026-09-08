package org.rsmod.content.skills.construction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

class ConstructionThroneRoomTest {
    @Test
    fun `each throne floor choice resolves to its own lever result`() {
        assertNull(ThroneFloorTrap.FLOOR_DECORATION.activatedLocId)
        assertEquals(13681, ThroneFloorTrap.fromBuildable("STEEL_CAGE_TRAP")?.activatedLocId)
        assertEquals(6521, ThroneFloorTrap.fromBuildable("TRAP_TRAPDOOR")?.activatedLocId)
        assertEquals(13682, ThroneFloorTrap.fromBuildable("LESSER_MAGIC_CAGE")?.activatedLocId)
        assertEquals(13683, ThroneFloorTrap.fromBuildable("GREATER_MAGIC_CAGE")?.activatedLocId)
    }

    @Test
    fun `steel cage surrounds the exact central floor square with room rotation`() {
        val tiles =
            listOf(
                CoordGrid(100, 200, 1),
                CoordGrid(101, 200, 1),
                CoordGrid(100, 201, 1),
                CoordGrid(101, 201, 1),
            )
        val pieces = ThroneRoomTrapLayout.steelCagePieces(tiles, roomRotation = 1)
        assertEquals(tiles.toSet(), pieces.map(LocInfo::coords).toSet())
        assertEquals(
            listOf(LocAngle.North, LocAngle.East, LocAngle.South, LocAngle.West),
            pieces.map(LocInfo::angle),
        )
    }

    @Test
    fun `magic cage uses the south west anchor of the central square`() {
        val tiles =
            listOf(
                CoordGrid(101, 201, 1),
                CoordGrid(100, 200, 1),
                CoordGrid(101, 200, 1),
                CoordGrid(100, 201, 1),
            )
        val cage = ThroneRoomTrapLayout.magicCagePiece(tiles, ThroneFloorTrap.GREATER_MAGIC_CAGE)
        assertEquals(13683, cage.id)
        assertEquals(CoordGrid(100, 200, 1), cage.coords)
    }
}
