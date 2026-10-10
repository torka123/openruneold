package org.rsmod.content.minigames.toa

import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.map.CoordGrid

/** Minimal native session for owned-queue tests that do not exercise cache-backed manager code. */
internal fun ownedQueueTestSession(id: Long = 1): InstanceSession {
    val area =
        InstanceArea.Fixed(
            southWest = CoordGrid(3200, 3200),
            northEast = CoordGrid(3210, 3210),
            enterCoord = RegionLocal(level = 0, regionZoneX = 50, regionZoneZ = 50, 1, 1),
            exitCoord = CoordGrid(3200, 3200),
        )
    val spec =
        InstanceSpec(
            fee = 0,
            maxPlayers = 1,
            reclaimTicks = 1,
            graceTicks = 1,
            area = area,
            settingsRowId = 0,
        )
    return InstanceSession(
        id = InstanceId(id),
        regionIds = mutableSetOf(),
        owner = id,
        key = "toa-owned-queue-test-$id",
        spec = spec,
        placement = InstancePlacement.Fixed(area),
        access = InstanceAccess.Private,
    )
}
