package org.rsmod.api.instances.region

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.map.CoordGrid

sealed class InstancePlacement {
    abstract val enterCoord: RegionLocal
    abstract val exitCoord: CoordGrid
    abstract val npcSpawns: List<InstanceNpc>

    data class Allocated(
        override val enterCoord: RegionLocal,
        override val exitCoord: CoordGrid,
        val regionTemplate: RegionStaticTemplate,
        override val npcSpawns: List<InstanceNpc>,
        val instanceLevel: Int,
        val baseAreaKey: BaseAreaKey,
    ) : InstancePlacement()

    data class Fixed(val area: InstanceArea.Fixed) : InstancePlacement() {
        override val enterCoord: RegionLocal
            get() = area.enterCoord

        override val exitCoord: CoordGrid
            get() = area.exitCoord

        override val npcSpawns: List<InstanceNpc>
            get() = area.npcSpawns
    }
}

data class BaseAreaKey(val zoneX: Int, val zoneZ: Int, val baseLevel: Int)
