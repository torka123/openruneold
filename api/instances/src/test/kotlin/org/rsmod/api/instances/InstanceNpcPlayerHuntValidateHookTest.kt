package org.rsmod.api.instances

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceNpcPlayerHuntValidateHookTest {
    @Test
    fun `world NPCs only hunt world players`() {
        assertTrue(sameHuntInstance(null, null))
        assertFalse(sameHuntInstance(null, InstanceId(1)))
        assertFalse(sameHuntInstance(InstanceId(1), null))
    }

    @Test
    fun `instance NPCs only hunt players in their exact instance`() {
        assertTrue(sameHuntInstance(InstanceId(41), InstanceId(41)))
        assertFalse(sameHuntInstance(InstanceId(41), InstanceId(42)))
    }
}
