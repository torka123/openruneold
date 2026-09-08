package org.rsmod.api.specials.energy

import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player

@ResourceLock("gamevals")
class SpecialAttackEnergyRegenerationTest {
    @Test
    fun `normal and lightbearer intervals match thirty and fifteen seconds`() {
        assertEquals(50, SpecialAttackEnergyRegeneration.NORMAL_INTERVAL_TICKS)
        assertEquals(25, SpecialAttackEnergyRegeneration.LIGHTBEARER_INTERVAL_TICKS)
        assertEquals(50, SpecialAttackEnergyRegeneration.interval(hasLightbearer = false))
        assertEquals(25, SpecialAttackEnergyRegeneration.interval(hasLightbearer = true))
    }

    @Test
    fun `lightbearer equip never delays an earlier natural regeneration`() {
        assertTrue(
            SpecialAttackEnergyRegeneration.shouldResetAfterRingChange(
                lightbearer = true,
                remainingTicks = 26,
            )
        )
        assertFalse(SpecialAttackEnergyRegeneration.shouldResetAfterRingChange(true, 25))
        assertFalse(SpecialAttackEnergyRegeneration.shouldResetAfterRingChange(true, 1))
        assertTrue(SpecialAttackEnergyRegeneration.shouldResetAfterRingChange(false, 1))
    }

    @Test
    fun `pause survives ring changes and restart begins a full current interval`() {
        loadGameVals()
        val player = Player()

        SpecialAttackEnergyRegeneration.restart(player, hasLightbearer = false)
        assertFalse(SpecialAttackEnergyRegeneration.isPaused(player))
        assertTrue(player.softTimerMap.isNotEmpty)

        SpecialAttackEnergyRegeneration.pause(player)
        assertTrue(SpecialAttackEnergyRegeneration.isPaused(player))
        assertFalse(player.softTimerMap.isNotEmpty)

        SpecialAttackEnergyRegeneration.refreshInterval(player)
        assertTrue(SpecialAttackEnergyRegeneration.isPaused(player))
        assertFalse(player.softTimerMap.isNotEmpty)

        SpecialAttackEnergyRegeneration.restart(player, hasLightbearer = true)
        assertFalse(SpecialAttackEnergyRegeneration.isPaused(player))
        assertTrue(player.softTimerMap.isNotEmpty)
    }

    private fun loadGameVals() {
        val root = Path.of("").toAbsolutePath()
        GameValProvider.load("$root${File.separator}")
    }
}
