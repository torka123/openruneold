package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
class VardorvisNativeLifecycleTest {
    @Test
    fun `native entry binds selection before the first special and overdue recurrence waits for QTE close`() {
        Dt2NativeLifecycleFixture().use { f ->
            val run = f.activate()
            val owner = assertNotNull(run.vardorvisTasks)
            val encounter = assertNotNull(f.bossEncounters.existing(run.boss))
            val initialTask = assertNotNull(run.vardorvisSpecialTask)
            assertNull(encounter.vardorvisSpecialHistory().previous)
            assertEquals(110, run.nextSpecialCycle)
            assertEquals(1, owner.size)
            f.tickTo(109)
            f.queueProcess.process()
            assertNull(run.activeSpecial)
            assertSame(initialTask, run.vardorvisSpecialTask)
            f.tickTo(110)
            assertEquals(VardorvisSpecial.DARTING_SPIKES, run.activeSpecial)
            val oracle = Dt2SeededRng(42)
            oracle.nextInt(Int.MAX_VALUE)
            assertEquals(120 + oracle.nextInt(3), run.nextSpecialCycle)
            val recurrence = assertNotNull(run.vardorvisSpecialTask)
            assertNotSame(initialTask, recurrence)
            assertEquals(1, owner.size)

            // Keep the existing native QTE endpoint pending while the special deadline elapses.
            run.vardorvisQteOpen = true
            run.vardorvisQteEndCycle = Int.MAX_VALUE
            run.vardorvisQteRemaining += 6
            val deadline = run.nextSpecialCycle
            f.tickTo(deadline + 3)
            assertSame(recurrence, run.vardorvisSpecialTask)
            assertEquals(deadline, run.nextSpecialCycle)
            assertEquals(1, owner.size)
            assertTrue(f.manager.destroyVardorvisSpore(run.player, 6))
            assertFalse(run.vardorvisQteOpen)
            assertFalse(f.manager.destroyVardorvisSpore(run.player, 6))
            f.tickTo(f.clock.cycle + 1)
            val next = assertNotNull(run.vardorvisSpecialTask)
            assertNotSame(recurrence, next)
            assertTrue(run.nextSpecialCycle in f.clock.cycle + 10..f.clock.cycle + 12)
            assertEquals(1, owner.size)
            f.events.publish(GameLifecycle.LateCycle)
            assertSame(
                next,
                run.vardorvisSpecialTask,
                "A repeated dispatch cannot replay the consumed opportunity",
            )
        }
    }

    @Test
    fun `forced specials replace both the initial and recurrent native deadlines`() {
        Dt2NativeLifecycleFixture().use { f ->
            val run = f.activate()
            val owner = assertNotNull(run.vardorvisTasks)
            val initial = assertNotNull(run.vardorvisSpecialTask)
            f.tickTo(105)
            assertTrue(f.manager.forcePhase(run.player, "AXES"))
            val forced = assertNotNull(run.vardorvisSpecialTask)
            assertNotSame(initial, forced)
            assertEquals(VardorvisSpecial.AXES, run.activeSpecial)
            assertTrue(run.nextSpecialCycle in 115..117)
            val replacedDeadline = run.nextSpecialCycle
            assertEquals(1, owner.size)
            f.tickTo(110)
            assertSame(forced, run.vardorvisSpecialTask, "The old +10 deadline was cancelled")
            assertEquals(VardorvisSpecial.AXES, run.activeSpecial)

            assertTrue(f.manager.forcePhase(run.player, "HEAD_GAZE"))
            val replacement = assertNotNull(run.vardorvisSpecialTask)
            assertNotSame(forced, replacement)
            assertEquals(VardorvisSpecial.HEAD_GAZE, run.activeSpecial)
            assertTrue(run.nextSpecialCycle in 120..122)
            f.tickTo(replacedDeadline)
            assertSame(replacement, run.vardorvisSpecialTask)
            assertEquals(1, owner.size)
            val deadline = run.nextSpecialCycle
            f.tickTo(deadline - 1)
            assertSame(replacement, run.vardorvisSpecialTask)
            f.tickTo(deadline)
            assertNotSame(replacement, run.vardorvisSpecialTask)
            assertEquals(VardorvisSpecial.DARTING_SPIKES, run.activeSpecial)
            assertEquals(1, owner.size)
        }
    }

    @Test
    fun `captured native death completion respawns once seventeen ticks later after actor deletion`() {
        Dt2NativeLifecycleFixture().use { f ->
            val run = f.activate()
            val oldBoss = run.boss
            val oldEncounter = assertNotNull(f.bossEncounters.existing(oldBoss))
            val owner = assertNotNull(run.vardorvisTasks)
            oldBoss.hitpoints = 0
            assertSame(run, f.manager.beginBossDeath(oldBoss))
            assertNull(f.manager.beginBossDeath(oldBoss))
            assertEquals(0, owner.size)
            assertNull(run.vardorvisSpecialTask)
            f.npcs.del(oldBoss, Int.MAX_VALUE)
            assertFalse(oldBoss.isSlotAssigned)
            assertTrue(oldEncounter.isEnded)
            assertNull(f.instances.instanceForNpc(oldBoss))
            f.tickTo(104)
            assertEquals(Dt2State.DYING, run.state)
            assertEquals(0, owner.size)
            f.manager.finishBossDeath(oldBoss, run)
            assertEquals(121, run.respawnCycle)
            assertEquals(Dt2State.RESPAWNING, run.state)
            assertEquals(1, owner.size)
            assertFalse(owner.isClosed)
            f.tickTo(106)
            f.manager.finishBossDeath(oldBoss, run)
            assertEquals(121, run.respawnCycle)
            assertEquals(1, owner.size)
            f.tickTo(120)
            assertSame(oldBoss, run.boss)
            assertTrue(f.instances.npcsForInstance(run.session.id).isEmpty())
            f.tickTo(121)
            val freshBoss = run.boss
            assertNotSame(oldBoss, freshBoss)
            assertTrue(freshBoss.isSlotAssigned)
            assertEquals(Dt2State.PREPARING, run.state)
            assertTrue(freshBoss.movementLocked)
            val freshEncounter = assertNotNull(f.bossEncounters.existing(freshBoss))
            assertNotSame(oldEncounter, freshEncounter)
            assertNull(freshEncounter.vardorvisSpecialHistory().previous)
            assertEquals(listOf(freshBoss), f.instances.npcsForInstance(run.session.id))
            assertEquals(0, owner.size)
            f.tickTo(160)
            f.manager.finishBossDeath(oldBoss, run)
            assertSame(freshBoss, run.boss)
            assertEquals(Dt2State.PREPARING, run.state)
            assertEquals(0, owner.size)
        }
    }

    @Test
    fun `reset retires pending work and the old encounter before a fresh dormant actor`() {
        Dt2NativeLifecycleFixture().use { f ->
            val run = f.activate()
            val oldBoss = run.boss
            val oldEncounter = assertNotNull(f.bossEncounters.existing(oldBoss))
            val owner = assertNotNull(run.vardorvisTasks)
            assertTrue(f.manager.forcePhase(run.player, "STRANGLE"))
            assertEquals(VardorvisSpecial.STRANGLE, oldEncounter.vardorvisSpecialHistory().previous)
            val revision = owner.revision
            assertEquals(1, owner.size)
            assertTrue(f.manager.reset(run.player))
            assertFalse(oldBoss.isSlotAssigned)
            assertTrue(oldEncounter.isEnded)
            val freshBoss = run.boss
            val freshEncounter = assertNotNull(f.bossEncounters.existing(freshBoss))
            assertNotSame(oldBoss, freshBoss)
            assertNotSame(oldEncounter, freshEncounter)
            assertNull(freshEncounter.vardorvisSpecialHistory().previous)
            assertTrue(owner.revision > revision)
            assertEquals(0, owner.size)
            assertNull(run.vardorvisSpecialTask)
            assertEquals(Dt2State.PREPARING, run.state)
            f.tickTo(140)
            assertSame(freshBoss, run.boss)
            assertNull(run.activeSpecial)
            assertEquals(0, owner.size)
            assertNull(f.manager.attackDenial(run.player, freshBoss))
            run.vardorvisNextPathCheckCycle = Int.MAX_VALUE
            assertEquals(150, run.nextSpecialCycle)
            assertEquals(1, owner.size)
            assertNull(freshEncounter.vardorvisSpecialHistory().previous)
        }
    }

    @Test
    fun `native departure closes active special and deleted actor respawn work`() {
        for (respawning in listOf(false, true)) for (departure in
            listOf("leave", "logout", "terminate")) {
            try {
                Dt2NativeLifecycleFixture().use { f ->
                    val run = f.activate()
                    val boss = run.boss
                    val owner = assertNotNull(run.vardorvisTasks)
                    if (respawning) {
                        boss.hitpoints = 0
                        assertSame(run, f.manager.beginBossDeath(boss))
                        f.npcs.del(boss, Int.MAX_VALUE)
                        f.manager.finishBossDeath(boss, run)
                    }
                    assertEquals(1, owner.size)
                    when (departure) {
                        "leave" -> assertTrue(f.leave(run.player))
                        "logout" -> f.events.publish(SessionStateEvent.Logout(run.player))
                        else -> assertTrue(f.instances.terminate(run.session))
                    }
                    val context = "respawning=$respawning departure=$departure"
                    assertTrue(run.retired, context)
                    assertEquals(0, owner.size, context)
                    assertNull(f.instances.sessionForPlayer(run.player), context)
                    // Logout clears pending work immediately; native session resource closure is
                    // deferred.
                    f.instances.tickReclaim(f.clock.cycle + 1)
                    assertTrue(owner.isClosed, context)
                    assertTrue(f.npcList.none(), context)
                    f.tickTo(200)
                    f.queueProcess.processOwned(owner)
                    f.manager.finishBossDeath(boss, run)
                    assertTrue(f.npcList.none(), context)
                    assertEquals(0, f.worldQueues.size, context)
                    assertFailsWith<IllegalStateException> { owner.add {} }
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    private fun Dt2NativeLifecycleFixture.activate(): Dt2EncounterManager.Run {
        val run = enter()
        assertNotNull(run.vardorvisTasks)
        assertNotNull(
            bossEncounters.existing(run.boss),
            "Entry must bind before any native combat lookup",
        )
        assertNull(manager.attackDenial(run.player, run.boss))
        run.vardorvisNextPathCheckCycle = Int.MAX_VALUE
        return run
    }

    private fun Dt2NativeLifecycleFixture.tickTo(target: Int) {
        require(target >= clock.cycle)
        while (clock.cycle < target) {
            events.publish(GameLifecycle.StartCycle)
            queueProcess.process()
            clock.tick()
            events.publish(GameLifecycle.LateCycle)
        }
    }

    @AfterTest fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        }
    }
}
