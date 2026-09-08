package org.rsmod.api.combat.formulas

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc

class NpcSlayerTaskBonusPolicyTest {
    @Test
    fun `ordinary npc preserves an assigned slayer bonus`() {
        val npc = Npc(NpcServerType(id = 1, name = "ordinary"))

        assertTrue(npc.allowsSlayerTaskBonuses(assignedTask = true))
        assertFalse(npc.allowsSlayerTaskBonuses(assignedTask = false))
    }

    @Test
    fun `runtime immunity suppresses only the combat bonus`() {
        val npc = Npc(NpcServerType(id = 2, name = "awakened leviathan"))
        npc.combatImmunities.slayerTaskBonuses = true

        assertFalse(npc.allowsSlayerTaskBonuses(assignedTask = true))
        assertFalse(npc.allowsSlayerTaskBonuses(assignedTask = false))
    }
}
