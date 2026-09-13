package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.generic.killcount.KillcountNpcKillHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType

/**
 * M11 delivery mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md: retained W6/Fumus15221135 and
 * cache11283..11286/param46=-1. Real registered lethal queues, native death coroutine/removal,
 * native hero selection and killcount hook run here. Table rolls/delivery are a separate shared
 * boundary; these tests do not certify durable main Nex rewards or normal guard phase timing.
 */
@OptIn(InternalApi::class)
class NexBodyguardDeathDropsIntegrationTest {
    @Test
    fun `all four native guard deaths dispatch once to the winning hero without bones or Nex KC`() {
        val kills = mutableListOf<NpcDeathKillContext>()
        val objects = mock(ObjRepository::class.java)
        val f = world(kills, objects, duo = true)
        val partner = assertNotNull(f.partner)
        VarPlayerIntMapSetter.set(f.player, "varp.total_nex_kills", 17)
        VarPlayerIntMapSetter.set(partner, "varp.total_nex_kills", 23)
        val boss = f.npc(11278)
        val modifier = StandardNpcHitModifier(f.events)
        val nextPhases = listOf("SHADOW", "BLOOD", "ICE", "ZAROS")
        for (index in 0..3) {
            boss.hitpoints = listOf(2720, 2040, 1360, 680)[index] + 1
            f.damage(boss, 20)
            f.tick()
            val guard = f.npc(11283 + index)
            val coords = guard.coords
            guard.hitpoints = 31
            guard.queueHit(partner, 1, HitType.Magic, 30, modifier)
            f.damage(guard, 1)
            f.damage(guard, 1) // Duplicate lethal was queued while the same guard was alive.

            var ticks = 0
            while (f.encounters.of(boss).currentPhaseName != nextPhases[index] && ticks++ < 10) f
                .tick()
            assertEquals(nextPhases[index], f.encounters.of(boss).currentPhaseName)
            assertTrue(
                guard.isSlotAssigned,
                "Keep the existing phase handoff before native removal",
            )
            assertEquals(index, kills.size, "Do not dispatch drops before native death finishes")
            finishDeath(f, guard)

            assertEquals(index + 1, kills.size)
            val kill = kills.last()
            assertSame(guard, kill.npc)
            assertSame(partner, kill.hero, "The highest native damage contributor owns this drop")
            assertEquals(coords, kill.dropCoords)
            assertFalse(kill.npc.isSlotAssigned)
            assertEquals(17, f.player.vars["varp.total_nex_kills"])
            assertEquals(23, partner.vars["varp.total_nex_kills"])
            f.tick(2)
            assertEquals(index + 1, kills.size)
        }
        assertEquals(4, kills.map { it.lootTrackerEventId }.distinct().size)
        verifyNoInteractions(objects)
        assertTrue(
            mockingDetails(f.rewardService).invocations.none { it.method.name.startsWith("reward") }
        )
    }

    @Test
    fun `native deletion or room retirement during a guard death cancels its unfinished drop`() {
        for (retirement in listOf("guard", "session")) {
            val kills = mutableListOf<NpcDeathKillContext>()
            val objects = mock(ObjRepository::class.java)
            val f = world(kills, objects)
            f.reachGuard(11283)
            val guard = f.npc(11283)
            guard.hitpoints = 1
            f.damage(guard, 1)
            var ticks = 0
            while (guard.activeCoroutine == null && ticks++ < 10) f.tick()
            assertNotNull(guard.activeCoroutine)
            assertTrue(guard.isSlotAssigned)
            assertTrue(kills.isEmpty())

            if (retirement == "guard") f.repository.del(guard, Int.MAX_VALUE)
            else assertTrue(f.instances.terminate(f.session()))
            f.tick(40)

            assertFalse(guard.isSlotAssigned)
            assertNull(guard.activeCoroutine)
            assertTrue(kills.isEmpty(), retirement)
            verifyNoInteractions(objects)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `main Nex and reaver native deaths retain their existing no table routes`() {
        val kills = mutableListOf<NpcDeathKillContext>()
        val objects = mock(ObjRepository::class.java)
        val f = world(kills, objects)
        val reaver = f.summonReaver()
        reaver.hitpoints = 1
        f.damage(reaver, 1)
        finishDeath(f, reaver)
        assertTrue(kills.isEmpty())

        f.reachPhase(NexState.ZAROS)
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        boss.hitpoints = 1
        f.damage(boss, 1)
        var ticks = 0
        while (encounter.currentPhaseName != "RESETTING" && ticks++ < 40) f.tick()
        assertEquals("RESETTING", encounter.currentPhaseName)
        assertTrue(boss.isSlotAssigned)
        assertTrue(boss.hidden)
        assertTrue(kills.isEmpty())
        verifyNoInteractions(objects)
        assertEquals(
            1,
            mockingDetails(f.rewardService).invocations.count {
                it.method.name.startsWith("reward")
            },
        )
    }

    private fun world(
        kills: MutableList<NpcDeathKillContext>,
        objects: ObjRepository,
        duo: Boolean = false,
    ) =
        NexMinionTestWorld(
            duo = duo,
            nativeDeaths = true,
            deathObjects = objects,
            deathDropHooks =
                setOf(NpcDeathDropHook { error("Nex guards explicitly have no remains") }),
            deathKillHooks = linkedSetOf(NpcDeathKillHook { kills += it }, KillcountNpcKillHook()),
        )

    private fun finishDeath(f: NexMinionTestWorld, npc: Npc) {
        var ticks = 0
        while (npc.isSlotAssigned && ticks++ < 40) f.tick()
        assertFalse(npc.isSlotAssigned, "Native death did not finish")
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
