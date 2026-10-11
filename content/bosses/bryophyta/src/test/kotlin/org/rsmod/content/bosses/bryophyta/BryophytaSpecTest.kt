package org.rsmod.content.bosses.bryophyta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.instances.InstanceId

class BryophytaSpecTest {
    @Test
    fun `attack interval and one in five summon weight are exact`() {
        assertEquals(6, BRYOPHYTA_SPEC.stats.attackRate)
        val selector = assertIs<Selector.WeightedRandom>(BRYOPHYTA_SPEC.phases.getValue("combat").selector)
        assertEquals(5, selector.entries.sumOf { it.weight })
        assertEquals(1, selector.entries.single { it.ability == "summon_growthlings" }.weight)
    }

    @Test
    fun `both attacks max 16 and bypass protection prayers`() {
        val melee = BRYOPHYTA_SPEC.abilities.getValue("melee").hits().single()
        val magic = BRYOPHYTA_SPEC.abilities.getValue("magic").hits().single()
        assertEquals(16, assertIs<DamageExpr.Roll>(melee.damage).range.last)
        assertEquals(16, assertIs<DamageExpr.Roll>(magic.damage).range.last)
        assertEquals(ProtectionPrayer.Bypass, melee.protectionPrayer)
        assertEquals(ProtectionPrayer.Bypass, magic.protectionPrayer)
    }

    @Test
    fun `growthling summon gate is encounter scoped and cooldown safe`() {
        assertTrue(canSummonGrowthlings(active = 0, currentTick = 20, nextSummonTick = 20))
        assertFalse(canSummonGrowthlings(active = 1, currentTick = 100, nextSummonTick = 20))
        assertFalse(canSummonGrowthlings(active = 0, currentTick = 19, nextSummonTick = 20))
    }

    @Test
    fun `chest claim is idempotent`() {
        val state = BryophytaRunState()
        val id = InstanceId(9)
        state.initialize(id)
        assertFalse(state.beginClaim(id))
        state.markDefeated(id)
        assertTrue(state.beginClaim(id))
        assertFalse(state.beginClaim(id))
        assertEquals(BryophytaRunPhase.Respawning, state.phase(id))
        state.markActive(id)
        assertEquals(BryophytaRunPhase.Active, state.phase(id))
        assertFalse(state.beginClaim(id))
        state.markDefeated(id)
        assertTrue(state.beginClaim(id))
        assertFalse(state.beginClaim(id))
        assertEquals(BryophytaRunPhase.Respawning, state.phase(id))
    }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }
}
