package org.rsmod.content.drops

import java.util.Collections
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/**
 * Concrete in-memory outcome from [NpcRewardDeliveryService.prepare], including an empty outcome.
 *
 * Contains no live recipient, NPC, drop callbacks or cache definitions. Item variables already
 * include native monster-drop provenance. This value is not a durable entitlement or delivery
 * receipt: persistence, recipient identity and native ground/pickup recovery remain separate.
 */
public class PreparedNpcReward
internal constructor(
    public val sourceNpcId: Int,
    public val sourceNpcKey: String,
    public val coords: CoordGrid,
    public val duration: Int,
    items: List<Item>,
) {
    /** Ordered allocation lines; duplicates are intentional and are not merged. */
    public val items: List<Item> = Collections.unmodifiableList(ArrayList(items))

    public data class Item(public val ordinal: Int, public val obj: InvObj)
}
