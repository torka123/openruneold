package org.rsmod.content.skills.construction

internal data class HouseViewerCell(val x: Int, val z: Int, val level: Int)

internal object HouseViewerLayout {
    const val ROOM_GRID_SIZE = 8
    private const val CLIENT_GRID_SIZE = 9
    private const val CLIENT_FLOOR_CELLS = CLIENT_GRID_SIZE * CLIENT_GRID_SIZE

    private data class ViewerChoiceSegment(val word: Int, val wordBit: Int, val width: Int)

    /*
     * Revision-240 client script 1376 splits the first four furniture choices across all three
     * integer arguments and the remaining five across the first two.
     */
    private val viewerChoiceSegments =
        listOf(
            listOf(
                ViewerChoiceSegment(0, 15, 3),
                ViewerChoiceSegment(1, 0, 2),
                ViewerChoiceSegment(2, 0, 3),
            ),
            listOf(
                ViewerChoiceSegment(0, 18, 3),
                ViewerChoiceSegment(1, 2, 2),
                ViewerChoiceSegment(2, 3, 3),
            ),
            listOf(
                ViewerChoiceSegment(0, 21, 3),
                ViewerChoiceSegment(1, 4, 2),
                ViewerChoiceSegment(2, 6, 3),
            ),
            listOf(
                ViewerChoiceSegment(0, 24, 2),
                ViewerChoiceSegment(1, 6, 3),
                ViewerChoiceSegment(2, 9, 2),
            ),
            listOf(ViewerChoiceSegment(0, 26, 2), ViewerChoiceSegment(1, 9, 3)),
            listOf(ViewerChoiceSegment(0, 28, 2), ViewerChoiceSegment(1, 12, 3)),
            listOf(ViewerChoiceSegment(0, 30, 2), ViewerChoiceSegment(1, 15, 3)),
            listOf(ViewerChoiceSegment(1, 18, 5)),
            listOf(ViewerChoiceSegment(1, 23, 5)),
        )

    fun decodeCell(comsub: Int): HouseViewerCell? {
        if (comsub !in 0 until CLIENT_FLOOR_CELLS * 3) return null
        val level = comsub / CLIENT_FLOOR_CELLS
        val local = comsub % CLIENT_FLOOR_CELLS
        val x = local % CLIENT_GRID_SIZE
        val z = local / CLIENT_GRID_SIZE
        if (x !in 0 until ROOM_GRID_SIZE || z !in 0 until ROOM_GRID_SIZE) return null
        return HouseViewerCell(x, z, level)
    }

    fun packCoord(level: Int, x: Int, z: Int): Int = (level shl 28) or (x shl 14) or z

    /** Packs room furniture exactly as revision-240 client script 1376 decodes it. */
    fun packFurnitureChoices(choices: List<Int>): IntArray {
        val packed = IntArray(3)
        choices.take(viewerChoiceSegments.size).forEachIndexed { choiceIndex, choice ->
            var sourceBit = 0
            viewerChoiceSegments[choiceIndex].forEach { segment ->
                val mask = (1 shl segment.width) - 1
                val part = (choice ushr sourceBit) and mask
                packed[segment.word] = packed[segment.word] or (part shl segment.wordBit)
                sourceBit += segment.width
            }
        }
        return packed
    }

    /** Component slot used by the revision-240 House Viewer for a room's 9x9 map cell. */
    fun gridPos(x: Int, z: Int, level: Int): Int =
        ((z * CLIENT_GRID_SIZE) + 1 + x) + level * CLIENT_FLOOR_CELLS

    /**
     * The stock viewer addresses its room controls by slot rather than by map coordinate. Keep a
     * deterministic server-side slot order so a click always resolves to the same stable room
     * identity, including after rooms have been added, moved, or deleted in the working layout.
     */
    fun orderedRooms(rooms: List<HouseRoom>): List<HouseRoom> =
        rooms.sortedWith(
            compareBy<HouseRoom>({ it.level }, { it.zoneX }, { it.zoneZ }, { it.stableId })
        )

    fun rotateRooms(rooms: List<HouseRoom>, roomIds: Set<String>, delta: Int): List<HouseRoom> =
        rooms.map { room ->
            if (room.stableId in roomIds) {
                room.copy(rotation = Math.floorMod(room.rotation + delta, 4))
            } else {
                room
            }
        }

    fun moveRooms(
        rooms: List<HouseRoom>,
        roomIds: Set<String>,
        dx: Int,
        dz: Int,
        levelDelta: Int,
    ): List<HouseRoom> =
        rooms.map { room ->
            if (room.stableId in roomIds) {
                room.copy(
                    zoneX = room.zoneX + dx,
                    zoneZ = room.zoneZ + dz,
                    level = room.level + levelDelta,
                )
            } else {
                room.copy()
            }
        }

    fun deleteRooms(
        rooms: List<HouseRoom>,
        furniture: Map<String, String>,
        roomIds: Set<String>,
    ): HouseViewerDeletion {
        val retainedRooms = rooms.filterNot { it.stableId in roomIds }
        val retainedFurniture =
            furniture.filterKeys { key -> roomIds.none { id -> key.startsWith("$id:") } }
        return HouseViewerDeletion(retainedRooms, retainedFurniture)
    }

    fun rotateLocal(x: Int, z: Int, rotation: Int): Pair<Int, Int> =
        when (Math.floorMod(rotation, 4)) {
            0 -> x to z
            // Region-copy rotation 1 is (z, 7-x). Keep Viewer rebuilds identical to the
            // physical dynamic-zone transform used by both revision 240 and the donor.
            1 -> z to (ROOM_GRID_SIZE - 1 - x)
            2 -> (ROOM_GRID_SIZE - 1 - x) to (ROOM_GRID_SIZE - 1 - z)
            else -> (ROOM_GRID_SIZE - 1 - z) to x
        }

    fun unrotateLocal(x: Int, z: Int, rotation: Int): Pair<Int, Int> =
        rotateLocal(x, z, 4 - Math.floorMod(rotation, 4))

    fun remapLocal(localX: Int, localZ: Int, oldRotation: Int, newRotation: Int): Pair<Int, Int> {
        val canonical = unrotateLocal(localX, localZ, oldRotation)
        return rotateLocal(canonical.first, canonical.second, newRotation)
    }

    fun linkedStack(
        rooms: List<HouseRoom>,
        selected: HouseRoom,
        hasVerticalConnector: (HouseRoom) -> Boolean,
    ): Set<String> {
        if (!hasVerticalConnector(selected)) return setOf(selected.stableId)
        val byLevel =
            rooms
                .filter { it.zoneX == selected.zoneX && it.zoneZ == selected.zoneZ }
                .associateBy(HouseRoom::level)
        val linked = mutableSetOf(selected.stableId)
        var level = selected.level - 1
        while (true) {
            val room = byLevel[level] ?: break
            if (!hasVerticalConnector(room)) break
            linked += room.stableId
            level--
        }
        level = selected.level + 1
        while (true) {
            val room = byLevel[level] ?: break
            if (!hasVerticalConnector(room)) break
            linked += room.stableId
            level++
        }
        return linked
    }

    fun connectedRoomIds(
        rooms: List<HouseRoom>,
        hasVerticalConnector: (HouseRoom) -> Boolean,
    ): Set<String> {
        val entrance =
            rooms.singleOrNull { it.room == "GARDEN" && it.level == 1 } ?: return emptySet()
        val visited = mutableSetOf(entrance.stableId)
        val queue = ArrayDeque<HouseRoom>()
        queue += entrance
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            rooms.forEach { candidate ->
                if (candidate.stableId in visited) return@forEach
                val horizontal =
                    candidate.level == current.level &&
                        kotlin.math.abs(candidate.zoneX - current.zoneX) +
                            kotlin.math.abs(candidate.zoneZ - current.zoneZ) == 1
                val vertical =
                    candidate.zoneX == current.zoneX &&
                        candidate.zoneZ == current.zoneZ &&
                        kotlin.math.abs(candidate.level - current.level) == 1 &&
                        hasVerticalConnector(current) &&
                        hasVerticalConnector(candidate)
                if (horizontal || vertical) {
                    visited += candidate.stableId
                    queue += candidate
                }
            }
        }
        return visited
    }
}

internal data class HouseViewerDeletion(
    val rooms: List<HouseRoom>,
    val furniture: Map<String, String>,
)
