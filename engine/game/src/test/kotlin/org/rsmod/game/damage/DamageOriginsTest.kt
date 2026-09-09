package org.rsmod.game.damage

import kotlin.test.*
import org.rsmod.game.hit.HitOrigin

class DamageOriginsTest {
    @Test
    fun `origin totals conserve native contribution without inventing an online player`() {
        val ledger = DamageContributions()
        HitOrigin.entries.forEachIndexed { index, origin ->
            ledger.recordPlayer(17, index + 1, origin)
        }
        assertEquals(28, ledger.totalDamage())
        assertEquals(28, ledger.topPlayer()?.damage)
        HitOrigin.entries.forEachIndexed { index, origin ->
            assertEquals(index + 1, ledger.damageBy(17, origin))
        }
        assertEquals(0, ledger.damageBy(18, HitOrigin.Direct))
    }

    @Test
    fun `absorption preserves origin breakdown and clear removes both views`() {
        val first =
            DamageContributions().apply {
                recordPlayer(17, 9)
                recordPlayer(17, 3, HitOrigin.Recoil)
            }
        val combined =
            DamageContributions().apply {
                recordPlayer(17, 2, HitOrigin.Burn)
                absorbFrom(first)
            }
        assertEquals(14, combined.totalDamage())
        assertEquals(9, combined.damageBy(17, HitOrigin.Direct))
        assertEquals(3, combined.damageBy(17, HitOrigin.Recoil))
        assertEquals(2, combined.damageBy(17, HitOrigin.Burn))
        combined.clear()
        assertTrue(combined.isEmpty)
        assertEquals(0, combined.damageBy(17, HitOrigin.Burn))
        assertEquals(12, first.totalDamage())
    }

    @Test
    fun `negative zero and overflow cannot corrupt contribution or origin totals`() {
        val ledger = DamageContributions()
        ledger.recordPlayer(17, -1)
        ledger.recordPlayer(17, 0)
        assertTrue(ledger.isEmpty)
        ledger.recordPlayer(17, Int.MAX_VALUE - 2)
        ledger.recordPlayer(17, 100, HitOrigin.Recoil)
        assertEquals(2, ledger.damageBy(17, HitOrigin.Recoil))
        ledger.recordPlayer(18, Int.MAX_VALUE, HitOrigin.Burn)
        assertEquals(Int.MAX_VALUE, ledger.totalDamage())
        assertEquals(Int.MAX_VALUE, ledger.topPlayer()?.damage)
    }
}
