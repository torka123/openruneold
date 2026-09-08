package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals

class LeviathanScoreboardTest {
    @Test
    fun `native interface and component contract matches the capture`() {
        assertEquals(49475, Dt2Assets.LEVIATHAN_SCOREBOARD)
        assertEquals(831, LeviathanScoreboard.INTERFACE)
        assertEquals(
            setOf(6, 9, 11, 13, 15, 17, 19),
            LeviathanScoreboard.texts(0, LeviathanRecordState()).keys,
        )
    }

    @Test
    fun `empty records render truthful captured defaults`() {
        assertEquals(
            linkedMapOf(
                6 to "Leviathan Statistics",
                9 to "0",
                11 to "0",
                13 to "-",
                15 to "0",
                17 to "0",
                19 to "-",
            ),
            LeviathanScoreboard.texts(0, LeviathanRecordState()),
        )
    }

    @Test
    fun `personal values come from kill count and persistent records`() {
        val records =
            LeviathanRecordState(
                deaths = 1_234,
                normalPersonalBestTicks = 141,
                awakenedPersonalBestTicks = 90,
            )

        val text = LeviathanScoreboard.texts(killCount = 48_747_765, records = records)

        assertEquals("48,747,765", text[LeviathanScoreboard.PERSONAL_KILL_COUNT])
        assertEquals("1,234", text[LeviathanScoreboard.PERSONAL_DEATHS])
        assertEquals("1:24", text[LeviathanScoreboard.PERSONAL_BEST])
    }

    @Test
    fun `global cells use durable aggregate values and the normal-mode best`() {
        val globals =
            LeviathanGlobalRecordState(
                normalKills = 12_345,
                awakenedKills = 67,
                deaths = 890,
                normalBest = LeviathanGlobalBest(1, "Fast Player", 99),
                awakenedBest = LeviathanGlobalBest(2, "Awakened Player", 80),
            )

        val text = LeviathanScoreboard.texts(0, LeviathanRecordState(), globals)

        assertEquals("12,412", text[LeviathanScoreboard.GLOBAL_KILL_COUNT])
        assertEquals("890", text[LeviathanScoreboard.GLOBAL_DEATHS])
        assertEquals("0:59", text[LeviathanScoreboard.GLOBAL_BEST])
    }

    @Test
    fun `time formatting is tick based and never fabricates a zero best`() {
        assertEquals("-", LeviathanScoreboard.formatTime(0))
        assertEquals("0:00", LeviathanScoreboard.formatTime(1))
        assertEquals("0:59", LeviathanScoreboard.formatTime(99))
        assertEquals("1:00", LeviathanScoreboard.formatTime(100))
    }

    @Test
    fun `dynamic interface labels escape client markup`() {
        assertEquals(
            "&lt;col=ff0000&gt;Levi&amp;athan&lt;/col&gt;&quot;&#39; Statistics",
            LeviathanScoreboard.title("<col=ff0000>Levi&athan</col>\"'"),
        )
    }
}
