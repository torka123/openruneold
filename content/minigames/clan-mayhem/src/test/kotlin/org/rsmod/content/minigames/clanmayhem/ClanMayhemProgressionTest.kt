package org.rsmod.content.minigames.clanmayhem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClanMayhemProgressionTest {
    @Test
    fun `personal progression codec round trips every field`() {
        val expected =
            ClanMayhemPersonalProgress(
                games = 12,
                wins = 4,
                secondPlaces = 3,
                thirdPlaces = 2,
                kills = 41,
                deaths = 19,
                assists = 7,
                enemyDamage = 8_500,
                bossDamage = 1_200,
                bossObjectives = 3,
                cratesOpened = 9,
                keysOpened = 6,
                pointsEarned = 340,
                currentWinStreak = 2,
                bestWinStreak = 3,
                trioWins = 1,
                fiveManWins = 3,
                welfareWins = 2,
                midWins = 1,
                maxWins = 1,
            )

        val actual =
            ClanMayhemPersonalProgressCodec.decode(ClanMayhemPersonalProgressCodec.encode(expected))

        assertEquals(expected, actual)
    }

    @Test
    fun `personal progression codec rejects corrupt or negative values`() {
        assertNull(ClanMayhemPersonalProgressCodec.decode(null))
        assertNull(ClanMayhemPersonalProgressCodec.decode(mutableListOf(0)))
        val negative = ClanMayhemPersonalProgressCodec.encode(ClanMayhemPersonalProgress())
        negative[1] = -1
        assertNull(ClanMayhemPersonalProgressCodec.decode(negative))
    }
}
