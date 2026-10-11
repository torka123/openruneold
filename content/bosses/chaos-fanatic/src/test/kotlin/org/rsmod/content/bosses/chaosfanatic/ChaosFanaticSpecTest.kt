package org.rsmod.content.bosses.chaosfanatic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.bosses.spec.Selector

class ChaosFanaticSpecTest {
    @Test
    fun `combat profile keeps revision accurate speed style and max hit`() {
        assertEquals(listOf(CHAOS_FANATIC_NPC), CHAOS_FANATIC_SPEC.npcTypes)
        assertEquals(2, CHAOS_FANATIC_SPEC.stats.attackRate)

        val hit = CHAOS_FANATIC_SPEC.abilities.getValue("magic").hits().single()
        assertEquals(0..CHAOS_FANATIC_MAX_HIT, assertIs<DamageExpr.Roll>(hit.damage).range)
        assertEquals(HitType.Magic, hit.type)
        assertEquals(ProtectionPrayer.Standard, hit.protectionPrayer)
    }

    @Test
    fun `both specials are present and normal magic remains the common attack`() {
        assertEquals(setOf("magic", "disarm", "explosion"), CHAOS_FANATIC_SPEC.abilities.keys)
        val selector =
            assertIs<Selector.WeightedRandom>(CHAOS_FANATIC_SPEC.phases.getValue("combat").selector)
        assertEquals(6, selector.entries.single { it.ability == "magic" }.weight)
        assertTrue(selector.entries.any { it.ability == "disarm" })
        assertTrue(selector.entries.any { it.ability == "explosion" })
        assertEquals(2, DISARM_PROJECTILE_COUNT)
        assertEquals(6, EXPLOSION_PROJECTILE_COUNT)
    }

    @Test
    fun `every canonical attack quote is retained`() {
        assertEquals(6, CHAOS_FANATIC_QUOTES.size)
        assertTrue("BURN!" in CHAOS_FANATIC_QUOTES)
        assertTrue("I shall call him squidgy and he shall be my squidgy!!" in CHAOS_FANATIC_QUOTES)
    }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Projectile -> listOfNotNull(hit)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }
}
