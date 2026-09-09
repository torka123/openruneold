package org.rsmod.content.bosses.royaltitans

import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

class RoyalTitansEntryContractTest {
    @Test
    fun `entrance uses the revision 240 source region and captured start tile`() {
        assertEquals(11669, ROYAL_TITANS_SOURCE_REGION)
        assertEquals(RegionLocal(0, 45, 149, 31, 37), ROYAL_TITANS_ENTRY)
        assertEquals(CoordGrid(2951, 9573, 0), ROYAL_TITANS_OUTSIDE)
    }

    @Test
    fun `entrance and exit use revision 240 object ids`() {
        assertEquals(55986, RoyalTitansAssets.ENTRANCE)
        assertEquals(55987, RoyalTitansAssets.EXIT)
        assertEquals(56004, RoyalTitansAssets.SCOREBOARD)
    }

    @Test
    fun `active titan ids are not confused with corpse ids`() {
        assertEquals(12596, RoyalTitansAssets.BRANDA_ACTIVE)
        assertEquals(14147, RoyalTitansAssets.ELDRIC_ACTIVE)
        assertEquals(14148, RoyalTitansAssets.BRANDA_CORPSE)
        assertEquals(14149, RoyalTitansAssets.ELDRIC_CORPSE)
    }

    @Test
    fun `both titans start at their captured local tiles`() {
        assertEquals(RegionLocal(0, 45, 149, 26, 30), ROYAL_TITANS_BRANDA_SPAWN)
        assertEquals(RegionLocal(0, 45, 149, 34, 30), ROYAL_TITANS_ELDRIC_SPAWN)
    }
}
