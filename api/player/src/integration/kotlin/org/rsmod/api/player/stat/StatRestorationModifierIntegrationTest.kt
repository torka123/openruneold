package org.rsmod.api.player.stat

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState

class StatRestorationModifierIntegrationTest {
    @Test
    fun GameTestState.statAddAppliesTheConfiguredGainPercentageExactlyOnce() = runGameTest {
        player.setBaseLevel(stats.hitpoints, 99)
        player.setCurrentLevel(stats.hitpoints, 1)
        StatRestorationModifierState.setPercentage(player, stats.hitpoints, 50)

        player.statAdd(stats.hitpoints, constant = 10, percent = 20)

        assertEquals(15, player.hitpoints)
    }

    @Test
    fun GameTestState.statHealAppliesTheConfiguredGainPercentageBeforeItsBaseCap() =
        runGameTest {
            player.setBaseLevel(stats.hitpoints, 99)
            player.setCurrentLevel(stats.hitpoints, 50)
            StatRestorationModifierState.setPercentage(player, stats.hitpoints, 50)

            player.statHeal(stats.hitpoints, constant = 10, percent = 20)

            assertEquals(64, player.hitpoints)
        }

    @Test
    fun GameTestState.zeroPercentBlocksAGainAndClearRestoresDefaultBehavior() =
        runGameTest {
            player.setBaseLevel(stats.hitpoints, 99)
            player.setCurrentLevel(stats.hitpoints, 50)
            StatRestorationModifierState.setPercentage(player, stats.hitpoints, 0)

            player.statHeal(stats.hitpoints, constant = 20, percent = 0)
            assertEquals(50, player.hitpoints)

            StatRestorationModifierState.clear(player, stats.hitpoints)
            player.statHeal(stats.hitpoints, constant = 20, percent = 0)
            assertEquals(70, player.hitpoints)
        }

    @Test
    fun GameTestState.scaledGainsRetainStatAddAndStatHealCaps() = runGameTest {
        player.setBaseLevel(stats.hitpoints, 99)
        player.setCurrentLevel(stats.hitpoints, 250)
        StatRestorationModifierState.setPercentage(player, stats.hitpoints, 50)

        player.statAdd(stats.hitpoints, constant = Int.MAX_VALUE, percent = 0)
        assertEquals(255, player.hitpoints)

        player.setCurrentLevel(stats.hitpoints, 95)
        player.statHeal(stats.hitpoints, constant = 20, percent = 0)
        assertEquals(99, player.hitpoints)
    }
}
