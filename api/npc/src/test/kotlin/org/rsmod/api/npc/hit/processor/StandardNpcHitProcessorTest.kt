package org.rsmod.api.npc.hit.processor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StandardNpcHitProcessorTest {
    @Test
    fun `admin one hit applies to an ordinary positive hit`() {
        assertTrue(
            shouldApplyAdminOneHit(
                enabled = true,
                incomingDamage = 1,
                hitpoints = 255,
                ignoresCombat = false,
                scriptedDamageGate = false,
            )
        )
    }

    @Test
    fun `admin one hit cannot bypass a scripted lifecycle gate`() {
        assertFalse(
            shouldApplyAdminOneHit(
                enabled = true,
                incomingDamage = 127,
                hitpoints = 255,
                ignoresCombat = false,
                scriptedDamageGate = true,
            )
        )
    }

    @Test
    fun `admin one hit preserves zero damage from an invulnerability modifier`() {
        assertFalse(
            shouldApplyAdminOneHit(
                enabled = true,
                incomingDamage = 0,
                hitpoints = 127,
                ignoresCombat = false,
                scriptedDamageGate = false,
            )
        )
    }

    @Test
    fun `admin one hit remains disabled for ignored combat`() {
        assertFalse(
            shouldApplyAdminOneHit(
                enabled = true,
                incomingDamage = 1,
                hitpoints = 255,
                ignoresCombat = true,
                scriptedDamageGate = false,
            )
        )
    }

    @Test
    fun `scripted progress bars can suppress only the automatic hitpoint bar`() {
        assertFalse(shouldShowHitpointHeadbar(suppressed = true))
        assertTrue(shouldShowHitpointHeadbar(suppressed = false))
    }
}
