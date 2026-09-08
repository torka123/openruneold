package org.rsmod.game.hit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HitValidityTest {
    @Test
    fun `built hits retain a live generation check instead of a launch boolean`() {
        var generation = 1
        val first = builder().apply { validity = HitValidity { generation == 1 } }.build()
        assertTrue(first.isValidNow())
        generation = 2
        val second = builder().apply { validity = HitValidity { generation == 2 } }.build()
        assertFalse(first.isValidNow())
        assertTrue(second.isValidNow())
        generation = 3
        assertFalse(first.isValidNow())
        assertFalse(second.isValidNow())
    }

    @Test
    fun `hit validity tracks disconnect death and membership independently`() {
        var alive = true
        var connected = true
        var instance = 17
        val hit = builder().apply {
            validity = HitValidity { alive && connected && instance == 17 }
        }.build()
        assertTrue(hit.isValidNow())
        alive = false
        assertFalse(hit.isValidNow())
        alive = true
        connected = false
        assertFalse(hit.isValidNow())
        connected = true
        instance = 18
        assertFalse(hit.isValidNow())
        assertTrue(builder().build().isValidNow(), "Default generic combat is unchanged")
    }

    private fun builder() = HitBuilder(
        type = HitType.Magic, damage = 30, sourceUid = null, sourceSlot = null,
        isFromNpc = false, isFromPlayer = false, clientDelay = 0,
        righthandType = null, secondaryType = null,
        targetHitmark = 16, sourceHitmark = 16, publicHitmark = null,
        zeroDamageHitmarkLit = null, zeroDamageHitmarkTint = null, maxDamageHitmarkLit = null,
        targetMaxDamageThreshold = Int.MAX_VALUE, sourceMaxDamageThreshold = Int.MAX_VALUE,
    )
}
