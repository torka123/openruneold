package org.rsmod.api.game.process.npc

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class NpcRegenProcessorTest {
    @Test
    fun `Akkha flag blocks natural hp regen while stat restoration continues`() {
        val npc =
            Npc(
                NpcServerType(
                    id = 1,
                    name = "Akkha test actor",
                    hitpoints = 100,
                    attack = 100,
                    strength = 100,
                    defence = 100,
                    ranged = 100,
                    magic = 100,
                    regenRate = 1,
                ),
                CoordGrid(0, 1, 1, 0, 0),
            )
        npc.hitpoints = 50
        npc.attackLvl = 50
        npc.suppressHitpointsRegen = true
        npc.regenClock = 0

        NpcRegenProcessor().process(npc)

        assertEquals(50, npc.hitpoints)
        assertEquals(51, npc.attackLvl)
    }
}
