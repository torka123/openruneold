package org.rsmod.game.entity

import dev.openrune.types.NpcServerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NpcCombatImmunitiesTest {
    @Test
    fun `runtime combat immunities default off`() {
        val npc = npc()

        assertFalse(npc.combatImmunities.toxins)
        assertFalse(npc.combatImmunities.statDrain)
        assertFalse(npc.combatImmunities.slayerTaskBonuses)
    }

    @Test
    fun `stat drain immunity rejects only combat level reductions`() {
        val npc = npc()
        npc.combatImmunities.statDrain = true

        npc.attackLvl = 1
        npc.strengthLvl = 2
        npc.defenceLvl = 3
        npc.rangedLvl = 4
        npc.magicLvl = 5
        npc.magicDefenceLvl = 6
        npc.hitpoints = 7

        assertEquals(100, npc.attackLvl)
        assertEquals(101, npc.strengthLvl)
        assertEquals(102, npc.defenceLvl)
        assertEquals(103, npc.rangedLvl)
        assertEquals(104, npc.magicLvl)
        assertEquals(104, npc.magicDefenceLvl)
        assertEquals(7, npc.hitpoints)
    }

    @Test
    fun `stat drain immunity permits boosts and normal npc drains remain unchanged`() {
        val immune = npc()
        immune.combatImmunities.statDrain = true
        immune.defenceLvl = 120
        assertEquals(120, immune.defenceLvl)

        val ordinary = npc()
        ordinary.defenceLvl = 12
        assertEquals(12, ordinary.defenceLvl)
    }

    private fun npc(): Npc =
        Npc(
            NpcServerType(
                id = 1,
                name = "combat immunity test npc",
                attack = 100,
                strength = 101,
                defence = 102,
                ranged = 103,
                magic = 104,
                hitpoints = 105,
            )
        )
}
