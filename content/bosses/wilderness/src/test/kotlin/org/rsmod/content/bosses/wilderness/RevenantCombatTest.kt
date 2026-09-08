package org.rsmod.content.bosses.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RevenantCombatTest {
    @Test
    fun `all ordinary revenant forms have explicit model compatible animations`() {
        assertEquals(11, REVENANT_COMBAT_DEFINITIONS.size)
        assertEquals(
            setOf(
                "npc.wild_cave_imp",
                "npc.wild_cave_goblin",
                "npc.wild_cave_pyrefiend",
                "npc.wild_cave_hobgoblin",
                "npc.wild_cave_cyclops",
                "npc.wild_cave_hellhound",
                "npc.wild_cave_demon",
                "npc.wild_cave_ork",
                "npc.wild_cave_darkbeast",
                "npc.wild_cave_knight",
                "npc.wild_cave_dragon",
            ),
            REVENANT_COMBAT_DEFINITIONS.map { it.npcInternal }.toSet(),
        )
        assertTrue(
            REVENANT_COMBAT_DEFINITIONS.all {
                it.attackAnimation > 0 && it.blockAnimation > 0 && it.deathAnimation > 0
            }
        )
    }

    @Test
    fun `ordinary revenant max hits match the modern eleven form progression`() {
        assertEquals(
            listOf(2, 3, 5, 8, 16, 14, 18, 20, 23, 27, 30),
            REVENANT_COMBAT_DEFINITIONS.map { it.maxHit },
        )
    }

    @Test
    fun `ordinary revenants attack every five ticks and use capped healing`() {
        assertEquals(5, REVENANT_ATTACK_RATE)
        assertEquals(20, REVENANT_HEAL_AMOUNT)
        assertEquals(15, REVENANT_HEAL_COOLDOWN_TICKS)
        assertEquals(8, REVENANT_HEAL_MIN_USES)
        assertEquals(25, REVENANT_HEAL_MAX_USES)
        assertEquals(5, REVENANT_MAGIC_FREEZE_TICKS)
    }

    @Test
    fun `revenants cannot heal from zero health or after death is queued`() {
        assertTrue(revenantCanHeal(hitpoints = 49, baseHitpoints = 100, deathQueued = false))
        assertTrue(!revenantCanHeal(hitpoints = 50, baseHitpoints = 100, deathQueued = false))
        assertTrue(!revenantCanHeal(hitpoints = 1, baseHitpoints = 100, deathQueued = true))
        assertTrue(!revenantCanHeal(hitpoints = 0, baseHitpoints = 100, deathQueued = true))
    }
}
