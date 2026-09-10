package org.rsmod.content.bosses.cerberus

import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.map.CoordGrid

/** Resolves the owning allocation before interpreting any source-room coordinates. */
internal class CerberusArena
@Inject
constructor(private val regions: RegionRegistry, private val instances: InstanceManager) {
    fun localCoord(session: InstanceSession, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session,
                RegionLocal(0, CERBERUS_SOURCE_MAP_X, CERBERUS_SOURCE_MAP_Z, localX, localZ),
            )
        )

    fun sourceCoords(session: InstanceSession, coords: CoordGrid): CoordGrid? {
        // regionIds contains allocation UIDs, not OSRS map-square IDs.
        val region = regions[coords] ?: return null
        if (region.uid !in session.regionIds) return null
        val source = regions.normalizeCoords(coords)
        return source.takeIf {
            it != CoordGrid.NULL &&
                it.level == 0 &&
                it.mx == CERBERUS_SOURCE_MAP_X &&
                it.mz == CERBERUS_SOURCE_MAP_Z
        }
    }

    fun contains(session: InstanceSession, coords: CoordGrid): Boolean =
        sourceCoords(session, coords) != null
}
