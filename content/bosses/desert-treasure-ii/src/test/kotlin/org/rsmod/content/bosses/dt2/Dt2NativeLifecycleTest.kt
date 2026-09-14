package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.InstancePlayerJoinEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

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
            assertFalse(f.tryEnter(player, seed = 99L))
            assertSame(run, f.run(run.session))
            assertEquals(listOf(run.boss), f.instances.npcsForInstance(run.session.id))
        }
    }

    @Test
    fun `protected native admission preserves all four owners difficulty setup and Vardorvis safe tile`() {
        for (bossKind in Dt2Boss.entries) for (difficulty in Dt2Difficulty.entries) {
            val case = "$bossKind $difficulty"
            try {
                Dt2NativeLifecycleFixture(bossKind).use { f ->
                    val player = f.player()
                    assertNotNull(f.instanceRegistry.get(bossKind.key), case)
                    if (bossKind == Dt2Boss.VARDORVIS && difficulty == Dt2Difficulty.AWAKENED) {
                        assertFalse(f.tryEnter(player, difficulty), case)
                        assertEquals(0, f.allocations, case)
                        assertTrue(f.npcList.none(), case)
                        assertNull(f.instances.sessionForPlayer(player), case)
                        return@use
                    }
                    val run = f.enter(player, difficulty, seed = 97531L)
                    assertEquals(bossKind.key, run.session.key, case)
                    assertEquals(difficulty, run.difficulty, case)
                    assertEquals(97531L, run.seed, case)
                    assertEquals(bossKind.hitpoints(difficulty), run.boss.hitpoints, case)
                    assertEquals(Dt2State.PREPARING, run.state, case)
                    assertSame(run, f.run(run.session), case)
                    assertEquals(
                        f.instances.localCoord(
                            run.session,
                            if (bossKind == Dt2Boss.VARDORVIS) Dt2Assets.VARDORVIS_SAFE_ENTRY
                            else bossKind.enter,
                        ),
                        player.coords,
                        case,
                    )
                    when (bossKind) {
                        Dt2Boss.DUKE -> {
                            assertEquals(DukeRules.NORMAL, run.boss.type.id, case)
                            assertEquals(DukeRules.ASLEEP, run.boss.visType.id, case)
                            assertTrue(run.boss.ignoreCombatInteractions, case)
                            assertTrue(run.duke.scene.isNotEmpty(), case)
                        }
                        Dt2Boss.LEVIATHAN -> assertEquals(5, run.helpers.size, case)
                        Dt2Boss.WHISPERER -> assertEquals(1, player.inv.count(fragmentName()), case)
                        Dt2Boss.VARDORVIS -> assertTrue(run.boss.movementLocked, case)
                    }
                    val actors = f.instances.npcsForInstance(run.session.id).toList()
                    repeat(2) {
                        f.events.publish(
                            InstancePlayerJoinEvent(
                                player,
                                run.session.key,
                                run.session.id,
                                run.session.owner,
                            )
                        )
                    }
                    assertSame(run, f.run(run.session), case)
                    assertEquals(actors, f.instances.npcsForInstance(run.session.id), case)
                    if (bossKind == Dt2Boss.WHISPERER)
                        assertEquals(1, player.inv.count(fragmentName()), case)
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `refused protected entry leaves no admitted run actors or issued items`() {
        for (bossKind in Dt2Boss.entries) for (denyTeleport in listOf(false, true)) {
            try {
                Dt2NativeLifecycleFixture(bossKind).use { f ->
                    val player = f.player()
                    val before = player.coords
                    f.teleportRefused = denyTeleport
                    f.allocationRefused = !denyTeleport
                    assertFalse(f.tryEnter(player), "$bossKind teleport=$denyTeleport")
                    assertEquals(before, player.coords)
                    assertNull(f.instances.sessionForPlayer(player))
                    assertNull(f.instances.sessionForOwner(checkNotNull(player.uuid)))
                    assertTrue(f.instances.sessionsForKey(bossKind.key).isEmpty())
                    assertFalse(f.manager.isActive(player))
                    assertTrue(f.npcList.none())
                    assertTrue(player.inv.all { it == null })
                    assertNull(player.lootDropDuration)
                    f.teleportRefused = false
                    f.allocationRefused = false
                    assertFalse(f.enter(player).retired, "Failure must release pending admission")
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `busy dead and full Whisperer inventory are rejected before native allocation`() {
        Dt2NativeLifecycleFixture(Dt2Boss.WHISPERER).use { f ->
            val player = f.player()
            player.delay = f.clock.cycle + 1
            assertFalse(f.tryEnter(player))
            assertEquals(0, f.allocations)
            player.delay = 0
            player.statMap.setCurrentLevel("stat.hitpoints", 0)
            assertFalse(f.tryEnter(player))
            assertEquals(0, f.allocations)
            player.statMap.setCurrentLevel("stat.hitpoints", 99)
            val item = assertNotNull(ServerCacheManager.getItem(1205))
            for (slot in 0 until player.inv.size) player.inv[slot] = InvObj(item, 1)
            val before = player.inv.toList()
            assertFalse(f.tryEnter(player))
            assertEquals(0, f.allocations)
            assertEquals(before, player.inv.toList())
            assertNull(f.instances.sessionForPlayer(player))
            assertTrue(f.npcList.none())
        }
    }

    @Test
    fun `protected exit refusal preserves every room and successful exit permits fresh admission`() {
        for (bossKind in Dt2Boss.entries) {
            try {
                Dt2NativeLifecycleFixture(bossKind).use { f ->
                    val player = f.player()
                    val first = f.enter(player)
                    val inside = player.coords
                    val actors = f.instances.npcsForInstance(first.session.id).toList()
                    f.teleportRefused = true
                    assertFalse(f.leave(player))
                    assertEquals(inside, player.coords)
                    assertSame(first.session, f.instances.sessionForPlayer(player))
                    assertFalse(first.retired)
                    assertEquals(actors, f.instances.npcsForInstance(first.session.id))
                    val refused =
                        InstanceLeaveRequestEvent(player, first.session.key, first.session.id)
                    f.events.publish(refused)
                    assertEquals(RaidLeaveResult.Failed, refused.result)
                    f.teleportRefused = false
                    player.delay = f.clock.cycle + 1
                    val busy =
                        InstanceLeaveRequestEvent(player, first.session.key, first.session.id)
                    f.events.publish(busy)
                    assertEquals(RaidLeaveResult.Busy, busy.result)
                    player.delay = 0
                    assertTrue(f.leave(player))
                    assertEquals(bossKind.exit, player.coords)
                    assertTrue(first.retired)
                    assertTrue(actors.none { it.isSlotAssigned })
                    assertNull(f.instances.sessionForPlayer(player))
                    assertNull(player.lootDropDuration)
                    val second = f.enter(player)
                    assertNotEquals(first.session.id, second.session.id)
                    assertNotSame(first.boss, second.boss)
                    assertSame(second, f.run(second.session))
                    val stale =
                        InstanceLeaveRequestEvent(player, first.session.key, first.session.id)
                    f.events.publish(stale)
                    assertEquals(RaidLeaveResult.Unhandled, stale.result)
                    assertSame(second.session, f.instances.sessionForPlayer(player))
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `sealed Vardorvis exit remains handled without leaving and opens after defeat`() {
        Dt2NativeLifecycleFixture().use { f ->
            val player = f.player()
            val run = f.enter(player)
            assertNull(f.manager.attackDenial(player, run.boss))
            val inside = player.coords
            assertTrue(f.leave(player, Dt2Assets.VARDORVIS_EXIT_DISABLED))
            assertEquals(inside, player.coords)
            assertSame(run.session, f.instances.sessionForPlayer(player))
            assertFalse(run.retired)
            val boss = run.boss
            assertSame(run, f.manager.beginBossDeath(boss))
            f.npcs.del(boss, Int.MAX_VALUE)
            f.manager.finishBossDeath(boss, run)
            assertTrue(f.leave(player, Dt2Assets.VARDORVIS_EXIT_DISABLED))
            assertNull(f.instances.sessionForPlayer(player))
            assertEquals(Dt2Boss.VARDORVIS.exit, player.coords)
        }
    }

    @Test
    fun `exterior Leviathan rowboat reclaims native death storage without creating a room`() {
        Dt2NativeLifecycleFixture(Dt2Boss.LEVIATHAN).use { f ->
            val player = f.player()
            val run = f.enter(player)
            assertTrue(run.rewardEligible)
            val coins = InvObj(assertNotNull(ServerCacheManager.getItem(995)), 75)
            val context =
                PlayerDeathContext(
                    player,
                    player.coords,
                    false,
                    0,
                    false,
                    true,
                    false,
                    false,
                    false,
                    0,
                    null,
                )
            assertTrue(
                f.storage.store(
                    context,
                    PlayerDeathDrops.DeathDropResult(
                        emptyList(),
                        emptyList(),
                        listOf(coins),
                        emptyList(),
                        0L,
                    ),
                )
            )
            Dt2DeathCleanup(f.manager).cleanup(player)
            f.instances.handleDeath(player, f.clock.cycle)
            assertTrue(run.retired)
            assertNull(f.instances.sessionForPlayer(player))
            assertTrue(f.storage.hasItems(player))
            val allocations = f.allocations
            assertTrue(f.leave(player, Dt2Assets.LEVIATHAN_EXIT_ROWBOATS.first()))
            assertEquals(75, player.inv.count("obj.coins"))
            assertFalse(f.storage.hasItems(player))
            assertEquals(allocations, f.allocations)
            assertFalse(f.leave(player, Dt2Assets.LEVIATHAN_EXIT_ROWBOATS.first()))
            assertEquals(75, player.inv.count("obj.coins"))
            assertNull(f.instances.sessionForPlayer(player))
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
        private fun fragmentName(): String =
            assertNotNull(ServerCacheManager.getItem(Dt2Assets.BLACKSTONE_FRAGMENT)).internalName

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        }
    }
}
