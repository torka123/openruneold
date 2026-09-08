package org.rsmod.api.game.process.npc

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class NpcMovementGuardTest {
    @Test
    fun `main cycle preserves a scripted step performed by an earlier npc`() {
        val start = CoordGrid(0, 1, 1, 0, 0)
        val destination = start.translateX(1)
        val npc = Npc(NpcServerType(id = 1, name = "rolling boulder"), start)
        npc.previousCoords = start
        npc.coords = destination
        npc.lastMovement = 42

        npc.beginMainCycle(42)

        assertEquals(start, npc.previousCoords)
        assertEquals(destination, npc.coords)
    }

    @Test
    fun `main cycle snapshots stationary npc coordinates normally`() {
        val old = CoordGrid(0, 1, 1, 0, 0)
        val current = old.translateX(1)
        val npc = Npc(NpcServerType(id = 1, name = "stationary npc"), current)
        npc.previousCoords = old
        npc.lastMovement = 41

        npc.beginMainCycle(42)

        assertEquals(current, npc.previousCoords)
    }

    @Test
    fun `removed npc discards a route queued later in the same cycle`() {
        val start = CoordGrid(0, 1, 1, 0, 0)
        val npc = Npc(NpcServerType(id = 1, name = "removed shadow"), start)
        check(!npc.isSlotAssigned)
        npc.walk(start.translateZ(1))

        val discarded = npc.discardLateRouteIfRemoved()

        assertTrue(discarded)
        assertEquals(null, npc.routeRequest)
        assertTrue(npc.routeDestination.isEmpty())
        assertEquals(start, npc.coords)
    }
}
