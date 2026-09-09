package org.rsmod.content.bosses.royaltitans

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDropLocationHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

/** Uses the native unsafe-death pile policy; this is not a separate gravestone service. */
internal class RoyalTitansDeathDropLocation
@Inject
constructor(private val instances: InstanceManager) : PlayerDeathDropLocationHook {
    override fun dropLocation(context: PlayerDeathContext): CoordGrid? {
        val session = instances.sessionForPlayer(context.player) ?: return null
        if (session.key != ROYAL_TITANS_INSTANCE_KEY) return null
        val base = instances.localCoord(session, RegionLocal(0, 45, 149, 0, 0)) ?: return null
        val coords = context.coords
        if (
            coords.level != base.level ||
                coords.x !in base.x until base.x + 64 ||
                coords.z !in base.z until base.z + 64
        )
            return null
        return ROYAL_TITANS_OUTSIDE
    }
}
