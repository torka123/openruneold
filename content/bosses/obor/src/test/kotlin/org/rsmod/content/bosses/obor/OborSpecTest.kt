package org.rsmod.content.bosses.obor

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

class OborSpecTest {
    @Test
    fun `combat chooses melee in range and ranged otherwise`() {
        assertEquals(6, OBOR_SPEC.stats.attackRate)
        val selector = assertIs<Selector.Conditional>(OBOR_SPEC.phases.getValue("combat").selector)
        assertEquals("melee", selector.branches.single().second)
        assertEquals("ranged", selector.fallback)
    }

    @Test
    fun `max hits and ranged prayer reduction match current encounter`() {
        val melee = OBOR_SPEC.abilities.getValue("melee").hits().single()
        val ranged = OBOR_SPEC.abilities.getValue("ranged").hits().single()
        assertEquals(22, assertIs<DamageExpr.Roll>(melee.damage).range.last)
        assertEquals(26, assertIs<DamageExpr.Roll>(ranged.damage).range.last)
        assertEquals(ProtectionPrayer.Reduce(50), ranged.protectionPrayer)
    }

    @Test
    fun `reward claim changes state once`() {
        val state = OborRunState()
        val id = InstanceId(7)
        state.initialize(id)
        assertFalse(state.beginClaim(id))
        state.markDefeated(id)
        assertTrue(state.beginClaim(id))
        state.markActive(id)
        assertEquals(OborRunPhase.Active, state.phase(id))
        assertFalse(state.beginClaim(id))
        state.markDefeated(id)
        assertTrue(state.beginClaim(id))
        assertFalse(state.beginClaim(id))
        assertEquals(OborRunPhase.Respawning, state.phase(id))
        assertFalse(state.beginClaim(id))
        assertEquals(OborRunPhase.Respawning, state.phase(id))
    }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }
}
