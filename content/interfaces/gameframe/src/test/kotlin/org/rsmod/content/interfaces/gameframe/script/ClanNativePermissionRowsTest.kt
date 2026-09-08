package org.rsmod.content.interfaces.gameframe.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.api.net.social.ClanBroadcastKey
import org.rsmod.api.net.social.ClanPermissionKey

class ClanNativePermissionRowsTest {
    @Test
    fun `rev240 native permission catalog contains access plus all 53 leaves in cache order`() {
        val rows = ClanNativePermissionRows.ALL

        assertEquals(54, rows.size)
        assertEquals("Permission access", rows.first().label)
        assertEquals("Edit rank titles", rows[1].label)
        assertEquals("Combat Masteries", rows.last().label)
        assertEquals("Guests may listen in chat", rows[10].label)
        assertEquals("Event starting notification", rows[16].label)
        assertEquals("Joining/Leaving clan", rows[25].label)
        assertEquals("Level-up broadcasts", rows[33].label)
        assertEquals("Loot broadcasts", rows[39].label)
        assertEquals("PK broadcasts", rows[44].label)
    }

    @Test
    fun `every visible rank-backed row maps explicitly`() {
        val rows = ClanNativePermissionRows.ALL

        assertTrue(
            ClanPermissionKey.entries.all { key -> rows.count { it.permission == key } == 1 }
        )
        assertTrue(ClanBroadcastKey.entries.all { key -> rows.count { it.broadcast == key } == 1 })
    }
}
