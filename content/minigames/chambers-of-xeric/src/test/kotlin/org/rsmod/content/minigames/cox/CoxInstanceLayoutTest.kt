package org.rsmod.content.minigames.cox

import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.api.instances.RegionLocal

class CoxInstanceLayoutTest {
    @Test
    fun regularRaidEntryResolvesToItsOriginalSourceMap() {
        assertEquals(
            RegionLocal(level = 0, regionZoneX = 51, regionZoneZ = 81, localX = 35, localZ = 4),
            CoxInstanceLayout.area(olmOnly = false).enterCoord,
        )
    }

    @Test
    fun olmOnlyEntryResolvesToItsOriginalSourceMap() {
        assertEquals(
            RegionLocal(level = 0, regionZoneX = 50, regionZoneZ = 89, localX = 33, localZ = 24),
            CoxInstanceLayout.area(olmOnly = true).enterCoord,
        )
    }

    @Test
    fun rotatedDonorRoomCoordinateUsesInverseChunkRotation() {
        assertEquals(
            RegionLocal(level = 2, regionZoneX = 52, regionZoneZ = 82, localX = 17, localZ = 22),
            CoxInstanceLayout.local(CoxDonorArea.FLOOR_TWO_SE, 22, 38),
        )
    }
}
