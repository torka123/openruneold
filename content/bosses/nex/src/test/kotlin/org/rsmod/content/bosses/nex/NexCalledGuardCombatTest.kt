package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.game.hit.HitType

/** Capture3284 t631-636 and808 t2414-2426: calls do not replace the elemental attack phase. */
class NexCalledGuardCombatTest {
    @Test
    fun `every called guard preserves elemental normals even at the incoming damage floor`() {
        val cases =
            listOf(
                Triple(11283, "SMOKE", 1997),
                Triple(11284, "SHADOW", 1999),
                Triple(11285, "BLOOD", 2000),
                Triple(11286, "ICE", 2004),
            )
        for ((guard, phase, projectile) in cases) {
            val f = NexMinionTestWorld()
            f.reachGuard(guard)
            val boss = f.npc(11278)
            boss.hitpoints = listOf(2550, 1870, 1190, 510)[guard - 11283]
            f.player.coords = NEX_SPAWN.translateX(-4)
            assertEquals(phase, f.encounters.of(boss).currentPhaseName)
            f.attack(boss)
            assertEquals(projectile, f.projectiles().last().spotanim)
            assertEquals(
                if (guard == 11284) HitType.Ranged else HitType.Magic,
                f.hits().single().second.type,
            )
            assertNotNull(
                f.manager.attackDenial(f.player, boss),
                "Incoming damage immunity remains gated",
            )
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `bodyguard call neither resets native ordinary count nor cancels melee continuation`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        f.player.coords = NEX_SPAWN.translateX(-4)
        repeat(2) {
            f.attack(boss)
            f.tick(4)
        }
        f.player.coords = NEX_SPAWN.translateX(-1)
        f.selectionRolls.add(0)
        f.attack(boss) // Third normal starts three-melee sequence.
        f.reachGuard(11283)
        boss.hitpoints = 2550
        f.tick(3)
        repeat(2) {
            f.attack(boss)
            f.tick(4)
        }
        assertEquals(
            listOf(HitType.Melee, HitType.Melee, HitType.Melee),
            f.hits().takeLast(3).map { it.second.type },
        )
        assertEquals(listOf(3), f.selectionBounds)
        f.attack(boss)
        assertEquals("SMOKE.special", encounter.recentAttackTraces().last().ability)
        assertEquals(5, f.hits().size)
    }

    @Test
    fun `called guard restores Nex targeting without freezing her ordinary phase`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        f.reachGuard(11283)
        boss.mode = null
        f.tick()
        assertNotNull(boss.mode)
        assertEquals("SMOKE", f.encounters.of(boss).currentPhaseName)
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
