package org.rsmod.content.minigames.corruptedgauntlet

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

internal object CorruptedGauntletInstanceLayout {
    private const val SOURCE_LEVEL = 1

    fun area(layout: CorruptedGauntletLayout): InstanceArea.Template {
        val template =
            RegionTemplate.createLarge {
                layout.rooms.forEach { room ->
                    for (destinationX in 0..1) {
                        for (destinationZ in 0..1) {
                            val sourceOffset =
                                sourceOffset(destinationX, destinationZ, room.rotation)
                            val source =
                                ZoneKey(
                                    room.sourceChunkX + sourceOffset.first,
                                    room.sourceChunkZ + sourceOffset.second,
                                    SOURCE_LEVEL,
                                )
                            val targetX = room.grid.x * 2 + destinationX
                            val targetZ = room.grid.z * 2 + destinationZ
                            when (room.rotation) {
                                0 -> this[targetX, targetZ, SOURCE_LEVEL] = source
                                1 -> this[targetX, targetZ, SOURCE_LEVEL] = source.rotate90()
                                2 -> this[targetX, targetZ, SOURCE_LEVEL] = source.rotate180()
                                3 -> this[targetX, targetZ, SOURCE_LEVEL] = source.rotate270()
                                else ->
                                    error("Invalid Corrupted Gauntlet rotation ${room.rotation}")
                            }
                        }
                    }
                }
            }
        return InstanceArea.template(template, sourceEntry(), publicExit())
    }

    fun originFromEntry(layout: CorruptedGauntletLayout, entry: CoordGrid): CoordGrid {
        val room = layout[layout.start]
        val offset = rotateTile(5, 5, room.rotation)
        return entry.translate(
            -(layout.start.x * CorruptedGauntletConfig.ROOM_SIZE + offset.first),
            -(layout.start.z * CorruptedGauntletConfig.ROOM_SIZE + offset.second),
        )
    }

    fun roomBase(origin: CoordGrid, point: CorruptedGauntletGridPoint): CoordGrid =
        origin.translate(
            point.x * CorruptedGauntletConfig.ROOM_SIZE,
            point.z * CorruptedGauntletConfig.ROOM_SIZE,
        )

    fun roomAt(origin: CoordGrid, tile: CoordGrid): CorruptedGauntletGridPoint? {
        if (tile.level != origin.level) return null
        val localX = tile.x - origin.x
        val localZ = tile.z - origin.z
        if (
            localX !in
                0 until CorruptedGauntletConfig.GRID_SIZE * CorruptedGauntletConfig.ROOM_SIZE ||
                localZ !in
                    0 until CorruptedGauntletConfig.GRID_SIZE * CorruptedGauntletConfig.ROOM_SIZE
        ) {
            return null
        }
        return CorruptedGauntletGridPoint(
            localX / CorruptedGauntletConfig.ROOM_SIZE,
            localZ / CorruptedGauntletConfig.ROOM_SIZE,
        )
    }

    fun bossSpawn(origin: CoordGrid): CoordGrid =
        roomBase(origin, CorruptedGauntletGridPoint(3, 3)).translate(6, 7)

    fun bossEntry(origin: CoordGrid): CoordGrid =
        roomBase(origin, CorruptedGauntletGridPoint(3, 3)).translate(2, 2)

    private fun sourceEntry(): RegionLocal {
        val sourceX = CorruptedGauntletConfig.SourceChunks.START_X * 8 + 5
        val sourceZ = CorruptedGauntletConfig.SourceChunks.START_Z * 8 + 5
        return RegionLocal(
            level = SOURCE_LEVEL,
            regionZoneX = sourceX / 64,
            regionZoneZ = sourceZ / 64,
            localX = sourceX % 64,
            localZ = sourceZ % 64,
        )
    }

    private fun publicExit(): CoordGrid =
        CoordGrid(
            CorruptedGauntletConfig.LOBBY_X,
            CorruptedGauntletConfig.LOBBY_Z,
            CorruptedGauntletConfig.LOBBY_LEVEL,
        )

    private fun sourceOffset(destinationX: Int, destinationZ: Int, rotation: Int): Pair<Int, Int> =
        when (rotation) {
            0 -> destinationX to destinationZ
            1 -> (1 - destinationZ) to destinationX
            2 -> (1 - destinationX) to (1 - destinationZ)
            3 -> destinationZ to (1 - destinationX)
            else -> error("Invalid rotation $rotation")
        }

    private fun rotateTile(x: Int, z: Int, rotation: Int): Pair<Int, Int> =
        when (rotation) {
            0 -> x to z
            1 -> z to (15 - x)
            2 -> (15 - x) to (15 - z)
            3 -> (15 - z) to x
            else -> error("Invalid rotation $rotation")
        }
}
