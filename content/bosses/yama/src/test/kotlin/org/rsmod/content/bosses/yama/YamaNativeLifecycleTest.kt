package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.events.InstancePlayerJoinEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.player.hit.queueHit
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.map.CoordGrid

class YamaNativeLifecycleTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `registered native join owns one throne and duplicate notification preserves the run`() {
        val f = YamaNativeLifecycleFixture()
        val player = f.player()
        val run = f.enter(player)
        val session = run.session
        assertNotNull(f.instanceRegistry.get(YAMA_INSTANCE_KEY))
        assertEquals(listOf(run.throne), f.instances.npcsForInstance(session.id))
        repeat(2) {
            f.events.publish(
                InstancePlayerJoinEvent(player, session.key, session.id, session.owner)
            )
        }
        assertSame(run, f.run(session))
        assertEquals(setOf(player.accountHash), run.participants.keys)
        assertEquals(setOf(run.throne), run.combatActors)
        assertEquals(listOf(run.throne), f.instances.npcsForInstance(session.id))
        assertTrue(f.instances.terminate(session))
    }

    @Test
    fun `native owner departure keeps the duo partner and targets the remaining member`() {
        val f = YamaNativeLifecycleFixture()
        val (owner, partner, run) = f.duo()
        val boss = f.startFight(owner, run)
        f.instances.leave(owner, run.session, f.clock.cycle)
        assertFalse(f.encounters.isActive(owner))
        assertTrue(f.encounters.isActive(partner))
        assertEquals(setOf(partner.accountHash), run.participants.keys)
        assertEquals(partner.accountHash, run.ownerHash)
        assertSame(run.session, f.instances.sessionForPlayer(partner))
        assertEquals(run.session.id, f.instances.instanceForNpc(boss))
        assertSame(partner, assertIs<InteractionPlayerOp>(boss.interaction).target)
        val cleanup = f.burnCleanupCount(owner)
        f.events.publish(
            InstancePlayerLeaveEvent(owner, run.session.key, run.session.id, run.session.owner)
        )
        assertEquals(cleanup, f.burnCleanupCount(owner))
        assertEquals(1, cleanup)
        assertTrue(f.instances.terminate(run.session))
    }

    @Test
    fun `native participant scopes retire queued hits independently and never revive on reentry`() {
        val f = YamaNativeLifecycleFixture()
        val (owner, partner, run) = f.duo()
        val boss = f.startFight(owner, run)
        val oldScope = assertNotNull(partner.combatHitScope)
        val ownerHit = owner.queueHit(boss, delay = 3, type = HitType.Magic, damage = 10)
        val partnerHit = partner.queueHit(boss, delay = 3, type = HitType.Magic, damage = 10)
        assertTrue(ownerHit.isValidNow())
        assertTrue(partnerHit.isValidNow())

        f.instances.leave(partner, run.session, f.clock.cycle)
        assertTrue(boss.isSlotAssigned)
        assertEquals(run.session.id, f.instances.instanceForNpc(boss))
        assertFalse(partnerHit.isValidNow())
        assertTrue(ownerHit.isValidNow())
        assertNull(partner.combatHitScope)
        assertSame(run.session, f.instances.sessionForPlayer(owner))

        val replacement = f.enter(partner)
        assertNotSame(oldScope, assertNotNull(partner.combatHitScope))
        assertFalse(partnerHit.isValidNow())
        val replacementBoss = f.startFight(partner, replacement)
        val replacementHit =
            partner.queueHit(replacementBoss, delay = 3, type = HitType.Magic, damage = 10)
        assertTrue(replacementHit.isValidNow())
        assertTrue(ownerHit.isValidNow())

        // Coordinate mutation and the disconnect signal retire tokens before a lifecycle tick.
        val inside = partner.coords
        partner.coords = CoordGrid(3222, 3218)
        assertFalse(replacementHit.isValidNow())
        partner.coords = inside
        assertFalse(replacementHit.isValidNow())
        assertFalse(partnerHit.isValidNow())
        assertTrue(ownerHit.isValidNow())
        owner.clientDisconnected.set(true)
        assertFalse(ownerHit.isValidNow())
        assertTrue(boss.isSlotAssigned)
        assertTrue(f.instances.terminate(run.session))
        assertTrue(f.instances.terminate(replacement.session))
    }

    @Test
    fun `native exit logout disconnect death and external teleport retire a solo run once`() {
        val endings =
            listOf<Pair<String, (YamaNativeLifecycleFixture, Player, InstanceSession) -> Unit>>(
                "exit" to
                    { f, player, session ->
                        f.instances.leave(player, session, f.clock.cycle)
                    },
                "logout" to { f, player, _ -> f.instances.handleLogout(player, f.clock.cycle) },
                "disconnect" to
                    { f, player, _ ->
                        player.clientDisconnected.set(true)
                        f.nativeLifecycle.onPostTick(player)
                    },
                "death" to { f, player, _ -> f.instances.handleDeath(player, f.clock.cycle) },
                "teleport" to
                    { f, player, _ ->
                        val destination = CoordGrid(3222, 3218)
                        player.coords = destination
                        assertTrue(
                            f.instances.leaveForExternalTeleport(player, destination, f.clock.cycle)
                        )
                    },
            )
        for ((name, end) in endings) {
            try {
                val f = YamaNativeLifecycleFixture()
                val player = f.player()
                val run = f.enter(player)
                val helper = f.attachHelper(run)
                end(f, player, run.session)
                f.instances.tickReclaim(f.clock.cycle + 1)
                assertFalse(f.encounters.isActive(player), name)
                assertNull(f.instances.sessionForPlayer(player), name)
                assertNull(f.instances.sessionForId(run.session.id), name)
                assertEquals(YamaState.INVALIDATED, run.state, name)
                assertTrue(run.participants.isEmpty(), name)
                assertTrue(run.combatActors.isEmpty(), name)
                assertNull(f.instances.instanceForNpc(helper), name)
                assertTrue(f.npcList.none(), name)
                assertNull(player.lootDropDuration, name)
                assertEquals(1, f.burnCleanupCount(player), name)
                f.events.publish(
                    InstancePlayerLeaveEvent(
                        player,
                        run.session.key,
                        run.session.id,
                        run.session.owner,
                    )
                )
                assertFalse(f.instances.terminate(run.session), name)
                assertEquals(1, f.burnCleanupCount(player), name)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `termination deletes native helpers outside the combat set and permits fresh reentry`() {
        val f = YamaNativeLifecycleFixture()
        val player = f.player()
        val first = f.enter(player)
        val helper = f.attachHelper(first)
        assertFalse(f.encounters.isEncounterNpc(helper))
        assertEquals(first.session.id, f.instances.instanceForNpc(helper))
        assertTrue(f.instances.terminate(first.session))
        assertEquals(YamaState.INVALIDATED, first.state)
        assertTrue(first.combatActors.isEmpty())
        assertFalse(helper.isSlotAssigned)
        assertFalse(first.throne.isSlotAssigned)
        assertNull(f.instances.instanceForNpc(helper))
        assertNull(f.instances.instanceForNpc(first.throne))
        val second = f.enter(player)
        assertNotEquals(first.session.id, second.session.id)
        assertNotSame(first, second)
        assertNotSame(first.throne, second.throne)
        assertEquals(listOf(second.throne), f.instances.npcsForInstance(second.session.id))
        f.events.publish(
            InstancePlayerLeaveEvent(
                player,
                first.session.key,
                first.session.id,
                first.session.owner,
            )
        )
        assertSame(second.session, f.instances.sessionForPlayer(player))
        assertEquals(setOf(player.accountHash), second.participants.keys)
        assertTrue(f.instances.terminate(second.session))
    }

    @Test
    fun `captured death continuation completes after native deletion and cannot reward twice`() {
        val f = YamaNativeLifecycleFixture()
        val player = f.player()
        val run = f.enter(player)
        val boss = f.startFight(player, run)
        assertSame(run, f.encounters.beginBossDeath(boss))
        f.npcs.del(boss, Int.MAX_VALUE)
        assertNull(f.instances.instanceForNpc(boss))
        f.encounters.finishBossDeath(boss, run)
        assertEquals(YamaState.DEFEATED, run.state)
        assertNull(run.boss)
        assertFalse(boss in run.combatActors)
        assertEquals(1, f.rewardRolls)
        assertEquals(1, player.attr[YAMA_KILL_COUNT])
        f.encounters.finishBossDeath(boss, run)
        assertEquals(1, f.rewardRolls)
        assertEquals(1, player.attr[YAMA_KILL_COUNT])
        assertTrue(f.instances.terminate(run.session))
    }

    @Test
    fun `death continuation captured before teardown cannot affect a replacement session`() {
        val f = YamaNativeLifecycleFixture()
        val player = f.player()
        val first = f.enter(player)
        val boss = f.startFight(player, first)
        assertSame(first, f.encounters.beginBossDeath(boss))
        assertTrue(f.instances.terminate(first.session))
        val second = f.enter(player)
        repeat(2) { f.encounters.finishBossDeath(boss, first) }
        assertEquals(0, f.rewardRolls)
        assertEquals(0, player.attr[YAMA_KILL_COUNT] ?: 0)
        assertEquals(YamaState.LOBBY, second.state)
        assertSame(second.session, f.instances.sessionForPlayer(player))
        assertEquals(listOf(second.throne), f.instances.npcsForInstance(second.session.id))
        assertTrue(f.instances.terminate(second.session))
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
