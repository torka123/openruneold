package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

class ClueRewardTierTest {
    @Test
    fun `hard and elite boosts require claimed reward state not qualification`() {
        val player = Player()
        for (state in 0..2) {
            VarPlayerIntMapSetter.set(player, "varbit.ca_tier_status_hard", state)
            VarPlayerIntMapSetter.set(player, "varbit.ca_tier_status_elite", state)
            assertEquals(state == 2, player.hasUnlockedHardCombat())
            assertEquals(state == 2, player.hasUnlockedEliteCombat())
            assertEquals(if (state == 2) 23 else 25, player.hardClueDropDenominator(25))
            assertEquals(if (state == 2) 95 else 100, player.eliteClueDropDenominator(100))
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            }
        }
    }
}
