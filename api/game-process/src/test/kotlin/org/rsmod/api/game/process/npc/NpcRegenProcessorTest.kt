package org.rsmod.api.game.process.npc

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class NpcRegenProcessorTest {
    @Test
    fun `defence minimum allows natural restoration and boosted defence decay`() {
        val npc = Npc(NpcServerType(id = 1, defence = 260, hitpoints = 100, regenRate = 1))
        npc.defenceDrainMinimum = 250
        npc.defenceLvl = 0
        assertEquals(250, npc.defenceLvl)
        val processor = NpcRegenProcessor()

        for (expected in 251..260) {
            npc.regenClock = 0
            processor.process(npc)
            assertEquals(expected, npc.defenceLvl)
        }
        npc.regenClock = 0
        processor.process(npc)
        assertEquals(260, npc.defenceLvl)

        npc.defenceLvl = 262
        for (expected in listOf(261, 260, 260)) {
            npc.regenClock = 0
            processor.process(npc)
            assertEquals(expected, npc.defenceLvl)
        }
        assertEquals(250, npc.defenceDrainMinimum)
    }

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
