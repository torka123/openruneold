package org.rsmod.api.combat.commons.magic

import dev.openrune.types.NpcMode
import java.util.WeakHashMap
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Shared authority for lure ownership, recharge and the NPC's flee suppression. */
public object DarkLureState {
    public const val COOLDOWN: Int = 17
    public const val IMPLING_DURATION: Int = 10
    private val nextCast: AttributeKey<Int> = AttributeKey(resetOnDeath = true, temp = true)
    private val active: MutableMap<Npc, Lure> = WeakHashMap()

    public fun isImpling(npc: Npc): Boolean =
        npc.visType.name.endsWith(" impling", ignoreCase = true)

    public fun rejection(player: Player, npc: Npc, cycle: Int): String? {
        prune(cycle)
        return when {
            player.attr.getOrDefault(nextCast, 0) > cycle ->
                "You need to wait before casting Dark Lure again."
            npc.visType.name.contains("chompy", ignoreCase = true) ->
                "This spell has no effect on that creature."
            active[npc]?.let { it.impling && it.owner !== player } == true ->
                "That impling is already being lured by another player."
            else -> null
        }
    }

    public fun recordCast(player: Player, cycle: Int) {
        player.attr[nextCast] = cycle + COOLDOWN
    }

    public fun apply(player: Player, npc: Npc, cycle: Int) {
        prune(cycle)
        if (!npc.isSlotAssigned || !player.isSlotAssigned || player.pendingLogout ||
            player.hitpoints <= 0 || npc.coords.level != player.coords.level) return
        if (active[npc]?.let { it.impling && it.owner !== player } == true) return
        val impling = isImpling(npc)
        active[npc] = Lure(player, npc.uid.packed, cycle, impling, npc.mode)
        if (impling) npc.mode = NpcMode.None
        npc.faceSquare(player.coords)
        npc.walk(player.coords)
    }

    public fun preventsRetreat(npc: Npc): Boolean = active.containsKey(npc)

    public fun tick(player: Player, cycle: Int) {
        prune(cycle)
        for ((npc, lure) in active) {
            if (lure.owner !== player || !lure.impling) continue
            // Preserve the current corner when already inside the two-by-two following area.
            val x = npc.coords.x.coerceIn(player.coords.x, player.coords.x + 1)
            val z = npc.coords.z.coerceIn(player.coords.z, player.coords.z + 1)
            val destination = CoordGrid(x, z, player.coords.level)
            npc.mode = NpcMode.None
            npc.faceSquare(player.coords)
            if (npc.coords != destination) npc.walk(destination)
            else npc.routeDestination.clear()
        }
    }

    private fun prune(cycle: Int) {
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val (npc, lure) = iterator.next()
            val owner = lure.owner
            val expired =
                if (lure.impling) cycle >= lure.startedAt + IMPLING_DURATION
                else cycle >= lure.startedAt + IMPLING_DURATION &&
                    npc.vars["varn.lastcombat"] + 10 < cycle
            if (!npc.isSlotAssigned || npc.uid.packed != lure.uid || !owner.isSlotAssigned ||
                owner.pendingLogout || owner.hitpoints <= 0 || npc.hitpoints <= 0 ||
                npc.coords.level != owner.coords.level || npc.distanceTo(owner) > 25 || expired) {
                if (lure.impling && npc.isSlotAssigned && npc.uid.packed == lure.uid) {
                    npc.routeDestination.clear()
                    if (npc.mode == NpcMode.None) npc.mode = lure.previousMode
                }
                iterator.remove()
            }
        }
    }

    private data class Lure(
        val owner: Player,
        val uid: Int,
        val startedAt: Int,
        val impling: Boolean,
        val previousMode: NpcMode?,
    )
}
