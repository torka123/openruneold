package org.rsmod.content.bosses.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaledictusWorldEventTest {
    @Test
    fun `normal cycle warns at nine minutes and spawns exactly sixty seconds later`() {
        val timeline = MaledictusTimeline()
        timeline.scheduleAfter(250)
        assertFalse(timeline.dueWarning(1_149))
        assertTrue(timeline.dueWarning(1_150))
        timeline.markWarned()
        assertFalse(timeline.dueSpawn(1_249))
        assertTrue(timeline.dueSpawn(1_250))
        assertEquals(100, timeline.spawnTick - timeline.warningTick)
    }

    @Test
    fun `spawn lifetime is ten minutes and completion starts a fresh cycle`() {
        val timeline = MaledictusTimeline()
        timeline.scheduleAfter(0)
        timeline.markWarned()
        timeline.markSpawned(1_000)
        assertFalse(timeline.dueDespawn(1_999))
        assertTrue(timeline.dueDespawn(2_000))
        timeline.finish(2_000)
        assertFalse(timeline.active)
        assertEquals(2_900, timeline.warningTick)
        assertEquals(3_000, timeline.spawnTick)
    }

    @Test
    fun `forced warning uses the real sixty second lead and cannot duplicate timeline state`() {
        val timeline = MaledictusTimeline()
        timeline.forceWarning(500)
        assertTrue(timeline.dueWarning(500))
        timeline.markWarned()
        assertFalse(timeline.dueWarning(500))
        assertEquals(600, timeline.spawnTick)
        timeline.markSpawned(600)
        assertTrue(timeline.active)
        assertNull(timeline.ticksUntilSpawn(600))
    }

    @Test
    fun `all spawn definitions carry truthful typed announcement and fallback data`() {
        assertEquals(11, MALEDICTUS_SPAWNS.size)
        assertEquals(11, MALEDICTUS_SPAWNS.map { it.announcementName }.distinct().size)
        assertTrue(MALEDICTUS_SPAWNS.all { it.fallbackTiles.isNotEmpty() })
        assertTrue(
            MALEDICTUS_SPAWNS.all {
                it.announcementName.contains(it.compassDescription) &&
                    it.announcementName.contains(it.wildernessSection)
            }
        )
        assertEquals(
            mapOf(
                RevenantCaveSection.North to 3,
                RevenantCaveSection.Middle to 5,
                RevenantCaveSection.South to 3,
            ),
            MALEDICTUS_SPAWNS.groupingBy { it.section }.eachCount(),
        )
    }

    @Test
    fun `warning names match the three cave sections`() {
        assertEquals("northern", RevenantCaveSection.North.warningName)
        assertEquals("middle", RevenantCaveSection.Middle.warningName)
        assertEquals("southern", RevenantCaveSection.South.warningName)
    }

    @Test
    fun `repeated cycle selection has multiple real choices`() {
        val first = MALEDICTUS_SPAWNS.first()
        val last = MALEDICTUS_SPAWNS.last()
        assertNotEquals(first.tile, last.tile)
        assertNotEquals(first.section, last.section)
    }

    @Test
    fun `reward configuration provides two complete dragon rolls and two food pieces`() {
        assertEquals(2, MaledictusRewardConfig.REVENANT_DRAGON_ROLLS)
        assertEquals(2, MaledictusRewardConfig.PARTICIPANT_FOOD_COUNT)
        assertTrue(MaledictusRewardConfig.DRAGON_MAIN.all { it.weight > 0 })
        val selected =
            (0 until MaledictusRewardConfig.dragonMainTotalWeight)
                .map(MaledictusRewardConfig::selectDragonMain)
                .toSet()
        assertEquals(MaledictusRewardConfig.DRAGON_MAIN.toSet(), selected)
    }

    @Test
    fun `timing constants are explicit server ticks`() {
        assertEquals(600, SERVER_TICK_MILLIS)
        assertEquals(100, MALEDICTUS_WARNING_LEAD_TICKS)
        assertEquals(900, MALEDICTUS_WARNING_DELAY_TICKS)
        assertEquals(1_000, MALEDICTUS_SPAWN_INTERVAL_TICKS)
        assertEquals(1_000, MALEDICTUS_ACTIVE_LIFETIME_TICKS)
    }
}
