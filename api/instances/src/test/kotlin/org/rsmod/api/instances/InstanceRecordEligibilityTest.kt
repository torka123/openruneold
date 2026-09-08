package org.rsmod.api.instances

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class InstanceRecordEligibilityTest {
    private val area =
        InstanceArea.copyRegions(
            regionIds = listOf(1),
            enterCoord = RegionLocal(0, 0, 0, 0, 0),
            exitCoord = CoordGrid.ZERO,
        )

    @Test
    fun `instance records are eligible by default`() {
        val spec = InstanceSettings().withArea(area, settingsRowId = 0)

        assertTrue(spec.recordsEligible)
    }

    @Test
    fun `record eligibility can be disabled for debug encounters`() {
        val spec = InstanceSettings(recordsEligible = false).withArea(area, settingsRowId = 0)

        assertFalse(spec.recordsEligible)
    }
}
