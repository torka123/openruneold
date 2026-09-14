package org.rsmod.content.minigames.toa

import kotlin.random.Random
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/** Actual manager and native impact preservation at 63d79bed9; no additional OSRS claims. */
@OptIn(InternalApi::class)
class KephriBombNativeTest {
    @Test
    fun `launch damage retains original tile while lock and native impact use their own current snapshots`() {
        for ((radius, protection, distance) in
            listOf(Triple(0, false, 0), Triple(1, true, 1), Triple(1, false, 2))) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = start(f, player)
                val source = f.boss(raid)
                val impacts = nativeHits(f)
                val original = tile(f, raid, 25, 24)
                val locked = tile(f, raid, 39, 39)
                player.coords = original
                // Choose a nonzero retained roll so the prayer/radius assertions are observable.
                while (roll(raid, f.clock.cycle, original) < 6) f.advance(1)
                val launched = f.clock.cycle
                val damage = roll(raid, launched, original)
                schedule(f, raid, source, player, radius)
                assertEquals(damage, raid.encounter.kephriBombTargets.single().damage)
                assertEquals(launched + 3, raid.encounter.kephriBombTargets.single().locksAt)
                player.coords = locked
                f.dispatchLateCycle(launched + 2)
                assertTrue(raid.encounter.kephriBombs.isEmpty())
                f.advance(1)
                f.queueProcess.process()
                assertTrue(raid.encounter.kephriBombs.isEmpty())
                f.dispatchLateCycle()
                val bomb = raid.encounter.kephriBombs.single()
                assertEquals(locked, bomb.tile)
                assertEquals(damage, bomb.damage)
                assertEquals(launched + 7, bomb.landsAt)
                assertEquals(
                    locked,
                    projectiles(f).last { it.spotanim == KephriBombs.PROJECTILE }.endCoord,
                )
                player.coords = if (distance <= radius) original else locked
                VarPlayerIntMapSetter.set(
                    player,
                    "varbit.prayer_protectfrommagic",
                    if (protection) 0 else 1,
                )
                f.dispatchLateCycle(launched + 5)
                assertTrue(f.deferredHits(player).isEmpty())
                f.dispatchLateCycle(launched + 6)
                val deferred = f.deferredHits(player).single()
                assertEquals(1, deferred.first)
                assertEquals(
                    damage,
                    deferred.second.builder.damage,
                    "No early prayer or position modification",
                )
                assertTrue(bomb.hitQueued)
                player.coords = CoordGrid(locked.x + distance, locked.z, locked.level)
                VarPlayerIntMapSetter.set(
                    player,
                    "varbit.prayer_protectfrommagic",
                    if (protection) 1 else 0,
                )
                val before = player.hitpoints
                f.advance(1)
                impacts.process(player)
                val expected =
                    if (distance > radius) 0 else if (protection) damage * 34 / 100 else damage
                assertEquals(
                    expected,
                    before - player.hitpoints,
                    "radius=$radius protected=$protection distance=$distance",
                )
                assertTrue(f.deferredHits(player).isEmpty())
                assertTrue(
                    bomb in raid.encounter.kephriBombs,
                    "Native impact precedes LateCycle visual removal",
                )
                f.dispatchLateCycle()
                assertTrue(raid.encounter.kephriBombs.isEmpty())
                assertEquals(0, f.bossEncounters.of(source).phaseAbilityCount)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `recovery bomber advances plus eight then plus twelve without targets and preserves launched bombs on recovery end`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player, aerial = true)
            val source = f.boss(raid)
            f.hitNpc(player, source, source.hitpoints)
            assertTrue(raid.encounter.kephriRecovering)
            val queues = assertNotNull(raid.encounter.kephriBombQueues)
            val first = queues.bomberAt
            assertEquals(
                f.clock.cycle + 8,
                first,
                "Recovery arms at the native incoming hit impact",
            )
            assertNotNull(raid.participant(player.uid)).spectating = true
            f.dispatchLateCycle(first - 1)
            assertEquals(first, queues.bomberAt)
            f.dispatchLateCycle(first)
            assertTrue(queues.bombs.isEmpty())
            assertEquals(first + 12, queues.bomberAt)
            assertNotNull(raid.participant(player.uid)).spectating = false
            f.advance(12)
            f.queueProcess.process()
            assertTrue(queues.bombs.isEmpty())
            f.dispatchLateCycle()
            val bomb = queues.bombs.single()
            assertFalse(
                bomb.hitQueued,
                "The later bomber dispatch cannot enter the earlier preparation slot",
            )
            assertEquals(f.clock.cycle + 4, bomb.landsAt)
            assertEquals(1, bomb.radius)
            assertEquals(first + 24, queues.bomberAt)
            finishRecovery(f, raid, source)
            source.actionDelay = Int.MAX_VALUE
            assertFalse(raid.encounter.kephriRecovering)
            assertEquals(-1, queues.bomberAt)
            assertSame(bomb, queues.bombs.single())
            f.dispatchLateCycle(bomb.landsAt)
            assertTrue(bomb.hitQueued)
            assertTrue(queues.bombs.isEmpty())
            assertEquals(-1, queues.bomberAt)
        }
    }

    @Test
    fun `native account rejoin rebinds unlocked bombs and leaving cancels only unlocked player targets`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player)
            val source = f.boss(raid)
            val queues = assertNotNull(raid.encounter.kephriBombQueues)
            schedule(f, raid, source, player)
            val pending = queues.targets.single()
            f.events.publish(SessionStateEvent.Logout(player))
            f.players.remove(player.slotId)
            f.advance(10)
            f.dispatchLateCycle()
            assertSame(pending, queues.targets.single())
            val replacement =
                f.player(9).apply {
                    uuid = player.uuid
                    accountHash = player.accountHash
                    accountId = player.accountId
                    characterId = player.characterId
                    assignUid()
                }
            assertTrue(f.manager.openLobby(replacement))
            assertSame(queues, raid.encounter.kephriBombQueues)
            assertSame(source, f.boss(raid))
            assertEquals(pending.copy(target = replacement.uid), queues.targets.single())
            replacement.coords = tile(f, raid, 39, 39)
            f.dispatchLateCycle()
            assertEquals(replacement.coords, queues.bombs.single().tile)
            assertEquals(
                f.clock.cycle + 4,
                queues.bombs.single().landsAt,
                "Overdue lock rearms from actual dispatch",
            )
            assertTrue(queues.targets.isEmpty())
        }
        framework().clearInlineMocks()
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val member = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, member)
            f.start(leader, partySize = 2)
            val raid = f.enterKephri(leader)
            val source = f.boss(raid).apply { actionDelay = Int.MAX_VALUE }
            val queues = assertNotNull(raid.encounter.kephriBombQueues)
            schedule(f, raid, source, member)
            f.dispatchLateCycle(f.clock.cycle + 3)
            val locked = queues.bombs.single()
            schedule(f, raid, source, member)
            assertEquals(1, queues.targets.size)
            assertTrue(f.manager.leave(member))
            assertFalse(queues.isClosed)
            assertTrue(queues.targets.isEmpty())
            assertSame(locked, queues.bombs.single())
            f.dispatchLateCycle(locked.landsAt)
            assertTrue(queues.bombs.isEmpty())
            assertTrue(f.deferredHits(member).isEmpty())
            assertEquals(1, f.deferredHits(leader).size)
        }
    }

    @Test
    fun `shield interruption and native deletion or reset retire pending bomb work without delayed rearm`() {
        for (ending in
            listOf("shield", "delete-recovering", "state-reset", "room-reset", "terminate")) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = start(f, player, aerial = true)
                val source = f.boss(raid)
                val queues = assertNotNull(raid.encounter.kephriBombQueues)
                if (ending == "delete-recovering") f.hitNpc(player, source, source.hitpoints)
                schedule(f, raid, source, player)
                f.dispatchLateCycle(f.clock.cycle + 3)
                assertEquals(1, queues.bombs.size)
                schedule(f, raid, source, player)
                assertEquals(1, queues.targets.size)
                when (ending) {
                    "shield" -> f.hitNpc(player, source, source.hitpoints)
                    "delete-recovering" -> f.npcs.del(source, Int.MAX_VALUE)
                    "state-reset" -> raid.encounter.reset()
                    "room-reset" -> f.enterKephri(player)
                    "terminate" -> f.instances.terminate(raid.instance)
                }
                assertTrue(queues.targets.isEmpty(), ending)
                assertTrue(queues.bombs.isEmpty(), ending)
                assertEquals(ending != "shield", queues.isClosed, ending)
                if (ending == "state-reset") assertNull(raid.encounter.kephriBombQueues)
                if (ending == "room-reset") assertNotSame(queues, raid.encounter.kephriBombQueues)
                if (ending != "shield") {
                    assertEquals(-1, queues.bomberAt, ending)
                    val packets = projectiles(f).size
                    f.advance(30)
                    f.queueProcess.process()
                    queues.processBombs(
                        { fail("Retired lock: $ending") },
                        { fail("Retired hit: $ending") },
                        { fail("Retired landing: $ending") },
                    )
                    queues.processBomber()
                    if (ending == "delete-recovering") {
                        f.dispatchLateCycle()
                        assertEquals(
                            -1,
                            queues.bomberAt,
                            "Missing body cannot rearm a closed bomber owner",
                        )
                    }
                    assertEquals(packets, projectiles(f).size, ending)
                    assertTrue(f.deferredHits(player).isEmpty(), ending)
                }
            }
            framework().clearInlineMocks()
        }
    }

    private fun start(
        f: ToaNativeCombatFixture,
        player: Player,
        aerial: Boolean = false,
    ): ToaRaidSession {
        val party = f.manager.createParty(player)
        if (aerial) assertTrue(party.invocations.toggle(ToaInvocation.AERIAL_ASSAULT))
        f.start(player, seed = 42)
        return f.enterKephri(player).also { f.boss(it).actionDelay = Int.MAX_VALUE }
    }

    private fun schedule(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        source: Npc,
        target: Player,
        radius: Int = 0,
    ) {
        ToaRaidManager::class
            .java
            .getDeclaredMethod(
                "scheduleKephriBomb",
                ToaRaidSession::class.java,
                Npc::class.java,
                Player::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            .apply { isAccessible = true }
            .invoke(f.manager, raid, source, target, radius, 24)
    }

    private fun finishRecovery(f: ToaNativeCombatFixture, raid: ToaRaidSession, source: Npc) {
        ToaRaidManager::class
            .java
            .getDeclaredMethod("finishKephriRecovery", ToaRaidSession::class.java, Npc::class.java)
            .apply { isAccessible = true }
            .invoke(f.manager, raid, source)
    }

    private fun nativeHits(f: ToaNativeCombatFixture): PlayerQueueProcessor {
        val scripts = ScriptContext(f.events, CheatCommandMap(), EngineQueueCache())
        with(PlayerHitScript()) { scripts.startup() }
        return PlayerQueueProcessor(f.events, f.protectedAccess)
    }

    private fun roll(raid: ToaRaidSession, tick: Int, tile: CoordGrid): Int =
        Random(
                raid.startSnapshot.seed xor
                    tick.toLong() xor
                    (tile.x.toLong() shl 32) xor
                    tile.z.toLong()
            )
            .nextInt(
                ToaRules.maxHit(24, raid.raidLevel, raid.pathLevels[ToaPath.SCABARAS] ?: 0)
                    .coerceAtLeast(1) + 1
            )

    private fun tile(f: ToaNativeCombatFixture, raid: ToaRaidSession, x: Int, z: Int): CoordGrid =
        assertNotNull(
            f.instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(ToaRoom.KEPHRI, x, z))
        )

    private fun projectiles(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world)
            .invocations
            .flatMap { it.arguments.filterIsInstance<ProjAnim>() }
            .filter {
                it.spotanim in
                    setOf(
                        KephriBombs.OVERHEAD_PROJECTILE,
                        KephriBombs.PROJECTILE,
                        ToaAssets.KEPHRI_BUG,
                    )
            }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
