package org.rsmod.content.skills.fishing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WildernessFishingTest {
    @Test
    fun `resource and surface dark crab identities share the same method`() {
        assertEquals("npc.49_61_crabs", WildernessFishing.RESOURCE_CRAB_SPOT)
        assertEquals("npc.52_59_crabs", WildernessFishing.WILDERNESS_CRAB_SPOT)
        assertEquals(85, FishingMethod.DarkCrab.minimumLevel)
        assertEquals("obj.wilderness_fishing_bait", FishingMethod.DarkCrab.bait)
        assertEquals("queue.generic_queue10", WildernessFishing.FISHING_QUEUE)
    }

    @Test
    fun `catch chance rises with level and remains capped`() {
        assertEquals(15, WildernessFishing.catchChance(1, 1))
        assertTrue(WildernessFishing.catchChance(50, 1) > WildernessFishing.catchChance(1, 1))
        assertEquals(90, WildernessFishing.catchChance(99, 1))
    }

    @Test
    fun `special Wilderness catches retain their OSRS requirements`() {
        assertEquals(53, FishingMethod.LavaEel.minimumLevel)
        assertEquals("obj.oily_fishing_rod", FishingMethod.LavaEel.tool)
        assertEquals(85, FishingMethod.DarkCrab.minimumLevel)
        assertEquals("obj.lobster_pot", FishingMethod.DarkCrab.tool)
    }
}
