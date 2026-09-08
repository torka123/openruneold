package org.rsmod.content.minigames.barrows

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BarrowsWorldTopologyTest {
    @Test
    fun everyRealSurfaceMoundMapsToADistinctUndergroundCrypt() {
        val brothers = BarrowsBrother.entries

        assertEquals(6, brothers.size)
        assertEquals(6, brothers.map { it.moundCenter }.toSet().size)
        assertEquals(6, brothers.map { it.cryptEntryCoords }.toSet().size)
        brothers.forEach { brother ->
            assertTrue(brother.containsSurfaceMound(brother.moundCenter))
            assertEquals(0, brother.moundCenter.level)
            assertEquals(3, brother.cryptEntryCoords.level)
            assertTrue(brother.cryptEntryCoords.x in 3520..3583)
            assertTrue(brother.cryptEntryCoords.z in 9664..9727)
        }
    }

    @Test
    fun allSixBrothersAreReachableAsThePerRunTunnelAssignment() {
        val tunnelAssignments =
            (0 until BarrowsBrother.entries.size).map(BarrowsBrother.entries::get)

        assertEquals(BarrowsBrother.entries.toSet(), tunnelAssignments.toSet())
        assertEquals(6, tunnelAssignments.distinct().size)
    }
    @Test
    fun tunnelEntryIsTheRequestedDungeonCoordinate() {
        assertEquals(3568, BARROWS_TUNNEL_ENTRY.x)
        assertEquals(9695, BARROWS_TUNNEL_ENTRY.z)
        assertEquals(0, BARROWS_TUNNEL_ENTRY.level)
    }

    @Test
    fun tunnelCryptMovesDirectlyIntoTheChestRoom() {
        assertEquals(3551, BARROWS_CHEST_ROOM_ENTRY.x)
        assertEquals(9694, BARROWS_CHEST_ROOM_ENTRY.z)
        assertEquals(0, BARROWS_CHEST_ROOM_ENTRY.level)
    }

}
