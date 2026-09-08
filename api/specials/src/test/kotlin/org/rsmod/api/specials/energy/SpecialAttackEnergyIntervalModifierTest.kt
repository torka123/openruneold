package org.rsmod.api.specials.energy

import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player

@ResourceLock("gamevals")
class SpecialAttackEnergyIntervalModifierTest {
    @Test
    fun defaultAndDoubledIntervalsComposeWithLightbearer() {
        assertEquals(50, SpecialAttackEnergyRegeneration.interval(false, 1))
        assertEquals(25, SpecialAttackEnergyRegeneration.interval(true, 1))
        assertEquals(25, SpecialAttackEnergyRegeneration.interval(false, 2))
        assertEquals(13, SpecialAttackEnergyRegeneration.interval(true, 2))
    }

    @Test
    fun largeDivisorsCapAtOneTickAndNonPositiveDivisorsFail() {
        assertEquals(1, SpecialAttackEnergyRegeneration.interval(false, Int.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> {
            SpecialAttackEnergyRegeneration.interval(false, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SpecialAttackEnergyRegeneration.interval(false, -1)
        }
    }

    @Test
    fun temporaryDivisorActivatesAndClearsWithoutResumingAPausedClock() {
        loadGameVals()
        val player = Player()
        SpecialAttackEnergyRegeneration.pause(player)

        SpecialAttackEnergyRegeneration.setIntervalDivisor(player, 2)
        assertEquals(2, SpecialAttackEnergyRegeneration.intervalDivisor(player))
        assertTrue(SpecialAttackEnergyRegeneration.isPaused(player))

        SpecialAttackEnergyRegeneration.clearIntervalDivisor(player)
        assertEquals(1, SpecialAttackEnergyRegeneration.intervalDivisor(player))
        assertTrue(SpecialAttackEnergyRegeneration.isPaused(player))
    }

    @Test
    fun intervalChangesOnlyReplaceATimerWhenRequired() {
        assertFalse(
            SpecialAttackEnergyRegeneration.shouldResetAfterIntervalChange(
                scheduledInterval = 50,
                desiredInterval = 25,
                remainingTicks = 20,
            )
        )
        assertTrue(
            SpecialAttackEnergyRegeneration.shouldResetAfterIntervalChange(
                scheduledInterval = 50,
                desiredInterval = 25,
                remainingTicks = 30,
            )
        )
        assertTrue(
            SpecialAttackEnergyRegeneration.shouldResetAfterIntervalChange(
                scheduledInterval = 25,
                desiredInterval = 50,
                remainingTicks = 10,
            )
        )
    }

    private fun loadGameVals() {
        val root = Path.of("").toAbsolutePath()
        GameValProvider.load("$root${File.separator}")
    }
}
