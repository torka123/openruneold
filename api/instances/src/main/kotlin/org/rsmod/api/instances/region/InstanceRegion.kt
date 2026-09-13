package org.rsmod.api.instances.region

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.worldCoord
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

internal fun Region.resolveLocal(local: RegionLocal, area: InstanceArea) =
    when (area) {
        is InstanceArea.Fixed -> local.worldCoord()
        is InstanceArea.Template,
        is InstanceArea.CopyRegions ->
            normal[local.level, local.regionZoneX, local.regionZoneZ, local.localX, local.localZ]
    }

internal fun InstanceSession.enterCoord(region: Region?): CoordGrid =
    localCoord(region, placement.enterCoord)

internal fun InstanceSession.localCoord(region: Region?, local: RegionLocal): CoordGrid =
    when (placement) {
        is InstancePlacement.Fixed -> local.worldCoord()
        is InstancePlacement.Allocated -> checkNotNull(region).resolveLocal(local, spec.area)
    }
