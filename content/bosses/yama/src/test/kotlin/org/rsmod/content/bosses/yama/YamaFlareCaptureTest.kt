package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals

class YamaFlareCaptureTest {
    @Test
    fun `normal and contract Fire Streak density matches supplied captures`() {
        assertEquals(8, YamaRules.fireStreakCount(YamaContract.NONE))
        assertEquals(9, YamaRules.fireStreakCount(YamaContract.GLYPHIC))
    }

    @Test
    fun `multi flare positions match the four captured arena coordinates`() {
        assertEquals(
            listOf(23 to 33, 26 to 25, 35 to 25, 38 to 33),
            YamaRules.flareSpawnPositions(count = 4, singleCursor = 0),
        )
        assertEquals(
            listOf(23 to 33, 26 to 25),
            YamaRules.flareSpawnPositions(count = 2, singleCursor = 0),
        )
    }

    @Test
    fun `phase three one-flare turns rotate through the captured order`() {
        assertEquals(
            listOf(26 to 25, 23 to 33, 35 to 25, 38 to 33),
            (0..3).flatMap { YamaRules.flareSpawnPositions(count = 1, singleCursor = it) },
        )
        assertEquals(listOf(26 to 25), YamaRules.flareSpawnPositions(count = 1, singleCursor = 4))
    }

    @Test
    fun `flare becomes attackable after spawn and its captured yellow bar is twenty ticks`() {
        assertEquals(3, YamaRules.VOID_FLARE_ATTACKABLE_DELAY)
        assertEquals(25, YamaRules.VOID_FLARE_BASE_LIFETIME)
        assertEquals(600, YamaRules.VOID_FLARE_PROGRESS_CLIENT_CYCLES)
        assertEquals(20, YamaRules.VOID_FLARE_PROGRESS_CLIENT_CYCLES / 30)
    }
}
