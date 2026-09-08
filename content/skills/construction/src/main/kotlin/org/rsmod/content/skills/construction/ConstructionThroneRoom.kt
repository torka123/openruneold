package org.rsmod.content.skills.construction

import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.loc.LocLayerConstants

internal enum class ThroneFloorTrap(val buildableName: String, val activatedLocId: Int?) {
    FLOOR_DECORATION("FLOOR_DECORATION", null),
    STEEL_CAGE("STEEL_CAGE_TRAP", 13681),
    PITFALL("TRAP_TRAPDOOR", 6521),
    LESSER_MAGIC_CAGE("LESSER_MAGIC_CAGE", 13682),
    GREATER_MAGIC_CAGE("GREATER_MAGIC_CAGE", 13683);

    companion object {
        fun fromBuildable(name: String): ThroneFloorTrap? =
            entries.firstOrNull { it.buildableName == name }
    }
}

internal object ThroneRoomTrapLayout {
    val leverLocIds: Set<Int> = setOf(13674, 13672, 13673)
    val cageLocIds: Set<Int> = setOf(13681, 13682, 13683)
    val requiredLocIds: Set<Int> =
        leverLocIds + cageLocIds + ThroneFloorTrap.PITFALL.activatedLocId!!

    fun orderedFloorSquare(tiles: Collection<CoordGrid>): List<CoordGrid> {
        val unique = tiles.distinct()
        require(unique.size == 4) { "A throne trap requires four distinct floor tiles." }
        val minX = unique.minOf(CoordGrid::x)
        val maxX = unique.maxOf(CoordGrid::x)
        val minZ = unique.minOf(CoordGrid::z)
        val maxZ = unique.maxOf(CoordGrid::z)
        require(maxX - minX == 1 && maxZ - minZ == 1) {
            "A throne trap must occupy one contiguous 2x2 floor square."
        }
        val level = unique.first().level
        require(unique.all { it.level == level }) { "A throne trap cannot span planes." }
        val ordered =
            listOf(
                CoordGrid(maxX, minZ, level),
                CoordGrid(minX, minZ, level),
                CoordGrid(minX, maxZ, level),
                CoordGrid(maxX, maxZ, level),
            )
        require(unique.toSet() == ordered.toSet()) {
            "The throne trap floor locations do not form the expected 2x2 square."
        }
        return ordered
    }

    fun steelCagePieces(tiles: Collection<CoordGrid>, roomRotation: Int): List<LocInfo> =
        orderedFloorSquare(tiles).mapIndexed { index, tile ->
            centrepiece(ThroneFloorTrap.STEEL_CAGE.activatedLocId!!, tile, index + roomRotation)
        }

    fun magicCagePiece(tiles: Collection<CoordGrid>, trap: ThroneFloorTrap): LocInfo {
        require(
            trap == ThroneFloorTrap.LESSER_MAGIC_CAGE || trap == ThroneFloorTrap.GREATER_MAGIC_CAGE
        )
        val anchor = orderedFloorSquare(tiles)[1]
        return centrepiece(trap.activatedLocId!!, anchor, 0)
    }

    private fun centrepiece(objectId: Int, tile: CoordGrid, rotations: Int): LocInfo {
        val shape = LocShape.CentrepieceStraight
        val angle = LocAngle[rotations and 3]
        return LocInfo(
            layer = LocLayerConstants.of(shape.id),
            coords = tile,
            entity = LocEntity(objectId, shape.id, angle.id),
        )
    }
}
