package org.rsmod.content.bosses.corporealbeast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class CorporealBeastRulesTest {
    @Test
    fun `melee can only be selected in reach`() {
        assertEquals(CorporealBeastAttack.Melee, CorporealBeastLegacyCombat.attack(true, 0, 0))
        assertEquals(CorporealBeastAttack.Magic, CorporealBeastLegacyCombat.attack(false, 0, 0))
        assertEquals(CorporealBeastAttack.Drain, CorporealBeastLegacyCombat.attack(false, 99, 1))
        assertEquals(
            CorporealBeastAttack.AreaMagic,
            CorporealBeastLegacyCombat.attack(false, 99, 2),
        )
    }

    @Test
    fun `attack rhythm is normally four ticks with an occasional three tick turn`() {
        assertEquals(3, CorporealBeastLegacyCombat.attackRate(0))
        assertEquals(4, CorporealBeastLegacyCombat.attackRate(1))
        assertEquals(3, CorporealBeastLegacyCombat.attackRate(8))
    }

    @Test
    fun `footprint detects melee reach and stomp tiles`() {
        assertEquals(0, CorporealBeastLegacyCombat.distanceToFootprint(10, 10, 5, 12, 12))
        assertEquals(1, CorporealBeastLegacyCombat.distanceToFootprint(10, 10, 5, 15, 12))
        assertTrue(CorporealBeastRules.isUnderFootprint(10, 10, 5, 14, 14))
        assertFalse(CorporealBeastRules.isUnderFootprint(10, 10, 5, 15, 14))
    }

    @Test
    fun `area spell uses separate direct and splash maximums`() {
        assertEquals(40, CorporealBeastRules.areaMaximum(direct = true, split = false))
        assertEquals(30, CorporealBeastRules.areaMaximum(direct = false, split = false))
        assertEquals(30, CorporealBeastRules.areaMaximum(direct = true, split = true))
        assertEquals(20, CorporealBeastRules.areaMaximum(direct = false, split = true))
        assertTrue(CorporealBeastRules.splitCandidates(CoordGrid(100, 100, 0)).distinct().size >= 6)
    }

    @Test
    fun `only one dark core can spawn and only from valid triggers`() {
        assertFalse(CorporealBeastRules.coreSpawnFromHit(31, 0, coreAlive = false))
        assertTrue(CorporealBeastRules.coreSpawnFromHit(32, 0, coreAlive = false))
        assertFalse(CorporealBeastRules.coreSpawnFromHit(32, 0, coreAlive = true))
        assertTrue(CorporealBeastRules.coreSpawnFromAttack(999, 0, coreAlive = false))
        assertFalse(CorporealBeastRules.coreSpawnFromAttack(1_000, 0, coreAlive = false))
    }

    @Test
    fun `public lairs do not leak state across their separate rooms`() {
        val outer = CoordGrid(2993, 4254, 2)
        val inner = CoordGrid(2993, 4382, 2)
        assertTrue(CorporealBeastRules.roomMatches(outer, outer.translate(20, 0)))
        assertFalse(CorporealBeastRules.roomMatches(outer, inner))
        assertFalse(CorporealBeastRules.roomMatches(outer, CoordGrid(2993, 4254, 1)))
    }

    @Test
    fun `no-combat reset arms at exactly three hundred ticks`() {
        assertFalse(CorporealBeastRules.shouldReset(399, 100))
        assertTrue(CorporealBeastRules.shouldReset(400, 100))
    }
}
