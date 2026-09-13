package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.events.InstancePlayerJoinUnboundEvent
import org.rsmod.api.instances.events.InstanceStartedEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.instances.hook.InstanceLifecycleScript
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Fixed-public foundation mapped in docs/bosses/nex/NEX_OSRS_AUDIT.md. Exercises the authoritative
 * manager, resolver, player identities, native NPC registration/deletion and lifecycle events.
 * Physical copied-region allocation is recorded, while fixed paths must never request it. Attribute
 * assertions cover the manager's persistence boundary, not account deserialization.
 */
@OptIn(InternalApi::class)
class FixedInstanceIntegrationTest {
    @Test
    fun `fixed public creation resolves canonical coordinates without allocating or changing the map`() {
        val f = Fixture()
        val before = f.collisionSnapshot()
        val session = f.create(f.spec(actors = false), tick = 17)
        assertSame(session, f.manager.sessionForId(session.id))
        assertEquals(listOf(session), f.manager.sessionsForKey(KEY).toList())
        assertTrue(session.isServerOwned)
        assertTrue(session.regionIds.isEmpty())
        assertEquals(ENTRY, f.manager.localCoord(session, local(ENTRY)))
        assertEquals(17, session.startedAtTick)
        assertEquals(1, f.started)
        assertEquals(before, f.collisionSnapshot())
        assertEquals(0, f.npcs.count())
        f.assertNoRegions()
        assertTrue(f.manager.terminate(session))
        assertEquals(before, f.collisionSnapshot())
        f.assertNoRegions()
    }

    @Test
    fun `fixed geometry private creation and overlapping ownership fail without leaking authority`() {
        val invalid =
            listOf<() -> InstanceArea.Fixed>(
                { InstanceArea.fixed(SW, SW, ENTRY, EXIT) },
                { InstanceArea.fixed(SW, CoordGrid(NE.x, NE.z, 1), ENTRY, EXIT) },
                { InstanceArea.fixed(SW, NE, EXIT, EXIT) },
                { InstanceArea.fixed(SW, NE, ENTRY, EXIT, listOf(spawn(7852, EXIT))) },
                {
                    InstanceArea.fixed(
                        CoordGrid(10000, 100),
                        CoordGrid(10032, 132),
                        CoordGrid(10008, 108),
                        EXIT,
                    )
                },
            )
        invalid.forEach { assertFailsWith<IllegalArgumentException> { it() } }
        val f = Fixture()
        val player = f.player(1)
        // No inventory is installed: the explicit fixed-area rejection must precede any fee path.
        assertIs<InstanceManager.Result.Failed>(
            f.manager.create(
                player,
                KEY,
                f.spec(actors = false).copy(fee = 100),
                InstanceAccess.Private,
                0,
            )
        )
        assertNull(f.manager.firstSession())
        assertNull(f.manager.sessionForOwner(assertNotNull(player.uuid)))
        val first = f.create(f.spec(actors = false))
        assertNull(f.manager.createServerOwned(KEY, first.spec, InstanceAccess.Friends, 1))
        val overlapping = InstanceArea.fixed(SW.translate(1, 1), NE.translate(1, 1), ENTRY, EXIT)
        assertNull(
            f.manager.createServerOwned(
                "overlap",
                InstanceSettings().withArea(overlapping, 0),
                InstanceAccess.Friends,
                1,
            )
        )
        val adjacent =
            InstanceArea.fixed(
                CoordGrid(NE.x, SW.z),
                CoordGrid(NE.x + 32, NE.z),
                CoordGrid(NE.x + 2, SW.z + 2),
                EXIT,
            )
        val upper =
            InstanceArea.fixed(
                CoordGrid(SW.x, SW.z, 1),
                CoordGrid(NE.x, NE.z, 1),
                CoordGrid(ENTRY.x, ENTRY.z, 1),
                EXIT,
            )
        assertNotNull(
            f.manager.createServerOwned(
                "adjacent",
                InstanceSettings().withArea(adjacent, 0),
                InstanceAccess.Friends,
                1,
            )
        )
        assertNotNull(
            f.manager.createServerOwned(
                "upper",
                InstanceSettings().withArea(upper, 0),
                InstanceAccess.Friends,
                1,
            )
        )
        assertEquals(1, f.manager.sessionsForKey(KEY).size)
        assertEquals(0, f.npcs.count())
        f.assertNoRegions()
    }

    @Test
    fun `join planning cannot move or enrol and only live physical arrival finalizes once`() {
        val f = Fixture()
        val session = f.create(f.spec(actors = false))
        val player = f.player(1)
        val result = assertIs<InstanceManager.Result.Joined>(f.manager.join(player, session, 1))
        assertEquals(ENTRY, result.enter)
        assertEquals(EXIT, player.coords)
        assertNull(f.manager.sessionForPlayer(player))
        f.manager.finalizeEntry(player, session, 1)
        assertTrue(f.manager.playersIn(session).isEmpty())
        player.coords = CoordGrid(ENTRY.x, ENTRY.z, 1)
        f.manager.finalizeEntry(player, session, 2)
        assertNull(f.manager.sessionForPlayer(player))
        player.coords = ENTRY
        f.manager.finalizeEntry(player, session, 3)
        f.manager.finalizeEntry(player, session, 4)
        assertSame(session, f.manager.sessionForPlayer(player))
        assertEquals(listOf(player), f.manager.playersIn(session))
        assertEquals(1, f.joins.size)
        assertEquals(session.id, player.currentInstanceId())
        assertEquals(0, player.vars["varbit.player_in_instance"])
        assertNull(player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
        val stale = f.player(2).apply { coords = ENTRY }
        f.manager.terminate(session)
        f.manager.finalizeEntry(stale, session, 5)
        assertNull(f.manager.sessionForPlayer(stale))
        assertNull(stale.currentInstanceId())
        f.assertNoRegions()
    }

    @Test
    fun `first entry starts deferred actors once and empty public reentry preserves actors and state`() {
        for (deferred in listOf(false, true)) {
            val f = Fixture()
            val session = f.create(f.spec(deferred = deferred))
            assertEquals(if (deferred) 0 else 2, f.npcs.count())
            assertEquals(if (deferred) 0 else 1, f.started)
            val player = f.player(1)
            val beforeJoin = f.manager.npcsForInstance(session.id)
            f.enter(player, session, 10)
            val actors = f.manager.npcsForInstance(session.id)
            assertEquals(2, actors.size)
            if (!deferred) assertEquals(beforeJoin, actors)
            val key = InstanceStateKey<Any>("public-round")
            val state = session.state(key) { Any() }
            f.manager.leave(player, session, 11)
            f.manager.tickReclaim(10000)
            assertSame(session, f.manager.sessionForId(session.id))
            f.enter(player, session, 10001)
            assertEquals(actors, f.manager.npcsForInstance(session.id))
            assertTrue(actors.all { it.isSlotAssigned })
            assertSame(state, session.stateOrNull(key))
            assertEquals(1, f.started)
            assertEquals(2, f.npcs.count())
            f.assertNoRegions()
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `native paired reset retains members and content while replacing owned actors and helpers`() {
        val f = Fixture()
        val session = f.create()
        val player = f.player(1)
        f.enter(player, session)
        val key = InstanceStateKey<Any>("round-state")
        val state = session.state(key) { Any() }
        val unrelated = f.nativeNpc(12596, SW.translate(2, 2))
        val untouched = f.collisionSnapshot()
        var resetting = false
        var rejectedNestedResets = 0
        f.events.subscribeUnbound(NpcStateEvents.Delete::class.java) {
            if (resetting) {
                assertFalse(
                    f.manager.resetNpcs(session, 99),
                    "Native deletion must not reenter actor reset",
                )
                rejectedNestedResets++
            }
        }
        repeat(3) { tick ->
            val helper = f.nativeNpc(12596, SW.translate(20, 20))
            f.manager.attachNpc(session.id, helper)
            val old = f.manager.npcsForInstance(session.id)
            resetting = true
            assertTrue(f.manager.resetNpcs(session, tick + 1))
            resetting = false
            val fresh = f.manager.npcsForInstance(session.id)
            assertEquals(setOf(FIRST_NPC, SECOND_NPC), fresh.map { it.coords }.toSet())
            assertEquals(2, fresh.size)
            old.forEach {
                assertFalse(it.isSlotAssigned)
                assertNull(f.manager.instanceForNpc(it))
                assertTrue(fresh.none { replacement -> replacement === it })
            }
            fresh.forEach { assertEquals(session.id, f.manager.instanceForNpc(it)) }
            assertSame(session, f.manager.sessionForPlayer(player))
            assertEquals(listOf(player), f.manager.playersIn(session))
            assertSame(state, session.stateOrNull(key))
            assertTrue(unrelated.isSlotAssigned)
            assertNull(f.manager.instanceForNpc(unrelated))
            assertEquals(3, f.npcs.count())
            assertEquals(untouched, f.collisionSnapshot())
        }
        assertTrue(rejectedNestedResets > 0)
        val deleted = f.manager.npcsForInstance(session.id).first()
        f.npcRepo.del(deleted, Int.MAX_VALUE)
        assertNull(f.manager.instanceForNpc(deleted))
        assertFalse(deleted in f.manager.npcsForInstance(session.id))
        f.assertNoRegions()
    }

    @Test
    fun `fixed membership uses level aware half open bounds and detaches at the bank`() {
        for (destination in
            listOf(
                EXIT,
                CoordGrid(NE.x, ENTRY.z),
                CoordGrid(ENTRY.x, NE.z),
                CoordGrid(ENTRY.x, ENTRY.z, 1),
            )) {
            val f = Fixture()
            val session = f.create(f.spec(actors = false))
            val player = f.player(1)
            f.enter(player, session)
            for (inside in listOf(SW, NE.translate(-1, -1))) {
                player.coords = inside
                assertFalse(f.manager.leaveForExternalTeleport(player, inside, 1))
                assertSame(session, f.manager.sessionForPlayer(player))
            }
            player.coords = destination
            assertTrue(f.manager.leaveForExternalTeleport(player, destination, 2))
            assertEquals(destination, player.coords)
            assertNull(f.manager.sessionForPlayer(player))
            assertTrue(f.manager.playersIn(session).isEmpty())
            assertNull(player.currentInstanceId())
            assertNull(player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
            assertSame(session, f.manager.sessionForId(session.id))
            f.assertNoRegions()
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `logout disconnect death and missing player slots release fixed membership without relocating saves`() {
        for (ending in listOf("logout", "disconnect", "death", "missing")) {
            val f = Fixture()
            val session = f.create()
            val player = f.player(1)
            f.enter(player, session)
            player.coords = ENTRY.translate(1, 1)
            val savedTile = player.coords
            val actors = f.manager.npcsForInstance(session.id)
            when (ending) {
                "logout" -> assertEquals(savedTile, f.manager.leaveForLogout(player, 1))
                "disconnect" -> {
                    player.clientDisconnected.set(true)
                    assertTrue(f.manager.handleDisconnect(player, 1))
                }
                "death" -> f.manager.handleDeath(player, 1)
                "missing" -> f.players.remove(player.slotId)
            }
            f.manager.tickReclaim(2)
            assertTrue(session.occupants.isEmpty(), ending)
            assertTrue(f.manager.playersIn(session).isEmpty(), ending)
            assertNull(f.manager.sessionForPlayer(player), ending)
            assertEquals(savedTile, player.coords, ending)
            assertNull(player.attr[InstanceAttributes.LOGIN_EXIT_COORD], ending)
            assertEquals(0, player.vars["varbit.player_in_instance"], ending)
            assertSame(session, f.manager.sessionForId(session.id))
            assertEquals(actors, f.manager.npcsForInstance(session.id))
            assertTrue(actors.all { it.isSlotAssigned })
            f.assertNoRegions()
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `replacement players cannot inherit membership and stale departure cannot evict a fresh admission`() {
        for (sameUid in listOf(false, true)) {
            val f = Fixture()
            val session = f.create(f.spec(actors = false))
            val old = f.player(1)
            f.enter(old, session)
            val replacement =
                f.player(1, uuid = if (sameUid) assertNotNull(old.uuid) else 999L).apply {
                    coords = ENTRY
                    attr[InstanceAttributes.CURRENT_INSTANCE_ID] = session.id.value
                    attr[InstanceAttributes.LOGIN_EXIT_COORD] = EXIT.packed
                    VarPlayerIntMapSetter.set(this, "varbit.player_in_instance", 1)
                }
            if (sameUid) assertEquals(old.uid, replacement.uid)
            assertNotSame(old, replacement)
            assertNull(f.manager.sessionForPlayer(replacement))
            assertTrue(f.manager.playersIn(session).isEmpty())
            f.manager.tickReclaim(1)
            assertTrue(session.occupants.isEmpty())
            assertEquals(
                session.id,
                replacement.currentInstanceId(),
                "Pruning the old identity mutated its replacement",
            )
            assertEquals(EXIT.packed, replacement.attr[InstanceAttributes.LOGIN_EXIT_COORD])
            f.enter(replacement, session, 2)
            assertEquals(listOf(replacement), f.manager.playersIn(session))
            assertEquals(
                EXIT.packed,
                replacement.attr[InstanceAttributes.LOGIN_EXIT_COORD],
                "Fixed admission must preserve a recovery marker owned by copied entry or account loading",
            )
            f.manager.leave(old, session, 3)
            f.manager.handleLogout(old, 3)
            assertSame(session, f.manager.sessionForPlayer(replacement))
            assertEquals(listOf(replacement), f.manager.playersIn(session))
            assertEquals(session.id, replacement.currentInstanceId())
            assertEquals(
                1,
                replacement.vars["varbit.player_in_instance"],
                "Fixed membership must not rewrite copied-instance client state",
            )
            f.assertNoRegions()
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `cleanup failure and reentrant actor reset cannot release overlap early or resurrect a session`() {
        val f = Fixture()
        val session = f.create()
        val player = f.player(1)
        f.enter(player, session)
        val old = f.manager.npcsForInstance(session.id)
        var cleanupCalls = 0
        session.onCleanup {
            cleanupCalls++
            assertNull(
                f.manager.createServerOwned(
                    "during-cleanup",
                    session.spec,
                    InstanceAccess.Friends,
                    1,
                )
            )
            error("fixture cleanup failure")
        }
        session.onCleanup { cleanupCalls++ }
        val failure = assertFailsWith<IllegalStateException> { f.manager.terminate(session) }
        assertEquals("fixture cleanup failure", failure.message)
        assertEquals(2, cleanupCalls)
        assertTrue(session.isEnded)
        assertNull(f.manager.sessionForId(session.id))
        assertNull(f.manager.sessionForPlayer(player))
        assertNull(player.currentInstanceId())
        assertNull(player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
        assertEquals(0, f.npcs.count())
        old.forEach {
            assertFalse(it.isSlotAssigned)
            assertNull(f.manager.instanceForNpc(it))
        }
        assertFalse(f.manager.terminate(session))
        val fresh = f.create()
        val freshActors = f.manager.npcsForInstance(fresh.id)
        assertNotEquals(session.id, fresh.id)
        assertFalse(f.manager.terminate(session))
        assertEquals(freshActors, f.manager.npcsForInstance(fresh.id))
        for (duringCreate in listOf(true, false)) {
            val subject = if (duringCreate) fresh else f.create()
            var terminateDuringSpawn = true
            val terminateFromNativeCallback = {
                if (terminateDuringSpawn) {
                    terminateDuringSpawn = false
                    assertTrue(f.manager.terminate(subject))
                }
            }
            if (duringCreate)
                f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
                    terminateFromNativeCallback()
                }
            else
                for (npcType in listOf(7852L, 7851L)) {
                    f.events.subscribeKeyed(NpcStateEvents.Spawn::class.java, npcType) {
                        terminateFromNativeCallback()
                    }
                }
            assertFalse(f.manager.resetNpcs(subject, 2))
            assertTrue(subject.isEnded)
            assertNull(f.manager.sessionForId(subject.id))
            assertTrue(f.manager.npcsForInstance(subject.id).isEmpty())
            assertEquals(0, f.npcs.count(), "An actor spawned after callback termination leaked")
        }
        f.assertNoRegions()
    }

    @Test
    fun `external native deletion retains overlap until its detached actor finishes the native tail`() {
        val f = Fixture()
        val session = f.create()
        val actors = f.manager.npcsForInstance(session.id)
        val deleted = actors.first()
        var callbackCount = 0
        f.events.subscribeUnbound(NpcStateEvents.Delete::class.java) {
            if (npc === deleted) {
                callbackCount++
                // The registered InstanceLifecycleScript ran first, but native deletion still
                // has zone/collision/UID work after this event returns.
                assertNull(f.manager.instanceForNpc(deleted))
                assertFalse(deleted in f.manager.npcsForInstance(session.id))
                assertTrue(deleted.isSlotAssigned)
                assertNull(deleted.uid.resolve(f.npcs))
                assertTrue(f.manager.terminate(session))
                assertNull(
                    f.manager.createServerOwned(
                        "during-native-delete",
                        session.spec,
                        InstanceAccess.Friends,
                        1,
                    )
                )
            }
        }
        f.npcRepo.del(deleted, Int.MAX_VALUE)
        assertEquals(1, callbackCount)
        assertFalse(deleted.isSlotAssigned)
        assertNull(
            f.manager.createServerOwned("before-reconcile", session.spec, InstanceAccess.Friends, 1)
        )
        f.manager.tickReclaim(2)
        assertTrue(session.isEnded)
        assertTrue(actors.none { it.isSlotAssigned })
        assertTrue(f.manager.npcsForInstance(session.id).isEmpty())
        val replacement = f.create()
        assertNotEquals(session.id, replacement.id)
        assertEquals(2, f.npcs.count())
        assertEquals(1, callbackCount)
        f.assertNoRegions()
    }

    @Test
    fun `helper attached by an old actor deletion callback remains owned through reset and teardown`() {
        val f = Fixture()
        val session = f.create()
        val old = f.manager.npcsForInstance(session.id)
        var addedHelper: Npc? = null
        f.events.subscribeUnbound(NpcStateEvents.Delete::class.java) {
            if (npc === old.first()) {
                assertNull(addedHelper)
                addedHelper = f.nativeNpc(12596, SW.translate(20, 20))
                f.manager.attachNpc(session.id, assertNotNull(addedHelper))
            }
        }
        assertTrue(f.manager.resetNpcs(session, 1))
        val helper = assertNotNull(addedHelper)
        val owned = f.manager.npcsForInstance(session.id)
        assertEquals(3, owned.size)
        assertTrue(owned.any { it === helper })
        assertEquals(session.id, f.manager.instanceForNpc(helper))
        assertTrue(helper.isSlotAssigned)
        assertEquals(
            setOf(FIRST_NPC, SECOND_NPC),
            owned.filter { it !== helper }.map { it.coords }.toSet(),
        )
        assertTrue(old.none { it.isSlotAssigned })
        assertTrue(f.manager.terminate(session))
        assertTrue(owned.none { it.isSlotAssigned })
        owned.forEach { assertNull(f.manager.instanceForNpc(it)) }
        assertEquals(0, f.npcs.count())
        assertTrue(f.manager.npcsForInstance(session.id).isEmpty())
        f.assertNoRegions()
    }

    @Test
    fun `native Delete listener failure propagates and retains failed ownership while other actors are attempted`() {
        val f = Fixture()
        val session = f.create()
        val actors = f.manager.npcsForInstance(session.id)
        val failed = actors.first()
        val otherOwned = actors.last()
        val unrelated = f.nativeNpc(12596, SW.translate(2, 2))
        val nativeFailure = IllegalStateException("fixture native Delete failure")
        val deletionAttempts = mutableListOf<Npc>()
        f.events.subscribeUnbound(NpcStateEvents.Delete::class.java) {
            deletionAttempts += npc
            if (npc === failed) throw nativeFailure
        }
        assertSame(
            nativeFailure,
            assertFailsWith<IllegalStateException> { f.manager.terminate(session) },
        )
        assertTrue(session.isEnded)
        assertTrue(failed in deletionAttempts)
        assertTrue(
            otherOwned in deletionAttempts,
            "One native failure must not skip other owned actors",
        )
        assertFalse(otherOwned.isSlotAssigned)
        assertNull(f.manager.instanceForNpc(otherOwned))
        // Native Delete listeners run after list removal and before UID/collision retirement.
        // The shared manager must retain failure, not pretend this partial engine state is
        // repaired.
        assertTrue(failed.isSlotAssigned)
        assertNull(failed.uid.resolve(f.npcs))
        assertTrue(unrelated.isSlotAssigned)
        assertNull(f.manager.instanceForNpc(unrelated))
        assertNull(
            f.manager.createServerOwned(
                "after-native-failure",
                session.spec,
                InstanceAccess.Friends,
                1,
            )
        )
        val attempts = deletionAttempts.size
        assertFalse(f.manager.resetNpcs(session, 2))
        assertFalse(f.manager.terminate(session))
        f.manager.tickReclaim(10)
        assertNull(
            f.manager.createServerOwned(
                "after-failed-reclaim",
                session.spec,
                InstanceAccess.Friends,
                10,
            )
        )
        assertEquals(
            attempts,
            deletionAttempts.size,
            "Failed native teardown must not retry implicitly",
        )
        assertTrue(unrelated.isSlotAssigned)
        f.assertNoRegions()
    }

    @Test
    fun `deleting a creating native actor then terminating quarantines the area instead of reporting cleanup`() {
        for (duringCreate in listOf(true, false)) {
            val f = Fixture()
            val session = f.create()
            var removedCandidate: Npc? = null
            val deleteThenTerminate: (Npc) -> Unit = { candidate ->
                if (removedCandidate == null) {
                    removedCandidate = candidate
                    val uid = candidate.uid
                    assertSame(candidate, uid.resolve(f.npcs))
                    f.npcRepo.del(candidate, Int.MAX_VALUE)
                    assertFalse(candidate.isSlotAssigned)
                    assertNull(uid.resolve(f.npcs))
                    assertTrue(f.manager.terminate(session))
                }
            }
            if (duringCreate) {
                f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
                    deleteThenTerminate(npc)
                }
            } else {
                for (npcType in listOf(7852L, 7851L)) {
                    f.events.subscribeKeyed(NpcStateEvents.Spawn::class.java, npcType) {
                        deleteThenTerminate(npc)
                    }
                }
            }
            assertFailsWith<IllegalStateException> { f.manager.resetNpcs(session, 1) }
            assertFalse(assertNotNull(removedCandidate).isSlotAssigned)
            // The enclosing native add continues its zone work after these callbacks return.
            // No assertion here claims that its resulting zone/collision residue was repaired.
            assertNull(
                f.manager.createServerOwned(
                    "after-lost-native-actor",
                    session.spec,
                    InstanceAccess.Friends,
                    2,
                )
            )
            assertFalse(f.manager.resetNpcs(session, 2))
            assertFalse(f.manager.terminate(session))
            f.manager.tickReclaim(3)
            assertNull(
                f.manager.createServerOwned(
                    "after-lost-native-reclaim",
                    session.spec,
                    InstanceAccess.Friends,
                    3,
                )
            )
            f.assertNoRegions()
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `native deletion callback can transfer another actor without the old snapshot deleting its new ownership`() {
        for (resetFirst in listOf(false, true)) {
            val f = Fixture()
            val first = f.create()
            val adjacentArea =
                InstanceArea.fixed(
                    CoordGrid(NE.x, SW.z),
                    CoordGrid(NE.x + 32, NE.z),
                    CoordGrid(NE.x + 2, SW.z + 2),
                    EXIT,
                )
            val second =
                assertNotNull(
                    f.manager.createServerOwned(
                        "transfer-target",
                        InstanceSettings().withArea(adjacentArea, 0),
                        InstanceAccess.Friends,
                        0,
                    )
                )
            val old = f.manager.npcsForInstance(first.id)
            val trigger = old.first()
            val transferred = old.last()
            var transfers = 0
            f.events.subscribeUnbound(NpcStateEvents.Delete::class.java) {
                if (npc === trigger) {
                    transfers++
                    assertTrue(transferred.isSlotAssigned)
                    f.manager.detachNpc(first.id, transferred)
                    f.manager.attachNpc(second.id, transferred)
                }
            }
            if (resetFirst) assertTrue(f.manager.resetNpcs(first, 1))
            else assertTrue(f.manager.terminate(first))
            assertEquals(1, transfers)
            assertFalse(trigger.isSlotAssigned)
            assertTrue(transferred.isSlotAssigned)
            assertSame(transferred, transferred.uid.resolve(f.npcs))
            assertEquals(second.id, f.manager.instanceForNpc(transferred))
            assertEquals(listOf(transferred), f.manager.npcsForInstance(second.id))
            assertFalse(transferred in f.manager.npcsForInstance(first.id))
            if (resetFirst) {
                assertEquals(2, f.manager.npcsForInstance(first.id).size)
                assertTrue(f.manager.terminate(first))
                assertTrue(transferred.isSlotAssigned)
                assertEquals(second.id, f.manager.instanceForNpc(transferred))
            }
            assertTrue(f.manager.terminate(second))
            assertFalse(transferred.isSlotAssigned)
            assertNull(f.manager.instanceForNpc(transferred))
            assertEquals(0, f.npcs.count())
            assertEquals(1, transfers)
            f.assertNoRegions()
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `copied defaults still allocate translate set recovery and relocate on logout`() {
        for (serverOwned in listOf(false, true)) {
            val f = Fixture(allowCopies = true)
            val player = f.player(1)
            val spec =
                InstanceSettings(maxPlayers = 2, destroyWhenEmpty = true)
                    .withArea(
                        InstanceArea.copyRegions(
                            listOf(9116),
                            enterCoord = RegionLocal(0, 35, 156, 40, 38),
                            exitCoord = EXIT,
                        ),
                        0,
                    )
            val session: InstanceSession
            val enter: CoordGrid
            if (serverOwned) {
                session =
                    assertNotNull(
                        f.manager.createServerOwned("copied", spec, InstanceAccess.Friends, 0)
                    )
                enter =
                    assertIs<InstanceManager.Result.Joined>(f.manager.join(player, session, 0))
                        .enter
            } else {
                val created =
                    assertIs<InstanceManager.Result.Created>(
                        f.manager.create(player, "copied", spec, InstanceAccess.Private, 0)
                    )
                session = created.session
                enter = created.enter
            }
            assertEquals(1, f.allocations)
            assertEquals(if (serverOwned) 1 else 0, f.protections)
            assertEquals(1, session.regionIds.size)
            assertEquals(CoordGrid(6440, 6438), enter)
            player.coords = enter
            f.manager.finalizeEntry(player, session, 0)
            assertSame(session, f.manager.sessionForPlayer(player))
            assertEquals(1, player.vars["varbit.player_in_instance"])
            assertEquals(EXIT.packed, player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
            assertEquals(EXIT, f.manager.leaveForLogout(player, 1))
            assertEquals(EXIT, player.coords)
            assertEquals(EXIT.packed, player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
            assertNull(player.currentInstanceId())
            f.manager.tickReclaim(2)
            if (serverOwned) assertTrue(f.manager.terminate(session))
            assertEquals(if (serverOwned) 1 else 0, f.unprotections)
            assertNull(f.manager.sessionForId(session.id))
            framework().clearInlineMocks()
        }
    }

    private class Fixture(allowCopies: Boolean = false) {
        val clock = MapClock()
        val events = EventBus()
        val players = PlayerList()
        val npcs = NpcList()
        val collision = CollisionFlagMap()
        val npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
        var allocations = 0
        var protections = 0
        var unprotections = 0
        val regions =
            mock(RegionRepository::class.java) { call ->
                check(allowCopies) { "Fixed session called RegionRepository.${call.method.name}" }
                when (call.method.name.substringBefore('-')) {
                    "add" -> {
                        val base = CoordGrid(6400 + allocations * 64, 6400)
                        val region =
                            Region(base, base.translate(64, 64), ++allocations, allocations)
                        for (x in 0..7) for (z in 0..7) {
                            region.registerZone(
                                RegionZoneCopy(ZoneKey(35 * 8 + x, 156 * 8 + z, 0), 0, null),
                                ZoneKey.from(base.translate(x * 8, z * 8)),
                            )
                        }
                        region
                    }
                    "isValid" -> true
                    "protect" -> {
                        protections++
                        null
                    }
                    "unprotect" -> {
                        unprotections++
                        null
                    }
                    else -> RETURNS_DEFAULTS.answer(call)
                }
            }
        val manager =
            InstanceManager(
                regions,
                npcRepo,
                players,
                events,
                InstanceAreaResolver(),
                clock,
                collision,
                npcs,
            )
        val joins = mutableListOf<Player>()
        var started = 0

        init {
            for (level in 0..1) for (x in 2880..3000 step 8) for (z in 5168..5240 step 8) {
                collision.allocateIfAbsent(x, z, level)
            }
            for (x in 6400..6592 step 8) for (z in 6400..6464 step 8) collision.allocateIfAbsent(
                x,
                z,
                0,
            )
            events.subscribeKeyed(InstanceStartedEvent::class.java, instanceEventId(KEY)) {
                started++
            }
            events.subscribeUnbound(InstancePlayerJoinUnboundEvent::class.java) { joins += player }
            with(InstanceLifecycleScript(manager, clock)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun spec(actors: Boolean = true, deferred: Boolean = false): InstanceSpec =
            InstanceSettings(maxPlayers = 4, spawnOnFirstJoin = deferred)
                .withArea(
                    InstanceArea.fixed(
                        SW,
                        NE,
                        ENTRY,
                        EXIT,
                        if (actors) listOf(spawn(7852, FIRST_NPC), spawn(7851, SECOND_NPC))
                        else emptyList(),
                    ),
                    0,
                )

        fun create(spec: InstanceSpec = spec(), tick: Int = 0): InstanceSession =
            assertNotNull(manager.createServerOwned(KEY, spec, InstanceAccess.Friends, tick))

        fun player(slot: Int, uuid: Long = slot.toLong()): Player =
            Player().apply {
                slotId = slot
                this.uuid = uuid
                observerUUID = uuid
                assignUid()
                coords = EXIT
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                players[slot] = this
            }

        fun enter(player: Player, session: InstanceSession, tick: Int = 0) {
            val joined =
                assertIs<InstanceManager.Result.Joined>(manager.join(player, session, tick))
            player.coords = joined.enter
            manager.finalizeEntry(player, session, tick)
            assertSame(session, manager.sessionForPlayer(player))
        }

        fun nativeNpc(id: Int, coords: CoordGrid): Npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(id)), coords).also {
                npcRepo.add(it, Int.MAX_VALUE)
            }

        fun collisionSnapshot(): List<Int> =
            (SW.x until NE.x).flatMap { x ->
                (SW.z until NE.z).map { z -> collision[x, z, SW.level] }
            }

        fun assertNoRegions() {
            assertEquals(0, allocations)
            assertEquals(0, protections)
            assertEquals(0, unprotections)
            verifyNoInteractions(regions)
        }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private const val KEY = "fixed-native-test"
        private val SW = CoordGrid(2912, 5184)
        private val NE = CoordGrid(2944, 5216)
        private val ENTRY = CoordGrid(2920, 5200)
        private val EXIT = CoordGrid(2908, 5180)
        private val FIRST_NPC = CoordGrid(2922, 5204)
        private val SECOND_NPC = CoordGrid(2932, 5204)

        private fun local(coords: CoordGrid) =
            RegionLocal(coords.level, coords.mx, coords.mz, coords.lx, coords.lz)

        private fun spawn(id: Int, coords: CoordGrid) =
            InstanceNpc(RSCM.getReverseMapping(RSCMType.NPC, id), coords)

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
