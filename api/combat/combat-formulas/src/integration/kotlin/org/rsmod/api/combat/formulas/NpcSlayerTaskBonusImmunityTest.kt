package org.rsmod.api.combat.formulas

import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Npc

class NpcSlayerTaskBonusImmunityTest {
    @Test
    fun GameTestState.`runtime immunity suppresses bonuses without changing assignment`() =
        runInjectedGameTest(NoDependencies::class) {
            player.setVarp("varp.slayer_target", TEST_TASK)
            val type = NpcServerType(id = 99, name = "task target")
            type.slayerTaskIds = intArrayOf(TEST_TASK)
            val npc = Npc(type)

            assertTrue(type.isSlayerTask(player))
            assertTrue(npc.isSlayerTask(player))

            npc.combatImmunities.slayerTaskBonuses = true

            assertTrue(type.isSlayerTask(player), "The underlying Slayer assignment must remain")
            assertFalse(npc.isSlayerTask(player), "Only task-only combat bonuses are suppressed")
        }

    private class NoDependencies @Inject constructor()

    private companion object {
        const val TEST_TASK = 1234
    }
}
