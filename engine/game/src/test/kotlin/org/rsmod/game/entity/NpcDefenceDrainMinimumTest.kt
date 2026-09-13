package org.rsmod.game.entity

import dev.openrune.types.NpcServerType
import kotlin.test.*
import org.rsmod.annotations.InternalApi

/**
 * S08 native mutation contract in docs/bosses/nex/NEX_OSRS_AUDIT.md. The representative 260/250
 * values use retained W1 revision15325291 line22 and W2 revision15331654 line59. These generic
 * policy tests do not establish a Nex boost formula or change weapon drain/spillover behavior.
 */
@OptIn(InternalApi::class)
class NpcDefenceDrainMinimumTest {
    @Test
    fun `default and cleared policy preserve ordinary raw defence assignments`() {
        val npc = npc()
        assertNull(npc.defenceDrainMinimum)
        for (value in listOf(255, 0, -10, Int.MIN_VALUE, Int.MAX_VALUE)) {
            npc.defenceLvl = value
            assertEquals(value, npc.defenceLvl)
        }

        npc.defenceDrainMinimum = 250
        npc.defenceLvl = 0
        assertEquals(250, npc.defenceLvl)
        npc.defenceDrainMinimum = null
        npc.defenceLvl = -10
        assertEquals(-10, npc.defenceLvl)
    }

    @Test
    fun `reductions above the minimum succeed and deeper repeated drains stop immediately`() {
        val npc = npc().apply { defenceDrainMinimum = 250 }

        npc.defenceLvl = 255
        assertEquals(255, npc.defenceLvl)
        npc.defenceLvl = 249
        assertEquals(250, npc.defenceLvl)
        for (value in listOf(249, 200, 0, -1, Int.MIN_VALUE)) {
            npc.defenceLvl = value
            assertEquals(250, npc.defenceLvl)
        }
        assertEquals(260, npc.baseDefenceLvl)
    }

    @Test
    fun `configured minimum permits boosts restoration and later bounded reductions`() {
        val npc = npc().apply { defenceDrainMinimum = 250 }
        npc.defenceLvl = 0
        npc.defenceLvl = 260
        assertEquals(260, npc.defenceLvl)
        npc.defenceLvl = 300
        assertEquals(300, npc.defenceLvl)
        npc.defenceLvl = 280
        assertEquals(280, npc.defenceLvl)
        npc.defenceLvl = 200
        assertEquals(250, npc.defenceLvl)
        assertEquals(260, npc.baseDefenceLvl)
    }

    @Test
    fun `stat drain immunity rejects reductions before applying the optional minimum`() {
        val npc =
            npc().apply {
                defenceDrainMinimum = 250
                combatImmunities.statDrain = true
            }
        npc.defenceLvl = 255
        assertEquals(260, npc.defenceLvl)
        npc.defenceLvl = 0
        assertEquals(260, npc.defenceLvl)
        npc.defenceLvl = 300
        assertEquals(300, npc.defenceLvl)
        npc.defenceLvl = 290
        assertEquals(300, npc.defenceLvl)

        npc.combatImmunities.statDrain = false
        npc.defenceLvl = 290
        assertEquals(290, npc.defenceLvl)
        npc.defenceLvl = 0
        assertEquals(250, npc.defenceLvl)
    }

    @Test
    fun `negative minima are rejected without changing the existing policy or level`() {
        val npc = npc().apply { defenceDrainMinimum = 250 }
        for (invalid in listOf(-1, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException> { npc.defenceDrainMinimum = invalid }
            assertEquals(250, npc.defenceDrainMinimum)
            assertEquals(260, npc.defenceLvl)
        }

        npc.defenceDrainMinimum = 0
        npc.defenceLvl = -1
        assertEquals(0, npc.defenceLvl)
    }

    @Test
    fun `configuring a minimum above depleted defence never heals an attempted drain`() {
        val npc = npc()
        npc.defenceLvl = 200
        npc.defenceDrainMinimum = 250
        assertEquals(200, npc.defenceLvl)
        npc.defenceLvl = 100
        assertEquals(200, npc.defenceLvl)
        npc.defenceLvl = 220
        assertEquals(220, npc.defenceLvl)
        npc.defenceLvl = 210
        assertEquals(220, npc.defenceLvl)
        npc.defenceLvl = 260
        npc.defenceLvl = 100
        assertEquals(250, npc.defenceLvl)
    }

    @Test
    fun `transmog and native stat copies retain and enforce the configured policy`() {
        val npc = npc().apply { defenceDrainMinimum = 250 }
        val otherForm = NpcServerType(id = 2, defence = 100, hitpoints = 50)
        npc.transmog(otherForm, Int.MAX_VALUE)
        assertSame(otherForm, npc.visType)
        assertEquals(250, npc.defenceDrainMinimum)
        assertEquals(260, npc.defenceLvl)

        npc.copyCurrentStats(otherForm)
        assertEquals(250, npc.defenceLvl)
        assertEquals(260, npc.baseDefenceLvl)
        assertEquals(250, npc.defenceDrainMinimum)
        npc.copyStats(NpcServerType(id = 3, defence = 300))
        assertEquals(300, npc.defenceLvl)
        assertEquals(300, npc.baseDefenceLvl)
        npc.defenceLvl = 0
        assertEquals(250, npc.defenceLvl)
        npc.resetTransmog()
        assertSame(npc.type, npc.visType)
        assertEquals(250, npc.defenceDrainMinimum)
    }

    @Test
    fun `native respawn restores original stats while preserving the drain policy`() {
        val npc =
            npc().apply {
                slotId = 1
                assignUid()
                defenceDrainMinimum = 250
            }
        npc.transmog(NpcServerType(id = 2, defence = 300), Int.MAX_VALUE)
        npc.copyStats(npc.visType)
        npc.defenceLvl = 0
        assertEquals(250, npc.defenceLvl)

        npc.setRespawnValues()

        assertSame(npc.type, npc.visType)
        assertEquals(260, npc.baseDefenceLvl)
        assertEquals(260, npc.defenceLvl)
        assertEquals(250, npc.defenceDrainMinimum)
        npc.defenceLvl = 0
        assertEquals(250, npc.defenceLvl)
        assertNull(npc().defenceDrainMinimum)
    }

    private fun npc() = Npc(NpcServerType(id = 1, defence = 260, hitpoints = 100))
}
