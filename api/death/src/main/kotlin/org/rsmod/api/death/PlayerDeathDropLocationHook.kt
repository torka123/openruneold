package org.rsmod.api.death

import org.rsmod.map.CoordGrid

/** Redirects normal death piles out of disposable instances without changing item selection. */
public fun interface PlayerDeathDropLocationHook {
    public fun dropLocation(context: PlayerDeathContext): CoordGrid?
}
