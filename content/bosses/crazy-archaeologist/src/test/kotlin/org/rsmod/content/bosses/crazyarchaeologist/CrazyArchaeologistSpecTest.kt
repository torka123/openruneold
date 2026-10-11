package org.rsmod.content.bosses.crazyarchaeologist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.map.CoordGrid

class CrazyArchaeologistSpecTest {
    @Test
    fun `standard attacks use exact speed styles and max hit`() {
        assertEquals(listOf(CRAZY_ARCHAEOLOGIST_NPC), CRAZY_ARCHAEOLOGIST_SPEC.npcTypes)
        assertEquals(3, CRAZY_ARCHAEOLOGIST_SPEC.stats.attackRate)

        val melee = CRAZY_ARCHAEOLOGIST_SPEC.abilities.getValue("melee").hits().single()
        val ranged = CRAZY_ARCHAEOLOGIST_SPEC.abilities.getValue("ranged").hits().single()
        assertEquals(HitType.Melee, melee.type)
        assertEquals(HitType.Ranged, ranged.type)
        assertEquals(CRAZY_STANDARD_MAX_HIT, assertIs<DamageExpr.Roll>(melee.damage).range.last)
        assertEquals(CRAZY_STANDARD_MAX_HIT, assertIs<DamageExpr.Roll>(ranged.damage).range.last)
    }

    @Test
    fun `rain attack is present with three book targets and exact max hit`() {
        assertEquals(24, CRAZY_SPECIAL_MAX_HIT)
        assertEquals(4, RAIN_DELAY_TICKS)
        val center = CoordGrid(10, 10)
        val tiles = rainTiles(center)
        assertEquals(3, tiles.size)
        assertEquals(3, tiles.toSet().size)
        assertTrue(center in tiles)

        val selector =
            assertIs<Selector.WeightedRandom>(CRAZY_ARCHAEOLOGIST_SPEC.phases.getValue("combat").selector)
        assertTrue(selector.entries.any { it.ability == "rain_of_knowledge" })
    }

    @Test
    fun `canonical overhead lines are retained`() {
        assertEquals(6, CRAZY_ARCHAEOLOGIST_QUOTES.size)
        assertEquals("Rain of knowledge!", RAIN_QUOTE)
        assertTrue("You belong in a museum!" in CRAZY_ARCHAEOLOGIST_QUOTES)
    }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Projectile -> listOfNotNull(hit)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }
}
