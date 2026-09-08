package dev.openrune

import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CollectionLogR240OverviewAuditTest {
    @Test
    fun `revision 240 collection overview contract is cache authoritative`() {
        ServerCacheManager.init(240)
        val overview = requireNotNull(ServerCacheManager.getInterface(908))

        assertEquals("collection_overview", overview.internalName)
        assertEquals("current_rank", overview.components[10]?.internalName)
        assertEquals("progress_bar_rank_left", overview.components[11]?.internalName)
        assertEquals("progress_bar_rank_right", overview.components[15]?.internalName)
        assertEquals("latest_items", overview.components[18]?.internalName)
        assertEquals("latest_items_data", overview.components[21]?.internalName)
        assertArrayEquals(
            arrayOf(
                7802,
                59506697,
                59506711,
                59506696,
                59506710,
                59506715,
                59506702,
                59506699,
                59506703,
                59506701,
                59506705,
                59506709,
                59506693,
                59506695,
                59506707,
            ),
            overview.components[3]?.onLoad,
        )

        assertNotNull(ServerCacheManager.getVarp(4612))
        assertNotNull(ServerCacheManager.getVarp(4848))
        repeat(12) { index ->
            assertNotNull(ServerCacheManager.getVarp(4623 + index * 2))
            assertNotNull(ServerCacheManager.getVarp(4624 + index * 2))
        }
        val currentDay = requireNotNull(ServerCacheManager.getVarbit(9535))
        assertEquals(143, currentDay.baseVar.id)
        assertEquals(3..16, currentDay.bits)
    }
}
