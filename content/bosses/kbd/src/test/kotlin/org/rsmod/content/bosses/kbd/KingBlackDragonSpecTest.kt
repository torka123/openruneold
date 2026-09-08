package org.rsmod.content.bosses.kbd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ImpactEffect
import org.rsmod.api.bosses.spec.ImpactProtection
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.combat.commons.DragonfireProtection

class KingBlackDragonSpecTest {
    @Test
    fun `attack distribution and breath effects match the documented profile`() {
        assertEquals(4, KING_BLACK_DRAGON_SPEC.stats.attackRate)
        val selector = assertIs<Selector.WeightedRandom>(
            KING_BLACK_DRAGON_SPEC.phases.getValue("combat").selector,
        )
        assertEquals(
            mapOf("melee" to 3, "dragonfire" to 3, "poison_breath" to 1, "freeze_breath" to 1, "shock_breath" to 1),
            selector.entries.associate { it.ability to it.weight },
        )

        assertHit("melee", HitType.Melee, 25)
        assertHit("dragonfire", HitType.KbdDragonfire, 65)
        val poison = assertIs<ImpactEffect.Poison>(assertHit("poison_breath", HitType.KbdSpecialDragonfire, 50).impactEffects.single())
        val freeze = assertIs<ImpactEffect.Freeze>(assertHit("freeze_breath", HitType.KbdSpecialDragonfire, 50).impactEffects.single())
        val drain = assertIs<ImpactEffect.StatDrain>(assertHit("shock_breath", HitType.KbdSpecialDragonfire, 50).impactEffects.single())
        assertEquals(Triple(8, 1, 2), Triple(poison.damage, poison.chance, poison.outOf))
        assertEquals(Triple(10, 1, 2), Triple(freeze.ticks, freeze.chance, freeze.outOf))
        assertEquals(2, drain.amount)
        assertEquals(5, drain.stats.size)
        assertEquals(ImpactProtection.KingBlackDragon, poison.protection)
        assertEquals(ImpactProtection.KingBlackDragon, freeze.protection)
        assertEquals(ImpactProtection.KingBlackDragon, drain.protection)
    }

    @Test
    fun `all KBD dragonfire protection combinations match the OSRS table`() {
        val rows = listOf(
            ProtectionRow(false, false, false, false, 65, 50),
            ProtectionRow(true, false, false, false, 15, 10),
            ProtectionRow(false, true, false, false, 20, 15),
            ProtectionRow(true, true, false, false, 15, 10),
            ProtectionRow(false, false, true, false, 50, 50),
            ProtectionRow(true, false, true, false, 0, 50),
            ProtectionRow(false, true, true, false, 0, 10),
            ProtectionRow(true, true, true, false, 0, 10),
            ProtectionRow(false, false, false, true, 5, 15),
            ProtectionRow(true, false, false, true, 0, 15),
            ProtectionRow(false, true, false, true, 0, 10),
            ProtectionRow(true, true, false, true, 0, 10),
        )
        rows.forEach { row ->
            assertEquals(row.standard, row.resolve(special = false), "standard: $row")
            assertEquals(row.special, row.resolve(special = true), "special: $row")
        }
    }

    private fun assertHit(ability: String, type: HitType, maxHit: Int): Effect.Hit {
        val hit = KING_BLACK_DRAGON_SPEC.abilities.getValue(ability).hits().single()
        assertEquals(type, hit.type)
        assertEquals(maxHit, assertIs<DamageExpr.Roll>(hit.damage).range.last)
        return hit
    }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Projectile -> listOfNotNull(hit)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }

    private data class ProtectionRow(
        val shield: Boolean,
        val protect: Boolean,
        val antifire: Boolean,
        val superAntifire: Boolean,
        val standard: Int,
        val special: Int,
    ) {
        fun resolve(special: Boolean): Int =
            DragonfireProtection.resolveKbdMaxHit(
                special = special,
                shield = shield,
                protect = protect,
                antifire = antifire,
                superAntifire = superAntifire,
            )
    }
}
