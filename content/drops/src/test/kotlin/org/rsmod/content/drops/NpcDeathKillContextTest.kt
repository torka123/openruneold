package org.rsmod.content.drops

import dev.openrune.types.NpcServerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class NpcDeathKillContextTest {
    @Test
    fun `custom drop coordinate remains distinct from the npc tile`() {
        val npcCoords = CoordGrid(0, 50, 50, 0, 0)
        val requestedDropCoords = npcCoords.translateX(4)
        val npc = Npc(NpcServerType(id = 1, name = "drop coordinate test npc"), npcCoords)
        val context =
            NpcDeathKillContext(
                hero = Player(),
                npc = npc,
                lootTrackerEventId = 7,
                dropCoords = requestedDropCoords,
            )

        assertEquals(requestedDropCoords, context.dropCoords)
        assertNotEquals(context.npc.coords, context.dropCoords)
    }
}
