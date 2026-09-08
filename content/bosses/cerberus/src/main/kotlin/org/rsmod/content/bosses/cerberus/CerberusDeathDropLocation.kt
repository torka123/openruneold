package org.rsmod.content.bosses.cerberus

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDropLocationHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.map.CoordGrid

internal class CerberusDeathDropLocation @Inject constructor(
    private val instances: InstanceManager,
    private val arena: CerberusArena,
) : PlayerDeathDropLocationHook {
    override fun dropLocation(context: PlayerDeathContext): CoordGrid? {
        val session = instances.sessionForPlayer(context.player) ?: return null
        if (session.key != CERBERUS_INSTANCE_KEY || !arena.contains(session, context.coords)) return null
        return CERBERUS_PUBLIC_EXIT
    }
}
