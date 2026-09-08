package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ToaInteractionCacheTest {
    @Test
    fun `revision 240 barrier exposes pass and quick pass`() {
        ToaCacheTestSupport.initRevision240()
        val barrier = assertNotNull(ServerCacheManager.getObject(ToaLocs.CHALLENGE_BARRIER))
        assertEquals("Barrier", barrier.name)
        assertEquals("Pass", barrier.actions.getOpOrNull(0))
        assertEquals("Quick-Pass", barrier.actions.getOpOrNull(1))
    }

    @Test
    fun `revision 240 raid and room exits expose leave`() {
        ToaCacheTestSupport.initRevision240()
        val ids = ToaLocs.leaveRaid + ToaLocs.returnToLobby
        ids.forEach { id ->
            val exit = assertNotNull(ServerCacheManager.getObject(id), "loc $id")
            assertEquals("Exit", exit.name, "loc $id")
            assertEquals("Leave", exit.actions.getOpOrNull(0), "loc $id")
        }
    }

    @Test
    fun `revision 240 Wardens crystal exposes use as its primary action`() {
        ToaCacheTestSupport.initRevision240()
        val crystal = assertNotNull(ServerCacheManager.getObject(ToaLocs.WARDENS_CRYSTAL))
        assertEquals("Teleport crystal", crystal.name)
        assertEquals("Use", crystal.actions.getOpOrNull(0))
    }
}
