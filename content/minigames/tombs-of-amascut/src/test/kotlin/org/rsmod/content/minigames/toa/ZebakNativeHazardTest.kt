package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/** Retained jug, corpse, splash and acid behavior at 327407463; no new OSRS timing claims. */
@OptIn(InternalApi::class)
class ZebakNativeHazardTest {
    @Test
    fun `tied jug collisions retain actor order then cleanse water at the following native slot`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = prepare(f, player)
            val state = raid.zebak
            f.dispatchLateCycle(f.clock.cycle + 6)
            state.acidTiles.clear()
            val jugs = state.jugs.values.take(2)
            val rocks = state.rocks.values.toList()
            assertEquals(2, jugs.size)
            assertEquals(2, rocks.size)
            // Arm in reverse order: native due-node order must not replace the retained jug loop.
            stageCollision(f, raid, player, jugs[1], rocks[1])
            stageCollision(f, raid, player, jugs[0], rocks[0])
            val collision = f.clock.cycle
            val centers = rocks.map { it.npc.coords }
            state.acidTiles.addAll(centers)
            jugs.forEach {
                assertTrue(it.collisionPending)
                assertFalse(it.collisionReady)
                assertNotNull(it.collisionTask)
                assertFalse(f.manager.pushZebakJug(player, it.npc))
            }
            f.advance(1)
            f.queueProcess.process()
            assertTrue(jugs.all { it.npc.isSlotAssigned && !it.collisionReady })
            f.dispatchLateCycle()
            assertEquals(collision + 1, f.clock.cycle)
            assertTrue(jugs.all { !it.npc.isSlotAssigned && it.collisionTask == null })
            assertEquals(centers, splashes(f).map { it.startCoord }.distinct())
            assertTrue(centers.all { it in state.waterTiles && it in state.acidTiles })
            val splashes = state.waterSplashes.toList()
            assertEquals(2, splashes.size)
            assertTrue(splashes.all { it.cleanseTask != null })
            f.dispatchLateCycle(collision + 2)
            assertTrue(centers.none { it in state.acidTiles })
            assertTrue(state.waterSplashes.isEmpty())
            assertTrue(splashes.all { it.cleanseTask == null })
        }
    }

    @Test
    fun `launched splash survives enrage preparation cleanup and cleanses on its original next tick`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = prepare(f, player)
            val state = raid.zebak
            f.dispatchLateCycle(f.clock.cycle + 6)
            state.acidTiles.clear()
            val jug = state.jugs.values.first()
            val tile = jug.tile
            state.acidTiles += tile
            assertTrue(f.manager.hitZebakJug(player, jug.npc))
            val splash = state.waterSplashes.single()
            assertTrue(tile in state.waterTiles)
            val body = assertNotNull(state.body)
            body.hitpoints = body.baseHitpointsLvl / 4
            f.dispatchLateCycle()
            assertEquals(ZebakState.ENRAGED, state.state)
            assertTrue(state.jugs.isEmpty())
            assertTrue(state.rocks.isEmpty())
            assertSame(splash, state.waterSplashes.single())
            assertTrue(tile in state.acidTiles)
            f.dispatchLateCycle(f.clock.cycle + 1)
            assertFalse(tile in state.acidTiles)
            assertTrue(state.waterSplashes.isEmpty())
            assertNull(splash.cleanseTask)
            assertFalse(assertNotNull(state.payloadQueues).isClosed)
        }
    }

    @Test
    fun `third roar corpse readiness survives enrage and deletes rocks exactly four ticks later`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = prepare(f, player)
            val state = raid.zebak
            val prepared = f.clock.cycle
            assertNotNull(raid.participant(player.uid)).spectating = true
            for (offset in listOf(6, 33, 37, 39, 41)) {
                f.dispatchLateCycle(prepared + offset)
            }
            val rocks = state.rocks.values.toList()
            assertEquals(2, rocks.size)
            assertTrue(rocks.all { it.hitpoints == 0 && it.collapsing })
            assertTrue(rocks.all { it.collapseTask != null && !it.collapseReady })
            val body = assertNotNull(state.body)
            body.hitpoints = body.baseHitpointsLvl / 4
            f.dispatchLateCycle()
            assertEquals(ZebakState.ENRAGED, state.state)
            f.dispatchLateCycle(prepared + 44)
            assertTrue(rocks.all { it.npc.isSlotAssigned })
            f.advance(1)
            f.queueProcess.process()
            assertTrue(rocks.all { it.npc.isSlotAssigned && !it.collapseReady })
            f.dispatchLateCycle()
            assertTrue(state.rocks.isEmpty())
            assertTrue(rocks.all { !it.npc.isSlotAssigned && it.collapseTask == null })
            assertEquals(ZebakState.ENRAGED, state.state)
        }
    }

    @Test
    fun `leaving acid cancels its old deadline and resets immediate exposure before the next ramp`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player)
            val state = raid.zebak
            val acid = player.coords
            val safe = tile(f, raid, 38, 32)
            state.acidTiles += acid
            f.dispatchLateCycle()
            val first = f.clock.cycle
            val runtime = state.playerState.getValue(player.uid)
            assertEquals(listOf(4), acidDamage(f, player))
            assertEquals(1, runtime.acidStack)
            assertFalse(runtime.acidReady)
            assertNotNull(runtime.acidTask)
            assertTrue(
                f.hits(player).any { it.second.context.attribution.origin == HitOrigin.Poison }
            )
            player.coords = safe
            f.dispatchLateCycle(first + 1)
            assertEquals(0, runtime.acidStack)
            assertTrue(runtime.acidReady)
            assertNull(runtime.acidTask)
            player.coords = acid
            f.dispatchLateCycle(first + 2)
            assertEquals(listOf(4, 4), acidDamage(f, player))
            f.dispatchLateCycle(first + 4)
            assertEquals(
                listOf(4, 4),
                acidDamage(f, player),
                "The canceled first deadline cannot fire",
            )
            f.advance(2)
            f.queueProcess.process()
            assertFalse(runtime.acidReady, "Default processing cannot release an owned exposure")
            f.dispatchLateCycle()
            assertEquals(listOf(4, 4, 6), acidDamage(f, player))
            assertEquals(2, runtime.acidStack)
            assertFalse(runtime.acidReady)
            f.dispatchLateCycle(first + 10)
            assertEquals(listOf(4, 4, 6, 8), acidDamage(f, player))
        }
    }

    @Test
    fun `acid readiness retains the player runtime and original deadline through native account rejoin`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player)
            val state = raid.zebak
            val acid = player.coords
            state.acidTiles += acid
            f.dispatchLateCycle()
            val first = f.clock.cycle
            val runtime = state.playerState.getValue(player.uid)
            assertEquals(listOf(4), acidDamage(f, player))
            f.events.publish(SessionStateEvent.Logout(player))
            f.players.remove(player.slotId)
            f.advance(2)
            f.queueProcess.process()
            f.dispatchLateCycle()
            assertFalse(runtime.acidReady)
            val replacement =
                f.player(9).apply {
                    uuid = player.uuid
                    accountHash = player.accountHash
                    accountId = player.accountId
                    characterId = player.characterId
                    assignUid()
                }
            assertTrue(f.manager.openLobby(replacement))
            assertSame(runtime, state.playerState.getValue(replacement.uid))
            replacement.coords = acid
            f.dispatchLateCycle(first + 3)
            assertTrue(acidDamage(f, replacement).isEmpty())
            f.dispatchLateCycle(first + 4)
            assertEquals(listOf(6), acidDamage(f, replacement))
            assertEquals(2, runtime.acidStack)
            assertFalse(runtime.acidReady)
            assertTrue(
                acidDamage(f, player).size <= 1,
                "No callback can deliver to the old identity",
            )
        }
    }

    @Test
    fun `death deletion reset and termination cancel pending jug splash and acid callbacks`() {
        for (retirement in listOf("death", "delete", "reset", "terminate")) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = prepare(f, player)
                val state = raid.zebak
                f.dispatchLateCycle(f.clock.cycle + 6)
                state.acidTiles.clear()
                val jugs = state.jugs.values.take(2)
                val rock = state.rocks.values.first()
                stageCollision(f, raid, player, jugs[0], rock)
                assertTrue(f.manager.hitZebakJug(player, jugs[1].npc))
                val splash = state.waterSplashes.single()
                state.acidTiles += player.coords
                f.dispatchLateCycle()
                val exposure = state.playerState.getValue(player.uid)
                assertNotNull(exposure.acidTask, retirement)
                assertNotNull(jugs[0].collisionTask, retirement)
                assertNotNull(splash.cleanseTask, retirement)
                val queues = assertNotNull(state.payloadQueues)
                val body = assertNotNull(state.body)
                val packets = splashes(f).size
                when (retirement) {
                    "death" -> f.manager.handleNpcKill(player, body)
                    "delete" -> f.npcs.del(body, Int.MAX_VALUE)
                    "reset" -> f.enterZebak(player)
                    "terminate" -> f.instances.terminate(raid.instance)
                }
                assertTrue(queues.isClosed, retirement)
                assertNull(jugs[0].collisionTask, retirement)
                assertFalse(jugs[0].collisionPending, retirement)
                assertFalse(jugs[0].collisionReady, retirement)
                assertNull(splash.cleanseTask, retirement)
                assertNull(exposure.acidTask, retirement)
                assertFalse(exposure.acidReady, retirement)
                val remainingAcid = state.acidTiles.toSet()
                val remainingHits = f.hits(player).size
                f.advance(100)
                f.queueProcess.process()
                queues.processJugReadiness()
                queues.processWaterSplashes()
                queues.processRockReadiness()
                queues.processAcidReadiness()
                assertEquals(packets, splashes(f).size, retirement)
                assertEquals(remainingAcid, state.acidTiles, retirement)
                assertEquals(remainingHits, f.hits(player).size, retirement)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `native boss deletion cancels the third pulse rock corpse deadline`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = prepare(f, player)
            val prepared = f.clock.cycle
            assertNotNull(raid.participant(player.uid)).spectating = true
            for (offset in listOf(6, 33, 37, 39, 41)) f.dispatchLateCycle(prepared + offset)
            val rocks = raid.zebak.rocks.values.toList()
            assertTrue(rocks.all { it.collapseTask != null })
            val queues = assertNotNull(raid.zebak.payloadQueues)
            f.npcs.del(assertNotNull(raid.zebak.body), Int.MAX_VALUE)
            assertTrue(queues.isClosed)
            assertTrue(rocks.all { it.collapseTask == null && !it.collapseReady })
            f.advance(4)
            f.queueProcess.process()
            queues.processRockReadiness()
            assertTrue(rocks.all { !it.collapseReady })
        }
    }

    private fun start(f: ToaNativeCombatFixture, player: Player): ToaRaidSession {
        val raid = f.start(player, seed = 42)
        f.enterZebak(player)
        PathingEntityCommon.telejump(player, f.collision, tile(f, raid, 37, 32))
        f.dispatchLateCycle(raid.zebak.emergesAt)
        assertNotNull(raid.zebak.body).actionDelay = Int.MAX_VALUE
        return raid
    }

    private fun prepare(f: ToaNativeCombatFixture, player: Player): ToaRaidSession {
        val raid = start(f, player)
        val body = assertNotNull(raid.zebak.body)
        raid.zebak.firstSpecial = ZebakSpecial.ROAR
        f.hitNpc(player, body, body.baseHitpointsLvl * 16 / 100)
        f.dispatchLateCycle()
        assertEquals(ZebakState.PREP_ROAR, raid.zebak.state)
        body.actionDelay = Int.MAX_VALUE
        return raid
    }

    private fun stageCollision(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        player: Player,
        jug: ZebakJugRuntime,
        rock: ZebakRockRuntime,
    ) {
        val center = rock.npc.coords
        jug.tile = CoordGrid(center.x - 1, center.z, center.level)
        PathingEntityCommon.telejump(jug.npc, f.collision, jug.tile)
        PathingEntityCommon.telejump(
            player,
            f.collision,
            CoordGrid(center.x - 2, center.z, center.level),
        )
        assertTrue(f.manager.pushZebakJug(player, jug.npc))
        f.dispatchLateCycle()
        assertTrue(jug.collisionPending)
        assertEquals(center, jug.tile)
        assertSame(jug, raid.zebak.jugs.getValue(jug.npc.hashCode()))
    }

    private fun tile(f: ToaNativeCombatFixture, raid: ToaRaidSession, x: Int, z: Int): CoordGrid =
        assertNotNull(
            f.instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(ToaRoom.ZEBAK, x, z))
        )

    private fun splashes(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world)
            .invocations
            .flatMap { it.arguments.filterIsInstance<ProjAnim>() }
            .filter { it.spotanim == ZebakAssets.JUG_SPLASH }

    private fun acidDamage(f: ToaNativeCombatFixture, player: Player): List<Int> =
        f.hits(player)
            .filter { it.second.context.attribution.origin != HitOrigin.Poison }
            .map { it.second.damage }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
