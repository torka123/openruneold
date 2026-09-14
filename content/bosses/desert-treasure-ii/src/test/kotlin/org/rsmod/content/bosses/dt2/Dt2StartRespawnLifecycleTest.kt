package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
class Dt2StartRespawnLifecycleTest {
    @Test
    fun `Whisperer disturb activates exactly four ticks later through the native lifecycle`() {
        withFixture(Dt2Boss.WHISPERER) { f ->
            val run = f.enter()
            val boss = run.boss
            assertEquals(Dt2State.PREPARING, run.state)
            assertEquals(Dt2Assets.WHISPERER_SPAWN_ID, boss.visType.id)
            assertTrue(f.manager.activateWhisperer(run.player, boss))
            val owner = assertNotNull(run.lifecycleTasks)
            assertEquals(104, run.startReadyCycle)
            assertEquals(1, owner.size)
            assertFalse(f.manager.activateWhisperer(run.player, boss))
            f.dispatchAt(103)
            assertEquals(Dt2State.STARTING, run.state)
            assertEquals(Dt2Assets.WHISPERER_SPAWN_ID, boss.visType.id)
            assertNotNull(f.manager.attackDenial(run.player, boss))
            f.clock.cycle = 104
            f.queueProcess.process()
            assertEquals(
                Dt2State.STARTING,
                run.state,
                "Default processing cannot consume owned work",
            )
            f.dispatchAt(104)
            assertEquals(Dt2State.ACTIVE, run.state)
            assertEquals(Dt2Assets.WHISPERER_ACTIVE_ID, boss.visType.id)
            assertEquals(104, run.killStartCycle)
            assertNull(f.manager.attackDenial(run.player, boss))
            assertEquals(0, owner.size)
            f.dispatchAt(105)
            assertEquals(104, run.killStartCycle)
            assertSame(boss, run.boss)
        }
    }

    @Test
    fun `Leviathan handholds cross inside at one tick then emerge for four more ticks`() {
        withFixture(Dt2Boss.LEVIATHAN) { f ->
            val run = f.enter()
            val waitingTile = run.player.coords
            val inside =
                assertNotNull(f.instances.localCoord(run.session, Dt2Assets.LEVIATHAN_INSIDE_ENTRY))
            f.start(run)
            val owner = assertNotNull(run.lifecycleTasks)
            assertEquals(101, run.startReadyCycle)
            assertEquals(waitingTile, run.player.coords)
            f.dispatchAt(100)
            assertEquals(waitingTile, run.player.coords)
            assertEquals(Dt2State.STARTING, run.state)
            f.clock.cycle = 101
            f.queueProcess.process()
            assertEquals(waitingTile, run.player.coords)
            f.dispatchAt(101)
            assertEquals(inside, run.player.coords)
            assertEquals(105, run.startReadyCycle)
            assertEquals(105, run.boss.actionDelay)
            assertEquals(Dt2State.STARTING, run.state)
            assertEquals(1, owner.size, "The nested activation remains queued after emergence")
            assertFalse(
                f.manager.activateLeviathan(run.player, Dt2Assets.LEVIATHAN_START_HANDHOLDS, inside)
            )
            f.dispatchAt(104)
            assertEquals(Dt2State.STARTING, run.state)
            assertNotNull(f.manager.attackDenial(run.player, run.boss))
            f.dispatchAt(105)
            assertEquals(Dt2State.ACTIVE, run.state)
            assertEquals(105, run.killStartCycle)
            assertEquals(inside, run.player.coords)
            assertEquals(0, owner.size)
            assertNull(f.manager.attackDenial(run.player, run.boss))
            f.dispatchAt(106)
            assertEquals(105, run.killStartCycle)
        }
    }

    @Test
    fun `captured deleted actors respawn once at each native deadline and Leviathan activates three ticks later`() {
        for (kind in listOf(Dt2Boss.DUKE, Dt2Boss.WHISPERER, Dt2Boss.LEVIATHAN)) {
            withFixture(kind) { f ->
                val run = f.enter()
                val boss = run.boss
                val deathStart = f.clock.cycle
                boss.hitpoints = 0
                assertSame(run, f.manager.beginBossDeath(boss), kind.name)
                assertNull(f.manager.beginBossDeath(boss), kind.name)
                f.npcs.del(boss, Int.MAX_VALUE)
                assertFalse(boss.isSlotAssigned, kind.name)
                assertNull(f.instances.instanceForNpc(boss), kind.name)
                f.dispatchAt(deathStart + 4)
                assertEquals(Dt2State.DYING, run.state, kind.name)
                f.manager.finishBossDeath(boss, run)
                val deadline =
                    when (kind) {
                        Dt2Boss.DUKE -> deathStart + DukeRules.RESPAWN_TICKS
                        Dt2Boss.WHISPERER -> f.clock.cycle + WHISPERER_RESPAWN_TICKS
                        else -> f.clock.cycle + LEVIATHAN_RESPAWN_TICKS
                    }
                val owner = assertNotNull(run.lifecycleTasks)
                val revision = owner.revision
                val actors = f.instances.npcsForInstance(run.session.id).toList()
                assertEquals(deadline, run.respawnCycle, kind.name)
                assertEquals(Dt2State.RESPAWNING, run.state, kind.name)
                assertEquals(1, owner.size, kind.name)
                f.dispatchAt(f.clock.cycle + 2)
                f.manager.finishBossDeath(boss, run)
                assertEquals(deadline, run.respawnCycle, kind.name)
                assertEquals(revision, owner.revision, "Duplicate completion must not rearm $kind")
                assertEquals(actors, f.instances.npcsForInstance(run.session.id), kind.name)
                f.dispatchAt(deadline - 1)
                assertSame(boss, run.boss, kind.name)
                f.clock.cycle = deadline
                f.queueProcess.process()
                assertSame(boss, run.boss, "Default processing cannot respawn $kind")
                f.dispatchAt(deadline)
                val freshBoss = run.boss
                assertNotSame(boss, freshBoss, kind.name)
                assertTrue(freshBoss.isSlotAssigned, kind.name)
                assertEquals(run.session.id, f.instances.instanceForNpc(freshBoss), kind.name)
                assertEquals(
                    if (kind == Dt2Boss.LEVIATHAN) Dt2State.STARTING else Dt2State.PREPARING,
                    run.state,
                    kind.name,
                )
                assertEquals(if (kind == Dt2Boss.LEVIATHAN) 1 else 0, owner.size, kind.name)
                f.manager.finishBossDeath(boss, run)
                f.dispatchAt(deadline + 2)
                assertSame(freshBoss, run.boss, kind.name)
                if (kind == Dt2Boss.LEVIATHAN) {
                    assertEquals(deadline + 3, run.startReadyCycle)
                    assertEquals(Dt2State.STARTING, run.state)
                    f.dispatchAt(deadline + 3)
                    assertEquals(Dt2State.ACTIVE, run.state)
                    assertEquals(deadline + 3, run.killStartCycle)
                    assertEquals(0, owner.size)
                } else {
                    assertEquals(Dt2State.PREPARING, run.state, kind.name)
                }
                assertSame(freshBoss, run.boss, kind.name)
            }
        }
    }

    @Test
    fun `reset cancels pending start emergence and respawn callbacks before replacing the actor`() {
        for (case in pendingCases) {
            withFixture(case.boss) { f ->
                val run = f.pending(case)
                val oldBoss = run.boss
                val owner = assertNotNull(run.lifecycleTasks)
                val revision = owner.revision
                val deadline = if (case.respawn) run.respawnCycle else run.startReadyCycle
                assertEquals(1, owner.size, case.toString())
                assertTrue(f.manager.reset(run.player), case.toString())
                val freshBoss = run.boss
                assertNotSame(oldBoss, freshBoss, case.toString())
                assertFalse(oldBoss.isSlotAssigned, case.toString())
                assertTrue(owner.revision > revision, case.toString())
                assertFalse(owner.isClosed, case.toString())
                assertEquals(0, owner.size, case.toString())
                assertEquals(Dt2State.PREPARING, run.state, case.toString())
                f.manager.finishBossDeath(oldBoss, run)
                f.dispatchAt(deadline + 5)
                f.queueProcess.processOwned(owner)
                assertSame(freshBoss, run.boss, case.toString())
                assertEquals(Dt2State.PREPARING, run.state, case.toString())
                assertEquals(0, owner.size, case.toString())
            }
        }
    }

    @Test
    fun `native departure clears pending lifecycle work and old callbacks cannot reach a fresh room`() {
        for ((index, case) in pendingCases.withIndex()) {
            withFixture(case.boss) { f ->
                val run = f.pending(case)
                val oldBoss = run.boss
                val owner = assertNotNull(run.lifecycleTasks)
                val deadline = if (case.respawn) run.respawnCycle else run.startReadyCycle
                when (index % 3) {
                    0 -> assertTrue(f.leave(run.player), case.toString())
                    1 -> f.events.publish(SessionStateEvent.Logout(run.player))
                    else -> assertTrue(f.instances.terminate(run.session), case.toString())
                }
                assertTrue(run.retired, case.toString())
                assertEquals(0, owner.size, case.toString())
                assertNull(f.instances.sessionForPlayer(run.player), case.toString())
                // Logout retires work immediately; native session resources close on reclaim.
                f.instances.tickReclaim(f.clock.cycle + 1)
                assertTrue(owner.isClosed, case.toString())
                assertTrue(f.npcList.none(), case.toString())
                val freshRun = f.enter(run.player)
                assertNotEquals(run.session.id, freshRun.session.id, case.toString())
                f.manager.finishBossDeath(oldBoss, run)
                f.dispatchAt(deadline + 5)
                f.queueProcess.processOwned(owner)
                assertSame(freshRun, f.run(freshRun.session), case.toString())
                assertFalse(freshRun.retired, case.toString())
                assertEquals(Dt2State.PREPARING, freshRun.state, case.toString())
                assertEquals(0, owner.size, case.toString())
                assertFailsWith<IllegalStateException> { owner.add {} }
            }
        }
    }

    private fun Dt2NativeLifecycleFixture.start(run: Dt2EncounterManager.Run) {
        when (bossKind) {
            Dt2Boss.WHISPERER -> assertTrue(manager.activateWhisperer(run.player, run.boss))
            Dt2Boss.LEVIATHAN -> {
                val handholds =
                    assertNotNull(instances.localCoord(run.session, RegionLocal(0, 32, 99, 22, 32)))
                assertTrue(
                    manager.activateLeviathan(
                        run.player,
                        Dt2Assets.LEVIATHAN_START_HANDHOLDS,
                        handholds,
                    )
                )
            }
            else -> error("No start interaction in this lifecycle test for $bossKind")
        }
    }

    private fun Dt2NativeLifecycleFixture.pending(case: PendingCase): Dt2EncounterManager.Run {
        val run = enter()
        if (case.respawn) {
            run.boss.hitpoints = 0
            assertSame(run, manager.beginBossDeath(run.boss))
            npcs.del(run.boss, Int.MAX_VALUE)
            manager.finishBossDeath(run.boss, run)
        } else {
            start(run)
            if (case.emergence) dispatchAt(clock.cycle + 1)
        }
        return run
    }

    /**
     * Dispatch the actual owner at its observed clock; Duke uses StartCycle, the others LateCycle.
     */
    private fun Dt2NativeLifecycleFixture.dispatchAt(cycle: Int) {
        require(cycle >= clock.cycle)
        clock.cycle = cycle
        if (bossKind == Dt2Boss.DUKE) events.publish(GameLifecycle.StartCycle)
        else events.publish(GameLifecycle.LateCycle)
    }

    private fun withFixture(kind: Dt2Boss, action: (Dt2NativeLifecycleFixture) -> Unit) {
        try {
            Dt2NativeLifecycleFixture(kind).use(action)
        } finally {
            framework().clearInlineMocks()
        }
    }

    private data class PendingCase(
        val boss: Dt2Boss,
        val respawn: Boolean,
        val emergence: Boolean = false,
    )

    private val pendingCases =
        listOf(
            PendingCase(Dt2Boss.DUKE, respawn = true),
            PendingCase(Dt2Boss.WHISPERER, respawn = false),
            PendingCase(Dt2Boss.WHISPERER, respawn = true),
            PendingCase(Dt2Boss.LEVIATHAN, respawn = false),
            PendingCase(Dt2Boss.LEVIATHAN, respawn = false, emergence = true),
            PendingCase(Dt2Boss.LEVIATHAN, respawn = true),
        )

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
