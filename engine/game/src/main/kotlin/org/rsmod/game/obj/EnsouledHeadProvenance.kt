package org.rsmod.game.obj

import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/** Original monster-drop location, persisted in the otherwise unused vars of fresh heads. */
public object EnsouledHeadProvenance {
    public val freshIds: Set<Int> = ((13447..13510 step 3).toList() + 26996).toSet()

    public fun isFresh(id: Int): Boolean = id in freshIds

    public fun isHead(id: Int): Boolean = isFresh(id) || isFresh(id - 1)

    public fun fromMonsterDrop(item: InvObj, coords: CoordGrid): InvObj {
        val freshId = when {
            isFresh(item.id) -> item.id
            isFresh(item.id - 1) -> item.id - 1
            else -> return item
        }
        return item.copy(id = freshId, vars = coords.packed + 1)
    }

    public fun origin(id: Int, vars: Int): CoordGrid? =
        if (isFresh(id) && vars != 0) CoordGrid(vars - 1) else null

    public fun canReanimateAt(id: Int, vars: Int, coords: CoordGrid): Boolean {
        val origin = origin(id, vars) ?: return false
        return origin.level == coords.level &&
            kotlin.math.abs(origin.x - coords.x) <= 15 &&
            kotlin.math.abs(origin.z - coords.z) <= 15
    }

    /** Banking or exposing a head to an economic container permanently removes local eligibility. */
    public fun normalizeStored(item: InvObj): InvObj =
        if (isFresh(item.id)) item.copy(id = item.id + 1, vars = 0) else item
}
