package dev.openrune.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlayerNpcRespawnPolicyTest {
    @Test
    fun `standard one hundred cycle respawn is halved`() {
        assertEquals(50, SlayerNpcRespawnPolicy.halve(100))
    }

    @Test
    fun `odd respawns round up without becoming faster than half`() {
        assertEquals(51, SlayerNpcRespawnPolicy.halve(101))
    }

    @Test
    fun `respawn never becomes zero`() {
        assertEquals(1, SlayerNpcRespawnPolicy.halve(1))
        assertEquals(1, SlayerNpcRespawnPolicy.halve(0))
    }

    @Test
    fun `permanent no-respawn sentinel remains permanent`() {
        assertEquals(Int.MAX_VALUE, SlayerNpcRespawnPolicy.halve(Int.MAX_VALUE))
    }
}
