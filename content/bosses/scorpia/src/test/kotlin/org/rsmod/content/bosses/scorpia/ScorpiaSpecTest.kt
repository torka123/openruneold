package org.rsmod.content.bosses.scorpia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.api.bosses.spec.AccuracyMode
import org.rsmod.api.bosses.spec.ImpactEffect
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.map.CoordGrid

class ScorpiaSpecTest {
    @Test
    fun `combat profile and guardian threshold are exact`() {
        assertEquals(listOf(SCORPIA_NPC), SCORPIA_SPEC.npcTypes)
        assertEquals(4, SCORPIA_SPEC.stats.attackRate)
        val hit = SCORPIA_SPEC.abilities.getValue("melee").hits().single()
        assertEquals(16, assertIs<DamageExpr.Roll>(hit.damage).range.last)
        assertEquals(HitType.Melee, hit.type)
        assertEquals(MeleeAttackType.Stab, hit.meleeType)
        val poison = assertIs<ImpactEffect.Poison>(hit.impactEffects.single())
        assertEquals(20, poison.damage)
        assertEquals(1, poison.chance)
        assertEquals(4, poison.outOf)
        assertEquals(20, SCORPIA_POISON_DAMAGE)
        assertEquals(2, SCORPIA_PRAYER_DRAIN)
        assertTrue(SCORPIA_SPEC.triggers.isEmpty())
        assertEquals(99, SCORPIA_GUARDIAN_TRIGGER_HP)
        assertEquals(3, SCORPIA_HEAL_RANGE)
        assertEquals(25, GUARDIAN_DESPAWN_TICKS)
    }

    @Test
    fun `offspring keeps its separate ranged timing hit and poison profile`() {
        assertEquals(listOf(SCORPIA_OFFSPRING_NPC), SCORPIA_OFFSPRING_SPEC.npcTypes)
        assertEquals(6, SCORPIA_OFFSPRING_SPEC.stats.attackRate)
        val hit = SCORPIA_OFFSPRING_SPEC.abilities.getValue("ranged").hits().single()
        assertEquals(HitType.Ranged, hit.type)
        assertEquals(0..2, assertIs<DamageExpr.Roll>(hit.damage).range)
        assertEquals(AccuracyMode.Always, hit.accuracy)
        val poison = assertIs<ImpactEffect.Poison>(hit.impactEffects.single())
        assertEquals(6, poison.damage)
        assertEquals(1, poison.chance)
        assertEquals(4, poison.outOf)
        assertEquals(2, SCORPIA_OFFSPRING_MAX_HIT)
        assertEquals(6, SCORPIA_OFFSPRING_POISON_DAMAGE)
        assertEquals(6, SCORPIA_OFFSPRING_ATTACK_RATE)
    }

    @Test
    fun `all three cache entrances and exits have distinct safe links`() {
        assertEquals("loc.wilderness_scorpion_entrance", SCORPIA_CAVERN)
        assertEquals("loc.wilderness_scorpion_exit", SCORPIA_CREVICE)
        assertEquals(3, SCORPIA_ENTRANCE_LINKS.size)
        assertEquals(3, SCORPIA_EXIT_LINKS.size)
        assertEquals(3, SCORPIA_ENTRANCE_LINKS.values.toSet().size)
        assertEquals(3, SCORPIA_EXIT_LINKS.values.toSet().size)

        assertEquals(CoordGrid(3233, 10332), SCORPIA_ENTRANCE_LINKS[CoordGrid(3231, 3936)])
        assertEquals(CoordGrid(3232, 10351), SCORPIA_ENTRANCE_LINKS[CoordGrid(3231, 3951)])
        assertEquals(CoordGrid(3243, 10351), SCORPIA_ENTRANCE_LINKS[CoordGrid(3241, 3949)])
        // Revision-240 cavern models are 4x2. These destinations are centered immediately
        // outside the matching model footprint instead of clipping into its crevice wall.
        assertEquals(CoordGrid(3232, 3938), SCORPIA_EXIT_LINKS[CoordGrid(3233, 10331)])
        assertEquals(CoordGrid(3232, 3950), SCORPIA_EXIT_LINKS[CoordGrid(3232, 10352)])
        assertEquals(CoordGrid(3241, 3948), SCORPIA_EXIT_LINKS[CoordGrid(3243, 10352)])
        assertTrue(SCORPIA_EXIT_LINKS.values.all { it.z < 10_000 })
        assertEquals(TeleportType.Exempt, SCORPIA_TRAVERSAL_TYPE)
    }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }
}
