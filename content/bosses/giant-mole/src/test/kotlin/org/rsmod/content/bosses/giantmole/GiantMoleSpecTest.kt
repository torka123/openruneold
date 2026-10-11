package org.rsmod.content.bosses.giantmole

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.map.CoordGrid

class GiantMoleSpecTest {
    @Test
    fun `custom encounter has exact simple melee profile`() {
        assertEquals(listOf(GIANT_MOLE_NPC), GIANT_MOLE_SPEC.npcTypes)
        assertEquals(4, GIANT_MOLE_SPEC.stats.attackRate)
        assertEquals(0, GIANT_MOLE_SPEC.stats.aggressionRadius)

        val hit = GIANT_MOLE_SPEC.abilities.getValue("melee").hits().single()
        assertEquals(21, assertIs<DamageExpr.Roll>(hit.damage).range.last)
        assertEquals(HitType.Melee, hit.type)
        assertEquals(MeleeAttackType.Crush, hit.meleeType)
        assertEquals(ProtectionPrayer.Standard, hit.protectionPrayer)
    }

    @Test
    fun `burrowing and relocation are deliberately absent`() {
        assertEquals(setOf("melee"), GIANT_MOLE_SPEC.abilities.keys)
        assertEquals(setOf("combat"), GIANT_MOLE_SPEC.phases.keys)
        assertFalse(GIANT_MOLE_SPEC.abilities.keys.any { "burrow" in it })
        assertFalse(GIANT_MOLE_SPEC.triggers.isNotEmpty())

        val selector =
            assertIs<Selector.WeightedRandom>(GIANT_MOLE_SPEC.phases.getValue("combat").selector)
        assertEquals(listOf("melee"), selector.entries.map { it.ability })
    }

    @Test
    fun `public entry uses the verified Mole Hole arrival tile`() {
        assertEquals(CoordGrid(1752, 5137), GIANT_MOLE_ENTRY)
    }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }
}
