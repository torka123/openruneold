package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/** Actual manager placement, native queued impacts and native NPC-delete scene cleanup. */
@OptIn(InternalApi::class)
class BabaBananaNativeTest {
    @Test
    fun `Jungle Japes adjacent piles arm at one tick and queue native hits for current and previous tiles`() {
        ToaNativeCombatFixture(trackLocs = true).use { f ->
            val player = f.player()
            val raid = enterJapes(f, player)
            val tile = bananaTile(f, raid)
            val adjacent = CoordGrid(tile.x + 1, tile.z, tile.level)
            player.coords = adjacent
            player.lastProcessedCoord = tile
            val created = f.clock.cycle
            place(f, raid, tile)
            assertEquals(listOf(tile, adjacent), raid.baba.bananas.keys.toList())
            assertEquals(setOf(tile, adjacent), bananaLocTiles(f))
            assertEquals(
                listOf(created + 1, created + 1),
                raid.baba.bananas.values.map { it.armedAt },
            )
            assertEquals(
                listOf(created + 50, created + 50),
                raid.baba.bananas.values.map { it.expiresAt },
            )

            f.dispatchLateCycle(created)
            assertTrue(f.hits(player).isEmpty())
            assertFalse(player.isBound)
            assertEquals(2, bananaLocTiles(f).size)
            f.dispatchLateCycle(created + 1)
            assertTrue(raid.baba.bananas.isEmpty())
            assertTrue(bananaLocTiles(f).isEmpty())
            val hits = f.hits(player)
            assertEquals(listOf(1, 1), hits.map { it.first })
            assertTrue(hits.all { it.second.type == HitType.Typeless })
            val damage =
                ToaRules.maxHit(
                    BabaTuning.BANANA_DAMAGE.value,
                    raid.raidLevel,
                    raid.pathLevels[ToaPath.APMEKEN] ?: 0,
                )
            assertEquals(listOf(damage, damage), hits.map { it.second.damage })
            assertEquals(BabaTuning.BABOON_STUN_TICKS.value, player.bindCyclesRemaining)
            assertEquals(created + 1 + BabaTuning.BABOON_STUN_TICKS.value, player.bindUntil)
            assertEquals(99, player.hitpoints, "The launch does not deliver native damage early")
            f.advance(1)
            hits.forEach { (_, hit) -> f.processHit(player, hit) }
            assertEquals(99 - damage * 2, player.hitpoints)
        }
    }

    @Test
    fun `native fifty tick expiry removes occupied piles without binding or queuing damage`() {
        ToaNativeCombatFixture(trackLocs = true).use { f ->
            val player = f.player()
            val raid = enterJapes(f, player)
            val tile = bananaTile(f, raid)
            player.coords = CoordGrid(tile.x + 5, tile.z + 5, tile.level)
            player.lastProcessedCoord = CoordGrid.NULL
            val created = f.clock.cycle
            place(f, raid, tile)
            f.dispatchLateCycle(created + 49)
            assertEquals(2, raid.baba.bananas.size)
            assertEquals(2, bananaLocTiles(f).size)
            assertTrue(f.hits(player).isEmpty())
            player.coords = tile
            player.lastProcessedCoord = CoordGrid(tile.x + 1, tile.z, tile.level)
            f.dispatchLateCycle(created + 50)
            assertTrue(raid.baba.bananas.isEmpty())
            assertTrue(bananaLocTiles(f).isEmpty())
            assertTrue(f.hits(player).isEmpty())
            assertFalse(player.isBound)
            assertEquals(99, player.hitpoints)
        }
    }

    @Test
    fun `native Baba deletion removes persistent banana locs before later manager cleanup`() {
        ToaNativeCombatFixture(trackLocs = true).use { f ->
            val player = f.player()
            val raid = enterJapes(f, player)
            val source = f.baba(raid)
            val tile = bananaTile(f, raid)
            place(f, raid, tile)
            val queues = assertNotNull(raid.baba.bananaQueues)
            assertEquals(2, bananaLocTiles(f).size)
            // Stop at the native Delete event, before the retained post-delete kill callback.
            f.npcs.del(source, Int.MAX_VALUE)
            assertFalse(source.isSlotAssigned)
            assertTrue(queues.isClosed)
            assertTrue(raid.baba.bananas.isEmpty())
            assertTrue(bananaLocTiles(f).isEmpty())
            assertTrue(f.hits(player).isEmpty())

            val cleanup =
                ToaRaidManager::class
                    .java
                    .getDeclaredMethod(
                        "cleanupBaba",
                        ToaRaidSession::class.java,
                        Boolean::class.javaPrimitiveType,
                    )
            cleanup.isAccessible = true
            cleanup.invoke(f.manager, raid, false)
            queues.close()
            assertTrue(bananaLocTiles(f).isEmpty())
        }
    }

    private fun enterJapes(f: ToaNativeCombatFixture, player: Player): ToaRaidSession {
        val party = f.manager.createParty(player)
        assertTrue(party.invocations.toggle(ToaInvocation.JUNGLE_JAPES))
        f.start(player)
        return f.enterBaba(player)
    }

    private fun bananaTile(f: ToaNativeCombatFixture, raid: ToaRaidSession): CoordGrid =
        assertNotNull(
            f.instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(
                    ToaRoom.BABA,
                    BabaArenaLayout.MIN_LOCAL_X + 2,
                    BabaArenaLayout.MIN_LOCAL_Z + 2,
                ),
            )
        )

    private fun bananaLocTiles(f: ToaNativeCombatFixture): Set<CoordGrid> =
        f.placedLocs
            .filter { it.id == BabaAssets.BANANA_PILE_LOC }
            .mapTo(linkedSetOf()) { it.coords }

    private fun place(f: ToaNativeCombatFixture, raid: ToaRaidSession, tile: CoordGrid) {
        // Native baboon death is covered separately; invoke its existing room payload directly.
        val method =
            ToaRaidManager::class.java.declaredMethods.single {
                it.name.startsWith("handleBabaBaboonDeath") && it.parameterCount == 2
            }
        method.isAccessible = true
        method.invoke(f.manager, raid, tile.packed)
    }

    @AfterEach
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
