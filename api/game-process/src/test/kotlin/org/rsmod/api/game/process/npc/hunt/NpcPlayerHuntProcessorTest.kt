package org.rsmod.api.game.process.npc.hunt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.vars.VarPlayerIntMap

class NpcPlayerHuntProcessorTest {
    @Test
    fun `server-only last-combat varp is read without a cache definition`() {
        val vars = VarPlayerIntMap()
        vars.backing.put(LAST_COMBAT_VARP, 417)

        assertEquals(417, vars.huntVarp(LAST_COMBAT_VARP))
    }

    @Test
    fun `unset hunt varp retains the normal zero default`() {
        assertEquals(0, VarPlayerIntMap().huntVarp(LAST_COMBAT_VARP))
    }

    @Test
    fun `high combat player remains huntable in Wilderness`() {
        assertEquals(false, blockedByOutsideWildernessTolerance(7, 126, playerInWilderness = true))
    }

    @Test
    fun `outside Wilderness combat tolerance remains unchanged`() {
        assertEquals(true, blockedByOutsideWildernessTolerance(7, 126, playerInWilderness = false))
        assertEquals(
            false,
            blockedByOutsideWildernessTolerance(70, 126, playerInWilderness = false),
        )
    }

    private companion object {
        const val LAST_COMBAT_VARP = 65522
    }
}
