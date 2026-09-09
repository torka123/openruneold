package org.rsmod.content.bosses.alchemicalhydra

import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

/** Source-template geometry must be mapped; a nine-zone template is not region-aligned. */
internal fun InstanceManager.hydraCoord(session: InstanceSession, coord: RegionLocal): CoordGrid =
    requireNotNull(localCoord(session, coord)) { "Hydra local coordinate is unavailable: $coord" }

internal fun InstanceManager.hydraTile(run: HydraArenaState, x: Int, z: Int): CoordGrid {
    val southWest =
        hydraCoord(
            run.session,
            RegionLocal(
                0,
                HYDRA_SOURCE_MAP_X,
                HYDRA_SOURCE_MAP_Z,
                AlchemicalHydraRules.CHAMBER_MIN_X,
                AlchemicalHydraRules.CHAMBER_MIN_Z,
            ),
        )
    val northEast =
        hydraCoord(
            run.session,
            RegionLocal(
                0,
                HYDRA_SOURCE_MAP_X,
                HYDRA_SOURCE_MAP_Z,
                AlchemicalHydraRules.CHAMBER_MAX_X,
                AlchemicalHydraRules.CHAMBER_MAX_Z,
            ),
        )
    return CoordGrid(
        x.coerceIn(minOf(southWest.x, northEast.x), maxOf(southWest.x, northEast.x)),
        z.coerceIn(minOf(southWest.z, northEast.z), maxOf(southWest.z, northEast.z)),
        southWest.level,
    )
}

internal fun InstanceManager.inHydraChamber(run: HydraArenaState): Boolean {
    val tile = run.player.coords
    val origin =
        hydraCoord(run.session, RegionLocal(0, HYDRA_SOURCE_MAP_X, HYDRA_SOURCE_MAP_Z, 0, 0))
    return tile.level == origin.level &&
        AlchemicalHydraRules.inChamber(tile.x - origin.x, tile.z - origin.z)
}
