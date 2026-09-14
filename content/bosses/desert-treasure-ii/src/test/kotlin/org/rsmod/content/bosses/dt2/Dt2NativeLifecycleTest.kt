package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid

@OptIn(InternalApi::class)
class Dt2NativeLifecycleTest {
    @AfterTest fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `native entry owns the typed run and duplicate entry cannot create a second boss`() {
        Dt2NativeLifecycleFixture().use { f ->
            val player = f.player()
            val run = f.enter(player)
            assertSame(run, f.run(run.session))
            assertEquals(player.uid, run.playerUid)
            assertFalse(run.retired)
            assertEquals(listOf(run.boss), f.instances.npcsForInstance(run.session.id))
            assertFalse(f.manager.enter(player, f.bossKind, Dt2Difficulty.NORMAL, seed = 99L))
            assertSame(run, f.run(run.session))
            assertEquals(listOf(run.boss), f.instances.npcsForInstance(run.session.id))
        }
    }

    @Test
    fun `native logout disconnect death and external movement retire each owner once`() {
        val endings =
            listOf<Pair<String, (Dt2NativeLifecycleFixture, Player, InstanceSession) -> Unit>>(
                "leave" to
                    { f, player, session ->
                        f.instances.leave(player, session, f.clock.cycle)
                    },
                "logout event" to
                    { f, player, _ ->
                        f.events.publish(SessionStateEvent.Logout(player))
                    },
                "disconnect" to
                    { f, player, _ ->
                        player.clientDisconnected.set(true)
                        f.nativeLifecycle.onPostTick(player)
                    },
                "death" to { f, player, _ -> f.instances.handleDeath(player, f.clock.cycle) },
                "external movement event" to
                    { f, player, _ ->
                        val previous = player.coords
                        player.coords = CoordGrid(3222, 3218)
                        f.events.publish(PlayerMovementEvent.CoordsMovedEvent(player, previous))
                    },
            )
        for (bossKind in listOf(Dt2Boss.VARDORVIS, Dt2Boss.LEVIATHAN)) {
            for ((name, end) in endings) {
                try {
                    Dt2NativeLifecycleFixture(bossKind).use { f ->
                        val player = f.player()
                        val run = f.enter(player)
                        val actor = f.attachActor(run, mechanic = false)
                        end(f, player, run.session)
                        f.instances.tickReclaim(f.clock.cycle + 1)
                        assertTrue(run.retired, "$bossKind $name")
                        assertEquals(Dt2State.INVALIDATED, run.state, name)
                        assertFalse(f.manager.isActive(player), name)
                        assertNull(f.instances.sessionForPlayer(player), name)
                        assertNull(f.instances.sessionForId(run.session.id), name)
                        assertNull(f.instances.instanceForNpc(actor), name)
                        assertTrue(f.npcList.none(), name)
                        assertNull(player.lootDropDuration, name)
                        assertEquals(1, f.closeCount(player), name)
                        f.events.publish(
                            InstancePlayerLeaveEvent(
                                player,
                                run.session.key,
                                run.session.id,
                                run.session.owner,
                            )
                        )
                        assertFalse(f.instances.terminate(run.session), name)
                        assertEquals(1, f.closeCount(player), name)
                    }
                } finally {
                    framework().clearInlineMocks()
                }
            }
        }
    }

    @Test
    fun `native lookup requires the exact player UID and registered mechanic NPC identity`() {
        Dt2NativeLifecycleFixture().use { f ->
            val player = f.player()
            val run = f.enter(player)
            val helper = f.attachActor(run, mechanic = true)
            val sceneActor = f.attachActor(run, mechanic = false)
            assertTrue(f.manager.isOwnedBy(player, run.boss))
            assertTrue(f.manager.isEncounterNpc(helper))
            assertFalse(f.manager.isEncounterNpc(sceneActor))
            assertFalse(f.manager.isOwnedBy(player, sceneActor))

            val replacementLogin =
                f.player(2).apply {
                    uuid = player.uuid
                    accountId = player.accountId
                    characterId = player.characterId
                    accountHash = player.accountHash
                    assignUid()
                }
            assertFalse(f.manager.isActive(replacementLogin))
            assertFalse(f.manager.isOwnedBy(replacementLogin, run.boss))
            player.uuid = 999L
            player.assignUid()
            assertNotEquals(run.playerUid, player.uid)
            assertFalse(f.manager.isActive(player))
            assertFalse(f.manager.isOwnedBy(player, run.boss))
        }
    }

    @Test
    fun `helper completion consumes progress once while native ownership remains until deletion`() {
        Dt2NativeLifecycleFixture().use { f ->
            val run = f.enter()
            val helper = f.attachActor(run, mechanic = true)
            val sceneActor = f.attachActor(run, mechanic = false)
            val before = run.specialProgress
            f.manager.helperDied(helper)
            assertEquals(before + 1, run.specialProgress)
            assertFalse(helper in run.helpers)
            assertFalse(f.manager.isEncounterNpc(helper))
            assertEquals(run.session.id, f.instances.instanceForNpc(helper))
            f.manager.helperDied(helper)
            f.manager.helperDied(sceneActor)
            assertEquals(before + 1, run.specialProgress)
            f.npcs.del(helper, Int.MAX_VALUE)
            assertNull(f.instances.instanceForNpc(helper))
            f.manager.helperDied(helper)
            assertEquals(before + 1, run.specialProgress)
        }
    }

    @Test
    fun `captured death completion survives native deletion and duplicates cannot move respawn`() {
        Dt2NativeLifecycleFixture().use { f ->
            val run = f.enter()
            val boss = run.boss
            assertSame(run, f.manager.beginBossDeath(boss))
            assertNull(f.manager.beginBossDeath(boss))
            f.npcs.del(boss, Int.MAX_VALUE)
            assertNull(f.instances.instanceForNpc(boss))
            assertFalse(f.manager.isEncounterNpc(boss))
            f.manager.finishBossDeath(boss, run)
            assertEquals(Dt2State.RESPAWNING, run.state)
            val respawn = run.respawnCycle
            f.clock.cycle += 2
            f.manager.finishBossDeath(boss, run)
            assertEquals(respawn, run.respawnCycle)
            assertEquals(Dt2State.RESPAWNING, run.state)
        }
    }

    @Test
    fun `teardown and reentry cannot revive old hit scopes or captured death completion`() {
        Dt2NativeLifecycleFixture().use { f ->
            val player = f.player()
            val first = f.enter(player)
            val oldBoss = first.boss
            val oldHit = f.attackVardorvis(first)
            assertTrue(oldHit.context.isScoped)
            assertTrue(oldHit.isValidNow())
            assertSame(first, f.manager.beginBossDeath(oldBoss))
            assertTrue(f.instances.terminate(first.session))
            assertTrue(first.retired)
            assertFalse(oldBoss.isSlotAssigned)
            assertFalse(oldHit.isValidNow())
            val second = f.enter(player)
            assertNotEquals(first.session.id, second.session.id)
            assertNotSame(first, second)
            assertNotSame(oldBoss, second.boss)
            val freshHit = f.attackVardorvis(second)
            assertTrue(freshHit.context.isScoped)
            assertTrue(freshHit.isValidNow())
            repeat(2) { f.manager.finishBossDeath(oldBoss, first) }
            f.events.publish(
                InstancePlayerLeaveEvent(
                    player,
                    first.session.key,
                    first.session.id,
                    first.session.owner,
                )
            )
            assertSame(second.session, f.instances.sessionForPlayer(player))
            assertSame(second, f.run(second.session))
            assertFalse(second.retired)
            assertEquals(Dt2State.ACTIVE, second.state)
            assertFalse(oldHit.isValidNow())
            assertTrue(freshHit.isValidNow())
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
