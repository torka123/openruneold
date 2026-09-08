package org.rsmod.api.instances

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InstanceKillBestPersistenceTest {
    @Test
    fun `kill best map is account backed`() {
        assertEquals(
            "instance_kill_best_ticks_v1",
            InstanceAttributes.KILL_BEST_TICKS.persistenceKey,
        )
        assertFalse(InstanceAttributes.KILL_BEST_TICKS.temp)
    }
}
