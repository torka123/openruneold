package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YamaRecordsTest {
    @Test
    fun `successful kills update count personal best and persisted recent times`() {
        val first = YamaRecords.recordSuccess(null, null, null, 100)
        assertEquals(1, first.killCount)
        assertEquals(100, first.personalBestTicks)
        assertEquals(listOf(100), first.recentKillTimes)
        assertNull(first.fourKillAverageTicks)

        val slower =
            YamaRecords.recordSuccess(
                first.killCount,
                first.personalBestTicks,
                first.recentKillTimes,
                120,
            )
        assertEquals(2, slower.killCount)
        assertEquals(100, slower.personalBestTicks)
        assertEquals(listOf(100, 120), slower.recentKillTimes)
    }

    @Test
    fun `rolling average uses only the latest four successful kills`() {
        var records = YamaRecords.snapshot(null, null, null)
        listOf(10, 20, 30, 40, 50).forEach { ticks ->
            records =
                YamaRecords.recordSuccess(
                    records.killCount,
                    records.personalBestTicks,
                    records.recentKillTimes,
                    ticks,
                )
        }
        assertEquals(5, records.killCount)
        assertEquals(10, records.personalBestTicks)
        assertEquals(listOf(20, 30, 40, 50), records.recentKillTimes)
        assertEquals(35, records.fourKillAverageTicks)
    }

    @Test
    fun `corrupt persisted record values are sanitized deterministically`() {
        val records = YamaRecords.snapshot(-20, -1, listOf(-5, 0, 10, 20, 30, 40, 50))
        assertEquals(0, records.killCount)
        assertNull(records.personalBestTicks)
        assertEquals(listOf(20, 30, 40, 50), records.recentKillTimes)
        assertEquals(35, records.fourKillAverageTicks)
    }

    @Test
    fun `record times render using six tenths per server tick`() {
        assertEquals("--", YamaRecords.formatTime(null))
        assertEquals("--", YamaRecords.formatTime(0))
        assertEquals("0:00.6", YamaRecords.formatTime(1))
        assertEquals("1:00.0", YamaRecords.formatTime(100))
        assertEquals("1:00.6", YamaRecords.formatTime(101))
    }

    @Test
    fun `records imp uses the revision 240 npc and a bounded visible sequence`() {
        assertEquals(14181, YamaAssets.IMP)
        assertEquals(2, YamaRecords.IMP_APPROACH_TICKS)
        assertEquals(2, YamaRecords.IMP_DESTROY_TICKS)
        assertEquals(1438, YAMA_RECORDS_IMP_SPAWN.x)
        assertEquals(10077, YAMA_RECORDS_IMP_SPAWN.z)
        assertEquals(1438, YAMA_RECORDS_IMP_PRESENTATION.x)
        assertEquals(10075, YAMA_RECORDS_IMP_PRESENTATION.z)
    }
}
