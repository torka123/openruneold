package org.rsmod.content.bosses.wilderness

import org.rsmod.map.CoordGrid

internal data class WildernessBossSpawn(
    val internalName: String,
    val coords: CoordGrid,
    val room: WildernessBossRoom,
)

internal val WILDERNESS_BOSS_SPAWNS =
    listOf(
        WildernessBossSpawn("npc.callisto_singles", CoordGrid(1759, 11536), ARTIO_ROOM),
        WildernessBossSpawn("npc.venenatis_singles", CoordGrid(1630, 11544, 2), SPINDEL_ROOM),
        WildernessBossSpawn("npc.vetion_single", CoordGrid(1887, 11546, 1), CALVARION_ROOM),
        WildernessBossSpawn("npc.callisto", CoordGrid(3358, 10320), CALLISTO_ROOM),
        WildernessBossSpawn("npc.venenatis", CoordGrid(3422, 10199, 2), VENENATIS_ROOM),
        WildernessBossSpawn("npc.vetion", CoordGrid(3296, 10201, 1), VETION_ROOM),
    )
