package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
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
import org.rsmod.api.repo.EntityLifecycleProcess
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.SessionStateEvent
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

    @Test
    fun `allocated default empty join still respawns actors restarts clock and clears damage`() {
        for (deferred in listOf(false, true)) {
            val f = Fixture(allowCopies = true)
            val player = f.player(1)
            val created =
                assertIs<InstanceManager.Result.Created>(
                    f.manager.create(
                        player,
                        KEY,
                        f.copiedSpec(deferred),
                        InstanceAccess.Private,
                        10,
                    )
                )
            val session = created.session
            assertEquals(if (deferred) 0 else 1, f.manager.npcsForInstance(session.id).size)
            assertEquals(1, f.started)
            f.enter(player, session, 20)
            val original = f.manager.npcsForInstance(session.id).single()
            original.hitpoints = 7
            val key = InstanceStateKey<Any>("copied-default-state")
            val state = session.state(key) { Any() }
            session.damageContributions.record(player, 37)
            val starts = f.started
            f.clock.cycle = 30
            f.events.publish(SessionStateEvent.Logout(player))
            f.manager.tickReclaim(31)
            assertTrue(session.occupants.isEmpty())
            assertTrue(original.isSlotAssigned)
            assertSame(session, f.manager.sessionForId(session.id))
            f.enter(player, session, 50)
            val replacement = f.manager.npcsForInstance(session.id).single()
            assertNotSame(original, replacement)
            assertFalse(original.isSlotAssigned)
            assertNull(f.manager.instanceForNpc(original))
            assertTrue(replacement.isSlotAssigned)
            assertEquals(session.id, f.manager.instanceForNpc(replacement))
            assertEquals(50, session.startedAtTick)
            assertEquals(starts + 1, f.started)
            assertEquals(0, session.damageContributions.totalDamage())
            assertSame(
                state,
                session.stateOrNull(key),
                "Ordinary actor reset retains content state",
            )
            assertEquals(1, f.allocations)
            assertTrue(f.manager.terminate(session))
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `allocated retained join preserves fight through native logout replacement and finalize entry`() {
        for (serverOwned in listOf(false, true)) {
            val f = Fixture(allowCopies = true)
            val originalPlayer = f.player(1)
            val spec = f.copiedSpec()
            val session =
                if (serverOwned) f.create(spec, tick = 10)
                else
                    assertIs<InstanceManager.Result.Created>(
                            f.manager.create(originalPlayer, KEY, spec, InstanceAccess.Private, 10)
                        )
                        .session
            f.enter(originalPlayer, session, 20)
            val actor = f.manager.npcsForInstance(session.id).single()
            val actorUid = actor.uid
            actor.hitpoints = 7
            val key = InstanceStateKey<Any>("retained-raid-state")
            val state = session.state(key) { Any() }
            session.damageContributions.record(originalPlayer, 37)
            session.bossFightStartTick = 21
            val starts = f.started
            val joins = f.joins.size
            var npcCreates = 0
            f.events.subscribeUnbound(NpcStateEvents.Create::class.java) { npcCreates++ }
            f.clock.cycle = 30
            f.events.publish(SessionStateEvent.Logout(originalPlayer))
            f.manager.tickReclaim(31)
            assertTrue(session.occupants.isEmpty())
            assertEquals(EXIT, originalPlayer.coords)
            assertNull(f.manager.sessionForPlayer(originalPlayer))
            val replacement = f.player(1, assertNotNull(originalPlayer.uuid))
            assertNotSame(originalPlayer, replacement)
            val result =
                assertIs<InstanceManager.Result.Joined>(
                    f.manager.join(replacement, session, 50, resetIfEmpty = false)
                )
            assertTrue(session.occupants.isEmpty(), "Join planning must not enroll the new player")
            assertNull(f.manager.sessionForPlayer(replacement))
            assertEquals(EXIT, replacement.coords)
            assertEquals(joins, f.joins.size)
            assertSame(actor, f.manager.npcsForInstance(session.id).single())
            assertEquals(actorUid, actor.uid)
            assertEquals(7, actor.hitpoints)
            assertSame(state, session.stateOrNull(key))
            assertEquals(37, session.damageContributions.damageBy(replacement))
            assertEquals(20, session.startedAtTick)
            assertEquals(21, session.bossFightStartTick)
            assertEquals(starts, f.started)
            assertEquals(0, npcCreates)
            replacement.coords = result.enter
            f.manager.finalizeEntry(replacement, session, 50)
            f.manager.finalizeEntry(replacement, session, 51)
            assertSame(session, f.manager.sessionForPlayer(replacement))
            assertEquals(listOf(replacement), f.manager.playersIn(session))
            assertEquals(joins + 1, f.joins.size)
            assertEquals(1, replacement.vars["varbit.player_in_instance"])
            assertEquals(EXIT.packed, replacement.attr[InstanceAttributes.LOGIN_EXIT_COORD])
            assertSame(actor, f.manager.npcsForInstance(session.id).single())
            assertEquals(37, session.damageContributions.totalDamage())
            assertEquals(20, session.startedAtTick)
            assertEquals(starts, f.started)
            assertEquals(0, npcCreates)
            assertEquals(1, f.allocations)
            assertTrue(f.manager.terminate(session))
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `retained allocated join still enforces live access grace capacity and region validity`() {
        val cases =
            mapOf(
                "ended" to "That instance no longer exists.",
                "access" to "You do not have access to that instance.",
                "grace" to "That instance is ending and no longer accepts players.",
                "full" to "That instance is full.",
                "invalid" to "That instance is no longer available. Please create a new instance.",
            )
        for ((condition, reason) in cases) {
            val f = Fixture(allowCopies = true)
            val owner = f.player(1)
            val session =
                assertIs<InstanceManager.Result.Created>(
                        f.manager.create(owner, KEY, f.copiedSpec(), InstanceAccess.Private, 10)
                    )
                    .session
            f.enter(owner, session, 20)
            if (condition != "full") {
                f.clock.cycle = 30
                f.events.publish(SessionStateEvent.Logout(owner))
            }
            val candidate = if (condition in setOf("access", "full")) f.player(2) else owner
            when (condition) {
                "ended" -> f.manager.terminate(session)
                "grace" -> session.enterGrace(30)
                "full" -> session.access = InstanceAccess.Friends
                "invalid" -> f.validRegion = false
            }
            val before = f.manager.npcsForInstance(session.id)
            val starts = f.started
            val joins = f.joins.size
            val result =
                assertIs<InstanceManager.Result.Failed>(
                    f.manager.join(candidate, session, 50, resetIfEmpty = false),
                    condition,
                )
            assertEquals(reason, result.reason, condition)
            assertEquals(starts, f.started, condition)
            assertEquals(joins, f.joins.size, condition)
            assertNull(f.manager.sessionForPlayer(candidate), condition)
            if (condition == "invalid") {
                assertNull(f.manager.sessionForId(session.id))
                assertTrue(before.none { it.isSlotAssigned })
            } else {
                assertEquals(before, f.manager.npcsForInstance(session.id), condition)
            }
            if (!session.isEnded) assertTrue(f.manager.terminate(session))
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `guarded dynamic spawn owns the prepared actor before native events and preserves its configuration`() {
        val f = Fixture()
        val session = f.create(f.spec(actors = false))
        val candidate =
            Npc(assertNotNull(ServerCacheManager.getNpc(12596)), FIRST_NPC).apply {
                hitpoints = 317
                baseHitpointsLvl = 421
                mode = NpcMode.ApPlayer2
            }
        var creates = 0
        var spawns = 0
        fun assertPreparedOwner(npc: Npc) {
            assertSame(candidate, npc)
            assertSame(candidate, npc.uid.resolve(f.npcs))
            assertEquals(session.id, f.manager.instanceForNpc(npc))
            assertEquals(listOf(candidate), f.manager.npcsForInstance(session.id))
            assertEquals(FIRST_NPC, npc.coords)
            assertEquals(317, npc.hitpoints)
            assertEquals(421, npc.baseHitpointsLvl)
            assertEquals(NpcMode.ApPlayer2, npc.mode)
        }
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            creates++
            assertPreparedOwner(npc)
        }
        f.events.subscribeKeyed(NpcStateEvents.Spawn::class.java, 12596L) {
            spawns++
            assertPreparedOwner(npc)
        }
        assertTrue(f.manager.spawnFixedNpc(session, candidate))
        assertPreparedOwner(candidate)
        assertTrue(candidate.respawns, "Preserve the native permanent-spawn contract")
        assertEquals(-1, candidate.lifecycleDelCycle)
        assertEquals(1, creates)
        assertEquals(1, spawns)
        assertEquals(1, f.started, "Dynamic helpers must not restart the session")
        val uid = candidate.uid
        assertFailsWith<IllegalArgumentException> { f.manager.spawnFixedNpc(session, candidate) }
        assertEquals(uid, candidate.uid)
        assertEquals(1, creates)
        assertEquals(1, f.npcs.count())
        assertTrue(f.manager.terminate(session))
        assertFalse(candidate.isSlotAssigned)
        assertNull(f.manager.instanceForNpc(candidate))
        f.assertNoRegions()
    }

    @Test
    fun `guarded timed helper expires through native entity lifecycle and releases only its own ownership`() {
        val f = Fixture()
        val session = f.create(f.spec(actors = false))
        f.clock.cycle = 100
        val candidate = Npc(assertNotNull(ServerCacheManager.getNpc(12596)), FIRST_NPC)
        val permanent = Npc(assertNotNull(ServerCacheManager.getNpc(12596)), SECOND_NPC)
        assertTrue(f.manager.spawnFixedNpc(session, permanent))
        assertTrue(f.manager.spawnFixedNpc(session, candidate, duration = 20))
        assertEquals(120, candidate.lifecycleDelCycle)
        assertFalse(candidate.respawns)
        val lifecycle =
            EntityLifecycleProcess(
                f.npcRepo,
                mock(LocRepository::class.java),
                mock(ObjRepository::class.java),
                mock(ControllerRepository::class.java),
            )
        f.clock.cycle = 119
        lifecycle.process()
        assertTrue(candidate.isSlotAssigned)
        assertEquals(session.id, f.manager.instanceForNpc(candidate))
        f.clock.cycle = 120
        lifecycle.process()
        assertFalse(candidate.isSlotAssigned)
        assertNull(f.manager.instanceForNpc(candidate))
        assertEquals(listOf(permanent), f.manager.npcsForInstance(session.id))
        assertTrue(permanent.isSlotAssigned)
        assertSame(session, f.manager.sessionForId(session.id))
        f.manager.tickReclaim(120)
        assertTrue(f.manager.terminate(session))
        assertFalse(permanent.isSlotAssigned)
        assertEquals(0, f.npcs.count())
        f.assertNoRegions()
    }

    @Test
    fun `guarded spawn refuses invalid session state and rejects malformed candidates before native mutation`() {
        for (state in listOf("forged", "ended", "expired", "grace", "copied")) {
            val f = Fixture(allowCopies = state == "copied")
            val session =
                if (state == "copied") {
                    val copied =
                        InstanceSettings()
                            .withArea(
                                InstanceArea.copyRegions(
                                    listOf(9116),
                                    enterCoord = RegionLocal(0, 35, 156, 40, 38),
                                    exitCoord = EXIT,
                                ),
                                0,
                            )
                    assertNotNull(
                        f.manager.createServerOwned(
                            "copied-dynamic-control",
                            copied,
                            InstanceAccess.Friends,
                            0,
                        )
                    )
                } else f.create(f.spec(actors = false))
            val target =
                if (state == "forged") {
                    InstanceSession(
                        session.id,
                        session.regionIds,
                        session.owner,
                        session.key,
                        session.spec,
                        session.placement,
                        session.access,
                        isServerOwned = true,
                    )
                } else session
            when (state) {
                "ended" -> f.manager.terminate(session)
                "expired" -> session.markArenaExpired()
                "grace" -> session.enterGrace(1)
            }
            val candidate = Npc(assertNotNull(ServerCacheManager.getNpc(12596)), FIRST_NPC)
            val before = f.collisionSnapshot()
            assertFalse(f.manager.spawnFixedNpc(target, candidate), state)
            assertFalse(candidate.isSlotAssigned, state)
            assertEquals(NpcUid.NULL, candidate.uid, state)
            assertNull(f.manager.instanceForNpc(candidate), state)
            assertEquals(0, f.npcs.count(), state)
            assertEquals(before, f.collisionSnapshot(), state)
            if (state == "copied") assertEquals(1, f.allocations) else f.assertNoRegions()
            framework().clearInlineMocks()
        }
        val f = Fixture()
        val session = f.create(f.spec(actors = false))
        val before = f.collisionSnapshot()
        val type = assertNotNull(ServerCacheManager.getNpc(12596))
        val malformed =
            listOf(
                Npc(type, EXIT),
                Npc(type, EXIT).apply { coords = FIRST_NPC },
                Npc(type, CoordGrid(FIRST_NPC.x, FIRST_NPC.z, 1)),
                Npc(type, FIRST_NPC).apply { slotId = 100 },
                Npc(type, FIRST_NPC).apply {
                    slotId = 101
                    assignUid()
                    slotId = PathingEntity.INVALID_SLOT
                },
            )
        malformed.forEach { candidate ->
            assertFailsWith<IllegalArgumentException> {
                f.manager.spawnFixedNpc(session, candidate)
            }
            assertNull(f.manager.instanceForNpc(candidate))
        }
        fun nativeLifecycle(npc: Npc) =
            listOf(
                npc.lifecycleAddCycle,
                npc.lifecycleDelCycle,
                npc.lifecycleRevealCycle,
                npc.lifecycleRespawnCycle,
                npc.lifecycleChangeCycle,
                npc.lifecycleDelayedAddCycle,
                npc.lifecycleDelayedAddDuration,
            )
        val deletedTimed = Npc(type, FIRST_NPC)
        f.npcRepo.add(deletedTimed, 20)
        val oldDeadline = deletedTimed.lifecycleDelCycle
        f.npcRepo.del(deletedTimed, Int.MAX_VALUE)
        assertFalse(deletedTimed.isSlotAssigned)
        assertEquals(NpcUid.NULL, deletedTimed.uid)
        assertEquals(20, oldDeadline)
        assertEquals(oldDeadline, deletedTimed.lifecycleDelCycle)
        assertNotEquals(ZoneKey.NULL, deletedTimed.lastProcessedZone)
        val deletedState = nativeLifecycle(deletedTimed)
        val deletedZone = deletedTimed.lastProcessedZone
        assertFailsWith<IllegalArgumentException> { f.manager.spawnFixedNpc(session, deletedTimed) }
        assertEquals(
            deletedState,
            nativeLifecycle(deletedTimed),
            "Rejected reuse must preserve native deadlines",
        )
        assertEquals(deletedZone, deletedTimed.lastProcessedZone)
        assertNull(f.manager.instanceForNpc(deletedTimed))

        val pending = Npc(type, SECOND_NPC)
        f.npcRepo.addDelayed(pending, spawnDelay = 5, duration = 20)
        assertFalse(pending.isSlotAssigned)
        assertEquals(NpcUid.NULL, pending.uid)
        assertEquals(5, pending.lifecycleDelayedAddCycle)
        assertEquals(20, pending.lifecycleDelayedAddDuration)
        val pendingState = nativeLifecycle(pending)
        val pendingZone = pending.lastProcessedZone
        assertFailsWith<IllegalArgumentException> { f.manager.spawnFixedNpc(session, pending) }
        assertEquals(
            pendingState,
            nativeLifecycle(pending),
            "Rejection must not clear or reschedule the pending native add",
        )
        assertEquals(pendingZone, pending.lastProcessedZone)
        assertFalse(pending.isSlotAssigned)
        assertNull(f.manager.instanceForNpc(pending))
        assertEquals(0, f.npcs.count())
        assertTrue(f.manager.npcsForInstance(session.id).isEmpty())
        assertEquals(before, f.collisionSnapshot())
        f.assertNoRegions()
    }

    @Test
    fun `guarded dynamic spawn rejects nested registration while member departure leaves public ownership intact`() {
        val f = Fixture()
        val session = f.create(f.spec(actors = false))
        val player = f.player(1)
        f.enter(player, session)
        val tile = player.coords
        val type = assertNotNull(ServerCacheManager.getNpc(12596))
        val candidate = Npc(type, FIRST_NPC)
        val nested = Npc(type, SECOND_NPC)
        var creates = 0
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            creates++
            assertSame(candidate, npc)
            assertFalse(f.manager.spawnFixedNpc(session, nested))
            assertFalse(nested.isSlotAssigned)
            assertEquals(NpcUid.NULL, nested.uid)
            assertEquals(tile, f.manager.leaveForLogout(player, 1))
        }
        assertTrue(f.manager.spawnFixedNpc(session, candidate))
        assertEquals(1, creates)
        assertEquals(tile, player.coords)
        assertNull(f.manager.sessionForPlayer(player))
        assertNull(player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
        assertSame(session, f.manager.sessionForId(session.id))
        assertEquals(listOf(candidate), f.manager.npcsForInstance(session.id))
        assertEquals(session.id, f.manager.instanceForNpc(candidate))
        assertNull(f.manager.instanceForNpc(nested))
        assertEquals(1, f.npcs.count())
        assertEquals(1, f.started)
        assertTrue(f.manager.terminate(session))
        assertFalse(candidate.isSlotAssigned)
        f.assertNoRegions()
    }

    @Test
    fun `guarded dynamic spawn handles callback termination and quarantines lost native registration`() {
        for (duringCreate in listOf(true, false)) for (deleteCandidate in listOf(false, true)) {
            val f = Fixture()
            val session = f.create(f.spec(actors = false))
            val candidate = Npc(assertNotNull(ServerCacheManager.getNpc(12596)), FIRST_NPC)
            var callbacks = 0
            val retire: (Npc) -> Unit = { npc ->
                assertSame(candidate, npc)
                callbacks++
                assertEquals(session.id, f.manager.instanceForNpc(candidate))
                if (deleteCandidate) f.npcRepo.del(candidate, Int.MAX_VALUE)
                assertTrue(f.manager.terminate(session))
            }
            if (duringCreate)
                f.events.subscribeUnbound(NpcStateEvents.Create::class.java) { retire(npc) }
            else f.events.subscribeKeyed(NpcStateEvents.Spawn::class.java, 12596L) { retire(npc) }
            if (deleteCandidate) {
                assertFailsWith<IllegalStateException> {
                    f.manager.spawnFixedNpc(session, candidate)
                }
                // The outer native add may retain zone/collision residue. Only quarantine is
                // claimed.
                assertNull(
                    f.manager.createServerOwned(
                        "dynamic-failed-overlap",
                        session.spec,
                        InstanceAccess.Friends,
                        1,
                    )
                )
                f.manager.tickReclaim(2)
                assertNull(
                    f.manager.createServerOwned(
                        "dynamic-failed-reclaim",
                        session.spec,
                        InstanceAccess.Friends,
                        2,
                    )
                )
            } else {
                assertFalse(f.manager.spawnFixedNpc(session, candidate))
                assertTrue(session.isEnded)
                assertFalse(candidate.isSlotAssigned)
                assertNull(f.manager.instanceForNpc(candidate))
                assertTrue(f.manager.npcsForInstance(session.id).isEmpty())
                assertEquals(0, f.npcs.count())
                assertNotNull(
                    f.manager.createServerOwned(
                        "dynamic-next-owner",
                        session.spec,
                        InstanceAccess.Friends,
                        1,
                    )
                )
            }
            assertEquals(1, callbacks)
            f.assertNoRegions()
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
        var validRegion = true
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
                    "isValid" -> validRegion
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

        fun copiedSpec(deferred: Boolean = false): InstanceSpec =
            InstanceSettings(maxPlayers = 1, reclaimTicks = 1000, spawnOnFirstJoin = deferred)
                .withArea(
                    InstanceArea.copyRegions(
                        listOf(9116),
                        enterCoord = RegionLocal(0, 35, 156, 40, 38),
                        exitCoord = EXIT,
                        npcSpawns = listOf(spawn(7852, CoordGrid(35 * 64 + 42, 156 * 64 + 40))),
                    ),
                    0,
                )

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
