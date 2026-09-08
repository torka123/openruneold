package org.rsmod.content.drops.tables.monsters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlchemicalHydraDropTableTest {
    @Test
    fun `only the final combat form owns the reward table`() {
        assertEquals(listOf("npc.hydraboss_4"), alchemicalHydraDropTable.npcs)
    }

    @Test
    fun `unique pre-roll is sequential and stops at the first success`() {
        assertEquals("obj.dragon_thrownaxe" to 750, roll(0, 250)?.let { it.obj to it.count.first })
        assertEquals("obj.dragon_knife", roll(1, 0, 0)?.obj)
        assertEquals("obj.hydra_claw", roll(1, 1, 0)?.obj)
        assertEquals("obj.hydra_tail", roll(1, 1, 1, 0)?.obj)
        assertEquals("obj.hydra_leather", roll(1, 1, 1, 1, 0)?.obj)
        assertEquals("obj.hydra_eye", roll(1, 1, 1, 1, 1, 0)?.obj)
        assertNull(roll(1, 1, 1, 1, 1, 1))
    }

    @Test
    fun `brimstone ring pieces use persisted collection order`() {
        val eyeFirst = roll(1, 1, 1, 1, 1, 0)
        val fangSecond = roll(1, 1, 1, 1, 1, 0, quantities = mapOf(22973 to 1))
        val heartThird = roll(1, 1, 1, 1, 1, 0, quantities = mapOf(22973 to 1, 22971 to 1))

        assertEquals("obj.hydra_eye", eyeFirst?.obj)
        assertEquals("obj.hydra_fang", fangSecond?.obj)
        assertEquals("obj.hydra_heart", heartThird?.obj)
    }

    private fun roll(
        vararg rolls: Int,
        quantities: Map<Int, Int> = emptyMap(),
    ): org.rsmod.api.droptable.DropRollItem? {
        val values = ArrayDeque(rolls.toList())
        return AlchemicalHydraUniqueEngine.roll(quantities) { bound ->
            check(values.isNotEmpty()) { "Missing scripted roll for bound $bound" }
            values.removeFirst().also { check(it in 0 until bound) }
        }
    }
}
