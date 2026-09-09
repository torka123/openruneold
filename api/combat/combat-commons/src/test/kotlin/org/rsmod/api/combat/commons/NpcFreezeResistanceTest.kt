package org.rsmod.api.combat.commons

import kotlin.test.*

class NpcFreezeResistanceTest {
    @Test
    fun `full resistance is immune while partial resistance retains native minimum tick`() {
        assertEquals(0, npcBindDuration(32, 100))
        assertEquals(0, npcBindDuration(32, 150))
        assertEquals(1, npcBindDuration(1, 99))
        assertEquals(16, npcBindDuration(32, 50))
        assertEquals(32, npcBindDuration(32, 0))
        assertEquals(32, npcBindDuration(32, -1))
    }
}
